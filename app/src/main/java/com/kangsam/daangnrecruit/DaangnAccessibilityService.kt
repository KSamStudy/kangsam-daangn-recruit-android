package com.kangsam.daangnrecruit

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 당근 화면을 읽기만 하는 접근성 서비스 (개발 5단계).
 * 지금은 '내 공고' 상세 화면(아래 '지원자 보기' 버튼이 있는 화면)만 읽어서, 그 공고를 공고 탭에 맞춤 (PostActions.importFromScreen).
 * 누르기·입력·스크롤 같은 동작은 하지 않고, 다른 화면의 글자는 저장하지 않음.
 * 폰 설정 → 접근성에서 이 앱을 켜고, 앱의 '당근 화면 읽기'도 켰을 때만 동작 (기본 꺼짐).
 */
class DaangnAccessibilityService : AccessibilityService() {

    // DB 쓰기는 화면(메인) 말고 뒤에서 함
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val readLater = Runnable { readScreen() }
    private var lastSignature = ""
    private var lastReadAt = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != DAANGN) return
        if (!ScreenRead.isEnabled(this)) return
        // 화면이 바뀌는 동안 이벤트가 여러 번 오므로, 잠잠해지고 1초 뒤에 한 번만 읽음
        handler.removeCallbacks(readLater)
        handler.postDelayed(readLater, READ_DELAY_MS)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(readLater)
        scope.cancel()
        super.onDestroy()
    }

    private fun readScreen() {
        val post = runCatching { readMyPost() }
            .onFailure { saveError("화면 읽기", it) }
            .getOrNull() ?: return

        // 같은 화면을 1분 안에 다시 읽은 거면 넘어감
        val now = System.currentTimeMillis()
        val signature = post.toString()
        if (signature == lastSignature && now - lastReadAt < SAME_SCREEN_MS) return
        lastSignature = signature
        lastReadAt = now

        scope.launch {
            runCatching { PostActions.importFromScreen(AppDatabase.get(applicationContext), post) }
                .onSuccess { message ->
                    ScreenRead.saveLast(applicationContext, message)
                    Log.i(TAG, message)
                }
                .onFailure { saveError("공고 가져오기", it) }
        }
    }

    /** 지금 화면이 당근 '내 공고' 상세면 그 공고, 아니면 null */
    private fun readMyPost(): ScreenPost? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != DAANGN) return null
        val nodes = ArrayList<ScreenNode>()
        collect(root, nodes, 0)
        return MyPostParser.parse(nodes)
    }

    /** 화면을 위에서부터 차례대로 펼침 (부모 먼저) — uiautomator 덤프와 같은 순서 */
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenNode>, depth: Int) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        out.add(ScreenNode(node.className?.toString().orEmpty(), node.text?.toString().orEmpty()))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private fun saveError(place: String, e: Throwable) {
        Log.e(TAG, "$place 오류", e)
        scope.launch {
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
    }
}

/** '당근 화면 읽기' 켜기·끄기(기본 꺼짐)와 마지막 결과 한 줄 (설정 → 알림 기록 (개발용)에 보임) */
object ScreenRead {
    private const val PREFS = "screen_read"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST = "last"
    private const val KEY_LAST_AT = "last_at"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun saveLast(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST, message)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    /** 마지막 결과 (시각, 한 줄). 아직 없으면 null */
    fun last(context: Context): Pair<Long, String>? {
        val p = prefs(context)
        val message = p.getString(KEY_LAST, null) ?: return null
        return p.getLong(KEY_LAST_AT, 0L) to message
    }

    /** 폰 설정 → 접근성에서 이 앱의 '당근 화면 읽기'가 켜져 있는지 */
    fun isServiceOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, DaangnAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
