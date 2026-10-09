package com.kangsam.daangnrecruit

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 당근 알림을 골라서 폰 안에 기록만 하는 서비스.
 * 알림을 지우거나 누르는 동작은 하지 않는다 (읽기 전용).
 * 다중 계정 앱 안에 당근이 여러 개라, 어느 계정 알림인지 구분할 단서를 찾으려고 정보를 넉넉히 기록한다.
 */
class DaangnNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "알림 리스너 연결됨")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 앱 화면에서 기록을 켜둔 경우에만 동작 (기본 OFF)
        if (!NotificationLog.isEnabled(this)) return

        // 정해둔 앱에서 온 알림만 기록
        if (sbn.packageName !in WATCHED_PACKAGES) return

        val n = sbn.notification
        val extras = n.extras
        val entry = JSONObject().apply {
            put("time", sbn.postTime)
            put("package", sbn.packageName)
            put("id", sbn.id)
            put("tag", sbn.tag ?: "")
            put("key", sbn.key)
            put("channel", n.channelId ?: "")
            put("group", n.group ?: "")
            put("ticker", n.tickerText?.toString() ?: "")
            put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
            put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")
            put("bigText", extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: "")
            put("subText", extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "")
            put("conversationTitle", extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: "")
            put("extrasKeys", extras.keySet().joinToString(","))
        }
        NotificationLog.append(this, entry)
        Log.i(TAG, "기록함: $entry")
    }

    companion object {
        private const val TAG = "DaangnListener"

        // 기록할 앱 목록
        val WATCHED_PACKAGES = setOf(
            "com.towneers.www",              // 당근 (기본 앱)
            "com.excelliance.multiaccounts", // 다중 계정 앱 (구인 계정 당근들이 이 안에서 실행됨)
            "com.android.shell",             // 테스트용 (adb로 보내는 테스트 알림), 나중에 뺄 것
        )
    }
}

/** 기록 파일과 ON/OFF 설정을 다루는 곳 (폰 안 이 앱 전용 저장소에만 저장) */
object NotificationLog {
    private const val FILE_NAME = "daangn_notifications.jsonl"
    private const val PREFS_NAME = "settings"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    @Synchronized
    fun append(context: Context, entry: JSONObject) {
        File(context.filesDir, FILE_NAME).appendText(entry.toString() + "\n")
    }

    fun readRecent(context: Context, limit: Int = 20): List<JSONObject> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .takeLast(limit)
            .reversed()
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
    }
}