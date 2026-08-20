package com.autolyrics.lyrics

import com.autolyrics.model.LyricsCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedLyricsCandidateRankerTest {
    private val ranker = RuleBasedLyricsCandidateRanker()

    @Test
    fun `higher quality candidate ranks first`() {
        val exact = fixture(providerId = "exact")
        val wrongDuration = fixture(providerId = "wrong-duration", durationMs = 5_000)
        val live = fixture(
            providerId = "live",
            album = "Live in Germany",
            durationMs = 379_000
        )

        val result = rank(listOf(live, wrongDuration, exact))

        assertEquals("exact", result.first().candidate.providerId)
        assertTrue(result.zipWithNext().all { (left, right) -> left.score >= right.score })
    }

    @Test
    fun `exact title contributes more than a mismatched title`() {
        val exact = fixture(providerId = "exact")
        val mismatch = fixture(providerId = "mismatch", title = "In the End")

        val result = rank(listOf(mismatch, exact))

        assertEquals("exact", result.first().candidate.providerId)
    }

    @Test
    fun `exact artist contributes more than a mismatched artist`() {
        val exact = fixture(providerId = "exact")
        val mismatch = fixture(providerId = "mismatch", artist = "Different Artist")

        val result = rank(listOf(mismatch, exact))

        assertEquals("exact", result.first().candidate.providerId)
    }

    @Test
    fun `closer duration contributes more than a distant duration`() {
        val close = fixture(providerId = "close", durationMs = 188_000)
        val distant = fixture(providerId = "distant", durationMs = 204_000)

        val result = rank(listOf(distant, close))

        assertEquals("close", result.first().candidate.providerId)
    }

    @Test
    fun `matching album contributes more than a mismatched album`() {
        val matching = fixture(providerId = "matching")
        val mismatch = fixture(providerId = "mismatch", album = "Other Album")

        val result = rank(listOf(mismatch, matching))

        assertEquals("matching", result.first().candidate.providerId)
    }

    @Test
    fun `synced lyrics contribute more than plain lyrics alone`() {
        val synced = fixture(providerId = "synced", synced = true, plain = false)
        val plain = fixture(providerId = "plain", synced = false, plain = true)

        val result = rank(listOf(plain, synced))

        assertEquals("synced", result.first().candidate.providerId)
    }

    @Test
    fun `unexpected version marker lowers ranking`() {
        val studio = fixture(providerId = "studio")
        val live = fixture(providerId = "live", title = "Numb (Live)")

        val result = rank(listOf(live, studio))

        assertEquals("studio", result.first().candidate.providerId)
        assertTrue(result.first().score > result.last().score)
    }

    @Test
    fun `empty input returns empty result`() {
        assertTrue(rank(emptyList()).isEmpty())
    }

    @Test
    fun `single candidate is returned unchanged`() {
        val candidate = fixture(providerId = "only")

        val result = rank(listOf(candidate))

        assertEquals(1, result.size)
        assertSame(candidate, result.single().candidate)
    }

    @Test
    fun `identical candidates retain stable input order`() {
        val first = fixture(providerId = null)
        val second = first.copy()

        val result = rank(listOf(first, second))

        assertSame(first, result[0].candidate)
        assertSame(second, result[1].candidate)
    }

    @Test
    fun `ties use provider id as deterministic fallback`() {
        val second = fixture(providerId = "b")
        val first = fixture(providerId = "a")

        val result = rank(listOf(second, first))

        assertEquals(listOf("a", "b"), result.map { it.candidate.providerId })
    }

    @Test
    fun `scores are clamped to normalized range`() {
        val high = fixture(providerId = "high")
        val low = fixture(
            providerId = "low",
            title = "Different",
            artist = "Different",
            album = "Live Recording",
            durationMs = 500_000,
            instrumental = true,
            synced = false,
            plain = false
        )

        val result = rank(listOf(low, high))

        assertEquals(1.0, result.first().score, 0.0)
        assertEquals(0.0, result.last().score, 0.0)
    }

    @Test
    fun `missing optional fields are supported`() {
        val missing = fixture(providerId = null, durationMs = null)
        val complete = fixture(providerId = "complete")

        val result = rank(listOf(missing, complete))

        assertEquals(2, result.size)
        assertEquals("complete", result.first().candidate.providerId)
    }

    @Test
    fun `repeated executions are stable`() {
        val candidates = listOf(
            fixture(providerId = "b"),
            fixture(providerId = "a"),
            fixture(providerId = "live", album = "Live in Germany", durationMs = 379_000)
        )
        val expected = rank(candidates)

        repeat(20) {
            assertEquals(expected, rank(candidates))
        }
    }

    private fun fixture(
        providerId: String?,
        title: String = LyricsMatchingFixtures.target.title,
        artist: String = LyricsMatchingFixtures.target.artist,
        album: String = LyricsMatchingFixtures.target.album,
        durationMs: Long? = LyricsMatchingFixtures.target.durationMs,
        instrumental: Boolean = false,
        synced: Boolean = true,
        plain: Boolean = true
    ): LyricsCandidate {
        return LyricsMatchingFixtures.candidate(
            providerId = providerId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            instrumental = instrumental,
            synced = synced,
            plain = plain
        )
    }

    private fun rank(candidates: List<LyricsCandidate>) = runBlocking {
        ranker.rank(LyricsMatchingFixtures.rankingRequest(candidates)).candidates
    }
}
