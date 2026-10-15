package com.kangsam.daangnrecruit.sync

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/*
 * 당근 채팅방 읽기 (개발 5단계, 화면 구조는 10/15 덤프로 확인).
 * 위쪽: 상대 이름 · '구인중 공고명' · 시급 · (근무확정·추천하기·당근페이·선물하기 — 절대 안 누름)
 * 메시지: 화면에 보이는 것만 읽힘 (옛날 메시지는 위로 올려 가며 이어 붙임 — prepend)
 * 내 말풍선은 오른쪽, 상대는 왼쪽. 시각(오전/오후 H:MM)은 말풍선 옆 아래, 날짜 줄(2026년 10월 11일)은 가운데.
 * 순수 코드라 덤프로 시험함.
 */

/** 채팅방 위쪽: 상대 이름과 이 방의 공고 */
data class ChatRoomHeader(val name: String, val postTitle: String, val postStatus: String)

/** 채팅방 한 줄: 날짜 줄이거나 말풍선 (시각은 화면 글자 그대로, 안 보이면 null) */
data class ChatItem(val isDate: Boolean, val text: String, val ours: Boolean = false, val time: String? = null)

/** 날짜·시각까지 정해진 메시지 한 개 */
data class ChatLine(val time: Long, val ours: Boolean, val text: String)

/** 사람이 연 채팅방에서 지금 보이는 것 (서비스가 그대로 읽어 저장) */
data class ScreenChatRoom(val header: ChatRoomHeader, val items: List<ChatItem>) : DaangnScreen

object ChatRoomParser {
    private val TIME = Regex("""^(오전|오후)\s*(\d{1,2}):(\d{2})$""")
    private val DATE = Regex("""^(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일""")
    private val STATUS = Regex("""^(구인\s*중|.*마감.*|채용\s*완료|숨김)$""")

    /** 채팅방이면 위쪽 정보와 보이는 메시지, 아니면 null */
    fun parse(nodes: List<UiNode>): ScreenChatRoom? {
        val header = header(nodes) ?: return null
        return ScreenChatRoom(header, window(nodes))
    }

    fun header(nodes: List<UiNode>): ChatRoomHeader? {
        if (nodes.none { it.viewId.endsWith("messagesComposeView") }) return null
        val toolbar = nodes.indexOfFirst { it.viewId.endsWith("id/toolbar") }
        val summary = nodes.indexOfFirst { it.viewId.endsWith("webSummary") }
        if (toolbar < 0 || summary < 0) return null
        val name = UiTree.subtree(nodes, toolbar).map { nodes[it] }
            .firstOrNull { it.shortCls == "TextView" && it.text.isNotBlank() }?.text?.trim() ?: return null
        val texts = UiTree.texts(nodes, summary)
        val st = texts.indexOfFirst { STATUS.matches(it) }
        val title = (if (st >= 0) texts.getOrNull(st + 1) else texts.firstOrNull()) ?: return null
        return ChatRoomHeader(name, title, if (st >= 0) texts[st] else "")
    }

    /** 메시지 목록에서 스크롤할 칸 */
    fun messageList(nodes: List<UiNode>): Int? {
        val host = nodes.indexOfFirst { it.viewId.endsWith("messagesComposeView") }
        if (host < 0) return null
        return UiTree.subtree(nodes, host).firstOrNull { nodes[it].scrollable }
    }

    /** 지금 보이는 날짜 줄·말풍선 (위에서 아래 차례) */
    fun window(nodes: List<UiNode>): List<ChatItem> {
        val host = nodes.indexOfFirst { it.viewId.endsWith("messagesComposeView") }
        if (host < 0) return emptyList()
        val width = nodes.first().let { it.right - it.left }.takeIf { it > 0 } ?: 1080
        val times = mutableListOf<UiNode>()
        val dates = mutableListOf<UiNode>()
        val bubbles = LinkedHashMap<Int, MutableList<String>>() // 말풍선 칸 → 그 안 글자들
        for (i in UiTree.subtree(nodes, host)) {
            val n = nodes[i]
            if (n.shortCls != "TextView" || n.text.isBlank()) continue
            val t = n.text.trim()
            val box = UiTree.clickableAncestor(nodes, i, stopAt = host)
            when {
                box == null && TIME.matches(t) -> times.add(n)
                box == null && DATE.containsMatchIn(t) -> dates.add(n)
                box != null -> bubbles.getOrPut(box) { mutableListOf() }.add(t)
            }
        }
        val out = mutableListOf<Pair<Int, ChatItem>>()
        for (d in dates) out.add(d.top to ChatItem(isDate = true, text = d.text.trim()))
        for ((boxIndex, texts) in bubbles) {
            val box = nodes[boxIndex]
            val ours = box.centerX > width / 2
            // 시각: 말풍선 아래쪽 끝과 줄이 맞고, 내 말이면 왼쪽·상대 말이면 오른쪽에 있는 것
            val time = times.filter { t ->
                abs(t.bottom - box.bottom) <= 40 && (if (ours) t.right <= box.left + 8 else t.left >= box.right - 8)
            }.minByOrNull { abs(it.bottom - box.bottom) }?.text?.trim()
            out.add(box.top to ChatItem(isDate = false, text = texts.joinToString("\n"), ours = ours, time = time))
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    /**
     * 위로 올려서 읽은 창(older)을 지금까지 모은 것(acc) 앞에 붙임. 겹치는 줄은 한 번만.
     * 겹친 줄의 시각은 둘 중 보이는 쪽 것을 씀 (화면 끝에 걸친 말풍선은 시각이 안 보일 수 있음)
     */
    fun prepend(older: List<ChatItem>, acc: List<ChatItem>): List<ChatItem> {
        if (acc.isEmpty()) return older
        var best = 0
        for (k in minOf(older.size, acc.size) downTo 1) {
            if ((0 until k).all { same(older[older.size - k + it], acc[it]) }) {
                best = k
                break
            }
        }
        val merged = acc.toMutableList()
        for (j in 0 until best) {
            val o = older[older.size - best + j]
            if (merged[j].time == null && o.time != null) merged[j] = merged[j].copy(time = o.time)
        }
        return older.subList(0, older.size - best) + merged
    }

    private fun same(a: ChatItem, b: ChatItem) = a.isDate == b.isDate && a.ours == b.ours && a.text == b.text

    /**
     * 날짜·시각 정하기. 같은 사람이 같은 분에 연달아 보내면 마지막 말풍선에만 시각이 보여서, 그 시각을 위쪽에도 씀.
     * 날짜 줄보다 위에 있어 날짜를 모르는 메시지는 빼고, 뺀 수를 같이 돌려줌
     */
    fun resolve(items: List<ChatItem>, zone: ZoneId): Pair<List<ChatLine>, Int> {
        val times = arrayOfNulls<String>(items.size)
        var carry: String? = null
        var carryOurs: Boolean? = null
        for (i in items.indices.reversed()) {
            val item = items[i]
            if (item.isDate) {
                carry = null
                carryOurs = null
                continue
            }
            if (item.ours != carryOurs) {
                carry = null
                carryOurs = item.ours
            }
            if (item.time != null) carry = item.time
            times[i] = carry
        }
        var date: LocalDate? = null
        var skipped = 0
        val out = mutableListOf<ChatLine>()
        for ((i, item) in items.withIndex()) {
            if (item.isDate) {
                date = day(item.text) ?: date
                continue
            }
            val clock = times[i]?.let { clock(it) }
            val d = date
            if (d == null || clock == null) {
                skipped++
                continue
            }
            out.add(ChatLine(d.atTime(clock).atZone(zone).toInstant().toEpochMilli(), item.ours, item.text))
        }
        return out to skipped
    }

    private fun clock(text: String): LocalTime? {
        val m = TIME.matchEntire(text.trim()) ?: return null
        var h = m.groupValues[2].toInt() % 12
        if (m.groupValues[1] == "오후") h += 12
        return LocalTime.of(h, m.groupValues[3].toInt())
    }

    private fun day(text: String): LocalDate? = DATE.find(text)?.let {
        LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
}
