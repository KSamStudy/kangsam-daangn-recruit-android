package com.kangsam.daangnrecruit.sync

/*
 * 당근 안에서 길 찾기 (개발 5단계 '당근에서 가져오기'). 화면 구조는 10/15 덤프 6장으로 확인:
 * 나의 당근 · 구인글 관리 · 공고 상세 · 지원자 목록 · 채팅 목록 · 채팅방.
 * 어느 화면인지 알아보고, 누를 칸을 찾고, 누르면 안 되는 칸을 막는 일만 함 (실제로 누르는 건 SyncRunner).
 * 순수 코드라 덤프로 시험함.
 */

/** 앱이 다니는 당근 화면 */
enum class DaangnPage(val label: String) {
    MY_PAGE("나의 당근"),
    JOBS("구인글 관리"),
    POST_DETAIL("공고 상세"),
    APPLICANT_LIST("지원자 목록"),
    CHAT_LIST("채팅 목록"),
    CHAT_ROOM("채팅방"),
    OTHER("다른 화면"),
}

/** 채팅 목록의 지원자 방 한 줄 (당근·당근알바 같은 공식 방은 빼고). ago = 마지막 대화 때 ('3일 전'·'2달 전' — 화면 글자 그대로) */
data class ChatRow(val index: Int, val name: String, val area: String, val unread: Boolean, val ago: String = "")

object DaangnNav {
    private val JOBS_ROW = Regex("""^진행중인 구인\s*\d+$""")
    private val JOBS_HEADER = Regex("""^구인중\s*\d+$""")
    private val VIEWS = Regex("""^조회\s*\d+""")
    private val APPLICANTS = Regex("""^지원자\s*\d+\s*명$""")
    private val VIEWS_IN = Regex("""조회\s*[\d,]+""")
    private val APPLICANTS_IN = Regex("""지원자\s*[\d,]+\s*명""")
    private val LIST_TAB = Regex("""^지원자\s*\d+$""")

    /** 이 글자가 화면에 보이면 바로 멈춤 (보안 인증·이용 제한 화면 — 기능_목표 진행 규칙 '즉시 중단') */
    val STOP_WORDS = listOf(
        "이용이 제한", "이용 제한", "보안 문자", "자동 입력 방지", "로봇이 아닙니다",
        "비정상적인", "계정이 정지", "다시 로그인", "로그인이 필요",
    )

    /** 누를 칸 자신의 글자·설명에 이 말이 있으면 절대 안 누름 (글쓰기·전송·마감·수정·상태·광고·추천·근무확정·당근페이 등) */
    private val NEVER_CLICK = listOf(
        "마감", "수정", "광고", "추천", "채팅하기", "근무확정", "당근페이", "선물", "재등록", "글쓰기", "계정 만들기",
        "삭제", "차단", "신고", "전송", "보내기", "전화", "결제", "구인중", "사진 올리기", "나가기", "다음 기회",
        "끌어올리기", "끌올", "숨기", "공유", "지원서", "이력서", "통화",
    )

    /** 앱이 누르는 아래 탭 (이 둘만) */
    private val TABS = listOf("나의 당근", "채팅")

    /** 지금 어느 화면인지 */
    fun page(nodes: List<UiNode>): DaangnPage {
        if (nodes.any { it.viewId.endsWith("messagesComposeView") }) return DaangnPage.CHAT_ROOM
        if (nodes.any { it.text.trim() == "지원자 목록" } && nodes.any { LIST_TAB.matches(it.text.trim()) }) {
            return DaangnPage.APPLICANT_LIST
        }
        if (nodes.any { it.clickable && it.text.trim().startsWith("지원자 보기") }) return DaangnPage.POST_DETAIL
        if (nodes.any { it.shortCls == "TextView" && it.text.trim() == "구인글 관리" }) return DaangnPage.JOBS
        return when (selectedBottomTab(nodes)) {
            "나의 당근" -> DaangnPage.MY_PAGE
            "채팅" -> DaangnPage.CHAT_LIST
            else -> DaangnPage.OTHER
        }
    }

    /** 화면에 멈춤 글자가 있으면 그 글자 */
    fun stopWord(nodes: List<UiNode>): String? =
        nodes.firstNotNullOfOrNull { n -> STOP_WORDS.firstOrNull { n.text.contains(it) } }

    /** 멈춤 글자가 든 칸의 글 전체 (당근 안내 문구 — 계정 상태 기록에 그대로 남김, 200자까지) */
    fun stopNotice(nodes: List<UiNode>): String =
        nodes.firstOrNull { n -> STOP_WORDS.any { n.text.contains(it) } }?.text?.trim()?.take(200).orEmpty()

    /** 아래 탭(홈·커뮤니티·동네지도·채팅·나의 당근)이 있는 화면인지 */
    fun hasBottomNav(nodes: List<UiNode>): Boolean = nodes.any { it.viewId.endsWith("bottomNavCompose") }

    /** 아래 탭 중 지금 골라진 것 */
    fun selectedBottomTab(nodes: List<UiNode>): String? {
        val nav = nodes.indexOfFirst { it.viewId.endsWith("bottomNavCompose") }
        if (nav < 0) return null
        for (i in UiTree.subtree(nodes, nav)) {
            if (!nodes[i].selected) continue
            return UiTree.subtree(nodes, i).map { nodes[it].desc }.firstOrNull { it.isNotBlank() }
        }
        return null
    }

    /** 아래 탭에서 누를 칸 (지금 골라진 탭은 누를 수 없어 null) */
    fun bottomTab(nodes: List<UiNode>, label: String): Int? {
        val nav = nodes.indexOfFirst { it.viewId.endsWith("bottomNavCompose") }
        if (nav < 0) return null
        val d = UiTree.subtree(nodes, nav).firstOrNull { nodes[it].desc == label } ?: return null
        return UiTree.clickableAncestor(nodes, d, stopAt = nav)
    }

    /**
     * 나의 당근의 '진행중인 구인' 공고 카드들: (누를 칸, 공고명). 누르면 공고 상세 (10/15 재걸이 직접 연 길).
     * 카드 글자 = [공고명, '동네 ⸱ 조회 N ⸱ 지원자 N명', 상태]. 같은 모양을 품은 더 큰 칸은 빼고 가장 안쪽 칸만
     */
    fun myPostCards(nodes: List<UiNode>): List<Pair<Int, String>> {
        fun isCard(i: Int): Boolean {
            if (!nodes[i].clickable) return false
            val texts = UiTree.texts(nodes, i)
            return texts.any { VIEWS_IN.containsMatchIn(it) && APPLICANTS_IN.containsMatchIn(it) }
        }
        val out = mutableListOf<Pair<Int, String>>()
        for (i in nodes.indices) {
            if (!isCard(i)) continue
            val inner = UiTree.subtree(nodes, i).any { j -> j != i && isCard(j) }
            if (inner) continue
            val title = UiTree.texts(nodes, i).firstOrNull() ?: continue
            out.add(i to title)
        }
        return out
    }

    /** 나의 당근의 '진행중인 구인 N'에서 N (안 보이면 null) */
    fun liveRowCount(nodes: List<UiNode>): Int? =
        nodes.firstOrNull { JOBS_ROW.matches(it.text.trim()) }?.text?.filter { it.isDigit() }?.toIntOrNull()

    /** 나의 당근의 '진행중인 구인 N' 줄 (누르면 구인글 관리 — 지금 가져오기에선 안 씀) */
    fun jobsRow(nodes: List<UiNode>): Int? {
        val t = nodes.indices.firstOrNull { JOBS_ROW.matches(nodes[it].text.trim()) } ?: return null
        return UiTree.clickableAncestor(nodes, t)
    }

    /** 구인글 관리의 '구인중 N'에서 N (아직 안 떴으면 null) */
    fun liveCount(nodes: List<UiNode>): Int? =
        nodes.firstOrNull { JOBS_HEADER.matches(it.text.trim()) }?.text?.filter { it.isDigit() }?.toIntOrNull()

    /** 구인글 관리의 '구인중 N' 아래 공고 카드들: (누를 칸, 공고명). '새 글쓰기'·'최근 마감된 공고'에서 멈춤 */
    fun jobCards(nodes: List<UiNode>): List<Pair<Int, String>> {
        val header = nodes.indexOfFirst { JOBS_HEADER.matches(it.text.trim()) }
        if (header < 0) return emptyList()
        val out = mutableListOf<Pair<Int, String>>()
        var i = header + 1
        while (i < nodes.size) {
            val n = nodes[i]
            val t = n.text.trim()
            if (t == "새 글쓰기" || t == "최근 마감된 공고") break
            if (n.clickable && n.shortCls == "View") {
                val range = UiTree.subtree(nodes, i)
                val texts = UiTree.texts(nodes, i)
                if (texts.any { VIEWS.containsMatchIn(it) } && texts.any { APPLICANTS.matches(it) }) {
                    // 공고명: 카드 그림의 설명(공고명과 같음), 없으면 업직종 다음 글자
                    val title = range.map { nodes[it] }.firstOrNull { it.isImage && it.desc.isNotBlank() }?.desc?.trim()
                        ?: texts.getOrNull(1).orEmpty()
                    if (title.isNotBlank()) out.add(i to title)
                    i = range.last + 1
                    continue
                }
            }
            i++
        }
        return out
    }

    /** 공고 상세의 '지원자 보기 N' 버튼 */
    fun applicantsButton(nodes: List<UiNode>): Int? =
        nodes.indices.firstOrNull { nodes[it].clickable && nodes[it].text.trim().startsWith("지원자 보기") }

    /** 채팅 목록의 지원자 방들: 글자가 [이름, 동네, ·, N일 전, 마지막 메시지, (안 읽은 수)] 모양인 줄 */
    fun chatRows(nodes: List<UiNode>): List<ChatRow> {
        val out = mutableListOf<ChatRow>()
        for (i in nodes.indices) {
            val n = nodes[i]
            if (!n.clickable || !n.longClickable) continue
            val texts = UiTree.texts(nodes, i)
            if (texts.size < 4 || texts[2] != "·") continue
            // 안 읽은 수: 줄 오른쪽 끝에 있는 숫자만 있는 글자
            val badgeLeft = n.left + (n.right - n.left) * 8 / 10
            val unread = UiTree.subtree(nodes, i).any { j ->
                val c = nodes[j]
                c.text.isNotBlank() && c.text.trim().all { it.isDigit() } && c.left >= badgeLeft
            }
            out.add(ChatRow(i, texts[0], texts[1], unread, texts[3]))
        }
        return out
    }

    /**
     * 채팅 목록은 마지막 대화가 최근인 방부터 → 한 화면의 방이 모두 cutoff보다 오래됐으면 그 아래는 볼 필요가 없음
     * (cutoff = 앱에 있는 지원자가 가장 먼저 지원한 때 − ROOM_AGE_MARGIN_MS). 때를 못 읽는 줄이 있으면 false
     */
    fun allOlderThan(rows: List<ChatRow>, cutoff: Long, now: Long): Boolean =
        rows.isNotEmpty() && rows.all { r -> MyPostParser.estimatePostedAt(r.ago, now)?.let { it < cutoff } == true }

    /** 지원하기 전에 그 사람과 대화했을 수도 있어서 지원 시각보다 이만큼 더 오래된 방까지는 봄 */
    const val ROOM_AGE_MARGIN_MS = 7 * 86_400_000L

    /** 채팅방 입력창 (첫 메시지 드라이런에서 있는지만 봄 — 누르거나 쓰지 않음) */
    fun chatInput(nodes: List<UiNode>): Int? {
        val row = nodes.indexOfFirst { it.viewId.endsWith("composeInputRow") }
        val range = if (row >= 0) UiTree.subtree(nodes, row) else nodes.indices
        return range.firstOrNull { nodes[it].shortCls == "EditText" }
    }

    /** 채팅방 전송 버튼 (첫 메시지 드라이런에서 있는지만 봄 — 누르지 않음, isSafeToClick도 막음) */
    fun sendButton(nodes: List<UiNode>): Int? {
        val row = nodes.indexOfFirst { it.viewId.endsWith("composeInputRow") }
        if (row < 0) return null
        return UiTree.subtree(nodes, row).firstOrNull { i ->
            val n = nodes[i]
            val words = "${n.text} ${n.desc}"
            n.clickable && (words.contains("전송") || words.contains("보내기"))
        }
    }

    /**
     * 채팅 목록을 스크롤할 칸: 지원자 방 줄이 있으면 그 줄을 품은 스크롤 칸, 없으면(맨 위에 당근·당근알바 공식 방·알림 줄만 보이는
     * 화면 등 — 10/18 폰) 화면에서 가장 안쪽의 큰 스크롤 칸 (바깥 칸은 좌우로 넘기는 칸)
     */
    fun chatListScroller(nodes: List<UiNode>): Int? {
        chatRows(nodes).firstOrNull()?.let { row ->
            var p = nodes[row.index].parent
            while (p >= 0) {
                if (nodes[p].scrollable) return p
                p = nodes[p].parent
            }
        }
        return nodes.indices.filter { nodes[it].scrollable && nodes[it].bottom - nodes[it].top > 600 }.maxByOrNull { nodes[it].depth }
    }

    /**
     * 채팅 목록 화면의 모든 줄 (지원자 방 + 당근·당근알바 공식 방 등) — 넘겼는지(화면이 움직였는지) 볼 때.
     * 지원자 방 줄만 보면 공식 방·알림 줄만 바뀌는 곳에서 안 움직인 걸로 봐서 맨 위까지 못 감 (10/18 폰)
     */
    fun listRowKeys(nodes: List<UiNode>): List<String> =
        nodes.indices.filter { nodes[it].clickable && nodes[it].longClickable }.map { UiTree.texts(nodes, it).take(2).joinToString("|") }

    /**
     * 이 화면에서 앱이 누를 수 있는 칸 (화면을 옮기는 칸만):
     * 나의 당근의 진행중인 구인 공고 카드 · 구인글 관리의 구인중 공고 카드(지금은 안 씀) · 공고 상세 '지원자 보기' ·
     * 채팅 목록의 읽은 지원자 방 · 아래 '나의 당근'·'채팅' 탭. 지원자 목록(지원서가 열림)과 채팅방(입력창·전송·근무확정)에서는 아무것도 안 누름
     */
    fun clickTargets(nodes: List<UiNode>): Set<Int> {
        val out = mutableSetOf<Int>()
        val page = page(nodes)
        when (page) {
            DaangnPage.MY_PAGE -> myPostCards(nodes).forEach { out += it.first }
            DaangnPage.JOBS -> jobCards(nodes).forEach { out += it.first }
            DaangnPage.POST_DETAIL -> applicantsButton(nodes)?.let { out += it }
            DaangnPage.CHAT_LIST -> chatRows(nodes).filter { !it.unread }.forEach { out += it.index }
            else -> {}
        }
        if (page == DaangnPage.MY_PAGE || page == DaangnPage.CHAT_LIST || page == DaangnPage.OTHER) {
            for (tab in TABS) bottomTab(nodes, tab)?.let { out += it }
        }
        return out
    }

    /**
     * 이 칸을 눌러도 되는지 (누르기 바로 전에 한 번 더 봄):
     * ① 위 clickTargets 안에 있는 칸이고 ② 입력창이 아니고 ③ 자기 글자·설명에 누르면 안 되는 말이 없음.
     * 버튼이나 글자가 두 개 이하인 작은 칸(상태 칩 '구인중' 같은 것)은 그 안 글자까지 봄
     */
    fun isSafeToClick(nodes: List<UiNode>, i: Int): Boolean {
        if (i !in nodes.indices || i !in clickTargets(nodes)) return false
        val n = nodes[i]
        if (n.shortCls == "EditText") return false
        val own = "${n.text} ${n.desc}"
        if (NEVER_CLICK.any { own.contains(it) }) return false
        if (n.shortCls == "Button" || UiTree.texts(nodes, i).size <= 2) {
            val inner = UiTree.subtree(nodes, i).joinToString(" ") { "${nodes[it].text} ${nodes[it].desc}" }
            if (NEVER_CLICK.any { inner.contains(it) }) return false
        }
        return true
    }
}
