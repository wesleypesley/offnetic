package com.offnetic.ui.contacts

import android.util.Base64
import org.json.JSONObject

data class QrPairingData(
    val publicKey: String,
    val displayName: String?
) {
    fun toQrPayload(): String {
        val json = JSONObject().apply {
            put("pk", publicKey)
            put("dn", displayName ?: JSONObject.NULL)
        }
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    companion object {
        fun fromQrPayload(raw: String): QrPairingData? {
            return try {
                val jsonStr = String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                val dn = if (json.has("dn") && !json.isNull("dn")) json.getString("dn") else null
                QrPairingData(
                    publicKey = json.getString("pk"),
                    displayName = dn
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
