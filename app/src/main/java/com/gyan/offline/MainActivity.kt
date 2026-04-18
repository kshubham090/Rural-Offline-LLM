package com.gyan.offline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gyan.offline.ui.chat.ChatScreen
import com.gyan.offline.ui.onboarding.DownloadScreen
import com.gyan.offline.ui.onboarding.OnboardingScreen
import com.gyan.offline.ui.theme.GyanTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Route {
    const val ONBOARDING = "onboarding"
    const val DOWNLOAD   = "download"
    const val CHAT       = "chat"
}

class MainActivity : ComponentActivity() {

    private val app get() = application as GyanApplication

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* voice degrades gracefully if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var startRoute = Route.ONBOARDING
        var splashDone = false

        splash.setKeepOnScreenCondition { !splashDone }

        lifecycleScope.launch {
            val (onboardingDone, modelsDownloaded) = combine(
                app.prefs.onboardingDone,
                app.prefs.modelsDownloaded
            ) { a, b -> Pair(a, b) }.first()

            startRoute = when {
                !onboardingDone   -> Route.ONBOARDING
                !modelsDownloaded -> Route.DOWNLOAD
                else              -> Route.CHAT
            }
            splashDone = true

            micPermission.launch(Manifest.permission.RECORD_AUDIO)

            // Init AdMob only after reading consent
            val consented = app.prefs.adConsentGiven.first()
            val childDirected = app.prefs.isChildDirected.first()
            if (consented) app.adManager.initialize(childDirected)
        }

        setContent {
            GyanTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = startRoute) {

                    composable(Route.ONBOARDING) {
                        OnboardingScreen { adConsent ->
                            lifecycleScope.launch {
                                app.prefs.setOnboardingDone()
                                app.prefs.setAdConsent(adConsent)
                                if (adConsent) {
                                    val childDirected = app.prefs.isChildDirected.first()
                                    app.adManager.initialize(childDirected)
                                }
                            }
                            navController.navigate(Route.DOWNLOAD) {
                                popUpTo(Route.ONBOARDING) { inclusive = true }
                            }
                        }
                    }

                    composable(Route.DOWNLOAD) {
                        DownloadScreen {
                            navController.navigate(Route.CHAT) {
                                popUpTo(Route.DOWNLOAD) { inclusive = true }
                            }
                        }
                    }

                    composable(Route.CHAT) {
                        ChatScreen()
                    }
                }
            }
        }
    }
}
