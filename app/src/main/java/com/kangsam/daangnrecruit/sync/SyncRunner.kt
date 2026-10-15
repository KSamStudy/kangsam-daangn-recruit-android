package com.kangsam.daangnrecruit.sync

import com.kangsam.daangnrecruit.AppDatabase
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
 * 안전장치: 누를 칸은 DaangnNav가 고르고 isSafeToClick으로 한 번 더 봄(글쓰기·전송·마감·수정·상태·광고·추천·근무확정·당근페이 막음),
 * 누르기 전 사람처럼 1.2~2.8초 쉼, 한 번에 누르는 횟수 한도, 기다리던 화면이 안 뜨거나 당근을 벗어나거나
 * 보안 인증·이용 제한 글자가 보이면 바로 멈춤, 안 읽은 채팅방은 건너뜀
 */
class SyncRunner(
    private val driver: ScreenDriver,
    private val db: AppDatabase,
    private val zone: ZoneId,
    private val say: (String) -> Unit = {},
    private val random: Random = Random.Default,
) {
    class Stop(message: String) : Exception(message)

    /** 한 번 돌며 한 일 (설정 탭 '지난 실행 기록') */
    val log = mutableListOf<String>()
    private val results = mutableListOf<String>()
    private var clicks = 0

    /** 끝까지 돌거나 멈추고, 한 줄 요약을 돌려줌 */
    suspend fun run(): String {
        val summary = try {
            open()
            val root = toTabRoot()
            val myPage = postsAndApplicants(root)
            tab(myPage, "채팅", DaangnPage.CHAT_LIST)
            chats()
            "끝 — " + results.joinToString(" / ")
        } catch (e: Stop) {
            note("멈춤: ${e.message}")
            "멈춤: ${e.message}" + (if (results.isNotEmpty()) " (그 전까지: ${results.joinToString(" / ")})" else "")
        }
        driver.openApp()
        return summary
    }

    // ---------- 단계

    private suspend fun open() {
        note("당근 열기")
        if (!driver.openDaangn()) throw Stop("당근 앱을 못 열었어요")
        waitFor(timeoutMs = 15_000, awayMs = 15_000) { true } ?: throw Stop("당근이 안 열렸어요")
    }

    /** 아래 탭(홈·채팅·나의 당근…)이 있는 첫 화면까지 뒤로 */
    private suspend fun toTabRoot(): Snapshot {
        for (i in 0 until 6) {
            waitFor(timeoutMs = 5_000) { DaangnNav.hasBottomNav(it.nodes) }?.let { return it }
            back("아래 탭이 있는 첫 화면으로")
        }
        throw Stop("아래 탭이 있는 첫 화면으로 못 돌아갔어요")
    }

    /**
     * 나의 당근의 진행중인 구인 공고 카드마다: 공고 상세(공고) → 지원자 보기(지원자 목록) → 뒤로 두 번 → 나의 당근.
     * (구인글 관리의 카드는 공고 상세가 아니라 알바 화면의 지원자 목록을 열어서 쓰지 않음 — 10/15 폰에서 확인)
     */
    private suspend fun postsAndApplicants(start: Snapshot): Snapshot {
        tab(start, "나의 당근", DaangnPage.MY_PAGE)
        // 공고 카드는 화면 틀보다 늦게 뜸 → 뜰 때까지 기다림 (공고가 없으면 끝내 안 뜸)
        var myPage = loaded(DaangnPage.MY_PAGE) { DaangnNav.myPostCards(it.nodes).isNotEmpty() }
        val titles = DaangnNav.myPostCards(myPage.nodes).map { it.second }.distinct()
        if (titles.isEmpty()) {
            results.add("나의 당근에 진행중인 구인 공고가 안 보여 공고·지원자는 건너뜀")
            return myPage
        }
        val live = DaangnNav.liveRowCount(myPage.nodes)
        results.add("진행중인 구인 ${titles.size}개" + (if (live != null && live > titles.size) " (진행중 ${live}개 중 나의 당근에 보이는 것만)" else ""))
        for (title in titles) {
            say("공고 '$title' 가져오는 중")
            loaded(DaangnPage.MY_PAGE) { p -> DaangnNav.myPostCards(p.nodes).any { it.second == title } }
            tap("공고 '$title'") { nodes -> DaangnNav.myPostCards(nodes).firstOrNull { it.second == title }?.first }
            val s = waitFor { MyPostParser.parse(it.screenNodes) != null } ?: throw Stop("공고 상세가 안 떠서 멈췄어요 (${describe()})")
            save(s)
            if (DaangnNav.applicantsButton(s.nodes) != null) {
                tap("지원자 보기") { DaangnNav.applicantsButton(it) }
                expect(DaangnPage.APPLICANT_LIST)
                save(allCards())
                back("지원자 목록")
                expect(DaangnPage.POST_DETAIL)
            }
            back("공고 상세")
            myPage = expect(DaangnPage.MY_PAGE)
        }
        return myPage
    }

    /** 채팅 목록에서 앱에 있는 지원자 방을 하나씩 열어 대화를 읽음. 안 읽은 방은 건너뜀 */
    private suspend fun chats() {
        val names = db.applicantDao().nonSample().map { it.name }.toSet()
        if (names.isEmpty()) {
            results.add("채팅: 앱에 지원자가 없어 건너뜀")
            return
        }
        // 채팅 줄은 화면 틀보다 늦게 뜰 수 있음 → 지원자 방 줄이 보일 때까지 잠깐 기다림
        var s = loaded(DaangnPage.CHAT_LIST) { DaangnNav.chatRows(it.nodes).isNotEmpty() }
        // 목록 맨 위부터
        for (i in 0 until 5) s = scrollMoved(up = true, DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::rowKeys) ?: break
        val done = mutableSetOf<String>()
        var rooms = 0
        var unread = 0
        for (page in 0 until MAX_LIST_PAGES) {
            for (row in DaangnNav.chatRows(s.nodes)) {
                val key = "${row.name}|${row.area}"
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
                tap("채팅방 '${row.name}'") { nodes -> DaangnNav.chatRows(nodes).firstOrNull { "${it.name}|${it.area}" == key }?.index }
                expect(DaangnPage.CHAT_ROOM)
                // 위쪽 공고 줄(웹뷰)과 메시지는 방이 열린 뒤 조금 있다 뜸
                results.add(readRoom(loaded(DaangnPage.CHAT_ROOM) { r ->
                    ChatRoomParser.header(r.nodes) != null && ChatRoomParser.window(r.nodes).isNotEmpty()
                }))
                rooms++
                back("채팅방")
                s = expect(DaangnPage.CHAT_LIST)
            }
            if (rooms >= MAX_ROOMS) break
            s = scrollMoved(up = false, DaangnPage.CHAT_LIST, { DaangnNav.chatListScroller(it) }, ::rowKeys) ?: break
        }
        results.add("채팅방 ${rooms}개 읽음" + (if (unread > 0) " · 안 읽은 방 ${unread}개 건너뜀" else ""))
    }

    /** 채팅방 하나: 맨 아래(최근)부터 위로 올려 가며 이어 붙인 뒤 저장 */
    private suspend fun readRoom(first: Snapshot): String {
        val header = ChatRoomParser.header(first.nodes) ?: return "채팅방 위쪽을 못 읽음"
        if (db.applicantDao().findByTitleAndName(header.postTitle, header.name).isEmpty()) {
            return "'${header.name}' 방 — '${header.postTitle}' 지원자가 앱에 없어 건너뜀"
        }
        var items = ChatRoomParser.window(first.nodes)
        for (i in 0 until MAX_ROOM_PAGES) {
            val s = scrollMoved(up = true, DaangnPage.CHAT_ROOM, { ChatRoomParser.messageList(it) }) { ChatRoomParser.window(it.nodes) }
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
        val found = waitFor(timeoutMs = 6_000) { s ->
            last = s
            val list = ApplicantListParser.parse(s.screenNodes)
            val want = list?.tabText?.filter { it.isDigit() }?.toIntOrNull()
            val n = list?.applicants?.size ?: 0
            val done = (want != null && n >= want) || (n > 0 && n == lastCount)
            lastCount = n
            done
        }
        return found ?: last ?: expect(DaangnPage.APPLICANT_LIST)
    }

    /** 화면 한 장을 앱 기록에 맞추고 결과를 남김 */
    private suspend fun save(s: Snapshot) {
        val screen = ScreenImport.read(s.nodes) ?: return
        val message = ScreenImport.save(db, screen, zone)
        results.add(message)
        note(message)
    }

    /** 사람처럼 쉬었다가, 그때 화면에서 칸을 다시 찾아 안전한지 보고 누름 */
    private suspend fun tap(what: String, find: (List<UiNode>) -> Int?) {
        if (++clicks > MAX_CLICKS) throw Stop("한 번에 누르는 횟수(${MAX_CLICKS}번)를 넘어서 멈췄어요")
        driver.pause(random.nextLong(1_200, 2_800))
        val s = driver.current() ?: throw Stop("당근 화면을 벗어나서 멈췄어요")
        DaangnNav.stopWord(s.nodes)?.let { throw Stop("'$it' 글자가 보여서 멈췄어요") }
        val i = find(s.nodes) ?: throw Stop("누를 칸을 못 찾아서 멈췄어요: $what")
        if (!DaangnNav.isSafeToClick(s.nodes, i)) throw Stop("누르면 안 되는 칸이라 멈췄어요: $what")
        if (!driver.click(s, i)) throw Stop("누르기가 안 돼서 멈췄어요: $what")
        note("누름: $what")
    }

    private suspend fun back(what: String) {
        driver.pause(random.nextLong(900, 1_800))
        driver.back()
        note("뒤로: $what")
    }

    /**
     * 스크롤하고 화면이 바뀔 때까지 기다림 (스크롤이 끝나기 전에 읽어 '끝'으로 잘못 알지 않게).
     * 바뀐 화면을 돌려주고, 안 움직였으면(목록 끝·맨 위) null. key = 무엇이 바뀌었는지 볼 값
     */
    private suspend fun scrollMoved(up: Boolean, page: DaangnPage, find: (List<UiNode>) -> Int?, key: (Snapshot) -> Any?): Snapshot? {
        driver.pause(random.nextLong(700, 1_400))
        val s = driver.current() ?: return null
        val i = find(s.nodes) ?: return null
        val before = key(s)
        if (!driver.scroll(s, i, up)) return null
        return waitFor(timeoutMs = SCROLL_WAIT_MS) { it.page == page && key(it) != before }
    }

    private suspend fun tab(s: Snapshot, label: String, page: DaangnPage): Snapshot {
        if (s.page == page) return s
        tap("아래 '$label' 탭") { DaangnNav.bottomTab(it, label) }
        return expect(page)
    }

    /** 화면 틀은 먼저 뜨고 내용(공고 카드·채팅 줄 등)은 뒤에 뜨는 화면: 내용이 뜰 때까지 기다림. 끝내 안 뜨면 그때 화면 */
    private suspend fun loaded(page: DaangnPage, ready: (Snapshot) -> Boolean): Snapshot =
        waitFor(timeoutMs = LOAD_MS) { it.page == page && ready(it) } ?: expect(page)

    private suspend fun expect(page: DaangnPage): Snapshot =
        waitFor { it.page == page } ?: throw Stop("'${page.label}' 화면이 안 떠서 멈췄어요 (지금: ${describe()})")

    /**
     * 조건에 맞는 당근 화면이 뜰 때까지 기다림. 그동안 멈춤 글자가 보이면 멈춤,
     * 당근이 아닌 화면이 awayMs 넘게 이어지면(사람이 홈을 눌렀거나 다른 앱이 뜸) 멈춤
     */
    private suspend fun waitFor(timeoutMs: Long = WAIT_MS, awayMs: Long = AWAY_MS, ok: (Snapshot) -> Boolean): Snapshot? {
        val start = driver.clock()
        var awaySince = -1L
        while (driver.clock() - start < timeoutMs) {
            val s = driver.current()
            if (s == null) {
                if (awaySince < 0) awaySince = driver.clock()
                if (driver.clock() - awaySince > awayMs) throw Stop("당근 화면을 벗어나서 멈췄어요")
            } else {
                awaySince = -1L
                DaangnNav.stopWord(s.nodes)?.let { throw Stop("'$it' 글자가 보여서 멈췄어요") }
                if (ok(s)) return s
            }
            driver.pause(POLL_MS)
        }
        return null
    }

    private fun describe(): String {
        val s = driver.current() ?: return "당근 밖"
        return s.page.label + " · " + s.nodes.map { it.text.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(" / ")
    }

    private fun rowKeys(s: Snapshot) = DaangnNav.chatRows(s.nodes).map { "${it.name}|${it.area}" }

    private fun note(line: String) {
        log.add(line)
        say(line)
    }

    companion object {
        const val MAX_CLICKS = 80     // 한 번에 누르는 횟수 한도
        const val MAX_ROOMS = 20      // 한 번에 여는 채팅방 수 한도
        const val MAX_LIST_PAGES = 10 // 채팅 목록을 아래로 넘기는 횟수 한도
        const val MAX_ROOM_PAGES = 20 // 채팅방 하나를 위로 올리는 횟수 한도
        const val WAIT_MS = 12_000L   // 화면이 뜨기를 기다리는 시간
        const val SCROLL_WAIT_MS = 2_500L // 스크롤 뒤 화면이 바뀌기를 기다리는 시간 (안 바뀌면 끝)
        const val LOAD_MS = 8_000L        // 화면 틀이 뜬 뒤 내용(공고 카드·채팅 줄 등)이 뜨기를 기다리는 시간
        const val AWAY_MS = 3_000L    // 당근을 벗어난 채로 기다려 주는 시간
        const val POLL_MS = 400L
    }
}
