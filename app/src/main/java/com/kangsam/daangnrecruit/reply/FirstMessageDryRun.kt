package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.sync.ApplicantImport
import com.kangsam.daangnrecruit.sync.ChatRoomParser
import com.kangsam.daangnrecruit.sync.ChatRow
import com.kangsam.daangnrecruit.sync.DaangnNav
import com.kangsam.daangnrecruit.sync.DaangnPage
import com.kangsam.daangnrecruit.sync.DaangnWalker
import com.kangsam.daangnrecruit.sync.ScreenDriver
import com.kangsam.daangnrecruit.sync.Snapshot
import kotlin.random.Random

/**
 * 당근 첫 메시지 드라이런 (사용자 프로세스 B2, 1차 도전 목표 '채팅방 진입 → 문구 생성 → 입력창 입력 없음').
 * 한 지원자에 대해: 보낼 문구를 고르고(ReplyPlanner.firstMessage) 발송 전 검사 → 앱 기록으로 보낼지 판단 → 당근에서 그 지원자 방을 열어
 * 방 위쪽(이름·공고명)이 맞는지, 방에 사람이 보낸 우리 쪽 메시지가 있는지 보고, 입력창·전송 버튼이 있는지만 확인 → 나와서 우리 앱.
 *
 * 방 가는 길 (지원자 목록 길은 실제 발송 시험과 같은 RoomRoute):
 * - 앱에 이 지원자와의 당근 대화가 있으면 (방이 있음) → 채팅 탭에서 그 방을 찾음 (목록을 넘기며 — 지원하기 전 방만 남으면 그만).
 *   안 읽은 방은 열지 않음 (열면 상대에게 읽음으로 보이고 담당자 화면의 안 읽음 표시가 사라짐)
 * - 없으면 (아직 채팅을 안 한 새 지원자 — 채팅 목록에 방이 없음, 10/20 재걸) → 공고에서 그 사람을 직접 눌러 들어감:
 *   나의 당근 → 공고 → 지원자 보기 → 그 사람 카드 → 안 읽은 카드면 지원서 → '채팅하기', 앱이 전에 연 카드면 카드 아래 '채팅하기'.
 *   안 읽은 지원서를 열면 당근에서 읽은 카드가 됨 → 앱 기록에 사유 '앱이 지원서를 엶 (드라이런)' (상태는 '새 지원' 그대로)
 *   담당자가 이미 본 카드는 방을 열지 않음 (담당자가 대화 중일 수 있고, 안 읽은 메시지가 있으면 열 때 읽음으로 보임)
 *
 * 입력창에 쓰거나 전송하지 않음 — 방 안에서는 아무것도 누르지 않음 (입력창·전송은 DaangnNav.isSafeToClick·LiveNav.isSafe가 막음).
 * 앱 기록은 지원서를 열었다는 사유 말고는 바꾸지 않고 결과만 남김. 끝나면 당근을 첫 화면(채팅 목록 맨 위 · 나의 당근)으로 돌려놓음
 */
class FirstMessageDryRun(
    private val driver: ScreenDriver,
    private val db: AppDatabase,
    private val applicantId: Long,
    private val say: (String) -> Unit = {},
    random: Random = Random.Default,
) {
    /** 한 일 (지원자 상세 '드라이런 기록') */
    val log = mutableListOf<String>()

    /** 멈춤 글자(보안 인증·이용 제한 등)로 멈췄으면 그 멈춤 — 접근성 서비스가 전체 멈춤 + 계정 상태 기록 (Safety) */
    var alarm: DaangnWalker.Stop? = null
        private set
    private val w = DaangnWalker(driver, ::note, random)
    private val route = RoomRoute(w, db, ::note, ApplicantImport.APP_OPENED_DRY, "dryrun")

    /** 방에 가 본 결과 */
    private data class Visit(val opened: Boolean, val reason: String = "", val block: String? = null)

    /** 한 번 돌고 한 줄 결과를 돌려줌 */
    suspend fun run(): String {
        val a = db.applicantDao().get(applicantId) ?: return "드라이런 — 지원자를 못 찾음"
        val messages = db.messageDao().listFor(a.id)
        // 지원자 상세 '다음에 앱이 할 일'에 보이는 것과 같은 문구 (Templates에서 이 지원자 몫으로 고른 것, 최근에 보낸 문구는 피함)
        val recent = db.messageDao().recentByMethod(ReplyRules.SENT_BY_APP, Templates.RECENT_TO_AVOID).map { it.content }
        val text = ReplyPlanner.firstMessage(a, recent)
        val problems = ReplyRules.check(text)
        val dbBlock = ReplyRules.firstMessageBlock(a, messages)
        note("보낼 문구: $text")
        note("발송 전 검사: " + if (problems.isEmpty()) "통과 (${text.length}자 · ${text.trim().lines().size}줄)" else "걸림 — ${problems.joinToString(", ")}")
        note("앱 기록으로 본 판단: " + (dbBlock?.let { "안 보냄 — $it" } ?: "보낼 수 있음"))
        if (a.isSample) return "드라이런 — 예시 지원자라 당근에 방이 없어 앱 기록만 봄"

        // 앱에 당근 대화가 있으면 방이 있음 → 채팅 목록에서, 없으면 아직 채팅 전 → 공고의 지원자 목록에서
        val chatted = messages.any { it.channel == DAANGN }
        val visit = try {
            w.open()
            val root = w.toTabRoot()
            if (chatted) fromChatList(root, a, text).also { tidyChatList() } else fromApplicants(a, text).also { tidyTabRoot() }
        } catch (e: DaangnWalker.Stop) {
            if (e.alarm) alarm = e
            note("멈춤: ${e.message}")
            w.keepScreen("stopped")
            driver.openApp()
            return "드라이런 멈춤: ${e.message}"
        }
        driver.openApp()

        val block = dbBlock ?: visit.block
        val verdict = when {
            !visit.opened -> "방까지 못 감 — ${visit.reason}"
            problems.isNotEmpty() -> "실제라면: 안 보냄 — 발송 전 검사에 걸림 (${problems.joinToString(", ")})"
            block != null -> "실제라면: 안 보냄 — $block"
            else -> "실제라면: 이 문구를 입력창에 쓰고 전송한 뒤 '첫 메시지 보냄'으로 (드라이런이라 입력·전송 안 함)"
        }
        note(verdict)
        return "드라이런 — $verdict"
    }

    // ---------- 방 가는 길 두 가지

    /** 대화가 있는 지원자: 채팅 탭에서 그 방을 찾아 열고, 확인만 하고 나옴 */
    private suspend fun fromChatList(root: Snapshot, a: Applicant, text: String): Visit {
        w.tab(root, "채팅", DaangnPage.CHAT_LIST)
        var s = w.loaded(DaangnPage.CHAT_LIST) { DaangnNav.chatRows(it.nodes).isNotEmpty() }
        if (DaangnNav.chatRows(s.nodes).isEmpty()) {
            w.keepScreen("chat_list", s)
            return Visit(false, "채팅 목록에서 방 줄을 못 찾음")
        }
        // 목록 맨 위부터 아래로 넘기며 이 지원자 방을 찾음 (이름 + 동네). 이 지원자가 지원하기 전 방만 남으면 그만
        s = w.scrollToTop(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys) ?: s
        val now = System.currentTimeMillis()
        val cutoff = a.appliedAt - DaangnNav.ROOM_AGE_MARGIN_MS
        var row: ChatRow? = null
        for (page in 0 until MAX_LIST_PAGES) {
            val here = DaangnNav.chatRows(s.nodes)
            val same = here.filter { it.name == a.name && (a.area.isBlank() || it.area == a.area) }
            if (same.size > 1) return Visit(false, "목록에 이름·동네가 같은 방이 ${same.size}개라 어느 방인지 모름")
            row = same.firstOrNull()
            if (row != null || DaangnNav.allOlderThan(here, cutoff, now)) break
            s = w.scrollDown(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys) ?: break
        }
        if (row == null) {
            return Visit(false, "채팅 목록에 이 지원자 방이 없음 — 앱엔 당근 대화가 있는데 목록에서 못 찾음 (이름·동네가 당근과 다를 수 있음)")
        }
        if (row.unread) return Visit(false, "안 읽은 메시지가 있는 방이라 열지 않음 (열면 상대에게 읽음으로 보임)")

        val key = "${row.name}|${row.area}"
        w.tap("채팅방 '${row.name}'") { nodes -> DaangnNav.chatRows(nodes).firstOrNull { "${it.name}|${it.area}" == key }?.index }
        w.expect(DaangnPage.CHAT_ROOM)
        val visit = look(a, text, route.roomLoaded())
        w.back("채팅방")
        w.expect(DaangnPage.CHAT_LIST)
        return visit
    }

    /** 아직 채팅 전인 새 지원자: 나의 당근 → 공고 → 지원자 보기 → 그 사람 카드 → (지원서 →) '채팅하기' → 채팅방에서 확인만 하고 나옴 */
    private suspend fun fromApplicants(a: Applicant, text: String): Visit {
        note("앱에 이 지원자와의 당근 대화가 없음 (아직 채팅 전) → 공고의 지원자 목록에서 그 사람을 눌러 들어감")
        val card = route.applicantCard(a)
        if (!card.unread && !ApplicantImport.appOpened(a.reason)) {
            w.keepScreen("dryrun_applicants", card.list)
            return Visit(false, "지원자 목록의 '${a.name}' 카드는 담당자가 이미 지원서를 본 카드라 채팅방은 열지 않음 (담당자가 대화 중일 수 있음)")
        }
        val visit = look(a, text, route.roomFromCard(a, card))
        w.back("채팅방")
        return visit
    }

    // ---------- 방 안 (누르지 않음)

    /** 채팅방에서 보기만: 위쪽 이름·공고명 · 보이는 우리 쪽 말풍선 · 입력창·전송 버튼 (누르지 않음). 보낼 문구를 띠에 잠깐 띄움 */
    private suspend fun look(a: Applicant, text: String, r: Snapshot): Visit {
        val header = ChatRoomParser.header(r.nodes)
        val wrong = when {
            header == null -> "채팅방 위쪽(이름·공고명)을 못 읽음"
            header.name != a.name -> "다른 사람 방 ('${header.name}')"
            a.postTitle.isNotBlank() && header.postTitle != a.postTitle -> "다른 공고 방 ('${header.postTitle}')"
            else -> null
        }
        val ours = ChatRoomParser.window(r.nodes).count { !it.isDate && it.ours }
        val input = DaangnNav.chatInput(r.nodes) != null
        val send = DaangnNav.sendButton(r.nodes) != null
        if (wrong == null && header != null) {
            note(
                "채팅방 엶: '${header.name}' · '${header.postTitle}' — 보이는 우리 쪽 말풍선 ${ours}개 · " +
                    "입력창 ${if (input) "찾음" else "못 찾음"} · 전송 버튼 ${if (send) "찾음" else "못 찾음"} (누르지 않음)"
            )
            // 띠에 보낼 문구를 띄워 둔 채 잠깐 (사람이 화면으로 볼 수 있게) — 입력창에는 쓰지 않음
            say("드라이런 — 보낼 문구 (입력·전송 안 함):\n$text")
            driver.pause(SHOW_MS)
        }
        w.keepScreen("dryrun_room", r)
        if (wrong != null) return Visit(false, wrong)
        if (!input || !send) return Visit(true, block = "입력창이나 전송 버튼을 못 찾음 — 화면이 바뀌었을 수 있음")
        return Visit(true, block = ReplyRules.roomBlock(ours))
    }

    // ---------- 끝나고 돌려놓기

    /** 화면이 움직였는지 볼 때는 공식 방·알림 줄까지 모든 줄로 (DaangnNav.listRowKeys) */
    private fun listKeys(s: Snapshot) = DaangnNav.listRowKeys(s.nodes)

    /** 채팅 목록 길이 끝나면 목록을 맨 위로 돌려놓음 (담당자가 당근을 열었을 때 내려간 채로 보이지 않게) */
    private suspend fun tidyChatList() {
        if (w.waitFor(timeoutMs = TIDY_WAIT_MS) { it.page == DaangnPage.CHAT_LIST } == null) {
            note("채팅 목록을 맨 위로 못 돌림 — 지금 화면: ${w.describe()}")
            return
        }
        w.scrollToTop(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys)
    }

    /** 지원자 목록 길이 끝나면 아래 탭이 있는 첫 화면(나의 당근)까지 뒤로 (지원서·지원자 목록이 열린 채로 남지 않게) */
    private suspend fun tidyTabRoot() {
        try {
            w.toTabRoot()
        } catch (e: DaangnWalker.Stop) {
            if (e.alarm || e.left) throw e
            note("나의 당근까지 못 돌아감 — ${e.message}")
        }
    }

    private fun note(line: String) {
        log.add(line)
        say(line)
    }

    companion object {
        const val MAX_LIST_PAGES = 30 // 채팅 목록을 아래로 넘기며 보는 화면 수 한도 (가져오기와 같음)
        const val SHOW_MS = 4_000L    // 채팅방에서 보낼 문구를 띠에 보여 주는 시간
        const val TIDY_WAIT_MS = 3_000L // 끝나고 채팅 목록을 맨 위로 돌려놓기 전에 채팅 목록 화면을 기다리는 시간
        private const val DAANGN = "당근" // 대화 표 '채널'
    }
}
