package com.kangsam.daangnrecruit.reply

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.sync.ChatRoomParser
import com.kangsam.daangnrecruit.sync.DaangnNav
import com.kangsam.daangnrecruit.sync.DaangnPage
import com.kangsam.daangnrecruit.sync.DaangnWalker
import com.kangsam.daangnrecruit.sync.MyPostParser
import com.kangsam.daangnrecruit.sync.Snapshot
import com.kangsam.daangnrecruit.sync.UiNode

/**
 * 당근에서 그 지원자 채팅방까지 가는 길 — 첫 메시지 드라이런(FirstMessageDryRun)과 실제 발송 시험(LiveTurn)이 같이 씀.
 * 화면 옮기기는 DaangnWalker, 누를 칸은 LiveNav (그 사람 것만 찾고 누르기 직전에 한 번 더 봄). 당근 화면이 바뀌면 LiveNav를 고침.
 *
 * 아직 채팅을 안 한 새 지원자는 채팅 목록에 방이 없음 (10/20 재걸) → 공고에서 그 사람을 직접 눌러 들어감:
 *   나의 당근 → 공고 → 지원자 보기 → 그 사람 카드 (applicantCard)
 *   → 읽은 카드면 카드 아래 '채팅하기', 안 읽은 카드('N')면 지원서 → '채팅하기'
 *     (지원서에서 못 찾으면 목록으로 돌아와 카드 아래 '채팅하기') → 채팅방 (roomFromCard)
 * 안 읽은 지원서를 열면 당근에선 읽은 카드가 됨 → 앱 기록에 사유(openedReason)를 남김
 * (당근 가져오기가 그 카드를 '담당자가 봄 → 담당자 응대 중'으로 바꾸지 않게 — ApplicantImport.appOpened).
 * 대화가 이미 있는 지원자는 채팅 탭의 방 줄로 감 (그 길은 부르는 쪽에 — 드라이런은 목록을 넘기며, 실제 발송 시험은 맨 위 화면만)
 */
class RoomRoute(
    private val w: DaangnWalker,
    private val db: AppDatabase,
    private val note: (String) -> Unit,
    private val openedReason: String, // 앱이 안 읽은 지원서를 열었을 때 남기는 사유 (ApplicantImport.APP_OPENED · APP_OPENED_DRY)
    private val keepPrefix: String,   // 개발용으로 남기는 화면 이름 앞 ('live' · 'dryrun')
) {
    /** 지원자 목록 화면과 그 사람 카드 (누를 칸 번호) */
    class Card(val list: Snapshot, val index: Int) {
        /** 안 읽은 카드 ('N' — 아직 아무도 지원서를 안 엶) */
        val unread: Boolean get() = LiveNav.isUnreadCard(list.nodes, index)
    }

    /**
     * 나의 당근 → 공고 → 지원자 보기 → 그 사람 카드. 아래 탭이 있는 화면에서 시작. 카드를 못 찾으면 화면을 남기고 멈춤.
     * 공고 상세·지원자 목록은 웹 화면이라 늦게 뜰 때가 있어 WEB_WAIT_MS까지 기다림 (10/20 시험: 12초 넘게 걸려 멈춘 적 있음)
     */
    suspend fun applicantCard(a: Applicant): Card {
        val here = w.driver.current() ?: throw DaangnWalker.Stop("당근 화면을 벗어나서 멈췄어요", left = true)
        w.tab(here, "나의 당근", DaangnPage.MY_PAGE)
        w.loaded(DaangnPage.MY_PAGE) { p -> DaangnNav.myPostCards(p.nodes).any { it.second == a.postTitle } }
        w.tap("공고 '${a.postTitle}'") { nodes -> DaangnNav.myPostCards(nodes).firstOrNull { it.second == a.postTitle }?.first }
        w.waitFor(timeoutMs = DaangnWalker.WEB_WAIT_MS) { MyPostParser.parse(it.screenNodes) != null }
            ?: throw DaangnWalker.Stop("공고 상세가 안 떠서 멈췄어요 (${DaangnWalker.WEB_WAIT_MS / 1000}초)")
        w.tap("지원자 보기") { DaangnNav.applicantsButton(it) }
        w.expect(DaangnPage.APPLICANT_LIST, DaangnWalker.WEB_WAIT_MS)
        val list = w.loaded(DaangnPage.APPLICANT_LIST, DaangnWalker.WEB_WAIT_MS) { LiveNav.applicantCard(it.nodes, a.name) != null }
        val card = LiveNav.applicantCard(list.nodes, a.name)
        if (card == null) {
            w.keepScreen("${keepPrefix}_applicants", list)
            throw DaangnWalker.Stop("지원자 목록에서 '${a.name}' 카드를 못 찾음 (막 지원했으면 목록에 아직 안 떴을 수 있음)")
        }
        return Card(list, card)
    }

    /** 카드에서 채팅방까지: 읽은 카드면 카드 아래 '채팅하기', 안 읽은 카드면 지원서 → '채팅하기' */
    suspend fun roomFromCard(a: Applicant, card: Card): Snapshot {
        if (LiveNav.cardChatButton(card.list.nodes, card.index) != null) return cardChat(a)

        // 안 읽은 카드 → 지원서(웹 화면) → '채팅하기' (지원서 화면은 개발용으로 남김)
        w.tap("'${a.name}' 지원서", safe(LiveNav.Purpose.CARD, a)) { LiveNav.applicantCard(it, a.name) }
        markOpened(a)
        val app = w.waitFor(timeoutMs = DaangnWalker.WEB_WAIT_MS) { s ->
            s.page != DaangnPage.APPLICANT_LIST && s.nodes.any { it.text.contains(a.name) } && LiveNav.chatButton(s.nodes) != null
        }
        w.keepScreen("${keepPrefix}_application", app)
        if (app != null) {
            w.tap("지원서의 채팅하기", safe(LiveNav.Purpose.CHAT_BUTTON, a)) { nodes ->
                if (DaangnNav.page(nodes) == DaangnPage.APPLICANT_LIST) null else LiveNav.chatButton(nodes)
            }
            w.expect(DaangnPage.CHAT_ROOM)
            return roomLoaded()
        }
        // 지원서에 '채팅하기'가 안 보임 → 지원자 목록으로 돌아와 카드(이제 읽은 카드)의 '채팅하기'
        note("지원서에서 '채팅하기'를 못 찾음 → 지원자 목록으로 돌아가 카드의 '채팅하기'")
        if (w.driver.current()?.page != DaangnPage.APPLICANT_LIST) w.back("지원서")
        w.loaded(DaangnPage.APPLICANT_LIST) { s ->
            LiveNav.applicantCard(s.nodes, a.name)?.let { c -> LiveNav.cardChatButton(s.nodes, c) } != null
        }
        return cardChat(a)
    }

    /**
     * 채팅방이 다 뜰 때까지: 위쪽 이름·공고명과 입력창, 그다음 말풍선을 잠깐 더 기다림
     * (새 방은 말풍선이 없음 — 있는 방을 말풍선이 뜨기 전에 읽어 '우리 쪽 말 없음'으로 잘못 보지 않게)
     */
    suspend fun roomLoaded(): Snapshot {
        val room = w.loaded(DaangnPage.CHAT_ROOM) { r -> ChatRoomParser.header(r.nodes) != null && LiveNav.input(r.nodes) != null }
        if (ChatRoomParser.window(room.nodes).isNotEmpty()) return room
        return w.waitFor(timeoutMs = BUBBLES_MS) { it.page == DaangnPage.CHAT_ROOM && ChatRoomParser.window(it.nodes).isNotEmpty() } ?: room
    }

    /** 누르기 직전 확인 (LiveNav.isSafe — 그 쓰임에 맞는 그 사람 칸이고 절대 안 누르는 말이 없음) */
    fun safe(purpose: LiveNav.Purpose, a: Applicant): (List<UiNode>, Int) -> Boolean =
        { nodes, i -> LiveNav.isSafe(nodes, i, purpose, a.name) }

    /** 지원자 목록 카드 아래 '채팅하기' → 채팅방 */
    private suspend fun cardChat(a: Applicant): Snapshot {
        w.tap("'${a.name}' 카드의 채팅하기", safe(LiveNav.Purpose.CHAT_BUTTON, a)) { nodes ->
            LiveNav.applicantCard(nodes, a.name)?.let { c -> LiveNav.cardChatButton(nodes, c) }
        }
        w.expect(DaangnPage.CHAT_ROOM)
        return roomLoaded()
    }

    /** 앱이 안 읽은 지원서를 열었음을 적어 둠 ('새 지원'이고 사유가 비어 있을 때만 — 상태는 그대로) */
    private suspend fun markOpened(a: Applicant) {
        val cur = db.applicantDao().get(a.id) ?: return
        if (cur.status == ApplicantStatus.NEW && cur.reason.isBlank()) ApplicantStore.update(db, cur.copy(reason = openedReason))
    }

    companion object {
        const val BUBBLES_MS = 2_500L // 채팅방 위쪽·입력창이 뜬 뒤 말풍선을 더 기다리는 시간 (새 방이면 이만큼 기다리고 빈 방으로 봄)
    }
}
