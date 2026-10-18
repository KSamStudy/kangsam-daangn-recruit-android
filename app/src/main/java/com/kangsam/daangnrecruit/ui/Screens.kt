package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.sync.DaangnSync

// 탭별 화면 (화면 설계 '화면별 기능'). 지원자 탭은 ApplicantsScreen.kt, 공고 탭은 PostsScreen.kt, 확인 탭은 CheckScreen.kt, 나머지는 하나씩 채우는 중

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "홈",
        description = "지금 상황 보기 — 기능 켜짐 여부, 오늘 건수, 최근 활동",
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(refreshTick: Int, modifier: Modifier = Modifier) {
    // 설정 안에서 '알림 기록 (개발용)' 화면을 열고 닫음
    var showNotificationLog by rememberSaveable { mutableStateOf(false) }

    if (showNotificationLog) {
        // 폰의 뒤로가기를 누르면 설정으로 돌아옴
        BackHandler { showNotificationLog = false }
        NotificationLogScreen(
            refreshTick = refreshTick,
            onBack = { showNotificationLog = false },
            modifier = modifier
        )
    } else {
        Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("설정", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("처음 정하는 값과 전체 멈춤", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            DaangnSyncSection(refreshTick)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { showNotificationLog = true }) {
                Text("알림 기록 (개발용)")
            }
        }
    }
}

/** 프로세스 C '당근 가져오기' — 따로 켜고 끄는 기능 (스위치·버튼·언제·지난 실행 기록) */
@Composable
private fun DaangnSyncSection(refreshTick: Int) {
    val context = LocalContext.current
    val status by DaangnSync.status.collectAsState()
    val serviceOn = remember(refreshTick) { DaangnSync.isServiceOn(context) }
    var on by remember(refreshTick) { mutableStateOf(DaangnSync.isOn(context)) }
    var mode by remember { mutableStateOf(DaangnSync.mode(context)) }
    var message by remember { mutableStateOf("") }
    var last by remember { mutableStateOf(DaangnSync.last(context)) }
    var runLog by remember { mutableStateOf(DaangnSync.lastLog(context)) }
    var showLog by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick, status) {
        last = DaangnSync.last(context)
        runLog = DaangnSync.lastLog(context)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("당근 가져오기", style = MaterialTheme.typography.titleMedium)
            Text(
                "당근 화면을 읽어 공고·지원자·대화를 앱에 맞춰요. 당근에 쓰는 일(보내기·올리기·마감)은 하지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = on, onCheckedChange = {
            on = it
            DaangnSync.setOn(context, it)
        })
    }
    if (!serviceOn) {
        Text(
            "접근성(당근 화면 읽기)이 꺼져 있어요 — 폰 설정 → 접근성 → 설치된 앱에서 켜요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { message = DaangnSync.start(context) },
        enabled = on && serviceOn && !DaangnSync.running,
        modifier = Modifier.fillMaxWidth()
    ) { Text("당근에서 가져오기") }
    Text(
        "당근을 열고 나의 당근 → 공고 → 지원자 보기 → 채팅 탭 → 지원자 방을 차례로 열어요. " +
            "누르는 건 화면을 옮기는 버튼뿐이고, 안 읽은 채팅방은 열지 않아요 (지원자가 새로 보내는 메시지는 알림으로 받아 대화에 남겨요). " +
                "화면 아래에 '가져오는 중' 띠가 떠 있는 동안 폰을 만지지 마세요 (멈추려면 홈 버튼).",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    if (DaangnSync.running && status.isNotBlank()) Text("지금: $status", style = MaterialTheme.typography.bodySmall)
    Text(
        "마지막: " + (last?.let { (time, text) -> "${formatTime(time)} $text" } ?: "아직 없음"),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "오늘 버튼으로 ${DaangnSync.runsToday(context)}번 · 하루 ${DaangnSync.MAX_RUNS_PER_DAY}번까지",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (runLog.isNotEmpty()) {
        TextButton(onClick = { showLog = !showLog }) {
            Text(if (showLog) "지난 실행 기록 접기" else "지난 실행 기록 보기 (${runLog.size}줄)")
        }
        if (showLog) runLog.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }

    Spacer(Modifier.height(12.dp))
    Text("언제 가져올지", style = MaterialTheme.typography.titleSmall)
    listOf(
        DaangnSync.MODE_BUTTON to true,
        DaangnSync.MODE_SCHEDULE to false,
        DaangnSync.MODE_NOTIFICATION to false,
    ).forEach { (option, ready) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().selectable(
                selected = mode == option,
                enabled = ready,
                onClick = {
                    mode = option
                    DaangnSync.setMode(context, option)
                }
            )
        ) {
            RadioButton(selected = mode == option, onClick = null, enabled = ready)
            Text(option + if (ready) "" else " (준비 중)", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        "켜져 있으면 사람이 당근 화면을 열 때도 그 화면을 읽어 맞춰요.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 아직 안 만든 화면에 쓰는 임시 화면: 제목과 무엇이 들어갈지만 보여줌 */
@Composable
private fun PlaceholderScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("준비 중", style = MaterialTheme.typography.labelMedium)
    }
}
