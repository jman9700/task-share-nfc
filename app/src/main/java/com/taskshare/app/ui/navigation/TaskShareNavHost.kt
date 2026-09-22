package com.taskshare.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.transport.DeviceTransport
import com.taskshare.app.ui.calendar.CalendarScreen
import com.taskshare.app.ui.main.MainScreen
import com.taskshare.app.ui.newtask.NewTaskScreen
import com.taskshare.app.ui.onboarding.OnboardingScreen
import com.taskshare.app.ui.share.ShareUpdateScreen
import java.io.File

@Composable
fun TaskShareNavHost(
    repository: TaskRepository,
    nfcHandshake: NfcHandshake,
    transport: DeviceTransport,
    localVersionCode: Long,
    localVersionName: String,
    apkDownloadDestination: () -> File,
    onInstallApk: (File) -> Unit,
) {
    val navController = rememberNavController()
    var checkedOnboarding by remember { mutableStateOf(false) }
    var needsOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        needsOnboarding = !repository.hasLocalUser()
        checkedOnboarding = true
    }

    if (!checkedOnboarding) return

    NavHost(navController = navController, startDestination = if (needsOnboarding) Routes.ONBOARDING else Routes.MAIN) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(repository = repository, onDone = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainScreen(
                repository = repository,
                onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                onAddTask = { navController.navigate(Routes.NEW_TASK) },
                onShareUpdate = { navController.navigate(Routes.SHARE_UPDATE) },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(Routes.NEW_TASK) {
            NewTaskScreen(
                repository = repository,
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.SHARE_UPDATE) {
            ShareUpdateScreen(
                repository = repository,
                nfcHandshake = nfcHandshake,
                transport = transport,
                localVersionCode = localVersionCode,
                localVersionName = localVersionName,
                apkDownloadDestination = apkDownloadDestination,
                onInstallApk = onInstallApk,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
