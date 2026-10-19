package com.kangsam.daangnrecruit

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kangsam.daangnrecruit.ui.AppRoot
import com.kangsam.daangnrecruit.ui.DbErrorScreen
import com.kangsam.daangnrecruit.ui.theme.KangsamDaangnRecruitTheme
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 설정 화면 등에서 돌아올 때마다 숫자를 올려서, 화면이 권한 상태와 기록을 다시 읽게 함
    private var refreshTick by mutableIntStateOf(0)

    // 앱 알림 권한 (안드로이드 13부터 — 전체 멈춤·기록 문제 알림, AppAlerts). 답은 따로 안 씀 (거절하면 알림 없이 앱 안 안내만)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }

    /** 첫 화면에서 알림 권한을 한 번만 물음 (거절했으면 다시 안 물음 — 폰 설정에서 켬) */
    private fun askNotificationsOnce() {
        if (!AppAlerts.needsPermission(this)) return
        val prefs = getSharedPreferences("alerts", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 화면은 밝은 바탕 하나라서(화면 시안), 폰 상태 표시줄·아래 막대 글자를 늘 어둡게
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            KangsamDaangnRecruitTheme {
                // DB를 먼저 열어 봄 (DbOpen — 업데이트 뒤 사본·표 구조 옮기기). 못 열면 탭 대신 안내 한 장
                var dbState by remember { mutableStateOf<DbOpen.State?>(null) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    dbState = DbOpen.check(applicationContext)
                    DbOpen.db(applicationContext) // 열렸으면 하루 사본을 챙김
                    askNotificationsOnce()
                }
                when (val state = dbState) {
                    null -> Box(Modifier.fillMaxSize().background(Palette.Bg)) // 여는 동안 (보통 눈에 안 띄게 짧음)
                    DbOpen.State.Ok -> AppRoot(refreshTick = refreshTick)
                    is DbOpen.State.Failed -> DbErrorScreen(
                        failed = state,
                        backups = remember(state) { DbOpen.backups(applicationContext) },
                        onRetry = { scope.launch { dbState = DbOpen.retry(applicationContext) } },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }
}
