package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.reply.LiveGate
import com.kangsam.daangnrecruit.reply.LiveReply
import com.kangsam.daangnrecruit.reply.LiveSettings
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * 지원자 탭의 '실제 발송 시험 (테스트 폰에만)' 칸 — 개발 중에만 있는 것 (화면 설계). 10/18 재걸: 테스트 폰 계정으로
 * 첫 메시지 → 답 → 마무리를 한 번에 해 보고 끔. 테스트 폰 당근 닉네임 · 끔·연습·실제 발송 · 시험 대상 · 지금 하는 일 ·
 * '지금 한 번 확인' · 최근 결과. 접어 두면 한 줄.
 * 언제 돌지는 reply/LiveReply, 보내도 되는지는 reply/LiveGate, 설정 값은 reply/LiveSettings
 */
@Composable
fun LiveTestPanel(daangnOn: Boolean, applicants: List<Applicant>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var askLive by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(LiveSettings.mode(context)) }
    var name by remember { mutableStateOf(LiveSettings.testName(context)) }
    var armedAt by remember { mutableStateOf(LiveSettings.armedAt(context)) }
    var results by remember { mutableStateOf(LiveSettings.results(context)) }
    var lastLog by remember { mutableStateOf(LiveSettings.lastLog(context)) }
    val status by LiveReply.status.collectAsState()

    // 하는 일이 바뀌면(차례가 끝나면) 결과·모드를 다시 읽음 (결과 불명이면 LiveReply가 '끔'으로 바꿈)
    LaunchedEffect(status) {
        results = LiveSettings.results(context)
        lastLog = LiveSettings.lastLog(context)
        mode = LiveSettings.mode(context)
        armedAt = LiveSettings.armedAt(context)
    }

    // 켜진 채 닉네임(앱이 맡은 지원)이 바뀌면 처리 전 메시지를 맞춤 — 글자를 치는 동안은 기다림
    // (반쯤 친 이름으로 맞추면 시험 대상의 처리 전 메시지가 '넘김'으로 바뀌어 되돌릴 수 없어서)
    var settledName by remember { mutableStateOf(name) }
    LaunchedEffect(name) {
        if (name == settledName) return@LaunchedEffect
        delay(1_500)
        settledName = name
        if (mode != LiveGate.Mode.OFF) LiveReply.settleLater(context)
    }

    // 시험이 켜져 있는 동안 이 화면을 띄워 두면 폰 화면이 안 꺼짐 (꺼지면 앱이 당근을 못 다룸 — 폰 설정은 안 바꾸고 이 화면에만)
    val view = LocalView.current
    DisposableEffect(mode) {
        view.keepScreenOn = mode != LiveGate.Mode.OFF
        onDispose { view.keepScreenOn = false }
    }

    fun change(m: LiveGate.Mode) {
        LiveSettings.setMode(context, m)
        mode = m
        armedAt = LiveSettings.armedAt(context)
        if (m == LiveGate.Mode.OFF) LiveReply.cancelAll()
        LiveReply.settleLater(context) // 맡은 지원이 바뀜 → 앱이 응대하지 않게 된 지원자의 처리 전 메시지는 넘김으로
        // 실제 발송으로 바꾸면 기다리던 시험 대상(연습한 지원 등)에게 바로 이어서 (대상이 없으면 안내만)
        if (m == LiveGate.Mode.LIVE) LiveReply.checkNow(context)
    }

    val config = LiveGate.Config(mode = mode, testName = name.trim(), armedAt = armedAt, daangnReply = daangnOn)
    val targets = applicants.filter { LiveGate.isTarget(config, it) }
    val sameName = if (name.isBlank()) 0 else applicants.count { a ->
        !a.isSample && a.name.trim() == name.trim() && a !in targets
    }

    DevCard(
        title = "실제 발송 시험 (테스트 폰에만)",
        subtitle = "지금: ${mode.label}" + (if (mode != LiveGate.Mode.OFF && name.isNotBlank()) " · '${name.trim()}'" else "") +
            " · 당근 응대는 이 시험 대상에게만",
        modifier = modifier,
        onHeaderClick = { open = !open },
        trailing = {
            if (mode == LiveGate.Mode.LIVE) Pill("발송 중", Tone.RED, small = true)
            Icon(
                if (open) AppIcons.ChevronUp else AppIcons.ChevronDown,
                contentDescription = if (open) "접기" else "펼치기",
                tint = Palette.LineStrong,
                modifier = Modifier.size(18.dp),
            )
        },
    ) {
        if (open) {
            // 펼친 칸은 높이를 정해 두고 안에서 스크롤 (아래 지원자 목록이 밀려 안 보이지 않게)
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "테스트 폰 당근 닉네임과 이름이 같고 시험을 켠 뒤(앞 30분 포함) 들어온 지원에만 보내요. 기존 지원자에겐 안 보내요. " +
                        "개발 중엔 '당근 응대'를 켜도 이 시험 대상에게만 보내고, '문자 응대'는 값만 저장돼요.\n" +
                        "연습: 입력창에 쓰고 대조한 뒤 지움 (전송 안 함) · 실제 발송: 첫 메시지 → 번호가 틀리면 다시 요청(2번까지) → 번호를 받으면 마무리. 끝나면 '끔'.",
                    style = AppText.Caption,
                    color = Palette.Muted,
                )
                LabeledField(
                    label = "테스트 폰 당근 닉네임",
                    value = name,
                    onValueChange = { v ->
                        name = v
                        LiveSettings.setTestName(context, v) // 처리 전 메시지 맞추기는 위 LaunchedEffect(name) — 치기를 멈춘 뒤
                    },
                    placeholder = "당근 닉네임 그대로",
                )
                Segmented(
                    options = LiveGate.Mode.entries.map { it.label },
                    selected = mode.label,
                    onSelect = { label ->
                        val m = LiveGate.Mode.entries.first { it.label == label }
                        if (m == LiveGate.Mode.LIVE && mode != LiveGate.Mode.LIVE) askLive = true else change(m)
                    },
                    height = 40.dp,
                )
                val warnings = buildList {
                    if (!daangnOn) add("'당근 응대'가 꺼져 있어서 돌지 않아요 — 위 스위치를 켜요")
                    if (name.isBlank()) add("테스트 폰 닉네임을 넣어야 돌아요")
                    if (sameName > 0) add("이 이름의 기존 지원자가 ${sameName}명 있어요 — 그 사람에겐 안 보내지만, 이름이 겹치면 카드·방을 못 골라 멈출 수 있어요")
                }
                warnings.forEach { NoteBox(it, Tone.RED) }
                if (mode != LiveGate.Mode.OFF && armedAt > 0) {
                    Text(
                        "켠 때 ${formatTime(armedAt)} — 그 30분 전부터 들어온 지원만 대상 · 시험 중엔 이 화면을 띄워 두세요 (화면이 안 꺼지게 해 둠)",
                        style = AppText.Caption,
                        color = Palette.Muted,
                    )
                }
                Text(
                    if (targets.isEmpty()) "시험 대상: 없음 (테스트 폰으로 지원하면 알림으로 들어와요)"
                    else "시험 대상: " + targets.joinToString(", ") { "${it.name} (${it.status})" },
                    style = AppText.Small,
                    color = Palette.Ink,
                )
                if (status.isNotBlank()) Text("하는 일: $status", style = AppText.Small, color = Palette.Ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        "지금 한 번 확인",
                        onClick = { LiveReply.checkNow(context) },
                        enabled = mode != LiveGate.Mode.OFF,
                        height = 40.dp,
                        textStyle = AppText.SmallStrong,
                    )
                    TextAction(if (showLog) "기록 닫기" else "마지막 기록", onClick = { showLog = !showLog })
                }
                results.take(6).forEach { Text(it, style = AppText.Mono, color = Palette.Ink2) }
                if (showLog) lastLog.takeLast(40).forEach { Text("· $it", style = AppText.Mono, color = Palette.Muted2) }
            }
        }
    }

    if (askLive) {
        AlertDialog(
            onDismissRequest = { askLive = false },
            title = { Text("실제 발송으로 바꿀까요?", style = AppText.Section, color = Palette.Ink) },
            text = {
                Text(
                    "테스트 폰 '${name.trim()}' 지원자에게 당근 메시지를 실제로 보내요. 기존 지원자에겐 안 보내요. 시험이 끝나면 '끔'으로 바꿔요.",
                    style = AppText.Body,
                    color = Palette.Ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askLive = false
                    change(LiveGate.Mode.LIVE)
                }, enabled = name.isNotBlank()) { Text("실제 발송", style = AppText.BodyStrong, color = if (name.isNotBlank()) Palette.Red else Palette.Placeholder) }
            },
            dismissButton = { TextButton(onClick = { askLive = false }) { Text("취소", style = AppText.BodyStrong, color = Palette.Ink2) } },
            containerColor = Palette.Surface,
        )
    }
}
