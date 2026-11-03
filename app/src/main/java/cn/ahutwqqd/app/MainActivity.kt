package cn.ahutwqqd.app

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.ahutwqqd.app.data.SignRepository
import cn.ahutwqqd.app.ui.AhutSignTheme
import cn.ahutwqqd.app.ui.AppRoot
import cn.ahutwqqd.app.ui.AppViewModel
import cn.ahutwqqd.app.util.Permissions
import cn.ahutwqqd.app.work.GuardService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须先于 super.onCreate，开屏底色才会跟着 values-night 走
        installSplashScreen()

        // 状态栏与导航栏都透明，图标深浅由系统深色模式自动决定
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        // 在 Activity 处于前台时拉起守护服务：Android 12 起后台启动前台服务会被拒，
        // 这里借用「用户刚点开应用」的合法时机把常驻服务挂上去。
        // 只在真的开了自动签到 + 守护时才拉，否则每次打开应用都会闪一条守护通知。
        val settings = SignRepository.instance.settings.value
        if (settings.autoSignEnabled && settings.guardEnabled) {
            GuardService.start(this)
        }

        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            AhutSignTheme(
                themeMode = settings.themeMode,
                themeStyle = settings.themeStyle
            ) {
                NotificationPermissionGate()
                AppRoot(viewModel)
            }
        }
    }
}

/** 首次启动时申请通知权限，用户拒绝也不影响手动签到 */
@androidx.compose.runtime.Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Permissions.needsNotificationRequest(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
