package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.Message
import com.kangsam.daangnrecruit.ReplyTask
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 응대 할 일 — 실제 발송 시험이 '그 사람 당근 방을 볼 차례'를 폰 DB(reply_tasks, 한 사람에 한 줄)에 두고 꺼내 쓰는 규칙
 * (순수 코드 — 폰 없이 시험). 언제 꺼내 돌릴지(화면·접근성·다른 일 중)는 LiveReply(폰).
 *
 * 10/20 재걸:
 *  - 앱이 시작한 대화는 '담당자 확인'으로 넘기지 않는 한 번호 받기(연락처 확보)까지 끝냄 — 중간에 조용히 멈춘 채 남지 않게
 *  - 보낸 뒤엔 주기적으로 확인하지 않음. 그 지원자의 답 알림이 왔을 때만 방을 보고 다음 단계로
 *
 *   할 일이 생김: 시험 대상의 지원 알림 → '첫 메시지' · 시험 대상이 보낸 채팅 알림 → '답이 옴' · '지금 한 번 확인'
 *                 · 접근성 서비스가 켜질 때 첫 메시지를 아직 못 보낸 새 지원 (지원 알림을 놓쳤거나 앱이 꺼져 있던 사이)
 *   차례가 끝나면 (after):
 *     보냄 · 기다림 · 끝 · 사람에게 넘김 · 연습 · 앱이 맡을 사람이 아님 → 할 일 지움 (다음은 그 사람의 답 알림)
 *       단, 답이 와서 봤는데 방에 답이 아직 안 보이면(기다림) 1분 뒤 한 번만 더
 *     막힘 (화면이 예상과 다름 · 다른 방이 열림 · 당근을 벗어남 · 오류) → 1·3·10·30분 뒤 다시 → 그래도 안 되면 담당자 확인
 *     오늘 한도 → 다음 날 아침 9시 · 설정 때문에 안 돎(전체 멈춤 등)·멈춤 글자 → 할 일 그대로 (켜지면 이어서)
 *     앱이 더 못 함 (발송 전 검사 · 한 사람 한도 · 결과 불명) → 담당자 확인
 *   차례가 도는 사이 그 사람의 답이 또 오면(보냄·기다림으로 끝났을 때) 지우지 않고 한 번 더 본다 (touchedAt)
 *   담당자 확인은 실제 발송일 때만 상태를 바꿈 (연습은 상태 그대로 — LiveTurn과 같음). 알림은 LiveReply가 보냄
 */
object ReplyTasks {
    const val WHY_FIRST = "첫 메시지"
    const val WHY_REPLY = "답이 옴"
    const val WHY_RETRY = "막혀서 다시"
    const val WHY_MANUAL = "지금 한 번 확인"
    const val WHY_TOMORROW = "오늘 한도 — 내일 아침"

    /** 막혔을 때 다시 해 볼 간격 (분). 다 쓰고도 막히면 담당자 확인 */
    val RETRY_MINUTES = listOf(1, 3, 10, 30)
    const val REPLY_AGAIN_MS = 60_000L // 답이 와서 봤는데 방에 아직 안 보이면 한 번 더 볼 때
    const val MORNING_HOUR = 9         // 오늘 한도면 이 시에 다시

    private const val DAANGN = "당근"
    private const val OURS = "우리"

    /** 차례가 끝난 뒤 할 일을 어떻게 했는지 */
    sealed class After {
        /** 할 일 지움 */
        data object Cleared : After()

        /** 막혀서 다시 (tries = 다시 한 횟수) */
        data class Again(val dueAt: Long, val tries: Int) : After()

        /** 그때 다시 (막힌 횟수는 안 셈 — 오늘 한도) */
        data class Later(val dueAt: Long) : After()

        /** 할 일 그대로 (설정이 켜지면·멈춤이 풀리면 이어서) */
        data object Kept : After()

        /** 담당자 확인으로 넘기고 할 일 지움. changed = 지원자 상태를 바꿨는지 (연습·사람이 이미 바꾼 줄은 그대로) */
        data class Flagged(val reason: String, val changed: Boolean) : After()

        /** 도는 사이 그 사람의 답이 또 옴 → 지우지 않고 한 번 더 */
        data object Retouched : After()
    }

    /** 끝난 차례로 정한 할 일 (DB에 쓰기 전 — 순수) */
    fun decide(t: ReplyTask, outcome: LiveTurn.Outcome, line: String, now: Long, zone: ZoneId): After = when (outcome) {
        LiveTurn.Outcome.SENT, LiveTurn.Outcome.DONE, LiveTurn.Outcome.HANDED_OVER,
        LiveTurn.Outcome.REHEARSED, LiveTurn.Outcome.NOT_MINE -> After.Cleared
        LiveTurn.Outcome.WAITING ->
            if (t.why == WHY_REPLY && t.tries == 0) After.Again(now + REPLY_AGAIN_MS, 1) else After.Cleared
        LiveTurn.Outcome.PAUSED, LiveTurn.Outcome.ALARM -> After.Kept
        LiveTurn.Outcome.TOMORROW -> After.Later(nextMorning(now, zone))
        LiveTurn.Outcome.CANT, LiveTurn.Outcome.UNKNOWN -> After.Flagged("앱이 이어서 못 함 — $line", changed = false)
        LiveTurn.Outcome.STOPPED, LiveTurn.Outcome.LEFT ->
            if (t.tries < RETRY_MINUTES.size) After.Again(now + RETRY_MINUTES[t.tries] * 60_000L, t.tries + 1)
            else After.Flagged("앱이 ${t.tries + 1}번 해 봐도 채팅방에서 못 함 — 마지막: $line", changed = false)
    }

    /**
     * 할 일을 잡음 (한 사람에 한 줄). 이미 있으면 더 이른 때를 둠 — manual('지금 한 번 확인')이면 바로 · 막힌 횟수를 지움.
     * touchedAt은 늘 지금 (차례가 도는 사이 생긴 할 일을 지우지 않으려고)
     */
    suspend fun add(db: AppDatabase, applicantId: Long, why: String, dueAt: Long, now: Long, manual: Boolean = false): ReplyTask {
        val dao = db.replyTaskDao()
        val cur = dao.get(applicantId)
        val t = when {
            cur == null -> ReplyTask(applicantId = applicantId, dueAt = dueAt, why = why, touchedAt = now)
            manual -> cur.copy(dueAt = dueAt, why = why, tries = 0, problem = "", touchedAt = now)
            dueAt < cur.dueAt -> cur.copy(dueAt = dueAt, why = why, touchedAt = now)
            else -> cur.copy(touchedAt = now)
        }
        dao.put(t)
        return t
    }

    /**
     * 차례(ran — 돌기 시작할 때의 할 일)가 끝난 뒤: 정한 대로 DB에 씀. startedAt = 차례를 시작한 때,
     * live = 실제 발송인지 (연습이면 담당자 확인으로 상태를 바꾸지 않음)
     */
    suspend fun after(
        db: AppDatabase, ran: ReplyTask, outcome: LiveTurn.Outcome, line: String,
        startedAt: Long, now: Long, zone: ZoneId, live: Boolean,
    ): After {
        val dao = db.replyTaskDao()
        val cur = dao.get(ran.applicantId)
        // 보냄·기다림으로 끝났는데 도는 사이 그 사람의 답이 또 옴 → 지우지 않고 한 번 더 (그 답에 답하려고)
        if (cur != null && cur.touchedAt > startedAt && (outcome == LiveTurn.Outcome.SENT || outcome == LiveTurn.Outcome.WAITING)) {
            dao.put(cur.copy(tries = 0, problem = ""))
            return After.Retouched
        }
        val base = cur ?: ran
        return when (val d = decide(base, outcome, line, now, zone)) {
            After.Cleared -> {
                dao.remove(ran.applicantId)
                d
            }
            is After.Again -> {
                val why = if (outcome == LiveTurn.Outcome.WAITING) WHY_REPLY else WHY_RETRY
                dao.put(base.copy(dueAt = d.dueAt, why = why, tries = d.tries, problem = line))
                d
            }
            is After.Later -> {
                dao.put(base.copy(dueAt = d.dueAt, why = WHY_TOMORROW, problem = line))
                d
            }
            is After.Flagged -> {
                dao.remove(ran.applicantId)
                d.copy(changed = live && flag(db, ran.applicantId, d.reason))
            }
            After.Kept, After.Retouched -> d
        }
    }

    /**
     * 담당자 확인으로 (확인 탭 카드 — '멈추기 전 상태'를 남겨 '다시 맡기기'로 되돌림). 앱이 다루는 상태(새 지원·첫 메시지 보냄·연락처 확보)일
     * 때만 — 사람이 이미 바꾼 줄(담당자 응대 중 등)은 그대로. 바꿨으면 true
     */
    suspend fun flag(db: AppDatabase, applicantId: Long, reason: String): Boolean {
        val a = db.applicantDao().get(applicantId) ?: return false
        if (a.status !in LiveGate.HANDLED) return false
        ApplicantStore.update(db, a.copy(status = ApplicantStatus.STAFF_CHECK, statusBeforeHold = a.status, reason = reason))
        return true
    }

    /**
     * 시험을 끌 때 (before = 끄기 전 설정): 할 일을 모두 지우고, 실제 발송 중 앱이 시작했는데 번호를 아직 못 받은 대화는
     * 담당자 확인으로 (끝은 연락처 확보 또는 담당자 확인 — 10/20 재걸). 앱이 아직 아무것도 안 보낸 새 지원은 그대로 (사람이 봄).
     * 담당자 확인으로 바꾼 지원자를 돌려줌
     */
    suspend fun stopAll(db: AppDatabase, before: LiveGate.Config, reason: String): List<Applicant> {
        val flagged = mutableListOf<Applicant>()
        if (before.mode == LiveGate.Mode.LIVE) {
            val cfg = before.copy(mode = LiveGate.Mode.LIVE)
            for (a in db.applicantDao().nonSample()) {
                if (!LiveGate.isTarget(cfg, a) || a.status !in STARTED_STATES) continue
                val messages = db.messageDao().listFor(a.id)
                if (!appStarted(messages) || closed(messages)) continue
                if (flag(db, a.id, reason)) flagged += a
            }
        }
        db.replyTaskDao().clear()
        return flagged
    }

    /**
     * 접근성 서비스가 켜질 때: 첫 메시지를 아직 못 보낸 시험 대상(새 지원 · 앱이 보낸 당근 메시지 없음)에 할 일이 없으면 잡음
     * (지원 알림을 놓쳤거나 앱·폰이 꺼져 있던 사이). 보낸 뒤 답을 기다리는 사람은 잡지 않음 — 답 알림이 올 때만. 잡은 수
     */
    suspend fun addMissedFirst(db: AppDatabase, cfg: LiveGate.Config, dueAt: Long, now: Long): Int {
        if (LiveGate.configProblem(cfg) != null) return 0
        var n = 0
        for (a in db.applicantDao().nonSample()) {
            if (!LiveGate.isTarget(cfg, a) || a.status != ApplicantStatus.NEW || a.contactingMark.isNotBlank()) continue
            if (db.replyTaskDao().get(a.id) != null || appStarted(db.messageDao().listFor(a.id))) continue
            add(db, a.id, WHY_FIRST, dueAt, now)
            n++
        }
        return n
    }

    /** 앱이 맡아 대화 중인 시험 대상 (마무리 전) */
    suspend fun active(db: AppDatabase, cfg: LiveGate.Config): List<Applicant> {
        if (LiveGate.configProblem(cfg) != null) return emptyList()
        return db.applicantDao().nonSample().filter { a ->
            LiveGate.isTarget(cfg, a) && a.status in LiveGate.HANDLED && !closed(db.messageDao().listFor(a.id))
        }
    }

    /** 채팅 알림을 보낸 사람 이름(당근 닉네임) 중 앱이 맡아 대화 중인 시험 대상 — 그 사람의 답이 온 것 */
    suspend fun targetsNamed(db: AppDatabase, cfg: LiveGate.Config, senders: Collection<String>): List<Applicant> {
        val names = senders.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (names.isEmpty()) return emptyList()
        return active(db, cfg).filter { it.name.trim() in names }
    }

    /** 앱이 마무리까지 보냈는지 (그 뒤엔 앱이 보낼 것 없음 — Conversation) */
    fun closed(messages: List<Message>): Boolean = messages.any {
        it.sendMethod == ReplyRules.SENT_BY_APP && Conversation.kindOf(it.messageKey) == Conversation.Kind.CLOSE
    }

    /** 앱이 이 지원자에게 당근 메시지를 보냈는지 (보냄 · 결과 불명) — 앱이 대화를 시작했는지 */
    fun appStarted(messages: List<Message>): Boolean = messages.any {
        it.channel == DAANGN && it.sender == OURS && Conversation.kindOf(it.messageKey) != null &&
            (it.sendMethod == ReplyRules.SENT_BY_APP || it.sendMethod == LiveTurn.RESULT_UNKNOWN)
    }

    /** 오늘 한도일 때 다시 할 때: 아침 9시 전이면 오늘 9시, 아니면 내일 9시 */
    fun nextMorning(now: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val today = t.toLocalDate().atTime(LocalTime.of(MORNING_HOUR, 0)).atZone(zone)
        return (if (t.isBefore(today)) today else today.plusDays(1)).toInstant().toEpochMilli()
    }

    /** 시험을 끌 때 담당자 확인으로 넘길 수 있는 상태 (앱이 시작했고 번호를 아직 못 받음) */
    private val STARTED_STATES = setOf(ApplicantStatus.NEW, ApplicantStatus.FIRST_SENT)
}
