package com.kangsam.daangnrecruit

/**
 * 상시 공고 화면·공고 편집의 버튼이 하는 일 (사용자 프로세스 A0·A1·A2·A3·A8·B0, DB 설계 1단계).
 * 당근에는 아무것도 하지 않고 앱 안 기록만 바꿈 — 당근에 글을 올리는 기능(게시 기능 A2~A7)은 아직 없음.
 * 당근 '내 공고' 화면에서 읽은 공고는 importFromScreen (개발 5단계, 접근성으로 읽기만)
 */
object PostActions {
    const val POST_ON_CLOSE = "마감되면"
    const val POST_PERIODIC = "주기마다"
    const val POST_MANUAL = "직접 올릴 때만"

    /** 건너뛰기: 진행 중 지원자가 이 수 이상이면 새 글을 기다림 (1차값 4명) */
    const val SKIP_THRESHOLD = 4

    /** 진행 중 지원자 (1차값: 연락처 확보 ~ 가능 시간 확보) */
    val IN_PROGRESS = setOf(ApplicantStatus.CONTACT, ApplicantStatus.SMS, ApplicantStatus.TIME)

    /** '내 공고' 화면에서 가져온 게시물 표시 (사유 앞부분). 개발용 '가져온 공고 지우기'도 이걸로 찾음 */
    const val IMPORT_MARK = "'내 공고' 화면에서 가져옴"

    /** '마감했어요'로 남긴 마감의 확인 방법. 당근 '내 공고'에서 마감이 보이면 '… 뒤 '내 공고' 확인'으로 바뀜 */
    const val CLOSED_BY_BUTTON = "담당자의 '마감했어요'"
    const val CLOSE_UNCHECKED = "$CLOSED_BY_BUTTON ('내 공고' 확인 전)"

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

    /** 마감했어요 (A8): 담당자가 당근에서 마감한 글을 마감으로 남김. 마감 확인은 당근 '내 공고' 화면을 읽을 때 (importFromScreen) */
    suspend fun markClosed(db: AppDatabase, post: StandingPost): String {
        val dao = db.postDao()
        val live = dao.postingsOf(post.id).firstOrNull { it.status == "게시됨" } ?: return "올라가 있는 글이 없어요"
        val now = System.currentTimeMillis()
        dao.updatePosting(
            live.copy(
                status = "마감", closedAt = now, closedBy = "담당자", closeCheckedAt = null,
                closeCheckMethod = CLOSE_UNCHECKED,
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
        val waiting = waitingFor(db, title)
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
        attach(db, waiting, standingId, postingId)
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

    /**
     * 당근 '내 공고' 화면에서 읽은 공고를 앱 기록에 맞춤 (개발 5단계 — B0 직접 올린 공고 등록, A8 마감 확인).
     * 당근에는 아무것도 하지 않음. 무엇을 했는지 한 줄을 돌려줌 (설정 → 알림 기록 (개발용)에 보임)
     */
    suspend fun importFromScreen(db: AppDatabase, sp: ScreenPost, now: Long = System.currentTimeMillis()): String {
        val posts = db.postDao()
        val sameTitle = posts.findByTitle(sp.title)
        val live = sameTitle.filter { it.status == "게시됨" }
        val pressed = sameTitle.filter { it.status == "마감" && it.closeCheckMethod == CLOSE_UNCHECKED }

        // 당근에서 구인중이 아닌 글 (마감 등) → 앱에 올라가 있는 걸로 돼 있으면 마감으로 (A8 마감 확인)
        if (!sp.isLive) {
            for (p in pressed) {
                posts.updatePosting(p.copy(closeCheckedAt = now, closeCheckMethod = "$CLOSED_BY_BUTTON 뒤 '내 공고' 확인"))
            }
            for (p in live) {
                posts.updatePosting(
                    p.copy(
                        status = "마감", closedAt = now, closedBy = "담당자", closeCheckedAt = now,
                        closeCheckMethod = "'내 공고' 확인 (마감 시각은 앱이 본 때)",
                    )
                )
                posts.getStandingPost(p.standingPostId)?.let { s ->
                    if (s.currentPostingId == p.id) posts.updateStandingPost(s.copy(currentPostingId = null))
                }
            }
            val state = sp.status.ifBlank { "구인중 아님" }
            return when {
                live.isNotEmpty() -> "'${sp.title}' — 당근 상태: $state → 앱에서도 마감으로 남김"
                pressed.isNotEmpty() -> "'${sp.title}' — '마감했어요'를 당근에서 확인함 (당근 상태: $state)"
                else -> "'${sp.title}' — 당근 상태: $state (앱에 올라가 있는 글이 아니라 그대로 둠)"
            }
        }

        // 이미 앱에 올라가 있는 글 → 직접 올린 공고면 빈 칸만 화면 내용으로 채움
        if (live.size > 1) return "'${sp.title}' — 앱에 같은 공고명이 ${live.size}개 올라가 있어 그대로 둠 (확인 필요)"
        if (live.size == 1) {
            val p = live[0]
            val s = posts.getStandingPost(p.standingPostId)
            if (s != null && s.postWhen == POST_MANUAL) {
                val filled = s.copy(
                    category = s.category.ifBlank { sp.category },
                    area = s.area.ifBlank { sp.area },
                    schedule = s.schedule.ifBlank { sp.schedule },
                    pay = s.pay.ifBlank { sp.pay },
                    body = s.body.ifBlank { sp.body },
                )
                if (filled != s) {
                    posts.updateStandingPost(filled)
                    return "'${sp.title}' — 이미 있는 공고에 화면 내용(급여·조건·본문 등)을 채움"
                }
            }
            return "'${sp.title}' — 이미 앱에 있음 (${p.round}회차 게시 중)"
        }

        // 앱에선 '마감했어요'를 눌렀는데 당근에는 아직 구인중 → 알리기만 (앱이 당근에서 마감하지 않음, A8)
        if (pressed.isNotEmpty()) {
            return "'${sp.title}' — 앱에선 '마감했어요'를 눌렀는데 당근에는 아직 구인중이에요. 당근에서 마감해 주세요"
        }

        // 새로 가져오기: 직접 올린 공고 등록(B0)과 같은 기록 (상시 공고 '직접 올릴 때만' + 게시물 '사람이 올림')
        val sameStanding = posts.standingPostsWithTitle(sp.title)
        sameStanding.firstOrNull { it.postWhen != POST_MANUAL }?.let {
            return "'${sp.title}' — 앱의 상시 공고 '${it.name}'도 같은 공고명이라 그대로 둠 (확인 필요)"
        }
        val waiting = waitingFor(db, sp.title)
        val estimated = MyPostParser.estimatePostedAt(sp.postedAgo, now) ?: now
        val postedAt = minOf(estimated, waiting.minOfOrNull { it.appliedAt } ?: estimated)
        val earlier = sameStanding.firstOrNull() // 전에 가져왔던 직접 올린 공고면 다음 회차로
        val standingId = earlier?.id ?: posts.insertStandingPost(
            StandingPost(
                name = sp.title, title = sp.title, body = sp.body, category = sp.category, area = sp.area,
                schedule = sp.schedule, pay = sp.pay, postWhen = POST_MANUAL, status = "켜짐",
            )
        )
        val round = (earlier?.let { e -> posts.postingsOf(e.id).maxOfOrNull { it.round } } ?: 0) + 1
        val postingId = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = round, title = sp.title, body = sp.body,
                postedBy = "사람이 올림", postedAt = postedAt, status = "게시됨",
                reason = "$IMPORT_MARK (게시 시각은 '${sp.postedAgo}'로 어림)",
            )
        )
        posts.setCurrentPosting(standingId, postingId)
        attach(db, waiting, standingId, postingId)
        return "'${sp.title}' 새로 가져옴 (${round}회차 게시 중)" +
            if (waiting.isNotEmpty()) " · 기다리던 지원자 ${waiting.size}명을 붙임" else ""
    }

    /**
     * 개발용: '내 공고' 화면에서 가져온 게시물(과, 다른 회차가 없으면 그 상시 공고)을 지움.
     * 붙어 있던 지원자는 다시 '공고 못 찾음' 보류로 (다시 가져오면 다시 붙음). 지운 게시물 수를 돌려줌
     */
    suspend fun deleteImported(db: AppDatabase): Int {
        val posts = db.postDao()
        val imported = posts.postingsWithReasonPrefix(IMPORT_MARK)
        if (imported.isEmpty()) return 0
        val ids = imported.map { it.id }
        db.applicantDao().holdForDeletedPostings(ids, ApplicantStatus.HOLD, HoldReason.POST_NOT_FOUND, ApplicantStatus.MERGED)
        posts.clearCurrentPosting(ids)
        posts.deletePostingsById(ids)
        val emptied = imported.map { it.standingPostId }.distinct().filter { posts.postingsOf(it).isEmpty() }
        if (emptied.isNotEmpty()) posts.deleteStandingPostsById(emptied)
        return imported.size
    }

    /** 그 공고명으로 못 찾았던 지원자 (보류 · 공고 못 찾음) */
    private suspend fun waitingFor(db: AppDatabase, title: String): List<Applicant> =
        db.applicantDao().findByTitleAndStatus(title, ApplicantStatus.HOLD)
            .filter { it.reason.startsWith(HoldReason.POST_NOT_FOUND) }

    /** 기다리던 지원자를 게시물에 붙이고 멈추기 전 상태로 */
    private suspend fun attach(db: AppDatabase, waiting: List<Applicant>, standingId: Long, postingId: Long) {
        for (w in waiting) {
            db.applicantDao().update(
                w.copy(
                    standingPostId = standingId, postingId = postingId,
                    status = w.statusBeforeHold.ifBlank { ApplicantStatus.NEW }, statusBeforeHold = "", reason = "",
                )
            )
        }
    }
}
