package com.autolyrics.lyrics

import com.autolyrics.model.LyricsResolution
import com.autolyrics.model.ResolutionReason
import com.autolyrics.model.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLyricsResolutionPolicyTest {
    private val policy = DefaultLyricsResolutionPolicy()

    @Test
    fun `high confidence candidate satisfying constraints is automatically selected`() {
        val candidate = LyricsMatchingFixtures.ranked(
            score = 0.95,
            candidate = LyricsMatchingFixtures.candidate(providerId = "best")
        )

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertTrue(result is LyricsResolution.AutoSelect)
        assertSame(candidate, (result as LyricsResolution.AutoSelect).candidate)
    }

    @Test
    fun `candidate exactly at automatic threshold is accepted`() {
        val candidate = LyricsMatchingFixtures.ranked(score = 0.85)

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertTrue(result is LyricsResolution.AutoSelect)
    }

    @Test
    fun `candidate below choice threshold requires manual search`() {
        val candidate = LyricsMatchingFixtures.ranked(score = 0.59)

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertManualSearch(result, ResolutionReason.NO_PLAUSIBLE_CANDIDATES)
    }

    @Test
    fun `plausible but low confidence candidate requires user choice`() {
        val candidate = LyricsMatchingFixtures.ranked(score = 0.75)

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertUserChoice(result, ResolutionReason.LOW_CONFIDENCE)
    }

    @Test
    fun `similar top scores require user choice`() {
        val first = LyricsMatchingFixtures.ranked(
            score = 0.92,
            candidate = LyricsMatchingFixtures.candidate(providerId = "first")
        )
        val second = LyricsMatchingFixtures.ranked(
            score = 0.88,
            candidate = LyricsMatchingFixtures.candidate(providerId = "second")
        )

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(first, second))

        assertUserChoice(result, ResolutionReason.AMBIGUOUS_RESULTS)
    }

    @Test
    fun `policy can select lower ranked candidate when top candidate fails hard constraint`() {
        val rankerPreferred = LyricsMatchingFixtures.ranked(
            score = 0.97,
            candidate = LyricsMatchingFixtures.candidate(
                providerId = "duration-outlier",
                durationMs = 379_000
            )
        )
        val policyPreferred = LyricsMatchingFixtures.ranked(
            score = 0.90,
            candidate = LyricsMatchingFixtures.candidate(providerId = "valid")
        )

        val result = policy.resolve(
            LyricsMatchingFixtures.resolutionRequest(rankerPreferred, policyPreferred)
        )

        assertTrue(result is LyricsResolution.AutoSelect)
        assertEquals(
            "valid",
            (result as LyricsResolution.AutoSelect).candidate.candidate.providerId
        )
    }

    @Test
    fun `policy rejects automatic selection when all candidates fail hard constraints`() {
        val outlier = LyricsMatchingFixtures.ranked(
            score = 0.97,
            candidate = LyricsMatchingFixtures.candidate(durationMs = 379_000)
        )

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(outlier))

        assertUserChoice(result, ResolutionReason.HARD_CONSTRAINT_FAILED)
    }

    @Test
    fun `missing candidate duration prevents automatic selection`() {
        val candidate = LyricsMatchingFixtures.ranked(
            score = 0.95,
            candidate = LyricsMatchingFixtures.candidate(durationMs = null)
        )

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertUserChoice(result, ResolutionReason.HARD_CONSTRAINT_FAILED)
    }

    @Test
    fun `empty ranking requires manual search`() {
        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest())

        assertManualSearch(result, ResolutionReason.NO_CANDIDATES)
    }

    @Test
    fun `incomplete target metadata requires manual search`() {
        val incompleteTarget = TrackInfo(
            title = "Numb",
            artist = "",
            album = "Meteora",
            durationMs = 187_000
        )
        val candidate = LyricsMatchingFixtures.ranked(score = 0.95)

        val result = policy.resolve(
            LyricsMatchingFixtures.resolutionRequest(candidate, target = incompleteTarget)
        )

        assertManualSearch(result, ResolutionReason.INCOMPLETE_METADATA)
    }

    @Test
    fun `candidate without lyrics is not plausible regardless of score`() {
        val unusable = LyricsMatchingFixtures.ranked(
            score = 1.0,
            candidate = LyricsMatchingFixtures.candidate(synced = false, plain = false)
        )

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(unusable))

        assertManualSearch(result, ResolutionReason.NO_PLAUSIBLE_CANDIDATES)
    }

    @Test
    fun `user choice result is limited to five candidates`() {
        val candidates = (1..7).map { index ->
            LyricsMatchingFixtures.ranked(
                score = 0.70,
                candidate = LyricsMatchingFixtures.candidate(providerId = index.toString())
            )
        }.toTypedArray()

        val result = policy.resolve(LyricsMatchingFixtures.resolutionRequest(*candidates))

        assertTrue(result is LyricsResolution.RequireUserChoice)
        assertEquals(5, (result as LyricsResolution.RequireUserChoice).candidates.size)
    }

    @Test
    fun `configured automatic threshold controls acceptance`() {
        val strictPolicy = DefaultLyricsResolutionPolicy(autoSelectThreshold = 0.95)
        val candidate = LyricsMatchingFixtures.ranked(score = 0.90)

        val result = strictPolicy.resolve(LyricsMatchingFixtures.resolutionRequest(candidate))

        assertUserChoice(result, ResolutionReason.LOW_CONFIDENCE)
    }

    private fun assertManualSearch(
        result: LyricsResolution,
        reason: ResolutionReason
    ) {
        assertTrue(result is LyricsResolution.RequireManualSearch)
        assertEquals(reason, (result as LyricsResolution.RequireManualSearch).reason)
    }

    private fun assertUserChoice(
        result: LyricsResolution,
        reason: ResolutionReason
    ) {
        assertTrue(result is LyricsResolution.RequireUserChoice)
        assertEquals(reason, (result as LyricsResolution.RequireUserChoice).reason)
    }
}
