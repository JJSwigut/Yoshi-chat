package com.example.yoshichat.data.thread

import android.content.Context

class ThreadSessionRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentThreadId(): String? =
        preferences.getString(KEY_CURRENT_THREAD_ID, null)?.takeIf { it.isNotBlank() }

    fun recentThreadIds(): List<String> =
        preferences.getString(KEY_RECENT_THREAD_IDS, null)
            ?.split(DELIMITER)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun saveCurrentThread(threadId: String) {
        val updated =
            (listOf(threadId) + recentThreadIds())
                .distinct()
                .take(MAX_RECENT_THREADS)
        preferences.edit()
            .putString(KEY_CURRENT_THREAD_ID, threadId)
            .putString(KEY_RECENT_THREAD_IDS, updated.joinToString(DELIMITER))
            .apply()
    }

    fun forgetThread(threadId: String) {
        val updated = recentThreadIds().filterNot { it == threadId }
        val editor =
            preferences.edit()
                .putString(KEY_RECENT_THREAD_IDS, updated.joinToString(DELIMITER))
        if (currentThreadId() == threadId) {
            editor.remove(KEY_CURRENT_THREAD_ID)
        }
        editor.apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "yoshi_thread_sessions"
        private const val KEY_CURRENT_THREAD_ID = "current_thread_id"
        private const val KEY_RECENT_THREAD_IDS = "recent_thread_ids"
        private const val DELIMITER = "\n"
        private const val MAX_RECENT_THREADS = 8
    }
}
