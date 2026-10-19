package com.kangsam.daangnrecruit

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/**
 * DB를 여는 문 (DB 설계 '폰 안 사본·표 구조가 바뀔 때', 사용자 프로세스 '예외 처리' — 업데이트 뒤 못 옮김).
 * 앱(프로세스)이 켜진 뒤 처음 한 번:
 *  1. 앱이 새로 깔렸으면(설치 시각이 바뀜) DB를 열기 전에 파일 그대로 사본 — 표 구조를 옮기기 전 기록
 *  2. DB를 열어 봄 — 표 구조가 바뀌었으면 여기서 옮김 (Migrations)
 *  3. 못 열면 지우지 않고 전체 멈춤을 켜고 이유를 앱 설정에 남김 → 첫 화면은 안내 한 장(MainActivity), 서비스는 DB를 안 씀
 * 그 뒤 하루 한 번 열린 DB에서 사본. 화면은 MainActivity가 여기서 '열림'을 받은 뒤에만 보임
 * 서비스·뒤에서 도는 일은 AppDatabase.get() 대신 db()로 받음 (못 열었으면 null)
 */
object DbOpen {
    sealed interface State {
        data object Ok : State
        data class Failed(val at: Long, val error: String) : State
    }

    private const val TAG = "DbOpen"
    private const val PREFS = "db_state"
    private const val KEY_BACKUP_INSTALL = "backup_install" // (10/19판) 사본을 남긴 설치 시각 — 10/20부터 KEY_BACKUP_KEY
    private const val KEY_BACKUP_KEY = "backup_key" // 사본을 남긴 설치 (설치 시각|APK 경로|DB 버전 — 같으면 이번 설치는 이미 남김)
    private const val KEY_FAILED_AT = "failed_at"
    private const val KEY_ERROR = "error"
    private const val DAILY_RETRY_MS = 60 * 60_000L // 하루 사본이 실패하면 1시간 뒤 다시

    private val openLock = Mutex()
    private val backupLock = Mutex()

    @Volatile
    private var state: State? = null

    /** 못 열었을 때 알릴 곳 (폰: KangsamApp이 AppAlerts.dbFailed를 넣음) */
    @Volatile
    var onFailed: ((Context) -> Unit)? = null

    @Volatile
    private var lastDailyTry = 0L

    @Volatile
    private var dailyDoneDay = "" // 하루 사본이 있는 날 (yyyyMMdd) — 같은 날엔 폴더를 다시 안 봄

    /** 처음 한 번 열어 보고 결과를 기억 (그 뒤엔 바로 돌려줌) */
    suspend fun check(context: Context): State {
        state?.let { return it }
        val app = context.applicationContext
        return openLock.withLock {
            state ?: withContext(Dispatchers.IO) { open(app) }.also { state = it }
        }
    }

    /** DB를 쓸 수 있으면 돌려줌, 못 열었으면 null. 하루 사본도 여기서 챙김 */
    suspend fun db(context: Context): AppDatabase? {
        val app = context.applicationContext
        if (check(app) != State.Ok) return null
        val db = AppDatabase.get(app)
        dailyIfDue(app, db)
        return db
    }

    /** 안내 화면의 '다시 열어 보기' */
    suspend fun retry(context: Context): State {
        openLock.withLock { state = null }
        return check(context)
    }

    /** 폰 안 사본 목록 (최근 것부터 — 설정 'DB'·안내 화면) */
    fun backups(context: Context): List<DbBackup.Copy> = DbBackup.list(backupDir(context.applicationContext))

    private suspend fun open(app: Context): State {
        val now = System.currentTimeMillis()
        val backupError = runCatching { backupBeforeOpen(app, now) }.exceptionOrNull()
        if (backupError != null) Log.e(TAG, "업데이트 뒤 사본 실패 (DB는 그대로 엶)", backupError)
        val error = runCatching { AppDatabase.get(app).openHelper.writableDatabase }.exceptionOrNull()
        if (error == null) {
            prefs(app).edit().remove(KEY_FAILED_AT).remove(KEY_ERROR).apply()
            if (backupError != null) logError(app, "DB 사본 (업데이트 뒤)", backupError)
            return State.Ok
        }
        // 못 엶: 지우지 않음. 전체 멈춤을 켜고 이유를 남김 (사람이 보고 고친 앱을 깔거나 사본으로 되살림)
        Log.e(TAG, "DB를 못 엶 — 지우지 않고 전체 멈춤", error)
        AppDatabase.reset()
        FeatureSettings.set(app, FeatureSettings.ALL_STOP, true)
        val text = describe(error)
        prefs(app).edit().putLong(KEY_FAILED_AT, now).putString(KEY_ERROR, text).apply()
        runCatching { onFailed?.invoke(app) }
        return State.Failed(now, text)
    }

    /**
     * 이번 설치에서 처음 열기 전이면 DB 파일 그대로 사본 (처음 깐 앱이면 DB가 없어 안 남김).
     * '이번 설치' = 설치(업데이트) 시각 + APK 경로 + 앱의 표 구조 버전. Android Studio로 코드만 바꿔 다시 깔면 설치 시각이
     * 그대로일 때가 있어서(10/20 운영폰 — 01:15에 깐 앱이 00:39 설치로 보여 사본이 안 생김) 설치마다 바뀌는 APK 경로와
     * 표 구조를 옮길 때 바뀌는 버전도 봄 — 옮기기 전 사본이 빠지지 않게
     */
    private fun backupBeforeOpen(app: Context, now: Long) {
        val key = installKey(app)
        val p = prefs(app)
        if (p.getString(KEY_BACKUP_KEY, null) == key) return
        DbBackup.copyClosed(app.getDatabasePath(AppDatabase.FILE_NAME), backupDir(app), now, ZoneId.systemDefault())
        p.edit().putString(KEY_BACKUP_KEY, key).remove(KEY_BACKUP_INSTALL).apply()
    }

    /** 오늘 사본이 없으면 열린 DB에서 하나 (다른 일이 사본 중이면 넘어감, 실패하면 1시간 뒤 다시) */
    private suspend fun dailyIfDue(app: Context, db: AppDatabase) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = DbBackup.stamp(now, zone).take(8)
        if (dailyDoneDay == today || now - lastDailyTry < DAILY_RETRY_MS) return
        if (!backupLock.tryLock()) return
        try {
            val dir = backupDir(app)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (DbBackup.dailyDue(dir, now, zone)) {
                        lastDailyTry = now
                        val file = DbBackup.copyOpen(db.openHelper.writableDatabase, app.getDatabasePath(AppDatabase.FILE_NAME), dir, now, zone)
                        Log.i(TAG, "하루 사본: ${file.name}")
                    }
                }
            }
            result.onSuccess { dailyDoneDay = today }.onFailure { logError(app, "DB 사본 (하루 한 번)", it) }
        } finally {
            backupLock.unlock()
        }
    }

    /** 이번 설치를 알아보는 값: 설치(업데이트) 시각 | APK 경로 | 표 구조 버전 */
    private fun installKey(app: Context): String {
        val pm = app.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(app.packageName, 0)
        }
        return "${info.lastUpdateTime}|${info.applicationInfo?.sourceDir.orEmpty()}|${AppDatabase.VERSION}"
    }

    private suspend fun logError(app: Context, place: String, e: Throwable) {
        Log.e(TAG, "$place 오류", e)
        runCatching {
            AppDatabase.get(app).errorLogDao().insert(ErrorLog(time = System.currentTimeMillis(), place = place, message = describe(e)))
        }
    }

    /** 오류 한 줄 (원인까지 — Room은 '옮기기 실패'를 안쪽 원인에 담음) */
    private fun describe(e: Throwable): String {
        val parts = generateSequence(e) { it.cause }.take(3).map { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }.distinct()
        return parts.joinToString(" ← ").take(600)
    }

    private fun backupDir(app: Context) = File(app.filesDir, DbBackup.DIR)

    private fun prefs(app: Context) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
