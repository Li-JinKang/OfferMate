package com.jk.offermate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jk.offermate.ui.navigation.OfferMateApp
import com.jk.offermate.ui.theme.OfferMateTheme

class MainActivity : ComponentActivity() {

    /** 用于把 `ACTION_SEND` 分享文本从 Activity 层传给 Compose 层（含 singleTask 复用场景）。 */
    private val sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OfferMateApplication).container
        sharedTextState.value = extractSharedText(intent)
        setContent {
            OfferMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RequestNotificationPermission()
                    OfferMateApp(
                        container = container,
                        sharedText = sharedTextState.value,
                        onSharedTextConsumed = { sharedTextState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)?.let { sharedTextState.value = it }
    }

    /** 仅处理 `ACTION_SEND` + `text/plain`（App 分享/浏览器分享链接的典型形态）。 */
    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    }
}

/** Android 13+ 首次进入时请求通知权限（用于后台分析完成通知）。 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
