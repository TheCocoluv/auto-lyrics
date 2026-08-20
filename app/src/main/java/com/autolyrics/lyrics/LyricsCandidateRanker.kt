package com.autolyrics.lyrics

import com.autolyrics.model.LyricsRankingRequest
import com.autolyrics.model.LyricsRankingResult

fun interface LyricsCandidateRanker {
    suspend fun rank(request: LyricsRankingRequest): LyricsRankingResult
}
