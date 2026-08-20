package com.autolyrics.lyrics

import com.autolyrics.model.LyricsCandidate
import com.autolyrics.model.LyricsRankingRequest
import com.autolyrics.model.LyricsRankingResult
import com.autolyrics.model.RankedLyricsCandidate
import com.autolyrics.model.TrackInfo
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class RuleBasedLyricsCandidateRanker : LyricsCandidateRanker {

    override suspend fun rank(request: LyricsRankingRequest): LyricsRankingResult {
        val ranked = request.candidates
            .map { candidate -> score(request.target, candidate) }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.ranked.score }
                    .thenBy { it.durationDeltaMs ?: Long.MAX_VALUE }
                    .thenByDescending { it.ranked.candidate.hasSyncedLyrics }
                    .thenBy { it.ranked.candidate.provider }
                    .thenBy { it.ranked.candidate.providerId.orEmpty() }
                    .thenBy { it.ranked.candidate.title }
            )
            .map { it.ranked }

        return LyricsRankingResult(ranked)
    }

    private fun score(target: TrackInfo, candidate: LyricsCandidate): ScoredCandidate {
        var score = 0.0
        val reasons = mutableListOf<String>()

        val titleScore = textMatchScore(target.title, candidate.title, 0.35, 0.25, 0.15)
        score += titleScore
        if (titleScore > 0) reasons += "title match"

        val artistScore = textMatchScore(target.artist, candidate.artist, 0.25, 0.20, 0.10)
        score += artistScore
        if (artistScore > 0) reasons += "artist match"

        val durationDeltaMs = durationDelta(target, candidate)
        val durationScore = when {
            durationDeltaMs == null -> 0.0
            durationDeltaMs <= 2_000 -> 0.30
            durationDeltaMs <= 5_000 -> 0.24
            durationDeltaMs <= 10_000 -> 0.16
            durationDeltaMs <= 20_000 -> 0.05
            durationDeltaMs > 30_000 -> -0.25
            else -> 0.0
        }
        score += durationScore
        if (durationDeltaMs != null) reasons += "duration delta ${durationDeltaMs}ms"

        val albumScore = albumMatchScore(target.album, candidate.album)
        score += albumScore
        if (albumScore > 0) reasons += "album match"

        when {
            candidate.hasSyncedLyrics -> {
                score += 0.05
                reasons += "synced lyrics"
            }
            candidate.hasPlainLyrics -> {
                score += 0.02
                reasons += "plain lyrics"
            }
            else -> {
                score -= 0.50
                reasons += "no usable lyrics"
            }
        }

        val targetMarkers = versionMarkers(target.title + " " + target.album)
        val candidateMarkers = versionMarkers(candidate.title + " " + candidate.album)
        if ((candidateMarkers - targetMarkers).isNotEmpty()) {
            score -= 0.20
            reasons += "version mismatch"
        }

        if (candidate.instrumental && "instrumental" !in targetMarkers) {
            score -= 0.40
            reasons += "instrumental mismatch"
        }

        val normalizedScore = (
            score.coerceIn(MIN_SCORE, MAX_SCORE) * SCORE_PRECISION
        ).roundToInt() / SCORE_PRECISION

        return ScoredCandidate(
            ranked = RankedLyricsCandidate(candidate, normalizedScore, reasons),
            durationDeltaMs = durationDeltaMs
        )
    }

    private fun textMatchScore(
        target: String,
        candidate: String,
        exactScore: Double,
        closeScore: Double,
        weakScore: Double
    ): Double {
        val targetText = normalize(target)
        val candidateText = normalize(candidate)
        if (targetText.isBlank() || candidateText.isBlank()) return 0.0
        if (targetText == candidateText) return exactScore

        val similarity = tokenSimilarity(targetText, candidateText)
        return when {
            similarity >= 0.80 -> closeScore
            similarity >= 0.50 -> weakScore
            else -> 0.0
        }
    }

    private fun albumMatchScore(target: String, candidate: String): Double {
        val targetText = normalize(target)
        val candidateText = normalize(candidate)
        if (targetText.isBlank() || candidateText.isBlank()) return 0.0
        if (targetText == candidateText) return 0.05
        return if (tokenSimilarity(targetText, candidateText) >= 0.75) 0.03 else 0.0
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val union = leftTokens.union(rightTokens).size.toDouble()
        return intersection / union
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(NON_ALPHANUMERIC, " ")
            .replace(MULTIPLE_SPACES, " ")
            .trim()
    }

    private fun versionMarkers(value: String): Set<String> {
        val normalized = normalize(value)
        return VERSION_MARKERS.filterTo(mutableSetOf()) { marker ->
            Regex("(?:^| )${Regex.escape(marker)}(?: |$)").containsMatchIn(normalized)
        }
    }

    private fun durationDelta(target: TrackInfo, candidate: LyricsCandidate): Long? {
        val candidateDuration = candidate.durationMs ?: return null
        if (target.durationMs <= 0 || candidateDuration <= 0) return null
        return abs(target.durationMs - candidateDuration)
    }

    private data class ScoredCandidate(
        val ranked: RankedLyricsCandidate,
        val durationDeltaMs: Long?
    )

    private companion object {
        const val MIN_SCORE = 0.0
        const val MAX_SCORE = 1.0
        const val SCORE_PRECISION = 10_000.0

        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        val MULTIPLE_SPACES = Regex("\\s+")
        val VERSION_MARKERS = setOf(
            "live",
            "remix",
            "acoustic",
            "instrumental",
            "demo",
            "sped up",
            "slowed",
            "nightcore",
            "karaoke"
        )
    }
}
