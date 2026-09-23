package com.taskshare.app.e2e

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.taskshare.app.MainActivity
import com.taskshare.app.permissions.BluetoothPermissions
import com.taskshare.app.testing.TaskShareTestHooks
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the case AddTaskAndSyncFlowTest deliberately doesn't: a fresh user who has NOT yet
 * granted Bluetooth permission. Explicitly revokes it (rather than assuming the test runner's
 * default install state) so this doesn't depend on how the instrumentation APK happened to be
 * installed. Doesn't attempt to drive the OS-level system permission dialog itself (that's not
 * part of the app's own Compose UI and needs UiAutomator, not this test's compose rule) — just
 * verifies the app correctly routes to the permission screen instead of straight to Main when
 * permission is missing, which is the actual behavior this change added.
 */
@RunWith(AndroidJUnit4::class)
class BluetoothPermissionScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearAppStateAndRevokePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.deleteDatabase("task-share.db")
        context.getSharedPreferences("task_share_secure_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        BluetoothPermissions.required().forEach { permission ->
            instrumentation.uiAutomation.revokeRuntimePermission(context.packageName, permission)
        }
    }

    @After
    fun resetHooks() {
        TaskShareTestHooks.reset()
    }

    @Test
    fun onboarding_withoutBluetoothPermission_routesToPermissionScreenNotMain() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Your name").performTextInput("Sam")
            composeRule.onNodeWithText("Get started").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Bluetooth needed to sync").assertExists()
        }
    }
}
