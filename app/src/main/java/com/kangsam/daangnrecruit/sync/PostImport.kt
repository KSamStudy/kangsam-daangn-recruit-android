package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.StandingPost

/**
 * 당근 '내 공고' 상세 화면에서 읽은 공고를 앱 기록에 맞춤 (프로세스 C — 사용자 프로세스 B0 직접 올린 공고 등록, A8 마감 확인).
 * 당근에는 아무것도 하지 않음. 무엇을 했는지 한 줄을 돌려줌
 */
object PostImport {
    /** 화면에서 가져온 게시물 표시 (사유 앞부분). 개발용 '가져온 공고·지원자 지우기'도 이걸로 찾음 */
    const val IMPORT_MARK = "'내 공고' 화면에서 가져옴"

    suspend fun importFromScreen(db: AppDatabase, sp: ScreenPost, now: Long = System.currentTimeMillis()): String {
        val posts = db.postDao()
        val sameTitle = posts.findByTitle(sp.title)
        val live = sameTitle.filter { it.status == "게시됨" }
        val pressed = sameTitle.filter { it.status == "마감" && it.closeCheckMethod == PostActions.CLOSE_UNCHECKED }

        // 당근에서 구인중이 아닌 글 (마감 등) → 앱에 올라가 있는 걸로 돼 있으면 마감으로 (A8 마감 확인)
        if (!sp.isLive) {
            for (p in pressed) {
                posts.updatePosting(
                    p.copy(closeCheckedAt = now, closeCheckMethod = "${PostActions.CLOSED_BY_BUTTON} 뒤 '내 공고' 확인")
                )
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
        sameStanding.firstOrNull { it.postWhen != PostActions.POST_MANUAL }?.let {
            return "'${sp.title}' — 앱의 상시 공고 '${it.name}'도 같은 공고명이라 그대로 둠 (확인 필요)"
        }
        val waiting = PostActions.waitingFor(db, sp.title)
        val estimated = MyPostParser.estimatePostedAt(sp.postedAgo, now) ?: now
        val postedAt = minOf(estimated, waiting.minOfOrNull { it.appliedAt } ?: estimated)
        val earlier = sameStanding.firstOrNull() // 전에 가져왔던 직접 올린 공고면 다음 회차로
        val standingId = earlier?.id ?: posts.insertStandingPost(
            StandingPost(
                name = sp.title, title = sp.title, body = sp.body, category = sp.category, area = sp.area,
                schedule = sp.schedule, pay = sp.pay, postWhen = PostActions.POST_MANUAL, status = "켜짐",
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
        PostActions.attach(db, waiting, standingId, postingId)
        return "'${sp.title}' 새로 가져옴 (${round}회차 게시 중)" +
            if (waiting.isNotEmpty()) " · 기다리던 지원자 ${waiting.size}명을 붙임" else ""
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
        posts.clearCurrentPosting(ids)
        posts.deletePostingsById(ids)
        val emptied = imported.map { it.standingPostId }.distinct().filter { posts.postingsOf(it).isEmpty() }
        if (emptied.isNotEmpty()) posts.deleteStandingPostsById(emptied)
        return imported.size
    }
}
