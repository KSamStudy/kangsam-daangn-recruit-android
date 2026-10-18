package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.FeatureSettings
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.StandingPost
import kotlinx.coroutines.launch

// 5. 상시 공고 — 공고 목록과 자동 게시 켜기·끄기 (화면 설계 '화면별 기능')
// 당근에 글을 올리거나 '내 공고'를 읽는 기능은 아직 없음 → 이 화면의 버튼은 앱 안 기록만 바꿈

@Composable
fun PostsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val postings by remember { db.postDao().observePostings() }.collectAsState(initial = emptyList())
    val applicants by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val accounts by remember { db.accountDao().observeAll() }.collectAsState(initial = emptyList())
    var autoPostOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.AUTO_POST)) }
    var message by remember { mutableStateOf("") }
    // null = 목록, 0 = 새 공고, 그 밖 = 고칠 공고 ID
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var manualTitle by rememberSaveable { mutableStateOf("") }

    // 버튼을 누르면 앱 안 기록을 고치고, 결과 한 줄을 위에 보여줌
    fun act(block: suspend () -> String) {
        scope.launch { message = runCatching { block() }.getOrElse { "오류: ${it.message}" } }
    }

    val editing = editingId
    if (editing != null) {
        BackHandler { editingId = null }
        PostEditScreen(
            postId = if (editing > 0L) editing else null,
            onDone = { msg ->
                editingId = null
                if (msg.isNotBlank()) message = msg
            },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top") {
            Column {
                Text("상시 공고", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                FeatureSwitch("자동 게시", "올릴 때 확인 · 게시 · 마감 확인 (A1~A8)", autoPostOn) {
                    autoPostOn = it
                    FeatureSettings.set(context, FeatureSettings.AUTO_POST, it)
                }
                Text(
                    "당근에 글을 올리거나 '내 공고'를 읽는 기능은 아직 없어요 — 이 화면은 앱 안 기록만 바꿔요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingId = 0L }, modifier = Modifier.weight(1f)) { Text("공고 추가") }
                    OutlinedButton(onClick = { showManual = !showManual }, modifier = Modifier.weight(1f)) {
                        Text("직접 올린 공고 등록")
                    }
                }
                if (showManual) {
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("당근에 올라가 있는 공고명 (그대로)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val title = manualTitle.trim()
                            act {
                                val n = PostActions.registerManual(db, title)
                                manualTitle = ""
                                showManual = false
                                "'$title'를 직접 올린 공고로 등록했어요" + (if (n > 0) " · 기다리던 지원자 ${n}명을 붙였어요" else "")
                            }
                        },
                        enabled = manualTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("등록") }
                }
            }
        }
        if (posts.isEmpty()) {
            item(key = "empty") {
                Text(
                    "아직 상시 공고가 없어요 — '공고 추가'나 '직접 올린 공고 등록'으로 시작해요",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                postings = postings.filter { it.standingPostId == post.id },
                applicants = applicants.filter { it.standingPostId == post.id },
                account = accounts.firstOrNull { it.id == post.accountId },
                onToggle = { on ->
                    act {
                        val problems = PostActions.setOn(db, post, on)
                        if (problems.isEmpty()) {
                            "${post.name}: ${if (on) "켰어요" else "껐어요"}"
                        } else {
                            "켜기 전 확인에 걸려 안 켜져요 — ${problems.joinToString(", ")}"
                        }
                    }
                },
                onPostNow = {
                    act { PostActions.tryPostNow(db, post, FeatureSettings.isOn(context, FeatureSettings.ALL_STOP)) }
                },
                onClosed = { act { PostActions.markClosed(db, post) } },
                onEdit = { editingId = post.id },
            )
        }
    }
}

/** 공고 카드: 이름·스위치, 게시 규칙 요약·계정, 지금 상태, 지원자 수(끝난 지원 빼고)·진행 중 지원자 수, 지금 올리기·마감했어요, 지난 회차 */
@Composable
private fun PostCard(
    post: StandingPost,
    postings: List<Posting>,
    applicants: List<Applicant>,
    account: Account?,
    onToggle: (Boolean) -> Unit,
    onPostNow: () -> Unit,
    onClosed: () -> Unit,
    onEdit: () -> Unit,
) {
    var showHistory by remember(post.id) { mutableStateOf(false) }
    val live = postings.firstOrNull { it.status == "게시됨" }
    // 지원자 수는 끝난 지원(종료·합침·미응답)을 빼고 셈 — 당근 지원자 목록의 수와 같게 (ApplicantView)
    val shown = applicants.count { ApplicantView.isShown(it) }
    val inProgress = applicants.count { it.status in PostActions.IN_PROGRESS }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                    Text(post.name + (if (post.isSample) "  (예시)" else ""), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${ruleSummary(post)} · ${account?.alias ?: "계정 없음"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = post.status == "켜짐", onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(6.dp))
            Text(nowStatus(post, postings, inProgress), style = MaterialTheme.typography.bodyMedium)
            Text(
                "지원자 ${shown}명 · 그중 연락처 받고 진행 중 ${inProgress}명",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPostNow, modifier = Modifier.weight(1f)) { Text("지금 올리기") }
                OutlinedButton(onClick = onClosed, enabled = live != null, modifier = Modifier.weight(1f)) {
                    Text("마감했어요")
                }
            }
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "지난 회차 접기" else "지난 회차 ${postings.size}개 보기")
            }
            if (showHistory) {
                postings.forEach { p -> Text(roundLine(p), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** 게시 규칙 요약 (예: 마감되면 바로 · 끌어올리기 안 함) */
private fun ruleSummary(p: StandingPost): String {
    val rule = when (p.postWhen) {
        PostActions.POST_ON_CLOSE -> "마감되면 " + (if (p.waitAfterCloseMinutes > 0) "${p.waitAfterCloseMinutes}분 뒤" else "바로")
        PostActions.POST_PERIODIC -> "주기마다 " + p.periodText.ifBlank { "(주기 정할 것)" }
        else -> "직접 올릴 때만"
    }
    return "$rule · 끌어올리기 ${p.bump}"
}

/** 지금 상태: N회차 게시 중 · N일째 / 마감 확인 · 다음 게시 / 지원자가 많아 기다림 */
private fun nowStatus(p: StandingPost, postings: List<Posting>, inProgress: Int): String {
    val live = postings.firstOrNull { it.status == "게시됨" }
    if (live != null) {
        val days = live.postedAt?.let { (System.currentTimeMillis() - it) / 86_400_000L + 1 } ?: 0L
        return "${live.round}회차 게시 중" + (if (days > 0) " · ${days}일째" else "")
    }
    val last = postings.maxByOrNull { it.round }
    if (p.status != "켜짐") return "꺼짐" + (last?.let { " · 마지막 ${it.round}회차 ${it.status}" } ?: "")
    if (p.postWhen == PostActions.POST_MANUAL) return "직접 올린 공고 — 새 글은 사람이 올려요"
    if (inProgress >= PostActions.SKIP_THRESHOLD) return "지원자가 많아 기다림 (진행 중 ${inProgress}명)"
    val next = p.nextPostAt?.let { " · 다음 게시 ${formatTime(it)}" } ?: ""
    return if (last != null && last.status == "마감") {
        "${last.round}회차 마감 확인$next (올리기 기능은 아직)"
    } else {
        "올라간 글 없음 · 올릴 차례 (올리기 기능은 아직)"
    }
}

private fun roundLine(p: Posting): String = buildString {
    append("${p.round}회차 · ${p.status}")
    p.postedAt?.let { append(" · 게시 ${formatTime(it)}") }
    p.closedAt?.let { append(" · 마감 ${formatTime(it)}") }
    if (p.reason.isNotBlank()) append(" · ${p.reason}")
}
