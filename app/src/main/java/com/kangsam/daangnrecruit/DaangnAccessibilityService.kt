package com.kangsam.daangnrecruit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.kangsam.daangnrecruit.sync.DaangnSync
import com.kangsam.daangnrecruit.sync.ScreenDriver
import com.kangsam.daangnrecruit.sync.ScreenImport
import com.kangsam.daangnrecruit.sync.Snapshot
import com.kangsam.daangnrecruit.sync.SyncRunner
import com.kangsam.daangnrecruit.sync.UiNode
import com.kangsam.daangnrecruit.sync.UiTree
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 접근성 서비스 = '당근 가져오기'(프로세스 C, sync 폴더)의 손. 이 서비스는 화면을 읽고 누르는 일만 빌려주고,
 * 무엇을 읽고 언제 누를지는 sync 폴더의 코드가 정함.
 * - 사람이 당근 화면을 열면: 기능이 켜져 있을 때 그 화면을 읽어 앱에 맞춤 (공고 상세·지원자 목록·채팅방)
 * - 설정의 '당근에서 가져오기': SyncRunner가 이 손으로 당근을 돌며 가져옴 (도는 동안 화면 아래에 '가져오는 중' 띠, 화면 안 꺼짐)
 * 당근 앱 화면만 받음(packageNames). 폰 설정 → 접근성에서 켜야 동작.
 */
class DaangnAccessibilityService : AccessibilityService(), ScreenDriver {

    // DB 쓰기는 뒤에서, 가져오기 실행은 화면(메인) 차례로
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val readLater = Runnable { readScreen() }
    private var lastSignature = ""
    private var lastReadAt = 0L
    private var banner: TextView? = null

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "접근성 서비스 연결됨")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        hideBanner()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        hideBanner()
        handler.removeCallbacks(readLater)
        ioScope.cancel()
        mainScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ---------- 사람이 연 화면 읽기

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != DAANGN) return
        if (!DaangnSync.canRun(this) || DaangnSync.running) return
        // 화면이 바뀌는 동안 이벤트가 여러 번 오므로, 잠잠해지고 1초 뒤에 한 번만 읽음
        handler.removeCallbacks(readLater)
        handler.postDelayed(readLater, READ_DELAY_MS)
    }

    private fun readScreen() {
        val screen = runCatching { current()?.let { ScreenImport.read(it.nodes) } }
            .onFailure { saveError("화면 읽기", it) }
            .getOrNull() ?: return
        // 같은 화면을 1분 안에 다시 읽은 거면 넘어감
        val now = System.currentTimeMillis()
        val signature = screen.toString()
        if (signature == lastSignature && now - lastReadAt < SAME_SCREEN_MS) return
        lastSignature = signature
        lastReadAt = now
        ioScope.launch {
            runCatching { ScreenImport.save(AppDatabase.get(applicationContext), screen, ZoneId.systemDefault()) }
                .onSuccess { message ->
                    DaangnSync.saveLast(applicationContext, message)
                    Log.i(TAG, message)
                }
                .onFailure { saveError("화면에서 가져오기", it) }
        }
    }

    // ---------- '당근에서 가져오기' 버튼 (DaangnSync.start가 부름)

    fun runSync(onDone: (String, List<String>) -> Unit) {
        mainScope.launch {
            showBanner()
            val runner = SyncRunner(this@DaangnAccessibilityService, AppDatabase.get(applicationContext), ZoneId.systemDefault(),
                say = { line ->
                    DaangnSync.say(line)
                    banner?.text = "$BANNER\n$line"
                })
            val result = runCatching { runner.run() }.getOrElse { e ->
                saveError("당근에서 가져오기", e)
                openApp()
                "오류로 멈춤: ${e.message ?: e.javaClass.simpleName}"
            }
            hideBanner()
            onDone(result, runner.log)
        }
    }

    /**
     * 가져오는 동안 화면 아래쪽에 띠를 띄움: 앱이 당근을 다루는 중임을 보여 주고, 도는 동안 화면이 꺼지지 않게 함 (FLAG_KEEP_SCREEN_ON).
     * 접근성 서비스만 띄울 수 있는 칸이라 다른 권한이 필요 없고, 손가락 터치는 그대로 아래로 지나감
     */
    private fun showBanner() {
        if (banner != null) return
        val view = TextView(this).apply {
            text = BANNER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD333333.toInt())
            textSize = 13f
            setPadding(36, 20, 36, 20)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 320
        }
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params) }
            .onSuccess { banner = view }
            .onFailure { saveError("가져오기 띠", it) }
    }

    private fun hideBanner() {
        val view = banner ?: return
        banner = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    // ---------- ScreenDriver: 가져오기가 쓰는 손

    override fun current(): Snapshot? {
        // 가져오는 동안에는 접근성 칸 기억(캐시)을 비우고 매번 새로 읽음: 화면이 한꺼번에 바뀔 때 '바뀜' 알림 일부가 빠지면
        // 옛 칸이 남아 새로 뜬 내용을 못 볼 수 있어서 (10/18 채팅 목록 의심 원인 중 하나). 안드로이드 13부터 됨
        if (DaangnSync.running && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) clearCache()
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != DAANGN) return null
        return snapshotOf(root)
    }

    override fun openDaangn(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(DAANGN) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { startActivity(intent) }.isSuccess
    }

    override fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
    }

    override fun click(s: Snapshot, index: Int): Boolean = refs(s).getOrNull(index)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

    /**
     * 화면 방향(위·아래)으로 스크롤. 채팅방처럼 거꾸로 쌓는 목록에서는 '앞·뒤' 동작의 방향이 반대라,
     * 위·아래 동작이 있는 칸이면 그것만 씀 (그 방향 동작이 없으면 끝까지 간 것). 위·아래 동작이 아예 없는 칸만 앞·뒤로
     */
    override fun scroll(s: Snapshot, index: Int, up: Boolean): Boolean {
        val node = refs(s).getOrNull(index) ?: return false
        val upAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val downAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        val actions = node.actionList.map { it.id }
        val want = if (up) upAction else downAction
        if (want in actions) return node.performAction(want)
        if (upAction in actions || downAction in actions) return false
        return node.performAction(if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    override fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override suspend fun pause(ms: Long) = delay(ms)

    override fun clock(): Long = SystemClock.uptimeMillis()

    /**
     * 개발용: 앱이 본 화면을 앱 전용 폴더(files/debug/이름.xml)에 uiautomator 덤프 모양으로 남김 (덮어씀).
     * 칸마다 누르기·스크롤 동작 목록(actions)도 적음. 맥으로 옮기기:
     * adb exec-out run-as com.kangsam.daangnrecruit cat files/debug/이름.xml > ~/Desktop/이름.xml
     */
    override fun saveScreen(s: Snapshot, name: String) {
        val refs = refs(s)
        val xml = UiTree.toXml(s.nodes) { i ->
            val actions = refs.getOrNull(i)?.actionList.orEmpty().mapNotNull { actionName(it.id) }
            if (actions.isEmpty()) emptyMap() else mapOf("actions" to actions.joinToString(" "))
        }
        ioScope.launch {
            runCatching {
                val dir = File(filesDir, "debug").apply { mkdirs() }
                File(dir, "$name.xml").writeText(xml)
            }.onFailure { saveError("화면 저장 (개발용)", it) }
        }
    }

    /** 화면 저장에 적을 동작 이름 (누르기·스크롤만) */
    private fun actionName(id: Int): String? = when (id) {
        AccessibilityNodeInfo.ACTION_CLICK -> "click"
        AccessibilityNodeInfo.ACTION_LONG_CLICK -> "longClick"
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "forward"
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "backward"
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> "up"
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> "down"
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id -> "left"
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id -> "right"
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun refs(s: Snapshot): List<AccessibilityNodeInfo> = s.handle as? List<AccessibilityNodeInfo> ?: emptyList()

    /** 접근성 칸 나무를 위에서부터 펼침 (부모 먼저) — uiautomator 덤프와 같은 차례 */
    private fun snapshotOf(root: AccessibilityNodeInfo): Snapshot {
        val nodes = ArrayList<UiNode>()
        val refs = ArrayList<AccessibilityNodeInfo>()
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, parent: Int, depth: Int) {
            if (depth > MAX_DEPTH || nodes.size > MAX_NODES) return
            n.getBoundsInScreen(r)
            val index = nodes.size
            nodes.add(
                UiNode(
                    parent = parent, depth = depth,
                    cls = n.className?.toString().orEmpty(),
                    text = n.text?.toString().orEmpty(),
                    desc = n.contentDescription?.toString().orEmpty(),
                    viewId = n.viewIdResourceName.orEmpty(),
                    clickable = n.isClickable, longClickable = n.isLongClickable,
                    scrollable = n.isScrollable, selected = n.isSelected,
                    left = r.left, top = r.top, right = r.right, bottom = r.bottom,
                )
            )
            refs.add(n)
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, index, depth + 1)
            }
        }
        walk(root, -1, 0)
        return Snapshot(nodes, refs)
    }

    private fun saveError(place: String, e: Throwable) {
        Log.e(TAG, "$place 오류", e)
        ioScope.launch {
            runCatching {
                AppDatabase.get(applicationContext).errorLogDao().insert(
                    ErrorLog(time = System.currentTimeMillis(), place = place, message = e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    companion object {
        private const val TAG = "DaangnScreen"
        private const val DAANGN = "com.towneers.www"
        private const val READ_DELAY_MS = 1_000L
        private const val SAME_SCREEN_MS = 60_000L
        private const val MAX_DEPTH = 100
        private const val MAX_NODES = 5_000
        private const val BANNER = "당근 가져오는 중 — 폰을 만지지 마세요 (멈추려면 홈 버튼)"

        /** 지금 켜져 있는 서비스 (꺼져 있으면 null) — '당근에서 가져오기' 버튼이 씀 */
        @Volatile
        var instance: DaangnAccessibilityService? = null
            private set
    }
}
