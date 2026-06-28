package com.offnetic.ui.contacts

import android.util.Base64
import org.json.JSONObject

private val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

data class QrPairingData(
    val publicKey: String,
    val displayName: String?,
    val nostrPublicKey: String? = null
) {
    fun toQrPayload(): String {
        val json = JSONObject().apply {
            put("pk", publicKey)
            put("dn", displayName ?: JSONObject.NULL)
            if (nostrPublicKey != null) put("nk", nostrPublicKey)
        }
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), BASE64_FLAGS)
    }

    companion object {
        fun fromQrPayload(raw: String): QrPairingData? {
            return try {
                val jsonStr = String(Base64.decode(raw, BASE64_FLAGS), Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                val dn = if (json.has("dn") && !json.isNull("dn")) json.getString("dn") else null
                val nk = if (json.has("nk") && !json.isNull("nk")) json.getString("nk") else null
                QrPairingData(
                    publicKey = json.getString("pk"),
                    displayName = dn,
                    nostrPublicKey = nk
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
