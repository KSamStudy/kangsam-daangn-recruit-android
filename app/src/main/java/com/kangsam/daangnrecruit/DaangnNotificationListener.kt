package com.kangsam.daangnrecruit

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 당근 알림을 읽어서 (1) 알림 원문을 기록 파일에 남기고, (2) 지원 알림이면 폰 안 DB에 지원자 한 줄을 남기는 서비스 (사용자 프로세스 B1).
 * 알림을 지우거나 누르는 동작은 하지 않는다 (읽기 전용).
 * 오류가 나도 서비스가 멈추지 않게 잡아서 오류 기록(DB)에 남긴다.
 */
class DaangnNotificationListener : NotificationListenerService() {

    // DB 쓰기는 화면(메인) 말고 뒤에서 함
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        Log.i(TAG, "알림 리스너 연결됨")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 앱 화면에서 기록을 켜둔 경우에만 동작 (기본 OFF). 끄면 기록·지원 감지 모두 멈춤 (수동 중지)
        if (!NotificationLog.isEnabled(this)) return

        // 정해둔 앱에서 온 알림만
        if (sbn.packageName !in WATCHED_PACKAGES) return

        // (1) 알림 원문 기록
        runCatching { NotificationLog.append(this, toJson(sbn)) }
            .onSuccess { Log.i(TAG, "기록함: ${sbn.packageName}") }
            .onFailure { saveError("알림 기록", it) }

        // (2) 지원 알림이면 지원자 한 줄 저장 (같은 알림은 두 번 저장하지 않음)
        val found = runCatching { ApplyDetector.detect(sbn) }
            .onFailure { saveError("지원 감지", it) }
            .getOrDefault(emptyList())
        if (found.isEmpty()) return
        scope.launch {
            val dao = AppDatabase.get(applicationContext).applicantDao()
            for (a in found) {
                runCatching { dao.insert(a) }
                    .onSuccess { id -> if (id > 0) Log.i(TAG, "새 지원 저장: ${a.name}") }
                    .onFailure { saveError("지원 저장", it) }
            }
        }
    }

    /** 알림 한 건을 기록 파일용 JSON으로 바꿈 */
    private fun toJson(sbn: StatusBarNotification): JSONObject {
        val n = sbn.notification
        val extras = n.extras
        return JSONObject().apply {
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
            put("messages", messagesJson(n))
            put("extrasKeys", extras.keySet().joinToString(","))
        }
    }

    /** 채팅 알림이면 묶여 있는 메시지(보낸 사람·내용·시각)를 꺼냄. 채팅 알림이 아니면 빈 목록 */
    private fun messagesJson(n: Notification): JSONArray {
        val list = JSONArray()
        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull() ?: return list
        for (m in style.messages) {
            list.put(JSONObject().apply {
                put("sender", m.person?.name?.toString() ?: "")
                put("text", m.text?.toString() ?: "")
                put("time", m.timestamp)
            })
        }
        return list
    }

    /** 오류를 로그캣과 폰 안 DB(오류 기록)에 남김. 여기서 또 실패해도 서비스는 계속 돎 */
    private fun saveError(place: String, e: Throwable) {
        Log.e(TAG, "$place 오류", e)
        scope.launch {
            runCatching {
                AppDatabase.get(applicationContext).errorLogDao().insert(
                    ErrorLog(time = System.currentTimeMillis(), place = place, message = e.toString())
                )
            }
        }
    }

    companion object {
        private const val TAG = "DaangnListener"

        // 기록할 앱 목록
        val WATCHED_PACKAGES = setOf(
            "com.towneers.www",              // 당근 (기본 앱, 10/15부터 구인 계정은 이 앱 하나로 씀)
            "com.excelliance.multiaccounts", // 다중 계정 앱 (10/15 폰에서 없어짐, 나중에 뺄 것)
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