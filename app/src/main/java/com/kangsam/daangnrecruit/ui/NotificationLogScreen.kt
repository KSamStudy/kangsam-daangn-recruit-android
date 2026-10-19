package com.kangsam.daangnrecruit.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.kangsam.daangnrecruit.SampleData
import com.kangsam.daangnrecruit.sync.ApplicantImport
import com.kangsam.daangnrecruit.sync.ChatImport
import com.kangsam.daangnrecruit.sync.DaangnSync
import com.kangsam.daangnrecruit.sync.PostImport
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 설정 > 알림 기록 (개발용) — 개발 중에만 있는 화면 (화면 설계 '개발 중에만 있는 것').
 * 알림 접근 권한과 알림 기록 스위치(알림 원문 기록 파일만 — 지원 감지 등은 이 스위치와 상관없이 돌고 전체 멈춤이면 멈춤, 10/19),
 * 접근성(당근 화면 읽기) 상태, 오류 기록, 예시 데이터 넣기·지우기, 테스트 지원자·가져온 공고·지원자·대화 지우기, 최근 알림 기록
 */
@Composable
fun NotificationLogScreen(refreshTick: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val notice = LocalNotice.current

    // 알림 접근 권한이 켜져 있는지
    val hasAccess = remember(refreshTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    // 알림 기록 ON/OFF (기본 OFF)
    var enabled by remember { mutableStateOf(NotificationLog.isEnabled(context)) }
    // 최근 기록
    var entries by remember { mutableStateOf(NotificationLog.readRecent(context)) }
    LaunchedEffect(refreshTick) { entries = NotificationLog.readRecent(context) }

    // 당근 가져오기(프로세스 C): 폰 설정의 접근성 + 이 기능 스위치 둘 다 켜져야 동작 (스위치는 설정 탭에도 있음)
    val screenServiceOn = remember(refreshTick) { DaangnSync.isServiceOn(context) }
    var screenReadOn by remember(refreshTick) { mutableStateOf(DaangnSync.isOn(context)) }
    var lastRead by remember { mutableStateOf(DaangnSync.last(context)) }
    LaunchedEffect(refreshTick) { lastRead = DaangnSync.last(context) }

    val errors by remember { db.errorLogDao().observeRecent() }.collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA) }

    fun act(label: String, block: suspend () -> String) {
        scope.launch { notice(runCatching { block() }.getOrElse { e -> "$label 오류: ${e.message}" }) }
    }

    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = "알림 기록 (개발용)", subtitle = "개발 중에만 있는 화면", onBack = onBack, backLabel = "설정으로")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1) 알림 접근 권한 + 알림 기록 스위치
            AppCard(radius = 14.dp, spacing = 6.dp) {
                StateLine("알림 접근 권한", if (hasAccess) "허용됨" else "꺼져 있음", ok = hasAccess)
                if (!hasAccess) {
                    AppButton(
                        "알림 접근 설정 열기",
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.PRIMARY,
                    )
                }
                HLine()
                SwitchRow(
                    "알림 기록",
                    "당근 알림 원문을 이 폰 안 파일에 기록해요 (개발용). 지원 감지는 이 스위치와 상관없이 돌고, 설정의 전체 멈춤이면 멈춰요. 알림을 지우거나 누르지 않아요.",
                    enabled,
                    onChange = { checked ->
                        enabled = checked
                        NotificationLog.setEnabled(context, checked)
                    },
                )
            }

            // 2) 당근 가져오기 (프로세스 C)
            AppCard(radius = 14.dp, spacing = 6.dp) {
                StateLine("접근성 (당근 화면 읽기)", if (screenServiceOn) "켜짐" else "꺼져 있음", ok = screenServiceOn)
                if (!screenServiceOn) {
                    AppButton(
                        "접근성 설정 열기",
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("운영폰 설정을 바꾸는 거라 담당자 OK 뒤에 켜요", style = AppText.Caption, color = Palette.Muted)
                }
                HLine()
                SwitchRow(
                    "당근 가져오기",
                    "켜두면 당근에서 내 공고 상세·지원자 목록·채팅방을 열 때 그 내용을 공고·지원자·대화에 맞춰요. 화면을 옮겨 다니며 한 번에 가져오는 건 설정 탭의 '당근에서 가져오기'예요.",
                    screenReadOn,
                    onChange = { checked ->
                        screenReadOn = checked
                        DaangnSync.setOn(context, checked)
                    },
                )
                Text(
                    "마지막: " + (lastRead?.let { (time, message) -> "${timeFormat.format(Date(time))} $message" } ?: "아직 없음"),
                    style = AppText.Caption,
                    color = Palette.Ink2,
                )
                AppButton(
                    "가져온 공고·지원자·대화 지우기",
                    onClick = {
                        act("지우기") {
                            val talks = ChatImport.deleteImported(db)
                            val people = ApplicantImport.deleteImported(db)
                            val posts = PostImport.deleteImported(db)
                            "가져온 게시물 ${posts}개 · 지원자 ${people}명 · 대화 ${talks}개를 지웠어요"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp,
                    textStyle = AppText.SmallStrong,
                )
            }

            // 3) 오류 기록·테스트 정리·예시 데이터
            AppCard(radius = 14.dp, spacing = 8.dp) {
                Text(
                    "오류 기록 ${errors.size}건" + (errors.firstOrNull()?.let {
                        " · 최근: ${timeFormat.format(Date(it.time))} ${it.place} — ${it.message}"
                    } ?: ""),
                    style = AppText.Small,
                    color = Palette.Ink,
                )
                AppButton(
                    "테스트 지원자 지우기",
                    onClick = { act("지우기") { "테스트 지원자 ${db.applicantDao().deleteTestRows()}명 지움" } },
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp,
                    textStyle = AppText.SmallStrong,
                )
                HLine()
                Text("예시 데이터 (화면 설계의 예시 상황 — 지원자 9명, 송도 · 중등 수학 공고)", style = AppText.Caption, color = Palette.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        "예시 데이터 넣기",
                        onClick = {
                            act("예시 데이터 넣기") {
                                val n = SampleData.insert(db)
                                if (n > 0) "예시 지원자 ${n}명과 공고를 넣었어요" else "예시 데이터가 이미 있어요"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                        textStyle = AppText.SmallStrong,
                    )
                    AppButton(
                        "예시 지우기",
                        onClick = { act("예시 지우기") { "예시 데이터를 지웠어요 (지원자 ${SampleData.delete(db)}명)" } },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                        textStyle = AppText.SmallStrong,
                    )
                }
            }

            // 4) 최근 기록 목록 (최근 20건)
            AppCard(radius = 14.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), spacing = 0.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("최근 기록 ${entries.size}건", style = AppText.Section, color = Palette.Ink, modifier = Modifier.weight(1f))
                    TextAction("새로고침", onClick = {
                        entries = NotificationLog.readRecent(context)
                        lastRead = DaangnSync.last(context)
                    })
                }
                entries.forEach { entry ->
                    HLine()
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            timeFormat.format(Date(entry.optLong("time"))) + "  ·  " + entry.optString("package"),
                            style = AppText.Tiny,
                            color = Palette.Muted,
                        )
                        Text(entry.optString("title"), style = AppText.BodyStrong, color = Palette.Ink)
                        Text(entry.optString("text"), style = AppText.Small, color = Palette.Ink2)
                    }
                }
            }
        }
    }
}

/** 상태 한 줄: 이름 · 켜짐(초록)/꺼짐(빨강) */
@Composable
private fun StateLine(label: String, state: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppText.BodyStrong, color = Palette.Ink, modifier = Modifier.weight(1f))
        Pill(state, if (ok) Tone.GREEN else Tone.RED)
    }
}
