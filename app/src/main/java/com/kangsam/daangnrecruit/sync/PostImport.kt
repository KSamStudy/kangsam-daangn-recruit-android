package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.StandingPost

/**
 * 당근 '내 공고' 상세 화면에서 읽은 공고를 앱 기록에 맞춤 (프로세스 C — 사용자 프로세스 B0 직접 올린 공고 등록, A8 마감 확인).
 * 당근에는 아무것도 하지 않음. 무엇을 했는지 한 줄을 돌려줌
 * - 상태 글자를 못 읽으면 아무것도 안 바꿈 (10/19 — 전에는 구인중이 아닌 것으로 봐서 올라간 글을 마감으로 바꿈, 점검 P1-8)
 * - 구인중이 아닌 글: 올린 때로 몇 회차인지 가려(PostRounds) 그 회차만 — 지난 회차의 상세를 열어도 지금 올라간 글은 그대로
 * - 구인중인 글: 공고명으로 맞춤 (구인중 글도 올린 때로 가릴지는 DB 설계 '정할 것'). 사람이 올린 글의 어림값 게시 시각은 고침
 */
object PostImport {
    /** 화면에서 가져온 게시물 표시 (사유 앞부분). 개발용 '가져온 공고·지원자 지우기'도 이걸로 찾음 */
    const val IMPORT_MARK = "'내 공고' 화면에서 가져옴"

    suspend fun importFromScreen(db: AppDatabase, sp: ScreenPost, now: Long = System.currentTimeMillis()): String {
        val posts = db.postDao()
        // 상태 글자를 못 읽으면 그대로 — 구인중이 아닌 것으로 보면 올라간 글을 마감으로 바꿔 버림 [P1-8]
        if (sp.status.isBlank()) return "'${sp.title}' — 공고 상태 글자를 못 읽어 그대로 둠"
        val sameTitle = posts.findByTitle(sp.title)
        val live = sameTitle.filter { it.status == "게시됨" }
        val pressed = sameTitle.filter { it.status == "마감" && it.closeCheckMethod == PostActions.CLOSE_UNCHECKED }

        // 당근에서 구인중이 아닌 글 (마감 등) → 몇 회차인지 가려 그 회차만 (A8 마감 확인)
        if (!sp.isLive) return closedOnDaangn(db, sp, sameTitle, now)

        // 이미 앱에 올라가 있는 글 → 사람이 올린 글이면 게시 시각을 고치고, 직접 올린 공고면 빈 칸만 화면 내용으로 채움
        if (live.size > 1) return "'${sp.title}' — 앱에 같은 공고명이 ${live.size}개 올라가 있어 그대로 둠 (확인 필요)"
        if (live.size == 1) {
            val p = live[0]
            val fixed = fixPostedAt(db, p, sp, now)
            val s = posts.getStandingPost(p.standingPostId)
            if (s != null && s.postWhen == PostActions.POST_MANUAL) {
                val filled = s.copy(
                    category = s.category.ifBlank { sp.category },
                    area = s.area.ifBlank { sp.area },
                    schedule = s.schedule.ifBlank { sp.schedule },
                    pay = s.pay.ifBlank { sp.pay },
                    body = s.body.ifBlank { sp.body },
                )
                if (filled != s) {
                    posts.updateStandingPost(filled)
                    return "'${sp.title}' — 이미 있는 공고에 화면 내용(급여·조건·본문 등)을 채움$fixed"
                }
            }
            return "'${sp.title}' — 이미 앱에 있음 (${p.round}회차 게시 중)$fixed"
        }

        // 앱에선 '마감했어요'를 눌렀는데 당근에는 아직 구인중 → 알리기만 (앱이 당근에서 마감하지 않음, A8)
        if (pressed.isNotEmpty()) {
            return "'${sp.title}' — 앱에선 '마감했어요'를 눌렀는데 당근에는 아직 구인중이에요. 당근에서 마감해 주세요"
        }

        // 새로 가져오기: 직접 올린 공고 등록(B0)과 같은 기록 (상시 공고 '직접 올릴 때만' + 게시물 '사람이 올림')
        val sameStanding = posts.standingPostsWithTitle(sp.title)
        sameStanding.firstOrNull { it.postWhen != PostActions.POST_MANUAL }?.let {
            return "'${sp.title}' — 앱의 상시 공고 '${it.name}'도 같은 공고명이라 그대로 둠 (확인 필요)"
        }
        val waiting = PostActions.waitingFor(db, sp.title)
        val accountId = AccountSync.current(db)?.id // 가져온 공고의 계정 = 지금 계정 (DB 설계 — 나의 당근 닉네임의 계정)
        val estimated = MyPostParser.estimatePostedAt(sp.postedAgo, now) ?: now
        val postedAt = minOf(estimated, waiting.minOfOrNull { it.appliedAt } ?: estimated)
        val earlier = sameStanding.firstOrNull() // 전에 가져왔던 직접 올린 공고면 다음 회차로
        val standingId = earlier?.id ?: posts.insertStandingPost(
            StandingPost(
                name = sp.title, title = sp.title, body = sp.body, category = sp.category, area = sp.area,
                schedule = sp.schedule, pay = sp.pay, postWhen = PostActions.POST_MANUAL, status = "켜짐", accountId = accountId,
            )
        )
        val round = (earlier?.let { e -> posts.postingsOf(e.id).maxOfOrNull { it.round } } ?: 0) + 1
        val postingId = posts.insertPosting(
            Posting(
                standingPostId = standingId, round = round, title = sp.title, body = sp.body, accountId = accountId,
                postedBy = PostActions.POSTED_BY_HUMAN, postedAt = postedAt, status = "게시됨",
                reason = "$IMPORT_MARK (게시 시각은 '${sp.postedAgo}'로 어림)",
            )
        )
        posts.setCurrentPosting(standingId, postingId)
        PostActions.attach(db, waiting, standingId, postingId)
        return "'${sp.title}' 새로 가져옴 (${round}회차 게시 중)" +
            if (waiting.isNotEmpty()) " · 기다리던 지원자 ${waiting.size}명을 붙임" else ""
    }

    /**
     * 당근에서 구인중이 아닌 글(마감 등): 화면의 글이 몇 회차인지 올린 때로 가려(PostRounds) 그 회차만 —
     * 올라가 있는 회차면 마감(마감한 쪽 담당자 — 앱이 올린 글도 · 마감 이유는 빈 칸 · 마감 시각은 앱이 본 때),
     * '마감했어요' 뒤 확인 전이면 확인. 확인되면 그때 다음 게시 시각. 맞는 회차가 없거나 둘 이상이면 바꾸지 않고 결과 줄에만
     */
    private suspend fun closedOnDaangn(db: AppDatabase, sp: ScreenPost, sameTitle: List<Posting>, now: Long): String {
        val posts = db.postDao()
        val state = sp.status
        val w = PostRounds.window(sp.postedAgo, now)
            ?: return "'${sp.title}' — 당근 상태: $state · 올린 때('${sp.postedAgo}')를 못 읽어 그대로 둠"
        val round = PostRounds.match(sameTitle, w)
        if (round == null) {
            val n = PostRounds.hits(sameTitle, w).size
            return "'${sp.title}' — 당근 상태: $state · " +
                if (n == 0) "올린 때('${sp.postedAgo}')에 맞는 회차가 앱에 없어 그대로 둠" else "올린 때에 맞는 회차가 ${n}개라 못 가려 그대로 둠 (확인 필요)"
        }
        val standing = posts.getStandingPost(round.standingPostId)
        return when {
            round.status == "게시됨" -> {
                posts.updatePosting(
                    round.copy(
                        status = "마감", closedAt = now, closedBy = "담당자", closeReason = "", closeCheckedAt = now,
                        closeCheckMethod = "'내 공고' 확인 (마감 시각은 앱이 본 때)",
                    )
                )
                standing?.let { s ->
                    posts.updateStandingPost(
                        s.copy(
                            currentPostingId = if (s.currentPostingId == round.id) null else s.currentPostingId,
                            nextPostAt = PostActions.nextAfterClose(s, now),
                        )
                    )
                }
                "'${sp.title}' ${round.round}회차 — 당근 상태: $state → 앱에서도 마감으로 남김"
            }
            round.status == "마감" && round.closeCheckMethod == PostActions.CLOSE_UNCHECKED -> {
                posts.updatePosting(round.copy(closeCheckedAt = now, closeCheckMethod = "${PostActions.CLOSED_BY_BUTTON} 뒤 '내 공고' 확인"))
                standing?.let { s -> posts.updateStandingPost(s.copy(nextPostAt = PostActions.nextAfterClose(s, now))) }
                "'${sp.title}' ${round.round}회차 — '마감했어요'를 당근에서 확인함 (당근 상태: $state)"
            }
            else -> "'${sp.title}' ${round.round}회차 — 당근 상태: $state (앱 기록도 '${round.status}' — 그대로)"
        }
    }

    /**
     * 사람이 올린 글(가져온 공고·직접 올린 공고)의 게시 시각은 어림값 → 구인중 상세의 올린 때 폭 밖이면 그 값으로 고침
     * (마감된 뒤 회차를 가릴 수 있게). '끌올'이면 끌어올린 때라 안 고침. 고쳤으면 결과 줄에 붙일 글
     */
    private suspend fun fixPostedAt(db: AppDatabase, p: Posting, sp: ScreenPost, now: Long): String {
        if (p.postedBy != PostActions.POSTED_BY_HUMAN) return ""
        val w = PostRounds.window(sp.postedAgo, now) ?: return ""
        val at = p.postedAt
        if (w.bumped || (at != null && at in w.from..w.to)) return ""
        val estimate = MyPostParser.estimatePostedAt(sp.postedAgo, now) ?: return ""
        db.postDao().updatePosting(p.copy(postedAt = estimate))
        return " · 게시 시각을 올린 때('${sp.postedAgo}')로 고침"
    }

    /**
     * 개발용: 화면에서 가져온 게시물(과, 다른 회차가 없으면 그 상시 공고)을 지움.
     * 붙어 있던 지원자는 다시 '공고 못 찾음' 보류로 (다시 가져오면 다시 붙음). 지운 게시물 수를 돌려줌
     */
    suspend fun deleteImported(db: AppDatabase): Int {
        val posts = db.postDao()
        val imported = posts.postingsWithReasonPrefix(IMPORT_MARK)
        if (imported.isEmpty()) return 0
        val ids = imported.map { it.id }
        db.applicantDao().holdForDeletedPostings(ids, ApplicantStatus.HOLD, HoldReason.POST_NOT_FOUND, ApplicantStatus.MERGED)
        ApplicantStore.settleAll(db) // 보류가 된 지원자의 처리 전 메시지 → 넘김
        posts.clearCurrentPosting(ids)
        posts.deletePostingsById(ids)
        val emptied = imported.map { it.standingPostId }.distinct().filter { posts.postingsOf(it).isEmpty() }
        if (emptied.isNotEmpty()) posts.deleteStandingPostsById(emptied)
        return imported.size
    }
}
