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

### Transport: NFC handshake + Bluetooth payload

Classic Android NFC (NDEF push / "Android Beam") was deprecated in Android 10 and its bandwidth
is far too low for a task database or an APK anyway. So NFC is used **only** for a tap-to-pair
handshake (exchanging a peer id, Bluetooth address, and a session token); the actual sync
payload and any APK pull happen over Bluetooth Classic (RFCOMM), chosen over BLE for its better
throughput on APK-sized transfers.

- [`nfc/NfcHandshake.kt`](app/src/main/java/com/taskshare/app/nfc/NfcHandshake.kt) — interface.
  `NfcReaderModeHandshake` (reader side) + `NfcHandshakeHostService` (HCE "tag" side) are real
  but **not yet hardware-validated** — see "Known gaps" below.
- [`transport/DeviceTransport.kt`](app/src/main/java/com/taskshare/app/transport/DeviceTransport.kt)
  — interface. `BluetoothDeviceTransport` is the real Bluetooth Classic implementation (also not
  yet hardware-validated); `FakeDeviceTransport` is an in-process loopback used by tests.

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

1. **Bluetooth MAC address retrieval.** Since Android 6.0, `BluetoothAdapter.getAddress()`
   returns a placeholder, not the real MAC, for privacy reasons. `NfcHandshakeHostService`
   currently has a `TODO_PLACEHOLDER_ADDRESS` where a real address is needed. Needs a design
   change (e.g. rely on OS-level Bluetooth discoverability/pairing instead of a literal MAC
   string, or switch to BLE with an advertised identifier) validated on real devices.
2. **`BluetoothDeviceTransport` wire codec.** `SyncPayloadCodec.encode/decode` are `TODO()`
   stubs — the transport shape (framed length-prefixed messages) is real, but payload
   serialization (likely `kotlinx.serialization` JSON) isn't wired up yet.
3. **Runtime permissions.** `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` (API 31+) and NFC reader-mode
   behavior need to be requested/handled in the share screen; not yet implemented.
4. **APK distribution end-to-end.** The "pull a newer version and prompt to install" flow
   (`ShareUpdateScreen`'s version-prompt dialog → `TransportSession.pullApk` →
   `MainActivity.requestInstall`) is wired, but needs a real device pass: signing consistency
   between the two installs, and the `REQUEST_INSTALL_PACKAGES` user consent flow.

## Open design questions

- Task retirement currently has no cross-device propagation at all (see "Sync model" above). If
  that turns out to matter in practice, the fix is an archived/tombstone flag that syncs
  additively like a completion instance does — flagged here rather than built speculatively.
