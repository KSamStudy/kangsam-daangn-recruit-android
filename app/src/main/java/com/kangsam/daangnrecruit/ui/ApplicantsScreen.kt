package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.FeatureSettings
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.reply.LiveReply
import com.kangsam.daangnrecruit.reply.MessageCare
import com.kangsam.daangnrecruit.reply.ReplyScope
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette

// 2. 지원자 — 공고별 목록과 응대 켜기·끄기 (화면 설계 '화면별 기능')
// 보일 지원자를 고르는 규칙(끝난 지원은 안 보임, 공고별 칸)은 ApplicantView. '실제 발송 시험' 칸은 개발 중에만 (LiveTestPanel)

/** 목록 필터 (고른 공고 안에서) */
private enum class ApplicantFilter(val label: String) {
    TO_HANDLE("처리할 것"), CHECK("확인 필요"), HANDED_OVER("넘김")
}

private fun matches(a: Applicant, f: ApplicantFilter): Boolean = when (f) {
    ApplicantFilter.TO_HANDLE -> ApplicantView.toHandle(a)
    ApplicantFilter.CHECK -> a.status in ApplicantView.CHECK
    ApplicantFilter.HANDED_OVER -> a.status == ApplicantStatus.HANDED_OVER
}

@Composable
fun ApplicantsScreen(onOpenApplicant: (Long) -> Unit, onGoCheck: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val applicants by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val postings by remember { db.postDao().observePostings() }.collectAsState(initial = emptyList())
    val checkCount = rememberCheckCount()
    var filter by rememberSaveable { mutableStateOf(ApplicantFilter.TO_HANDLE) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var daangnOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.DAANGN_REPLY)) }
    var smsOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.SMS_REPLY)) }

    // 공고 칸: 공고마다 보이는 지원자 수 (끝난 지원 빼고 — 당근 지원자 목록의 수와 같음). 처음엔 지금 올라가 있는 공고
    val groups = ApplicantView.groups(applicants, posts, postings)
    val group = groups.firstOrNull { it.key == groupKey } ?: groups.firstOrNull()
    val inGroup = group?.applicants.orEmpty()
    val shown = inGroup.filter { matches(it, filter) }
    val appliedToday = applicants.count { isToday(it.appliedAt) }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            ScreenHeader("지원자", "오늘 ${appliedToday}명 지원 · 확인 필요 ${checkCount}건")
        }

        // 위 스위치 2개 (기능 끄는 곳: 당근 응대 · 문자 응대)
        item(key = "switches") {
            AppCard(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                radius = 14.dp,
                padding = PaddingValues(start = 14.dp, end = 8.dp),
                spacing = 0.dp,
            ) {
                SwitchRow("당근 응대", "첫 메시지 · 번호 받기", daangnOn, onChange = {
                    daangnOn = it
                    FeatureSettings.set(context, FeatureSettings.DAANGN_REPLY, it)
                    if (it) LiveReply.resume(context) // 실제 발송 시험: 꺼져 있던 사이 남은 응대 할 일을 이어서
                })
                HLine()
                SwitchRow("문자 응대", "인적사항·가능 시간 받기 · 넘기기", smsOn, onChange = {
                    smsOn = it
                    FeatureSettings.set(context, FeatureSettings.SMS_REPLY, it)
                })
            }
        }

        // 개발 중에만: 실제 발송 시험 (테스트 폰에만)
        item(key = "live") {
            LiveTestPanel(
                daangnOn = daangnOn,
                applicants = applicants,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }

        if (groups.isNotEmpty()) {
            item(key = "tabs") {
                PostTabs(
                    groups = groups,
                    selectedKey = group?.key,
                    onSelect = { groupKey = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
            }
        }

        // 필터 (고른 공고 안에서): 처리할 것 · 확인 필요(보류·담당자 확인) · 넘김
        item(key = "filters") {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ApplicantFilter.entries.forEach { f ->
                        FilterPill("${f.label} ${inGroup.count { matches(it, f) }}", selected = filter == f, onClick = { filter = f })
                    }
                }
                Text(
                    "끝난 지원(종료·합침·미응답)은 안 보여요",
                    style = AppText.Caption,
                    color = Palette.Muted,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                )
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyNote(
                    when {
                        groups.isEmpty() -> "아직 지원자가 없어요 — 지원 알림이 오거나 설정에서 '당근에서 가져오기'를 하면 보여요"
                        filter == ApplicantFilter.TO_HANDLE -> "이 공고에서 지금 처리할 지원자가 없어요"
                        else -> "이 공고에 '${filter.label}' 지원자가 없어요"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        items(shown, key = { it.id }) { a ->
            ApplicantRow(
                a = a,
                postName = postNameOf(a, group),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    // 보류·담당자 확인은 확인 필요로, 나머지는 지원자 상세로
                    if (a.status in ApplicantView.CHECK) onGoCheck() else onOpenApplicant(a.id)
                },
            )
        }
    }
}

/** 공고 칸: 공고마다 '공고 이름 N명' (고른 칸은 초록 밑줄). 칸이 많으면 옆으로 넘기고, 고른 칸이 가려져 있으면 보이게 옮김 */
@Composable
private fun PostTabs(groups: List<ApplicantView.PostGroup>, selectedKey: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val rowState = rememberLazyListState()
    val selectedIndex = groups.indexOfFirst { it.key == selectedKey }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val info = rowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyShown = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!fullyShown) rowState.animateScrollToItem(selectedIndex)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Palette.TabLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
    ) {
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(groups, key = { it.key }) { g ->
                val selected = g.key == selectedKey
                val lost = g.key == ApplicantView.NO_POST
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable(role = Role.Tab) { onSelect(g.key) }
                        .semantics { this.selected = selected }
                        .drawBehind {
                            if (selected) {
                                val y = size.height - 1.dp.toPx()
                                drawLine(Palette.Green, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                            }
                        }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        buildAnnotatedString {
                            append(g.label + " ")
                            if (lost) {
                                withStyle(SpanStyle(color = Palette.Amber, fontWeight = FontWeight.SemiBold)) { append("${g.applicants.size}명") }
                            } else {
                                append("${g.applicants.size}명")
                            }
                        },
                        style = if (selected) AppText.Body.copy(fontWeight = FontWeight.Bold) else AppText.BodyMedium,
                        color = if (selected) Palette.GreenDark else Palette.Muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 목록 한 줄: 이름·동네, 상태 / 지원한 공고 · 지금 하는 일, '연락 중' 표시, 마지막 대화 시각(없으면 지원 시각) */
@Composable
private fun ApplicantRow(a: Applicant, postName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier,
        radius = 14.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        spacing = 6.dp,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    append(a.name)
                    withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Palette.Muted)) {
                        if (a.area.isNotBlank()) append(" · ${a.area}")
                        sampleTag(a)?.let { append("  $it") }
                    }
                },
                style = AppText.Name,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusPill(a.status)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$postName · ${nowDoing(a)}",
                style = AppText.Small,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (a.contactingMark.isNotBlank()) Tag("연락 중")
            Text(formatClock(a.lastMessageAt ?: a.appliedAt), style = AppText.Small, color = Palette.Muted, maxLines = 1)
        }
    }
}

/** 지원한 공고 이름 (목록 한 줄 앞부분): 고른 공고 칸 이름, 공고 못 찾음이면 알림의 공고명 */
private fun postNameOf(a: Applicant, group: ApplicantView.PostGroup?): String = when {
    group != null && group.key != ApplicantView.NO_POST -> shortPostName(group.label.removeSuffix(" (예시)"))
    a.postTitle.isNotBlank() -> "'${a.postTitle}'"
    else -> "공고명 없음"
}

/** 예시·테스트 줄 표시 (개발 중) */
private fun sampleTag(a: Applicant): String? = when {
    a.isSample -> "예시"
    a.sourceKey.startsWith("com.android.shell|") || a.rawText.startsWith("테스트") -> "테스트"
    else -> null
}

/** 받은 정보 5개 (문자로 받는 항목 — 지원자 상세와 같이 씀) */
internal val INFO_LABELS = listOf("성함", "대학·학과", "교육 경험", "가능 요일·시간대", "가능 기간")

internal fun infoValues(a: Applicant) = listOf(a.realName, a.school, a.experience, a.availableTime, a.availablePeriod)

/**
 * 앱이 맡은 지원인지 (화면용 — 정하는 곳은 ReplyScope 한 곳). 앱이 안 맡은 지원(당근 응대를 켜기 전·꺼져 있던 동안 들어온 지원,
 * 개발 중엔 실제 발송 시험 대상이 아닌 지원)은 사람이 응대 — 앱이 할 일을 보이지 않음.
 * 예시 데이터는 화면 설계의 예시 상황(앱이 맡은 지원)대로 보여 줌 (예시에는 보내지 않음 — ReplyRules.notAutoReason)
 */
internal fun appOwns(a: Applicant): Boolean = a.isSample || ReplyScope.owns(a)

/** 앱이 안 맡은 지원의 지금 하는 일 */
internal const val NOT_OWNED_DOING = "앱이 안 맡은 지원 · 사람이 응대"

/** 지금 하는 일 한 줄 (상태로) — 목록과 지원자 상세가 같이 씀. 앱이 응대하는 상태인데 앱이 안 맡은 지원이면 그렇다고 */
internal fun nowDoing(a: Applicant): String =
    if (a.status in MessageCare.HANDLING && !appOwns(a)) NOT_OWNED_DOING else statusDoing(a)

private fun statusDoing(a: Applicant): String = when (a.status) {
    ApplicantStatus.NEW -> "첫 메시지 보낼 차례"
    ApplicantStatus.FIRST_SENT ->
        "번호 기다리는 중" + (Regex("번호 다시 요청 (\\d+)번").find(a.reason)?.let { " (다시 요청 ${it.groupValues[1]}번)" } ?: "")
    ApplicantStatus.CONTACT -> "첫 문자 보낼 차례"
    ApplicantStatus.SMS -> {
        val missing = INFO_LABELS.zip(infoValues(a)).filter { it.second.isBlank() }.map { it.first }
        when {
            missing.size == INFO_LABELS.size -> "인적사항 답 기다리는 중"
            missing.isEmpty() -> "받을 정보를 다 받음"
            else -> missing.joinToString("·") + " 답 기다리는 중"
        }
    }
    ApplicantStatus.TIME -> "담당자에게 넘길 차례"
    ApplicantStatus.HANDED_OVER -> if (a.linearTicket.isNotBlank()) "넘김 → ${a.linearTicket}" else "담당자에게 넘김"
    ApplicantStatus.STAFF_CHECK -> a.reason.removePrefix("예외 주제: ").ifBlank { "예외" } + " → 자동 응답 멈춤"
    ApplicantStatus.STAFF_HANDLING -> if (a.reason.isBlank()) "자동 응답 멈춤" else "${a.reason} · 자동 응답 멈춤"
    ApplicantStatus.HOLD -> when {
        a.reason.startsWith(HoldReason.POST_NOT_FOUND) -> "공고 못 찾음 → 공고 고르기"
        a.reason.contains(HoldReason.MERGE) || a.sameCandidate.isNotBlank() ->
            "같은 번호" + (if (a.sameCandidate.isNotBlank() && a.sameCandidate != a.name) ", 이름 다름" else "") + " → 합치기 확인"
        else -> a.reason.ifBlank { "보류" }
    }
    else -> a.reason.ifBlank { a.status }
}
