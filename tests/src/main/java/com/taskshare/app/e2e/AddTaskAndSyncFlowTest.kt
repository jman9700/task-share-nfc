package com.taskshare.app.e2e

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.taskshare.app.MainActivity
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.sync.SyncInstanceDto
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.data.sync.SyncTaskDto
import com.taskshare.app.nfc.FakeNfcHandshake
import com.taskshare.app.permissions.BluetoothPermissions
import com.taskshare.app.testing.TaskShareTestHooks
import com.taskshare.app.transport.FakeDeviceTransport
import com.taskshare.app.transport.PairingInfo
import com.taskshare.app.update.RemoteAppVersion
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Drives the real app UI end-to-end through Compose semantics — no unit-level shortcuts into
 * ViewModels. The NFC tap and Bluetooth radio are swapped for fakes via TaskShareTestHooks
 * (set up before MainActivity launches) since two physical phones can't be tapped together in
 * CI; see README "Testing strategy" for why this is the deliberate tradeoff for this project.
 *
 * Bluetooth permissions are pre-granted here via GrantPermissionRule so these tests reach Main
 * screen the same way a user who already granted them would — the permission-request screen
 * itself (shown when they haven't) is covered separately by BluetoothPermissionScreenTest.
 */
@RunWith(AndroidJUnit4::class)
class AddTaskAndSyncFlowTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*BluetoothPermissions.required())

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("task-share.db")
        context.getSharedPreferences("task_share_secure_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun resetHooks() {
        TaskShareTestHooks.reset()
    }

    @Test
    fun onboarding_thenAddTask_appearsOnMainScreen() {
        TaskShareTestHooks.nfcHandshakeOverride = FakeNfcHandshake(PairingInfo("peer", "session-token"))
        TaskShareTestHooks.transportOverride = FakeDeviceTransport(
            peerPayload = SyncPayload("peer-device", emptyList(), emptyList(), emptyList()),
            peerVersion = RemoteAppVersion(1, "1.0"),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()

            // Onboarding: first launch has no local user yet.
            composeRule.onNodeWithText("Your name").performTextInput("Sam")
            composeRule.onNodeWithText("Get started").performClick()
            composeRule.waitForIdle()

            // Main screen -> add a task.
            composeRule.onNodeWithText("Task Share").assertExists()
            composeRule.onNodeWithContentDescription("Add task").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Name").performTextInput("Dishes")
            composeRule.onNodeWithText("Location / room").performTextInput("Kitchen")
            composeRule.onNodeWithText("Save task").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Dishes").assertExists()
        }
    }

    @Test
    fun shareUpdate_mergesNewTaskAndInstanceFromPeer_additively() {
        val peerTaskName = "Vacuum"
        TaskShareTestHooks.nfcHandshakeOverride = FakeNfcHandshake(PairingInfo("peer", "session-token"))
        TaskShareTestHooks.transportOverride = FakeDeviceTransport(
            peerPayload = SyncPayload(
                senderDeviceId = "peer-device",
                users = emptyList(),
                tasks = listOf(
                    SyncTaskDto(peerTaskName, "", "Living room", Frequency(1, FrequencyUnit.WEEK), LocalDate.now(), emptyList(), Priority.MEDIUM, Instant.now())
                ),
                instances = listOf(
                    SyncInstanceDto("peer-instance-1", peerTaskName, Instant.now(), "peer-user")
                ),
            ),
            peerVersion = RemoteAppVersion(1, "1.0"),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Your name").performTextInput("Sam")
            composeRule.onNodeWithText("Get started").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Share update via NFC").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Start NFC tap").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Sync complete").assertExists()
            composeRule.onNodeWithText("1 new task(s), 1 new completion(s), 0 new person(s) added.").assertExists()
        }
    }

    @Test
    fun shareUpdate_peerHasNewerVersion_offersUpdate_dismissingDoesNotInstall() {
        TaskShareTestHooks.nfcHandshakeOverride = FakeNfcHandshake(PairingInfo("peer", "session-token"))
        TaskShareTestHooks.transportOverride = FakeDeviceTransport(
            peerPayload = SyncPayload("peer-device", emptyList(), emptyList(), emptyList()),
            // The installed app's own versionCode is 1 (see app/build.gradle.kts); anything
            // higher makes the peer look newer, which is all FakeDeviceTransport checks.
            peerVersion = RemoteAppVersion(999, "9.9"),
            fakeApkBytes = byteArrayOf(1, 2, 3),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Your name").performTextInput("Sam")
            composeRule.onNodeWithText("Get started").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Share update via NFC").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Start NFC tap").performClick()
            composeRule.waitForIdle()

            // Not clicking "Install now": that triggers a real system install intent via
            // MainActivity.requestInstall, which isn't something to fire from an automated test.
            composeRule.onNodeWithText("Update ready").assertExists()
            composeRule.onNodeWithText("Not now").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Update ready").assertDoesNotExist()
        }
    }
}
