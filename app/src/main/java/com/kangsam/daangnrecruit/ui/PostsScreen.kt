package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.Account
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.FeatureSettings
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.StandingPost
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.launch

// 5. 상시 공고 — 공고 목록과 자동 게시 켜기·끄기 (화면 설계 '화면별 기능')
// 당근에 글을 올리는 기능(게시 기능 A2~A7)은 아직 없음 → 이 화면의 버튼은 앱 안 기록만 바꿈 (하는 일은 PostActions)

@Composable
fun PostsScreen(onEditPost: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val notice = LocalNotice.current
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val postings by remember { db.postDao().observePostings() }.collectAsState(initial = emptyList())
    val applicants by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val accounts by remember { db.accountDao().observeAll() }.collectAsState(initial = emptyList())
    var autoPostOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.AUTO_POST)) }
    val allStop = remember { FeatureSettings.isOn(context, FeatureSettings.ALL_STOP) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var manualTitle by rememberSaveable { mutableStateOf("") }

    // 버튼을 누르면 앱 안 기록을 고치고, 결과 한 줄을 화면 아래 띠로
    fun act(block: suspend () -> String) {
        scope.launch { notice(runCatching { block() }.getOrElse { "오류: ${it.message}" }) }
    }

    val liveCount = postings.count { it.status == "게시됨" }
    val onCount = posts.count { it.status == "켜짐" }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader("상시 공고", "켜짐 ${onCount}개 · 지금 올라가 있는 글 ${liveCount}개") {
                AppButton("공고 추가", onClick = { onEditPost(0L) }, kind = ButtonKind.PRIMARY, icon = AppIcons.Plus)
            }
        }

        // 위 스위치: 자동 게시 (기능 끄는 곳)
        item(key = "autoPost") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppCard(radius = 14.dp, padding = PaddingValues(start = 14.dp, end = 8.dp), spacing = 0.dp) {
                    SwitchRow("자동 게시", "켜진 상시 공고를 게시 규칙대로 올려요", autoPostOn, onChange = {
                        autoPostOn = it
                        FeatureSettings.set(context, FeatureSettings.AUTO_POST, it)
                    }, modifier = Modifier.heightIn(min = 58.dp))
                }
                // 개발 중 안내 (당근에 올리는 기능을 만들면 뺌)
                Text(
                    "개발 중: 당근에 글을 올리는 기능은 아직 없어서, 이 화면의 버튼은 앱 기록만 바꿔요",
                    style = AppText.Caption,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (posts.isEmpty()) {
            item(key = "empty") {
                EmptyNote("아직 상시 공고가 없어요 — '공고 추가'나 '직접 올린 공고 등록'으로 시작해요", Modifier.padding(horizontal = 16.dp))
            }
        }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                postings = postings.filter { it.standingPostId == post.id },
                applicants = applicants.filter { it.standingPostId == post.id },
                account = accounts.firstOrNull { it.id == post.accountId },
                allStop = allStop,
                modifier = Modifier.padding(horizontal = 16.dp),
                onToggle = { on ->
                    act {
                        val problems = PostActions.setOn(db, post, on)
                        if (problems.isEmpty()) "${post.name}: ${if (on) "켰어요" else "껐어요"}"
                        else "켜기 전 확인에 걸려 안 켜져요 — ${problems.joinToString(", ")}"
                    }
                },
                onPostNow = { act { PostActions.tryPostNow(db, post, FeatureSettings.isOn(context, FeatureSettings.ALL_STOP)) } },
                onClosed = { act { PostActions.markClosed(db, post) } },
                onEdit = { onEditPost(post.id) },
            )
        }

        // 직접 올린 공고 등록 (B0): 당근에 올라가 있는 공고명을 그대로 넣으면 그 공고명으로 기다리던 지원자가 붙음
        item(key = "manual") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!showManual) {
                    AppButton(
                        "직접 올린 공고 등록",
                        onClick = { showManual = true },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.DASHED,
                        icon = AppIcons.Plus,
                    )
                } else {
                    AppCard(radius = 14.dp, spacing = 8.dp) {
                        LabeledField(
                            label = "당근에 올라가 있는 공고명 (그대로)",
                            value = manualTitle,
                            onValueChange = { manualTitle = it },
                            placeholder = "예: 중2 공부 도와주실분!!",
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton("취소", onClick = { showManual = false }, modifier = Modifier.weight(1f))
                            AppButton(
                                "등록",
                                onClick = {
                                    val title = manualTitle.trim()
                                    act {
                                        val n = PostActions.registerManual(db, title)
                                        manualTitle = ""
                                        showManual = false
                                        "'$title'를 직접 올린 공고로 등록했어요" + (if (n > 0) " · 기다리던 지원자 ${n}명을 붙였어요" else "")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                kind = ButtonKind.PRIMARY,
                                enabled = manualTitle.isNotBlank(),
                            )
                        }
                    }
                }
                Text(
                    "당근에 올린 공고명을 그대로 넣으면 그 공고명으로 기다리던 지원자가 붙어요",
                    style = AppText.Caption,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 공고 카드: 이름과 켜짐·꺼짐 스위치, 게시 규칙 요약·계정, 지금 상태, 지원자 수(끝난 지원 빼고)·진행 중 지원자 수,
 * 지금 올리기·마감했어요, 지난 회차. 꺼져 있고 올라가 있는 글이 없으면 한 줄로 접음
 */
@Composable
private fun PostCard(
    post: StandingPost,
    postings: List<Posting>,
    applicants: List<Applicant>,
    account: Account?,
    allStop: Boolean,
    modifier: Modifier,
    onToggle: (Boolean) -> Unit,
    onPostNow: () -> Unit,
    onClosed: () -> Unit,
    onEdit: () -> Unit,
) {
    val on = post.status == "켜짐"
    val live = postings.firstOrNull { it.status == "게시됨" }
    val title = post.name + if (post.isSample) " (예시)" else ""

    if (!on && live == null) {
        // 꺼진 공고 (한 줄)
        AppCard(modifier = modifier, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), spacing = 0.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onEdit),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = AppText.CardTitle, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("꺼짐 · ${ruleShort(post)}", style = AppText.Caption, color = Palette.Muted)
                }
                Toggle(checked = false, onChange = onToggle, label = post.name)
            }
        }
        return
    }

    // 지원자 수는 끝난 지원(종료·합침·미응답)을 빼고 셈 — 당근 지원자 목록의 수와 같게 (ApplicantView)
    val shown = applicants.count { ApplicantView.isShown(it) }
    val inProgress = applicants.count { it.status in PostActions.IN_PROGRESS }
    val manual = post.postWhen == PostActions.POST_MANUAL
    val (stateText, stateTone) = nowStatus(post, postings, inProgress)
    val past = postings.filter { it.id != live?.id && it.status != "대기" }.sortedByDescending { it.round }

    AppCard(modifier = modifier, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = AppText.CardTitle,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(vertical = 11.dp),
            )
            Toggle(checked = on, onChange = onToggle, label = post.name)
        }
        Text("${ruleSummary(post)} · ${account?.alias ?: "계정 없음"}", style = AppText.Small, color = Palette.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stateText, stateTone)
            Text(
                live?.watchResult?.takeIf { it.isNotBlank() }?.let { "30분 지켜보기 $it" } ?: "",
                style = AppText.Caption,
                color = Palette.Muted,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Text("지원자 ${shown}명 · 그중 연락처 받고 진행 중 ${inProgress}명", style = AppText.Caption, color = Palette.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 지금 올리기: 직접 올릴 때만인 공고·전체 멈춤 중엔 안 됨 (자동 게시가 꺼져 있어도 됨)
            AppButton("지금 올리기", onClick = onPostNow, modifier = Modifier.weight(1f), enabled = !manual && !allStop)
            AppButton("마감했어요", onClick = onClosed, modifier = Modifier.weight(1f), kind = ButtonKind.DARK, enabled = live != null)
        }
        if (past.isNotEmpty()) {
            HLine()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("지난 회차", style = AppText.CaptionStrong, color = Palette.Muted)
                past.take(5).forEach { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${p.round}회차" + (p.postedAt?.let { " · ${formatDate(it)}" } ?: ""),
                            style = AppText.Caption,
                            color = Palette.Muted,
                            modifier = Modifier.width(78.dp),
                        )
                        Text(roundLine(p), style = AppText.Caption, color = Palette.Ink2, modifier = Modifier.weight(1f))
                    }
                }
                if (past.size > 5) Text("그 전 ${past.size - 5}개 더 있음", style = AppText.Caption, color = Palette.Muted)
            }
        }
    }
}

/** 게시 규칙 요약 (예: 마감되면 바로 · 끌어올리기 안 함) */
private fun ruleSummary(p: StandingPost): String = "${ruleShort(p)} · 끌어올리기 ${p.bump.ifBlank { "안 함" }}"

private fun ruleShort(p: StandingPost): String = when (p.postWhen) {
    PostActions.POST_ON_CLOSE -> "마감되면 " + (if (p.waitAfterCloseMinutes > 0) "${p.waitAfterCloseMinutes}분 뒤" else "바로")
    PostActions.POST_PERIODIC -> "주기마다 " + p.periodText.ifBlank { "(주기 정할 것)" }
    else -> "직접 올릴 때만"
}

/** 지금 상태: N회차 게시 중 · N일째 / 마감 — '내 공고' 확인 전 / 마감 확인 · 다음 게시 / 지원자가 많아 기다림 */
private fun nowStatus(p: StandingPost, postings: List<Posting>, inProgress: Int): Pair<String, Tone> {
    val live = postings.firstOrNull { it.status == "게시됨" }
    if (live != null) {
        val days = live.postedAt?.let { (System.currentTimeMillis() - it) / 86_400_000L + 1 } ?: 0L
        return ("${live.round}회차 게시 중" + (if (days > 0) " · ${days}일째" else "")) to Tone.GREEN
    }
    val last = postings.maxByOrNull { it.round }
    if (p.postWhen == PostActions.POST_MANUAL) return "직접 올린 공고 — 새 글은 사람이 올려요" to Tone.GRAY
    if (last != null && last.status == "마감" && last.closeCheckedAt == null && last.closeCheckMethod == PostActions.CLOSE_UNCHECKED) {
        return "${last.round}회차 마감 — '내 공고' 확인 전" to Tone.AMBER
    }
    if (inProgress >= PostActions.SKIP_THRESHOLD) return "지원자가 많아 기다림 (진행 중 ${inProgress}명)" to Tone.AMBER
    val next = p.nextPostAt?.let { " · 다음 게시 ${formatClock(it)}" } ?: ""
    return if (last != null && last.status == "마감") "${last.round}회차 마감 확인$next" to Tone.GRAY
    else "올라간 글 없음 · 올릴 차례" to Tone.GRAY
}

/** 지난 회차 한 줄: 마감 · 10/10 담당자 · 채용 끝남 */
private fun roundLine(p: Posting): String = buildString {
    append(p.status)
    p.closedAt?.let { append(" · ${formatDate(it)}") }
    if (p.closedBy.isNotBlank()) append(" ${p.closedBy}")
    if (p.reason.isNotBlank()) append(" · ${p.reason}")
}
