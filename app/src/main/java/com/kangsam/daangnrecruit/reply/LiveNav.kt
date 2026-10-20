package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.sync.DaangnNav
import com.kangsam.daangnrecruit.sync.UiNode
import com.kangsam.daangnrecruit.sync.UiTree

/**
 * 그 지원자에게 가는 누를 칸 찾기 — 실제 발송 시험과 첫 메시지 드라이런(둘 다 RoomRoute로 방까지 감)이 씀 (순수 코드 — 덤프로 시험).
 * 당근 가져오기(DaangnNav)는 지원서·'채팅하기'·입력창·전송을 절대 안 누르게 막아 둠. 그 사람에게 가는 칸만 여기서 따로 찾고,
 * 누르기 직전에 isSafe로 한 번 더 봄 (DaangnWalker.tap의 safe로 넘김). 입력창·전송은 실제 발송 시험만 씀 (드라이런은 있는지만 봄)
 *
 * - 지원자 목록(웹뷰 — 10/15 덤프): 그 사람 카드(이름이 같은 카드 하나 — 누르면 지원서), 읽은 카드 아래 '채팅하기' 버튼
 * - 지원서 (안 읽은 카드를 누르면 열림 — 아직 덤프 없음, 앱이 열 때 live_application·dryrun_application으로 남김):
 *   글자가 '채팅하기'인 누를 칸이 하나일 때만
 * - 채팅 목록: 그 사람 방 줄 (실제 발송 시험은 안 읽은 방이어도 엶 — 답하려면 열어야 함)
 * - 채팅방: 입력창(EditText), 전송('메세지 전송' — 10/15 덤프)
 */
object LiveNav {
    const val CHAT = "채팅하기"

    /** 누를 칸의 쓰임 (누르기 직전 확인에서 씀) */
    enum class Purpose { CARD, CHAT_BUTTON, ROOM_ROW, SEND }

    /** 실제 발송 시험에서도 절대 안 누르는 말 (다음 기회에·근무확정·추천·전화·첨부 등) */
    private val NEVER = listOf(
        "다음 기회", "근무확정", "추천", "당근페이", "선물", "전화", "통화", "신고", "차단", "삭제", "나가기", "마감", "수정",
        "광고", "결제", "구인중", "숨기", "공유", "기능 상자", "사진", "앨범", "카메라", "이모티콘",
    )

    /** 지원자 목록에서 그 사람 카드 (누르면 지원서). 이름이 정확히 같은 가장 안쪽 카드가 하나일 때만 */
    fun applicantCard(nodes: List<UiNode>, name: String): Int? {
        val cards = nodes.indices.filter { i ->
            val n = nodes[i]
            n.clickable && n.shortCls != "Button" && UiTree.subtree(nodes, i).any { j -> j != i && nodes[j].text.trim() == name }
        }
        val inner = cards.filter { c -> cards.none { o -> o != c && o in UiTree.subtree(nodes, c) } }
        return inner.singleOrNull()
    }

    /** 카드가 '안 읽은 지원서'(N)인지 */
    fun isUnreadCard(nodes: List<UiNode>, card: Int): Boolean = UiTree.subtree(nodes, card).any { nodes[it].text.trim() == "N" }

    /** 읽은 카드 아래 '채팅하기' (카드를 담은 칸 안, 카드 밖의 버튼). 안 읽은 카드엔 없음 */
    fun cardChatButton(nodes: List<UiNode>, card: Int): Int? {
        val holder = nodes[card].parent.takeIf { it >= 0 } ?: return null
        val inCard = UiTree.subtree(nodes, card)
        return UiTree.subtree(nodes, holder).firstOrNull { i -> i !in inCard && isChatButton(nodes, i) }
    }

    /** 지원서 화면의 '채팅하기' — 그런 칸이 하나일 때만 (여럿이면 지원자 목록처럼 다른 화면일 수 있어 null) */
    fun chatButton(nodes: List<UiNode>): Int? {
        val all = nodes.indices.filter { isChatButton(nodes, it) }
        val inner = all.filter { c -> all.none { o -> o != c && o in UiTree.subtree(nodes, c) } }
        return inner.singleOrNull()
    }

    /** 채팅 목록에서 그 사람 방 줄 (이름 + 동네가 맞는 줄이 하나일 때만 — 동네를 모르면 이름만) */
    fun roomRow(nodes: List<UiNode>, name: String, area: String): Int? =
        DaangnNav.chatRows(nodes).filter { it.name == name && (area.isBlank() || it.area == area) }.singleOrNull()?.index

    /** 채팅방 입력창 */
    fun input(nodes: List<UiNode>): Int? = DaangnNav.chatInput(nodes)

    /** 채팅방 입력창에 지금 쓰인 글자 (입력창이 없으면 null) */
    fun inputText(nodes: List<UiNode>): String? = input(nodes)?.let { nodes[it].text }

    /** 채팅방 전송 칸 */
    fun sendButton(nodes: List<UiNode>): Int? = DaangnNav.sendButton(nodes)

    /** 누르기 직전 확인: 그 쓰임에 맞는 칸이고, 절대 안 누르는 말이 없음 */
    fun isSafe(nodes: List<UiNode>, i: Int, purpose: Purpose, name: String): Boolean {
        if (i !in nodes.indices) return false
        val n = nodes[i]
        if (!n.clickable || n.shortCls == "EditText") return false
        val fits = when (purpose) {
            Purpose.CARD -> applicantCard(nodes, name) == i
            Purpose.CHAT_BUTTON -> isChatButton(nodes, i)
            Purpose.ROOM_ROW -> DaangnNav.chatRows(nodes).any { it.index == i && it.name == name }
            Purpose.SEND -> sendButton(nodes) == i
        }
        if (!fits) return false
        // 카드·방 줄은 안에 '추천 1' 같은 글자가 있어 자기 글자만, 버튼은 안쪽 글자까지 봄
        val words = if (purpose == Purpose.CARD || purpose == Purpose.ROOM_ROW) {
            "${n.text} ${n.desc}"
        } else {
            UiTree.subtree(nodes, i).joinToString(" ") { "${nodes[it].text} ${nodes[it].desc}" }
        }
        return NEVER.none { words.contains(it) }
    }

    /** 글자가 '채팅하기'인 누를 칸 (자기 글자·설명이거나, 안의 글자가 모두 '채팅하기') */
    private fun isChatButton(nodes: List<UiNode>, i: Int): Boolean {
        val n = nodes[i]
        if (!n.clickable) return false
        if (n.text.trim() == CHAT || n.desc.trim() == CHAT) return true
        val words = UiTree.subtree(nodes, i).flatMap { listOf(nodes[it].text.trim(), nodes[it].desc.trim()) }.filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { it == CHAT }
    }
}
