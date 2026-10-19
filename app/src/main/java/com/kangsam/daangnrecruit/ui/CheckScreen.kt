package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.CheckActions
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.sync.DaangnSync
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.launch

// 4. 확인 필요 — 앱이 정할 수 없는 일만 (화면 설계 '화면별 기능').
// 카드마다 누구(또는 어느 공고)·상태·이유와 버튼. 이름을 누르면 지원자 상세(대화를 보고 정할 수 있게). 버튼이 하는 일은 CheckActions

private enum class CardKind { MERGE, POST_NOT_FOUND, STAFF_CHECK, OTHER_HOLD }

private fun kindOf(a: Applicant): CardKind = when {
    a.status == ApplicantStatus.STAFF_CHECK -> CardKind.STAFF_CHECK
    a.reason.startsWith(HoldReason.POST_NOT_FOUND) -> CardKind.POST_NOT_FOUND
    a.reason.contains(HoldReason.MERGE) || a.sameCandidate.isNotBlank() -> CardKind.MERGE
    else -> CardKind.OTHER_HOLD
}

@Composable
fun CheckScreen(onOpenApplicant: (Long) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val notice = LocalNotice.current
    val held by remember {
        db.applicantDao().observeByStatus(listOf(ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK))
    }.collectAsState(initial = emptyList())
    val all by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val problemPostings by remember { db.postDao().observeProblemPostings() }.collectAsState(initial = emptyList())
    val livePostings by remember { db.postDao().observeLivePostings() }.collectAsState(initial = emptyList())
    val mismatch by remember { DaangnSync.mismatchFlow(context) }.collectAsState() // 닉네임이 달라 멈춘 당근 가져오기

    // 버튼을 누르면 DB를 고치고, 결과 한 줄을 화면 아래 띠로
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch { notice(runCatching { block() }.fold(onSuccess = { done }, onFailure = { "오류: ${it.message}" })) }
    }

    val postName = posts.associate { it.id to it.name }
    val total = held.size + problemPostings.size + (if (mismatch != null) 1 else 0)

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "top") { ScreenHeader("확인 필요", "앱이 정할 수 없는 것만 여기로 와요 · ${total}건") }
        if (total == 0) {
            item(key = "empty") { EmptyNote("지금 확인할 일이 없어요", Modifier.padding(horizontal = 16.dp)) }
        }
        mismatch?.let { nick ->
            item(key = "nickname") { NicknameCard(nick, Modifier.padding(horizontal = 16.dp), onOpenSettings) }
        }
        items(held, key = { "a${it.id}" }) { a ->
            val cardModifier = Modifier.padding(horizontal = 16.dp)
            val where = a.standingPostId?.let { postName[it] }?.let { shortPostName(it) }
            when (kindOf(a)) {
                CardKind.MERGE -> {
                    // 견줄 상대: 같은 번호의 끝나지 않은 줄, 같은 공고 먼저 (CheckActions.mergeTarget)
                    val other = CheckActions.mergeTarget(a, all)
                    MergeCard(
                        a = a, where = where, other = other, modifier = cardModifier,
                        onOpen = { onOpenApplicant(a.id) },
                        onSeparate = { act("${a.name}: 다른 사람으로 두고 진행해요") { CheckActions.separate(db, a) } },
                        onMerge = { o ->
                            val done = if (CheckActions.samePost(a, o)) "${a.name}·${o.name}: 같은 사람으로 합쳤어요"
                            else "${a.name}·${o.name}: 같은 사람으로 이었어요 — 다른 공고라 줄은 따로 둬요"
                            act(done) { CheckActions.merge(db, a, o) }
                        },
                    )
                }
                CardKind.POST_NOT_FOUND -> PostNotFoundCard(
                    a = a, livePostings = livePostings, postName = postName, modifier = cardModifier,
                    onOpen = { onOpenApplicant(a.id) },
                    onPick = { p -> act("${a.name}: '${p.title}' 공고에 붙였어요") { CheckActions.pickPosting(db, a, p) } },
                    onRegister = {
                        act("'${a.postTitle}'를 직접 올린 공고로 등록하고 기다리던 지원자를 붙였어요") { CheckActions.registerManualPost(db, a) }
                    },
                )
                CardKind.STAFF_CHECK -> StaffCheckCard(
                    a = a, where = where, modifier = cardModifier,
                    onOpen = { onOpenApplicant(a.id) },
                    onHandBack = { act("${a.name}: 앱에 다시 맡겼어요") { CheckActions.handBack(db, a) } },
                )
                CardKind.OTHER_HOLD -> OtherHoldCard(a, where, cardModifier) { onOpenApplicant(a.id) }
            }
        }
        items(problemPostings, key = { "p${it.id}" }) { p ->
            PostingProblemCard(p, postName[p.standingPostId], Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** 카드 틀 (노란 테두리 = 사람이 볼 것): 위 줄(누구 · 어느 공고 · 상태)과 내용 */
@Composable
private fun CheckCard(
    who: String,
    where: String,
    status: String,
    modifier: Modifier = Modifier,
    tone: Tone = statusTone(status),
    onOpen: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier, border = Palette.AmberLine, spacing = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                buildAnnotatedString {
                    append(who)
                    if (where.isNotBlank()) {
                        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Palette.Muted)) { append(" · $where") }
                    }
                },
                style = AppText.Name,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Pill(status, tone)
        }
        content()
    }
}

/** 이유 한 단락: 굵은 제목 · 설명 */
@Composable
private fun Reason(title: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Palette.Ink)) { append(title) }
            append(" · $text")
        },
        style = AppText.Small.copy(lineHeight = 20.sp),
        color = Palette.Ink2,
    )
}

/** 버튼 두 개 나란히 */
@Composable
private fun TwoButtons(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit, rightEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(left, onClick = onLeft, modifier = Modifier.weight(1f))
        AppButton(right, onClick = onRight, modifier = Modifier.weight(1f), kind = ButtonKind.DARK, enabled = rightEnabled)
    }
}

/** 합치기 보류: 같은 번호의 다른 지원 건 — 다른 사람(따로) / 같은 사람(합침) */
@Composable
private fun MergeCard(
    a: Applicant,
    where: String?,
    other: Applicant?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onSeparate: () -> Unit,
    onMerge: (Applicant) -> Unit,
) {
    CheckCard(a.name, where ?: a.postTitle, a.status, modifier, onOpen = onOpen) {
        val text = when {
            other == null -> "같은 번호인 다른 지원 건을 못 찾았어요. 정하기 전까지 자동 문자를 보내지 않아요."
            else -> {
                val samePost = CheckActions.samePost(a, other)
                val place = if (samePost) "같은 공고에" else "다른 공고('${other.postTitle.ifBlank { "공고명 없음" }}')에"
                val names = if (other.name != a.name) "${withWaGwa(other.name)} 이름이 달라요." else "${other.name} 건이 있어요."
                "$place 같은 번호로 지원한 $names 정하기 전까지 자동 문자를 보내지 않아요."
            }
        }
        Reason("합치기 확인", text)
        // 같은 공고면 합침(나중 줄을 '합침'), 다른 공고면 연결(줄은 따로 두고 같은 사람으로 봄) — DB 설계 '합치기 기록'
        val same = if (other != null && !CheckActions.samePost(a, other)) "같은 사람 (연결)" else "같은 사람 (합침)"
        TwoButtons("다른 사람 (따로)", onSeparate, same, { if (other != null) onMerge(other) }, rightEnabled = other != null)
    }
}

/** 공고 못 찾음: 지금 올라가 있는 글 중에서 고르기 / 직접 올린 공고 등록 */
@Composable
private fun PostNotFoundCard(
    a: Applicant,
    livePostings: List<Posting>,
    postName: Map<Long, String>,
    modifier: Modifier,
    onOpen: () -> Unit,
    onPick: (Posting) -> Unit,
    onRegister: () -> Unit,
) {
    var picking by remember(a.id) { mutableStateOf(false) }
    CheckCard(a.name, if (a.postTitle.isNotBlank()) "'${a.postTitle}'" else "공고명 없음", a.status, modifier, onOpen = onOpen) {
        Reason(
            HoldReason.POST_NOT_FOUND,
            "알림의 공고명과 같은 글이 앱에 없어요. 지금 올라가 있는 글 중에서 고르거나, 당근에 직접 올린 공고면 등록해 주세요. 그 전엔 자동 메시지를 보내지 않아요.",
        )
        TwoButtons(
            if (picking) "고르기 닫기" else "공고 고르기", { picking = !picking },
            "직접 올린 공고 등록", onRegister, rightEnabled = a.postTitle.isNotBlank(),
        )
        if (picking) {
            if (livePostings.isEmpty()) {
                NoteBox("지금 올라가 있는 글이 없어요 — '직접 올린 공고 등록'을 쓰세요", Tone.GRAY)
            }
            livePostings.forEach { p ->
                AppCard(radius = 12.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), spacing = 2.dp, onClick = { onPick(p) }) {
                    Text(p.title.ifBlank { "제목 없음" }, style = AppText.BodyStrong, color = Palette.Ink)
                    Text(
                        "${postName[p.standingPostId] ?: "상시 공고"} · ${p.round}회차" + (p.postedAt?.let { " · ${formatDate(it)} 게시" } ?: ""),
                        style = AppText.Caption,
                        color = Palette.Muted,
                    )
                }
            }
        }
        Text("등록하면 이 공고명으로 기다리던 지원자가 다 붙어요", style = AppText.Caption, color = Palette.Muted)
    }
}

/** 담당자 확인: 예외 주제·번호 문제 — 직접 답한 뒤 앱에 다시 맡기기 */
@Composable
private fun StaffCheckCard(a: Applicant, where: String?, modifier: Modifier, onOpen: () -> Unit, onHandBack: () -> Unit) {
    CheckCard(a.name, where ?: a.postTitle, a.status, modifier, onOpen = onOpen) {
        val topic = a.reason.removePrefix("예외 주제: ").ifBlank { "예외" }
        val back = a.statusBeforeHold.ifBlank { ApplicantStatus.NEW }
        Reason(topic, "이 대화는 자동 응답을 멈췄어요. 직접 답한 뒤 다시 맡기면 ${withEuro(back)} 돌아가요.")
        AppButton("직접 답한 뒤 앱에 다시 맡기기", onClick = onHandBack, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

/** 그 밖 보류 (연락 중 확인이 안 된 보류 등) — 카드의 버튼은 화면 설계 '정할 것' */
@Composable
private fun OtherHoldCard(a: Applicant, where: String?, modifier: Modifier, onOpen: () -> Unit) {
    CheckCard(a.name, where ?: a.postTitle, a.status, modifier, onOpen = onOpen) {
        Reason("보류", a.reason.ifBlank { "이유가 적혀 있지 않아요" })
        Text("이 카드의 버튼은 아직 정하지 않았어요 (화면 설계 '정할 것')", style = AppText.Caption, color = Palette.Muted)
    }
}

/** 닉네임이 달라 멈춘 당근 가져오기 (화면 설계 '확인 필요' — 누르면 설정 '계정'에서 같은 계정인지 다른 계정인지 정함) */
@Composable
private fun NicknameCard(nickname: String, modifier: Modifier, onOpenSettings: () -> Unit) {
    CheckCard("당근 가져오기", "나의 당근 닉네임 '$nickname'", "멈춤", modifier, tone = Tone.AMBER, onOpen = onOpenSettings) {
        Reason("닉네임이 다름", "이 폰 당근의 닉네임이 앱에 등록된 계정과 달라 가져오기와 사람이 연 화면 맞추기를 멈췄어요. 같은 계정인지 다른 계정인지 정해 주세요.")
        AppButton("설정 '계정'에서 정하기", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
    }
}

/** 공고 쪽 확인 필요 (검사 걸림·결과 불명) — 카드의 내용과 버튼은 화면 설계 '정할 것' */
@Composable
private fun PostingProblemCard(p: Posting, standingName: String?, modifier: Modifier) {
    CheckCard("${standingName ?: p.title.ifBlank { "공고" }} ${p.round}회차", "", p.status, modifier, tone = Tone.AMBER) {
        Reason(p.status, p.reason.ifBlank { "이유가 적혀 있지 않아요" })
        Text("이 카드의 버튼은 아직 정하지 않았어요 (화면 설계 '정할 것')", style = AppText.Caption, color = Palette.Muted)
    }
}
