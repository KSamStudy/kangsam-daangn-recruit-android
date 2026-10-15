package com.kangsam.daangnrecruit.sync

/*
 * 당근 '지원자 목록' 화면 읽기 (개발 5단계, 화면 구조는 10/15 uiautomator 덤프로 확인).
 * 공고 상세의 '지원자 보기 N'을 누르면 나오는 화면. 웹뷰라 스크롤 아래 지원자까지 한 번에 읽힘.
 * 지원자 카드 하나: 사진 → (N: 안 읽음) → 이름 → (상태: 연락중·연락 대기중) → 나이대·성별 → 동네 → 'N일 전 지원' → 거리·추천·인증·자기소개…
 * 이름·상태·나이대·동네·지원 때만 읽고, 자기소개·사진·거리 같은 나머지는 읽지 않음.
 * 안드로이드 기능을 쓰지 않는 순수 코드라 폰 없이도 시험할 수 있음.
 */

/** 지원자 목록 화면의 카드 하나 (화면에 보인 글자 그대로) */
data class ScreenApplicant(
    val name: String,         // 당근 이름
    val unread: Boolean,      // 'N' 표시 = 담당자가 아직 지원서를 안 읽음
    val daangnStatus: String, // 당근 상태 (예: 연락중, 연락 대기중). 안 읽었으면 빈 칸
    val ageGender: String,    // 예: 40대 여성 — 같은 사람 구분에만 씀
    val area: String,         // 동네
    val appliedAgo: String,   // 지원한 때 (예: 6시간 전)
)

/** 지원자 목록 화면 하나: 어느 공고의 지원자인지와 카드들 */
data class ScreenApplicantList(
    val postTitle: String,              // 공고명
    val tabText: String,                // 고른 탭 (예: 지원자 18)
    val applicants: List<ScreenApplicant>,
) : DaangnScreen

object ApplicantListParser {
    private val APPLIED = Regex("""^(\d+\s*(?:초|분|시간|일|주|개월|달|년)|방금)\s*전\s*지원$""")
    private val AGE = Regex("""^\d+대(\s*이상)?\s*(여성|남성)$""")
    private val TAB = Regex("""^지원자\s*\d+$""")

    /** 지원자 목록 화면이면 읽은 것, 아니면 null */
    fun parse(nodes: List<ScreenNode>): ScreenApplicantList? {
        // 글자가 있는 칸과 그림(카드 앞 사진)만 남김
        val items = nodes.filter { it.isImage || it.hasText }
        fun textAt(i: Int) = items[i].text.trim()
        fun isText(i: Int) = i in items.indices && !items[i].isImage && items[i].hasText

        // 1) 지원자 목록 화면인지: 맨 위 제목 '지원자 목록'
        if (items.indices.none { isText(it) && textAt(it) == "지원자 목록" }) return null

        // 2) 고른 탭이 '지원자 N'일 때만 읽음 ('다음 기회에' 탭은 읽지 않음).
        //    웹뷰가 탭 이름(tabs:opened…)을 알려주면 그걸로, 안 알려주면 '지원자 N' 글자가 있는지로
        val openTab = items.firstOrNull { it.viewId.contains("tabs:opened") && it.viewId.endsWith("trigger-root") }
        val tabText = openTab?.text?.trim()
            ?: items.indices.firstOrNull { isText(it) && TAB.matches(textAt(it)) }?.let { textAt(it) }
            ?: return null
        if (!TAB.matches(tabText)) return null

        // 3) 공고명 = '당근알바 바로가기' 다음 글자
        val shortcut = items.indices.firstOrNull { isText(it) && textAt(it) == "당근알바 바로가기" } ?: return null
        val titleIdx = (shortcut + 1 until items.size).firstOrNull { isText(it) } ?: return null
        val postTitle = textAt(titleIdx)

        // 4) 카드: 'N일 전 지원'마다 하나. 거기서 거꾸로 동네 → 나이대·성별 → (상태) → 이름 → (N) → 사진
        val list = mutableListOf<ScreenApplicant>()
        for (k in items.indices) {
            if (!isText(k)) continue
            val applied = APPLIED.matchEntire(textAt(k)) ?: continue
            if (!isText(k - 1)) continue
            var area = textAt(k - 1)
            var ageGender = ""
            val headEnd: Int // 이름·상태가 끝나는 곳 (그 바로 앞까지가 카드 앞부분)
            if (AGE.matches(area)) {
                ageGender = area; area = ""; headEnd = k - 1
            } else if (isText(k - 2) && AGE.matches(textAt(k - 2))) {
                ageGender = textAt(k - 2); headEnd = k - 2
            } else {
                headEnd = k - 1
            }
            val head = mutableListOf<String>()
            var j = headEnd - 1
            while (isText(j)) {
                head.add(0, textAt(j)); j--
            }
            if (j < 0 || !items[j].isImage) continue // 앞에 사진이 없으면 카드가 아님
            val unread = head.firstOrNull() == "N"
            if (unread) head.removeAt(0)
            val name = head.firstOrNull() ?: continue
            val status = head.drop(1).joinToString(" ")
            list.add(ScreenApplicant(name, unread, status, ageGender, area, "${applied.groupValues[1]} 전"))
        }
        if (list.isEmpty()) return null
        return ScreenApplicantList(postTitle, tabText, list)
    }
}
