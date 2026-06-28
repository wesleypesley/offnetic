package com.offnetic.ui.navigation

import android.content.Intent
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offnetic.data.nearby.NcapManager
import com.offnetic.service.NcapForegroundService
import com.offnetic.ui.call.CallActivity
import com.offnetic.ui.onboarding.SplashViewModel
import com.offnetic.ui.chat.ChatListScreen
import com.offnetic.ui.chat.ChatScreen
import com.offnetic.ui.contacts.AddContactConfirmScreen
import com.offnetic.ui.contacts.ContactDetailScreen
import com.offnetic.ui.contacts.MyQrScreen
import com.offnetic.ui.contacts.QrScannerScreen
import com.offnetic.ui.contacts.RequestsScreen
import com.offnetic.ui.onboarding.FullScreenIntentPermissionScreen
import com.offnetic.ui.onboarding.IdentityGenerationScreen
import com.offnetic.ui.onboarding.PermissionSlide
import com.offnetic.ui.onboarding.ProfileSetupScreen
import com.offnetic.ui.onboarding.SplashScreen
import com.offnetic.ui.screens.MainScreen
import com.offnetic.ui.settings.SettingsScreen
import com.offnetic.util.MessageNotificationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.net.URLEncoder

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavHostEntryPoint {
    fun ncapManager(): NcapManager
    fun messageNotificationManager(): MessageNotificationManager
}

@Composable
fun OffneticNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val ncapManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, NavHostEntryPoint::class.java)
            .ncapManager()
    }

    val messageNotificationManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, NavHostEntryPoint::class.java)
            .messageNotificationManager()
    }

    LaunchedEffect(Unit) {
        ncapManager.incomingCallEvents.collect { senderPublicKey ->
            if (!ncapManager.isCallActive) {
                val intent = Intent(context, CallActivity::class.java).apply {
                    putExtra("EXTRA_PEER_PUBLIC_KEY", URLEncoder.encode(senderPublicKey, "UTF-8"))
                    putExtra("EXTRA_IS_INCOMING", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    var showLocationDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ncapManager.locationRequired.collect { showLocationDialog = true }
    }
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Location Required", fontWeight = FontWeight.Bold) },
            text = { Text("Location must be enabled for device discovery. Open Settings to turn it on.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Open Settings", color = Color(0xFF4ADE80))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Cancel", color = Color(0x66FFFFFF))
                }
            },
            containerColor = Color(0xFF141414),
            titleContentColor = Color.White,
            textContentColor = Color(0xB3FFFFFF)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val pendingChatKey by messageNotificationManager.pendingChatNavigation.collectAsState()

    LaunchedEffect(pendingChatKey, currentRoute) {
        val key = pendingChatKey ?: return@LaunchedEffect
        if (currentRoute == Routes.MAIN) {
            messageNotificationManager.pendingChatNavigation.value = null
            navController.navigate(Routes.chatRoute(key))
        }
    }

    val splashViewModel: SplashViewModel = hiltViewModel()
    val hasIdentity by splashViewModel.hasIdentity.collectAsState()
    val hasProfile by splashViewModel.hasProfile.collectAsState()

    val pendingPairingPayload by messageNotificationManager.pendingPairingPayload.collectAsState()

    LaunchedEffect(pendingPairingPayload, hasIdentity, hasProfile) {
        val payload = pendingPairingPayload ?: return@LaunchedEffect
        if (hasIdentity && hasProfile) {
            messageNotificationManager.pendingPairingPayload.value = null
            navController.navigate(Routes.addContactConfirmRoute(payload))
        }
    }

    NavHost(
        navController = navController,
        startDestination = when {
            !hasIdentity -> Routes.SPLASH
            !hasProfile -> Routes.PROFILE_SETUP
            else -> Routes.MAIN
        }
    ) {
        composable(Routes.SPLASH) {
            val splashViewModel: SplashViewModel = hiltViewModel()
            val hasIdentity by splashViewModel.hasIdentity.collectAsState()

            SplashScreen(
                onDone = {
                    if (hasIdentity) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.PERMISSION_1_CONNECTIVITY)
                    }
                }
            )
        }

        composable(Routes.PERMISSION_1_CONNECTIVITY) {
            PermissionSlide(
                title = "Connectivity",
                onNext = { navController.navigate(Routes.PERMISSION_2_CAMERA_MIC) }
            )
        }

        composable(Routes.PERMISSION_2_CAMERA_MIC) {
            PermissionSlide(
                title = "Camera & Microphone",
                onNext = { navController.navigate(Routes.PERMISSION_3_NOTIFICATIONS) }
            )
        }

        composable(Routes.PERMISSION_3_NOTIFICATIONS) {
            PermissionSlide(
                title = "Notifications",
                onNext = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        navController.navigate(Routes.PERMISSION_4_FULL_SCREEN_INTENT)
                    } else {
                        navController.navigate(Routes.IDENTITY_GENERATION)
                    }
                }
            )
        }

        composable(Routes.PERMISSION_4_FULL_SCREEN_INTENT) {
            FullScreenIntentPermissionScreen(
                onNext = { navController.navigate(Routes.IDENTITY_GENERATION) }
            )
        }

        composable(Routes.IDENTITY_GENERATION) {
            IdentityGenerationScreen(
                onDone = { navController.navigate(Routes.PROFILE_SETUP) }
            )
        }

        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onDone = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.startForegroundService(Intent(context, NcapForegroundService::class.java))
            }
            MainScreen(navController)
        }

        composable(Routes.QR_SCANNER) {
            QrScannerScreen(
                onBack = { navController.popBackStack() },
                onScanComplete = { payload ->
                    navController.navigate(Routes.addContactConfirmRoute(payload)) {
                        launchSingleTop = true
                    }
                },
                onShowMyQr = { navController.navigate(Routes.MY_QR) }
            )
        }

        composable(Routes.MY_QR) {
            MyQrScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REQUESTS) {
            RequestsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADD_CONTACT_CONFIRM,
            arguments = listOf(navArgument("payload") { type = NavType.StringType })
        ) {
            AddContactConfirmScreen(
                onBack = { navController.popBackStack() },
                onConfirmed = { publicKey ->
                    navController.navigate(Routes.chatRoute(publicKey)) {
                        popUpTo(Routes.MAIN)
                    }
                }
            )
        }

        composable(Routes.CHAT_LIST) {
            val context = LocalContext.current
            val activity = context as? android.app.Activity
            ChatListScreen(
                onChatClick = { contactPublicKey ->
                    navController.navigate(Routes.chatRoute(contactPublicKey))
                },
                onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                onRequests = { navController.navigate(Routes.REQUESTS) },
                onNearbyClick = { navController.popBackStack() },
                onShutdown = {
                    context.stopService(Intent(context, NcapForegroundService::class.java))
                    activity?.finish()
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("contactPublicKey") { type = NavType.StringType })
        ) {
            val context = LocalContext.current
            ChatScreen(
                onBack = { navController.popBackStack() },
                onCall = { peerKey ->
                    val intent = Intent(context, CallActivity::class.java).apply {
                        putExtra("EXTRA_PEER_PUBLIC_KEY", URLEncoder.encode(peerKey, "UTF-8"))
                        putExtra("EXTRA_IS_INCOMING", false)
                    }
                    context.startActivity(intent)
                }
            )
        }

        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument("publicKey") { type = NavType.StringType })
        ) {
            ContactDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
