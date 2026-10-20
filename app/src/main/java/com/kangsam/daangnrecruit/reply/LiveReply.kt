package com.kangsam.daangnrecruit.reply

import android.content.Context
import android.util.Log
import com.kangsam.daangnrecruit.AppAlerts
import com.kangsam.daangnrecruit.AppDatabase
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * 실제 발송 시험의 폰 쪽: 응대 할 일(폰 DB reply_tasks — 규칙은 ReplyTasks)을 잡고, 돌리기(TaskRunner)를 깨움
 * (사용자 프로세스 B2·B3 — 테스트 폰 지원자에게만).
 *
 * 10/20 재걸: 보낸 뒤엔 주기적으로 확인하지 않음 — 그 지원자의 답 알림이 왔을 때만 방을 보고 다음 단계로.
 * 앱이 시작한 대화는 담당자 확인으로 넘기지 않는 한 번호 받기까지 끝냄 (막히면 다시 해 보고, 안 되면 담당자 확인 + 폰 알림).
 * 할 일은 폰 DB에 있어서 앱·폰이 다시 켜져도 이어서 함 (접근성 서비스가 켜질 때 onServiceReady).
 *
 *   시험 대상의 지원 알림 (리스너 → onApplied)              → 할 일 '첫 메시지' (20~40초 뒤)
 *   시험 대상이 보낸 채팅 알림 (리스너 → onChat)             → 할 일 '답이 옴' (15~30초 뒤) — 다른 사람·당근 공식 알림은 안 봄
 *   '지금 한 번 확인' · 실제 발송으로 바꿈 (checkNow)         → 시험 대상마다 바로
 *   접근성 서비스가 켜짐 (폰·앱 재시작 · 접근성을 다시 켬)     → 첫 메시지를 못 보낸 새 지원을 잡고 저장된 할 일을 이어서
 *   화면이 켜지고 잠금이 풀림 (접근성 서비스 → onScreenOn)    → 기다리던 할 일을 이어서
 *   '당근 응대'를 켬 · 전체 멈춤을 풂 (resume)               → 기다리던 할 일을 이어서
 *   시험을 끔 (turnedOff — 시험 칸 · 결과 불명 · 멈춤 글자)    → 할 일을 모두 지움 · 번호 전 대화는 담당자 확인 + 알림
 * 할 일 차례에 화면이 꺼짐·접근성이 꺼짐·다른 일 중이면 어떻게 하는지는 TaskRunner (순수 코드 — 폰 없이 시험).
 * 기다리는 시간은 시험용 — 실제 운영(모든 새 지원자)은 아직 없음 (켤 때는 보내는 때를 SendTiming으로)
 */
object LiveReply {
    private const val TAG = "DaangnLive"
    private const val APPLIED_MIN_MS = 20_000L
    private const val APPLIED_MAX_MS = 40_000L
    private const val CHAT_MIN_MS = 15_000L
    private const val CHAT_MAX_MS = 30_000L
    private const val STUCK_MS = 5 * 60_000L // 한 차례가 이보다 길면 멈춘 것으로 봄 (접근성 서비스가 중간에 꺼졌을 때)

    // 모든 일은 화면(메인) 차례에서 — 한 번에 한 차례만 돌게
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _status = MutableStateFlow("")

    /** 지금 하는 일 한 줄 (지원자 탭 '실제 발송 시험' 칸에 보임) */
    val status: StateFlow<String> = _status

    private var runner: TaskRunner? = null
    private var timer: Job? = null // 다음 할 일 때에 깨움 (메모리 — 앱이 다시 켜지면 onServiceReady가 다시 깨움)

    /** 지금 하는 일 한 줄 바꾸기 */
    fun say(line: String) {
        _status.value = line
    }

    /** 지원 알림으로 지원자를 새로 남긴 뒤 (리스너가 부름): 시험 대상이면 첫 메시지 할 일 */
    fun onApplied(context: Context, applicantId: Long) {
        val app = context.applicationContext
        scope.launch {
            val db = DbOpen.db(app) ?: return@launch
            val a = db.applicantDao().get(applicantId) ?: return@launch
            if (!LiveGate.isTarget(LiveSettings.config(app), a)) return@launch
            Log.i(TAG, "시험 대상 지원 알림")
            val wait = Random.nextLong(APPLIED_MIN_MS, APPLIED_MAX_MS)
            ReplyTasks.add(db, a.id, ReplyTasks.WHY_FIRST, now() + wait, now())
            say("지원 알림 — ${wait / 1000}초 뒤 당근에서 첫 메시지")
            kick(app)
        }
    }

    /**
     * 당근 채팅 알림 (리스너가 부름 — senders = 알림의 보낸 사람 이름, 당근 공식·당근알바는 뺀 것):
     * 앱이 맡아 대화 중인 시험 대상이 보낸 것이면 그 사람 방을 볼 할 일 (그 사람의 답이 왔을 때만 — 10/20 재걸)
     */
    fun onChat(context: Context, senders: List<String>) {
        if (senders.isEmpty()) return
        val app = context.applicationContext
        scope.launch {
            val cfg = LiveSettings.config(app)
            if (LiveGate.configProblem(cfg) != null) return@launch
            val db = DbOpen.db(app) ?: return@launch
            val people = ReplyTasks.targetsNamed(db, cfg, senders)
            if (people.isEmpty()) return@launch
            for (a in people) ReplyTasks.add(db, a.id, ReplyTasks.WHY_REPLY, now() + Random.nextLong(CHAT_MIN_MS, CHAT_MAX_MS), now())
            say("답 알림 (${people.joinToString(", ") { it.name }}) — 잠시 뒤 그 방을 봐요")
            kick(app)
        }
    }

    /** '지금 한 번 확인' 버튼 · 실제 발송으로 바꿀 때: 시험 대상마다 바로 한 차례 (연습도 다시 함). 어떻게 됐는지는 status로 */
    fun checkNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val cfg = LiveSettings.config(app)
            LiveGate.configProblem(cfg)?.let {
                say("안 돎 — $it")
                return@launch
            }
            val db = DbOpen.db(app) ?: return@launch
            val list = ReplyTasks.active(db, cfg)
            if (list.isEmpty()) {
                say("시험 대상 지원자가 없어요 — 테스트 폰('${cfg.testName}')으로 지원하면 알림으로 들어와요 (켠 뒤 지원만)")
                return@launch
            }
            for (a in list) ReplyTasks.add(db, a.id, ReplyTasks.WHY_MANUAL, now(), now(), manual = true)
            kick(app)
        }
    }

    /**
     * 접근성 서비스가 켜짐 (폰·앱이 다시 켜짐 · 접근성을 다시 켬): 첫 메시지를 못 보낸 새 지원을 할 일로 잡고, 저장된 할 일을 이어서.
     * 보낸 뒤 답을 기다리는 사람은 잡지 않음 (답 알림이 올 때만)
     */
    fun onServiceReady(context: Context) {
        val app = context.applicationContext
        scope.launch {
            DbOpen.db(app)?.let { db ->
                runCatching { ReplyTasks.addMissedFirst(db, LiveSettings.config(app), now() + APPLIED_MIN_MS, now()) }
                    .onSuccess { n -> if (n > 0) say("첫 메시지를 아직 못 보낸 시험 대상 ${n}명 — 할 일로 잡음") }
                    .onFailure { Log.e(TAG, "할 일 다시 잡기 오류", it) }
            }
            kick(app)
        }
    }

    /** 화면이 켜지고 잠금이 풀림 (접근성 서비스가 부름) → 기다리던 할 일을 이어서 */
    fun onScreenOn(context: Context) = resume(context)

    /** '당근 응대'를 켬 · 전체 멈춤을 풂 → 기다리던 할 일을 이어서 */
    fun resume(context: Context) = kick(context.applicationContext)

    /**
     * 시험을 끔 (before = 끄기 전 설정 — 시험 칸 '끔'): 할 일을 모두 지우고, 실제 발송 중 앱이 시작했는데 번호를 아직 못 받은 대화는
     * 담당자 확인 + 폰 알림 (끝은 연락처 확보 또는 담당자 확인 — 10/20 재걸). 처리 전 메시지는 넘김으로
     */
    fun turnedOff(context: Context, before: LiveGate.Config, why: String) {
        val app = context.applicationContext
        timer?.cancel()
        scope.launch { stop(app, before, why) }
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

    /**
     * 실제 발송 시험이 맡은 지원자인지 (리스너가 번호 받기(ContactFlow)를 건너뛸 때).
     * 이 사람의 번호·상태는 LiveTurn이 채팅방을 보고 정함 — 번호가 틀리면 다시 요청해야 해서 (10/18 재걸)
     */
    suspend fun owns(context: Context, applicantId: Long): Boolean {
        val a = DbOpen.db(context)?.applicantDao()?.get(applicantId) ?: return false
        return LiveGate.isTarget(LiveSettings.config(context), a)
    }

    // ---------- 돌리기 깨우기

    /** 때가 된 할 일을 돌림 (TaskRunner). 다음 할 일이 아직이면 그때 다시 깨움 */
    private fun kick(app: Context) {
        scope.launch {
            val wake = runCatching { runner(app).runDue() }
                .onFailure { Log.e(TAG, "할 일 돌리기 오류", it) }
                .getOrNull() ?: return@launch
            timer?.cancel()
            timer = scope.launch {
                delay((wake - now()).coerceAtLeast(0))
                timer = null
                kick(app)
            }
        }
    }

    private fun runner(app: Context): TaskRunner = runner ?: TaskRunner(PhoneEnv(app)).also { runner = it }

    /** 시험을 끌 때 정리 (시험 칸 · TaskRunner의 결과 불명·멈춤 글자) */
    private suspend fun stop(app: Context, before: LiveGate.Config, why: String) {
        val db: AppDatabase = DbOpen.db(app) ?: return
        val reason = "실제 발송 시험을 꺼서 앱이 멈춘 대화 ($why) — 담당자가 이어서 응대해 주세요"
        val flagged = runCatching { ReplyTasks.stopAll(db, before, reason) }
            .onFailure { Log.e(TAG, "시험 끄기 정리 오류", it) }
            .getOrDefault(emptyList())
        flagged.forEach { AppAlerts.staffCheck(app, it.id, it.name, reason) }
        AppAlerts.clearScreenNeeded(app)
        runCatching { ApplicantStore.settleAll(db) }
        say("시험 꺼짐 ($why) — 할 일을 모두 지움" + if (flagged.isNotEmpty()) " · 번호를 못 받은 대화 ${flagged.size}개는 담당자 확인으로" else "")
    }

    private fun now() = System.currentTimeMillis()

    /** TaskRunner가 쓰는 폰 쪽 (DB·설정·접근성 서비스·알림) */
    private class PhoneEnv(private val app: Context) : TaskRunner.Env {
        override suspend fun db(): AppDatabase? = DbOpen.db(app)
        override fun config(): LiveGate.Config = LiveSettings.config(app)
        override fun host(): TaskRunner.Host? = DaangnAccessibilityService.instance?.let { ServiceHost(it) }
        override fun now(): Long = System.currentTimeMillis()
        override val zone: ZoneId get() = ZoneId.systemDefault()
        override fun rehearsed(): Set<Long> = LiveSettings.rehearsed(app)
        override fun addRehearsed(applicantId: Long) = LiveSettings.addRehearsed(app, applicantId)
        override fun saveResult(line: String, log: List<String>) {
            LiveSettings.saveResult(app, line, log)
            Log.i(TAG, line)
        }
        override fun say(line: String) = LiveReply.say(line)
        override fun screenNeeded() = AppAlerts.screenNeeded(app)
        override fun clearScreenNeeded() = AppAlerts.clearScreenNeeded(app)
        override fun accessibilityNeeded() = AppAlerts.accessibilityNeeded(app)
        override fun staffCheck(applicantId: Long, name: String, reason: String) = AppAlerts.staffCheck(app, applicantId, name, reason)
        override suspend fun turnOff(before: LiveGate.Config, why: String) {
            LiveSettings.setMode(app, LiveGate.Mode.OFF)
            stop(app, before, why)
        }
    }

    /** 접근성 서비스 = 당근을 다루는 손. 한 차례가 STUCK_MS 안에 안 끝나면 멈춘 것으로 봄 (서비스가 꺼져 결과가 안 올 때) */
    private class ServiceHost(private val service: DaangnAccessibilityService) : TaskRunner.Host {
        override fun screenReady(): Boolean = service.screenReady()
        override val driving: Boolean get() = service.driving
        override suspend fun runTurn(applicantId: Long, config: LiveGate.Config): TaskRunner.Turn =
            withTimeoutOrNull(STUCK_MS) {
                suspendCancellableCoroutine { cont ->
                    service.runLiveTurn(applicantId, config) { line, log, outcome ->
                        if (cont.isActive) cont.resume(TaskRunner.Turn(line, log, outcome))
                    }
                }
            } ?: TaskRunner.Turn("차례가 ${STUCK_MS / 60_000}분 안에 안 끝남 (접근성 서비스가 꺼졌을 수 있음)", emptyList(), LiveTurn.Outcome.STOPPED)
    }
}
