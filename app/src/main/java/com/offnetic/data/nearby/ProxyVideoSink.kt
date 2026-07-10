package com.offnetic.data.nearby

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

class ProxyVideoSink : VideoSink {
    @Volatile
    private var target: VideoSink? = null

    @Synchronized
    override fun onFrame(frame: VideoFrame) {
        // No manual release needed: sinks registered via VideoTrack.addSink do not own
        // the frame — the native track releases it after onFrame returns. Dropping the
        // frame when target is null is the same pattern as AppRTC's ProxyVideoSink.
        target?.onFrame(frame)
    }

    @Synchronized
    fun setTarget(target: VideoSink?) {
        this.target = target
    }
}
