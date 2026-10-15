package com.kangsam.daangnrecruit.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.NotificationLog
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.SampleData
import com.kangsam.daangnrecruit.ScreenRead
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 설정 > 알림 기록 (개발용): 10/9에 만든 기록 화면을 옮김. 10/15 당근 화면 읽기(개발 5단계) 추가 */
@Composable
fun NotificationLogScreen(refreshTick: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    // 알림 접근 권한이 켜져 있는지
    val hasAccess = remember(refreshTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    // 알림 기록 ON/OFF (기본 OFF)
    var enabled by remember { mutableStateOf(NotificationLog.isEnabled(context)) }

    // 최근 기록
    var entries by remember { mutableStateOf(NotificationLog.readRecent(context)) }
    LaunchedEffect(refreshTick) {
        entries = NotificationLog.readRecent(context)
    }

    // 당근 화면 읽기: 폰 설정의 접근성 + 앱의 '당근 화면 읽기' 둘 다 켜져야 동작
    val screenServiceOn = remember(refreshTick) { ScreenRead.isServiceOn(context) }
    var screenReadOn by remember { mutableStateOf(ScreenRead.isEnabled(context)) }
    var lastRead by remember { mutableStateOf(ScreenRead.last(context)) }
    LaunchedEffect(refreshTick) {
        lastRead = ScreenRead.last(context)
    }

    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA) }

    // 내용이 길어져서 화면 전체를 스크롤
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 설정") }
        Text("알림 기록 (개발용)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // 1) 알림 접근 권한 상태
        Text(if (hasAccess) "알림 접근 권한: 허용됨" else "알림 접근 권한: 꺼져 있음")
        if (!hasAccess) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("알림 접근 설정 열기")
            }
        }
        Spacer(Modifier.height(16.dp))

        // 2) 알림 기록 ON/OFF
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("알림 기록", modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    NotificationLog.setEnabled(context, checked)
                }
            )
        }
        Text(
            "켜두면 당근 알림만 이 폰 안에 기록하고, 지원 알림이면 지원자 탭에 넣어요. 끄면 둘 다 멈춰요. 알림을 지우거나 누르지 않아요.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        // 3) 당근 화면 읽기 (개발 5단계) — '내 공고' 화면에서 공고 가져오기
        Text(if (screenServiceOn) "접근성 (당근 화면 읽기): 켜짐" else "접근성 (당근 화면 읽기): 꺼져 있음")
        if (!screenServiceOn) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("접근성 설정 열기")
            }
            Text("운영폰 설정을 바꾸는 거라 담당자 OK 뒤에 켜요", style = MaterialTheme.typography.labelSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("당근 화면 읽기", modifier = Modifier.weight(1f))
            Switch(
                checked = screenReadOn,
                onCheckedChange = { checked ->
                    screenReadOn = checked
                    ScreenRead.setEnabled(context, checked)
                }
            )
        }
        Text(
            "켜두면 당근에서 내 공고(아래에 '지원자 보기' 버튼이 있는 화면)를 열 때 그 공고를 공고 탭에 넣거나 맞춰요. 읽기만 하고 누르거나 입력하지 않아요. 다른 화면 글자는 저장하지 않아요.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "마지막: " + (lastRead?.let { (time, message) -> "${timeFormat.format(Date(time))} $message" } ?: "아직 없음"),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        var importMessage by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                scope.launch {
                    importMessage = runCatching { PostActions.deleteImported(db) }.fold(
                        onSuccess = { n -> "가져온 게시물 ${n}개를 지웠어요" },
                        onFailure = { e -> "지우기 오류: ${e.message}" },
                    )
                }
            }) {
                Text("가져온 공고 지우기")
            }
            Spacer(Modifier.width(8.dp))
            Text(importMessage, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        // 4) 오류 기록·테스트 정리 (개발용)
        val errors by remember { db.errorLogDao().observeRecent() }.collectAsState(initial = emptyList())
        var cleanupMessage by remember { mutableStateOf("") }
        Text(
            "오류 기록 ${errors.size}건" + (errors.firstOrNull()?.let {
                " · 최근: ${timeFormat.format(Date(it.time))} ${it.place} — ${it.message}"
            } ?: ""),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                scope.launch {
                    val n = db.applicantDao().deleteTestRows()
                    cleanupMessage = "테스트 지원자 ${n}명 지움"
                }
            }) {
                Text("테스트 지원자 지우기")
            }
            Spacer(Modifier.width(8.dp))
            Text(cleanupMessage, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        // 예시 데이터 (화면 설계의 예시 상황 — 지원자 9명, 송도 · 중등 수학 공고)
        var sampleMessage by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                scope.launch {
                    sampleMessage = runCatching { SampleData.insert(db) }.fold(
                        onSuccess = { n -> if (n > 0) "예시 지원자 ${n}명과 공고를 넣었어요" else "예시 데이터가 이미 있어요" },
                        onFailure = { e -> "예시 데이터 넣기 오류: ${e.message}" },
                    )
                }
            }) {
                Text("예시 데이터 넣기")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    sampleMessage = runCatching { SampleData.delete(db) }.fold(
                        onSuccess = { n -> "예시 데이터를 지웠어요 (지원자 ${n}명)" },
                        onFailure = { e -> "예시 데이터 지우기 오류: ${e.message}" },
                    )
                }
            }) {
                Text("예시 지우기")
            }
        }
        if (sampleMessage.isNotBlank()) {
            Text(sampleMessage, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        // 5) 최근 기록 목록 (최근 20건)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("최근 기록 ${entries.size}건", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                entries = NotificationLog.readRecent(context)
                lastRead = ScreenRead.last(context)
            }) {
                Text("새로고침")
            }
        }
        HorizontalDivider()
        entries.forEach { entry ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    timeFormat.format(Date(entry.optLong("time"))) + "  ·  " + entry.optString("package"),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(entry.optString("title"), style = MaterialTheme.typography.bodyLarge)
                Text(entry.optString("text"), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
    }
}
