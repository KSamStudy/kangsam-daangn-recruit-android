package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ReplyTask
import java.time.ZoneId

/**
 * 응대 할 일 돌리기 — 때가 된 할 일(ReplyTasks)을 한 번에 하나씩 차례(LiveTurn)로 돌리고, 끝나면 할 일을 정함 (순수 코드 — 폰 없이 시험).
 * 폰에 닿는 것(DB 열기·설정·접근성 서비스·알림·시계)은 Env로 받음 — 폰에선 LiveReply가 넣어 줌.
 *
 * runDue 한 번:
 *   할 일이 없음 → 끝 (null) · 가장 이른 할 일이 아직 → 그때 깨워 달라고 돌려줌
 *   때가 됨 → 지워진 지원자·연습을 마친 지원자면 지우고 다음
 *          → 접근성이 꺼짐 / 화면이 꺼짐·잠김 → 할 일 그대로 + 알림, 끝 (켜지면 Env 쪽에서 다시 부름)
 *          → 앱이 당근을 다루는 중(가져오기·드라이런) → 30초 뒤 다시
 *          → 차례 → 끝나면 ReplyTasks.after (도는 사이 화면이 꺼졌으면 막힌 횟수로 안 셈) → 다음 할 일
 *   설정 때문에 안 돎(시험 꺼짐·전체 멈춤·당근 응대 꺼짐) → 할 일 그대로, 끝
 * 차례가 도는 동안 다시 부르면 바로 끝 (한 번에 한 차례 — 도는 쪽이 끝나고 다음 할 일까지 봄)
 */
class TaskRunner(private val env: Env) {
    /** 폰에 닿는 것 */
    interface Env {
        suspend fun db(): AppDatabase?
        fun config(): LiveGate.Config
        fun host(): Host?             // 접근성 서비스 (꺼져 있으면 null)
        fun now(): Long
        val zone: ZoneId
        fun rehearsed(): Set<Long>     // 연습을 마친 지원자
        fun addRehearsed(applicantId: Long)
        fun saveResult(line: String, log: List<String>)
        fun say(line: String)
        fun screenNeeded()             // 알림: 화면을 켜고 잠금을 풀어 주세요
        fun clearScreenNeeded()
        fun accessibilityNeeded()      // 알림: 접근성을 켜 주세요
        fun staffCheck(applicantId: Long, name: String, reason: String) // 알림: 담당자 확인
        suspend fun turnOff(before: LiveGate.Config, why: String) // 시험을 끔 (결과 불명·멈춤 글자 — 번호 전 대화는 담당자 확인)
    }

    /** 당근을 다루는 손 (접근성 서비스) */
    interface Host {
        fun screenReady(): Boolean    // 화면이 켜져 있고 잠금이 풀려 있음
        val driving: Boolean          // 앱이 당근을 다루는 중 (가져오기·드라이런·차례)
        suspend fun runTurn(applicantId: Long, config: LiveGate.Config): Turn
    }

    /** 끝난 차례 */
    data class Turn(val line: String, val log: List<String>, val outcome: LiveTurn.Outcome)

    private var running = false

    /** 차례가 도는 중인지 */
    val busy: Boolean get() = running

    /** 때가 된 할 일을 돌림. 다음에 깨울 때를 돌려줌 (없으면 null — 할 일이 없거나 화면·접근성·설정을 기다림) */
    suspend fun runDue(): Long? {
        if (running) return null
        val cfg = env.config()
        if (LiveGate.configProblem(cfg) != null) return null
        val db = env.db() ?: return null
        val dao = db.replyTaskDao()
        while (true) {
            val next = dao.all().firstOrNull() ?: run {
                env.clearScreenNeeded()
                return null
            }
            if (next.dueAt > env.now()) return next.dueAt
            val a = db.applicantDao().get(next.applicantId)
            if (a == null) {
                dao.remove(next.applicantId) // 지워진 지원자
                continue
            }
            if (cfg.mode == LiveGate.Mode.REHEARSAL && next.why != ReplyTasks.WHY_MANUAL && a.id in env.rehearsed()) {
                dao.remove(a.id)
                env.say("연습은 지원자마다 저절로는 한 번만 해요 — 다시 하려면 '지금 한 번 확인', 보내려면 '실제 발송'")
                continue
            }
            val host = env.host()
            if (host == null) {
                env.say("할 일(${next.why})이 있는데 접근성(당근 화면 읽기)이 꺼져 있어요 — 켜면 이어서 해요")
                env.accessibilityNeeded()
                return null
            }
            if (!host.screenReady()) {
                env.say("할 일(${next.why})이 있는데 화면이 꺼져 있거나 잠겨 있어요 — 켜고 잠금을 풀면 이어서 해요")
                env.screenNeeded()
                return null
            }
            env.clearScreenNeeded()
            if (running) return null // 기다리는 사이 다른 부름이 먼저 차례를 시작함 (그쪽이 끝나고 다음까지 봄)
            if (host.driving) {
                env.say("앱이 당근을 다루는 중 — ${BUSY_RETRY_MS / 1000}초 뒤 다시")
                return env.now() + BUSY_RETRY_MS
            }
            running = true
            val started = env.now()
            val turn = try {
                env.say("${next.why} — 당근을 열어 '${a.name}' 방을 봐요 (${cfg.mode.label})")
                host.runTurn(a.id, cfg)
            } finally {
                running = false
            }
            finish(db, next, a.name, cfg, turn, started, host)
            if (LiveGate.configProblem(env.config()) != null) return null // 결과 불명·멈춤 글자로 시험이 꺼짐 등
        }
    }

    /** 차례가 끝남: 결과를 남기고 할 일을 정함. 도는 사이 화면이 꺼졌으면 막힌 횟수로 세지 않고 켜진 뒤 이어서 */
    private suspend fun finish(db: AppDatabase, task: ReplyTask, name: String, cfg: LiveGate.Config, turn: Turn, started: Long, host: Host) {
        val (line, log, outcome) = turn
        env.saveResult("$name (${cfg.mode.label}): $line", log)
        val screenOff = (outcome == LiveTurn.Outcome.LEFT || outcome == LiveTurn.Outcome.STOPPED) && !host.screenReady()
        if (screenOff) {
            env.say("$line — 화면이 꺼져서 멈춤 · 켜고 잠금을 풀면 이어서 해요")
            return // 할 일 그대로 (막힌 횟수 안 셈) — 다음 runDue가 화면을 보고 알림
        }
        if (outcome == LiveTurn.Outcome.REHEARSED) env.addRehearsed(task.applicantId)
        val live = cfg.mode == LiveGate.Mode.LIVE
        val after = ReplyTasks.after(db, task, outcome, line, started, env.now(), env.zone, live)
        env.say(
            when (after) {
                is ReplyTasks.After.Again -> "$line — ${(after.dueAt - env.now() + 59_999) / 60_000}분 뒤 다시 (${after.tries}/${ReplyTasks.RETRY_MINUTES.size})"
                is ReplyTasks.After.Later -> "$line — 내일 아침에 다시"
                is ReplyTasks.After.Flagged -> if (after.changed) "담당자 확인으로 넘김 — ${after.reason}" else "$line (연습 · 상태는 그대로)"
                ReplyTasks.After.Retouched -> "$line — 도는 사이 답이 또 와서 한 번 더 봐요"
                ReplyTasks.After.Kept -> "$line — 할 일은 그대로 (켜지면 이어서)"
                ReplyTasks.After.Cleared -> line
            }
        )
        if (after is ReplyTasks.After.Flagged && after.changed) env.staffCheck(task.applicantId, name, after.reason)
        // 방을 보고 사람에게 넘김 (다시 요청 한도·받은 번호와 다름 → 담당자 확인)도 알림. 사람이 방에서 직접 말했으면(담당자 응대 중) 알림 없음
        if (outcome == LiveTurn.Outcome.HANDED_OVER && live) {
            db.applicantDao().get(task.applicantId)?.takeIf { it.status == ApplicantStatus.STAFF_CHECK }
                ?.let { env.staffCheck(it.id, it.name, it.reason) }
        }
        if (outcome == LiveTurn.Outcome.UNKNOWN || outcome == LiveTurn.Outcome.ALARM) {
            env.turnOff(cfg, if (outcome == LiveTurn.Outcome.UNKNOWN) "결과 불명" else "멈춤 글자")
        }
    }

    companion object {
        const val BUSY_RETRY_MS = 30_000L // 앱이 당근을 다루는 중이면 이만큼 뒤 다시
    }
}
