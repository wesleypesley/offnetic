package com.offnetic.data.crypto

import android.util.Base64
import org.json.JSONObject

sealed class NcapEnvelope {

    enum class PayloadType {
        PRE_KEY_BUNDLE,
        SIGNAL_PRE_KEY_BUNDLE,
        SIGNAL_SESSION_TERMINATED,
        SIGNAL_MESSAGE,
        HEARTBEAT,
        QR_PAIRING_REQUEST,
        FILE_TRANSFER_REQUEST,
        FILE_TRANSFER_ACCEPT,
        FILE_TRANSFER_REJECT,
        FILE_TRANSFER_COMPLETE,
        CALL_OFFER,
        CALL_ANSWER,
        ICE_CANDIDATE,
        CALL_HANGUP,
        INITIAL_IDENTITY
    }

    data class Plain(
        val senderPublicKey: String,
        val payloadType: PayloadType,
        val payload: ByteArray
    ) : NcapEnvelope() {
        fun toBytes(): ByteArray {
            val json = JSONObject().apply {
                put("fmt", "plain")
                put("sender", senderPublicKey)
                put("type", payloadType.name)
                put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))
            }
            return json.toString().toByteArray(Charsets.UTF_8)
        }

        companion object {
            fun fromBytes(bytes: ByteArray): Plain? {
                return try {
                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                    Plain(
                        senderPublicKey = json.getString("sender"),
                        payloadType = PayloadType.valueOf(json.getString("type")),
                        payload = Base64.decode(json.getString("payload"), Base64.NO_WRAP)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    data class SealedSender(
        val ciphertext: ByteArray
    ) : NcapEnvelope() {
        fun toBytes(): ByteArray {
            val json = JSONObject().apply {
                put("sealed", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                put("fmt", "sealed")
            }
            return json.toString().toByteArray(Charsets.UTF_8)
        }

        companion object {
            fun fromBytes(bytes: ByteArray): SealedSender? {
                return try {
                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                    SealedSender(
                        ciphertext = Base64.decode(json.getString("sealed"), Base64.NO_WRAP)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    companion object {
        fun parse(bytes: ByteArray): NcapEnvelope? {
            return try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                when (json.optString("fmt", null)) {
                    "sealed" -> SealedSender.fromBytes(bytes)
                    else -> Plain.fromBytes(bytes)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
