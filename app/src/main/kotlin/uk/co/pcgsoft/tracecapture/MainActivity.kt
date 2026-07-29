package uk.co.pcgsoft.tracecapture

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import uk.co.pcgsoft.tracecapture.detail.CaptureDetailScreen
import uk.co.pcgsoft.tracecapture.inbox.InboxScreen
import uk.co.pcgsoft.tracecapture.ui.theme.TraceCaptureTheme

object AppRoutes {
    const val INBOX = "inbox"
    const val DETAIL = "detail/{captureId}"

    fun detail(captureId: String): String =
        "detail/${Uri.encode(captureId)}"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TraceCaptureTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.INBOX,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(AppRoutes.INBOX) {
                        InboxScreen(
                            onCaptureSelected = { captureId ->
                                navController.navigate(AppRoutes.detail(captureId))
                            }
                        )
                    }
                    composable(
                        route = AppRoutes.DETAIL,
                        arguments = listOf(
                            navArgument("captureId") { type = NavType.StringType }
                        )
                    ) {
                        CaptureDetailScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
