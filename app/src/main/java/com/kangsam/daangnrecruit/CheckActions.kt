package com.kangsam.daangnrecruit

/** 확인 필요 카드의 버튼이 하는 일 (화면 설계 '4. 확인 필요', DB 설계 '합치기 기록') */
object CheckActions {
    /**
     * 합치기 보류 카드가 견줄 상대: 같은 번호(연락처 키)의 다른 줄 중 끝나지 않은 줄(종료·합침·미응답 빼고) —
     * 같은 공고(상시 공고) 줄 먼저, 그다음 먼저 지원한 줄. 없으면 null
     */
    fun mergeTarget(a: Applicant, all: List<Applicant>): Applicant? =
        all.filter { it.id != a.id && a.phoneKey.isNotBlank() && it.phoneKey == a.phoneKey && it.status !in ApplicantView.FINISHED }
            .sortedWith(compareBy<Applicant>({ !samePost(a, it) }, { it.appliedAt }))
            .firstOrNull()

    /** 같은 공고에 낸 지원인지 (상시 공고 기준) — 같으면 합치고, 다르면 줄은 따로 둠 */
    fun samePost(a: Applicant, other: Applicant): Boolean = a.standingPostId != null && a.standingPostId == other.standingPostId

    /** 합치기 보류 → 다른 사람(따로): 멈추기 전 상태로 그대로 진행 */
    suspend fun separate(db: AppDatabase, a: Applicant) {
        ApplicantStore.update(db, release(a).copy(sameCandidate = ""))
    }

    /**
     * 합치기 보류 → 같은 사람 (DB 설계 '합치기 기록'):
     * - 같은 공고: 나중 줄을 '합침'으로 두고 합친 곳에 먼저 줄을 적음. 대화는 그대로
     * - 다른 공고: 줄은 따로 두고 보류만 풂 — 연락처 키가 같아서 같은 사람으로 봄 (같은 사람 후보 칸은 그대로) [P1-11]
     */
    suspend fun merge(db: AppDatabase, a: Applicant, other: Applicant) {
        if (!samePost(a, other)) {
            ApplicantStore.update(db, release(a))
            return
        }
        if (a.appliedAt >= other.appliedAt) {
            ApplicantStore.update(
                db,
                a.copy(
                    status = ApplicantStatus.MERGED, mergedInto = other.id, statusBeforeHold = "",
                    reason = "같은 사람 — ${other.name} 줄로 합침",
                )
            )
        } else {
            ApplicantStore.update(db, other.copy(status = ApplicantStatus.MERGED, mergedInto = a.id, reason = "같은 사람 — ${a.name} 줄로 합침"))
            ApplicantStore.update(db, release(a).copy(sameCandidate = ""))
        }
    }

    /** 담당자 확인 → 직접 답한 뒤 앱에 다시 맡기기: 멈추기 전 상태로 */
    suspend fun handBack(db: AppDatabase, a: Applicant) {
        ApplicantStore.update(db, release(a))
    }

    /** 공고 못 찾음 → 공고 고르기 */
    suspend fun pickPosting(db: AppDatabase, a: Applicant, p: Posting) {
        ApplicantStore.update(db, release(a).copy(standingPostId = p.standingPostId, postingId = p.id))
    }

    /** 공고 못 찾음 → 직접 올린 공고 등록 (B0). 같은 공고명으로 못 찾았던 지원자를 모두 붙이고 그 수를 돌려줌 */
    suspend fun registerManualPost(db: AppDatabase, a: Applicant): Int =
        PostActions.registerManual(db, a.postTitle, isSample = a.isSample)

    /** 보류·담당자 확인을 풀어 멈추기 전 상태로 ('공고 못 찾음 — 당근 상태: …'였으면 '당근 상태: …'를 되살림) */
    private fun release(a: Applicant) = a.copy(
        status = a.statusBeforeHold.ifBlank { ApplicantStatus.NEW },
        statusBeforeHold = "",
        reason = HoldReason.restore(a.reason),
    )
}
