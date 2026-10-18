package com.kangsam.daangnrecruit.reply

import android.content.Context
import com.kangsam.daangnrecruit.DaangnAccessibilityService
import com.kangsam.daangnrecruit.FeatureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 프로세스 B '지원자 응대'의 폰 쪽 입구 (지금은 첫 메시지 드라이런 — 입력·전송 없음 — 과 스위치 값만).
 * 실제로 보내는 건 담당자가 '당근 응대'를 켠 뒤에만 만들고 켬 (기능_목표 진행 규칙, 확인사항 8).
 * 폰 화면을 다루는 손은 접근성 서비스(DaangnAccessibilityService)가 빌려주고, 당근 가져오기(C)와 동시에 돌지 않음.
 * reply 폴더의 나머지는 폰 없이 시험하는 순수 코드: 문구 틀(Templates) · 보낼 때(SendTiming) · 발송 전 검사(ReplyRules) ·
 * 번호 찾기(PhoneNumber) · 번호 받기(ContactFlow) · 다음에 할 일(ReplyPlanner) · 드라이런(FirstMessageDryRun)
 */
object DaangnReply {
    private const val PREFS = "reply"

    private val _status = MutableStateFlow("")

    /** 지금 하는 일 한 줄 (지원자 상세에 보임) */
    val status: StateFlow<String> = _status

    /** 지금 드라이런 중인지 */
    @Volatile
    var running: Boolean = false
        private set

    /** 지원자 상세의 '첫 메시지 드라이런' 버튼. 시작했으면 안내, 못 하면 이유 */
    fun startDryRun(context: Context, applicantId: Long): String {
        if (FeatureSettings.isOn(context, FeatureSettings.ALL_STOP)) return "'전체 멈춤'이 켜져 있어서 안 돌아요"
        val host = DaangnAccessibilityService.instance
            ?: return "접근성(당근 화면 읽기)이 꺼져 있어요 — 폰 설정 → 접근성에서 켜요"
        if (running || host.driving) return "이미 앱이 당근을 다루는 중이에요"
        running = true
        _status.value = "시작"
        val app = context.applicationContext
        host.runDryRun(applicantId) { result, log -> finished(app, applicantId, result, log) }
        return "당근을 열어 이 지원자 채팅방까지 가 봐요 — 입력·전송은 안 해요. 끝날 때까지 폰을 만지지 마세요 (멈추려면 홈 버튼)"
    }

    /** 지금 하는 일 한 줄 바꾸기 */
    fun say(line: String) {
        _status.value = line
    }

    /** 응대 기능 스위치 값 (ReplyPlanner가 '지금 안 보내는 이유'를 정할 때) */
    fun switches(context: Context) = ReplyPlanner.Switches(
        daangnReply = FeatureSettings.isOn(context, FeatureSettings.DAANGN_REPLY),
        smsReply = FeatureSettings.isOn(context, FeatureSettings.SMS_REPLY),
        allStop = FeatureSettings.isOn(context, FeatureSettings.ALL_STOP),
    )

    private fun finished(context: Context, applicantId: Long, result: String, log: List<String>) {
        running = false
        prefs(context).edit()
            .putString("last_$applicantId", result)
            .putLong("last_at_$applicantId", System.currentTimeMillis())
            .putString("log_$applicantId", log.takeLast(40).joinToString("\n"))
            .apply()
        _status.value = result
    }

    /** 이 지원자의 마지막 드라이런 (시각, 한 줄) */
    fun last(context: Context, applicantId: Long): Pair<Long, String>? {
        val p = prefs(context)
        val text = p.getString("last_$applicantId", null) ?: return null
        return p.getLong("last_at_$applicantId", 0L) to text
    }

    /** 이 지원자의 마지막 드라이런 기록 */
    fun lastLog(context: Context, applicantId: Long): List<String> =
        prefs(context).getString("log_$applicantId", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
