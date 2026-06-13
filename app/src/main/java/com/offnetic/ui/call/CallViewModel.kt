package com.offnetic.ui.call

import android.net.wifi.WifiManager
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.local.db.dao.CallHistoryDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.entity.CallHistoryEntity
import com.offnetic.data.nearby.CallSignal
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.nearby.WebRtcManager
import com.offnetic.domain.model.CallPhase
import com.offnetic.domain.model.CallState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CallViewModel @AssistedInject constructor(
    @Assisted private val peerPublicKey: String,
    @Assisted private val webRtcManager: WebRtcManager,
    private val ncapManager: NcapManager,
    private val contactDao: ContactDao,
    private val wifiManager: WifiManager,
    private val callHistoryDao: CallHistoryDao
) {

    @AssistedFactory
    interface Factory {
        fun create(peerPublicKey: String, webRtcManager: WebRtcManager): CallViewModel
    }

    private var isIncoming: Boolean = false
    private var cameraEnabled: Boolean = false
    private var hangupRecorded: Boolean = false

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val callStateFlow = webRtcManager.getCallState(peerPublicKey)
    val callState: StateFlow<CallState> = callStateFlow.asStateFlow()

    private val _callDuration = MutableStateFlow("")
    val callDuration: StateFlow<String> = _callDuration.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _finishEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val finishEvent: SharedFlow<Unit> = _finishEvent.asSharedFlow()

    private var callStartTime: Long = 0L

    private var timeoutJob: kotlinx.coroutines.Job? = null
    private var durationTimerJob: kotlinx.coroutines.Job? = null

    fun startOutgoingCall(peerPublicKey: String, displayName: String) {
        android.util.Log.e("offCall", "startOutgoingCall $displayName")
        isIncoming = false
        callStartTime = System.currentTimeMillis()
        callStateFlow.update { it.copy(peerDisplayName = displayName, peerPublicKey = peerPublicKey, error = "") }
        internalScope.launch {
            if (!wifiManager.isWifiEnabled) {
                android.util.Log.e("offCall", "startOutgoingCall WIFI OFF")
                _toastMessage.emit("Wi-Fi required for calls")
                callStateFlow.update { it.copy(phase = CallPhase.ENDED, error = "Wi-Fi required") }
                _finishEvent.emit(Unit)
                return@launch
            }
            webRtcManager.initialize()
            observeCallSignals()
            webRtcManager.startCall(peerPublicKey, isVideo = true, displayName)
        }
    }

    fun acceptIncomingCall(peerPublicKey: String, displayName: String) {
        android.util.Log.e("offCall", "acceptIncomingCall $displayName")
        isIncoming = true
        callStartTime = System.currentTimeMillis()
        callStateFlow.update { it.copy(peerDisplayName = displayName, peerPublicKey = peerPublicKey, error = "") }
        internalScope.launch {
            if (!wifiManager.isWifiEnabled) {
                android.util.Log.e("offCall", "acceptIncomingCall WIFI OFF")
                _toastMessage.emit("Wi-Fi required for calls")
                callStateFlow.update { it.copy(phase = CallPhase.ENDED, error = "Wi-Fi required") }
                _finishEvent.emit(Unit)
                return@launch
            }
            webRtcManager.initialize()
            observeCallSignals()
            webRtcManager.enterP2pDiscoveryMode(peerPublicKey)
            callStateFlow.update { it.copy(
                phase = CallPhase.INCOMING, isVideo = true
            ) }
            launchIncomingTimeout()
        }
    }

    private fun observeCallSignals() {
        var prevPhase = callStateFlow.value.phase
        android.util.Log.e("offCall", "observeCallSignals starting prevPhase=$prevPhase")

        val pendingOffer = WebRtcManager.pendingIncomingOffers.remove(peerPublicKey)
        if (pendingOffer != null) {
            val (sdpJson, endpointId) = pendingOffer
            android.util.Log.e("offCall", "observeCallSignals applying cached CALL_OFFER for ${peerPublicKey.take(8)}")
            webRtcManager.onSdpReceived(peerPublicKey, sdpJson, endpointId)
        }

        internalScope.launch {
            callStateFlow.collect { state ->
                if (state.phase != prevPhase) {
                    android.util.Log.e("offCall", "state ${prevPhase}→${state.phase} connectedAt=${state.connectedAt} error=${state.error}")
                }
                if (state.phase == CallPhase.CONNECTED && state.connectedAt > 0) {
                    startDurationTimer(state.connectedAt)
                }
                if (state.phase == CallPhase.ENDED && prevPhase != CallPhase.ENDED && !hangupRecorded) {
                    hangupRecorded = true
                    callHistoryDao.insert(
                        CallHistoryEntity(
                            peerPublicKey = peerPublicKey,
                            type = CallHistoryEntity.TYPE_VIDEO,
                            direction = if (isIncoming) CallHistoryEntity.DIRECTION_INCOMING
                                else CallHistoryEntity.DIRECTION_OUTGOING,
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = if (prevPhase == CallPhase.CONNECTED) callDurationSeconds(state.connectedAt) else 0
                        )
                    )
                }
                prevPhase = state.phase
            }
        }
        internalScope.launch {
            ncapManager.incomingCallSignals.collect { signal ->
                if (signal.senderPublicKey == peerPublicKey) {
                    handleCallSignal(signal)
                }
            }
        }
    }

    fun acceptCall() {
        android.util.Log.e("offCall", "acceptCall currentPhase=${callStateFlow.value.phase}")
        timeoutJob?.cancel()
        callStateFlow.update { it.copy(phase = CallPhase.CONNECTING, error = "") }
        internalScope.launch { webRtcManager.acceptCall(peerPublicKey) }
    }

    fun hangup() {
        android.util.Log.e("offCall", "hangup")
        timeoutJob?.cancel()
        webRtcManager.hangup(peerPublicKey)
        internalScope.launch {
            _finishEvent.emit(Unit)
        }
    }

    fun toggleMute() {
        webRtcManager.toggleMute(peerPublicKey)
    }

    fun toggleSpeaker() {
        webRtcManager.toggleSpeaker(peerPublicKey)
    }

    fun setSpeakerOn(peerPublicKey: String) {
        webRtcManager.setSpeakerOn(peerPublicKey)
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
        webRtcManager.setCameraEnabled(peerPublicKey, enabled)
    }

    fun toggleCamera() {
        cameraEnabled = !cameraEnabled
        webRtcManager.setCameraEnabled(peerPublicKey, cameraEnabled)
    }

    fun flipCamera() {
        webRtcManager.flipCamera(peerPublicKey)
    }

    private suspend fun handleCallSignal(signal: CallSignal) {
        if (signal.type != NcapEnvelope.PayloadType.CALL_OFFER && signal.timestamp < callStartTime) {
            android.util.Log.e("offCall", "handleCallSignal SKIP stale ${signal.type} ts=${signal.timestamp} start=$callStartTime")
            return
        }
        android.util.Log.e("offCall", "handleCallSignal ${signal.type} from ${signal.senderPublicKey.take(8)} endpoint=${signal.endpointId.take(6)}")
        val payload = String(signal.payload, Charsets.UTF_8)
        when (signal.type) {
            NcapEnvelope.PayloadType.CALL_ANSWER,
            NcapEnvelope.PayloadType.CALL_OFFER ->
                webRtcManager.onSdpReceived(peerPublicKey, payload, signal.endpointId)
            NcapEnvelope.PayloadType.ICE_CANDIDATE ->
                webRtcManager.onIceCandidateReceived(peerPublicKey, payload)
            NcapEnvelope.PayloadType.CALL_HANGUP -> {
                webRtcManager.onCallHangup(peerPublicKey)
                _finishEvent.emit(Unit)
            }
            else -> Timber.w("CallViewModel: unknown signal type ${signal.type}")
        }
    }

    private fun startDurationTimer(connectedAt: Long) {
        durationTimerJob?.cancel()
        durationTimerJob = internalScope.launch {
            while (callStateFlow.value.phase == CallPhase.CONNECTED) {
                val elapsed = System.currentTimeMillis() - connectedAt
                val minutes = (elapsed / 60000).toInt()
                val seconds = ((elapsed % 60000) / 1000).toInt()
                _callDuration.value = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }

    private fun launchIncomingTimeout() {
        timeoutJob = internalScope.launch {
            delay(60_000L)
            if (callStateFlow.value.phase == CallPhase.INCOMING) {
                callStateFlow.update { it.copy(phase = CallPhase.ENDED, error = "Missed call") }
                callHistoryDao.insert(
                    CallHistoryEntity(
                        peerPublicKey = peerPublicKey,
                        type = CallHistoryEntity.TYPE_VIDEO,
                        direction = CallHistoryEntity.DIRECTION_MISSED,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0
                    )
                )
                _finishEvent.emit(Unit)
            }
        }
    }

    fun cleanup() {
        internalScope.cancel()
    }

    private fun callDurationSeconds(connectedAt: Long): Int {
        if (connectedAt == 0L) return 0
        return ((System.currentTimeMillis() - connectedAt) / 1000).toInt()
    }
}
