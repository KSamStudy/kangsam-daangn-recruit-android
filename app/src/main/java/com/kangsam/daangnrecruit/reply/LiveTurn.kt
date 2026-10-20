package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.Message
import com.kangsam.daangnrecruit.sync.AccountSync
import com.kangsam.daangnrecruit.sync.ApplicantImport
import com.kangsam.daangnrecruit.sync.ChatImport
import com.kangsam.daangnrecruit.sync.ChatRoomParser
import com.kangsam.daangnrecruit.sync.DaangnNav
import com.kangsam.daangnrecruit.sync.DaangnPage
import com.kangsam.daangnrecruit.sync.DaangnWalker
import com.kangsam.daangnrecruit.sync.ScreenDriver
import com.kangsam.daangnrecruit.sync.Snapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * 실제 발송 시험의 한 차례 — 테스트 폰 지원자 한 명에게만 (사용자 프로세스 B2·B3, 10/18 재걸: 전체 과정을 한 번에):
 *   ① 대상·안전 (LiveGate) → ② 당근에서 그 사람 채팅방 열기 → ③ 방 읽기 (ChatRoomParser)
 *   → ④ 다음에 할 일 (Conversation) → ⑤ 문구 (ReplyPlanner·Templates — 무작위) · 발송 전 검사 (ReplyRules)
 *   → ⑥ 입력창에 쓰고 쓰인 글자 대조 → (연습이면 전송 칸만 찾고 지우고 끝) 전송 → 방에 우리 말풍선으로 보여야 보낸 것
 *   → ⑦ 기록 (대화 표 · 지원자 상태 — 번호는 ContactFlow와 같은 규칙) → 방에서 나와 우리 앱으로
 * ③에서 방에 보이는 메시지를 대화 표에 먼저 남김 (ChatImport — 판단에 쓴 지원자 말·번호도 기록에 남게, 앱 처리 기록대로 처리 전).
 * 보내면 그 전 처리 전 메시지는 '답함', 사람에게 넘기면 ApplicantStore가 '넘김'으로 (DB 설계 '앱 처리 기록')
 *
 * 부품마다 따로: 판단은 LiveGate·Conversation, 칸 찾기는 LiveNav (모두 순수 코드), 화면 옮기기는 DaangnWalker (가져오기와 같은 손),
 * 언제 돌지는 LiveReply (폰). 이 클래스는 순서만 이음. 차례가 어떻게 끝났는지는 outcome (LiveReply가 다음 차례를 정함)
 *
 * 방 열기 (RoomRoute — 첫 메시지 드라이런과 같은 길): 처음(앱이 보낸 게 없음)은 나의 당근 → 공고 → 지원자 보기 → 그 사람 카드 →
 *   읽은 카드면 아래 '채팅하기', 안 읽은 카드면 지원서 → '채팅하기' (지원서에 없으면 목록으로 돌아와 카드의 '채팅하기').
 *   그다음부터는 채팅 목록 맨 위 화면의 그 사람 방 (안 보이면 지원자 목록 길로)
 * 결과 불명(전송을 눌렀는데 방에 안 보임) → 다시 보내지 않음 · 기록은 '결과 불명' · outcome UNKNOWN (LiveReply가 시험을 끔)
 */
class LiveTurn(
    private val driver: ScreenDriver,
    private val db: AppDatabase,
    private val config: LiveGate.Config,
    private val applicantId: Long,
    private val say: (String) -> Unit = {},
    private val random: Random = Random.Default,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** 차례가 어떻게 끝났는지 */
    enum class Outcome {
        SENT,        // 보냄 (방에 보임)
        WAITING,     // 답 기다림 (안 보냄)
        DONE,        // 마무리까지 보냄 — 더 할 일 없음
        HANDED_OVER, // 사람에게 넘김 (담당자 응대 중·담당자 확인)
        REHEARSED,   // 연습 — 입력창에 쓰고 대조 · 전송 칸 확인 · 지움 (안 보냄)
        BLOCKED,     // 안 보냄 — 대상·안전 규칙에 걸림 (당근을 안 엶) 또는 보내면 안 되는 방
        STOPPED,     // 멈춤 — 화면이 예상과 달라 멈춤 (다시 해 볼 수 있음)
        LEFT,        // 당근을 벗어나 멈춤 — 사람이 홈을 눌렀거나 다른 앱이 뜸 (멈추는 방법이라 다시 안 함)
        ALARM,       // 멈춤 글자(보안 인증·이용 제한 등)가 보임 — 즉시 중단 (LiveReply가 시험을 끔)
        UNKNOWN,     // 결과 불명 — 전송을 눌렀는데 방에 안 보임 (다시 안 보냄)
    }

    /** 한 일 (지원자 탭 '실제 발송 시험'의 기록) */
    val log = mutableListOf<String>()

    var outcome = Outcome.BLOCKED
        private set

    /** 멈춤 글자(보안 인증·이용 제한 등)로 멈췄으면 그 멈춤 — 접근성 서비스가 전체 멈춤 + 계정 상태 기록 (Safety) */
    var alarm: DaangnWalker.Stop? = null
        private set

    /** 결과 불명이 났는지 (그러면 시험을 꺼야 함) */
    val unknown: Boolean get() = outcome == Outcome.UNKNOWN

    private val w = DaangnWalker(driver, ::note, random)
    private val route = RoomRoute(w, db, ::note, ApplicantImport.APP_OPENED, "live")

    /** 한 차례 돌고 한 줄 결과를 돌려줌 */
    suspend fun run(): String {
        val a = db.applicantDao().get(applicantId) ?: return end(Outcome.BLOCKED, "지원자를 못 찾음")
        val messages = db.messageDao().listFor(a.id)
        val sent = sentByApp(messages)
        val unknownSends = messages.count { it.sender == OURS && it.sendMethod == RESULT_UNKNOWN }
        val today = db.messageDao().recentByMethod(ReplyRules.SENT_BY_APP, 200).count { it.channel == DAANGN && sameDay(it.time, now()) }
        // 하루 한도: 이 지원의 공고를 올린 계정(모르면 지금 계정)의 한도와 시험 한도 중 작은 것
        val account = a.standingPostId?.let { db.postDao().getStandingPost(it)?.accountId }?.let { db.accountDao().get(it) }
            ?: AccountSync.current(db)
        LiveGate.block(config, a, sent.size, today, unknownSends, LiveGate.dailyLimit(account))?.let { return end(Outcome.BLOCKED, "안 보냄 — $it") }
        note("'${a.name}' — ${config.mode.label} (앱이 보낸 당근 메시지 ${sent.size}개)")
        val result = try {
            visit(a, sent)
        } catch (e: DaangnWalker.Stop) {
            if (e.alarm) alarm = e
            note("멈춤: ${e.message}")
            w.keepScreen("live_stopped")
            end(if (e.alarm) Outcome.ALARM else if (e.left) Outcome.LEFT else Outcome.STOPPED, "멈춤: ${e.message}")
        }
        driver.openApp()
        return result
    }

    // ---------- 차례

    private suspend fun visit(a: Applicant, sent: List<Conversation.Sent>): String {
        val room = openRoom(a, firstTime = sent.isEmpty())
        w.keepScreen("live_room", room)
        val header = ChatRoomParser.header(room.nodes)
        val wrong = when {
            header == null -> "채팅방 위쪽(이름·공고명)을 못 읽음"
            header.name != a.name -> "다른 사람 방 ('${header.name}')"
            a.postTitle.isNotBlank() && header.postTitle != a.postTitle -> "다른 공고 방 ('${header.postTitle}')"
            else -> null
        }
        if (wrong != null) return leave(Outcome.BLOCKED, "안 보냄 — $wrong")
        val items = ChatRoomParser.window(room.nodes)
        // 방에 보이는 메시지를 대화 표에 남김 (지원자 말은 앱 처리 기록대로 — 앱이 응대 중이면 처리 전)
        val (lines, skipped) = ChatRoomParser.resolve(items, zone)
        note("대화 표: " + ChatImport.save(db, a, lines, skipped))
        val bubbles = items.filter { !it.isDate }.map { Conversation.Bubble(it.ours, it.text) }
        note("방 열림 — 말풍선 ${bubbles.size}개 (우리 ${bubbles.count { it.ours }} · 지원자 ${bubbles.count { !it.ours }})")
        return when (val next = Conversation.next(sent, bubbles, knownKey = a.phoneKey)) {
            is Conversation.Next.Wait -> leave(Outcome.WAITING, "기다림 — ${next.reason}")
            is Conversation.Next.Done -> leave(Outcome.DONE, "끝 — ${next.reason}")
            is Conversation.Next.Stop -> {
                val live = config.mode == LiveGate.Mode.LIVE
                if (live) handOver(a, next)
                leave(Outcome.HANDED_OVER, "사람에게 넘김 — ${next.reason}" + if (live) " ('${next.status}'로)" else " (연습이라 상태는 그대로)")
            }
            is Conversation.Next.Send -> send(a, sent, next)
        }
    }

    // ---------- ② 방 열기

    private suspend fun openRoom(a: Applicant, firstTime: Boolean): Snapshot {
        w.open()
        val root = w.toTabRoot()
        if (!firstTime) fromChatList(root, a)?.let { return it }
        return fromApplicants(a)
    }

    /** 채팅 목록 맨 위 화면에서 그 사람 방 (안 읽은 방이어도 엶 — 답하려면 열어야 함). 없으면 null */
    private suspend fun fromChatList(root: Snapshot, a: Applicant): Snapshot? {
        w.tab(root, "채팅", DaangnPage.CHAT_LIST)
        var s = w.loaded(DaangnPage.CHAT_LIST) { DaangnNav.chatRows(it.nodes).isNotEmpty() }
        s = w.scrollToTop(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, { DaangnNav.listRowKeys(it.nodes) }) ?: s
        if (LiveNav.roomRow(s.nodes, a.name, a.area) == null) {
            note("채팅 목록 맨 위 화면에 '${a.name}' 방이 없음 → 지원자 목록에서 찾음")
            return null
        }
        w.tap("채팅방 '${a.name}'", route.safe(LiveNav.Purpose.ROOM_ROW, a)) { LiveNav.roomRow(it, a.name, a.area) }
        w.expect(DaangnPage.CHAT_ROOM)
        return route.roomLoaded()
    }

    /** 나의 당근 → 공고 → 지원자 보기 → 그 사람 카드 → (지원서 →) '채팅하기' (RoomRoute — 드라이런과 같은 길) */
    private suspend fun fromApplicants(a: Applicant): Snapshot = route.roomFromCard(a, route.applicantCard(a))

    // ---------- ⑤⑥ 보내기

    private suspend fun send(a: Applicant, sent: List<Conversation.Sent>, next: Conversation.Next.Send): String {
        val recent = db.messageDao().recentByMethod(ReplyRules.SENT_BY_APP, Templates.RECENT_TO_AVOID).map { it.content }
        val retryNo = sent.count { it.kind == Conversation.Kind.RETRY } + 1
        val text = when (next.kind) {
            Conversation.Kind.FIRST -> ReplyPlanner.firstMessage(a, recent)
            Conversation.Kind.CLOSE -> ReplyPlanner.closingMessage(a, recent)
            Conversation.Kind.RETRY -> ReplyPlanner.retryMessage(a, retryNo, recent + sent.map { it.text })
        }
        val problems = ReplyRules.check(text)
        if (problems.isNotEmpty()) return leave(Outcome.BLOCKED, "안 보냄 — 발송 전 검사에 걸림 (${problems.joinToString(", ")})")
        note("보낼 문구 (${next.kind.label}): $text")
        val typed = type(text)
        if (config.mode == LiveGate.Mode.REHEARSAL) {
            w.keepScreen("live_room_typed", typed)
            val sendOk = LiveNav.sendButton(typed.nodes)?.let { LiveNav.isSafe(typed.nodes, it, LiveNav.Purpose.SEND, a.name) } == true
            clear()
            if (!sendOk) throw DaangnWalker.Stop("전송 칸을 못 찾음 (연습 — 입력창은 지움)")
            note("전송 칸 찾음 (연습이라 안 누름)")
            return leave(Outcome.REHEARSED, "연습 — '${next.kind.label}' 문구를 입력창에 쓰고 대조 · 전송 칸 확인까지 함 · 지우고 나옴 (전송 안 함)")
        }
        val before = mine(typed, text)
        w.tap("전송", route.safe(LiveNav.Purpose.SEND, a)) { LiveNav.sendButton(it) }
        val shown = w.waitFor(timeoutMs = SEND_CONFIRM_MS) { it.page == DaangnPage.CHAT_ROOM && mine(it, text) > before }
        if (shown == null) {
            w.keepScreen("live_unknown")
            record(a, next, text, retryNo, unknownResult = true)
            return leave(Outcome.UNKNOWN, "결과 불명 — 전송을 눌렀는데 방에 안 보임 (다시 안 보내고 시험을 끔)")
        }
        record(a, next, text, retryNo, unknownResult = false)
        note("보냄 — 방에 우리 말풍선으로 보임")
        return leave(Outcome.SENT, "보냄 — ${next.kind.label}: $text")
    }

    /** 방에 이 문구가 든 우리 말풍선 수 */
    private fun mine(s: Snapshot, text: String): Int =
        ChatRoomParser.window(s.nodes).count { !it.isDate && it.ours && Conversation.shows(it.text, text) }

    /** 입력창에 쓰고, 쓰인 글자가 보낼 문구와 같은지 대조 (다르면 지우고 멈춤) */
    private suspend fun type(text: String): Snapshot {
        driver.pause(random.nextLong(800, 1_600))
        val s = driver.current() ?: throw DaangnWalker.Stop("당근 화면을 벗어나서 멈췄어요", left = true)
        val input = LiveNav.input(s.nodes) ?: throw DaangnWalker.Stop("입력창을 못 찾음")
        if (!driver.setText(s, input, text)) throw DaangnWalker.Stop("입력창에 글자를 못 넣음")
        val typed = w.waitFor(timeoutMs = TYPE_WAIT_MS) { r ->
            LiveNav.inputText(r.nodes)?.let { Conversation.norm(it) == Conversation.norm(text) } == true
        }
        if (typed == null) {
            clear()
            throw DaangnWalker.Stop("입력창에 쓰인 글자가 보낼 문구와 달라서 안 보냄 (입력창은 지움)")
        }
        note("입력창에 씀 — 쓰인 글자와 보낼 문구가 같음")
        return typed
    }

    /** 입력창 비우기 (연습·대조가 안 맞을 때) */
    private suspend fun clear() {
        val s = driver.current() ?: return
        val input = LiveNav.input(s.nodes) ?: return
        driver.setText(s, input, "")
        driver.pause(CLEAR_PAUSE_MS)
    }

    // ---------- ⑦ 기록

    /**
     * 보낸 메시지를 대화 표에 남기고 지원자 상태를 바꿈 (결과 불명이면 메시지만 '결과 불명'으로).
     * 답한 지원자 메시지(보낸 때까지 처리 전인 것)는 '답함' — 상태를 바꾸기 전에 (보류 등으로 바뀌면 남은 처리 전은 '넘김')
     */
    private suspend fun record(a: Applicant, next: Conversation.Next.Send, text: String, retryNo: Int, unknownResult: Boolean) {
        val t = now()
        db.messageDao().insert(
            Message(
                time = t, applicantId = a.id, channel = DAANGN, sender = OURS, content = text,
                sendMethod = if (unknownResult) RESULT_UNKNOWN else ReplyRules.SENT_BY_APP,
                messageKey = "${Conversation.KEY}|${next.kind.step}|${a.id}|$t",
            )
        )
        db.applicantDao().touchLastMessage(a.id, t)
        if (unknownResult) return
        db.messageDao().listFor(a.id)
            .filter { it.sender == APPLICANT && it.appHandling == MessageCare.PENDING && it.time <= t }
            .forEach { db.messageDao().update(it.copy(appHandling = HANDLED_REPLY)) }
        val cur = db.applicantDao().get(a.id) ?: return
        val updated = when (next.kind) {
            Conversation.Kind.FIRST -> cur.copy(status = ApplicantStatus.FIRST_SENT, reason = "")
            Conversation.Kind.RETRY -> cur.copy(status = ApplicantStatus.FIRST_SENT, reason = "번호 다시 요청 ${retryNo}번")
            Conversation.Kind.CLOSE -> {
                // 번호 받기는 ContactFlow와 같은 규칙: '연락처 확보', 같은 번호의 다른 지원 건이 있으면 '보류'(합치기 보류)
                val f = next.phone
                if (f == null) cur.copy(status = ApplicantStatus.CONTACT, reason = "")
                else ContactFlow.gotNumber(cur, f, ContactFlow.othersWith(db, cur, f.key)).updated ?: cur
            }
        }
        ApplicantStore.update(db, updated)
        if (updated.status != cur.status) note("상태: '${cur.status}' → '${updated.status}'")
    }

    /** 사람에게 넘김: 상태를 바꾸고 사유를 적음 (풀면 멈추기 전 상태로). 처리 전 메시지는 '넘김'으로 (ApplicantStore) */
    private suspend fun handOver(a: Applicant, stop: Conversation.Next.Stop) {
        val cur = db.applicantDao().get(a.id) ?: return
        ApplicantStore.update(db, cur.copy(status = stop.status, statusBeforeHold = cur.status, reason = stop.reason))
    }

    // ---------- 도구

    /** 앱이 보낸 당근 메시지 (메시지 키 app|단계|…) */
    private fun sentByApp(messages: List<Message>): List<Conversation.Sent> = messages
        .filter { it.channel == DAANGN && it.sender == OURS && it.sendMethod == ReplyRules.SENT_BY_APP }
        .mapNotNull { m -> Conversation.kindOf(m.messageKey)?.let { Conversation.Sent(it, m.content, m.time) } }

    /** 방에서 나옴 (채팅방 → 앞 화면) 하고 결과 줄을 돌려줌 */
    private suspend fun leave(o: Outcome, line: String): String {
        if (driver.current()?.page == DaangnPage.CHAT_ROOM) w.back("채팅방")
        return end(o, line)
    }

    private fun end(o: Outcome, line: String): String {
        outcome = o
        return line
    }

    private fun sameDay(a: Long, b: Long): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    private fun note(line: String) {
        log.add(line)
        say(line)
    }

    companion object {
        const val RESULT_UNKNOWN = "결과 불명"   // 대화 표 '보낸 방법' (DB 설계)
        const val HANDLED_REPLY = MessageCare.ANSWERED // 대화 표 '앱 처리'
        const val TYPE_WAIT_MS = 4_000L          // 입력창에 글자가 보이기를 기다리는 시간
        const val SEND_CONFIRM_MS = 10_000L      // 전송 뒤 방에 우리 말풍선이 보이기를 기다리는 시간
        const val CLEAR_PAUSE_MS = 600L
        private const val DAANGN = "당근"
        private const val OURS = "우리"
        private const val APPLICANT = "지원자"
    }
}
