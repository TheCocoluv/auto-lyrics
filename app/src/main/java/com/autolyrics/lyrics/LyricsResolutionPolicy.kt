package com.autolyrics.lyrics

import com.autolyrics.model.LyricsResolution
import com.autolyrics.model.LyricsResolutionRequest

fun interface LyricsResolutionPolicy {
    fun resolve(request: LyricsResolutionRequest): LyricsResolution
}
