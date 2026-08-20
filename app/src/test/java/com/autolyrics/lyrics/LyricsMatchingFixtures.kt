package com.autolyrics.lyrics

import com.autolyrics.model.LyricsCandidate
import com.autolyrics.model.LyricsRankingRequest
import com.autolyrics.model.LyricsRankingResult
import com.autolyrics.model.LyricsResolutionRequest
import com.autolyrics.model.RankedLyricsCandidate
import com.autolyrics.model.TrackInfo

internal object LyricsMatchingFixtures {
    val target = TrackInfo(
        title = "Numb",
        artist = "Linkin Park",
        album = "Meteora",
        durationMs = 187_000
    )

    fun candidate(
        providerId: String? = "candidate",
        title: String = target.title,
        artist: String = target.artist,
        album: String = target.album,
        durationMs: Long? = target.durationMs,
        instrumental: Boolean = false,
        synced: Boolean = true,
        plain: Boolean = true,
        provider: String = "LRCLIB"
    ): LyricsCandidate {
        return LyricsCandidate(
            provider = provider,
            providerId = providerId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            instrumental = instrumental,
            syncedLyrics = if (synced) "[00:01.00]Test" else null,
            plainLyrics = if (plain) "Test" else null
        )
    }

    fun rankingRequest(
        candidates: List<LyricsCandidate>,
        target: TrackInfo = this.target
    ): LyricsRankingRequest {
        return LyricsRankingRequest(target, candidates)
    }

    fun ranked(
        score: Double,
        candidate: LyricsCandidate = candidate()
    ): RankedLyricsCandidate {
        return RankedLyricsCandidate(candidate, score)
    }

    fun resolutionRequest(
        vararg candidates: RankedLyricsCandidate,
        target: TrackInfo = this.target
    ): LyricsResolutionRequest {
        return LyricsResolutionRequest(target, LyricsRankingResult(candidates.toList()))
    }
}
