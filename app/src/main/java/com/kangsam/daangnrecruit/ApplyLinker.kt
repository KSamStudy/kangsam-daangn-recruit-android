package com.kangsam.daangnrecruit

/**
 * 지원 건을 게시물에 붙이는 곳 (사용자 프로세스 B1, DB 설계 '공고명 규칙').
 * 알림의 공고명과 같은 게시물 중 알림 시각에 올라가 있던 것 → 없으면 같은 공고명으로 가장 최근에 마감된 것 →
 * 그래도 없거나 둘 이상이면 못 찾은 것: 상태 '보류', 사유 '공고 못 찾음' (확인 필요에서 공고 고르기 / 직접 올린 공고 등록)
 *
 * 지원 알림 한 건을 남기는 입구는 save (알림 리스너는 부르기만): 공고를 붙인 뒤 같은 지원 건이 이미 있으면 새 줄을 안 만듦
 */
object ApplyLinker {
    /** 지원 알림 한 건을 남긴 결과 */
    enum class Kind {
        NEW,         // 새 줄
        SAME_PERSON, // 같은 글에 이름이 같은 줄이 이미 있음 (지원자 목록으로 먼저 들어옴 · 묶여 늦게 온 알림) — 새 줄 안 만듦
        SAME_NOTICE, // 같은 알림이 또 옴 (지원 키가 같음)
    }

    data class Saved(val id: Long, val kind: Kind)

    /**
     * 지원 알림 한 건 → 지원자 줄 (B1). 같은 알림이면 그대로, 공고를 붙인 뒤 같은 지원 건(sameApplication)이 있으면 그 줄을 돌려줌 [P1-1].
     * 지원 키는 알림(앱·시각·내용)과 지원자 목록(글·이름·…)이 서로 달라서 키만으로는 같은 지원을 못 알아봄 (DB 설계 '지원 키')
     */
    suspend fun save(db: AppDatabase, a: Applicant): Saved {
        val dao = db.applicantDao()
        dao.bySourceKey(a.sourceKey)?.let { return Saved(it.id, Kind.SAME_NOTICE) }
        val linked = link(db, a)
        sameApplication(db, linked)?.let { return Saved(it.id, Kind.SAME_PERSON) }
        val id = dao.insert(linked)
        if (id > 0) return Saved(id, Kind.NEW)
        return Saved(dao.bySourceKey(a.sourceKey)?.id ?: -1L, Kind.SAME_NOTICE) // 그사이 같은 알림이 먼저 들어감
    }

    /**
     * 이미 있는 같은 지원 건: 같은 글(게시물)에 이름이 같은 줄 — 글을 못 찾았으면 같은 공고명으로 못 찾은 줄 중 이름이 같은 줄.
     * 합친 줄은 뺌. 여럿이면 먼저 지원한 줄
     */
    suspend fun sameApplication(db: AppDatabase, a: Applicant): Applicant? {
        val dao = db.applicantDao()
        val postingId = a.postingId
        val rows = if (postingId != null) dao.byPosting(postingId) else dao.unlinked(a.postTitle)
        return rows.filter { it.name == a.name && it.status != ApplicantStatus.MERGED && it.isSample == a.isSample }.minByOrNull { it.appliedAt }
    }

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
