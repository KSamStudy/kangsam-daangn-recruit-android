package com.kangsam.daangnrecruit.reply

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.DaangnAccessibilityService
import com.kangsam.daangnrecruit.DbOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 실제 발송 시험의 폰 쪽: 언제 한 차례(LiveTurn)를 돌릴지 정함 (사용자 프로세스 B2·B3, 10/18 재걸 — 테스트 폰 계정으로 한 번에 해 보고 끔).
 *
 *   지원 알림으로 시험 대상이 들어옴 (리스너 → onApplied)     → 20~40초 뒤 한 차례 (첫 메시지)
 *   당근 알림이 옴 (리스너 → onNotice)                       → 답을 기다리는 시험 대상이 있으면 15~30초 뒤
 *     (실제 지원자 채팅 알림 모양을 아직 몰라서 당근 알림이면 다 — 차례는 방을 보고 할 일이 없으면 안 보냄)
 *   보냄 · 기다림으로 끝남                                    → 2분 뒤 한 번 더 봄 (알림이 늦거나 안 와도 이어지게. 보낸 뒤 5번까지)
 *   멈춤 (화면이 예상과 다름 — 막 지원해 목록에 아직 없음 등)    → 1분 뒤 다시 (2번까지)
 *   당근을 벗어나 멈춤 (사람이 홈을 누름)                      → 다시 안 함
 *   결과 불명 · 멈춤 글자 (보안 인증·이용 제한)                → 시험을 끔 (모드 '끔') — 사람이 보고 다시 켬
 *   앱이 당근을 다루는 중 (가져오기·드라이런·다른 차례)         → 20초마다 다시 (9번까지)
 *   연습은 지원자마다 저절로는 한 번만 ('지금 한 번 확인'은 언제나)
 *
 * 기다리는 시간은 시험용. 실제 운영(모든 새 지원자)은 아직 없음 — 켤 때는 보내는 때를 SendTiming(받은 뒤 2~10분 · 09~21시)으로.
 * 무엇을 보낼지·보내도 되는지는 LiveTurn·LiveGate·Conversation(순수 코드, 폰 없이 시험), 화면을 다루는 손은 접근성 서비스
 */
object LiveReply {
    private const val TAG = "DaangnLive"
    private const val APPLIED_MIN_MS = 20_000L
    private const val APPLIED_MAX_MS = 40_000L
    private const val NOTICE_MIN_MS = 15_000L
    private const val NOTICE_MAX_MS = 30_000L
    private const val RECHECK_MS = 120_000L
    private const val MAX_RECHECKS = 5
    private const val STOP_RETRY_MS = 60_000L
    private const val MAX_STOP_RETRIES = 2
    private const val BUSY_RETRY_MS = 20_000L
    private const val MAX_BUSY_RETRIES = 9
    private const val STUCK_MS = 5 * 60_000L // 한 차례가 이보다 길면 끝난 것으로 봄 (서비스가 중간에 꺼졌을 때)

    // 모든 일은 화면(메인) 차례에서 — 잡아 둔 차례 표를 한 줄로만 다루게
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _status = MutableStateFlow("")

    /** 지금 하는 일 한 줄 (지원자 탭 '실제 발송 시험' 칸에 보임) */
    val status: StateFlow<String> = _status

    private var running = false
    private var runningSince = 0L
    private val pending = HashMap<Long, Job>()   // 지원자 → 잡아 둔 차례
    private val dueAt = HashMap<Long, Long>()    // 지원자 → 그 차례가 돌 때
    private val rechecks = HashMap<Long, Int>()  // 지원자 → 보낸 뒤 '다시 보기' 횟수
    private val stopRetries = HashMap<Long, Int>()

    /** 지금 하는 일 한 줄 바꾸기 */
    fun say(line: String) {
        _status.value = line
    }

    /** 지원 알림으로 지원자를 새로 남긴 뒤 (리스너가 부름) */
    fun onApplied(context: Context, applicantId: Long) {
        val app = context.applicationContext
        scope.launch {
            val a = DbOpen.db(app)?.applicantDao()?.get(applicantId) ?: return@launch
            if (!LiveGate.isTarget(LiveSettings.config(app), a)) return@launch
            Log.i(TAG, "시험 대상 지원 알림")
            schedule(app, a.id, Random.nextLong(APPLIED_MIN_MS, APPLIED_MAX_MS), "지원 알림")
        }
    }

    /** 당근 알림이 옴 (리스너가 부름): 답을 기다리는 시험 대상이 있으면 그 방을 다시 봄 */
    fun onNotice(context: Context) {
        val app = context.applicationContext
        scope.launch {
            for (a in active(app).filter { it.status != ApplicantStatus.NEW }) {
                schedule(app, a.id, Random.nextLong(NOTICE_MIN_MS, NOTICE_MAX_MS), "당근 알림")
            }
        }
    }

    /** '지금 한 번 확인' 버튼: 시험 대상마다 바로 한 차례 (연습도 다시 함). 어떻게 됐는지는 status로 */
    fun checkNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val cfg = LiveSettings.config(app)
            LiveGate.configProblem(cfg)?.let {
                say("안 돎 — $it")
                return@launch
            }
            val list = active(app)
            if (list.isEmpty()) {
                say("시험 대상 지원자가 없어요 — 테스트 폰('${cfg.testName}')으로 지원하면 알림으로 들어와요 (켠 뒤 지원만)")
                return@launch
            }
            for (a in list) {
                rechecks[a.id] = 0
                stopRetries[a.id] = 0
                schedule(app, a.id, 0L, "지금 한 번 확인", manual = true)
            }
        }
    }

    /**
     * 시험 모드·대상이 바뀐 뒤: 앱이 이제 응대하지 않는 지원자의 처리 전 메시지를 '넘김'으로 (앱 처리 기록 — ApplicantStore.settleAll).
     * 시험을 끄면 아무도 안 맡으므로 남은 처리 전은 모두 넘김
     */
    fun settleLater(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { DbOpen.db(app)?.let { ApplicantStore.settleAll(it) } }
                .onSuccess { n -> if (n != null && n > 0) Log.i(TAG, "처리 전 메시지 ${n}개 → 넘김") }
                .onFailure { Log.e(TAG, "앱 처리 기록 맞추기 오류", it) }
        }
    }

    /** '끔'으로 바꿀 때: 잡아 둔 차례를 모두 취소 (돌고 있는 차례는 끝까지 — 멈추려면 홈 버튼) */
    fun cancelAll() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        dueAt.clear()
        rechecks.clear()
        stopRetries.clear()
        say("시험 꺼짐 — 잡아 둔 확인을 모두 취소")
    }

    /**
     * 실제 발송 시험이 맡은 지원자인지 (리스너가 번호 받기(ContactFlow)를 건너뛸 때).
     * 이 사람의 번호·상태는 LiveTurn이 채팅방을 보고 정함 — 번호가 틀리면 다시 요청해야 해서 (10/18 재걸)
     */
    suspend fun owns(context: Context, applicantId: Long): Boolean {
        val a = DbOpen.db(context)?.applicantDao()?.get(applicantId) ?: return false
        return LiveGate.isTarget(LiveSettings.config(context), a)
    }

    // ---------- 차례 잡기·돌리기

    /** 차례를 잡음. 이미 더 이른 차례가 잡혀 있으면 그대로 (알림이 잇달아 와도 미뤄지지 않게) */
    private fun schedule(ctx: Context, id: Long, delayMs: Long, why: String, manual: Boolean = false) {
        val due = System.currentTimeMillis() + delayMs
        val cur = pending[id]
        if (!manual && cur != null && cur.isActive && (dueAt[id] ?: Long.MAX_VALUE) <= due) return
        cur?.cancel()
        dueAt[id] = due
        if (delayMs > 0) say("$why — ${delayMs / 1000}초 뒤 당근에서 확인")
        pending[id] = scope.launch {
            if (delayMs > 0) delay(delayMs)
            pending.remove(id)
            dueAt.remove(id)
            runTurn(ctx, id, why, manual, busyTries = 0)
        }
    }

    private suspend fun runTurn(ctx: Context, id: Long, why: String, manual: Boolean, busyTries: Int) {
        val cfg = LiveSettings.config(ctx)
        LiveGate.configProblem(cfg)?.let {
            if (manual) say("안 돎 — $it")
            return
        }
        val db = DbOpen.db(ctx) ?: return
        val a = db.applicantDao().get(id) ?: return
        if (closed(db, a)) {
            if (manual) say("'${a.name}' — 마무리까지 보냄 (더 할 일 없음)")
            return
        }
        if (!manual && cfg.mode == LiveGate.Mode.REHEARSAL && id in LiveSettings.rehearsed(ctx)) {
            say("연습은 지원자마다 저절로는 한 번만 해요 — 다시 하려면 '지금 한 번 확인', 보내려면 '실제 발송'")
            return
        }
        val host = DaangnAccessibilityService.instance
        if (host == null) {
            finish(ctx, a, cfg, "안 돎 — 접근성(당근 화면 읽기)이 꺼져 있어요 (폰 설정 → 접근성)", emptyList(), null)
            return
        }
        if (running && SystemClock.elapsedRealtime() - runningSince > STUCK_MS) running = false
        if (running || host.driving) {
            if (busyTries >= MAX_BUSY_RETRIES) {
                finish(ctx, a, cfg, "안 돎 — 앱이 당근을 다루는 중이라 못 돎 ('지금 한 번 확인'으로 다시)", emptyList(), null)
                return
            }
            say("앱이 당근을 다루는 중 — ${BUSY_RETRY_MS / 1000}초 뒤 다시")
            dueAt[id] = System.currentTimeMillis() + BUSY_RETRY_MS
            pending[id] = scope.launch {
                delay(BUSY_RETRY_MS)
                pending.remove(id)
                dueAt.remove(id)
                runTurn(ctx, id, why, manual, busyTries + 1)
            }
            return
        }
        running = true
        runningSince = SystemClock.elapsedRealtime()
        say("$why — 당근을 열어 '${a.name}' 방을 봐요 (${cfg.mode.label})")
        host.runLiveTurn(id, cfg) { line, log, outcome ->
            running = false
            finish(ctx, a, cfg, line, log, outcome)
        }
    }

    /** 차례가 끝남: 결과를 남기고 다음 차례를 정함 */
    private fun finish(ctx: Context, a: Applicant, cfg: LiveGate.Config, line: String, log: List<String>, outcome: LiveTurn.Outcome?) {
        LiveSettings.saveResult(ctx, "${a.name} (${cfg.mode.label}): $line", log)
        Log.i(TAG, "${cfg.mode.label}: $line")
        say(line)
        val id = a.id
        when (outcome) {
            LiveTurn.Outcome.SENT -> {
                rechecks[id] = 0
                stopRetries[id] = 0
                recheckLater(ctx, id)
            }
            LiveTurn.Outcome.WAITING -> recheckLater(ctx, id)
            LiveTurn.Outcome.STOPPED -> stopRetry(ctx, id)
            LiveTurn.Outcome.REHEARSED -> LiveSettings.addRehearsed(ctx, id)
            LiveTurn.Outcome.UNKNOWN, LiveTurn.Outcome.ALARM -> {
                LiveSettings.setMode(ctx, LiveGate.Mode.OFF)
                cancelAll()
                settleLater(ctx)
                say(
                    if (outcome == LiveTurn.Outcome.UNKNOWN) "결과 불명 — 시험을 껐어요. 운영폰·테스트 폰 채팅방을 본 뒤 다시 켜요"
                    else "멈춤 글자가 보여 시험을 껐어요 — 당근 화면을 사람이 확인해 주세요"
                )
            }
            else -> {}
        }
    }

    /** 보낸 뒤 · 기다림 뒤: 2분 뒤 한 번 더 봄 (보낸 뒤 MAX_RECHECKS번까지) */
    private fun recheckLater(ctx: Context, id: Long) {
        val n = rechecks[id] ?: 0
        if (n >= MAX_RECHECKS) {
            say("답 확인을 ${MAX_RECHECKS}번 함 — 이제 당근 알림이 오면 다시 봐요 (또는 '지금 한 번 확인')")
            return
        }
        rechecks[id] = n + 1
        schedule(ctx, id, RECHECK_MS, "답 확인 ${n + 1}/$MAX_RECHECKS")
    }

    /** 화면이 예상과 달라 멈춤: 1분 뒤 다시 (MAX_STOP_RETRIES번까지) */
    private fun stopRetry(ctx: Context, id: Long) {
        val n = stopRetries[id] ?: 0
        if (n >= MAX_STOP_RETRIES) return
        stopRetries[id] = n + 1
        schedule(ctx, id, STOP_RETRY_MS, "멈춘 뒤 다시 ${n + 1}/$MAX_STOP_RETRIES")
    }

    /** 지금 시험 대상 (마무리까지 안 끝난 사람) */
    private suspend fun active(ctx: Context): List<Applicant> {
        val cfg = LiveSettings.config(ctx)
        if (LiveGate.configProblem(cfg) != null) return emptyList()
        val db = DbOpen.db(ctx) ?: return emptyList()
        return db.applicantDao().nonSample().filter { a -> LiveGate.isTarget(cfg, a) && a.status in LiveGate.HANDLED && !closed(db, a) }
    }

    /** 앱이 마무리까지 보낸 지원자인지 (그 뒤엔 앱이 보낼 것 없음 — Conversation) */
    private suspend fun closed(db: AppDatabase, a: Applicant): Boolean = db.messageDao().listFor(a.id).any {
        it.sendMethod == ReplyRules.SENT_BY_APP && Conversation.kindOf(it.messageKey) == Conversation.Kind.CLOSE
    }
}
