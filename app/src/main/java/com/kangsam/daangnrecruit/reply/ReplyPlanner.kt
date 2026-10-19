package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.Message
import java.time.ZoneId
import kotlin.random.Random

/**
 * '다음에 앱이 할 일' — 지원자 한 명의 상태·대화를 보고 앱이 다음에 무엇을 언제 보낼지 계획만 세움 (보내지 않음). 순수 코드.
 * 지원자 상세의 '다음에 앱이 할 일 (보내지 않음)'과 첫 메시지 드라이런이 씀. 실제로 보내는 코드를 만들면 이 계획대로 보내게 함
 *
 *   새 지원         → B2 당근 첫 메시지 (번호 요청)
 *   첫 메시지 보냄   → B3 번호 기다림 (번호가 오면 ContactFlow가 '연락처 확보'로)
 *   연락처 확보      → B3 당근 마무리 1회 → B4 첫 문자
 *   그 밖           → 앱이 보낼 것 없음 (이유)
 *
 * 문구는 Templates에서 무작위로 고르고(최근에 보낸 문구는 피함), 때는 SendTiming(받은 뒤 2~10분, 허용 시간 안).
 * 무작위 씨앗을 지원자·단계로 정해서, 같은 지원자에겐 보낼 때까지 같은 문구·같은 때가 보임 (화면을 열 때마다 안 바뀜)
 */
object ReplyPlanner {
    // 단계 (씨앗에도 씀 — 바꾸면 보이는 문구가 바뀜)
    const val STEP_FIRST = "B2"  // 당근 첫 메시지
    const val STEP_CLOSE = "B3"  // 번호 받은 뒤 당근 마무리
    const val STEP_SMS = "B4"    // 첫 문자
    const val STEP_RETRY = "B3R" // 번호 다시 요청 (뒤에 몇 번째인지)

    private const val DAANGN = "당근"
    private const val SMS = "문자"
    private const val OURS = "우리"
    private const val APPLICANT = "지원자"

    /** 기능 스위치 값 (FeatureSettings — 순수 코드에서 쓰려고 값으로 받음. DaangnReply.switches가 만듦) */
    data class Switches(val daangnReply: Boolean = false, val smsReply: Boolean = false, val allStop: Boolean = false)

    /**
     * 보낼 것 하나: 어디로 · 무엇 · 문구 · 보낼 때(sendAt — from에서 무작위로 기다린 뒤) · 발송 전 검사에 걸린 것 ·
     * 지금 안 보내는 이유들 (비면 보낼 수 있음 — 보내는 기능이 생기면)
     */
    data class Send(
        val channel: String,
        val what: String,
        val text: String,
        val sendAt: Long,
        val from: Long,
        val fromLabel: String,
        val problems: List<String>,
        val blocked: List<String>,
    )

    /** 계획: 지금 단계 한 줄 · 보낼 것들 · 참고 */
    data class Plan(val step: String, val sends: List<Send>, val notes: List<String>)

    /** 앱이 맡지 않은 지원이라 안 보내는 이유 (사용자 프로세스 B2 — 앱은 '당근 응대'를 켠 뒤 들어온 지원만 맡음) */
    const val NOT_OWNED = "앱이 맡지 않은 지원 — 사람이 응대 (개발 중엔 실제 발송 시험 대상만 앱이 맡음)"

    /**
     * recentSent = 앱이 최근에 보낸 문구들 (같은 문구를 피하려고).
     * owns = 앱이 맡은 지원인지 (ReplyScope.owns) — 안 맡았으면 보낼 것은 계획만 보이고 '지금은 안 보냄'에 그 이유
     */
    fun plan(
        a: Applicant,
        messages: List<Message>,
        recentSent: Collection<String>,
        switches: Switches,
        zone: ZoneId,
        owns: Boolean = true,
    ): Plan =
        when (a.status) {
            ApplicantStatus.NEW -> firstPlan(a, messages, recentSent, switches, zone, owns)
            ApplicantStatus.FIRST_SENT -> Plan(
                "B3 번호 받기 — 지원자 답을 기다림", emptyList(),
                listOfNotNull(
                    if (!owns) "$NOT_OWNED — 지원자 답은 '넘김'으로 남음" else null,
                    "지원자가 번호를 보내면 알림으로 받아 연락처를 저장하고 '연락처 확보'로 (알림 메시지는 '당근 가져오기'가 켜져 있을 때 기록)",
                    "질문만 오면 짧은 답·번호 다시 요청, 답이 없으면 '미응답' — 아직 만들지 않음",
                ),
            )
            ApplicantStatus.CONTACT -> contactPlan(a, messages, recentSent, switches, zone, owns)
            ApplicantStatus.SMS, ApplicantStatus.TIME -> Plan(
                "B4-2 문자 응대 — 아직 만들지 않음", emptyList(),
                listOf("받은 답을 보고 다음 문자를 정하는 일(가리기·LLM 판단)은 다음 단계"),
            )
            ApplicantStatus.HANDED_OVER -> Plan("B6 넘김 — 앱이 할 일 없음 (리니어 구인 티켓에서 진행)", emptyList(), emptyList())
            ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK, ApplicantStatus.STAFF_HANDLING -> Plan(
                "사람이 볼 차례 — 앱이 보내지 않음", emptyList(),
                listOfNotNull(
                    a.reason.ifBlank { null },
                    a.statusBeforeHold.ifBlank { null }?.let { "풀리면 '$it'부터 이어서 함" },
                ),
            )
            else -> Plan("끝난 지원 — 앱이 할 일 없음", emptyList(), listOfNotNull(a.reason.ifBlank { null }))
        }

    /** 당근 첫 메시지 문구 (계획과 드라이런·실제 발송이 같은 문구를 쓰게) */
    fun firstMessage(a: Applicant, recentSent: Collection<String>): String =
        Templates.pick(Templates.DAANGN_FIRST, random(a, STEP_FIRST), recentSent)

    /** 번호 받은 뒤 당근 마무리 문구 (계획과 실제 발송이 같은 문구) */
    fun closingMessage(a: Applicant, recentSent: Collection<String>): String =
        Templates.pick(Templates.DAANGN_AFTER_NUMBER, random(a, STEP_CLOSE), recentSent)

    /** 번호 다시 요청 문구 — n번째 (1부터). 앞서 보낸 다시 요청 문구도 피하게 recentSent에 넣어 부름 */
    fun retryMessage(a: Applicant, n: Int, recentSent: Collection<String>): String =
        Templates.pick(Templates.DAANGN_RETRY, Random(seed(a.id, STEP_RETRY + n)), recentSent)

    /** 지원자·단계마다 정해지는 무작위 씨앗 */
    fun seed(applicantId: Long, step: String): Long = applicantId * 1_000_003L + step.hashCode()

    private fun random(a: Applicant, step: String) = Random(seed(a.id, step))

    // ---------- 새 지원 → B2 당근 첫 메시지

    private fun firstPlan(a: Applicant, messages: List<Message>, recent: Collection<String>, sw: Switches, zone: ZoneId, owns: Boolean): Plan {
        val random = random(a, STEP_FIRST)
        val text = Templates.pick(Templates.DAANGN_FIRST, random, recent) // firstMessage와 같은 순서로 뽑음
        val at = SendTiming.afterReceived(a.appliedAt, zone, random)
        val problems = ReplyRules.check(text)
        val blocked = listOfNotNull(
            allStop(sw),
            if (!owns) NOT_OWNED else null,
            ReplyRules.firstMessageBlock(a, messages),
            if (!sw.daangnReply) "'당근 응대'가 꺼져 있음" else null,
            failed(problems),
        )
        return Plan(
            "B2 당근 첫 메시지 — 번호 요청",
            listOf(Send(DAANGN, "첫 메시지", text, at, a.appliedAt, "지원", problems, blocked)),
            listOf(
                "당근으로 보내는 건 지금은 실제 발송 시험 대상에게만 (운영은 담당자 확인 뒤 — 확인사항 29)",
                "채팅방이 아직 없는 새 지원자는 지원서를 열어 '채팅하기'로 방을 만들어야 함 — 담당자 확인 전 (확인사항 5)",
            ),
        )
    }

    // ---------- 연락처 확보 → B3 당근 마무리 1회 + B4 첫 문자

    private fun contactPlan(a: Applicant, messages: List<Message>, recent: Collection<String>, sw: Switches, zone: ZoneId, owns: Boolean): Plan {
        val gotAt = numberTime(a, messages)
        val who = if (!owns) NOT_OWNED else ReplyRules.notAutoReason(a)
        val staff = ReplyRules.staffMessages(messages)
        val sends = mutableListOf<Send>()
        val notes = mutableListOf<String>()

        // B3 마무리: 번호 받은 뒤 우리 쪽 당근 메시지가 있으면 이미 함
        val closeRandom = random(a, STEP_CLOSE)
        val closeText = Templates.pick(Templates.DAANGN_AFTER_NUMBER, closeRandom, recent) // closingMessage와 같은 순서로 뽑음
        val closeAt = SendTiming.afterReceived(gotAt, zone, closeRandom)
        val closed = messages.lastOrNull { it.channel == DAANGN && it.sender == OURS && it.time >= gotAt }
        if (closed == null) {
            val problems = ReplyRules.check(closeText)
            sends += Send(
                DAANGN, "번호 받은 뒤 마무리", closeText, closeAt, gotAt, "번호 받은 때", problems,
                listOfNotNull(
                    allStop(sw), who,
                    if (staff > 0) "대화에 사람이 보낸 우리 쪽 메시지 ${staff}개 — 사람이 응대 중으로 봄" else null,
                    if (!sw.daangnReply) "'당근 응대'가 꺼져 있음" else null,
                    failed(problems),
                ),
            )
        } else {
            notes += "번호 받은 뒤 당근 마무리는 이미 함"
        }

        // B4 첫 문자: 우리 쪽 문자가 있으면 이미 함
        val smsRandom = random(a, STEP_SMS)
        val smsText = Templates.pick(Templates.SMS_FIRST, smsRandom, recent)
        val smsAt = SendTiming.afterPrevious(closed?.time ?: closeAt, zone, smsRandom)
        if (messages.none { it.channel == SMS && it.sender == OURS }) {
            val problems = ReplyRules.checkSms(smsText)
            sends += Send(
                SMS, "첫 문자", smsText, smsAt, closed?.time ?: closeAt, if (closed == null) "마무리" else "마무리 보낸 때", problems,
                listOfNotNull(
                    allStop(sw), who,
                    if (!sw.smsReply) "'문자 응대'가 꺼져 있음" else null,
                    "보내기 직전 '다른 담당자가 이미 연락 중인지' 확인(B4-1)을 아직 못 함",
                    failed(problems),
                ),
            )
        } else {
            notes += "첫 문자는 이미 보냄"
        }
        notes += "문자로 보내는 기능은 아직 없음, 당근 마무리는 실제 발송 시험 대상에게만 — 그 밖엔 무엇을 언제 보낼지 계획만 보여 줌"
        return Plan("B3 번호 받음 → 당근 마무리 1회 · B4 첫 문자", sends, notes)
    }

    /** 번호를 받은 때: 그 번호가 처음 나온 지원자 메시지 (없으면 마지막 대화·지원 시각) */
    private fun numberTime(a: Applicant, messages: List<Message>): Long =
        messages.firstOrNull { m -> m.sender == APPLICANT && PhoneNumber.find(m.content).any { it.key == a.phoneKey } }?.time
            ?: a.lastMessageAt ?: a.appliedAt

    private fun allStop(sw: Switches) = if (sw.allStop) "'전체 멈춤'이 켜져 있음" else null

    private fun failed(problems: List<String>) =
        if (problems.isNotEmpty()) "발송 전 검사에 걸림 (${problems.joinToString(", ")})" else null
}
