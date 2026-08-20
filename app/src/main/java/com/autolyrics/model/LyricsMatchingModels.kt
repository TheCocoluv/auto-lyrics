package com.autolyrics.model

data class LyricsRankingRequest(
    val target: TrackInfo,
    val candidates: List<LyricsCandidate>
)

data class RankedLyricsCandidate(
    val candidate: LyricsCandidate,
    val score: Double,
    val reasons: List<String> = emptyList()
)

data class LyricsRankingResult(
    val candidates: List<RankedLyricsCandidate>
)

data class LyricsResolutionRequest(
    val target: TrackInfo,
    val ranking: LyricsRankingResult
)

sealed interface LyricsResolution {
    data class AutoSelect(
        val candidate: RankedLyricsCandidate
    ) : LyricsResolution

    data class RequireUserChoice(
        val candidates: List<RankedLyricsCandidate>,
        val reason: ResolutionReason
    ) : LyricsResolution

    data class RequireManualSearch(
        val reason: ResolutionReason
    ) : LyricsResolution
}

enum class ResolutionReason {
    NO_CANDIDATES,
    NO_PLAUSIBLE_CANDIDATES,
    LOW_CONFIDENCE,
    AMBIGUOUS_RESULTS,
    INCOMPLETE_METADATA,
    HARD_CONSTRAINT_FAILED
}
