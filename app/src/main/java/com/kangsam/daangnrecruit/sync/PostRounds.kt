package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.Posting

/**
 * 당근 공고 상세의 글이 앱의 몇 회차인지 가리기 (DB 설계 '당근 가져오기로 들어온 공고' — 회차 가리기). 순수 코드.
 * 같은 상시 공고는 회차마다 공고명이 같아서, 마감된 글 상세를 열었을 때 공고명만으로는 어느 회차인지 모름 →
 * 상세의 올린 때('N분·N시간·N일 전')로 게시 시각이 들 수 있는 폭을 만들고, 같은 공고명 게시물 중 그 폭에 드는 것이 하나일 때만 그 회차.
 * - 당근은 지난 시간을 내려서 보여 줌 (2.7일 → '2일 전') → 실제는 [N+1 단위 전, N 단위 전]. 앞뒤로 한 단위씩 더 여유
 * - '끌올 N일 전'이면 끌어올린 때라서 마지막 끌어올린 시각과 견줌
 */
object PostRounds {
    /** 게시 시각(끌올이면 마지막 끌어올린 시각)이 들 수 있는 폭 */
    data class Window(val from: Long, val to: Long, val bumped: Boolean)

    private val AGO = Regex("""^(끌올\s*)?(?:(\d+)\s*(초|분|시간|일|주|개월|달|년)|방금)\s*전$""")
    private const val MINUTE = 60_000L

    /** '3일 전' → [5일 전, 2일 전], '방금 전' → [2분 전, 지금]. 못 읽으면 null */
    fun window(ago: String, now: Long): Window? {
        val m = AGO.matchEntire(ago.trim()) ?: return null
        val bumped = m.groupValues[1].isNotBlank()
        val n = m.groupValues[2].toLongOrNull() ?: return Window(now - 2 * MINUTE, now, bumped) // 방금 전
        val unit = unitMs(m.groupValues[3]) ?: return null
        return Window(from = now - (n + 2) * unit, to = now - maxOf(n - 1, 0L) * unit, bumped = bumped)
    }

    /** 폭에 드는 게시물 (끌올이면 마지막 끌어올린 시각, 아니면 게시 시각으로) */
    fun hits(candidates: List<Posting>, w: Window): List<Posting> = candidates.filter { p ->
        val t = if (w.bumped) p.lastBumpAt else p.postedAt
        t != null && t in w.from..w.to
    }

    /** 화면의 글에 맞는 회차 — 폭에 드는 게시물이 딱 하나일 때만, 아니면 null (못 가림) */
    fun match(candidates: List<Posting>, w: Window): Posting? = hits(candidates, w).singleOrNull()

    private fun unitMs(unit: String): Long? = when (unit) {
        "초" -> 1_000L
        "분" -> MINUTE
        "시간" -> 60 * MINUTE
        "일" -> 24 * 60 * MINUTE
        "주" -> 7 * 24 * 60 * MINUTE
        "개월", "달" -> 30 * 24 * 60 * MINUTE
        "년" -> 365 * 24 * 60 * MINUTE
        else -> null
    }
}
