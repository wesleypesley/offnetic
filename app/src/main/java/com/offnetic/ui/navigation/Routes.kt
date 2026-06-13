package com.offnetic.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val SPLASH = "splash"
    const val PERMISSION_1_CONNECTIVITY = "permissions/connectivity"
    const val PERMISSION_2_CAMERA_MIC = "permissions/camera_mic"
    const val PERMISSION_3_NOTIFICATIONS = "permissions/notifications"
    const val IDENTITY_GENERATION = "identity_generation"
    const val PROFILE_SETUP = "profile_setup"
    const val MAIN = "main"
    const val QR_SCANNER = "qr_scanner"
    const val MY_QR = "my_qr"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{contactPublicKey}"
    const val CONTACT_DETAIL = "contact_detail/{publicKey}"
    const val SETTINGS = "settings"

    fun chatRoute(contactPublicKey: String) =
        "chat/${URLEncoder.encode(contactPublicKey, "UTF-8")}"

    fun contactDetailRoute(publicKey: String) =
        "contact_detail/${URLEncoder.encode(publicKey, "UTF-8")}"

    fun decodeKey(encoded: String): String {
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Exception) {
            encoded
        }
    }
}
