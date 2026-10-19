package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus

/**
 * 실제 발송 시험 ① 대상·안전 판단 (순수 코드 — 폰 없이 시험). 10/18 재걸: 테스트 폰 계정으로 전체 과정을 실제로 해 보고 끔.
 * 기존 지원자에겐 절대 안 보내게 — 셋이 다 맞을 때만 대상:
 *   시험이 켜져 있음(연습·실제) · 이름이 테스트 폰 당근 닉네임과 같음 · 시험을 켠 뒤(30분 여유) 들어온 지원
 * 그 밖에 '당근 응대' 스위치·전체 멈춤·사람이 맡은 지원자(ReplyRules)·한도를 봄.
 * 설정 값은 LiveSettings(폰)가 Config로 만들어 넘김
 */
object LiveGate {
    enum class Mode(val label: String) { OFF("끔"), REHEARSAL("연습"), LIVE("실제 발송") }

    data class Config(
        val mode: Mode = Mode.OFF,
        val testName: String = "",        // 테스트 폰 당근 닉네임 (화면에 보이는 그대로)
        val armedAt: Long = 0L,           // 시험을 켠 때 (끔 → 연습·실제)
        val daangnReply: Boolean = false, // '당근 응대' 스위치 (기능 켜기·끄기 — B2~B3)
        val allStop: Boolean = false,     // '전체 멈춤'
    )

    const val ARM_MARGIN_MS = 30 * 60_000L // 켜기 전 이만큼 안에 들어온 지원도 대상 (지원하고 나서 켰을 때)
    const val MAX_PER_APPLICANT = 4        // 한 사람에게 보내는 당근 메시지 (첫 메시지 + 다시 요청 2번 + 마무리)
    const val MAX_PER_DAY = 10             // 하루에 앱이 보내는 당근 메시지 (시험 한도 — 계정 한도가 더 작으면 그것)

    /** 하루 당근 메시지 한도: 그 계정의 한도와 시험 한도 중 작은 것 (점검 P1-12 — 계정을 모르면 시험 한도) */
    fun dailyLimit(account: Account?): Int = minOf(account?.dailyMessageLimit ?: MAX_PER_DAY, MAX_PER_DAY)

    /** 앱이 실제 발송 시험에서 다루는 상태 (그 밖은 보낼 단계가 아님) */
    val HANDLED = setOf(ApplicantStatus.NEW, ApplicantStatus.FIRST_SENT, ApplicantStatus.CONTACT)

    /** 설정만 보고 시험이 안 도는 이유 (지원자와 상관없이 — '지금 한 번 확인'이 먼저 봄). 없으면 null */
    fun configProblem(c: Config): String? = when {
        c.mode == Mode.OFF -> "실제 발송 시험이 꺼져 있음"
        c.allStop -> "'전체 멈춤'이 켜져 있음"
        !c.daangnReply -> "'당근 응대'가 꺼져 있음"
        c.testName.isBlank() -> "테스트 폰 닉네임이 비어 있음"
        else -> null
    }

    /** 시험 대상인지 (알림이 왔을 때 일을 잡을지 — 보내도 되는지는 block) */
    fun isTarget(c: Config, a: Applicant): Boolean =
        c.mode != Mode.OFF && c.testName.isNotBlank() && !a.isSample && a.name.trim() == c.testName.trim() &&
            a.appliedAt >= c.armedAt - ARM_MARGIN_MS

    /**
     * 지금 이 지원자에게 보내면 안 되는 이유 (없으면 null). sentToApplicant·sentToday = 앱이 보낸 당근 메시지 수,
     * unknownSends = 결과 불명으로 남은 메시지 수 (보냈는지 모르는 게 있으면 사람이 방을 볼 때까지 안 보냄)
     */
    fun block(c: Config, a: Applicant, sentToApplicant: Int, sentToday: Int, unknownSends: Int = 0, dailyLimit: Int = MAX_PER_DAY): String? {
        configProblem(c)?.let { return it }
        val people = ReplyRules.peopleReason(a)
        return when {
            a.isSample -> "예시 지원자"
            a.name.trim() != c.testName.trim() -> "테스트 폰 지원자가 아님 — 기존 지원자에겐 안 보냄"
            a.appliedAt < c.armedAt - ARM_MARGIN_MS -> "시험을 켜기 전에 들어온 지원 — 기존 지원자에겐 안 보냄"
            a.postingId == null -> "공고를 못 찾은 지원 (보류)"
            people != null -> people
            a.status !in HANDLED -> "상태가 '${a.status}' — 앱이 당근으로 보낼 단계가 아님"
            unknownSends > 0 -> "결과 불명인 메시지 ${unknownSends}개 — 사람이 채팅방을 확인할 때까지 안 보냄"
            sentToApplicant >= MAX_PER_APPLICANT -> "이 지원자에게 보낸 당근 메시지가 한도 (${MAX_PER_APPLICANT}개)"
            sentToday >= dailyLimit -> "오늘 앱이 보낸 당근 메시지가 한도 (${dailyLimit}개)"
            else -> null
        }
    }
}
