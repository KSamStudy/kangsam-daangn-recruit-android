package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.ApplicantView
import java.time.ZoneId
import kotlin.random.Random

/**
 * 당근 가져오기 한 번 (프로세스 C). 화면을 다루는 손(ScreenDriver)만 받아서 돌아서, 다른 기능과 상관없이 혼자 돌고
 * 폰 없이 가짜 손으로 시험할 수 있음.
 *
 * 순서: 당근 열기 → (아래 탭이 있는 첫 화면까지 뒤로) → 나의 당근
 *       → 진행중인 구인 공고마다: 공고 상세(공고) → 지원자 보기(지원자) → 뒤로 → 채팅 탭
 *       → 지원자 방마다: 위로 올려 가며 읽기(대화) → 뒤로 → 우리 앱으로
 *
 * 안전장치: 화면을 옮겨 다니는 건 DaangnWalker (누를 칸은 DaangnNav가 고르고 isSafeToClick으로 한 번 더 봄,
 * 사람처럼 쉼, 누르는 횟수 한도, 기다리던 화면이 안 뜨거나 당근을 벗어나거나 보안 인증·이용 제한 글자가 보이면 바로 멈춤).
 * 안 읽은 채팅방은 건너뜀
 *
 * 개발용: 화면이 덤프와 다르게 보이면(채팅 줄을 못 찾음·앱에 있는 이름이 없음·방 위쪽을 못 읽음·멈춤) 그때 화면을
 * 폰 안 앱 전용 폴더(files/debug)에 남김 — 지원자 이름·메시지가 들어 있어 폰 밖으로는 맥 바탕화면·채팅에만
 */
class SyncRunner(
    private val driver: ScreenDriver,
    private val db: AppDatabase,
    private val zone: ZoneId,
    private val say: (String) -> Unit = {},
    random: Random = Random.Default,
) {
    /** 한 번 돌며 한 일 (설정 탭 '지난 실행 기록') */
    val log = mutableListOf<String>()
    private val results = mutableListOf<String>()
    private val w = DaangnWalker(driver, ::note, random)

    /** 끝까지 돌거나 멈추고, 한 줄 요약을 돌려줌 */
    suspend fun run(): String {
        val summary = try {
            w.open()
            val root = w.toTabRoot()
            val myPage = postsAndApplicants(root)
            w.tab(myPage, "채팅", DaangnPage.CHAT_LIST)
            chats()
            tidyChatList()
            "끝 — " + results.joinToString(" / ")
        } catch (e: DaangnWalker.Stop) {
            note("멈춤: ${e.message}")
            w.keepScreen("stopped")
            "멈춤: ${e.message}" + (if (results.isNotEmpty()) " (그 전까지: ${results.joinToString(" / ")})" else "")
        }
        driver.openApp()
        return summary
    }

    // ---------- 단계

    /**
     * 나의 당근의 진행중인 구인 공고 카드마다: 공고 상세(공고) → 지원자 보기(지원자 목록) → 뒤로 두 번 → 나의 당근.
     * (구인글 관리의 카드는 공고 상세가 아니라 알바 화면의 지원자 목록을 열어서 쓰지 않음 — 10/15 폰에서 확인)
     */
    private suspend fun postsAndApplicants(start: Snapshot): Snapshot {
        w.tab(start, "나의 당근", DaangnPage.MY_PAGE)
        // 공고 카드는 화면 틀보다 늦게 뜸 → 뜰 때까지 기다림 (공고가 없으면 끝내 안 뜸)
        var myPage = w.loaded(DaangnPage.MY_PAGE) { DaangnNav.myPostCards(it.nodes).isNotEmpty() }
        val titles = DaangnNav.myPostCards(myPage.nodes).map { it.second }.distinct()
        if (titles.isEmpty()) {
            results.add("나의 당근에 진행중인 구인 공고가 안 보여 공고·지원자는 건너뜀")
            return myPage
        }
        val live = DaangnNav.liveRowCount(myPage.nodes)
        results.add("진행중인 구인 ${titles.size}개" + (if (live != null && live > titles.size) " (진행중 ${live}개 중 나의 당근에 보이는 것만)" else ""))
        for (title in titles) {
            say("공고 '$title' 가져오는 중")
            w.loaded(DaangnPage.MY_PAGE) { p -> DaangnNav.myPostCards(p.nodes).any { it.second == title } }
            w.tap("공고 '$title'") { nodes -> DaangnNav.myPostCards(nodes).firstOrNull { it.second == title }?.first }
            val s = w.waitFor { MyPostParser.parse(it.screenNodes) != null }
                ?: throw DaangnWalker.Stop("공고 상세가 안 떠서 멈췄어요 (${w.describe()})")
            save(s)
            if (DaangnNav.applicantsButton(s.nodes) != null) {
                w.tap("지원자 보기") { DaangnNav.applicantsButton(it) }
                w.expect(DaangnPage.APPLICANT_LIST)
                save(allCards())
                w.back("지원자 목록")
                w.expect(DaangnPage.POST_DETAIL)
            }
            w.back("공고 상세")
            myPage = w.expect(DaangnPage.MY_PAGE)
        }
        return myPage
    }

    /**
     * 채팅 목록에서 앱에 있는 지원자 방을 하나씩 열어 대화를 읽음. 안 읽은 방은 건너뜀.
     * 끝난 지원(종료·합침·미응답 — 당근 지원자 목록에서 빠진 사람 포함)은 방을 열지 않음 (ApplicantView)
     */
    private suspend fun chats() {
        val people = db.applicantDao().nonSample().filter { ApplicantView.isShown(it) }
        val names = people.map { it.name }.toSet()
        if (names.isEmpty()) {
            results.add("채팅: 앱에 끝나지 않은 지원자가 없어 건너뜀")
            return
        }
        // 채팅 줄은 화면 틀보다 늦게 뜰 수 있음 → 지원자 방 줄이 보일 때까지 잠깐 기다림
        var s = w.loaded(DaangnPage.CHAT_LIST) { DaangnNav.chatRows(it.nodes).isNotEmpty() }
        if (DaangnNav.chatRows(s.nodes).isEmpty()) {
            // 10/18 폰: 덤프(uiautomator)에는 지원자 방 줄이 있는데 앱은 하나도 못 봄 → 앱이 본 화면을 남겨 원인을 봄
            w.keepScreen("chat_list", s)
            results.add("채팅 목록에서 방 줄을 못 찾아 채팅은 건너뜀")
            return
        }
        val first = s
        // 목록 맨 위부터 (전에 내려 둔 자리에서 시작할 수 있음)
        s = w.scrollToTop(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys) ?: s
        val top = s
        // 앱 지원자가 가장 먼저 지원한 때보다 (여유를 두고) 오래된 방만 남으면 그만 내려감
        val now = System.currentTimeMillis()
        val cutoff = people.minOf { it.appliedAt } - DaangnNav.ROOM_AGE_MARGIN_MS
        val seen = mutableSetOf<String>() // 목록에서 본 지원자 방 (이름|동네)
        val done = mutableSetOf<String>() // 그중 앱에 있는 사람의 방
        val fresh = mutableListOf<Int>()  // 화면마다 새로 본 방 수 (넘기기가 얼마나 움직이는지 보려고)
        var rooms = 0
        var unread = 0
        var end = "넘기기 한도(${MAX_LIST_PAGES}화면)까지"
        for (page in 0 until MAX_LIST_PAGES) {
            val here = DaangnNav.chatRows(s.nodes)
            fresh += here.count { "${it.name}|${it.area}" !in seen }
            for (row in here) {
                val key = "${row.name}|${row.area}"
                seen += key
                if (row.name !in names || key in done) continue
                done += key
                if (row.unread) {
                    unread++
                    note("안 읽은 방이라 건너뜀: ${row.name}")
                    continue
                }
                if (rooms >= MAX_ROOMS) break
                // 방에서 돌아오는 사이 목록 차례가 바뀌어 그 줄이 안 보이면 건너뜀 (다음 번에 읽음)
                if (key !in rowKeys(s)) {
                    note("목록에서 안 보여 건너뜀: ${row.name}")
                    continue
                }
                say("채팅 '${row.name}' 가져오는 중")
                w.tap("채팅방 '${row.name}'") { nodes -> DaangnNav.chatRows(nodes).firstOrNull { "${it.name}|${it.area}" == key }?.index }
                w.expect(DaangnPage.CHAT_ROOM)
                // 위쪽 공고 줄(웹뷰)과 메시지는 방이 열린 뒤 조금 있다 뜸
                results.add(readRoom(w.loaded(DaangnPage.CHAT_ROOM) { r ->
                    ChatRoomParser.header(r.nodes) != null && ChatRoomParser.window(r.nodes).isNotEmpty()
                }))
                rooms++
                w.back("채팅방")
                s = w.expect(DaangnPage.CHAT_LIST)
            }
            if (rooms >= MAX_ROOMS) {
                end = "한 번에 여는 방 수(${MAX_ROOMS}개)까지"
                break
            }
            if (DaangnNav.allOlderThan(here, cutoff, now)) {
                end = "앱 지원자가 지원하기 전 방까지"
                break
            }
            // 끝처럼 보이면 다음 묶음을 잠깐 기다렸다가 한 번 더 넘겨 봄 (DaangnWalker.scrollDown)
            val next = w.scrollDown(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys)
            if (next == null) {
                end = "목록 끝까지"
                break
            }
            s = next
        }
        note("채팅 목록: ${fresh.size}화면 봄 (화면마다 새 방 ${fresh.joinToString("·")}) · $end")
        // 방 줄은 보이는데 앱에 있는 이름이 하나도 없으면 이름 모양을 보려고 남김 (처음 뜬 화면 · 맨 위 화면)
        if (done.isEmpty()) {
            w.keepScreen("chat_list", first)
            if (top !== first) w.keepScreen("chat_list_top", top)
        }
        results.add(
            "채팅방 ${rooms}개 읽음" + (if (unread > 0) " · 안 읽은 방 ${unread}개 건너뜀" else "") +
                " (목록에서 본 방 ${seen.size}개 · 그중 앱에 있는 지원자 ${done.size}명)"
        )
    }

    /** 끝나면 채팅 목록을 맨 위로 돌려놓음 (담당자가 당근을 열었을 때 내려간 채로 보이지 않게, 다음 번도 위에서 시작) */
    private suspend fun tidyChatList() {
        if (driver.current()?.page != DaangnPage.CHAT_LIST) return
        w.scrollToTop(DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::listKeys)
    }

    /** 채팅방 하나: 맨 아래(최근)부터 위로 올려 가며 이어 붙인 뒤 저장 */
    private suspend fun readRoom(first: Snapshot): String {
        val header = ChatRoomParser.header(first.nodes)
        if (header == null) {
            w.keepScreen("chat_room", first)
            return "채팅방 위쪽을 못 읽음"
        }
        if (db.applicantDao().findByTitleAndName(header.postTitle, header.name).isEmpty()) {
            return "'${header.name}' 방 — '${header.postTitle}' 지원자가 앱에 없어 건너뜀"
        }
        var items = ChatRoomParser.window(first.nodes)
        for (i in 0 until MAX_ROOM_PAGES) {
            val s = w.scrollMoved(up = true, DaangnPage.CHAT_ROOM, { ChatRoomParser.messageList(it) }) { ChatRoomParser.window(it.nodes) }
                ?: break // 더 안 올라감 (맨 위)
            val merged = ChatRoomParser.prepend(ChatRoomParser.window(s.nodes), items)
            if (merged.size == items.size) break
            items = merged
        }
        val (lines, skipped) = ChatRoomParser.resolve(items, zone)
        return ChatImport.save(db, header, lines, skipped)
    }

    // ---------- 도구

    /** 지원자 카드가 다 뜰 때까지 (탭의 숫자만큼, 또는 두 번 연달아 같은 수) */
    private suspend fun allCards(): Snapshot {
        var last: Snapshot? = null
        var lastCount = -1
        val found = w.waitFor(timeoutMs = 6_000) { s ->
            last = s
            val list = ApplicantListParser.parse(s.screenNodes)
            val want = list?.tabText?.filter { it.isDigit() }?.toIntOrNull()
            val n = list?.applicants?.size ?: 0
            val done = (want != null && n >= want) || (n > 0 && n == lastCount)
            lastCount = n
            done
        }
        return found ?: last ?: w.expect(DaangnPage.APPLICANT_LIST)
    }

    /** 화면 한 장을 앱 기록에 맞추고 결과를 남김 */
    private suspend fun save(s: Snapshot) {
        val screen = ScreenImport.read(s.nodes) ?: return
        val message = ScreenImport.save(db, screen, zone)
        results.add(message)
        note(message)
    }

    private fun rowKeys(s: Snapshot) = DaangnNav.chatRows(s.nodes).map { "${it.name}|${it.area}" }

    /** 화면이 움직였는지 볼 때는 공식 방·알림 줄까지 모든 줄로 (DaangnNav.listRowKeys) */
    private fun listKeys(s: Snapshot) = DaangnNav.listRowKeys(s.nodes)

    private fun note(line: String) {
        log.add(line)
        say(line)
    }

    companion object {
        const val MAX_ROOMS = 20      // 한 번에 여는 채팅방 수 한도
        const val MAX_LIST_PAGES = 30 // 채팅 목록을 아래로 넘기며 보는 화면 수 한도 (보통은 앱 지원자보다 오래된 방에서 멈춤)
        const val MAX_ROOM_PAGES = 20 // 채팅방 하나를 위로 올리는 횟수 한도
    }
}
