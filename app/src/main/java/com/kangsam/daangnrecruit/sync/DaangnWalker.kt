package com.kangsam.daangnrecruit.sync

import kotlin.random.Random

/**
 * 당근 화면을 옮겨 다니는 손: 당근 열기, 아래 탭 화면까지 뒤로, 칸 누르기, 뒤로, 스크롤, 화면 기다리기, 바로 멈춤.
 * 당근 가져오기(SyncRunner, 프로세스 C)와 첫 메시지 드라이런(reply 폴더, 프로세스 B)이 같이 씀.
 * 무엇을 누를지는 DaangnNav가 고르고, 누르기 직전에 그때 화면에서 칸을 다시 찾아 isSafeToClick으로 한 번 더 봄.
 *
 * 지키는 것: 누르기 전 사람처럼 1.2~2.8초 쉼, 한 번에 누르는 횟수 한도, 기다리던 화면이 안 뜨거나 당근을 벗어나거나
 * 보안 인증·이용 제한 글자가 보이면 바로 멈춤(Stop)
 */
class DaangnWalker(
    val driver: ScreenDriver,
    private val note: (String) -> Unit,
    private val random: Random = Random.Default,
) {
    /**
     * 바로 멈춤 (이유는 메시지). left = 당근을 벗어남 (사람이 홈을 눌렀거나 다른 앱이 뜸 — 멈추는 방법이라 다시 하지 않음),
     * alarm = 멈춤 글자(보안 인증·이용 제한 등)가 보임 (즉시 중단 — 실제 발송 시험은 시험을 끔)
     */
    class Stop(message: String, val left: Boolean = false, val alarm: Boolean = false) : Exception(message)

    private var clicks = 0

    /** 마지막 pageRows에서 넘기기 동작을 몇 번 했는지 (개발용 기록) */
    var lastNudges = 0
        private set

    /** 당근 열기 */
    suspend fun open() {
        note("당근 열기")
        if (!driver.openDaangn()) throw Stop("당근 앱을 못 열었어요")
        waitFor(timeoutMs = 15_000, awayMs = 15_000) { true } ?: throw Stop("당근이 안 열렸어요")
    }

    /** 아래 탭(홈·채팅·나의 당근…)이 있는 첫 화면까지 뒤로 */
    suspend fun toTabRoot(): Snapshot {
        for (i in 0 until 6) {
            waitFor(timeoutMs = 5_000) { DaangnNav.hasBottomNav(it.nodes) }?.let { return it }
            back("아래 탭이 있는 첫 화면으로")
        }
        throw Stop("아래 탭이 있는 첫 화면으로 못 돌아갔어요")
    }

    /**
     * 사람처럼 쉬었다가, 그때 화면에서 칸을 다시 찾아 안전한지 보고 누름.
     * safe = 눌러도 되는지 (기본은 DaangnNav — 화면을 옮기는 칸만. 실제 발송 시험은 reply/LiveNav의 확인을 따로 넘김)
     */
    suspend fun tap(what: String, safe: (List<UiNode>, Int) -> Boolean = DaangnNav::isSafeToClick, find: (List<UiNode>) -> Int?) {
        if (++clicks > MAX_CLICKS) throw Stop("한 번에 누르는 횟수(${MAX_CLICKS}번)를 넘어서 멈췄어요")
        driver.pause(random.nextLong(1_200, 2_800))
        val s = driver.current() ?: throw Stop("당근 화면을 벗어나서 멈췄어요", left = true)
        DaangnNav.stopWord(s.nodes)?.let { throw Stop("'$it' 글자가 보여서 멈췄어요", alarm = true) }
        val i = find(s.nodes) ?: throw Stop("누를 칸을 못 찾아서 멈췄어요: $what")
        if (!safe(s.nodes, i)) throw Stop("누르면 안 되는 칸이라 멈췄어요: $what")
        if (!driver.click(s, i)) throw Stop("누르기가 안 돼서 멈췄어요: $what")
        note("누름: $what")
    }

    suspend fun back(what: String) {
        driver.pause(random.nextLong(900, 1_800))
        driver.back()
        note("뒤로: $what")
    }

    /**
     * 스크롤하고 화면이 바뀔 때까지 기다림 (스크롤이 끝나기 전에 읽어 '끝'으로 잘못 알지 않게).
     * 바뀐 뒤에도 멈출 때까지 기다렸다가 돌려줌 (settle). 안 움직였으면(목록 끝·맨 위) null. key = 무엇이 바뀌었는지 볼 값
     */
    suspend fun scrollMoved(up: Boolean, page: DaangnPage, find: (List<UiNode>) -> Int?, key: (Snapshot) -> Any?): Snapshot? {
        driver.pause(random.nextLong(700, 1_400))
        val s = driver.current() ?: return null
        val i = find(s.nodes) ?: return null
        val before = key(s)
        if (!driver.scroll(s, i, up)) return null
        val moved = waitFor(timeoutMs = SCROLL_WAIT_MS) { it.page == page && key(it) != before } ?: return null
        return settle(page, moved)
    }

    /**
     * 화면이 멈출 때까지 기다림: 글자와 위치가 SETTLE_POLL_MS 간격으로 두 번 연달아 같으면 멈춘 것 (SETTLE_MS까지).
     * 당근 목록은 넘기기 동작이 애니메이션으로 움직여서, 바뀌자마자 읽으면 움직이는 도중의 화면이고 그 뒤에 지나간 줄은 못 봄
     * (10/18 밤 폰: 읽은 방 2개를 건너뜀 — 원인으로 봄). 멈춘 화면을 돌려주고, 끝내 안 멈추면 마지막에 본 화면
     */
    suspend fun settle(page: DaangnPage, first: Snapshot): Snapshot {
        var last = first
        var sig = still(first)
        val start = driver.clock()
        while (driver.clock() - start < SETTLE_MS) {
            driver.pause(SETTLE_POLL_MS)
            val s = driver.current() ?: return last
            DaangnNav.stopWord(s.nodes)?.let { throw Stop("'$it' 글자가 보여서 멈췄어요", alarm = true) }
            if (s.page != page) return last
            val g = still(s)
            if (g == sig) return s
            last = s
            sig = g
        }
        return last
    }

    /** 화면이 멈췄는지 볼 값: 글자가 있는 칸의 글자와 위치 */
    private fun still(s: Snapshot): List<String> =
        s.nodes.filter { it.text.isNotBlank() }.map { "${it.text}@${it.left},${it.top}" }

    /**
     * 줄 목록(채팅 목록) 한 화면 넘기기. 당근 채팅 목록은 한 번 넘겨도 1~2줄만 움직여서(10/18 폰: 10화면에 새 방 20개),
     * 새 줄이 MIN_NEW_ROWS개 보일 때까지 NUDGES번까지 이어서 넘김 (한 번에 많이 움직이는 목록이면 한 번으로 끝).
     * 넘길 때마다 목록이 멈출 때까지 기다렸다가 봄 (settle — 10/18 밤: 1~2줄씩 움직인 것처럼 보인 건 움직이는 도중에 읽어서였을 수 있음).
     * rows = 화면에 보이는 줄 이름들. 움직인 화면을 돌려주고, 처음부터 안 움직이면(맨 위·맨 끝) null
     */
    suspend fun pageRows(up: Boolean, page: DaangnPage, find: (List<UiNode>) -> Int?, rows: (Snapshot) -> List<String>): Snapshot? {
        driver.pause(random.nextLong(700, 1_400))
        var cur = driver.current() ?: return null
        val before = rows(cur).toSet()
        var moved: Snapshot? = null
        lastNudges = 0
        for (i in 0 until NUDGES) {
            if (i > 0) driver.pause(random.nextLong(250, 600))
            val idx = find(cur.nodes) ?: break
            val now = rows(cur)
            if (!driver.scroll(cur, idx, up)) break
            lastNudges++
            cur = waitFor(timeoutMs = SCROLL_WAIT_MS) { it.page == page && rows(it) != now } ?: break
            cur = settle(page, cur)
            moved = cur
            if (rows(cur).count { it !in before } >= MIN_NEW_ROWS) break
        }
        return moved
    }

    /**
     * 목록 맨 위까지 올림 — 당근은 전에 내려 둔 자리를 기억해서(10/18 폰: 채팅 목록이 '2달 전' 방들에서 시작), 안 움직일 때까지.
     * 맨 위 화면을 돌려줌 (이미 맨 위였으면 null)
     */
    suspend fun scrollToTop(page: DaangnPage, find: (List<UiNode>) -> Int?, rows: (Snapshot) -> List<String>): Snapshot? {
        var top: Snapshot? = null
        var n = 0
        while (n < MAX_UP_PAGES) {
            top = pageRows(up = true, page, find, rows) ?: break
            n++
        }
        note("맨 위로: ${n}화면 올림" + if (n >= MAX_UP_PAGES) " (한도라 맨 위가 아닐 수 있음)" else "")
        return top
    }

    /**
     * 아래로 한 화면 — 끝처럼 보이면 잠깐 기다렸다가 한 번 더 넘겨 봄 (끝에 닿아야 다음 묶음을 불러오는 목록일 때).
     * 그래도 안 바뀌면 끝(null)
     */
    suspend fun scrollDown(page: DaangnPage, find: (List<UiNode>) -> Int?, rows: (Snapshot) -> List<String>): Snapshot? {
        pageRows(up = false, page, find, rows)?.let { return it }
        driver.pause(MORE_WAIT_MS)
        return pageRows(up = false, page, find, rows)
    }

    /** 아래 탭으로 그 화면에 감 (이미 그 화면이면 그대로) */
    suspend fun tab(s: Snapshot, label: String, page: DaangnPage): Snapshot {
        if (s.page == page) return s
        tap("아래 '$label' 탭") { DaangnNav.bottomTab(it, label) }
        return expect(page)
    }

    /** 화면 틀은 먼저 뜨고 내용(공고 카드·채팅 줄 등)은 뒤에 뜨는 화면: 내용이 뜰 때까지 기다림. 끝내 안 뜨면 그때 화면 */
    suspend fun loaded(page: DaangnPage, ready: (Snapshot) -> Boolean): Snapshot =
        waitFor(timeoutMs = LOAD_MS) { it.page == page && ready(it) } ?: expect(page)

    suspend fun expect(page: DaangnPage): Snapshot =
        waitFor { it.page == page } ?: throw Stop("'${page.label}' 화면이 안 떠서 멈췄어요 (지금: ${describe()})")

    /**
     * 조건에 맞는 당근 화면이 뜰 때까지 기다림. 그동안 멈춤 글자가 보이면 멈춤,
     * 당근이 아닌 화면이 awayMs 넘게 이어지면(사람이 홈을 눌렀거나 다른 앱이 뜸) 멈춤
     */
    suspend fun waitFor(timeoutMs: Long = WAIT_MS, awayMs: Long = AWAY_MS, ok: (Snapshot) -> Boolean): Snapshot? {
        val start = driver.clock()
        var awaySince = -1L
        while (driver.clock() - start < timeoutMs) {
            val s = driver.current()
            if (s == null) {
                if (awaySince < 0) awaySince = driver.clock()
                if (driver.clock() - awaySince > awayMs) throw Stop("당근 화면을 벗어나서 멈췄어요", left = true)
            } else {
                awaySince = -1L
                DaangnNav.stopWord(s.nodes)?.let { throw Stop("'$it' 글자가 보여서 멈췄어요", alarm = true) }
                if (ok(s)) return s
            }
            driver.pause(POLL_MS)
        }
        return null
    }

    /** 지금 화면 한 줄 (멈춘 이유에 붙임) */
    fun describe(): String {
        val s = driver.current() ?: return "당근 밖"
        return s.page.label + " · " + s.nodes.map { it.text.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(" / ")
    }

    /**
     * 개발용: 앱이 본 화면을 폰 안(앱 전용 폴더 files/debug/이름.xml)에 남기고 기록에 칸 수를 적음.
     * 화면이 덤프와 다르게 보일 때 원인을 보려고. 실패해도 하던 일은 그대로
     */
    fun keepScreen(name: String, s: Snapshot? = null) {
        val shot = s ?: runCatching { driver.current() }.getOrNull() ?: return
        runCatching { driver.saveScreen(shot, name) }
        note("화면 저장 (개발용): $name.xml — ${UiTree.shape(shot.nodes)}")
    }

    companion object {
        const val MAX_CLICKS = 80     // 한 번에 누르는 횟수 한도
        const val WAIT_MS = 12_000L   // 화면이 뜨기를 기다리는 시간
        const val SCROLL_WAIT_MS = 2_500L // 스크롤 뒤 화면이 바뀌기를 기다리는 시간 (안 바뀌면 끝)
        const val SETTLE_MS = 3_000L      // 스크롤로 움직이기 시작한 화면이 멈추기를 기다리는 시간
        const val SETTLE_POLL_MS = 300L   // 멈췄는지 보는 간격 (두 번 연달아 같으면 멈춘 것)
        const val MORE_WAIT_MS = 3_000L   // 목록 끝처럼 보일 때 다음 묶음이 뜨기를 기다렸다가 한 번 더 넘겨 봄
        const val MAX_UP_PAGES = 40       // 목록 맨 위로 올릴 때 화면 수 한도
        const val NUDGES = 5              // 한 화면 넘길 때 넘기기 동작을 이어서 하는 횟수 한도
        const val MIN_NEW_ROWS = 4        // 한 화면 넘겼다고 보는 새 줄 수
        const val LOAD_MS = 8_000L        // 화면 틀이 뜬 뒤 내용(공고 카드·채팅 줄 등)이 뜨기를 기다리는 시간
        const val AWAY_MS = 3_000L    // 당근을 벗어난 채로 기다려 주는 시간
        const val POLL_MS = 400L
    }
}
