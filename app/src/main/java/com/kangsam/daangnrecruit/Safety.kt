package com.kangsam.daangnrecruit

import com.kangsam.daangnrecruit.sync.AccountSync

/**
 * 계정이 위험한 화면을 봤을 때 (사용자 프로세스 '예외 처리' — 보안 인증·이용 제한·계정 경고·정지 → 전체 멈춤 + 알림, 경고·정지 기록).
 * 당근을 다루다 멈춤 글자(DaangnNav.STOP_WORDS)가 보이면 접근성 서비스가 전체 멈춤을 켜고 여기서 계정 상태 기록을 남김 (점검 P1-7).
 * 순수 코드 — 전체 멈춤·앱 알림은 폰 쪽(접근성 서비스·AppAlerts)
 */
object Safety {
    const val SUSPENDED = "정지"
    const val WARNED = "경고"
    private const val DAY_MS = 24 * 60 * 60_000L

    /** 멈춤 글자 → 기록 종류: 정지·제한 글자면 정지, 그 밖(보안 문자·자동 입력 방지·로그인 등)은 경고 */
    fun kindOf(word: String): String = if (word.contains("정지") || word.contains("제한")) SUSPENDED else WARNED

    /** 직전 하루 동안 앱이 한 일 (게시·당근 메시지·문자 — 정지 원인 대조용, DB 설계 '계정 상태 기록') */
    suspend fun recentActivity(db: AppDatabase, now: Long): String {
        val since = now - DAY_MS
        val posted = db.postDao().appPostedBetween(since, now)
        val daangn = db.messageDao().appSentBetween("당근", since, now)
        val sms = db.messageDao().appSentBetween("문자", since, now)
        return "직전 하루: 게시 ${posted}건 · 당근 메시지 ${daangn}건 · 문자 ${sms}건"
    }

    /** 기록을 남길 계정: 지금 계정 (예시가 아닌 계정이 하나면 그 계정 — AccountSync) */
    suspend fun currentAccount(db: AppDatabase): Account? = AccountSync.current(db)

    /**
     * 멈춤 글자를 봄 → 계정 상태 기록 한 줄(종류·당근 안내 문구·직전 하루 앱 동작) + 계정 상태를 경고·정지로.
     * 계정이 없으면 기록 없이 null (전체 멈춤·알림은 그래도 함 — 부른 쪽)
     */
    suspend fun record(db: AppDatabase, account: Account?, word: String, notice: String, now: Long): AccountEvent? {
        val a = account ?: return null
        val kind = kindOf(word)
        val event = AccountEvent(
            accountId = a.id, time = now, kind = kind,
            daangnMessage = notice.ifBlank { word }.take(200),
            recentActivity = recentActivity(db, now),
        )
        val id = db.accountDao().insertEvent(event)
        // 계정 상태: 정지는 늘 정지로, 경고는 정지인 계정을 경고로 되돌리지 않음
        if (a.status != SUSPENDED) db.accountDao().update(a.copy(status = kind))
        return event.copy(id = id)
    }
}
