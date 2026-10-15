package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.StandingPost
import kotlinx.coroutines.launch

// 6. 공고 편집 — 상시 공고 하나 만들기·고치기 (화면 설계 '화면별 기능')

private val POST_WHEN_OPTIONS = listOf(PostActions.POST_ON_CLOSE, PostActions.POST_PERIODIC, PostActions.POST_MANUAL)
private val REMAIN_OPTIONS = listOf("그대로 둠", "앱이 마감하고 새로")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditScreen(postId: Long?, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val accounts by remember { db.accountDao().observeAll() }.collectAsState(initial = emptyList())
    var original by remember(postId) { mutableStateOf<StandingPost?>(null) }
    var form by remember(postId) { mutableStateOf<StandingPost?>(null) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(postId) {
        val loaded = postId?.let { db.postDao().getStandingPost(it) }
        original = loaded
        form = loaded ?: StandingPost(name = "")
    }

    val f = form
    if (f == null) {
        Text("불러오는 중…", modifier = modifier.padding(16.dp))
        return
    }
    // 켜기 전 확인 (A0) — 고칠 때마다 다시 봄
    val problems by produceState(initialValue = emptyList<String>(), f) {
        value = if (f.postWhen == PostActions.POST_MANUAL) emptyList() else PostActions.checkBeforeOn(db, f)
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = { onDone("") }) { Text("← 상시 공고") }
        Text(if (postId == null) "공고 추가" else "공고 편집", style = MaterialTheme.typography.titleLarge)
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Section("공고 내용")
        Field("이름 (앱 안에서 부르는 이름, 예: 송도 · 중등 수학)", f.name) { form = f.copy(name = it) }
        Field("성격 (일 종류 + 과목)", f.category) { form = f.copy(category = it) }
        Field("동네 (근무지)", f.area) { form = f.copy(area = it) }
        Field("수업 방식", f.teachingMode) { form = f.copy(teachingMode = it) }
        Field("대상 학년", f.targetGrade) { form = f.copy(targetGrade = it) }
        Field("요일·시간대", f.schedule) { form = f.copy(schedule = it) }
        Field("급여", f.pay) { form = f.copy(pay = it) }
        Field("제목 (당근에 올라가는 공고명)", f.title) { form = f.copy(title = it) }
        Field("본문", f.body, singleLine = false) { form = f.copy(body = it) }
        val approval = if (f.approvedBy.isBlank()) {
            "승인 전"
        } else {
            "승인: ${f.approvedBy} ${f.approvedAt?.let { formatTime(it) } ?: ""}"
        }
        Text("문구 버전 v${f.textVersion} · $approval", style = MaterialTheme.typography.bodySmall)
        val saved = original
        if (saved != null && saved.approvedBy.isBlank()) {
            TextButton(onClick = {
                scope.launch {
                    val approved = saved.copy(approvedBy = "담당자(임시)", approvedAt = System.currentTimeMillis())
                    db.postDao().updateStandingPost(approved)
                    original = approved
                    form = form?.copy(approvedBy = approved.approvedBy, approvedAt = approved.approvedAt)
                    message = "v${approved.textVersion} 문구를 승인으로 표시했어요 (임시)"
                }
            }) {
                Text("문구 승인 (임시 — 누가 어떻게 할지 '정할 것')")
            }
        }

        Section("게시 규칙")
        Text("올리는 때", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            POST_WHEN_OPTIONS.forEach { opt ->
                FilterChip(selected = f.postWhen == opt, onClick = { form = f.copy(postWhen = opt) }, label = { Text(opt) })
            }
        }
        when (f.postWhen) {
            PostActions.POST_ON_CLOSE -> {
                Field("마감 후 기다리는 시간 (분, 0이면 바로)", f.waitAfterCloseMinutes.toString(), numeric = true) {
                    form = f.copy(waitAfterCloseMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0)
                }
            }
            PostActions.POST_PERIODIC -> {
                Field("주기·시각 (예: 7일마다 14:00)", f.periodText) { form = f.copy(periodText = it) }
                Text("주기 때 이전 글이 남아 있으면", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMAIN_OPTIONS.forEach { opt ->
                        FilterChip(
                            selected = f.whenPreviousRemains == opt,
                            onClick = { form = f.copy(whenPreviousRemains = opt) },
                            label = { Text(opt) }
                        )
                    }
                }
            }
            else -> Text("새 글은 사람이 올려요 — 앱은 마감 확인만 해요", style = MaterialTheme.typography.bodySmall)
        }
        Field("끌어올리기 (안 함 / 간격)", f.bump) { form = f.copy(bump = it) }
        Field("앱이 마감하는 조건 (없음 / 조건)", f.appCloseCondition) { form = f.copy(appCloseCondition = it) }
        Field("건너뛰기 조건", f.skipCondition) { form = f.copy(skipCondition = it) }
        Text("게시 계정", style = MaterialTheme.typography.bodySmall)
        if (accounts.isEmpty()) {
            Text("계정 없음 — 설정에서 계정을 추가해요 (다음 단계)", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { acc ->
                FilterChip(
                    selected = f.accountId == acc.id,
                    onClick = { form = f.copy(accountId = acc.id) },
                    label = { Text("${acc.alias} · ${acc.status}") }
                )
            }
        }
        Text("업체 표기: [정할 것] (1차값 '업체 표기')", style = MaterialTheme.typography.bodySmall)
        Field("넘길 구인 티켓", f.linearTicket) { form = f.copy(linearTicket = it) }

        Section("켜기 전 확인")
        when {
            f.postWhen == PostActions.POST_MANUAL ->
                Text("직접 올린 공고는 확인할 것이 없어요", style = MaterialTheme.typography.bodySmall)
            problems.isEmpty() ->
                Text("통과 — 문구 승인 · 공고명 안 겹침 · 계정 정지 아님", style = MaterialTheme.typography.bodySmall)
            else -> problems.forEach {
                Text("걸림: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            "하나라도 걸리면 상시 공고 화면에서 이 공고의 스위치가 켜지지 않아요",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    runCatching { PostActions.save(db, original, f) }
                        .onSuccess { onDone(it) }
                        .onFailure { message = "오류: ${it.message}" }
                }
            },
            enabled = f.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("저장") }
        Text(
            "제목·본문을 고치면 문구 버전이 올라가고, 승인 뒤 다음 회차부터 새 문구로 올라가요",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
