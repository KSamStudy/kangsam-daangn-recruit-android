package com.kangsam.daangnrecruit.sync

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.kangsam.daangnrecruit.DaangnAccessibilityService
import com.kangsam.daangnrecruit.FeatureSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 프로세스 C '당근 가져오기' — 따로 켜고 끄는 기능 하나 (게시 A·응대 B와 상관없이 혼자 돎).
 * 하는 일: 당근 화면을 읽어 공고·지원자·대화를 앱 기록에 맞춤. 당근에 쓰는 일(보내기·올리기·마감·수정)은 없음.
 * 언제: ① 켜져 있으면 사람이 당근 화면을 열 때마다 ② 설정의 '당근에서 가져오기' 버튼
 *       (③ 정해진 시간마다 · ④ 알림 올 때마다는 다음 단계 — 설정에서 고르게만 해 둠)
 * 켜져 있으면 지원자가 보낸 채팅 메시지 알림도 그 지원자 대화에 남김 (ChatNotify, 알림 리스너가 부름 — 10/18)
 * 이 기능의 코드는 모두 sync 폴더. 폰 화면을 다루는 손은 접근성 서비스(DaangnAccessibilityService)가 빌려줌
 */
object DaangnSync {
    const val MODE_BUTTON = "버튼 누를 때만"
    const val MODE_SCHEDULE = "정해진 시간마다"
    const val MODE_NOTIFICATION = "알림 올 때마다"
    const val MAX_RUNS_PER_DAY = 10 // 하루에 버튼으로 도는 횟수 한도

    // 10/15 '당근 화면 읽기' 스위치 값을 이 기능의 스위치로 그대로 이어 씀
    private const val PREFS = "screen_read"

    private val _status = MutableStateFlow("")

    /** 지금 하는 일 한 줄 (설정 탭에 보임) */
    val status: StateFlow<String> = _status

    /** 지금 버튼으로 도는 중인지 (그동안은 사람이 연 화면 읽기를 쉼) */
    @Volatile
    var running: Boolean = false
        private set

    /** 기능 스위치 (기본 꺼짐) */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    /** 지금 돌아도 되는지: 스위치가 켜져 있고 '전체 멈춤'이 아님 */
    fun canRun(context: Context): Boolean = isOn(context) && !FeatureSettings.isOn(context, FeatureSettings.ALL_STOP)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
    }

    /** 언제 가져올지 (지금은 버튼만 돎) */
    fun mode(context: Context): String = prefs(context).getString("mode", MODE_BUTTON) ?: MODE_BUTTON

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString("mode", mode).apply()
    }

    /** 마지막으로 한 일 (시각, 한 줄) — 사람이 연 화면을 읽은 것과 버튼으로 돈 것 모두 */
    fun last(context: Context): Pair<Long, String>? {
        val p = prefs(context)
        val message = p.getString("last", null) ?: return null
        return p.getLong("last_at", 0L) to message
    }

    fun saveLast(context: Context, message: String) {
        prefs(context).edit().putString("last", message).putLong("last_at", System.currentTimeMillis()).apply()
    }

    /** 지난번 버튼 실행의 단계별 기록 */
    fun lastLog(context: Context): List<String> =
        prefs(context).getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** 오늘 버튼으로 돈 횟수 */
    fun runsToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString("day", "") == LocalDate.now().toString()) p.getInt("count", 0) else 0
    }

    /** 폰 설정 → 접근성에서 이 앱의 '당근 화면 읽기'가 켜져 있는지 (화면을 읽고 누르는 손) */
    fun isServiceOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, DaangnAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** 설정의 '당근에서 가져오기' 버튼: 한 번 돌기 시작. 안내 한 줄을 돌려줌 */
    fun start(context: Context): String {
        if (!isOn(context)) return "'당근 가져오기'가 꺼져 있어요 — 위 스위치를 켜요"
        if (!canRun(context)) return "'전체 멈춤'이 켜져 있어서 안 돌아요"
        val host = DaangnAccessibilityService.instance
            ?: return "접근성(당근 화면 읽기)이 꺼져 있어요 — 폰 설정 → 접근성에서 켜요"
        if (running) return "이미 가져오는 중이에요"
        if (runsToday(context) >= MAX_RUNS_PER_DAY) return "오늘은 하루 한도(${MAX_RUNS_PER_DAY}번)를 다 썼어요"
        running = true
        countRun(context)
        say("시작")
        host.runSync { result, log -> finished(context.applicationContext, result, log) }
        return "당근을 열어서 가져와요 — 끝날 때까지 폰을 만지지 마세요 (멈추려면 홈 버튼)"
    }

    /** 지금 하는 일 한 줄 바꾸기 */
    fun say(line: String) {
        _status.value = line
    }

    private fun finished(context: Context, result: String, log: List<String>) {
        running = false
        saveLast(context, result)
        prefs(context).edit().putString("log", log.takeLast(60).joinToString("\n")).apply()
        say(result)
    }

    private fun countRun(context: Context) {
        prefs(context).edit()
            .putString("day", LocalDate.now().toString())
            .putInt("count", runsToday(context) + 1)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
