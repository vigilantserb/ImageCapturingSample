package com.imagecapturingsample

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class MainActivity : AppCompatActivity() {
    private val SAMPLE_HLS_URI = "https://pf5.broadpeak-vcdn.com/bpk-tv/tvrll/llcmaf/index.m3u8"
    private val REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val playerView = findViewById<StyledPlayerView>(R.id.player_view)
        val player: ExoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = player

        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
        val hlsMediaSource: HlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(SAMPLE_HLS_URI))

        player.setMediaSource(hlsMediaSource)
        player.prepare()
        player.play()

        askForScreenShare()
    }

    private fun askForScreenShare() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjectionManager?.createScreenCaptureIntent()?.let {
            startActivityForResult(it, REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            return
        }

        if (resultCode != Activity.RESULT_OK) {
            finish()
            return
        }

        val cloneIntent = data?.clone() as Intent
        AndroidScreenGrabber.permissionIntent = cloneIntent

        //Start service
        val serviceIntent = Intent(this, MainService::class.java)
        startService(serviceIntent)
    }
}