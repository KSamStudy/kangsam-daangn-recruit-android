package com.kangsam.daangnrecruit

/**
 * 상시 공고 화면·공고 편집의 버튼이 하는 일 (사용자 프로세스 A0·A1·A2·A3·A8·B0, DB 설계 1단계).
 * 당근에는 아무것도 하지 않고 앱 안 기록만 바꿈 — 당근에 글을 올리는 기능(게시 기능 A2~A7)과 '내 공고' 읽기는 아직 없음
 */
object PostActions {
    const val POST_ON_CLOSE = "마감되면"
    const val POST_PERIODIC = "주기마다"
    const val POST_MANUAL = "직접 올릴 때만"

    /** 건너뛰기: 진행 중 지원자가 이 수 이상이면 새 글을 기다림 (1차값 4명) */
    const val SKIP_THRESHOLD = 4

    /** 진행 중 지원자 (1차값: 연락처 확보 ~ 가능 시간 확보) */
    val IN_PROGRESS = setOf(ApplicantStatus.CONTACT, ApplicantStatus.SMS, ApplicantStatus.TIME)

    /** 켜기 전 확인 (A0): 걸린 것 목록. 비어 있으면 켤 수 있음 */
    suspend fun checkBeforeOn(db: AppDatabase, post: StandingPost): List<String> {
        val problems = mutableListOf<String>()
        if (post.approvedBy.isBlank()) problems.add("문구 승인 전")
        if (post.title.isBlank()) {
            problems.add("제목(공고명) 없음")
        } else {
            val sameTitleOn = db.postDao().standingPostsWithTitle(post.title)
                .filter { it.id != post.id && it.status == "켜짐" }
            if (sameTitleOn.isNotEmpty()) {
                problems.add("켜진 다른 공고와 공고명이 겹침 (${sameTitleOn.joinToString { it.name }})")
            }
            val liveOthers = db.postDao().findByTitle(post.title)
                .filter { it.standingPostId != post.id && it.status == "게시됨" }
            if (liveOthers.isNotEmpty()) problems.add("올라가 있는 다른 글과 공고명이 겹침")
        }
        val account = post.accountId?.let { db.accountDao().get(it) }
        if (account == null) {
            problems.add("게시 계정 없음")
        } else if (account.status == "정지") {
            problems.add("계정 정지")
        }
        return problems
    }

    /** 공고 스위치 켜기·끄기. 켤 때 확인(A0)에 걸리면 안 켜고 걸린 것을 돌려줌 (직접 올린 공고는 확인 없음) */
    suspend fun setOn(db: AppDatabase, post: StandingPost, on: Boolean): List<String> {
        if (on && post.postWhen != POST_MANUAL) {
            val problems = checkBeforeOn(db, post)
            if (problems.isNotEmpty()) return problems
        }
        db.postDao().updateStandingPost(post.copy(status = if (on) "켜짐" else "꺼짐"))
        return emptyList()
    }

    /** 지금 올리기 (A1): 게시 전 검사(A3)까지만 하고 결과를 돌려줌. 당근에 올리는 기능은 아직 없음 */
    suspend fun tryPostNow(db: AppDatabase, post: StandingPost, allStopped: Boolean): String {
        if (allStopped) return "전체 멈춤 중이라 올릴 수 없어요"
        if (post.postWhen == POST_MANUAL) return "직접 올린 공고는 새 글을 사람이 올려요"
        val problems = mutableListOf<String>()
        val live = db.postDao().postingsOf(post.id).firstOrNull { it.status == "게시됨" }
        if (live != null) problems.add("이 공고의 ${live.round}회차가 아직 올라가 있음")
        problems.addAll(checkBeforeOn(db, post))
        return if (problems.isEmpty()) {
            "게시 전 검사 통과 — 당근에 올리는 기능은 아직 없어서 여기까지만 해요"
        } else {
            "게시 전 검사 걸림 — ${problems.joinToString(", ")}"
        }
    }

    /** 마감했어요 (A8): 담당자가 당근에서 마감한 글을 마감으로 남김. '내 공고'로 확인하는 기능은 아직 없음 */
    suspend fun markClosed(db: AppDatabase, post: StandingPost): String {
        val dao = db.postDao()
        val live = dao.postingsOf(post.id).firstOrNull { it.status == "게시됨" } ?: return "올라가 있는 글이 없어요"
        val now = System.currentTimeMillis()
        dao.updatePosting(
            live.copy(
                status = "마감", closedAt = now, closedBy = "담당자", closeCheckedAt = now,
                closeCheckMethod = "담당자의 '마감했어요' ('내 공고' 확인은 아직 없음)",
                reason = live.reason.ifBlank { "담당자가 마감" },
            )
        )
        val next = if (post.postWhen == POST_ON_CLOSE) now + post.waitAfterCloseMinutes * 60_000L else post.nextPostAt
        dao.updateStandingPost(post.copy(currentPostingId = null, nextPostAt = next))
        return "${live.round}회차를 마감으로 남겼어요"
    }

    /**
     * 직접 올린 공고 등록 (B0, DB 설계 규칙 '직접 올린 공고'): 상시 공고(직접 올릴 때만) 1개 + 게시물 1줄(사람이 올림).
     * 그 공고명으로 못 찾았던 지원자(보류 · 공고 못 찾음)를 붙이고, 붙인 수를 돌려줌
     */
    suspend fun registerManual(db: AppDatabase, title: String, isSample: Boolean = false): Int {
        require(title.isNotBlank()) { "공고명이 비어 있어요" }
        val posts = db.postDao()
        check(posts.findByTitle(title).none { it.status == "게시됨" }) {
            "이미 올라가 있는 공고명이에요 — '공고 고르기'를 쓰세요"
        }
        val waiting = db.applicantDao().findByTitleAndStatus(title, ApplicantStatus.HOLD)
            .filter { it.reason.startsWith(HoldReason.POST_NOT_FOUND) }
        val postedAt = waiting.minOfOrNull { it.appliedAt } ?: System.currentTimeMillis()
        val standingId = posts.insertStandingPost(
            StandingPost(name = title, title = title, postWhen = POST_MANUAL, status = "켜짐", isSample = isSample)
        )
        val postingId = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = 1, title = title, postedBy = "사람이 올림", postedAt = postedAt,
                status = "게시됨",
                reason = if (waiting.isEmpty()) "직접 올린 공고 등록" else "직접 올린 공고 등록 (게시 시각은 첫 지원 시각으로 둠)",
                isSample = isSample,
            )
        )
        posts.setCurrentPosting(standingId, postingId)
        for (w in waiting) {
            db.applicantDao().update(
                w.copy(
                    standingPostId = standingId, postingId = postingId,
                    status = w.statusBeforeHold.ifBlank { ApplicantStatus.NEW }, statusBeforeHold = "", reason = "",
                )
            )
        }
        return waiting.size
    }

    /** 공고 편집 저장 (A2): 제목·본문이 바뀌면 문구 버전 +1, 승인은 다시 받아야 함. 안내 문구를 돌려줌 */
    suspend fun save(db: AppDatabase, original: StandingPost?, form: StandingPost): String {
        val dao = db.postDao()
        if (original == null) {
            dao.insertStandingPost(form.copy(id = 0, textVersion = 1, status = "꺼짐", approvedBy = "", approvedAt = null))
            return "'${form.name}' 공고를 추가했어요 — 꺼진 상태예요 (문구 승인 뒤 켤 수 있어요)"
        }
        val textChanged = original.title != form.title || original.body != form.body
        return if (textChanged) {
            val v = original.textVersion + 1
            dao.updateStandingPost(form.copy(textVersion = v, approvedBy = "", approvedAt = null))
            "v${v}로 저장했어요 — 문구 승인 뒤 다음 회차부터 이 문구로 올라가요"
        } else {
            dao.updateStandingPost(form)
            "저장했어요"
        }
    }
}
