package com.bph.geosusuaudio

import android.content.Context

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("geo_susu_memory", Context.MODE_PRIVATE)

    fun saveLast(heard: String, answer: String, itemId: String, score: Int) {
        prefs.edit()
            .putString("last_heard", heard)
            .putString("last_answer", answer)
            .putString("last_item_id", itemId)
            .putInt("last_score", score)
            .putLong("last_time", System.currentTimeMillis())
            .putInt("total_answers", getTotalAnswers() + 1)
            .apply()
    }

    fun getTotalAnswers(): Int = prefs.getInt("total_answers", 0)

    fun getLastHeard(): String = prefs.getString("last_heard", "").orEmpty()

    fun getLastAnswer(): String = prefs.getString("last_answer", "").orEmpty()

}
