package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.StandingPost
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.launch

// 6. 공고 편집 — 상시 공고 하나 만들기·고치기 (화면 설계 '화면별 기능'). 저장·켜기 전 확인이 하는 일은 PostActions

private val POST_WHEN_OPTIONS = listOf(PostActions.POST_ON_CLOSE, PostActions.POST_PERIODIC, PostActions.POST_MANUAL)
private val REMAIN_OPTIONS = listOf("그대로 둠", "앱이 마감하고 새로")

/** 켜기 전 확인(A0) 항목 — PostActions.checkBeforeOn이 돌려주는 걸린 것을 항목마다 맞춰 봄 */
private val BEFORE_ON_ITEMS = listOf(
    "문구 승인됨" to "문구 승인 전",
    "제목 있음" to "제목(공고명) 없음",
    "공고명 안 겹침" to "공고명이 겹침",
    "게시 계정 있음" to "게시 계정 없음",
    "계정 정지 아님" to "계정 정지",
)

@Composable
fun PostEditScreen(postId: Long?, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val notice = LocalNotice.current
    val accounts by remember { db.accountDao().observeAll() }.collectAsState(initial = emptyList())
    var original by remember(postId) { mutableStateOf<StandingPost?>(null) }
    var form by remember(postId) { mutableStateOf<StandingPost?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        val loaded = postId?.let { db.postDao().getStandingPost(it) }
        original = loaded
        form = loaded ?: StandingPost(name = "")
    }

    val f = form
    val isNew = postId == null
    val headerSub = when {
        f == null -> "불러오는 중…"
        isNew -> "새 공고 · 꺼진 채로 추가돼요"
        else -> "${original?.name ?: f.name} · ${original?.status ?: f.status}"
    }

    Column(modifier.fillMaxSize().imePadding()) {
        DetailTopBar(
            title = if (isNew) "공고 추가" else "공고 편집",
            subtitle = headerSub,
            onBack = { onDone("") },
            backLabel = "상시 공고로",
        )
        if (f == null) return@Column

        // 켜기 전 확인 (A0) — 고칠 때마다 다시 봄
        val problems by produceState(initialValue = emptyList<String>(), f) {
            value = if (f.postWhen == PostActions.POST_MANUAL) emptyList() else PostActions.checkBeforeOn(db, f)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ContentCard(
                f = f,
                original = original,
                onChange = { form = it },
                onApprove = { saved ->
                    scope.launch {
                        val approved = saved.copy(approvedBy = "담당자(임시)", approvedAt = System.currentTimeMillis())
                        db.postDao().updateStandingPost(approved)
                        original = approved
                        form = form?.copy(approvedBy = approved.approvedBy, approvedAt = approved.approvedAt)
                        notice("v${approved.textVersion} 문구를 승인으로 표시했어요 (개발용)")
                    }
                },
            )
            RuleCard(f = f, accounts = accounts, onChange = { form = it })
            BeforeOnCard(f = f, problems = problems)
        }

        // 아래 고정: 미리보기 · 저장
        Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
            HLine(Palette.Line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppButton("미리보기", onClick = { showPreview = true }, modifier = Modifier.weight(1f), height = 48.dp, textStyle = AppText.Section)
                AppButton(
                    "저장",
                    onClick = {
                        scope.launch {
                            runCatching { PostActions.save(db, original, f) }
                                .onSuccess { onDone(it) }
                                .onFailure { notice("오류: ${it.message}") }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.PRIMARY,
                    enabled = f.name.isNotBlank(),
                    height = 48.dp,
                    textStyle = AppText.Section,
                )
            }
        }

        if (showPreview) PreviewDialog(f) { showPreview = false }
    }
}

/** 공고 내용: 이름, 성격·동네·방식, 대상 학년·요일·시간대·급여, 제목(당근 공고명), 본문, 문구 버전·승인 */
@Composable
private fun ContentCard(f: StandingPost, original: StandingPost?, onChange: (StandingPost) -> Unit, onApprove: (StandingPost) -> Unit) {
    AppCard(radius = 14.dp, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("공고 내용", style = AppText.Section, color = Palette.Ink, modifier = Modifier.weight(1f))
            val v = original?.textVersion ?: f.textVersion
            val approvedAt = original?.approvedAt
            if (original != null && original.approvedBy.isNotBlank() && approvedAt != null) {
                Pill("문구 v$v · ${formatDate(approvedAt)} 승인", Tone.GREEN)
            } else {
                Pill("문구 v$v · 승인 전", Tone.AMBER)
            }
        }
        LabeledField("이름 (앱 안에서 부르는 이름)", f.name, { onChange(f.copy(name = it)) }, placeholder = "예: 송도 · 중등 수학")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("성격", f.category, { onChange(f.copy(category = it)) }, Modifier.weight(2f), placeholder = "과목 수업 — 수학")
            LabeledField("동네", f.area, { onChange(f.copy(area = it)) }, Modifier.weight(1f), placeholder = "송도동")
            LabeledField("방식", f.teachingMode, { onChange(f.copy(teachingMode = it)) }, Modifier.weight(1f), placeholder = "방문")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("대상 학년", f.targetGrade, { onChange(f.copy(targetGrade = it)) }, Modifier.weight(1f), placeholder = "중1~중3")
            LabeledField("요일·시간대", f.schedule, { onChange(f.copy(schedule = it)) }, Modifier.weight(1f), placeholder = "협의 가능")
            LabeledField("급여", f.pay, { onChange(f.copy(pay = it)) }, Modifier.weight(1f), placeholder = "[급여]")
        }
        LabeledField(
            "제목 (당근에 올라가는 공고명)", f.title, { onChange(f.copy(title = it)) },
            placeholder = "예: 송도 중등 수학 선생님 구해요",
            hint = "다른 글과 겹치지 않게 · 동네·학년을 넣으면 좋아요",
        )
        LabeledField(
            "본문 (회차마다 그대로)", f.body, { onChange(f.copy(body = it)) },
            singleLine = false, minLines = 3, textStyle = AppText.Message,
        )
        val textChanged = original != null && (original.title != f.title || original.body != f.body)
        Text(
            when {
                original == null -> "새 공고는 꺼진 채로 추가돼요 · 문구 승인 뒤 켤 수 있어요"
                textChanged -> "저장하면 v${original.textVersion + 1} · 승인 후 다음 회차부터 올라가요"
                else -> "제목·본문을 고치면 v${original.textVersion + 1} · 승인 후 다음 회차부터 올라가요"
            },
            style = AppText.Caption,
            color = Palette.Amber,
        )
        // 개발 확인용 임시 버튼 (승인하는 곳은 화면 설계 '정할 것·확인할 것')
        if (original != null && original.approvedBy.isBlank()) {
            AppButton(
                "문구 승인 (개발용)",
                onClick = { onApprove(original) },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.DASHED,
                height = 40.dp,
                textStyle = AppText.SmallStrong,
            )
        }
    }
}

/** 게시 규칙: 올리는 때를 고르면 맞는 항목만 (마감 후 기다리는 시간 / 주기·이전 글 처리), 그 밖 끌어올리기 등. 값을 누르면 고침 */
@Composable
private fun RuleCard(f: StandingPost, accounts: List<Account>, onChange: (StandingPost) -> Unit) {
    AppCard(radius = 14.dp, spacing = 4.dp) {
        Text("게시 규칙", style = AppText.Section, color = Palette.Ink, modifier = Modifier.padding(bottom = 4.dp))
        Text("올리는 때", style = AppText.Small, color = Palette.Muted)
        Segmented(POST_WHEN_OPTIONS, f.postWhen, onSelect = { onChange(f.copy(postWhen = it)) })
        Text(
            when (f.postWhen) {
                PostActions.POST_ON_CLOSE -> "이전 글이 마감된 게 확인되면 새 글을 올려요 · 고른 것에 맞는 항목만 보여요"
                PostActions.POST_PERIODIC -> "정한 주기마다 새 글을 올려요 · 고른 것에 맞는 항목만 보여요"
                else -> "새 글은 사람이 올려요 — 앱은 마감 확인만 해요"
            },
            style = AppText.Caption,
            color = Palette.Muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        when (f.postWhen) {
            PostActions.POST_ON_CLOSE -> InlineEditRow(
                label = "마감 후 기다리는 시간",
                value = if (f.waitAfterCloseMinutes > 0) f.waitAfterCloseMinutes.toString() else "",
                onValueChange = { onChange(f.copy(waitAfterCloseMinutes = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0)) },
                placeholder = "없음 · 바로",
                suffix = "분 뒤",
                keyboardType = KeyboardType.Number,
            )
            PostActions.POST_PERIODIC -> {
                InlineEditRow("주기·시각", f.periodText, { onChange(f.copy(periodText = it)) }, placeholder = "예: 7일마다 14:00")
                Text("주기 때 이전 글이 남아 있으면", style = AppText.Small, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
                Segmented(REMAIN_OPTIONS, f.whenPreviousRemains, onSelect = { onChange(f.copy(whenPreviousRemains = it)) }, height = 40.dp)
            }
        }
        InlineEditRow("끌어올리기", f.bump, { onChange(f.copy(bump = it)) }, placeholder = "안 함")
        InlineEditRow("앱이 마감하는 조건", f.appCloseCondition, { onChange(f.copy(appCloseCondition = it)) }, placeholder = "없음 · 담당자가 마감")
        InlineEditRow("건너뛰기", f.skipCondition, { onChange(f.copy(skipCondition = it)) }, placeholder = "진행 중 지원자 4명 이상이면 기다림")
        AccountRow(f, accounts, onChange)
        ValueRow("업체 표기", "[정할 것]", valueColor = Palette.Amber)
        InlineEditRow("넘길 구인 티켓", f.linearTicket, { onChange(f.copy(linearTicket = it)) }, placeholder = "리니어 구인 티켓")
        Text("오른쪽 값을 누르면 고칠 수 있어요", style = AppText.Caption, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 게시 계정: 누르면 계정 목록 */
@Composable
private fun AccountRow(f: StandingPost, accounts: List<Account>, onChange: (StandingPost) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == f.accountId }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("게시 계정", style = AppText.Small, color = Palette.Muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = accounts.isNotEmpty(), role = Role.DropdownList) { open = true }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current?.let { accountLabel(it) } ?: if (accounts.isEmpty()) "계정 없음" else "고르기",
                    style = AppText.BodyStrong,
                    color = if (current != null) Palette.Ink else Palette.Muted,
                    textAlign = TextAlign.End,
                )
                if (accounts.isNotEmpty()) {
                    Icon(AppIcons.ChevronDown, contentDescription = null, tint = Palette.LineStrong, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text("${accountLabel(acc)} · ${acc.status}", style = AppText.Body, color = Palette.Ink) },
                        onClick = {
                            open = false
                            onChange(f.copy(accountId = acc.id))
                        },
                    )
                }
            }
        }
    }
}

private fun accountLabel(a: Account): String = a.alias + if (a.device.isNotBlank()) " (${a.device})" else ""

/** 켜기 전 확인 (A0): 항목마다 통과·걸림. 하나라도 걸리면 그 공고의 스위치가 켜지지 않음 (직접 올릴 때만이면 확인할 것 없음) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeforeOnCard(f: StandingPost, problems: List<String>) {
    val manual = f.postWhen == PostActions.POST_MANUAL
    val ok = manual || problems.isEmpty()
    val bg = if (ok) Palette.GreenSoft else Palette.AmberCard
    val ink = if (ok) Palette.GreenDark else Palette.Amber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("켜기 전 확인", style = AppText.SmallBold, color = ink)
            if (manual) {
                Text("확인할 것 없음 (직접 올릴 때만)", style = AppText.SmallMedium, color = ink)
            } else {
                BEFORE_ON_ITEMS.forEach { (label, failKey) ->
                    val failed = problems.any { it.contains(failKey) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (failed) AppIcons.Close else AppIcons.Check,
                            contentDescription = if (failed) "걸림" else "통과",
                            tint = if (failed) Palette.Red else Palette.GreenDark,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            label,
                            style = AppText.SmallMedium,
                            color = if (failed) Palette.Red else Palette.GreenDark,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        // 항목에 없는 걸림이 있으면 그대로 보여 줌
        problems.filter { p -> BEFORE_ON_ITEMS.none { p.contains(it.second) } }.forEach {
            Text("걸림: $it", style = AppText.Caption, color = Palette.Red)
        }
        Text("하나라도 걸리면 이 공고의 스위치가 켜지지 않아요", style = AppText.Caption, color = ink)
    }
}

/** 미리보기: 당근에 올라갈 제목·조건·본문 */
@Composable
private fun PreviewDialog(f: StandingPost, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.title.ifBlank { "제목 없음" }, style = AppText.Section, color = Palette.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val meta = listOf(f.area, f.teachingMode, f.targetGrade, f.schedule, f.pay).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = AppText.Small, color = Palette.Muted)
                if (f.category.isNotBlank()) Text(f.category, style = AppText.Small, color = Palette.Muted)
                Text(f.body.ifBlank { "본문 없음" }, style = AppText.Message.copy(fontSize = AppText.Body.fontSize), color = Palette.Ink)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", style = AppText.BodyStrong, color = Palette.Green) } },
        containerColor = Palette.Surface,
    )
}
