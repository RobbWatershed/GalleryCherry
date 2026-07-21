package me.devsaki.hentoid.core

import android.net.Uri
import android.widget.VideoView

fun VideoView.load(uri: Uri) {
    this.setVideoURI(uri)
    this.setOnPreparedListener {
        it.setVolume(0f, 0f)
        it.isLooping = true
        this.start()
    }
}