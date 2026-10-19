package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.HoldReason

/**
 * B3 번호 받기 + B3-1 합치기 확인 (사용자 프로세스): 지원자가 보낸 당근 메시지에서 휴대폰 번호를 찾아 앱 기록에 남김.
 * 당근·문자에는 아무것도 보내지 않음 — 번호 받은 뒤 당근 마무리·첫 문자는 ReplyPlanner가 '다음에 할 일'로 보여 주기만 함.
 *
 * 부르는 곳
 * - 알림으로 막 받은 지원자 메시지 → onMessage (DaangnNotificationListener, ChatNotify가 새로 남긴 메시지마다)
 * - 화면에서 가져온 지난 대화 → fillFromHistory (DaangnAccessibilityService, 가져오기·사람이 연 채팅방 뒤).
 *   지난 대화는 이미 사람이 응대한 대화라 상태는 안 바꾸고 연락처만 채움
 *
 * 정하는 일 (decide)
 * - 앱이 맡은 지원자 = '첫 메시지 보냄', 또는 '당근 응대'가 켜져 있을 때 '새 지원' (사람이 보낸 우리 쪽 메시지·'연락 중' 표시가 없을 때):
 *   - 010 번호 하나 → 연락처 저장 + '연락처 확보'
 *   - 같은 번호의 다른 지원 건이 있음 → 연락처 저장 + '보류'(합치기 보류). 담당자가 확인 탭에서 '합침·따로'를 정함
 *     (지원자 합치기 규칙엔 같은 번호 + 같은 공고는 합친다고 돼 있지만, 처음엔 앱이 혼자 합치지 않고 담당자가 보게 둠)
 *   - 번호가 여러 개 · 010 11자리가 아님 · 이미 받은 번호와 다름 → '담당자 확인'
 *     (확인 탭에서 담당자가 대화를 보고 직접 답한 뒤 '앱에 다시 맡기기'. 문서의 '보류 + 확인 요청'을 버튼이 있는 카드로)
 * - 그 밖 (사람이 맡음 · 이미 문자 단계 · 끝난 지원 · 지난 대화): 번호가 하나로 분명하고 연락처가 비어 있을 때 연락처만 저장
 * - 예시 지원자도 똑같이 함 (adb 테스트 알림으로 흐름을 보려고 — 예시에겐 앱이 실제로 보내지 않음, ReplyRules.notAutoReason)
 */
object ContactFlow {
    private const val APPLICANT = "지원자" // 대화 표의 보낸 쪽

    /** 정한 일: 바꾼 지원자 줄 (안 바꾸면 null) + 한 줄 설명 */
    data class Change(val updated: Applicant?, val note: String)

    /**
     * 알림으로 막 받은 지원자 메시지 하나. 바꾼 게 있으면 한 줄, 없으면 null.
     * appInCharge = '당근 응대'가 켜져 있는지 (켜져 있으면 '새 지원'에서 번호를 받아도 앱이 맡은 것으로 봄)
     */
    suspend fun onMessage(db: AppDatabase, applicantId: Long, text: String, appInCharge: Boolean): String? {
        val found = PhoneNumber.find(text)
        if (found.isEmpty()) return null
        val a = db.applicantDao().get(applicantId) ?: return null
        val staff = ReplyRules.staffMessages(db.messageDao().listFor(a.id))
        val change = decide(a, found, others(db, a, found), appInCharge, fromHistory = false, staffMessages = staff) ?: return null
        change.updated?.let { ApplicantStore.update(db, it) }
        return "${a.name}: ${change.note}"
    }

    /** 화면에서 가져온 지난 대화에서 연락처가 빈 지원자의 번호를 채움 (상태는 그대로). 채운 사람마다 한 줄 */
    suspend fun fillFromHistory(db: AppDatabase): List<String> {
        val out = mutableListOf<String>()
        for (a in db.applicantDao().withoutPhone(ApplicantStatus.MERGED)) {
            val found = db.messageDao().listFor(a.id)
                .filter { it.sender == APPLICANT && it.channel == "당근" }
                .flatMap { PhoneNumber.find(it.content) }
            if (found.isEmpty()) continue
            val change = decide(a, found, others(db, a, found), appInCharge = false, fromHistory = true) ?: continue
            val updated = change.updated ?: continue // 번호가 애매하면 지난 대화로는 채우지 않음
            ApplicantStore.update(db, updated)
            out += "${a.name}: ${change.note}"
        }
        return out
    }

    /** 번호가 하나로 분명할 때 그 번호를 가진 다른 지원 건 (합친 줄은 뺌) */
    private suspend fun others(db: AppDatabase, a: Applicant, found: List<PhoneNumber.Found>): List<Applicant> {
        val key = found.map { it.key }.distinct().singleOrNull() ?: return emptyList()
        return othersWith(db, a, key)
    }

    /** 이 번호(키)를 가진 다른 지원 건 (합친 줄은 뺌) — 실제 발송 시험의 마무리(LiveTurn)도 씀 */
    suspend fun othersWith(db: AppDatabase, a: Applicant, key: String): List<Applicant> =
        db.applicantDao().samePhone(key, a.id).filter { it.status != ApplicantStatus.MERGED }

    /**
     * 번호를 보고 할 일을 정함 (순수 함수 — 시험은 이것으로). 할 일이 없으면 null
     * - found: 메시지에서 찾은 번호들, others: 같은 번호의 다른 지원 건
     * - fromHistory: 지난 대화에서 찾은 번호 (상태를 안 바꿈)
     * - staffMessages: 대화에 사람이 보낸 우리 쪽 메시지 수 (있으면 사람이 응대 중으로 봄 — B2)
     */
    fun decide(
        a: Applicant,
        found: List<PhoneNumber.Found>,
        others: List<Applicant>,
        appInCharge: Boolean,
        fromHistory: Boolean,
        staffMessages: Int = 0,
    ): Change? {
        if (found.isEmpty() || a.status == ApplicantStatus.MERGED) return null
        val keys = found.map { it.key }.distinct()
        val byApp = !fromHistory && staffMessages == 0 && appHandles(a, appInCharge)

        // 번호가 애매함 (번호는 기록에 안 씀 — 사유에도 번호는 안 적음)
        val problem = when {
            keys.size > 1 -> "번호가 ${keys.size}개"
            !PhoneNumber.isValidKey(keys.single()) -> "010으로 시작하는 11자리가 아님"
            a.phoneKey.isNotBlank() && a.phoneKey != keys.single() -> "이미 받은 번호와 다름"
            else -> null
        }
        if (problem != null) {
            if (!byApp) return Change(null, "번호를 안 채움 — $problem")
            return Change(
                a.copy(status = ApplicantStatus.STAFF_CHECK, statusBeforeHold = a.status, reason = "번호 확인 필요 — $problem"),
                "'담당자 확인'으로 — 번호 확인 필요 ($problem)",
            )
        }

        val f = found.first { it.key == keys.single() }
        if (a.phoneKey == f.key) return null // 이미 받은 번호
        val withPhone = a.copy(phone = f.raw, phoneKey = f.key)
        if (!byApp) {
            if (others.isEmpty()) return Change(withPhone, "연락처 저장 (상태 그대로 '${a.status}')")
            // 같은 번호의 다른 지원 건은 같은 사람 후보 칸에 (표시만 — 상태·합치기는 그대로, DB 설계 '같은 사람 후보')
            val names = others.joinToString(", ") { it.name }
            return Change(withPhone.copy(sameCandidate = names), "연락처 저장 (상태 그대로 '${a.status}') · 같은 번호의 다른 지원 건: $names")
        }
        return gotNumber(a, f, others)
    }

    /**
     * 앱이 맡은 지원자에게서 번호를 하나로 분명히 받음: 연락처 저장 + '연락처 확보'.
     * 같은 번호의 다른 지원 건이 있으면 '보류'(합치기 보류) — 담당자가 확인 탭에서 정함. 실제 발송 시험의 마무리(LiveTurn)도 씀
     */
    fun gotNumber(a: Applicant, f: PhoneNumber.Found, others: List<Applicant>): Change {
        val withPhone = a.copy(phone = f.raw, phoneKey = f.key)
        if (others.isNotEmpty()) {
            val o = others.first()
            val where = if (samePost(a, o)) "같은 공고" else "다른 공고"
            return Change(
                withPhone.copy(
                    status = ApplicantStatus.HOLD, statusBeforeHold = ApplicantStatus.CONTACT,
                    reason = "${HoldReason.MERGE} — 같은 번호의 다른 지원 건 (${o.name} · $where)",
                    sameCandidate = others.joinToString(", ") { it.name },
                ),
                "연락처 저장 + '보류'(합치기 보류) — 같은 번호의 다른 지원 건 ${others.size}개 ($where)",
            )
        }
        return Change(withPhone.copy(status = ApplicantStatus.CONTACT, reason = ""), "연락처 저장 + '연락처 확보'")
    }

    /** 앱이 맡은 지원자인지: 사람이 맡지 않았고, '첫 메시지 보냄'이거나 앱이 당근 응대를 맡은 '새 지원' */
    private fun appHandles(a: Applicant, appInCharge: Boolean): Boolean =
        ReplyRules.peopleReason(a) == null &&
            (a.status == ApplicantStatus.FIRST_SENT || (a.status == ApplicantStatus.NEW && appInCharge))

    /** 같은 공고에 낸 지원인지 (상시 공고 기준, 없으면 공고명) */
    private fun samePost(a: Applicant, o: Applicant): Boolean =
        (a.standingPostId != null && a.standingPostId == o.standingPostId) || (a.postTitle.isNotBlank() && a.postTitle == o.postTitle)
}
