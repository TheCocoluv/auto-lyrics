package com.autolyrics.lyrics

import com.autolyrics.model.LyricsCandidate
import com.autolyrics.model.LyricsResolution
import com.autolyrics.model.LyricsResolutionRequest
import com.autolyrics.model.RankedLyricsCandidate
import com.autolyrics.model.ResolutionReason
import com.autolyrics.model.TrackInfo
import kotlin.math.abs

class DefaultLyricsResolutionPolicy(
    private val autoSelectThreshold: Double = 0.85,
    private val choiceThreshold: Double = 0.60,
    private val ambiguityMargin: Double = 0.10,
    private val maximumAutoDurationDeltaMs: Long = 5_000
) : LyricsResolutionPolicy {

    init {
        require(autoSelectThreshold in 0.0..1.0)
        require(choiceThreshold in 0.0..autoSelectThreshold)
        require(ambiguityMargin in 0.0..1.0)
        require(maximumAutoDurationDeltaMs >= 0)
    }

    override fun resolve(request: LyricsResolutionRequest): LyricsResolution {
        val target = request.target
        if (target.title.isBlank() || target.artist.isBlank()) {
            return LyricsResolution.RequireManualSearch(ResolutionReason.INCOMPLETE_METADATA)
        }

        if (request.ranking.candidates.isEmpty()) {
            return LyricsResolution.RequireManualSearch(ResolutionReason.NO_CANDIDATES)
        }

        val plausible = request.ranking.candidates.filter { ranked ->
            hasUsableLyrics(ranked.candidate) && ranked.score >= choiceThreshold
        }
        if (plausible.isEmpty()) {
            return LyricsResolution.RequireManualSearch(
                ResolutionReason.NO_PLAUSIBLE_CANDIDATES
            )
        }

        val automaticCandidates = plausible.filter { ranked ->
            passesAutomaticConstraints(target, ranked.candidate)
        }
        val top = automaticCandidates.firstOrNull()
        if (top == null) {
            return requireChoice(plausible, ResolutionReason.HARD_CONSTRAINT_FAILED)
        }

        if (top.score < autoSelectThreshold) {
            return requireChoice(plausible, ResolutionReason.LOW_CONFIDENCE)
        }

        val runnerUp = automaticCandidates.getOrNull(1)
        if (runnerUp != null && top.score - runnerUp.score < ambiguityMargin) {
            return requireChoice(plausible, ResolutionReason.AMBIGUOUS_RESULTS)
        }

        return LyricsResolution.AutoSelect(top)
    }

    private fun passesAutomaticConstraints(
        target: TrackInfo,
        candidate: LyricsCandidate
    ): Boolean {
        if (!hasUsableLyrics(candidate)) return false
        if (candidate.title.isBlank() || candidate.artist.isBlank()) return false
        if (target.durationMs <= 0) return false

        val candidateDuration = candidate.durationMs ?: return false
        if (candidateDuration <= 0) return false
        if (abs(target.durationMs - candidateDuration) > maximumAutoDurationDeltaMs) return false

        val targetIsInstrumental = target.title.contains("instrumental", ignoreCase = true) ||
            target.album.contains("instrumental", ignoreCase = true)
        if (candidate.instrumental && !targetIsInstrumental) return false

        return true
    }

    private fun hasUsableLyrics(candidate: LyricsCandidate): Boolean {
        return candidate.hasSyncedLyrics || candidate.hasPlainLyrics
    }

    private fun requireChoice(
        candidates: List<RankedLyricsCandidate>,
        reason: ResolutionReason
    ): LyricsResolution.RequireUserChoice {
        return LyricsResolution.RequireUserChoice(
            candidates = candidates.take(MAXIMUM_CHOICE_CANDIDATES),
            reason = reason
        )
    }

    private companion object {
        const val MAXIMUM_CHOICE_CANDIDATES = 5
    }
}
