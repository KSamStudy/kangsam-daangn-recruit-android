package com.kangsam.daangnrecruit

import com.kangsam.daangnrecruit.sync.AccountSync

/**
 * 상시 공고 화면·공고 편집의 버튼이 하는 일 (사용자 프로세스 A0·A1·A2·A3·A8·B0, DB 설계 1단계).
 * 당근에는 아무것도 하지 않고 앱 안 기록만 바꿈 — 당근에 글을 올리는 기능(게시 기능 A2~A7)은 아직 없음.
 * 당근 화면에서 공고를 가져오는 일은 따로 도는 기능 '당근 가져오기'(sync 폴더, 프로세스 C)가 함
 */
object PostActions {
    const val POST_ON_CLOSE = "마감되면"
    const val POST_PERIODIC = "주기마다"
    const val POST_MANUAL = "직접 올릴 때만"

    /** 건너뛰기: 진행 중 지원자가 이 수 이상이면 새 글을 기다림 (1차값 4명) */
    const val SKIP_THRESHOLD = 4

    /** 진행 중 지원자 (1차값: 연락처 확보 ~ 가능 시간 확보) */
    val IN_PROGRESS = setOf(ApplicantStatus.CONTACT, ApplicantStatus.SMS, ApplicantStatus.TIME)

    /** '마감했어요'로 남긴 마감의 확인 방법. 당근 '내 공고'에서 마감이 보이면 '… 뒤 '내 공고' 확인'으로 바뀜 */
    const val CLOSED_BY_BUTTON = "담당자의 '마감했어요'"
    const val CLOSE_UNCHECKED = "$CLOSED_BY_BUTTON ('내 공고' 확인 전)"

    /** '마감했어요' 마감 이유의 기본값 (화면 설계 '마감했어요' — 채용 끝남 / 그 밖 한 줄) */
    const val CLOSE_HIRED = "채용 끝남"

    /** 게시물 '올린 방법': 사람이 올림 (직접 올린 공고 · 당근에서 가져온 공고 — 게시 시각이 어림값일 수 있음) */
    const val POSTED_BY_HUMAN = "사람이 올림"

    /**
     * 마감이 확인된 뒤의 다음 게시 시각 (DB 설계 '상시 공고' 다음 게시 시각): 마감되면 → 확인한 때 + 기다리는 시간,
     * 주기마다 → 정해 둔 주기 그대로, 직접 올릴 때만 → 비움
     */
    fun nextAfterClose(post: StandingPost, confirmedAt: Long): Long? = when (post.postWhen) {
        POST_ON_CLOSE -> confirmedAt + post.waitAfterCloseMinutes * 60_000L
        POST_PERIODIC -> post.nextPostAt
        else -> null
    }

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

    /**
     * 마감했어요 (A8): 담당자가 당근에서 마감한 글을 마감으로 남김 — 고른 마감 이유(채용 끝남 / 그 밖 한 줄)와 함께.
     * 마감 확인은 '당근 가져오기'가 '내 공고' 화면을 읽을 때 (PostImport). 다음 게시 시각은 그 확인 뒤에 정함 —
     * 확인 전에 새 글을 올리면 같은 공고가 둘 올라가 있을 수 있어서 (10/19 — 전에는 누르자마자 정함)
     */
    suspend fun markClosed(db: AppDatabase, post: StandingPost, closeReason: String = CLOSE_HIRED, now: Long = System.currentTimeMillis()): String {
        val dao = db.postDao()
        val live = dao.postingsOf(post.id).firstOrNull { it.status == "게시됨" } ?: return "올라가 있는 글이 없어요"
        val why = closeReason.trim().ifBlank { CLOSE_HIRED }
        dao.updatePosting(
            live.copy(
                status = "마감", closedAt = now, closedBy = "담당자", closeReason = why,
                closeCheckedAt = null, closeCheckMethod = CLOSE_UNCHECKED,
            )
        )
        val cur = dao.getStandingPost(post.id) ?: post
        dao.updateStandingPost(
            cur.copy(
                currentPostingId = if (cur.currentPostingId == live.id) null else cur.currentPostingId,
                nextPostAt = if (cur.postWhen == POST_ON_CLOSE) null else cur.nextPostAt,
            )
        )
        return "${live.round}회차를 마감으로 남겼어요 ($why) — '내 공고'에서 마감이 보이면 다음 게시 시각을 정해요"
    }

    /**
     * 직접 올린 공고 등록 (B0, DB 설계 규칙 '직접 올린 공고'): 상시 공고(직접 올릴 때만) 1개 + 게시물 1줄(사람이 올림).
     * 같은 공고명의 직접 올린 공고가 이미 있으면 그 공고의 다음 회차로, 앱이 올리는 상시 공고와 같은 공고명이면 등록하지 않음 (공고명 규칙).
     * 그 공고명으로 못 찾았던 지원자(보류 · 공고 못 찾음)를 붙이고, 붙인 수를 돌려줌
     */
    suspend fun registerManual(db: AppDatabase, title: String, isSample: Boolean = false, now: Long = System.currentTimeMillis()): Int {
        require(title.isNotBlank()) { "공고명이 비어 있어요" }
        val posts = db.postDao()
        check(posts.findByTitle(title).none { it.status == "게시됨" }) {
            "이미 올라가 있는 공고명이에요 — '공고 고르기'를 쓰세요"
        }
        val sameTitle = posts.standingPostsWithTitle(title)
        sameTitle.firstOrNull { it.postWhen != POST_MANUAL }?.let {
            error("앱이 올리는 상시 공고 '${it.name}'와 공고명이 같아 등록하지 않았어요 (공고명이 겹치면 지원을 못 가림)")
        }
        val earlier = sameTitle.firstOrNull() // 전에 등록한 직접 올린 공고 → 다음 회차
        val waiting = waitingFor(db, title)
        val postedAt = waiting.minOfOrNull { it.appliedAt } ?: now
        // 이 폰의 당근 계정이 올린 글 — 지금 계정(계정이 하나일 때)을 붙임. 모르면 비워 두고 당근 가져오기가 채움 (DB 설계 '계정')
        val accountId = if (isSample) null else AccountSync.current(db)?.id
        val standingId = earlier?.id ?: posts.insertStandingPost(
            StandingPost(name = title, title = title, postWhen = POST_MANUAL, status = "켜짐", accountId = accountId, isSample = isSample)
        )
        val round = (earlier?.let { e -> posts.postingsOf(e.id).maxOfOrNull { it.round } } ?: 0) + 1
        val postingId = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = round, title = title, accountId = accountId, postedBy = POSTED_BY_HUMAN, postedAt = postedAt,
                status = "게시됨",
                reason = if (waiting.isEmpty()) "직접 올린 공고 등록" else "직접 올린 공고 등록 (게시 시각은 첫 지원 시각으로 둠)",
                isSample = isSample,
            )
        )
        posts.setCurrentPosting(standingId, postingId)
        attach(db, waiting, standingId, postingId)
        return waiting.size
    }

    /**
     * 공고 편집 저장 (A2): 제목·본문이 바뀌면 문구 버전 +1, 승인은 다시 받아야 함. 안내 문구를 돌려줌.
     * 고칠 수 있는 칸만 지금 DB 줄에 옮겨 씀 — 화면을 연 사이 앱이 바꾼 값(지금 올라간 글·다음 게시 시각·켜짐·승인)을 덮지 않게
     */
    suspend fun save(db: AppDatabase, original: StandingPost?, form: StandingPost): String {
        val dao = db.postDao()
        if (original == null) {
            dao.insertStandingPost(form.copy(id = 0, textVersion = 1, status = "꺼짐", approvedBy = "", approvedAt = null))
            return "'${form.name}' 공고를 추가했어요 — 꺼진 상태예요 (문구 승인 뒤 켤 수 있어요)"
        }
        val cur = dao.getStandingPost(original.id) ?: return "이 공고가 앱에 없어요 (지워졌을 수 있어요)"
        val edited = editable(cur, form)
        val textChanged = cur.title != form.title || cur.body != form.body
        return if (textChanged) {
            val v = cur.textVersion + 1
            dao.updateStandingPost(edited.copy(textVersion = v, approvedBy = "", approvedAt = null))
            "v${v}로 저장했어요 — 문구 승인 뒤 다음 회차부터 이 문구로 올라가요"
        } else {
            dao.updateStandingPost(edited)
            "저장했어요"
        }
    }

    /** 공고 편집에서 고칠 수 있는 칸만 form에서 가져옴 (나머지 — 문구 버전·승인·켜짐·다음 게시 시각·지금 올라간 글 — 은 DB 값) */
    fun editable(cur: StandingPost, form: StandingPost): StandingPost = cur.copy(
        name = form.name, category = form.category, area = form.area, targetGrade = form.targetGrade, schedule = form.schedule,
        pay = form.pay, teachingMode = form.teachingMode, title = form.title, body = form.body, accountId = form.accountId,
        postWhen = form.postWhen, waitAfterCloseMinutes = form.waitAfterCloseMinutes, periodText = form.periodText,
        whenPreviousRemains = form.whenPreviousRemains, bump = form.bump, appCloseCondition = form.appCloseCondition,
        skipCondition = form.skipCondition, linearTicket = form.linearTicket,
    )

    /** 그 공고명으로 못 찾았던 지원자 (보류 · 공고 못 찾음). 당근 가져오기도 씀 */
    internal suspend fun waitingFor(db: AppDatabase, title: String): List<Applicant> =
        db.applicantDao().findByTitleAndStatus(title, ApplicantStatus.HOLD)
            .filter { it.reason.startsWith(HoldReason.POST_NOT_FOUND) }

    /** 기다리던 지원자를 게시물에 붙이고 멈추기 전 상태로 (사유의 '당근 상태: …'는 되살림). 당근 가져오기도 씀 */
    internal suspend fun attach(db: AppDatabase, waiting: List<Applicant>, standingId: Long, postingId: Long) {
        for (w in waiting) {
            ApplicantStore.update(
                db,
                w.copy(
                    standingPostId = standingId, postingId = postingId,
                    status = w.statusBeforeHold.ifBlank { ApplicantStatus.NEW }, statusBeforeHold = "",
                    reason = HoldReason.restore(w.reason),
                )
            )
        }
    }
}
