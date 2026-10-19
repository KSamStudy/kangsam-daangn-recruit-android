package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.Applicant

/**
 * '앱이 맡은 지원'을 정하는 한 곳 (사용자 프로세스 B2 — 앱은 켠 뒤 들어온 지원만 맡음, 구현 계획 '지키는 것').
 * 지금(운영 전)은 실제 발송 시험 대상만: 테스트 폰 닉네임 · 시험을 켠 뒤 들어온 지원 (LiveGate.isTarget).
 * 운영 버전(담당자 확인사항 29 뒤)은 여기만 바꿈 — '당근 응대'를 켠 뒤 들어온 새 지원 모두.
 * 설정 값(시험 모드·닉네임·켠 때)은 폰 앱 설정에 있어서, 앱이 켜질 때 KangsamApp이 읽는 법을 넣어 줌 (시험 틀에서는 시험 값)
 */
object ReplyScope {
    /** 지금 설정을 읽는 법 (폰: LiveSettings.config) — 넣기 전엔 시험 꺼짐으로 봄 (아무도 안 맡음) */
    @Volatile
    var settings: () -> LiveGate.Config = { LiveGate.Config() }

    /** 앱이 맡은 지원인지 */
    fun owns(a: Applicant): Boolean = LiveGate.isTarget(settings(), a)

    /** 앱이 응대 중인 지원자인지 (맡음 + 응대하는 상태 + '연락 중' 표시 없음 — MessageCare) */
    fun handles(a: Applicant): Boolean = MessageCare.appHandles(a, owns(a))
}
