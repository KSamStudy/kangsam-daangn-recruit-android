package com.kangsam.daangnrecruit.reply

import android.content.Context
import com.kangsam.daangnrecruit.FeatureSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 실제 발송 시험 설정 (폰 안 앱 설정 'live_send' — DB 표가 아니라 앱 설정, 기능 켜기·끄기와 같은 자리). 처음엔 꺼짐.
 * 모드(끔·연습·실제 발송) · 테스트 폰 당근 닉네임 · 켠 때 · 최근 결과·기록.
 * 판단에 쓰는 값은 config()로 묶어 LiveGate(순수 코드)에 넘김 — 판단 규칙은 여기 말고 LiveGate에
 */
object LiveSettings {
    private const val PREFS = "live_send"
    private const val MAX_RESULTS = 20 // 최근 결과 줄 수
    private const val MAX_LOG = 120    // 마지막 차례 기록 줄 수

    fun mode(context: Context): LiveGate.Mode {
        val name = prefs(context).getString("mode", null) ?: return LiveGate.Mode.OFF
        return LiveGate.Mode.entries.firstOrNull { it.name == name } ?: LiveGate.Mode.OFF
    }

    /** 모드 바꾸기. 끔 → 연습·실제 발송으로 켤 때 '켠 때'를 지금으로 (연습 ↔ 실제 발송은 그대로 — 연습한 지원으로 이어 가게) */
    fun setMode(context: Context, mode: LiveGate.Mode) {
        val before = mode(context)
        val e = prefs(context).edit().putString("mode", mode.name)
        if (before == LiveGate.Mode.OFF && mode != LiveGate.Mode.OFF) e.putLong("armed_at", System.currentTimeMillis())
        e.apply()
    }

    /** 테스트 폰 당근 닉네임 (화면에 보이는 그대로) */
    fun testName(context: Context): String = prefs(context).getString("test_name", "").orEmpty()

    fun setTestName(context: Context, name: String) {
        prefs(context).edit().putString("test_name", name.trim()).apply()
    }

    /** 시험을 켠 때 (끔 → 연습·실제 발송) */
    fun armedAt(context: Context): Long = prefs(context).getLong("armed_at", 0L)

    /** 판단에 쓰는 값 한 묶음 (LiveGate) — '당근 응대'·'전체 멈춤' 스위치도 같이 */
    fun config(context: Context) = LiveGate.Config(
        mode = mode(context),
        testName = testName(context),
        armedAt = armedAt(context),
        daangnReply = FeatureSettings.isOn(context, FeatureSettings.DAANGN_REPLY),
        allStop = FeatureSettings.isOn(context, FeatureSettings.ALL_STOP),
    )

    /** 한 차례 결과를 남김 (최근 것이 앞) + 그 차례 기록 */
    @Synchronized
    fun saveResult(context: Context, line: String, log: List<String>) {
        val stamp = SimpleDateFormat("M/d HH:mm:ss", Locale.KOREA).format(Date())
        val results = (listOf("$stamp $line".replace('\n', ' ')) + results(context)).take(MAX_RESULTS)
        prefs(context).edit()
            .putString("results", results.joinToString("\n"))
            .putString("log", log.takeLast(MAX_LOG).joinToString("\n") { it.replace('\n', ' ') })
            .apply()
    }

    /** 최근 결과 (최근 것이 앞) */
    fun results(context: Context): List<String> =
        prefs(context).getString("results", "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** 마지막 차례의 단계별 기록 */
    fun lastLog(context: Context): List<String> =
        prefs(context).getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** 연습을 마친 지원자 (연습은 지원자마다 저절로는 한 번만 — 다시 하려면 '지금 한 번 확인') */
    fun rehearsed(context: Context): Set<Long> =
        prefs(context).getString("rehearsed", "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    fun addRehearsed(context: Context, applicantId: Long) {
        val ids = (rehearsed(context) + applicantId).toList().takeLast(50)
        prefs(context).edit().putString("rehearsed", ids.joinToString(",")).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
