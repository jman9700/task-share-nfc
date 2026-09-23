# Task Share

A serverless Android app for domestic partners to track recurring household tasks (dishes,
exercise, cleaning the car, …) and keep each other's task lists in sync by tapping phones
together — no account, no backend, no internet dependency.

## Modules

- **`app`** — the Android application (Kotlin, Jetpack Compose, Room/SQLCipher).
- **`tests`** — a standalone Android test module (`com.android.test`) containing GUI-level,
  end-to-end instrumented tests that drive `app`'s real UI through Compose semantics. It's a
  separate Gradle module rather than an `androidTest/` folder inside `app`, per the project's
  "split app and tests" goal.

## Architecture

### Sync model

There is no central server. Two devices exchange data by tapping phones (NFC) and transferring
the actual payload over Bluetooth. The sync is **strictly additive**:

- **Tasks** match across devices **by name** (case/whitespace-insensitive). If both partners
  create a task called "Dishes" independently, they merge into one task the first time the
  devices sync. A task's `location`, `owners`, `priority`, and `frequency` are only set once, at
  creation — later local edits to those fields are intentionally device-local and never
  overwritten or propagated by sync.
- **Task instances** (completions) match by a stable id generated when the completion is
  recorded, so re-syncing the same two devices repeatedly never creates duplicates.
- **Deleting/archiving a task is local-only.** There's no delete propagation or tombstone, so if
  you archive a task on your phone but your partner's phone still has it, a future sync will
  re-add it. Accepted as a known v1 limitation — see "Open design questions" below.
- **Known limitation:** renaming a task *after* it has already synced does not propagate (name
  is only read at creation) and will break future name-matching for that task across devices.

See [`SyncMerger`](app/src/main/java/com/taskshare/app/data/sync/SyncMerger.kt) for the actual
merge rules and [`SyncMergerTest`](app/src/test/java/com/taskshare/app/data/sync/SyncMergerTest.kt)
for the behavior this locks in.

### Transport: NFC handshake + BLE discovery + Bluetooth payload

Classic Android NFC (NDEF push / "Android Beam") was deprecated in Android 10 and its bandwidth
is far too low for a task database or an APK anyway. So NFC is used **only** for a tap-to-pair
handshake; the actual sync payload and any APK pull happen over Bluetooth Classic (RFCOMM),
chosen over BLE for its better throughput on APK-sized transfers.

The handshake can't hand over a Bluetooth address directly — Android has blocked apps from
reading their own adapter's address since 6.0. Instead NFC carries a random per-tap
`sessionToken`; the tapped phone advertises that token over BLE
([`BlePairingAdvertiser`](app/src/main/java/com/taskshare/app/transport/ble/BlePairingAdvertiser.kt))
and the phone that initiated the tap scans for it
([`BlePairingScanner`](app/src/main/java/com/taskshare/app/transport/ble/BlePairingScanner.kt)),
reading the peer's *real* address off the scan result — that read is unrestricted; only reading
your own address is. Once the address is known, the two sides connect over Classic RFCOMM as
before. The tapped phone has no screen open during any of this (HCE services run in the
background), so it's driven entirely by
[`PassiveSyncResponder`](app/src/main/java/com/taskshare/app/nfc/PassiveSyncResponder.kt) from
inside `NfcHandshakeHostService`, using the same `BluetoothTransportSession` protocol code the
active side uses — both ends run identical send-then-receive steps once the socket is open, so
neither needs to know whether it's the "client" or the "server".

- [`nfc/NfcHandshake.kt`](app/src/main/java/com/taskshare/app/nfc/NfcHandshake.kt) — interface.
  `NfcReaderModeHandshake` (reader side) + `NfcHandshakeHostService` (HCE "tag" side) are real
  but **not yet hardware-validated** — see "Known gaps" below.
- [`transport/DeviceTransport.kt`](app/src/main/java/com/taskshare/app/transport/DeviceTransport.kt)
  — interface. `BluetoothDeviceTransport` (client/active side) and `BluetoothRfcommServer`
  (accept/passive side) are the real Bluetooth Classic implementations (not yet
  hardware-validated); `FakeDeviceTransport` is an in-process loopback used by tests.
- [`transport/SyncPayloadCodec.kt`](app/src/main/java/com/taskshare/app/transport/SyncPayloadCodec.kt)
  — the actual wire format (length-prefixed fields, no new dependency), unit tested for round-trip
  correctness including empty payloads and unicode/delimiter-like text.

### Local storage

Room over SQLCipher, with the SQLCipher passphrase generated once and stored in
`EncryptedSharedPreferences` (key material backed by the Android Keystore) — see
[`DbKeyProvider`](app/src/main/java/com/taskshare/app/data/db/DbKeyProvider.kt). The database
file on disk is encrypted; the passphrase is never written in plaintext.

### Identity

Each device generates a random UUID as "this user's" id on first launch (see
`OnboardingScreen`), paired with a user-chosen display name. The UUID, not the name, is the
stable identity used for task ownership and completion attribution, so renaming yourself later
doesn't break history. `HouseholdUser` records are exchanged additively during sync so each
device learns the other partner's display name.

## Testing strategy

GUI-level, end-to-end tests (in `tests/`) drive the real app through Compose's semantics tree —
tapping the actual "Add task" FAB, typing into the actual form fields, reading the actual task
list — rather than calling into ViewModels directly. Since the NFC tap and Bluetooth transfer
can't be exercised by two physical phones in CI, both are hidden behind interfaces
(`NfcHandshake`, `DeviceTransport`) and swapped for fakes via a small test seam,
[`TaskShareTestHooks`](app/src/main/java/com/taskshare/app/testing/TaskShareTestHooks.kt), which
`MainActivity` checks before constructing the real radio implementations. This lets the E2E
suite cover onboarding, adding a task, and a full additive sync merge, in an emulator, with no
hardware.

**This does not replace real-device testing.** Before relying on the NFC/Bluetooth path, it
needs a manual pass on two actual phones — see "Known gaps" below for the specific things that
are structurally written but unverified.

Run the pure-logic unit tests (sync merge rules, urgency scoring, calendar projection, version
comparison) with:

```bash
./gradlew :app:testDebugUnitTest
```

Run the GUI E2E tests on a connected device/emulator with:

```bash
./gradlew :tests:connectedDebugAndroidTest
```

## Known gaps (need real hardware to close)

1. ~~Bluetooth MAC address retrieval.~~ **Resolved** — see "Transport" above: NFC now only
   carries a session token, and the real address comes from a BLE scan result instead of the
   blocked `getAddress()` self-lookup. Structurally complete and unit-tested where it can be
   (the codec), but the BLE advertise/scan/RFCOMM-accept path itself is still
   **not yet hardware-validated** — two real phones are needed to confirm timing (how long a
   tap needs to hold before BLE advertising is actually broadcasting), OEM BLE stack quirks, and
   that `PassiveSyncResponder`'s background execution actually survives long enough inside a
   `HostApduService`'s process lifecycle.
2. ~~`BluetoothDeviceTransport` wire codec.~~ **Resolved** — `SyncPayloadCodec` is a real,
   unit-tested implementation now (see `SyncPayloadCodecTest`), not a `TODO()` stub.
3. ~~Runtime permissions.~~ **Resolved** —
   [`BluetoothPermissionScreen`](app/src/main/java/com/taskshare/app/ui/permissions/BluetoothPermissionScreen.kt)
   requests `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`/`BLUETOOTH_ADVERTISE` (API 31+) or
   `ACCESS_FINE_LOCATION` (API 26–30) right after onboarding, since `PassiveSyncResponder` runs
   from a background service with no Activity to request permissions from later — waiting until
   the user opens the share screen wouldn't be enough to reliably serve an incoming tap.
   Declining doesn't block the rest of the app; it's skippable, with an in-context "grant
   permission" prompt added to `ShareUpdateScreen` itself as a second chance, and
   `BluetoothDeviceTransport.connect()` now fails fast with a clear message
   (`MissingBluetoothPermissionException`) instead of a bare `SecurityException` several layers
   down. See `BluetoothPermissionsTest` for the API-level branching logic and
   `BluetoothPermissionScreenTest` for the routing behavior — **not yet hardware-validated**
   beyond that: real OEM permission-dialog UX (timing, "don't ask again" flows) still needs a
   two-device pass, same as the rest of this transport.
4. **APK distribution end-to-end.** The "pull a newer version and prompt to install" flow
   (`ShareUpdateScreen`'s version-prompt dialog → `TransportSession.pullApk` →
   `MainActivity.requestInstall`) works from the active/client side, but `PassiveSyncResponder`
   doesn't yet serve APK bytes if asked — that needs a small protocol addition (a request flag
   after the payload exchange) that hasn't been built. A pull attempt against the passive side
   today will just fail once the socket closes, rather than transfer anything. Also still needs:
   signing consistency between the two installs, and the `REQUEST_INSTALL_PACKAGES` user consent
   flow on a real device.

## Open design questions

- Task retirement currently has no cross-device propagation at all (see "Sync model" above). If
  that turns out to matter in practice, the fix is an archived/tombstone flag that syncs
  additively like a completion instance does — flagged here rather than built speculatively.
