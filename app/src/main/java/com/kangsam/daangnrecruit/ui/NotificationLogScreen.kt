package com.kangsam.daangnrecruit.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.kangsam.daangnrecruit.NotificationLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설정 > 알림 기록 (개발용): 10/9에 만든 기록 화면을 옮김 */
@Composable
fun NotificationLogScreen(refreshTick: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

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

    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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
            "켜두면 당근·다중 계정 앱 알림만 이 폰 안에 기록해요. 알림을 지우거나 누르지 않아요.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        // 3) 최근 기록 목록
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("최근 기록 ${entries.size}건", modifier = Modifier.weight(1f))
            TextButton(onClick = { entries = NotificationLog.readRecent(context) }) {
                Text("새로고침")
            }
        }
        HorizontalDivider()
        LazyColumn {
            items(entries) { entry ->
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
}