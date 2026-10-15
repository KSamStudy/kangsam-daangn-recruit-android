package com.kangsam.daangnrecruit

/**
 * 지원 건을 게시물에 붙이는 곳 (사용자 프로세스 B1, DB 설계 '공고명 규칙').
 * 알림의 공고명과 같은 게시물 중 알림 시각에 올라가 있던 것 → 없으면 같은 공고명으로 가장 최근에 마감된 것 →
 * 그래도 없거나 둘 이상이면 못 찾은 것: 상태 '보류', 사유 '공고 못 찾음' (확인 필요에서 공고 고르기 / 직접 올린 공고 등록)
 */
object ApplyLinker {
    suspend fun link(db: AppDatabase, a: Applicant): Applicant {
        if (a.postTitle.isBlank()) return hold(a, "${HoldReason.POST_NOT_FOUND} (알림에 공고명 없음)")
        val posting = matchPosting(db.postDao().findByTitle(a.postTitle), a.appliedAt)
            ?: return hold(a, HoldReason.POST_NOT_FOUND)
        return a.copy(standingPostId = posting.standingPostId, postingId = posting.id)
    }

    fun matchPosting(candidates: List<Posting>, time: Long): Posting? {
        // 알림 시각에 올라가 있던 것
        val live = candidates.filter { p ->
            val postedAt = p.postedAt
            val closedAt = p.closedAt
            postedAt != null && postedAt <= time && (closedAt == null || closedAt > time)
        }
        if (live.size == 1) return live[0]
        if (live.size > 1) return null
        // 없으면 같은 공고명으로 가장 최근에 마감된 것 (둘 이상이 같은 때 마감됐으면 못 찾음)
        val closed = candidates.filter { p ->
            val closedAt = p.closedAt
            closedAt != null && closedAt <= time
        }.sortedByDescending { it.closedAt ?: 0L }
        if (closed.isEmpty()) return null
        if (closed.size >= 2 && closed[0].closedAt == closed[1].closedAt) return null
        return closed[0]
    }

    private fun hold(a: Applicant, reason: String) =
        a.copy(status = ApplicantStatus.HOLD, statusBeforeHold = a.status, reason = reason)
}
