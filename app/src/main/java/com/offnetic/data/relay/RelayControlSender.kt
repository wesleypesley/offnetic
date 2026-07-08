package com.offnetic.data.relay

import com.offnetic.data.crypto.Bech32
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.nostr.GiftWrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayControlSender @Inject constructor(
    private val nostrIdentityManager: NostrIdentityManager,
    private val relayPool: RelayPool
) {
    private val receiptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastReceiptAtMs = AtomicLong(0L)
    suspend fun sendConnectionRequest(
        recipientNpub: String,
        myOffneticPk: String,
        myDisplayName: String,
        myBundleBytes: ByteArray
    ): Boolean {
        val recipientPub = decode(recipientNpub) ?: return false
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return false
        val content = JSONObject().apply {
            put("pk", myOffneticPk)
            put("name", myDisplayName)
            put("bundle", Base64.getEncoder().encodeToString(myBundleBytes))
        }.toString()
        val giftWrap = GiftWrap.wrap(
            senderPriv = myPriv,
            recipientPub = recipientPub,
            content = content,
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf(RelayControl.TAG_TYPE, RelayControl.TYPE_REQUEST))
        )
        return relayPool.publish(giftWrap) > 0
    }

    suspend fun sendBundle(recipientNpub: String, bundleBytes: ByteArray): Boolean {
        val recipientPub = decode(recipientNpub) ?: return false
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return false
        val giftWrap = GiftWrap.wrap(
            senderPriv = myPriv,
            recipientPub = recipientPub,
            content = Base64.getEncoder().encodeToString(bundleBytes),
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf(RelayControl.TAG_TYPE, RelayControl.TYPE_BUNDLE))
        )
        return relayPool.publish(giftWrap) > 0
    }

    fun sendDeliveryAck(recipientNpub: String, messageUuid: String) {
        receiptScope.launch { throttledReceipt(recipientNpub, messageUuid, RelayControl.TYPE_ACK) }
    }

    fun sendReadReceipt(recipientNpub: String, messageUuid: String) {
        receiptScope.launch { throttledReceipt(recipientNpub, messageUuid, RelayControl.TYPE_READ) }
    }

    suspend fun sendCallSignal(recipientNpub: String, type: String, payload: String): Boolean {
        val recipientPub = decode(recipientNpub) ?: return false
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return false
        val giftWrap = GiftWrap.wrap(
            senderPriv = myPriv,
            recipientPub = recipientPub,
            content = payload,
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf(RelayControl.TAG_TYPE, type))
        )
        return relayPool.publish(giftWrap) > 0
    }

    suspend fun sendFileBlossom(recipientNpub: String, payloadJson: String, messageUuid: String): Boolean {
        val recipientPub = decode(recipientNpub) ?: return false
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return false
        val giftWrap = GiftWrap.wrap(
            senderPriv = myPriv,
            recipientPub = recipientPub,
            content = payloadJson,
            kind = GiftWrap.KIND_DM,
            tags = listOf(
                listOf(RelayControl.TAG_TYPE, RelayControl.TYPE_FILE_BLOSSOM),
                listOf("u", messageUuid)
            )
        )
        return relayPool.publish(giftWrap) > 0
    }

    private suspend fun throttledReceipt(recipientNpub: String, messageUuid: String, type: String) {
        val wait = RECEIPT_INTERVAL_MS - (System.currentTimeMillis() - lastReceiptAtMs.get())
        if (wait > 0) delay(wait)
        lastReceiptAtMs.set(System.currentTimeMillis())
        runCatching { publishReceipt(recipientNpub, messageUuid, type) }
    }

    private suspend fun publishReceipt(recipientNpub: String, messageUuid: String, type: String) {
        val recipientPub = decode(recipientNpub) ?: return
        val myPriv = nostrIdentityManager.getKeyPair()?.privateKey ?: return
        val giftWrap = GiftWrap.wrap(
            senderPriv = myPriv,
            recipientPub = recipientPub,
            content = "",
            kind = GiftWrap.KIND_DM,
            tags = listOf(listOf(RelayControl.TAG_TYPE, type), listOf("u", messageUuid))
        )
        relayPool.publish(giftWrap)
    }

    private fun decode(npub: String): ByteArray? {
        val decoded = Bech32.decode(npub) ?: return null
        val (hrp, bytes) = decoded
        return if (hrp == "npub" && bytes.size == 32) bytes else null
    }

    companion object {
        private const val RECEIPT_INTERVAL_MS = 350L
    }
}
