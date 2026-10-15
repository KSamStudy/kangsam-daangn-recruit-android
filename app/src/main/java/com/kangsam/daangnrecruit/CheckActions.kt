package com.kangsam.daangnrecruit

/** 확인 필요 카드의 버튼이 하는 일 (화면 설계 '4. 확인 필요', DB 설계 '합치기 기록') */
object CheckActions {
    /** 합치기 보류 → 다른 사람(따로): 멈추기 전 상태로 그대로 진행 */
    suspend fun separate(db: AppDatabase, a: Applicant) {
        db.applicantDao().update(release(a).copy(sameCandidate = ""))
    }

    /** 합치기 보류 → 같은 사람(합침): 나중 줄을 '합침'으로 두고 합친 곳에 먼저 줄을 적음. 대화는 그대로 */
    suspend fun merge(db: AppDatabase, a: Applicant, other: Applicant) {
        val dao = db.applicantDao()
        if (a.appliedAt >= other.appliedAt) {
            dao.update(
                a.copy(
                    status = ApplicantStatus.MERGED, mergedInto = other.id, statusBeforeHold = "",
                    reason = "같은 사람 — ${other.name} 줄로 합침",
                )
            )
        } else {
            dao.update(other.copy(status = ApplicantStatus.MERGED, mergedInto = a.id, reason = "같은 사람 — ${a.name} 줄로 합침"))
            dao.update(release(a).copy(sameCandidate = ""))
        }
    }

    /** 담당자 확인 → 직접 답한 뒤 앱에 다시 맡기기: 멈추기 전 상태로 */
    suspend fun handBack(db: AppDatabase, a: Applicant) {
        db.applicantDao().update(release(a))
    }

    /** 공고 못 찾음 → 공고 고르기 */
    suspend fun pickPosting(db: AppDatabase, a: Applicant, p: Posting) {
        db.applicantDao().update(release(a).copy(standingPostId = p.standingPostId, postingId = p.id))
    }

    /** 공고 못 찾음 → 직접 올린 공고 등록 (B0). 같은 공고명으로 못 찾았던 지원자를 모두 붙이고 그 수를 돌려줌 */
    suspend fun registerManualPost(db: AppDatabase, a: Applicant): Int =
        PostActions.registerManual(db, a.postTitle, isSample = a.isSample)

    /** 보류·담당자 확인을 풀어 멈추기 전 상태로 */
    private fun release(a: Applicant) = a.copy(
        status = a.statusBeforeHold.ifBlank { ApplicantStatus.NEW },
        statusBeforeHold = "",
        reason = "",
    )
}
