package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.Message

/**
 * 지원자 응대(프로세스 B)에서 앱이 메시지를 보내기 전에 보는 규칙. 순수 코드라 폰 없이 시험함.
 * - 보낼지 판단: 사용자 프로세스 B2 · '기능 켜기·끄기' (자동으로 보내지 않는 지원자)
 * - 발송 전 검사: 사용자 프로세스 '발송 전 검사' · '문구 규칙', 금지 표현은 '사람이 정할 항목 1차값' 초안 (담당자 검토 전)
 * 보낼 문구 자체는 Templates, 보내는 때는 SendTiming
 */
object ReplyRules {
    const val MAX_LINES = 2  // 당근 메시지 줄 수 (문구 규칙 '당근 채팅' 1~2줄)
    const val MAX_CHARS = 70 // 당근 메시지 글자 수, 공백 포함 (1차값 금지 표현 '길이')
    const val SEND_FROM = 9  // 발송 허용 시간대 09~21시 (1차값 '시간·한도')
    const val SEND_UNTIL = 21

    /** 앱이 보낸 우리 쪽 메시지의 '보낸 방법' (그 밖의 우리 쪽 메시지는 사람이 보낸 것) */
    const val SENT_BY_APP = "앱 자동"

    /** 업체 느낌 말 (1차값 금지 표현 — 회사명 표기 목록은 담당자 결정 전이라 지금은 '강쌤'만 넣음) */
    val BUSINESS_WORDS = listOf("저희 업체", "저희 센터", "저희 회사", "저희 학원", "소속 강사", "강사 시스템", "상담 예약", "강쌤")

    /** 일정 확정 말 (문구 규칙 '당근·문자 공통' — 학부모 OK 전 일정 확정 금지) */
    val SCHEDULE_WORDS = listOf("확정", "뵙겠습니다", "뵐게요", "잡아드릴게요", "잡을게요", "정해드릴게요", "로 정할게요")

    private val MONEY = Regex("""\d[\d,]*\s*(만\s*)?원""")
    private val LINK = Regex("""https?://|www\.|\.(com|kr|net|io|co)\b""", RegexOption.IGNORE_CASE)

    /** 앱이 자동으로 보내지 않는 상태 (사람이 맡았거나 확인이 필요한 지원자) */
    val NOT_AUTO = setOf(ApplicantStatus.HOLD, ApplicantStatus.STAFF_HANDLING, ApplicantStatus.STAFF_CHECK)

    /** 이 지원자에겐 앱이 자동으로 보내지 않는 이유 (예시 · 사람이 맡음). 보내도 되면 null */
    fun notAutoReason(a: Applicant): String? =
        if (a.isSample) "예시 지원자 (당근에 방이 없음)" else peopleReason(a)

    /**
     * 사람이 맡고 있어서 앱이 손대지 않는 이유 ('연락 중' 표시 · 보류 · 담당자 응대 중 · 담당자 확인 — 기능 켜기·끄기 '자동으로 보내지 않는 지원자').
     * 예시 지원자는 따지지 않음 (개발 시험에서 예시로 흐름을 보려고 — 예시는 notAutoReason이 보내지 않게 막음)
     */
    fun peopleReason(a: Applicant): String? = when {
        a.contactingMark.isNotBlank() -> "'연락 중' 표시가 있음 — 사람이 직접 연락 중"
        a.status in NOT_AUTO -> "상태가 '${a.status}'" + a.reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() + " — 앱이 자동으로 보내지 않음"
        else -> null
    }

    /**
     * B2 첫 메시지를 보내도 되는지 앱 기록(DB)으로 봄. 안 되면 이유, 되면 null
     * - 예시 지원자 · '연락 중' 표시 · 보류·담당자 응대 중·담당자 확인 → 자동으로 보내지 않음
     * - 상태가 '새 지원'이 아님 → 이미 보냈거나 끝난 지원
     * - 대화에 앱이 보내지 않은 우리 쪽 메시지 → 사람이 응대 중으로 봄 ('담당자 응대 중'으로 바꿀 것 — B2)
     */
    fun firstMessageBlock(a: Applicant, messages: List<Message>): String? {
        notAutoReason(a)?.let { return it }
        if (a.status != ApplicantStatus.NEW) return "상태가 '${a.status}' — 첫 메시지는 '새 지원'일 때만 보냄"
        val staff = staffMessages(messages)
        if (staff > 0) return "대화에 앱이 보내지 않은 우리 쪽 메시지 ${staff}개 — 사람이 응대 중으로 봄 ('담당자 응대 중'으로)"
        return null
    }

    /** 대화에서 사람(담당자)이 보낸 우리 쪽 메시지 수 */
    fun staffMessages(messages: List<Message>): Int = messages.count { it.sender == "우리" && it.sendMethod != SENT_BY_APP }

    /** 채팅방 화면에서 본 우리 쪽 말풍선 수 → 있으면 사람이 응대 중 (앱이 보낸 당근 메시지는 아직 없으니 모두 사람이 보낸 것) */
    fun roomBlock(oursInRoom: Int): String? =
        if (oursInRoom > 0) "채팅방에 우리 쪽 메시지 ${oursInRoom}개 — 사람이 응대 중으로 봄 ('담당자 응대 중'으로)" else null

    /** 당근 메시지 발송 전 검사 (LLM 말고 정해진 규칙으로). 걸린 것 목록, 비어 있으면 통과 */
    fun check(text: String): List<String> {
        if (text.isBlank()) return listOf("빈 문구")
        val out = common(text).toMutableList()
        val lines = text.trim().lines().size
        if (lines > MAX_LINES) out.add(0, "줄 수 ${lines}줄 (최대 ${MAX_LINES}줄)")
        if (text.length > MAX_CHARS) out.add(0, "글자 수 ${text.length}자 (최대 ${MAX_CHARS}자)")
        return out
    }

    /** 문자 발송 전 검사: 첫 문장은 정해둔 문장 그대로 (문구 규칙 '문자') + 공통 금지 */
    fun checkSms(text: String): List<String> {
        if (text.isBlank()) return listOf("빈 문구")
        val out = common(text).toMutableList()
        if (text.lines().first().trim() != Templates.SMS_FIRST_LINE) out.add(0, "첫 문장이 정해둔 문장과 다름")
        return out
    }

    /** 당근·문자 공통: 이모지, 업체 느낌 말, 일정 확정 말, 금액·링크·전화번호 */
    private fun common(text: String): List<String> {
        val out = mutableListOf<String>()
        if (hasEmoji(text)) out += "이모지"
        BUSINESS_WORDS.filter { text.contains(it) }.forEach { out += "업체 느낌 말 '$it'" }
        SCHEDULE_WORDS.filter { text.contains(it) }.forEach { out += "일정 확정 말 '$it'" }
        if (MONEY.containsMatchIn(text)) out += "금액"
        if (LINK.containsMatchIn(text)) out += "링크"
        if (PhoneNumber.find(text).isNotEmpty()) out += "전화번호"
        return out
    }

    /** 이모지가 있는지 (그림 문자·기호·국기·변형 선택자) */
    fun hasEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** 발송 허용 시간인지 (시 0~23) */
    fun inSendHours(hour: Int): Boolean = hour in SEND_FROM until SEND_UNTIL
}
