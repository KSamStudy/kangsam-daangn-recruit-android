package com.kangsam.daangnrecruit

/**
 * 화면에 보일 지원자 고르기 — 지원자 탭(ui/ApplicantsScreen)과 공고 탭 카드(ui/PostsScreen)가 같이 씀.
 * 10/18 재걸 결정: 끝난 지원은 안 보이고 지금 처리할 것만, 공고별로 나눠서 (당근 지원자 목록의 수와 같게 — 헷갈리지 않게).
 *
 * - 끝난 지원(종료·합침·미응답)은 어느 화면에도 안 보임. DB에는 기록으로 남음
 *   (예: 당근 지원자 목록에서 빠져 '종료'로 둔 사람 — sync/ApplicantImport)
 * - 보이는 지원자 = 끝나지 않은 지원 → 공고 카드·공고 칸의 '지원자 N명' (당근 '지원자 N'과 같아야 함)
 * - 지금 처리할 것 = 보이는 지원자 중 넘김(담당자에게 넘겨 리니어로 간 것)을 뺀 것 → 지원자 탭에 처음 보이는 목록.
 *   넘김은 지원자 탭 '넘김' 필터에서만
 *
 * 순수 코드라 폰 없이 시험함. 보이는 기준을 바꿀 때는 여기만 고침
 */
object ApplicantView {
    /** 끝난 지원 — 화면에 안 보임 (DB 설계 상태 흐름의 '끝' 가운데 넘김을 뺀 것) */
    val FINISHED = setOf(ApplicantStatus.ENDED, ApplicantStatus.MERGED, ApplicantStatus.NO_REPLY)

    /** 확인 필요로 보내는 상태 (확인 탭 카드: 보류·담당자 확인) */
    val CHECK = setOf(ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK)

    /** 공고 칸 '공고 못 찾음'을 고른 값 */
    const val NO_POST = "none"

    private const val LIVE = "게시됨"

    /** 화면에 보이는 지원자: 끝나지 않은 지원 */
    fun isShown(a: Applicant): Boolean = a.status !in FINISHED

    /** 지금 처리할 것: 보이는 지원자에서 넘김을 뺀 것 */
    fun toHandle(a: Applicant): Boolean = isShown(a) && a.status != ApplicantStatus.HANDED_OVER

    /** 지원자 탭 위 공고 칸 하나: 그 공고에 지원한 사람 중 보이는 사람 */
    data class PostGroup(
        val key: String,                 // 고른 칸을 기억하는 값 (post:상시공고ID, 공고 못 찾음은 NO_POST)
        val label: String,               // 칸 이름 (상시 공고 이름 — 예시 공고면 '(예시)')
        val live: Boolean,               // 지금 당근에 올라가 있는 글(게시됨)이 있음
        val applicants: List<Applicant>, // 이 공고에 지원한 사람 중 보이는 사람
    )

    /**
     * 보이는 지원자를 상시 공고별로 나눔 (같은 상시 공고의 여러 회차는 한 칸).
     * 순서: 지금 올라가 있는 공고 → 보이는 지원자가 남은 다른 공고 → 예시 공고 → 공고 못 찾음.
     * 올라가 있는 공고는 지원자가 없어도 칸이 있음 (0명)
     */
    fun groups(applicants: List<Applicant>, posts: List<StandingPost>, postings: List<Posting>): List<PostGroup> {
        val shown = applicants.filter(::isShown)
        val byPost = shown.groupBy { it.standingPostId }
        val liveIds = postings.filter { it.status == LIVE }.map { it.standingPostId }.toSet()
        val postGroups = posts
            .filter { it.id in liveIds || byPost[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy<StandingPost>({ it.isSample }, { it.id !in liveIds }, { it.id }))
            .map { p ->
                PostGroup("post:${p.id}", p.name + if (p.isSample) " (예시)" else "", p.id in liveIds, byPost[p.id].orEmpty())
            }
        val known = posts.map { it.id }.toSet()
        val lost = shown.filter { it.standingPostId == null || it.standingPostId !in known }
        return if (lost.isEmpty()) postGroups else postGroups + PostGroup(NO_POST, HoldReason.POST_NOT_FOUND, false, lost)
    }
}
