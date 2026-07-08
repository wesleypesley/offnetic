package com.offnetic.data.nearby

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.network.NetworkMonitor
import com.offnetic.data.relay.RelayControl
import com.offnetic.data.relay.RelayControlSender
import com.offnetic.domain.model.CallPhase
import com.offnetic.domain.model.CallState
import com.offnetic.service.IncomingCallService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

enum class CallTransport { NCAPI, RELAY }

@Singleton
class WebRtcManager(
    private val context: Context,
    private val ncapManager: NcapManager,
    private val contactDao: ContactDao,
    private val relayControlSender: RelayControlSender,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val factoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var eglBase: EglBase? = null
    @Volatile private var peerConnectionFactory: PeerConnectionFactory? = null
    @Volatile private var initialized = false

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val videoSources = ConcurrentHashMap<String, VideoSource>()
    private val audioSources = ConcurrentHashMap<String, AudioSource>()
    private val localVideoTracks = ConcurrentHashMap<String, VideoTrack>()
    private val localAudioTracks = ConcurrentHashMap<String, AudioTrack>()
    private val videoCapturers = ConcurrentHashMap<String, VideoCapturer>()
    private val remoteVideoTracks = ConcurrentHashMap<String, MutableList<VideoTrack>>()
    private val pendingRemoteRenderers = ConcurrentHashMap<String, MutableList<SurfaceViewRenderer>>()
    private val rendererTracks = ConcurrentHashMap<Int, MutableList<VideoTrack>>()
    private val localProxies = ConcurrentHashMap<String, ProxyVideoSink>()
    private val remoteProxies = ConcurrentHashMap<String, ProxyVideoSink>()

    private val _callStates = ConcurrentHashMap<String, MutableStateFlow<CallState>>()
    val callStates: Map<String, StateFlow<CallState>>
        get() = _callStates.mapValues { it.value.asStateFlow() }

    private val iceCandidateCache = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val pendingSdpOffers = ConcurrentHashMap<String, SessionDescription>()
    private val pendingOfferEndpoints = ConcurrentHashMap<String, String>()
    private val pendingSdpAnswers = ConcurrentHashMap<String, SessionDescription>()
    private val disconnectGraceJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()
    private var initDeferred = CompletableDeferred<Unit>()

    // Relay-specific state
    private val callTransports = ConcurrentHashMap<String, CallTransport>()
    private val iceGatheringDeferreds = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    companion object {
        // Keyed by peerPublicKey. Removed on cleanupPeerConnection so stale offers don't accumulate
        // indefinitely from malicious or abandoned calls (Issue #11).
        val pendingIncomingOffers = ConcurrentHashMap<String, Pair<String, String>>()

    }

    private fun wifiEnabled(): Boolean =
        (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled

    fun initialize() {
        if (initDeferred.isCompleted) {
            if (initialized) {
                android.util.Log.e("WebRTC_ICE", "initialize: already initialized, eglBase=${eglBase != null}")
                if (eglBase == null) {
                    android.util.Log.e("WebRTC_ICE", "initialize: eglBase was null! Recreating...")
                    eglBase = EglBase.create()
                }
                return
            }
            initDeferred = CompletableDeferred()
        }
        initialized = true

        try {
            eglBase = EglBase.create()
            android.util.Log.e("WebRTC_ICE", "initialize: EglBase created OK")
        } catch (e: Exception) {
            android.util.Log.e("WebRTC_ICE", "initialize: EglBase.create() FAILED: ${e.message}")
            initialized = false
            return
        }

        factoryScope.launch {
            try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setFieldTrials("WebRTC-IncludeWifiDirect/Enabled/")
                        .createInitializationOptions()
                )

                val options = PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = true
                    networkIgnoreMask = 0
                }

                val audioModule = JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioModule)
                    .setVideoEncoderFactory(
                        org.webrtc.DefaultVideoEncoderFactory(
                            eglBase!!.eglBaseContext, true, true
                        )
                    )
                    .setVideoDecoderFactory(
                        org.webrtc.DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
                    )
                    .setOptions(options)
                    .createPeerConnectionFactory()

                Timber.d("WebRTC initialized")
                initDeferred.complete(Unit)
            } catch (e: Exception) {
                Timber.e(e, "WebRTC initialization failed")
                initialized = false
                initDeferred.completeExceptionally(e)
            }
        }
    }

    private suspend fun awaitInit() {
        initDeferred.await()
    }

    fun getCallState(peerPublicKey: String): MutableStateFlow<CallState> {
        return _callStates.getOrPut(peerPublicKey) { MutableStateFlow(CallState()) }
    }

    fun startCall(peerPublicKey: String, isVideo: Boolean, peerDisplayName: String) {
        android.util.Log.e("offCall", "startCall peer=${peerPublicKey.take(8)} video=$isVideo")
        ncapManager.setCallActive(true)
        iceCandidateCache.remove(peerPublicKey)
        pendingSdpOffers.remove(peerPublicKey)
        pendingOfferEndpoints.remove(peerPublicKey)
        pendingSdpAnswers.remove(peerPublicKey)
        val state = getCallState(peerPublicKey)
        // Reset any stale terminal state from a previous call to the same peer
        if (state.value.phase == CallPhase.ENDED) state.value = CallState()
        state.update { it.copy(phase = CallPhase.OUTGOING, isVideo = isVideo, peerPublicKey = peerPublicKey, peerDisplayName = peerDisplayName, connectedAt = 0L, error = "") }

        scope.launch {
            try { awaitInit() } catch (e: Exception) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Init failed: ${e.message}") }
                return@launch
            }

            // Transport selection: NCAPI if connected, else RELAY
            val endpointIds = ncapManager.getConnectedEndpointIds(peerPublicKey)
            val transport = if (endpointIds.isNotEmpty()) CallTransport.NCAPI else CallTransport.RELAY
            callTransports[peerPublicKey] = transport
            android.util.Log.e("offCall", "startCall transport=$transport for ${peerPublicKey.take(8)}")

            if (transport == CallTransport.NCAPI && !wifiEnabled()) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Wi-Fi required") }
                return@launch
            }

            val contact = if (transport == CallTransport.RELAY) contactDao.getByPublicKey(peerPublicKey) else null
            if (transport == CallTransport.RELAY) {
                if (contact?.nostrPublicKey == null) {
                    state.update { it.copy(phase = CallPhase.ENDED, error = "Peer not reachable") }
                    return@launch
                }
                if (!networkMonitor.isOnline.value) {
                    state.update { it.copy(phase = CallPhase.ENDED, error = "Internet required") }
                    return@launch
                }
            }

            val endpointId = if (transport == CallTransport.NCAPI) endpointIds.first() else ""
            android.util.Log.e("offCall", "startCall endpoint=$endpointId")

            val pc = createPeerConnection(peerPublicKey, isVideo, transport) ?: run {
                android.util.Log.e("offCall", "startCall PEER CONNECTION FAILED")
                state.update { it.copy(phase = CallPhase.ENDED, error = "Failed to create connection") }
                return@launch
            }

            if (transport == CallTransport.RELAY) {
                iceGatheringDeferreds[peerPublicKey] = CompletableDeferred()
            }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

            pc.createOffer(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            scope.launch createOffer@{
                                when (transport) {
                                    CallTransport.NCAPI -> {
                                        val json = JSONObject().apply {
                                            put("type", sdp.type.canonicalForm())
                                            put("sdp", sdp.description)
                                        }
                                        applyCachedAnswer(pc, peerPublicKey, state)
                                        ncapManager.sendCallSignal(
                                            endpointId,
                                            NcapEnvelope.PayloadType.CALL_OFFER,
                                            json.toString().toByteArray(Charsets.UTF_8)
                                        )
                                        android.util.Log.e("offCall", "CALL_OFFER sent to $endpointId")
                                        injectP2pCandidate(endpointId, sdp.description)
                                    }
                                    CallTransport.RELAY -> {
                                        // Non-trickle: wait for full ICE gather (up to 8s) then send complete SDP
                                        withTimeoutOrNull(8_000L) { iceGatheringDeferreds[peerPublicKey]?.await() }
                                        val finalDesc = pc.localDescription
                                        val offerJson = JSONObject().apply {
                                            put("type", finalDesc?.type?.canonicalForm() ?: sdp.type.canonicalForm())
                                            put("sdp", finalDesc?.description ?: sdp.description)
                                            put("video", isVideo)
                                        }
                                        val sent = relayControlSender.sendCallSignal(
                                            contact!!.nostrPublicKey!!,
                                            RelayControl.TYPE_CALL_OFFER,
                                            offerJson.toString()
                                        )
                                        if (!sent) {
                                            state.update { it.copy(phase = CallPhase.ENDED, error = "Couldn't reach relay") }
                                            cleanupPeerConnection(peerPublicKey)
                                            return@createOffer
                                        }
                                        android.util.Log.e("offCall", "CALL_OFFER sent via relay to ${contact.nostrPublicKey!!.take(8)}")
                                    }
                                }
                                state.update { it.copy(phase = CallPhase.CONNECTING) }
                                launchCallTimeout(peerPublicKey, state)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            state.update { it.copy(phase = CallPhase.ENDED, error = "Local SDP failed") }
                        }
                    }, sdp)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(err: String) {
                    Timber.e("startCall: createOffer failed for ${peerPublicKey.take(8)} — $err")
                    state.update { it.copy(phase = CallPhase.ENDED, error = err) }
                }
                override fun onSetFailure(err: String) {}
            }, constraints)
        }
    }

    fun acceptCall(peerPublicKey: String) {
        android.util.Log.e("offCall", "acceptCall peer=${peerPublicKey.take(8)}")
        ncapManager.setCallActive(true)
        iceCandidateCache.remove(peerPublicKey)
        val state = getCallState(peerPublicKey)
        val isVideo = state.value.isVideo
        state.update { it.copy(error = "") }

        scope.launch {
            try { awaitInit() } catch (e: Exception) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Init failed: ${e.message}") }
                return@launch
            }

            val transport = callTransports[peerPublicKey] ?: CallTransport.NCAPI

            val endpointId = if (transport == CallTransport.RELAY) {
                "" // relay: no endpoint needed
            } else {
                val id = pendingOfferEndpoints.remove(peerPublicKey)
                    ?: ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull()
                if (id == null) {
                    android.util.Log.e("offCall", "acceptCall NO ENDPOINT")
                    state.update { it.copy(phase = CallPhase.ENDED, error = "Peer disconnected") }
                    return@launch
                }
                id
            }

            if (transport == CallTransport.NCAPI && !wifiEnabled()) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Wi-Fi required") }
                return@launch
            }

            val pc = createPeerConnection(peerPublicKey, isVideo, transport) ?: run {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Failed to create connection") }
                return@launch
            }

            if (transport == CallTransport.RELAY) {
                iceGatheringDeferreds[peerPublicKey] = CompletableDeferred()
            }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

            val cachedOffer = pendingSdpOffers.remove(peerPublicKey)
            if (cachedOffer != null) {
                pc.setRemoteDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        createAndSendAnswer(pc, peerPublicKey, endpointId, constraints, state, transport)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String) {
                        state.update { it.copy(phase = CallPhase.ENDED, error = "setRemoteDescription: $err") }
                    }
                }, cachedOffer)
            } else {
                Timber.w("acceptCall: no cached SDP offer for ${peerPublicKey.take(8)}")
                state.update { it.copy(phase = CallPhase.ENDED, error = "No SDP offer received") }
                cleanupPeerConnection(peerPublicKey)
            }
        }
    }

    // Caches an incoming relay call offer and rings the device (C5/C7/C8)
    fun cacheRelayCallOffer(peer: String, sdpJson: String) = scope.launch {
        // C7 glare guard: ignore if we already have a PC or pending offer for this peer
        if (peerConnections.containsKey(peer) || pendingSdpOffers.containsKey(peer)) {
            android.util.Log.e("offCall", "cacheRelayCallOffer: glare guard dropped for ${peer.take(8)}")
            return@launch
        }
        callTransports[peer] = CallTransport.RELAY
        val isVideo = runCatching { JSONObject(sdpJson).optBoolean("video", false) }.getOrDefault(false)
        getCallState(peer).update { it.copy(isVideo = isVideo) }
        onSdpReceived(peer, sdpJson, "") // stores in pendingSdpOffers + sets INCOMING phase
        if (!ncapManager.isCallActive) {
            context.startForegroundService(
                Intent(context, IncomingCallService::class.java)
                    .setAction(IncomingCallService.ACTION_START_RINGING)
                    .putExtra(IncomingCallService.EXTRA_PEER_PUBLIC_KEY, peer)
            )
        }
    }

    // Single relay call signal intake — no VM collector for relay (C2)
    fun onRelayCallSignal(peer: String, type: String, payload: String) = scope.launch {
        android.util.Log.e("offCall", "onRelayCallSignal type=$type peer=${peer.take(8)}")
        when (type) {
            RelayControl.TYPE_CALL_ANSWER -> onSdpReceived(peer, payload, "")
            RelayControl.TYPE_ICE_CANDIDATE -> onIceCandidateReceived(peer, payload)
            RelayControl.TYPE_CALL_HANGUP -> {
                context.startService(
                    Intent(context, IncomingCallService::class.java)
                        .setAction(IncomingCallService.ACTION_STOP_RINGING)
                )
                onCallHangup(peer)
            }
        }
    }

    private fun applyCachedAnswer(
        pc: PeerConnection,
        peerPublicKey: String,
        state: MutableStateFlow<CallState>
    ) {
        val cachedAnswer = pendingSdpAnswers.remove(peerPublicKey) ?: return
        android.util.Log.e("WebRTC_ICE", "applyCachedAnswer: applying cached ANSWER for ${peerPublicKey.take(8)}")
        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                android.util.Log.e("WebRTC_ICE", "applyCachedAnswer: ANSWER set OK for ${peerPublicKey.take(8)}")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String) {
                android.util.Log.e("WebRTC_ICE", "applyCachedAnswer: setRemoteDescription failed: $err")
                state.update { it.copy(phase = CallPhase.ENDED, error = err) }
            }
        }, cachedAnswer)
    }

    private fun createAndSendAnswer(
        pc: PeerConnection,
        peerPublicKey: String,
        endpointId: String,
        constraints: MediaConstraints,
        state: MutableStateFlow<CallState>,
        transport: CallTransport = CallTransport.NCAPI
    ) {
        pc.createAnswer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        scope.launch {
                            when (transport) {
                                CallTransport.NCAPI -> {
                                    val json = JSONObject().apply {
                                        put("type", sdp.type.canonicalForm())
                                        put("sdp", sdp.description)
                                    }
                                    ncapManager.sendCallSignal(
                                        endpointId,
                                        NcapEnvelope.PayloadType.CALL_ANSWER,
                                        json.toString().toByteArray(Charsets.UTF_8)
                                    )
                                    android.util.Log.e("offCall", "CALL_ANSWER sent to $endpointId")
                                    injectP2pCandidate(endpointId, sdp.description)
                                    state.update { it.copy(phase = CallPhase.CONNECTING) }
                                    iceCandidateCache[peerPublicKey]?.forEach { sendIceCandidate(endpointId, it) }
                                    iceCandidateCache.remove(peerPublicKey)
                                }
                                CallTransport.RELAY -> {
                                    withTimeoutOrNull(8_000L) { iceGatheringDeferreds[peerPublicKey]?.await() }
                                    val finalDesc = pc.localDescription
                                    val answerJson = JSONObject().apply {
                                        put("type", finalDesc?.type?.canonicalForm() ?: sdp.type.canonicalForm())
                                        put("sdp", finalDesc?.description ?: sdp.description)
                                    }
                                    val npub = contactDao.getByPublicKey(peerPublicKey)?.nostrPublicKey
                                    if (npub != null) {
                                        val sent = relayControlSender.sendCallSignal(
                                            npub, RelayControl.TYPE_CALL_ANSWER, answerJson.toString()
                                        )
                                        if (!sent) {
                                            state.update { it.copy(phase = CallPhase.ENDED, error = "Couldn't reach relay") }
                                            cleanupPeerConnection(peerPublicKey)
                                            return@launch
                                        }
                                        android.util.Log.e("offCall", "CALL_ANSWER sent via relay to ${npub.take(8)}")
                                    }
                                    state.update { it.copy(phase = CallPhase.CONNECTING) }
                                }
                            }
                        }
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        state.update { it.copy(phase = CallPhase.ENDED, error = "Local answer failed") }
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String) {
                Timber.e("createAndSendAnswer: createAnswer failed for ${peerPublicKey.take(8)} — $err")
                state.update { it.copy(phase = CallPhase.ENDED, error = err) }
            }
            override fun onSetFailure(err: String) {}
        }, constraints)
    }

    fun onSdpReceived(peerPublicKey: String, sdpJson: String, endpointId: String = "") {
        val json = try { JSONObject(sdpJson) } catch (e: Exception) {
            Timber.w(e, "onSdpReceived: bad JSON for ${peerPublicKey.take(8)}"); return
        }
        val type = json.optString("type")
        val sdpString = json.optString("sdp")
        if (sdpString.isEmpty()) {
            Timber.w("onSdpReceived: empty sdp for ${peerPublicKey.take(8)}"); return
        }

        val sdpType = when (type) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> SessionDescription.Type.OFFER
        }

        val sdp = SessionDescription(sdpType, sdpString)
        val state = getCallState(peerPublicKey)

        val pc = peerConnections[peerPublicKey]
        if (pc == null) {
            if (sdpType == SessionDescription.Type.OFFER) {
                pendingSdpOffers[peerPublicKey] = sdp
                if (endpointId.isNotEmpty()) {
                    pendingOfferEndpoints[peerPublicKey] = endpointId
                }
                state.update { it.copy(phase = CallPhase.INCOMING) }
                Timber.d("SDP offer cached for ${peerPublicKey.take(8)} (waiting for acceptCall)")
            }
            return
        }

        val sigState = pc.signalingState()
        android.util.Log.e("WebRTC_ICE", "onSdpReceived: setting remote ${sdp.type} for ${peerPublicKey.take(8)} sigState=$sigState")

        if (sdp.type == SessionDescription.Type.OFFER && sigState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.e("WebRTC_ICE", "onSdpReceived: stale PC in HAVE_LOCAL_OFFER — discarding and caching new offer")
            peerConnections.remove(peerPublicKey)?.let { it.close(); it.dispose() }
            pendingSdpOffers[peerPublicKey] = sdp
            if (endpointId.isNotEmpty()) pendingOfferEndpoints[peerPublicKey] = endpointId
            state.update { it.copy(phase = CallPhase.INCOMING, error = "") }
            return
        }

        if (sdp.type == SessionDescription.Type.ANSWER && sigState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.e("WebRTC_ICE", "onSdpReceived: caching ANSWER (sigState=$sigState)")
            pendingSdpAnswers[peerPublicKey] = sdp
            return
        }
        if (sdp.type == SessionDescription.Type.OFFER && sigState != PeerConnection.SignalingState.STABLE && sigState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.e("WebRTC_ICE", "onSdpReceived: ignoring OFFER, sigState=$sigState")
            return
        }

        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Timber.d("onSdpReceived: ${sdp.type} set for ${peerPublicKey.take(8)}")
                if (sdp.type == SessionDescription.Type.OFFER) {
                    state.update { it.copy(phase = CallPhase.INCOMING) }
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String) {
                android.util.Log.e("WebRTC_ICE", "setRemoteDescription failed: $err")
                state.update { it.copy(phase = CallPhase.ENDED, error = err) }
            }
        }, sdp)
    }

    fun onIceCandidateReceived(peerPublicKey: String, candidateJson: String) {
        val json = try { JSONObject(candidateJson) } catch (e: Exception) {
            Timber.w(e, "onIceCandidateReceived: bad JSON for ${peerPublicKey.take(8)}"); return
        }
        val sdp = json.optString("sdp", "")
        if (sdp.isEmpty()) return
        val candidate = IceCandidate(json.optString("sdpMid", ""), json.optInt("sdpMLineIndex", 0), sdp)

        if (!peerConnections.containsKey(peerPublicKey)) {
            iceCandidateCache.getOrPut(peerPublicKey) { mutableListOf() }.add(candidate)
            return
        }
        peerConnections[peerPublicKey]?.addIceCandidate(candidate)
    }

    fun onCallHangup(peerPublicKey: String) {
        Timber.d("onCallHangup: remote hangup for ${peerPublicKey.take(8)}")
        val state = getCallState(peerPublicKey)
        state.update { it.copy(phase = CallPhase.ENDED) }
        cleanupPeerConnection(peerPublicKey)
    }

    fun hangup(peerPublicKey: String) {
        Timber.d("hangup: local hangup for ${peerPublicKey.take(8)}")
        sendOnDataChannel(peerPublicKey, JSONObject().apply { put("hangup", true) }.toString())
        val transport = callTransports[peerPublicKey]
        scope.launch {
            when (transport) {
                CallTransport.RELAY -> {
                    val npub = contactDao.getByPublicKey(peerPublicKey)?.nostrPublicKey
                    if (npub != null) {
                        relayControlSender.sendCallSignal(npub, RelayControl.TYPE_CALL_HANGUP, "")
                        android.util.Log.e("offCall", "CALL_HANGUP sent via relay to ${npub.take(8)}")
                    }
                }
                else -> {
                    val endpointId = ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull()
                    if (endpointId != null) {
                        ncapManager.sendCallSignal(endpointId, NcapEnvelope.PayloadType.CALL_HANGUP, ByteArray(0))
                    }
                }
            }
        }
        val state = getCallState(peerPublicKey)
        state.update { it.copy(phase = CallPhase.ENDED) }
        cleanupPeerConnection(peerPublicKey)
    }

    fun toggleMute(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val isMuted = !state.value.isMuted
        state.update { it.copy(isMuted = isMuted) }
        localAudioTracks[peerPublicKey]?.setEnabled(!isMuted)
        sendOnDataChannel(peerPublicKey, JSONObject().apply { put("mute", isMuted) }.toString())
    }

    fun toggleSpeaker(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val speakerOn = !state.value.isSpeakerOn
        state.update { it.copy(isSpeakerOn = speakerOn) }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            android.util.Log.e("offCall", "toggleSpeaker failed: ${e.message}")
        }
    }

    fun setSpeakerOn(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        state.update { it.copy(isSpeakerOn = true) }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            android.util.Log.e("offCall", "setSpeakerOn failed: ${e.message}")
        }
    }

    fun setSpeakerOff(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        state.update { it.copy(isSpeakerOn = false) }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            android.util.Log.e("offCall", "setSpeakerOff failed: ${e.message}")
        }
    }

    fun toggleCamera(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val isCameraOn = !state.value.isCameraOn
        state.update { it.copy(isCameraOn = isCameraOn) }
        localVideoTracks[peerPublicKey]?.setEnabled(isCameraOn)
    }

    fun setCameraEnabled(peerPublicKey: String, enabled: Boolean) {
        val state = getCallState(peerPublicKey)
        if (state.value.isCameraOn == enabled) return
        android.util.Log.e("offCall", "setCameraEnabled $enabled capturer=${videoCapturers[peerPublicKey] != null}")
        state.update { it.copy(isCameraOn = enabled) }
        localVideoTracks[peerPublicKey]?.setEnabled(enabled)
        if (enabled) {
            videoCapturers[peerPublicKey]?.startCapture(640, 480, 30)
        } else {
            videoCapturers[peerPublicKey]?.stopCapture()
        }
        sendOnDataChannel(peerPublicKey, JSONObject().apply { put("camera", enabled) }.toString())
    }

    fun flipCamera(peerPublicKey: String) {
        val capturer = videoCapturers[peerPublicKey] as? CameraVideoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                getCallState(peerPublicKey).update { it.copy(isFrontCamera = isFrontCamera) }
            }
            override fun onCameraSwitchError(error: String) {
                android.util.Log.e("offCall", "Camera switch failed: $error")
            }
        })
    }

    fun initSurface(renderer: SurfaceViewRenderer) {
        val ctx = eglBase?.eglBaseContext
        android.util.Log.e("VideoUI", "initSurface: eglBase=${ctx != null} renderer=${renderer.hashCode()}")
        renderer.init(ctx, null)
    }

    fun attachLocalVideo(peerPublicKey: String, renderer: SurfaceViewRenderer) {
        val track = localVideoTracks[peerPublicKey]
        android.util.Log.e("offCall", "attachLocalVideo track=${track != null} enabled=${track?.enabled()} renderer=${renderer.hashCode()}")
        if (track != null) {
            removeRendererSinks(renderer)
            track.addSink(renderer)
            rendererTracks.getOrPut(System.identityHashCode(renderer)) { mutableListOf() }.add(track)
        }
    }

    fun attachRemoteVideo(peerPublicKey: String, renderer: SurfaceViewRenderer) {
        synchronized(pendingRemoteRenderers) {
            val tracks = remoteVideoTracks[peerPublicKey]
            android.util.Log.e("offCall", "attachRemoteVideo tracks=${tracks?.size ?: 0} renderer=${renderer.hashCode()}")
            if (tracks != null && tracks.isNotEmpty()) {
                removeRendererSinks(renderer)
                tracks.forEach {
                    it.addSink(renderer)
                    rendererTracks.getOrPut(System.identityHashCode(renderer)) { mutableListOf() }.add(it)
                }
            } else {
                pendingRemoteRenderers.getOrPut(peerPublicKey) { mutableListOf() }.add(renderer)
            }
        }
    }

    private fun removeRendererSinks(renderer: SurfaceViewRenderer) {
        rendererTracks.remove(System.identityHashCode(renderer))?.forEach { it.removeSink(renderer) }
    }

    fun releaseSurface(renderer: SurfaceViewRenderer) {
        renderer.release()
    }

    fun bindVideoProxies(peerPublicKey: String) {
        val localProxy = localProxies.getOrPut(peerPublicKey) { ProxyVideoSink() }
        val remoteProxy = remoteProxies.getOrPut(peerPublicKey) { ProxyVideoSink() }
        localVideoTracks[peerPublicKey]?.addSink(localProxy)
        remoteVideoTracks[peerPublicKey]?.forEach { it.addSink(remoteProxy) }
    }

    fun swapVideoFeeds(peerPublicKey: String, localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        localProxies[peerPublicKey]?.setTarget(localRenderer)
        remoteProxies[peerPublicKey]?.setTarget(remoteRenderer)
    }

    private fun createPeerConnection(peerPublicKey: String, isVideo: Boolean, transport: CallTransport = CallTransport.NCAPI): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val egl = eglBase ?: return null

        val iceServers = if (transport == CallTransport.RELAY) {
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
        } else {
            emptyList()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = if (transport == CallTransport.RELAY)
                PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            else
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        val pc = factory.createPeerConnection(rtcConfig, createPeerConnectionObserver(peerPublicKey)) ?: return null
        peerConnections[peerPublicKey] = pc

        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannels.remove(peerPublicKey)?.let { it.close(); it.dispose() }
        val dc = pc.createDataChannel("callControl", dcInit)
        dataChannels[peerPublicKey] = dc
        dc.registerObserver(createDataChannelObserver(peerPublicKey))

        val audioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("audio_$peerPublicKey", audioSource)
        audioSources[peerPublicKey] = audioSource
        localAudioTracks[peerPublicKey] = audioTrack
        pc.addTrack(audioTrack)

        if (isVideo) {
            val videoCapturer = createVideoCapturer()
            if (videoCapturer != null) {
                val surfaceTextureHelper = SurfaceTextureHelper.create("video_$peerPublicKey", egl.eglBaseContext)
                surfaceTextureHelpers[peerPublicKey]?.dispose()
                surfaceTextureHelpers[peerPublicKey] = surfaceTextureHelper
                val videoSource = factory.createVideoSource(videoCapturer.isScreencast)
                activeVideoPeer = peerPublicKey
                activeSurfaceTextureHelper = surfaceTextureHelper
                activeVideoSource = videoSource
                videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                val videoTrack = factory.createVideoTrack("video_$peerPublicKey", videoSource)
                localVideoTracks[peerPublicKey] = videoTrack
                videoSources[peerPublicKey] = videoSource
                videoCapturers[peerPublicKey] = videoCapturer
                pc.addTrack(videoTrack)
            }
        }

        return pc
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val handler = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(error: String) { scope.launch { fallbackToCamera1() } }
            override fun onCameraDisconnected() { onCameraError("disconnected") }
            override fun onCameraFreezed(error: String) { onCameraError("freezed: $error") }
            override fun onCameraOpening(cameraName: String) {}
            override fun onFirstFrameAvailable() {}
            override fun onCameraClosed() {}
        }
        if (Camera2Enumerator.isSupported(context)) {
            val enumerator = Camera2Enumerator(context)
            val names = enumerator.deviceNames.toList()
            val name = names.find { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
            if (name != null) return enumerator.createCapturer(name, handler)
        }
        val enumerator = Camera1Enumerator(true)
        val names = enumerator.deviceNames.toList()
        return if (names.isNotEmpty()) enumerator.createCapturer(names[0], handler) else null
    }

    private var activeVideoPeer: String? = null
    private var activeSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var activeVideoSource: VideoSource? = null

    private fun fallbackToCamera1() {
        val peerKey = activeVideoPeer ?: return
        val stHelper = activeSurfaceTextureHelper ?: return
        val vSource = activeVideoSource ?: return
        android.util.Log.e("WebRTC_ICE", "fallbackToCamera1 for $peerKey")
        videoCapturers[peerKey]?.stopCapture()
        val enumerator = Camera1Enumerator(true)
        val deviceNames = enumerator.deviceNames
        if (deviceNames.isNotEmpty()) {
            val newCapturer = enumerator.createCapturer(deviceNames[0], object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(error: String) {}
                override fun onCameraDisconnected() {}
                override fun onCameraFreezed(error: String) {}
                override fun onCameraOpening(cameraName: String) {}
                override fun onFirstFrameAvailable() {}
                override fun onCameraClosed() {}
            })
            newCapturer?.let {
                it.initialize(stHelper, context, vSource.capturerObserver)
                it.startCapture(640, 480, 30)
                videoCapturers[peerKey] = it
            }
        }
    }

    private val surfaceTextureHelpers = ConcurrentHashMap<String, SurfaceTextureHelper>()

    private fun createPeerConnectionObserver(peerPublicKey: String): PeerConnection.Observer {
        val state = getCallState(peerPublicKey)
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                android.util.Log.e("WebRTC_ICE", "onIceCandidate ${peerPublicKey.take(8)}: ${candidate.sdp}")
                scope.launch { sendIceCandidateToPeer(peerPublicKey, candidate) }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                android.util.Log.e("WebRTC_ICE", "ICE state: $iceConnectionState for ${peerPublicKey.take(8)}")
                when (iceConnectionState) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
                        state.update { it.copy(phase = CallPhase.CONNECTED, connectedAt = System.currentTimeMillis()) }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
                        disconnectGraceJobs[peerPublicKey] = scope.launch {
                            kotlinx.coroutines.delay(15_000L)
                            if (state.value.phase != CallPhase.ENDED) {
                                state.update { it.copy(phase = CallPhase.ENDED) }
                                cleanupPeerConnection(peerPublicKey)
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (state.value.phase != CallPhase.ENDED) {
                            state.update { it.copy(phase = CallPhase.ENDED) }
                            cleanupPeerConnection(peerPublicKey)
                        }
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(gatheringState: PeerConnection.IceGatheringState) {
                android.util.Log.e("WebRTC_ICE", "ICE gathering: $gatheringState for ${peerPublicKey.take(8)}")
                if (gatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                    // Unblocks the 8s await in startCall/createAndSendAnswer for relay transport
                    iceGatheringDeferreds.remove(peerPublicKey)?.complete(Unit)
                }
            }
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {
                android.util.Log.e("WebRTC_ICE", "onDataChannel for ${peerPublicKey.take(8)}")
                dataChannels.remove(peerPublicKey)?.let { it.close(); it.dispose() }
                dataChannels[peerPublicKey] = channel
                channel.registerObserver(createDataChannelObserver(peerPublicKey))
            }
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    android.util.Log.e("offCall", "onAddTrack kind=${track.kind()} for ${peerPublicKey.take(8)}")
                    if (track is VideoTrack) {
                        remoteVideoTracks.getOrPut(peerPublicKey) { mutableListOf() }.add(track)
                        remoteProxies[peerPublicKey]?.let { track.addSink(it) }
                        synchronized(pendingRemoteRenderers) {
                            pendingRemoteRenderers[peerPublicKey]?.forEach { track.addSink(it) }
                            pendingRemoteRenderers.remove(peerPublicKey)
                        }
                    }
                }
            }
            override fun onRemoveTrack(p0: org.webrtc.RtpReceiver?) {
                val track = p0?.track() as? VideoTrack ?: return
                remoteVideoTracks[peerPublicKey]?.remove(track)
                remoteProxies[peerPublicKey]?.let { track.removeSink(it) }
            }
        }
    }

    private fun createDataChannelObserver(peerPublicKey: String): DataChannel.Observer {
        val state = getCallState(peerPublicKey)
        return object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                val dc = dataChannels[peerPublicKey] ?: return
                android.util.Log.e("WebRTC_ICE", "DataChannel state: ${dc.state()} for ${peerPublicKey.take(8)}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                try {
                    val json = JSONObject(String(data))
                    if (json.has("camera")) state.update { it.copy(isRemoteCameraOn = json.getBoolean("camera")) }
                    if (json.has("mute")) state.update { it.copy(isMuted = json.getBoolean("mute")) }
                    if (json.has("hangup")) { state.update { it.copy(phase = CallPhase.ENDED) }; cleanupPeerConnection(peerPublicKey) }
                } catch (e: Exception) {
                    android.util.Log.e("WebRTC_ICE", "DataChannel parse error: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendIceCandidateToPeer(peerPublicKey: String, candidate: IceCandidate) {
        // Non-trickle ICE for relay: skip individual candidates (bundled in SDP after gather completes)
        if (callTransports[peerPublicKey] == CallTransport.RELAY) return
        val endpointId = ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull() ?: return
        val json = JSONObject().apply {
            put("sdp", candidate.sdp); put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        ncapManager.sendCallSignal(endpointId, NcapEnvelope.PayloadType.ICE_CANDIDATE, json.toString().toByteArray(Charsets.UTF_8))
    }

    private suspend fun sendIceCandidate(endpointId: String, candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("sdp", candidate.sdp); put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        ncapManager.sendCallSignal(endpointId, NcapEnvelope.PayloadType.ICE_CANDIDATE, json.toString().toByteArray(Charsets.UTF_8))
    }

    private suspend fun injectP2pCandidate(endpointId: String, localSdp: String) {
        val p2pIp = try {
            java.net.NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.find { it.displayName == "p2p0" }
                ?.inetAddresses?.asSequence()
                ?.find { it.hostAddress?.startsWith("192.168.49.") == true }
                ?.hostAddress
        } catch (e: Exception) { null }
        if (p2pIp == null) return
        val ufrag = Regex("a=ice-ufrag:(\\S+)").find(localSdp)?.groupValues?.get(1) ?: return
        val foundation = Math.abs(p2pIp.hashCode()) % 2_000_000_000
        val sdp = "candidate:${foundation} 1 udp 2122252543 $p2pIp 58000 typ host generation 0 ufrag $ufrag"
        scope.launch {
            ncapManager.sendCallSignal(
                endpointId, NcapEnvelope.PayloadType.ICE_CANDIDATE,
                JSONObject().apply { put("sdp", sdp); put("sdpMid", "0"); put("sdpMLineIndex", 0) }
                    .toString().toByteArray(Charsets.UTF_8)
            )
        }
    }

    private fun sendOnDataChannel(peerPublicKey: String, message: String): Boolean {
        val channel = dataChannels[peerPublicKey]
        if (channel == null || channel.state() != DataChannel.State.OPEN) return false
        return try {
            channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(message.toByteArray()), false))
        } catch (e: Exception) { false }
    }

    private fun cleanupPeerConnection(peerPublicKey: String) {
        Timber.d("cleanupPeerConnection: ${peerPublicKey.take(8)}")
        localProxies[peerPublicKey]?.setTarget(null)
        remoteProxies[peerPublicKey]?.setTarget(null)
        ncapManager.setCallActive(false)
        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
        callTransports.remove(peerPublicKey)
        iceGatheringDeferreds.remove(peerPublicKey)?.complete(Unit)
        dataChannels.remove(peerPublicKey)?.let { it.close(); it.dispose() }
        peerConnections.remove(peerPublicKey)?.let { it.close(); it.dispose() }
        videoCapturers.remove(peerPublicKey)?.let {
            try { it.stopCapture() } catch (e: Exception) { Timber.w(e, "stopCapture failed") }
            it.dispose()
        }
        surfaceTextureHelpers.remove(peerPublicKey)?.dispose()
        videoSources.remove(peerPublicKey)?.dispose()
        audioSources.remove(peerPublicKey)?.dispose()
        localVideoTracks.remove(peerPublicKey)
        localAudioTracks.remove(peerPublicKey)
        remoteVideoTracks.remove(peerPublicKey)
        pendingRemoteRenderers.remove(peerPublicKey)
        pendingOfferEndpoints.remove(peerPublicKey)
        pendingSdpOffers.remove(peerPublicKey)
        pendingSdpAnswers.remove(peerPublicKey)
        iceCandidateCache.remove(peerPublicKey)
        pendingIncomingOffers.remove(peerPublicKey)
    }

    private fun launchCallTimeout(peerPublicKey: String, state: MutableStateFlow<CallState>) {
        scope.launch {
            kotlinx.coroutines.delay(60_000L)
            if (state.value.phase == CallPhase.CONNECTING || state.value.phase == CallPhase.OUTGOING) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Call timed out") }
                hangup(peerPublicKey)
            }
        }
    }

    fun destroy() {
        val peers = (peerConnections.keys + dataChannels.keys + videoCapturers.keys +
            videoSources.keys + audioSources.keys).toSet()
        peers.forEach { cleanupPeerConnection(it) }
        disconnectGraceJobs.values.forEach { it.cancel() }
        disconnectGraceJobs.clear()
        localVideoTracks.clear()
        localAudioTracks.clear()
        remoteVideoTracks.clear()
        iceCandidateCache.clear()
        _callStates.clear()
        localProxies.clear()
        remoteProxies.clear()
        rendererTracks.clear()
        peerConnectionFactory?.dispose()
        eglBase?.release()
        initialized = false
    }
}
