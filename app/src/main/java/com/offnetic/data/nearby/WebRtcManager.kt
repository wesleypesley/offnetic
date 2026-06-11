package com.offnetic.data.nearby

import android.content.Context
import com.offnetic.data.crypto.NcapEnvelope
import com.offnetic.domain.model.CallPhase
import com.offnetic.domain.model.CallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class WebRtcManager(
    private val context: Context,
    private val ncapManager: NcapManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val factoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var initialized = false

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

    companion object {
        val pendingIncomingOffers = ConcurrentHashMap<String, Pair<String, String>>()
    }

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
                        .setFieldTrials("")
                        .createInitializationOptions()
                )

                val options = PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = true
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
        return _callStates.getOrPut(peerPublicKey) {
            MutableStateFlow(CallState())
        }
    }

    fun startCall(peerPublicKey: String, isVideo: Boolean, peerDisplayName: String) {
        android.util.Log.e("offCall", "startCall peer=${peerPublicKey.take(8)} video=$isVideo")
        iceCandidateCache.remove(peerPublicKey)
        pendingSdpOffers.remove(peerPublicKey)
        pendingOfferEndpoints.remove(peerPublicKey)
        pendingSdpAnswers.remove(peerPublicKey)
        val state = getCallState(peerPublicKey)
        state.update { it.copy(phase = CallPhase.OUTGOING, isVideo = isVideo, peerPublicKey = peerPublicKey, peerDisplayName = peerDisplayName, connectedAt = 0L) }

        scope.launch {
            try {
                awaitInit()
            } catch (e: Exception) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Init failed: ${e.message}") }
                return@launch
            }
            val endpointIds = ncapManager.getConnectedEndpointIds(peerPublicKey)
            if (endpointIds.isEmpty()) {
                android.util.Log.e("offCall", "startCall NO ENDPOINT for ${peerPublicKey.take(8)}")
                state.update { it.copy(phase = CallPhase.ENDED, error = "Peer not connected") }
                return@launch
            }
            val endpointId = endpointIds.first()
            android.util.Log.e("offCall", "startCall endpoint=$endpointId")

            val pc = createPeerConnection(peerPublicKey, isVideo)
                ?: run {
                    android.util.Log.e("offCall", "startCall PEER CONNECTION FAILED")
                    state.update { it.copy(phase = CallPhase.ENDED, error = "Failed to create connection") }
                    return@launch
                }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isVideo) {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            pc.createOffer(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            val json = JSONObject().apply {
                                put("type", sdp.type.canonicalForm())
                                put("sdp", sdp.description)
                            }
                            applyCachedAnswer(pc, peerPublicKey, endpointId, state)
                            scope.launch {
                                ncapManager.sendCallSignal(
                                    endpointId,
                                    NcapEnvelope.PayloadType.CALL_OFFER,
                                    json.toString().toByteArray(Charsets.UTF_8)
                                )
                                android.util.Log.e("offCall", "CALL_OFFER sent to $endpointId")
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
                    Timber.e("startCall: createOffer failed for ${peerPublicKey.take(8)}... — $err")
                    state.update { it.copy(phase = CallPhase.ENDED, error = err) }
                }
                override fun onSetFailure(err: String) {}
            }, constraints)
        }
    }

    fun acceptCall(peerPublicKey: String) {
        android.util.Log.e("offCall", "acceptCall peer=${peerPublicKey.take(8)}")
        iceCandidateCache.remove(peerPublicKey)
        val state = getCallState(peerPublicKey)
        val currentState = state.value
        val isVideo = currentState.isVideo

        scope.launch {
            try {
                awaitInit()
            } catch (e: Exception) {
                state.update { it.copy(phase = CallPhase.ENDED, error = "Init failed: ${e.message}") }
                return@launch
            }
            val endpointId = pendingOfferEndpoints.remove(peerPublicKey)
                ?: ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull()
            if (endpointId == null) {
                android.util.Log.e("offCall", "acceptCall NO ENDPOINT (pendingOfferEndpoints empty, getConnectedEndpointIds empty)")
                android.util.Log.e("CallVM", "acceptCall: peer NOT connected (found=${
                    ncapManager.peers.value.any { p -> p.publicKey == peerPublicKey }
                })")
                state.update { it.copy(phase = CallPhase.ENDED, error = "Peer disconnected") }
                return@launch
            }

            val pc = createPeerConnection(peerPublicKey, isVideo)
                ?: run {
                    state.update { it.copy(phase = CallPhase.ENDED, error = "Failed to create connection") }
                    return@launch
                }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isVideo) {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            val cachedOffer = pendingSdpOffers.remove(peerPublicKey)
            if (cachedOffer != null) {
                pc.setRemoteDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        createAndSendAnswer(pc, peerPublicKey, endpointId, isVideo, constraints, state)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String) {
                        state.update { it.copy(phase = CallPhase.ENDED, error = "setRemoteDescription: $err") }
                    }
                }, cachedOffer)
            } else {
                Timber.w("acceptCall: no cached SDP offer for ${peerPublicKey.take(8)}..., cannot create answer")
                state.update { it.copy(phase = CallPhase.ENDED, error = "No SDP offer received") }
                cleanupPeerConnection(peerPublicKey)
            }
        }
    }

    private fun applyCachedAnswer(
        pc: PeerConnection,
        peerPublicKey: String,
        endpointId: String,
        state: MutableStateFlow<CallState>
    ) {
        val cachedAnswer = pendingSdpAnswers.remove(peerPublicKey) ?: return
        android.util.Log.e("WebRTC_ICE", "applyCachedAnswer: applying cached ANSWER for ${peerPublicKey.take(8)}...")
        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                android.util.Log.e("WebRTC_ICE", "applyCachedAnswer: ANSWER set OK for ${peerPublicKey.take(8)}...")
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
        isVideo: Boolean,
        constraints: MediaConstraints,
        state: MutableStateFlow<CallState>
    ) {
        pc.createAnswer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        val json = JSONObject().apply {
                            put("type", sdp.type.canonicalForm())
                            put("sdp", sdp.description)
                        }
                        scope.launch {
                            ncapManager.sendCallSignal(
                                endpointId,
                                NcapEnvelope.PayloadType.CALL_ANSWER,
                                json.toString().toByteArray(Charsets.UTF_8)
                            )
                            android.util.Log.e("offCall", "CALL_ANSWER sent to $endpointId")
                            state.update { it.copy(phase = CallPhase.CONNECTING) }

                            iceCandidateCache[peerPublicKey]?.forEach { candidate ->
                                sendIceCandidate(endpointId, peerPublicKey, candidate)
                            }
                            iceCandidateCache.remove(peerPublicKey)
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
                Timber.e("acceptCall: createAnswer failed for ${peerPublicKey.take(8)}... — $err")
                state.update { it.copy(phase = CallPhase.ENDED, error = err) }
            }
            override fun onSetFailure(err: String) {}
        }, constraints)
    }

    fun onSdpReceived(peerPublicKey: String, sdpJson: String, endpointId: String = "") {
        val json = JSONObject(sdpJson)
        val type = json.getString("type")
        val sdpString = json.getString("sdp")

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
                Timber.d("SDP offer cached for ${peerPublicKey.take(8)}... (waiting for acceptCall)")
            }
            return
        }

        val sigState = pc.signalingState()
        android.util.Log.e("WebRTC_ICE", "onSdpReceived: setting remote ${sdp.type} for ${peerPublicKey.take(8)}... sigState=$sigState")

        if (sdp.type == SessionDescription.Type.ANSWER && sigState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.e("WebRTC_ICE", "onSdpReceived: caching ANSWER (sigState=$sigState) — will apply after local desc set")
            pendingSdpAnswers[peerPublicKey] = sdp
            return
        }
        if (sdp.type == SessionDescription.Type.OFFER && sigState != PeerConnection.SignalingState.STABLE && sigState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.e("WebRTC_ICE", "onSdpReceived: ignoring OFFER, signaling state is $sigState (expected STABLE or HAVE_LOCAL_OFFER)")
            return
        }

        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Timber.d("onSdpReceived: ${sdp.type} set for ${peerPublicKey.take(8)}...")
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
        val json = JSONObject(candidateJson)
        val sdp = json.optString("sdp", "")
        if (sdp.isEmpty()) return
        val sdpMid = json.optString("sdpMid", "")
        val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)

        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)

        if (!peerConnections.containsKey(peerPublicKey)) {
            iceCandidateCache.getOrPut(peerPublicKey) { mutableListOf() }.add(candidate)
            return
        }

        val pc = peerConnections[peerPublicKey] ?: return
        pc.addIceCandidate(candidate)
    }

    fun onCallHangup(peerPublicKey: String) {
        Timber.d("onCallHangup: remote hangup for ${peerPublicKey.take(8)}...")
        val state = getCallState(peerPublicKey)
        state.update { it.copy(phase = CallPhase.ENDED) }
        cleanupPeerConnection(peerPublicKey)
    }

    fun hangup(peerPublicKey: String) {
        Timber.d("hangup: local hangup for ${peerPublicKey.take(8)}...")
        val json = JSONObject().apply { put("hangup", true) }
        sendOnDataChannel(peerPublicKey, json.toString())
        scope.launch {
            val endpointId = ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull()
            if (endpointId != null) {
                ncapManager.sendCallSignal(
                    endpointId,
                    NcapEnvelope.PayloadType.CALL_HANGUP,
                    ByteArray(0)
                )
            }
        }
        val state = getCallState(peerPublicKey)
        state.update { it.copy(phase = CallPhase.ENDED) }
        cleanupPeerConnection(peerPublicKey)
    }

    fun toggleMute(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val isMuted = !state.value.isMuted
        val json = JSONObject().apply { put("mute", isMuted) }
        if (!sendOnDataChannel(peerPublicKey, json.toString())) return
        state.update { it.copy(isMuted = isMuted) }
        localAudioTracks[peerPublicKey]?.setEnabled(!isMuted)
    }

    fun toggleSpeaker(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val speakerOn = !state.value.isSpeakerOn
        state.update { it.copy(isSpeakerOn = speakerOn) }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = speakerOn
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
        val json = JSONObject().apply { put("camera", enabled) }
        if (!sendOnDataChannel(peerPublicKey, json.toString())) {
            android.util.Log.e("offCall", "setCameraEnabled DataChannel send failed for ${peerPublicKey.take(8)}")
            return
        }
        android.util.Log.e("offCall", "setCameraEnabled $enabled capturer=${videoCapturers[peerPublicKey] != null} track=${localVideoTracks[peerPublicKey] != null}")
        state.update { it.copy(isCameraOn = enabled) }
        localVideoTracks[peerPublicKey]?.setEnabled(enabled)
        if (enabled) {
            videoCapturers[peerPublicKey]?.startCapture(640, 480, 30)
            android.util.Log.e("offCall", "setCameraEnabled startCapture done")
        } else {
            videoCapturers[peerPublicKey]?.stopCapture()
            android.util.Log.e("offCall", "setCameraEnabled stopCapture done")
        }
    }

    fun flipCamera(peerPublicKey: String) {
        val state = getCallState(peerPublicKey)
        val isFront = !state.value.isFrontCamera
        state.update { it.copy(isFrontCamera = isFront) }

        val capturer = videoCapturers[peerPublicKey] as? CameraVideoCapturer ?: return
        capturer.switchCamera(null)
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
            android.util.Log.e("offCall", "attachRemoteVideo tracks=${tracks?.size ?: 0} pending=${pendingRemoteRenderers[peerPublicKey]?.size ?: 0} renderer=${renderer.hashCode()}")
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
        val key = System.identityHashCode(renderer)
        val oldTracks = rendererTracks.remove(key)
        android.util.Log.e("WebRTC_ICE", "removeRendererSinks: renderer=${renderer.hashCode()} oldTracks=${oldTracks?.size ?: 0}")
        oldTracks?.forEach { it.removeSink(renderer) }
    }

    fun releaseSurface(renderer: SurfaceViewRenderer) {
        renderer.release()
    }

    fun bindVideoProxies(peerPublicKey: String) {
        val localProxy = localProxies.getOrPut(peerPublicKey) { ProxyVideoSink() }
        val remoteProxy = remoteProxies.getOrPut(peerPublicKey) { ProxyVideoSink() }
        localVideoTracks[peerPublicKey]?.let { track ->
            track.addSink(localProxy)
            android.util.Log.e("offCall", "bindVideoProxies local track bound to proxy")
        }
        remoteVideoTracks[peerPublicKey]?.forEach { track ->
            track.addSink(remoteProxy)
            android.util.Log.e("offCall", "bindVideoProxies remote track bound to proxy")
        }
    }

    fun swapVideoFeeds(peerPublicKey: String, localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        localProxies[peerPublicKey]?.setTarget(localRenderer)
        remoteProxies[peerPublicKey]?.setTarget(remoteRenderer)
        android.util.Log.e("offCall", "swapVideoFeeds local→${localRenderer.hashCode()} remote→${remoteRenderer.hashCode()}")
    }

    private fun createPeerConnection(peerPublicKey: String, isVideo: Boolean): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val egl = eglBase ?: return null

        val iceServers = emptyList<PeerConnection.IceServer>()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        val observer = createPeerConnectionObserver(peerPublicKey)

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: return null

        peerConnections[peerPublicKey] = pc

        val dcInit = DataChannel.Init().apply { ordered = true }
        val dc = pc.createDataChannel("callControl", dcInit)
        dataChannels[peerPublicKey] = dc
        dc.registerObserver(createDataChannelObserver(peerPublicKey))

        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        val audioTrack = factory.createAudioTrack("audio_$peerPublicKey", audioSource)
        audioSources[peerPublicKey] = audioSource
        localAudioTracks[peerPublicKey] = audioTrack
        pc.addTrack(audioTrack)

        if (isVideo) {
            val videoCapturer = createVideoCapturer()
            if (videoCapturer != null) {
                val surfaceTextureHelper = SurfaceTextureHelper.create(
                    "video_$peerPublicKey", egl.eglBaseContext
                )
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
            override fun onCameraError(error: String) {
                android.util.Log.e("WebRTC_ICE", "Camera ERROR: $error — retrying with Camera1")
                scope.launch { fallbackToCamera1(error) }
            }
            override fun onCameraDisconnected() { onCameraError("disconnected") }
            override fun onCameraFreezed(error: String) { onCameraError("freezed: $error") }
            override fun onCameraOpening(cameraName: String) {
                android.util.Log.e("WebRTC_ICE", "Camera opening: $cameraName")
            }
            override fun onFirstFrameAvailable() {
                android.util.Log.e("WebRTC_ICE", "Camera first frame available")
            }
            override fun onCameraClosed() {}
        }

        android.util.Log.e("WebRTC_ICE", "createVideoCapturer: Camera2=${Camera2Enumerator.isSupported(context)}")
        if (Camera2Enumerator.isSupported(context)) {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames.toList()
            android.util.Log.e("WebRTC_ICE", "createVideoCapturer: Camera2 devices=$deviceNames")
            val name = deviceNames.find { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()
            if (name != null) {
                android.util.Log.e("WebRTC_ICE", "createVideoCapturer: using Camera2 $name")
                return enumerator.createCapturer(name, handler)
            }
            android.util.Log.e("WebRTC_ICE", "createVideoCapturer: Camera2 no devices, falling back to Camera1")
        }
        val enumerator = Camera1Enumerator(true)
        val deviceNames = enumerator.deviceNames.toList()
        android.util.Log.e("WebRTC_ICE", "createVideoCapturer: Camera1 devices=$deviceNames")
        if (deviceNames.isNotEmpty()) {
            android.util.Log.e("WebRTC_ICE", "createVideoCapturer: using Camera1 ${deviceNames[0]}")
            return enumerator.createCapturer(deviceNames[0], handler)
        }
        return null
    }

    private var activeVideoPeer: String? = null
    private var activeSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var activeVideoSource: VideoSource? = null

    private fun fallbackToCamera1(error: String) {
        val peerKey = activeVideoPeer ?: return
        val stHelper = activeSurfaceTextureHelper ?: return
        val vSource = activeVideoSource ?: return
        android.util.Log.e("WebRTC_ICE", "fallbackToCamera1: stopping Camera2, trying Camera1 for $peerKey")
        videoCapturers[peerKey]?.stopCapture()
        val enumerator = Camera1Enumerator(true)
        val deviceNames = enumerator.deviceNames
        if (deviceNames.size > 0) {
            val newCapturer = enumerator.createCapturer(deviceNames[0], object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(error: String) {
                    android.util.Log.e("WebRTC_ICE", "Camera1 also failed: $error")
                }
                override fun onCameraDisconnected() {}
                override fun onCameraFreezed(error: String) {}
                override fun onCameraOpening(cameraName: String) {
                    android.util.Log.e("WebRTC_ICE", "Camera1 fallback opening: $cameraName")
                }
                override fun onFirstFrameAvailable() {
                    android.util.Log.e("WebRTC_ICE", "Camera1 fallback first frame")
                }
                override fun onCameraClosed() {}
            })
            if (newCapturer != null) {
                newCapturer.initialize(stHelper, context, vSource.capturerObserver)
                newCapturer.startCapture(640, 480, 30)
                videoCapturers[peerKey] = newCapturer
            }
        }
    }

    private val surfaceTextureHelpers = ConcurrentHashMap<String, SurfaceTextureHelper>()

    private fun createPeerConnectionObserver(peerPublicKey: String): PeerConnection.Observer {
        val state = getCallState(peerPublicKey)

        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                android.util.Log.e("WebRTC_ICE", "onIceCandidate gathered for ${peerPublicKey.take(8)}...")
                scope.launch {
                    sendIceCandidateToPeer(peerPublicKey, candidate)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState) {
                Timber.d("Signaling: $p0 for ${peerPublicKey.take(8)}...")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                android.util.Log.e("WebRTC_ICE", "ICE state changed: $iceConnectionState for ${peerPublicKey.take(8)}...")
                Timber.d("ICE: $iceConnectionState for ${peerPublicKey.take(8)}...")
                when (iceConnectionState) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
                        Timber.d("WebRtc: ICE CONNECTED for ${peerPublicKey.take(8)}...")
                        state.update { it.copy(phase = CallPhase.CONNECTED, connectedAt = System.currentTimeMillis()) }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Timber.d("WebRtc: ICE DISCONNECTED for ${peerPublicKey.take(8)}... (15s grace)")
                        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
                        disconnectGraceJobs[peerPublicKey] = scope.launch {
                            kotlinx.coroutines.delay(15_000L)
                            if (state.value.phase != CallPhase.ENDED) {
                                Timber.d("WebRtc: ICE DISCONNECTED grace expired → ENDED")
                                state.update { it.copy(phase = CallPhase.ENDED) }
                                cleanupPeerConnection(peerPublicKey)
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        Timber.d("WebRtc: ICE $iceConnectionState → ENDED for ${peerPublicKey.take(8)}...")
                        if (state.value.phase != CallPhase.ENDED) {
                            state.update { it.copy(phase = CallPhase.ENDED) }
                            cleanupPeerConnection(peerPublicKey)
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                android.util.Log.e("WebRTC_ICE", "ICE gathering: $state for ${peerPublicKey.take(8)}...")
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    Timber.d("ICE gathering complete for ${peerPublicKey.take(8)}...")
                }
            }
            override fun onAddStream(stream: MediaStream) {
                Timber.d("Remote stream added for ${peerPublicKey.take(8)}...")
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {
                android.util.Log.e("WebRTC_ICE", "onDataChannel: received for ${peerPublicKey.take(8)}...")
                dataChannels[peerPublicKey] = channel
                channel.registerObserver(createDataChannelObserver(peerPublicKey))
            }
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    android.util.Log.e("offCall", "onAddTrack kind=${track.kind()} enabled=${track.enabled()} for ${peerPublicKey.take(8)}")
                    if (track is VideoTrack) {
                        remoteVideoTracks.getOrPut(peerPublicKey) { mutableListOf() }.add(track)
                        synchronized(pendingRemoteRenderers) {
                            val pending = pendingRemoteRenderers[peerPublicKey]
                            android.util.Log.e("offCall", "onAddTrack VIDEO pending=${pending?.size ?: 0} for ${peerPublicKey.take(8)}")
                            pending?.forEach { renderer ->
                                android.util.Log.e("offCall", "onAddTrack binding remote video to renderer=${renderer.hashCode()}")
                                track.addSink(renderer)
                            }
                            pendingRemoteRenderers.remove(peerPublicKey)
                        }
                        Timber.d("Remote video track added for ${peerPublicKey.take(8)}...")
                    }
                }
            }
            override fun onRemoveTrack(p0: org.webrtc.RtpReceiver?) {}
        }
    }

    private fun createDataChannelObserver(peerPublicKey: String): DataChannel.Observer {
        val state = getCallState(peerPublicKey)
        return object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                val dc = dataChannels[peerPublicKey] ?: return
                android.util.Log.e("WebRTC_ICE", "DataChannel state: ${dc.state()} for ${peerPublicKey.take(8)}...")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val msg = String(data)
                android.util.Log.e("WebRTC_ICE", "DataChannel msg: $msg from ${peerPublicKey.take(8)}...")
                try {
                    val json = JSONObject(msg)
                    if (json.has("camera")) {
                        val enabled = json.getBoolean("camera")
                        android.util.Log.e("offCall", "DataChannel camera=$enabled from ${peerPublicKey.take(8)}")
                        state.update { it.copy(isCameraOn = enabled) }
                    }
                    if (json.has("mute")) {
                        val muted = json.getBoolean("mute")
                        android.util.Log.e("offCall", "DataChannel mute=$muted from ${peerPublicKey.take(8)}")
                        state.update { it.copy(isMuted = muted) }
                    }
                    if (json.has("hangup")) {
                        android.util.Log.e("offCall", "DataChannel hangup from ${peerPublicKey.take(8)}")
                        state.update { it.copy(phase = CallPhase.ENDED) }
                        cleanupPeerConnection(peerPublicKey)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebRTC_ICE", "DataChannel parse error: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendIceCandidateToPeer(peerPublicKey: String, candidate: IceCandidate) {
        val endpointId = ncapManager.getConnectedEndpointIds(peerPublicKey).firstOrNull() ?: return

        val json = JSONObject().apply {
            put("sdp", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        ncapManager.sendCallSignal(
            endpointId,
            NcapEnvelope.PayloadType.ICE_CANDIDATE,
            json.toString().toByteArray(Charsets.UTF_8)
        )
    }

    private suspend fun sendIceCandidate(endpointId: String, peerPublicKey: String, candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("sdp", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        ncapManager.sendCallSignal(
            endpointId,
            NcapEnvelope.PayloadType.ICE_CANDIDATE,
            json.toString().toByteArray(Charsets.UTF_8)
        )
    }

    private fun sendOnDataChannel(peerPublicKey: String, message: String): Boolean {
        val channel = dataChannels[peerPublicKey]
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            return false
        }
        return try {
            val buffer = DataChannel.Buffer(java.nio.ByteBuffer.wrap(message.toByteArray()), false)
            channel.send(buffer)
            true
        } catch (e: Exception) {
            android.util.Log.e("WebRTC_ICE", "DataChannel send failed: ${e.message}")
            false
        }
    }

    private fun cleanupPeerConnection(peerPublicKey: String) {
        Timber.d("cleanupPeerConnection: ${peerPublicKey.take(8)}...")
        disconnectGraceJobs.remove(peerPublicKey)?.cancel()
        dataChannels.remove(peerPublicKey)?.let {
            it.close()
            it.dispose()
        }
        // pc.dispose() also disposes the senders' attached local tracks —
        // don't dispose tracks manually or they get freed twice.
        peerConnections.remove(peerPublicKey)?.let {
            it.close()
            it.dispose()
        }

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
        pendingSdpAnswers.remove(peerPublicKey)
        iceCandidateCache.remove(peerPublicKey)
    }

    private fun launchCallTimeout(peerPublicKey: String, state: MutableStateFlow<CallState>) {
        scope.launch {
            kotlinx.coroutines.delay(60_000L)
            if (state.value.phase == CallPhase.CONNECTING || state.value.phase == CallPhase.OUTGOING) {
                Timber.d("Call timeout for ${peerPublicKey.take(8)}...")
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
        peerConnectionFactory?.dispose()
        eglBase?.release()
        initialized = false
    }
}
