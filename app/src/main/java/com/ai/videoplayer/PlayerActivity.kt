package com.ai.videoplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var selectedFilter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedFilter = intent.getIntExtra("filterIndex",0)
        //初始化播放器 + OpenGL滤镜渲染管线
        initPlayer()
    }

    private fun initPlayer(){
        player = ExoPlayer.Builder(this).build()
        //这里绑定GLSurfaceView，加载对应shader滤镜
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
