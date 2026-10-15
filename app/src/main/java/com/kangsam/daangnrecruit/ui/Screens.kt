package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 탭별 화면. 지원자 탭은 1차(지원 감지·기록) 목록만 먼저 넣고, 나머지는 회의 뒤에 시안대로 채움 (화면 설계 '화면별 기능')

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "홈",
        description = "지금 상황 보기 — 기능 켜짐 여부, 오늘 건수, 최근 활동",
        modifier = modifier
    )
}

@Composable
fun ApplicantsScreen(modifier: Modifier = Modifier) {
    // 폰 안 DB의 지원자 목록. DB가 바뀌면 자동으로 다시 그림
    val context = LocalContext.current
    val applicants by remember { AppDatabase.get(context).applicantDao().observeAll() }
        .collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("지원자", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "지원 알림으로 감지한 지원자 ${applicants.size}명 (당근 응대·문자 응대 켜기·끄기는 나중에)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        if (applicants.isEmpty()) {
            Text(
                "아직 없음 — 설정 → 알림 기록 (개발용)에서 기록이 켜져 있어야 감지해요",
                style = MaterialTheme.typography.labelMedium
            )
        } else {
            LazyColumn {
                items(applicants, key = { it.id }) { a ->
                    ApplicantRow(a)
                    HorizontalDivider()
                }
            }
        }
    }
}

/** 지원자 목록 한 줄: 이름·상태, 공고명, 지원 시각 */
@Composable
private fun ApplicantRow(a: Applicant) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(a.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            a.postTitle.ifBlank { "공고명 없음 (알림에 안 옴)" },
            style = MaterialTheme.typography.bodySmall
        )
        Text(TIME_FORMAT.format(Date(a.appliedAt)), style = MaterialTheme.typography.labelSmall)
    }
}

private val TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

@Composable
fun PostsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "상시 공고",
        description = "공고 목록과 자동 게시 켜기·끄기, 지금 올리기·마감했어요",
        modifier = modifier
    )
}

@Composable
fun CheckScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "확인 필요",
        description = "앱이 정할 수 없는 일만 모임 — 합치기 보류, 공고 못 찾음, 담당자 확인",
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
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("설정", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("처음 정하는 값과 전체 멈춤", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { showNotificationLog = true }) {
                Text("알림 기록 (개발용)")
            }
        }
    }
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