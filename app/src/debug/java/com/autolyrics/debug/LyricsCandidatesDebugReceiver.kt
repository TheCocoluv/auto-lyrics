package com.autolyrics.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autolyrics.lyrics.LrcLibClient
import kotlin.concurrent.thread

class LyricsCandidatesDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        thread(name = "lyrics-candidates-debug") {
            try {
                val candidates = LrcLibClient.searchCandidates(
                    trackName = TEST_TRACK,
                    artistName = TEST_ARTIST
                )

                Log.d(TAG, "candidate count=${candidates.size}")
                candidates.forEachIndexed { index, candidate ->
                    Log.d(
                        TAG,
                        "candidate[$index] " +
                            "provider=${candidate.provider}, " +
                            "title=${candidate.title}, " +
                            "artist=${candidate.artist}, " +
                            "album=${candidate.album}, " +
                            "durationMs=${candidate.durationMs}, " +
                            "synced=${candidate.hasSyncedLyrics}, " +
                            "plain=${candidate.hasPlainLyrics}"
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "candidate search failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AutoLyricsCandidateTest"
        const val TEST_TRACK = "Numb"
        const val TEST_ARTIST = "Linkin Park"
    }
}
