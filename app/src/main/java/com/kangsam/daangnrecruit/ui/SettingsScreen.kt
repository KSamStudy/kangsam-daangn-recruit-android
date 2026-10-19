package com.kangsam.daangnrecruit.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.FeatureSettings
import com.kangsam.daangnrecruit.sync.DaangnSync
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette

// 7. 설정 — 처음 정하는 값과 전체 멈춤 (화면 설계 '화면별 기능').
// 값은 '사람이 정할 항목 1차값' 초안을 보여 줌 (아직 앱에서 고치는 곳은 없음 — 정하지 않은 건 [정할 것]). 맨 아래 '알림 기록 (개발용)'

@Composable
fun SettingsScreen(refreshTick: Int, onOpenNotificationLog: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notice = LocalNotice.current
    val accounts by remember { AppDatabase.get(context).accountDao().observeAll() }.collectAsState(initial = emptyList())
    val notEditable = { notice("값을 고치는 곳은 아직 없어요 — 지금은 1차 값 초안을 보여 줘요") }

    Column(modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenHeader("설정", "처음 한 번 정하는 값 · 지금은 1차 값 초안")
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AllStopCard(refreshTick)
            DaangnSyncSection(refreshTick)

            val account = accounts.firstOrNull()
            SettingGroup("계정·게시") {
                SettingRow("계정", draft(account?.let(::accountLine) ?: "등록된 계정 없음"), notEditable)
                SettingRow("계정 한도", draft(account?.let(::limitLine) ?: "게시 하루 1 · 동시 1 · 당근 30"), notEditable)
                SettingRow("성격 목록", draft("과목 수업 — 수학 · [정할 것]"), notEditable)
                SettingRow("유료 광고", draft("안 씀 · 필수로 붙으면 안 올리고 알림"), notEditable, divider = false)
            }
            SettingGroup("문구") {
                SettingRow("당근 문구", draft("첫 메시지·마무리·짧은 답 5개·거절"), notEditable)
                SettingRow("문자 문구", draft("\"선생님 안녕하세요!…\" · 받을 항목 5개"), notEditable)
                SettingRow("금지 표현", draft("이모지·업체 느낌·일정 확정 등"), notEditable, divider = false)
            }
            SettingGroup("응대 규칙") {
                SettingRow("당근 대화", draft("최대 3번 · 72시간 무응답 종료"), notEditable)
                SettingRow("문자 대화", draft("답이 끊길 때 [정할 것]"), notEditable)
                SettingRow("예외 주제·알림", draft("주제 9개 · 알림 [정할 것]"), notEditable)
                SettingRow("연락 중 확인", draft("DB·티켓·대표폰·문자함"), notEditable)
                SettingRow("합치기 보류", draft("확인할 사람 [정할 것]"), notEditable, divider = false)
            }
            SettingGroup("시간·한도") {
                SettingRow("발송·게시 시간", draft("발송 09–21시 · 게시 평일 10–18시"), notEditable)
                SettingRow("마감 확인 때", draft("하루 2번 · 10시·15시"), notEditable)
                SettingRow("보내는 때", draft("받은 뒤 2–10분 · 이어서 1–3분"), notEditable)
                SettingRow("문자 한도·간격", draft("하루 50 · 간격 [정할 것]"), notEditable)
                SettingRow("자동으로 끄는 조건", draft("반려·숨김 1번 · 위반 1건 · 결과 불명 2번"), notEditable, divider = false)
            }
            SettingGroup("연결·개인정보") {
                SettingRow("LLM·가리기", draft("6개 가림 · 외부 LLM [정할 것]"), notEditable)
                SettingRow("DB", draft("폰 안 · 보관 기간·사본 [정할 것]"), notEditable)
                SettingRow("리니어 구인 티켓", draft("공고별 연결 · 담당자가 옮김"), notEditable, divider = false)
            }

            // 개발 중에만: 알림 기록 (개발용)
            DevCard(
                title = "알림 기록 (개발용)",
                subtitle = "알림 접근·접근성 상태, 예시 데이터, 오류 기록, 최근 알림",
                onHeaderClick = onOpenNotificationLog,
                trailing = {
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = Palette.LineStrong, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

/** 전체 멈춤 (빨강). 멈춰 있으면 '다시 시작' */
@Composable
private fun AllStopCard(refreshTick: Int) {
    val context = LocalContext.current
    val notice = LocalNotice.current
    var stopped by remember(refreshTick) { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.ALL_STOP)) }
    var ask by remember { mutableStateOf(false) }

    AppCard(radius = 14.dp, border = if (stopped) Palette.RedLine else Palette.Line) {
        if (!stopped) {
            AppButton(
                "전체 멈춤",
                onClick = { ask = true },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.DANGER,
                height = 48.dp,
                icon = AppIcons.Stop,
                textStyle = AppText.Section.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "앱이 하는 일(당근 가져오기·응대·공고 버튼)을 바로 멈춰요. 기능 하나만 끄려면 공고·지원자 화면의 스위치를 쓰세요. " +
                    "개발 중: 지원 감지는 아직 '알림 기록 (개발용)'의 알림 기록 스위치로 멈춰요",
                style = AppText.Caption.copy(lineHeight = 17.sp),
                color = Palette.Muted,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("전체 멈춤 중", Tone.RED, leading = AppIcons.Stop)
                Text("다시 시작할 때까지 멈춰 있어요", style = AppText.Small, color = Palette.Red, modifier = Modifier.weight(1f))
            }
            AppButton(
                "다시 시작",
                onClick = {
                    FeatureSettings.set(context, FeatureSettings.ALL_STOP, false)
                    stopped = false
                    notice("전체 멈춤을 풀었어요 — 기능은 각 스위치대로 돌아요")
                },
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
                icon = AppIcons.Play,
            )
        }
    }

    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("전체 멈춤할까요?", style = AppText.Section, color = Palette.Ink) },
            text = {
                Text(
                    "당근 가져오기·당근 응대(실제 발송 시험 포함)·공고 버튼이 바로 멈춰요. '다시 시작'을 누를 때까지 그대로예요.",
                    style = AppText.Body,
                    color = Palette.Ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    FeatureSettings.set(context, FeatureSettings.ALL_STOP, true)
                    stopped = true
                    notice("전체 멈춤 — 앱이 하는 일을 멈췄어요")
                }) { Text("멈춤", style = AppText.BodyStrong, color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("취소", style = AppText.BodyStrong, color = Palette.Ink2) } },
            containerColor = Palette.Surface,
        )
    }
}

/** 프로세스 C '당근 가져오기' — 따로 켜고 끄는 기능 (스위치·버튼·언제·지금·마지막·오늘·지난 실행 기록) */
@Composable
private fun DaangnSyncSection(refreshTick: Int) {
    val context = LocalContext.current
    val notice = LocalNotice.current
    val status by DaangnSync.status.collectAsState()
    val serviceOn = remember(refreshTick) { DaangnSync.isServiceOn(context) }
    var on by remember(refreshTick) { mutableStateOf(DaangnSync.isOn(context)) }
    var last by remember { mutableStateOf(DaangnSync.last(context)) }
    var runLog by remember { mutableStateOf(DaangnSync.lastLog(context)) }
    var runsToday by remember { mutableStateOf(DaangnSync.runsToday(context)) }
    var showLog by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick, status) {
        last = DaangnSync.last(context)
        runLog = DaangnSync.lastLog(context)
        runsToday = DaangnSync.runsToday(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("당근 가져오기")
        AppCard(radius = 14.dp, padding = PaddingValues(0.dp), spacing = 0.dp) {
            SwitchRow(
                "당근 가져오기",
                "켜면 사람이 연 당근 화면도 읽고, 지원자 메시지 알림도 기록해요",
                on,
                onChange = {
                    on = it
                    DaangnSync.setOn(context, it)
                },
                modifier = Modifier.padding(start = 14.dp, end = 8.dp).heightIn(min = 58.dp),
            )
            HLine()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!serviceOn) {
                    NoteBox("폰 설정의 접근성(당근 화면 읽기)이 꺼져 있어요 — 폰 설정 → 접근성 → 설치된 앱에서 켜요 (운영폰 설정이라 담당자 OK 뒤에)", Tone.RED) {
                        TextAction(
                            "접근성 설정 열기",
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            color = Palette.Red,
                        )
                    }
                }
                AppButton(
                    "당근에서 가져오기",
                    onClick = { notice(DaangnSync.start(context)) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.PRIMARY,
                    enabled = on && serviceOn && !DaangnSync.running,
                    height = 48.dp,
                    icon = AppIcons.Download,
                    textStyle = AppText.Section.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "당근을 열어 공고 → 지원자 → 채팅을 차례로 읽고 앱으로 돌아와요. 도는 동안 당근 화면 아래에 '가져오는 중' 띠가 떠요 — " +
                        "그동안 폰을 만지지 마세요 (멈추려면 홈 버튼)",
                    style = AppText.Caption.copy(lineHeight = 17.sp),
                    color = Palette.Muted,
                )
            }
            HLine()
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("언제 가져올지", style = AppText.Small, color = Palette.Muted)
                WhenLine("알림이 올 때", "새 지원·지원자 메시지를 알림 내용 그대로 바로 기록해요 (당근 화면은 안 열어요)")
                WhenLine("화면 가져오기", "위 '당근에서 가져오기'를 누를 때만 (정해진 시간마다는 나중에)")
                WhenLine("사람이 연 화면", "공고 상세·지원자 목록·채팅방을 열면 그 화면도 읽어 맞춰요")
            }
            HLine()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusLine("지금", if (DaangnSync.running) status.ifBlank { "가져오는 중" } else "쉬는 중")
                StatusLine("마지막", last?.let { (time, text) -> "${formatClock(time)} $text" } ?: "아직 없음")
                StatusLine("오늘", "버튼으로 ${runsToday}번 · 하루 ${DaangnSync.MAX_RUNS_PER_DAY}번까지")
            }
            HLine()
            SettingRow(
                "지난 실행 기록",
                AnnotatedString(if (runLog.isEmpty()) "없음" else (last?.let { "${formatClock(it.first)} · " } ?: "") + "단계별 ${runLog.size}줄"),
                onClick = { if (runLog.isNotEmpty()) showLog = !showLog },
                divider = false,
            )
            if (showLog) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    runLog.forEach { Text(it, style = AppText.Mono, color = Palette.Muted2) }
                }
            }
        }
    }
}

@Composable
private fun WhenLine(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppText.SmallStrong.copy(lineHeight = 19.sp), color = Palette.Green, modifier = Modifier.width(88.dp))
        Text(text, style = AppText.Small.copy(lineHeight = 19.sp), color = Palette.Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusLine(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = AppText.Small.copy(lineHeight = 19.sp), color = Palette.Muted, modifier = Modifier.width(44.dp))
        Text(text, style = AppText.Small.copy(lineHeight = 19.sp), color = Palette.Ink, modifier = Modifier.weight(1f))
    }
}

/** 설정 묶음: 작은 이름 + 흰 카드 안 줄들 */
@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title)
        AppCard(radius = 14.dp, padding = PaddingValues(0.dp), spacing = 0.dp, content = content)
    }
}

/** 1차 값 글자: [정할 것]은 노랑 굵게 */
private fun draft(text: String): AnnotatedString = buildAnnotatedString {
    val mark = "[정할 것]"
    var rest = text
    while (true) {
        val i = rest.indexOf(mark)
        if (i < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, i))
        withStyle(SpanStyle(color = Palette.Amber, fontWeight = FontWeight.SemiBold)) { append(mark) }
        rest = rest.substring(i + mark.length)
    }
}

private fun accountLine(a: Account): String = listOf(a.alias, a.device, a.status).filter { it.isNotBlank() }.joinToString(" · ")

private fun limitLine(a: Account): String = "게시 하루 ${a.dailyPostLimit} · 동시 ${a.concurrentPostLimit} · 당근 ${a.dailyMessageLimit}"
