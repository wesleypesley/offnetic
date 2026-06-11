package com.offnetic.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offnetic.data.nearby.NcapManager
import com.offnetic.service.NcapForegroundService
import com.offnetic.ui.call.CallActivity
import com.offnetic.ui.onboarding.SplashViewModel
import com.offnetic.ui.chat.ChatListScreen
import com.offnetic.ui.chat.ChatScreen
import com.offnetic.ui.contacts.BlockedContactsScreen
import com.offnetic.ui.contacts.ContactDetailScreen
import com.offnetic.ui.contacts.MyQrScreen
import com.offnetic.ui.contacts.QrScannerScreen
import com.offnetic.ui.nearby.NearbyPeersScreen
import com.offnetic.ui.onboarding.IdentityGenerationScreen
import com.offnetic.ui.onboarding.PermissionSlide
import com.offnetic.ui.onboarding.ProfileSetupScreen
import com.offnetic.ui.onboarding.SplashScreen
import com.offnetic.ui.screens.MainScreen
import com.offnetic.ui.settings.AccountScreen
import com.offnetic.ui.settings.SettingsScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.net.URLEncoder

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IncomingCallEntryPoint {
    fun ncapManager(): NcapManager
}

@Composable
fun OffneticNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val ncapManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, IncomingCallEntryPoint::class.java)
            .ncapManager()
    }

    LaunchedEffect(Unit) {
        ncapManager.incomingCallEvents.collect { senderPublicKey ->
            android.util.Log.e("offCall", "NavHost incomingCallEvents → launching CallActivity for ${senderPublicKey.take(8)}")
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra("EXTRA_PEER_PUBLIC_KEY", URLEncoder.encode(senderPublicKey, "UTF-8"))
                putExtra("EXTRA_IS_INCOMING", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
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
            val chatListViewModel: com.offnetic.ui.chat.ChatListViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                val isBgScanning = chatListViewModel.isScoutMode.value
                if (isBgScanning) {
                    context.startForegroundService(Intent(context, NcapForegroundService::class.java))
                }
            }
            MainScreen(navController)
        }

        composable(Routes.NEARBY) {
            NearbyPeersScreen(
                onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                onChatsClick = { navController.popBackStack() }
            )
        }

        composable(Routes.QR_SCANNER) {
            QrScannerScreen(
                onBack = { navController.popBackStack() },
                onScanComplete = { data ->
                    navController.navigate(Routes.chatRoute(data.publicKey)) {
                        popUpTo(Routes.MAIN)
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

        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onChatClick = { contactPublicKey ->
                    navController.navigate(Routes.chatRoute(contactPublicKey))
                },
                onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                onNearbyClick = { navController.popBackStack() }
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
                        putExtra("EXTRA_PEER_PUBLIC_KEY", Routes.decodeKey(peerKey))
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

        composable(Routes.BLOCKED_CONTACTS) {
            BlockedContactsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAccountClick = { navController.navigate(Routes.ACCOUNT) },
                onBlockedContactsClick = { navController.navigate(Routes.BLOCKED_CONTACTS) }
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
