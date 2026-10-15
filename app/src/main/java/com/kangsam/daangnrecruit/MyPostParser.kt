package com.kangsam.daangnrecruit

/*
 * 당근 '내 공고' 상세 화면 읽기 (개발 5단계).
 * 화면 구조는 10/15 uiautomator 덤프로 확인: 웹뷰지만 글자가 접근성으로 다 읽히고, 스크롤 아래(후기 등)까지 한 번에 읽힘.
 * 안드로이드 기능을 쓰지 않는 순수 코드라 폰 없이도 시험할 수 있음.
 */

/** 화면의 한 칸: 종류(TextView·Image·Button 등), 글자, 화면 안 이름(viewId — 웹뷰는 HTML id). 화면 위에서부터 차례대로 */
data class ScreenNode(val cls: String, val text: String, val viewId: String = "") {
    val isImage: Boolean get() = cls.substringAfterLast('.').let { it == "Image" || it == "ImageView" }
    val hasText: Boolean get() = text.isNotBlank()
}

/** 앱이 읽는 당근 화면 종류: 내 공고 상세(ScreenPost), 지원자 목록(ScreenApplicantList) */
sealed interface DaangnScreen

/** '내 공고' 상세 화면에서 읽은 공고 (화면에 보인 글자 그대로) */
data class ScreenPost(
    val title: String,      // 공고명
    val status: String,     // 상태 (예: 구인중)
    val postedAgo: String,  // 올린 때 (예: 8일 전)
    val pay: String,        // 급여 (예: 시급 25,000원)
    val category: String,   // 업직종 (예: 학원/과외/레슨)
    val schedule: String,   // 나머지 조건 줄 (예: 화~금 정기적으로 · 시간 협의 · 시범 수업 필요)
    val body: String,       // 본문
    val address: String,    // 근무지 (예: 인천광역시 검단구 불로동)
    val applicants: String, // 지원자 수 (예: 지원자 35명 · 지원자 보기 18)
) : DaangnScreen {
    /** 구인중이면 올라가 있는 글 */
    val isLive: Boolean get() = status.replace(" ", "") == "구인중"

    /** 동네 = 주소의 마지막 낱말 (예: 불로동) */
    val area: String get() = address.trim().split(Regex("""\s+""")).lastOrNull().orEmpty()
}

object MyPostParser {
    private val AGO = Regex("""^(끌올\s*)?(?:(\d+)\s*(초|분|시간|일|주|개월|달|년)|방금)\s*전$""")
    private val VIEWS = Regex("""^(관심\s*\d+\s*·?\s*)?조회\s*\d+""")
    private val PAY = Regex("""^(시급|일급|주급|월급|연봉|건당)\s""")
    private val STATUS = Regex("""^(구인\s*중|.*마감.*|채용\s*완료|숨김)$""")
    private val TOTAL_APPLICANTS = Regex("""^지원자\s*\d+\s*명$""")

    /** 내 공고 상세 화면이면 읽은 공고, 아니면 null */
    fun parse(nodes: List<ScreenNode>): ScreenPost? {
        // 글자가 있는 칸과 그림(조건 줄 앞 아이콘)만 남김
        val items = nodes.filter { it.isImage || it.hasText }
        fun textAt(i: Int) = items[i].text.trim()
        fun isText(i: Int) = !items[i].isImage && items[i].hasText

        // 1) 내 공고인지: 아래쪽 '지원자 보기 N' 버튼 (남의 공고에는 없음)
        val applicantsButton = items.indices.firstOrNull { isText(it) && textAt(it).startsWith("지원자 보기") }
            ?: return null

        // 2) '관심 N조회 N' 줄 — 공고 본문이 끝나는 곳. 없으면 다 안 뜬 화면이라 읽지 않음
        val viewsIdx = items.indices.firstOrNull { isText(it) && VIEWS.containsMatchIn(textAt(it)) } ?: return null

        // 3) 올린 때 (예: 8일 전) — 본문 위쪽에서만 찾음 (아래쪽 프로필·후기에도 '1초 전'·'6개월 전'이 있음)
        val agoIdx = (0 until viewsIdx).firstOrNull { isText(it) && AGO.matches(textAt(it)) } ?: return null

        // 4) 공고명 = 올린 때 바로 앞 글자
        val titleIdx = (agoIdx - 1 downTo 0).firstOrNull { isText(it) } ?: return null
        val title = textAt(titleIdx)

        // 5) 상태 = 공고명 위쪽에서 처음 나오는 상태 글자 (구인중 등)
        val status = (0 until titleIdx).firstOrNull { isText(it) && STATUS.matches(textAt(it)) }
            ?.let { textAt(it) }.orEmpty()
        if (title.length < 2 || title == status) return null

        // 6) 본문 = '관심 N조회 N' 바로 앞 글자
        val bodyIdx = (viewsIdx - 1 downTo agoIdx + 1).firstOrNull { isText(it) }
        val body = bodyIdx?.let { textAt(it) }.orEmpty()

        // 7) 조건 줄 = 올린 때 다음 첫 아이콘부터 본문 앞까지. 아이콘마다 한 줄 (예: '화~금' + '정기적으로')
        val lines = mutableListOf<String>()
        if (bodyIdx != null) {
            var current: MutableList<String>? = null
            for (i in agoIdx + 1 until bodyIdx) {
                if (items[i].isImage) {
                    current?.let { if (it.isNotEmpty()) lines.add(it.joinToString(" ")) }
                    current = mutableListOf()
                } else {
                    current?.add(textAt(i)) // 첫 아이콘 앞 글자(예: 후기 11개)는 버림
                }
            }
            current?.let { if (it.isNotEmpty()) lines.add(it.joinToString(" ")) }
        }
        val pay = lines.firstOrNull { PAY.containsMatchIn(it) }.orEmpty()
        val category = lines.firstOrNull { it != pay && it.contains('/') }.orEmpty()
        val schedule = lines.filter { it != pay && it != category }.joinToString(" · ")

        // 8) 근무지 = '복사' 바로 앞 글자
        val copyIdx = (viewsIdx + 1 until items.size).firstOrNull { isText(it) && textAt(it) == "복사" }
        val address = copyIdx?.let { c -> (c - 1 downTo viewsIdx + 1).firstOrNull { isText(it) } }
            ?.let { textAt(it) }.orEmpty()

        // 9) 지원자 수
        val total = items.indices.firstOrNull { isText(it) && TOTAL_APPLICANTS.matches(textAt(it)) }?.let { textAt(it) }
        val applicants = listOfNotNull(total, textAt(applicantsButton)).joinToString(" · ")

        return ScreenPost(title, status, textAt(agoIdx), pay, category, schedule, body, address, applicants)
    }

    /** '8일 전' → 그만큼 앞선 시각 (어림). 못 읽으면 null */
    fun estimatePostedAt(ago: String, now: Long): Long? {
        val m = AGO.matchEntire(ago.trim()) ?: return null
        val n = m.groupValues[2].toLongOrNull() ?: return now // 방금 전
        val unit = when (m.groupValues[3]) {
            "초" -> 1_000L
            "분" -> 60_000L
            "시간" -> 3_600_000L
            "일" -> 86_400_000L
            "주" -> 7 * 86_400_000L
            "개월", "달" -> 30 * 86_400_000L
            "년" -> 365 * 86_400_000L
            else -> return null
        }
        return now - n * unit
    }
}
