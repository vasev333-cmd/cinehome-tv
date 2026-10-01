package com.cinehome.tv

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.fragment.app.FragmentActivity

class PlayerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoPath = intent.getStringExtra("VIDEO_PATH") ?: return
        val videoView = VideoView(this)
        setContentView(videoView)

        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.setOnPreparedListener { mp ->
            mp.start()
        }
        videoView.setOnCompletionListener {
            finish()
        }
    }
}
