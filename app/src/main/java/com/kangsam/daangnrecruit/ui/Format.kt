package com.kangsam.daangnrecruit.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 화면에 쓰는 시각·글자 다듬기 (화면 파일들이 같이 씀)

private val TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
private val CLOCK_FORMAT = SimpleDateFormat("HH:mm", Locale.KOREA)
private val DATE_FORMAT = SimpleDateFormat("M/d", Locale.KOREA)
private val DAY_FORMAT = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)

/** 10/18 21:40 (날짜까지 — 기록·개발용 칸) */
internal fun formatTime(time: Long): String = TIME_FORMAT.format(Date(time))

/** 오늘이면 14:40, 아니면 10/18 21:40 (목록·말풍선) */
internal fun formatClock(time: Long, now: Long = System.currentTimeMillis()): String =
    if (sameDay(time, now)) CLOCK_FORMAT.format(Date(time)) else TIME_FORMAT.format(Date(time))

/** 14:40 (시각만) */
internal fun formatHourMinute(time: Long): String = CLOCK_FORMAT.format(Date(time))

/** 10/2 (날짜만 — 지난 회차) */
internal fun formatDate(time: Long): String = DATE_FORMAT.format(Date(time))

/** 10월 13일 (화) */
internal fun formatDay(time: Long): String = DAY_FORMAT.format(Date(time))

internal fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

internal fun isToday(time: Long, now: Long = System.currentTimeMillis()): Boolean = sameDay(time, now)

/** 오늘 0시 */
internal fun startOfDay(now: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** 지원자 목록에 쓰는 공고 이름: '송도 · 중등 수학' → '송도 중등 수학' (뒤에 ' · 지금 하는 일'이 붙어서 가운뎃점을 뺌) */
internal fun shortPostName(name: String): String = name.replace(" · ", " ").trim()

/** 마지막 글자에 받침이 있는지 (한글이 아니면 없음으로 봄) */
private fun hasBatchim(word: String): Boolean {
    val c = word.trim().lastOrNull() ?: return false
    if (c !in '가'..'힣') return false
    return (c - '가') % 28 != 0
}

/** ㄹ 받침인지 ('(으)로'는 ㄹ 받침 뒤에 '로') */
private fun hasRieulBatchim(word: String): Boolean {
    val c = word.trim().lastOrNull() ?: return false
    return c in '가'..'힣' && (c - '가') % 28 == 8
}

/** '문자 진행 중' + 으로 / '넘김' + 으로 / '새 지원' + 으로 → 앞말 + (으)로 */
internal fun withEuro(word: String): String = word + if (hasBatchim(word) && !hasRieulBatchim(word)) "으로" else "로"

/** 앞말 + 와/과 */
internal fun withWaGwa(word: String): String = word + if (hasBatchim(word)) "과" else "와"
