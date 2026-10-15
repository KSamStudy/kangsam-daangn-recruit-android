package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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