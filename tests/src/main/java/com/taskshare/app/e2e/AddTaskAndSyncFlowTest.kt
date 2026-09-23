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
import com.taskshare.app.testing.TaskShareTestHooks
import com.taskshare.app.transport.FakeDeviceTransport
import com.taskshare.app.transport.PairingInfo
import com.taskshare.app.update.RemoteAppVersion
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Drives the real app UI end-to-end through Compose semantics — no unit-level shortcuts into
 * ViewModels. The NFC tap and Bluetooth radio are swapped for fakes via TaskShareTestHooks
 * (set up before MainActivity launches) since two physical phones can't be tapped together in
 * CI; see README "Testing strategy" for why this is the deliberate tradeoff for this project.
 */
@RunWith(AndroidJUnit4::class)
class AddTaskAndSyncFlowTest {

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
                    SyncTaskDto(peerTaskName, "", "Living room", Frequency(1, FrequencyUnit.WEEK), emptyList(), Priority.MEDIUM, Instant.now())
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
}
