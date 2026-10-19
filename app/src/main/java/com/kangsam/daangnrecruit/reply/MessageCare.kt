package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus

/**
 * 앱 처리 기록 (DB 설계 '앱 처리 기록', 사용자 프로세스 '메시지 기록') — 지원자 메시지마다 앱이 어떻게 했는지. 순수 코드.
 *   처리 전(빈 칸) → 앱이 답하면 '답함' · 기다리기로 하면 '기다림' · 사람에게 넘기거나 끝나면 '넘김'
 * 앱이 응대 중인 지원자 = 앱이 맡은 지원(ReplyScope) + 상태가 새 지원·첫 메시지 보냄·연락처 확보·문자 진행 중·가능 시간 확보
 *   + '연락 중' 표시 없음. 그 지원자의 메시지만 처리 전으로 들어오고, 그 밖은 넘김으로 들어옴 (알림·채팅방·문자함 어디서 와도 같음).
 * 앱이 응대하지 않게 되면(담당자 확인·담당자 응대 중·보류·넘김·종료·미응답·합침, '연락 중' 표시) 처리 전인 메시지를 모두 넘김으로
 * — 지원자 줄을 바꾸는 곳(ApplicantStore.update)이 맞춤
 */
object MessageCare {
    const val PENDING = ""       // 처리 전
    const val ANSWERED = "답함"
    const val WAITING = "기다림"
    const val HANDED_OVER = "넘김"

    /** 앱이 응대하는 상태 */
    val HANDLING = setOf(
        ApplicantStatus.NEW, ApplicantStatus.FIRST_SENT, ApplicantStatus.CONTACT, ApplicantStatus.SMS, ApplicantStatus.TIME,
    )

    /** 앱이 응대 중인 지원자인지 (owns = 앱이 맡은 지원 — ReplyScope.owns) */
    fun appHandles(a: Applicant, owns: Boolean): Boolean = owns && a.status in HANDLING && a.contactingMark.isBlank()

    /** 들어오는 지원자 메시지의 앱 처리: 앱이 응대 중이면 처리 전(빈 칸), 아니면 넘김 */
    fun inbound(a: Applicant, owns: Boolean): String = if (appHandles(a, owns)) PENDING else HANDED_OVER
}
