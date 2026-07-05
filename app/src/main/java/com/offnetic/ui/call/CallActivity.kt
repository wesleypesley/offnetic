package com.offnetic.ui.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.offnetic.R
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.nearby.WebRtcManager
import com.offnetic.domain.model.CallPhase
import com.offnetic.service.IncomingCallService
import com.offnetic.ui.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    @Inject lateinit var webRtcManager: WebRtcManager
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var callViewModelFactory: CallViewModel.Factory

    private lateinit var callViewModel: CallViewModel

    private lateinit var pipRenderer: SurfaceViewRenderer
    private lateinit var fullscreenRenderer: SurfaceViewRenderer

    private lateinit var peerNameTv: TextView
    private lateinit var durationTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var incomingButtons: View
    private lateinit var controlPanel: View
    private lateinit var acceptBtn: ImageButton
    private lateinit var declineBtn: ImageButton
    private lateinit var toggleMicBtn: ImageButton
    private lateinit var micSlash: View
    private lateinit var toggleCameraBtn: ImageButton
    private lateinit var cameraSlash: View
    private lateinit var flipCameraBtn: ImageButton
    private lateinit var hangupBtn: ImageButton
    private lateinit var toggleSpeakerBtn: ImageButton
    private lateinit var cameraOffOverlay: View
    private lateinit var cameraOffNameTv: TextView
    private lateinit var cameraOffNameLabelTv: TextView

    private var peerPublicKey: String = ""
    private var isIncoming: Boolean = false
    private var pipVisible: Boolean = false
    private var cameraEnabled: Boolean = false
    private var swapped: Boolean = false
    private var pendingCameraEnable: Boolean = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_call)

        peerPublicKey = Routes.decodeKey(intent.getStringExtra("EXTRA_PEER_PUBLIC_KEY") ?: "")
        isIncoming = intent.getBooleanExtra("EXTRA_IS_INCOMING", false)
        android.util.Log.e("offCall", "onCreate peer=${peerPublicKey.take(8)} incoming=$isIncoming")

        if (peerPublicKey.isEmpty()) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        webRtcManager.initialize()
        bindViews()
        setupCall()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val newKey = Routes.decodeKey(intent.getStringExtra("EXTRA_PEER_PUBLIC_KEY") ?: "")
        android.util.Log.e("offCall", "onNewIntent newKey=${newKey.take(8)} current=${peerPublicKey.take(8)}")
        if (newKey != peerPublicKey) {
            hangup()
            peerPublicKey = newKey
            isIncoming = intent.getBooleanExtra("EXTRA_IS_INCOMING", false)
            setupCall()
        }
    }

    override fun onPause() {
        super.onPause()
        if (cameraEnabled) {
            callViewModel.setCameraEnabled(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (cameraEnabled && tracksBound) {
            callViewModel.setCameraEnabled(true)
            pipVisible = true
            pipRenderer.visibility = View.VISIBLE
            cameraSlash.visibility = View.GONE
            toggleCameraBtn.setColorFilter(0xFFFFFFFF.toInt())
            flipCameraBtn.visibility = View.VISIBLE
        } else if (!cameraEnabled && pendingCameraEnable) {
            pendingCameraEnable = false
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                enableCamera()
            }
        }
    }

    private fun bindViews() {
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
        pipRenderer = findViewById(R.id.pip_video_view)
        peerNameTv = findViewById(R.id.call_peer_name)
        durationTv = findViewById(R.id.call_duration)
        statusTv = findViewById(R.id.call_status)
        incomingButtons = findViewById(R.id.incoming_buttons)
        controlPanel = findViewById(R.id.control_panel)
        acceptBtn = findViewById(R.id.accept_button)
        declineBtn = findViewById(R.id.decline_button)
        toggleMicBtn = findViewById(R.id.toggle_mic_button)
        micSlash = findViewById(R.id.mic_slash)
        toggleCameraBtn = findViewById(R.id.toggle_camera_button)
        cameraSlash = findViewById(R.id.camera_slash)
        flipCameraBtn = findViewById(R.id.flip_camera_button)
        hangupBtn = findViewById(R.id.hangup_button)
        toggleSpeakerBtn = findViewById(R.id.toggle_speaker_button)
        cameraOffOverlay = findViewById(R.id.camera_off_overlay)
        cameraOffNameTv = findViewById(R.id.camera_off_name)
        cameraOffNameLabelTv = findViewById(R.id.camera_off_name_label)

        webRtcManager.initSurface(fullscreenRenderer)
        fullscreenRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        fullscreenRenderer.setEnableHardwareScaler(false)
        fullscreenRenderer.visibility = View.INVISIBLE

        webRtcManager.initSurface(pipRenderer)
        pipRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        pipRenderer.setZOrderMediaOverlay(true)
        pipRenderer.setEnableHardwareScaler(true)

        acceptBtn.setOnClickListener { acceptCall() }
        declineBtn.setOnClickListener { hangup() }
        hangupBtn.setOnClickListener { hangup() }
        toggleMicBtn.setOnClickListener { toggleMic() }
        toggleCameraBtn.setOnClickListener { onToggleCamera() }
        flipCameraBtn.setOnClickListener { flipCamera() }
        toggleSpeakerBtn.setOnClickListener { toggleSpeaker() }

        pipRenderer.setOnClickListener { if (tracksBound) swapFeeds() }
        fullscreenRenderer.setOnClickListener { if (tracksBound) swapFeeds() }
    }

    private fun setupCall() {
        callJob?.cancel()
        callJob = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Main + callJob!!)

        if (::callViewModel.isInitialized) {
            callViewModel.cleanup()
        }

        callViewModel = callViewModelFactory.create(peerPublicKey, webRtcManager)

        tracksBound = false
        finished = false
        callActive = false
        cameraEnabled = false
        callViewModel.setSpeakerOff(peerPublicKey)
        swapped = false
        pipVisible = false
        pendingCameraEnable = false
        pipRenderer.visibility = View.INVISIBLE
        fullscreenRenderer.visibility = View.INVISIBLE
        durationTv.visibility = View.GONE
        cameraOffOverlay.visibility = View.GONE
        toggleCameraBtn.setColorFilter(0x40FFFFFF)
        cameraSlash.visibility = View.VISIBLE
        flipCameraBtn.visibility = View.GONE
        toggleMicBtn.setColorFilter(0xFFFFFFFF.toInt())
        micSlash.visibility = View.GONE
        toggleSpeakerBtn.setImageResource(R.drawable.ic_call_speaker)
        toggleSpeakerBtn.setColorFilter(0xFFFFFFFF.toInt())

        scope.launch {
            val contact = contactDao.getByPublicKey(peerPublicKey)
            val displayName = contact?.displayName ?: peerPublicKey.take(12)

            peerNameTv.text = displayName
            cameraOffNameTv.text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            cameraOffNameLabelTv.text = displayName

            if (isIncoming) {
                callViewModel.acceptIncomingCall(peerPublicKey, displayName)
                statusTv.text = "Incoming call"
                incomingButtons.visibility = View.VISIBLE
                controlPanel.visibility = View.GONE
            } else {
                callViewModel.startOutgoingCall(peerPublicKey, displayName)
                statusTv.text = "Calling..."
                incomingButtons.visibility = View.GONE
                controlPanel.visibility = View.VISIBLE
            }
        }

        scope.launch {
            callViewModel.callState.collectLatest { state ->
                updateUI(state)
            }
        }

        scope.launch {
            callViewModel.callDuration.collectLatest { dur ->
                durationTv.text = dur
            }
        }

        scope.launch {
            callViewModel.toastMessage.collectLatest { msg ->
                Toast.makeText(this@CallActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        scope.launch {
            callViewModel.finishEvent.collectLatest {
                if (!finished) finish()
            }
        }
    }

    private fun updateUI(state: com.offnetic.domain.model.CallState) {
        android.util.Log.e("offCall", "updateUI phase=${state.phase} tracksBound=$tracksBound finished=$finished cameraEnabled=$cameraEnabled pipVisible=$pipVisible")
        when (state.phase) {
            CallPhase.IDLE -> {
                finishRunnable?.let { fullscreenRenderer.removeCallbacks(it) }
                finishRunnable = null
            }
            CallPhase.OUTGOING -> {
                finishRunnable?.let { fullscreenRenderer.removeCallbacks(it) }
                finishRunnable = null
                finished = false
                statusTv.text = "Calling..."
            }
            CallPhase.CONNECTING -> {
                finishRunnable?.let { fullscreenRenderer.removeCallbacks(it) }
                finishRunnable = null
                finished = false
                statusTv.text = "Connecting..."
            }
            CallPhase.INCOMING -> {
                finishRunnable?.let { fullscreenRenderer.removeCallbacks(it) }
                finishRunnable = null
                finished = false
                incomingButtons.visibility = View.VISIBLE
                controlPanel.visibility = View.GONE
            }
            CallPhase.CONNECTED -> {
                statusTv.text = ""
                durationTv.visibility = View.VISIBLE
                incomingButtons.visibility = View.GONE
                controlPanel.visibility = View.VISIBLE
                if (!tracksBound) {
                    tracksBound = true
                    fullscreenRenderer.visibility = View.VISIBLE
                    webRtcManager.bindVideoProxies(peerPublicKey)
                    updateVideoFeeds()
                }
                if (cameraEnabled && !pipVisible) {
                    pipVisible = true
                    pipRenderer.visibility = View.VISIBLE
                }
            }
            CallPhase.ENDED -> {
                statusTv.text = state.error ?: "Call ended"
                durationTv.visibility = View.GONE
                controlPanel.visibility = View.GONE
                incomingButtons.visibility = View.GONE
                pipRenderer.visibility = View.INVISIBLE
                fullscreenRenderer.visibility = View.INVISIBLE
                if (cameraEnabled) {
                    callViewModel.setCameraEnabled(false)
                    cameraEnabled = false
                }
                finished = true
                finishRunnable?.let { fullscreenRenderer.removeCallbacks(it) }
                finishRunnable = Runnable { finish() }
                fullscreenRenderer.postDelayed(finishRunnable!!, 1500L)
                cameraOffOverlay.visibility = View.GONE
            }
        }

        if (state.phase == CallPhase.CONNECTED) {
            cameraOffOverlay.visibility = if (state.isRemoteCameraOn) View.GONE else View.VISIBLE
        } else if (state.phase != CallPhase.ENDED) {
            cameraOffOverlay.visibility = View.GONE
        }

        micSlash.visibility = if (state.isMuted) View.VISIBLE else View.GONE
        toggleMicBtn.setColorFilter(if (state.isMuted) 0x40FFFFFF else 0xFFFFFFFF.toInt())

        toggleSpeakerBtn.setImageResource(if (state.isSpeakerOn) R.drawable.ic_call_speaker else R.drawable.ic_call_earpiece)
        toggleSpeakerBtn.setColorFilter(if (state.isSpeakerOn) 0xFFFFFFFF.toInt() else 0x40FFFFFF)
    }

    private fun updateVideoFeeds() {
        if (swapped) {
            webRtcManager.swapVideoFeeds(peerPublicKey, fullscreenRenderer, pipRenderer)
        } else {
            webRtcManager.swapVideoFeeds(peerPublicKey, pipRenderer, fullscreenRenderer)
        }
    }

    private fun swapFeeds() {
        if (!tracksBound) return
        swapped = !swapped
        updateVideoFeeds()
    }

    private var callActive = false
    private var finished = false
    private var finishRunnable: Runnable? = null
    private var tracksBound = false
    private var callJob: kotlinx.coroutines.Job? = null

    private fun stopIncomingRinging() {
        try {
            val stopIntent = Intent(this, IncomingCallService::class.java).apply {
                action = IncomingCallService.ACTION_STOP_RINGING
            }
            startService(stopIntent)
        } catch (_: Exception) {}
    }

    private fun acceptCall() {
        android.util.Log.e("offCall", "acceptCall tapped phase=${callViewModel.callState.value.phase}")
        if (callViewModel.callState.value.phase != CallPhase.INCOMING) return
        stopIncomingRinging()
        callViewModel.acceptCall()
        callActive = true
        incomingButtons.visibility = View.GONE
        controlPanel.visibility = View.VISIBLE
        statusTv.text = "Connecting..."
    }

    private fun hangup() {
        if (finished) return
        finished = true
        stopIncomingRinging()
        android.util.Log.e("offCall", "hangup finished=$finished")
        pipRenderer.visibility = View.INVISIBLE
        fullscreenRenderer.visibility = View.INVISIBLE
        callViewModel.hangup()
    }

    private fun onToggleCamera() {
        if (!cameraEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingCameraEnable = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return
            }
            enableCamera()
        } else {
            disableCamera()
        }
    }

    private fun enableCamera() {
        android.util.Log.e("offCall", "enableCamera tracksBound=$tracksBound pipVisible=$pipVisible")
        cameraEnabled = true
        callViewModel.setCameraEnabled(true)
        cameraSlash.visibility = View.GONE
        toggleCameraBtn.setColorFilter(0xFFFFFFFF.toInt())
        flipCameraBtn.visibility = View.VISIBLE
        if (tracksBound && !pipVisible) {
            pipVisible = true
            pipRenderer.visibility = View.VISIBLE
            updateVideoFeeds()
        }
    }

    private fun disableCamera() {
        android.util.Log.e("offCall", "disableCamera")
        cameraEnabled = false
        callViewModel.setCameraEnabled(false)
        cameraSlash.visibility = View.VISIBLE
        toggleCameraBtn.setColorFilter(0x40FFFFFF)
        flipCameraBtn.visibility = View.GONE
        pipVisible = false
        pipRenderer.visibility = View.INVISIBLE
    }

    private fun toggleMic() {
        callViewModel.toggleMute()
    }

    private fun toggleSpeaker() {
        callViewModel.toggleSpeaker()
    }

    private fun flipCamera() {
        callViewModel.flipCamera()
    }

    override fun onDestroy() {
        android.util.Log.e("offCall", "onDestroy finished=$finished")
        stopIncomingRinging()
        callJob?.cancel()
        if (!finished && ::callViewModel.isInitialized) {
            callViewModel.hangup()
        }
        if (::callViewModel.isInitialized) {
            callViewModel.cleanup()
        }
        if (::pipRenderer.isInitialized) {
            pipRenderer.release()
            fullscreenRenderer.release()
        }
        super.onDestroy()
    }
}
