package com.offnetic.data.nearby

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

class ProxyVideoSink : VideoSink {
    @Volatile
    private var target: VideoSink? = null

    @Synchronized
    override fun onFrame(frame: VideoFrame) {
        target?.onFrame(frame)
    }

    @Synchronized
    fun setTarget(target: VideoSink?) {
        this.target = target
    }
}
