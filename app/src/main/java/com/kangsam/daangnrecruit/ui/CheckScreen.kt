package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.CheckActions
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.Posting
import kotlinx.coroutines.launch

// 4. 확인 필요 — 앱이 정할 수 없는 일만 (화면 설계 '화면별 기능')

private enum class CardKind { MERGE, POST_NOT_FOUND, STAFF_CHECK, OTHER_HOLD }

private fun kindOf(a: Applicant): CardKind = when {
    a.status == ApplicantStatus.STAFF_CHECK -> CardKind.STAFF_CHECK
    a.reason.startsWith(HoldReason.POST_NOT_FOUND) -> CardKind.POST_NOT_FOUND
    a.reason.contains(HoldReason.MERGE) || a.sameCandidate.isNotBlank() -> CardKind.MERGE
    else -> CardKind.OTHER_HOLD
}

@Composable
fun CheckScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val held by remember {
        db.applicantDao().observeByStatus(listOf(ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK))
    }.collectAsState(initial = emptyList())
    val all by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val problemPostings by remember { db.postDao().observeProblemPostings() }.collectAsState(initial = emptyList())
    val livePostings by remember { db.postDao().observeLivePostings() }.collectAsState(initial = emptyList())
    var message by remember { mutableStateOf("") }

    // 버튼을 누르면 DB를 고치고, 결과 한 줄을 위에 보여줌
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            message = runCatching { block() }.fold(onSuccess = { done }, onFailure = { "오류: ${it.message}" })
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top") {
            Column {
                Text("확인 필요", style = MaterialTheme.typography.titleLarge)
                Text(
                    "앱이 정할 수 없는 일만 모여요 — ${held.size + problemPostings.size}건",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (held.isEmpty() && problemPostings.isEmpty()) {
            item(key = "empty") {
                Text("지금 확인할 일이 없어요", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(held, key = { "a${it.id}" }) { a ->
            when (kindOf(a)) {
                CardKind.MERGE -> {
                    val other = all.firstOrNull { it.id != a.id && it.phoneKey.isNotBlank() && it.phoneKey == a.phoneKey }
                    MergeCard(
                        a = a,
                        other = other,
                        onSeparate = { act("${a.name}: 다른 사람으로 두고 진행해요") { CheckActions.separate(db, a) } },
                        onMerge = { o -> act("${a.name}·${o.name}: 같은 사람으로 합쳤어요") { CheckActions.merge(db, a, o) } },
                    )
                }
                CardKind.POST_NOT_FOUND -> PostNotFoundCard(
                    a = a,
                    livePostings = livePostings,
                    onPick = { p -> act("${a.name}: '${p.title}' 공고에 붙였어요") { CheckActions.pickPosting(db, a, p) } },
                    onRegister = {
                        act("'${a.postTitle}'를 직접 올린 공고로 등록하고 지원자를 붙였어요") {
                            CheckActions.registerManualPost(db, a)
                        }
                    },
                )
                CardKind.STAFF_CHECK -> StaffCheckCard(
                    a = a,
                    onHandBack = { act("${a.name}: 앱에 다시 맡겼어요") { CheckActions.handBack(db, a) } },
                )
                CardKind.OTHER_HOLD -> OtherHoldCard(a)
            }
        }
        items(problemPostings, key = { "p${it.id}" }) { p -> PostingProblemCard(p) }
    }
}

/** 카드 틀: 맨 위에 카드 종류 (노랑 = 사람이 볼 것) */
@Composable
private fun CheckCard(kind: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(kind, style = MaterialTheme.typography.labelLarge, color = Color(0xFFB08800))
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** 누구·어느 공고·상태·이유 */
@Composable
private fun WhoLine(a: Applicant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(a.name, style = MaterialTheme.typography.titleMedium)
        if (a.area.isNotBlank()) {
            Text(" · ${a.area}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (a.isSample) {
            Text("  예시", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.weight(1f))
        StatusChip(a.status)
    }
    Text(a.postTitle.ifBlank { "공고명 없음" }, style = MaterialTheme.typography.bodySmall)
    Text("이유: ${a.reason.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MergeCard(a: Applicant, other: Applicant?, onSeparate: () -> Unit, onMerge: (Applicant) -> Unit) {
    CheckCard("합치기 보류") {
        WhoLine(a)
        Text(
            if (other != null) "같은 번호(${a.phone.ifBlank { "—" }}): ${other.name} · ${other.status}"
            else "같은 번호인 다른 지원 건을 못 찾았어요",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSeparate, modifier = Modifier.fillMaxWidth()) { Text("다른 사람 (따로)") }
        Button(
            onClick = { if (other != null) onMerge(other) },
            enabled = other != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("같은 사람 (합침)") }
    }
}

@Composable
private fun PostNotFoundCard(a: Applicant, livePostings: List<Posting>, onPick: (Posting) -> Unit, onRegister: () -> Unit) {
    var picking by remember(a.id) { mutableStateOf(false) }
    CheckCard("공고 못 찾음") {
        WhoLine(a)
        Text("알림의 공고명과 같은 공고가 앱에 없어요", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { picking = !picking }, modifier = Modifier.fillMaxWidth()) { Text("공고 고르기") }
        if (picking) {
            if (livePostings.isEmpty()) {
                Text("올라가 있는 공고가 없어요 — '직접 올린 공고 등록'을 쓰세요", style = MaterialTheme.typography.bodySmall)
            }
            livePostings.forEach { p ->
                TextButton(onClick = { onPick(p) }) { Text("${p.round}회차 · ${p.title.ifBlank { "제목 없음" }}") }
            }
        }
        Button(onClick = onRegister, enabled = a.postTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("직접 올린 공고 등록")
        }
    }
}

@Composable
private fun StaffCheckCard(a: Applicant, onHandBack: () -> Unit) {
    CheckCard("담당자 확인") {
        WhoLine(a)
        if (a.statusBeforeHold.isNotBlank()) {
            Text("다시 맡기면 '${a.statusBeforeHold}'(으)로 돌아가요", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onHandBack, modifier = Modifier.fillMaxWidth()) { Text("직접 답한 뒤 앱에 다시 맡기기") }
    }
}

@Composable
private fun OtherHoldCard(a: Applicant) {
    CheckCard("보류") {
        WhoLine(a)
        Text(
            "이 카드의 버튼은 아직 정하지 않았어요 (화면 설계 '정할 것' — 연락 중 확인 보류 등)",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun PostingProblemCard(p: Posting) {
    CheckCard("공고 — ${p.status}") {
        Text("${p.round}회차 · ${p.title.ifBlank { "제목 없음" }}", style = MaterialTheme.typography.titleSmall)
        Text("이유: ${p.reason.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
        Text("카드 버튼은 아직 정하지 않았어요 (화면 설계 '정할 것')", style = MaterialTheme.typography.labelSmall)
    }
}
