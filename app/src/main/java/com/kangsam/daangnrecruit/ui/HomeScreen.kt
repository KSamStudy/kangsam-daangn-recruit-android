package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.FeatureSettings
import com.kangsam.daangnrecruit.HoldReason
import com.kangsam.daangnrecruit.Message
import com.kangsam.daangnrecruit.PostActions
import com.kangsam.daangnrecruit.Posting
import com.kangsam.daangnrecruit.StandingPost
import com.kangsam.daangnrecruit.reply.PhoneNumber
import com.kangsam.daangnrecruit.reply.ReplyRules
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette

// 1. 홈 — 지금 상황 보기 (화면 설계 '화면별 기능'). 켜고 끄는 버튼은 두지 않음 (켜고 끄기는 공고·지원자 화면에서)

@Composable
fun HomeScreen(
    refreshTick: Int,
    onOpenTab: (AppTab) -> Unit,
    onOpenApplicant: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val now = remember(refreshTick) { System.currentTimeMillis() }
    val since = remember(refreshTick) { now - HomeSummary.RECENT_MS }
    val applicants by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val postings by remember { db.postDao().observePostings() }.collectAsState(initial = emptyList())
    val messages by remember(since) { db.messageDao().observeSince(since) }.collectAsState(initial = emptyList())
    val checkCount by remember { db.checkDao().observeCount() }.collectAsState(initial = 0)
    // 기능 스위치는 앱 설정 값이라 화면에 들어올 때·앱으로 돌아올 때 다시 읽음
    val switches = remember(refreshTick) {
        HomeSummary.Switches(
            autoPost = FeatureSettings.isOn(context, FeatureSettings.AUTO_POST),
            daangnReply = FeatureSettings.isOn(context, FeatureSettings.DAANGN_REPLY),
            smsReply = FeatureSettings.isOn(context, FeatureSettings.SMS_REPLY),
            allStop = FeatureSettings.isOn(context, FeatureSettings.ALL_STOP),
        )
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        HomeHeader(HomeSummary.appState(switches))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeatureCard(
                switches = switches,
                autoPostLine = HomeSummary.autoPostLine(posts, postings, now),
                waitingNumber = HomeSummary.waitingNumber(applicants),
                waitingSms = HomeSummary.waitingSmsReply(applicants),
                onOpenTab = onOpenTab,
            )
            TodayCard(
                today = HomeSummary.today(applicants, messages, now),
                checkCount = checkCount,
                now = now,
                onOpenTab = onOpenTab,
            )
            ActivityCard(
                items = HomeSummary.activities(applicants, posts, postings, messages, since, now),
                now = now,
                onOpen = { target ->
                    when (target) {
                        is HomeSummary.Target.Person -> onOpenApplicant(target.id)
                        is HomeSummary.Target.Tab -> onOpenTab(target.tab)
                    }
                },
            )
        }
    }
}

/** 맨 위: 앱 이름 + 앱 상태 (자동 동작 중 · 일부 꺼짐 · 전체 멈춤) */
@Composable
private fun HomeHeader(state: HomeSummary.AppState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Kangsam Daangn Recruit", style = AppText.Caption.copy(letterSpacing = 0.24.sp), color = Palette.Muted)
            Text("당근 구인 자동화", style = AppText.ScreenTitle, color = Palette.Ink)
        }
        val (bg, fg, dot) = when (state) {
            HomeSummary.AppState.RUNNING -> Triple(Palette.GreenSoft, Palette.GreenDark, Palette.Green)
            HomeSummary.AppState.PARTLY_OFF -> Triple(Palette.GraySoft, Palette.Muted2, Palette.LineStrong)
            HomeSummary.AppState.ALL_STOPPED -> Triple(Palette.RedSoft, Palette.Red, Palette.Red)
        }
        Row(
            modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(state.label, style = AppText.SmallStrong, color = fg, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** 기능: 자동 게시·당근 응대·문자 응대가 켜졌는지와 한 줄 현황. 누르면 그 기능을 끄는 화면으로 */
@Composable
private fun FeatureCard(
    switches: HomeSummary.Switches,
    autoPostLine: String,
    waitingNumber: Int,
    waitingSms: Int,
    onOpenTab: (AppTab) -> Unit,
) {
    AppCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), spacing = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("기능", style = AppText.Section, color = Palette.Ink, modifier = Modifier.weight(1f))
            Text("켜고 끄기는 공고·지원자 화면에서", style = AppText.Caption, color = Palette.Muted)
        }
        FeatureLine("자동 게시", autoPostLine, switches.autoPost, switches.allStop) { onOpenTab(AppTab.POSTS) }
        FeatureLine(
            "당근 응대",
            if (waitingNumber > 0) "번호 기다리는 중 ${waitingNumber}명" else "번호 기다리는 사람 없음",
            switches.daangnReply, switches.allStop,
        ) { onOpenTab(AppTab.APPLICANTS) }
        FeatureLine(
            "문자 응대",
            if (waitingSms > 0) "답 기다리는 중 ${waitingSms}명" else "답 기다리는 사람 없음",
            switches.smsReply, switches.allStop,
        ) { onOpenTab(AppTab.APPLICANTS) }
    }
}

@Composable
private fun FeatureLine(title: String, line: String, on: Boolean, allStop: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = AppText.BodyStrong, color = Palette.Ink)
                Text(line, style = AppText.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                allStop -> Pill("멈춤", Tone.RED)
                on -> Pill("켜짐", Tone.GREEN)
                else -> Pill("꺼짐", Tone.GRAY)
            }
        }
    }
}

/** 오늘: 새 지원·연락처 확보·넘김 건수, 확인 필요 건수 (누르면 확인 필요) */
@Composable
private fun TodayCard(today: HomeSummary.Today, checkCount: Int, now: Long, onOpenTab: (AppTab) -> Unit) {
    AppCard(padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp), spacing = 10.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘", style = AppText.Section, color = Palette.Ink, modifier = Modifier.weight(1f))
            Text(formatDay(now), style = AppText.Caption, color = Palette.Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tile = Modifier.weight(1f)
            TodayTile(today.applied, "새 지원", tile) { onOpenTab(AppTab.APPLICANTS) }
            TodayTile(today.contacts, "연락처 확보", tile) { onOpenTab(AppTab.APPLICANTS) }
            TodayTile(today.handedOver, "넘김", tile) { onOpenTab(AppTab.APPLICANTS) }
            // 확인할 게 있을 때만 노란 칸 (0건이면 다른 칸과 같게 — 아래 탭 빨간 숫자도 0이면 없음)
            TodayTile(checkCount, "확인 필요", tile, highlight = checkCount > 0) { onOpenTab(AppTab.CHECK) }
        }
    }
}

@Composable
private fun TodayTile(count: Int, label: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val bg = if (highlight) Palette.AmberSoft else Palette.Bg
    val numberColor = if (highlight) Palette.Amber else Palette.Ink
    val labelColor = if (highlight) Palette.Amber else Palette.Muted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$count", style = AppText.Number, color = numberColor)
        Text(
            label,
            style = AppText.Caption.copy(letterSpacing = (-0.24).sp),
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 최근 활동: 앱이 보낸 메시지, 게시·마감, 30분 지켜보기, 넘김, 확인 필요가 생긴 일 (최근 것이 위로) */
@Composable
private fun ActivityCard(items: List<HomeSummary.Activity>, now: Long, onOpen: (HomeSummary.Target) -> Unit) {
    AppCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), spacing = 0.dp) {
        Text("최근 활동", style = AppText.Section, color = Palette.Ink, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        if (items.isEmpty()) {
            HLine()
            Text(
                "최근 24시간 동안 앱이 한 일이 없어요",
                style = AppText.Small,
                color = Palette.Muted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        // 어제 것이 섞이면 시각 칸을 넓힘 ('어제 21:40')
        val timeWidth = if (items.all { isToday(it.time, now) }) 40.dp else 66.dp
        items.forEach { item ->
            HLine()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.target != null) { item.target?.let(onOpen) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (isToday(item.time, now)) formatHourMinute(item.time) else "어제 " + formatHourMinute(item.time),
                    style = AppText.Small.copy(lineHeight = 19.sp),
                    color = Palette.Muted,
                    modifier = Modifier.width(timeWidth),
                )
                Text(
                    buildAnnotatedString {
                        append(item.text)
                        if (item.tag.isNotBlank()) {
                            append(" → ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = item.tagColor)) { append(item.tag) }
                        }
                    },
                    style = AppText.Small.copy(lineHeight = 19.sp),
                    color = Palette.Ink2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 홈에 보일 값 고르기 (순수 코드 — 화면 그리기와 나눔. 세는 기준을 바꿀 때 여기만).
 * 지원자 수는 ApplicantView 기준 (끝난 지원은 안 셈)
 */
internal object HomeSummary {
    const val RECENT_MS = 24 * 60 * 60_000L // 최근 활동: 24시간
    private const val WATCH_MS = 30 * 60_000L   // 30분 지켜보기
    private const val MAX_ACTIVITIES = 8

    data class Switches(val autoPost: Boolean, val daangnReply: Boolean, val smsReply: Boolean, val allStop: Boolean)

    enum class AppState(val label: String) { RUNNING("자동 동작 중"), PARTLY_OFF("일부 꺼짐"), ALL_STOPPED("전체 멈춤") }

    fun appState(s: Switches): AppState = when {
        s.allStop -> AppState.ALL_STOPPED
        s.autoPost && s.daangnReply && s.smsReply -> AppState.RUNNING
        else -> AppState.PARTLY_OFF
    }

    /** 자동 게시 한 줄: 게시 중 · 마감 기다림 · 다음 게시 */
    fun autoPostLine(posts: List<StandingPost>, postings: List<Posting>, now: Long): String {
        // 직접 올릴 때만인 공고는 앱이 올리지 않아서 뺌
        val on = posts.filter { it.status == "켜짐" && it.postWhen != PostActions.POST_MANUAL }
        if (on.isEmpty()) return "앱이 올리는 켜진 상시 공고 없음"
        val byId = on.associateBy { it.id }
        val live = postings.filter { it.status == "게시됨" && it.standingPostId in byId }
        if (live.isNotEmpty()) {
            val p = live.first()
            val days = p.postedAt?.let { (now - it) / 86_400_000L + 1 } ?: 0L
            val name = byId[p.standingPostId]?.name.orEmpty()
            return "$name ${p.round}회차 게시 중" + (if (days > 0) " · ${days}일째" else "") +
                (if (live.size > 1) " 외 ${live.size - 1}개" else "")
        }
        val unconfirmed = postings.firstOrNull {
            it.standingPostId in byId && it.status == "마감" && it.closeCheckedAt == null && it.closeCheckMethod == PostActions.CLOSE_UNCHECKED
        }
        if (unconfirmed != null) return "${byId[unconfirmed.standingPostId]?.name} ${unconfirmed.round}회차 마감 — '내 공고' 확인 기다림"
        val next = on.mapNotNull { p -> p.nextPostAt?.let { p to it } }.minByOrNull { it.second }
        if (next != null) return "${next.first.name} 다음 게시 ${formatClock(next.second, now)}"
        return "올라가 있는 글 없음"
    }

    /** 당근 응대: 번호를 기다리는 사람 (첫 메시지 보냄) */
    fun waitingNumber(applicants: List<Applicant>): Int =
        applicants.count { ApplicantView.isShown(it) && it.status == ApplicantStatus.FIRST_SENT }

    /** 문자 응대: 답을 기다리는 사람 (문자 진행 중) */
    fun waitingSmsReply(applicants: List<Applicant>): Int =
        applicants.count { ApplicantView.isShown(it) && it.status == ApplicantStatus.SMS }

    data class Today(val applied: Int, val contacts: Int, val handedOver: Int)

    /**
     * 오늘 건수. 새 지원 = 오늘 지원한 사람, 넘김 = 오늘 넘긴 사람,
     * 연락처 확보 = 오늘 번호를 보내온 사람 (지원자 메시지에 그 사람 연락처가 처음 나온 때가 오늘 — 연락처를 받은 때는 표에 따로 없어서 대화로 셈)
     */
    fun today(applicants: List<Applicant>, messages: List<Message>, now: Long): Today {
        val byApplicant = messages.groupBy { it.applicantId }
        return Today(
            applied = applicants.count { isToday(it.appliedAt, now) },
            contacts = applicants.count { a ->
                a.phone.isNotBlank() && byApplicant[a.id].orEmpty()
                    .filter { it.sender == "지원자" && mentionsNumber(it.content, a) }
                    .minByOrNull { it.time }
                    ?.let { isToday(it.time, now) } == true
            },
            handedOver = applicants.count { a -> a.handedOverAt?.let { isToday(it, now) } == true },
        )
    }

    /** 메시지에 이 지원자 연락처가 있는지 (번호 찾기 규칙 + 가린 번호는 끝 4자리) */
    fun mentionsNumber(text: String, a: Applicant): Boolean {
        if (a.phoneKey.isNotBlank() && PhoneNumber.find(text).any { it.key == a.phoneKey }) return true
        val last4 = a.phone.filter(Char::isDigit).takeLast(4)
        return last4.length == 4 && text.contains("010") && text.contains(last4)
    }

    /** 누르면 갈 곳 */
    sealed interface Target {
        data class Person(val id: Long) : Target
        data class Tab(val tab: AppTab) : Target
    }

    /** 활동 한 줄: 시각 · 글 · (→ 굵은 표시) · 누르면 갈 곳 */
    data class Activity(val time: Long, val text: String, val tag: String = "", val tagColor: Color = Palette.Amber, val target: Target? = null)

    fun activities(
        applicants: List<Applicant>,
        posts: List<StandingPost>,
        postings: List<Posting>,
        messages: List<Message>,
        since: Long,
        now: Long,
    ): List<Activity> {
        val people = applicants.associateBy { it.id }
        val postName = posts.associate { it.id to it.name }
        val out = mutableListOf<Activity>()

        // 앱이 보낸 메시지 (보낸 방법: 앱 자동 · 결과 불명)
        messages.filter { it.sender == "우리" && it.time in since..now }.forEach { m ->
            val name = people[m.applicantId]?.name ?: return@forEach
            val target = Target.Person(m.applicantId)
            when (m.sendMethod) {
                ReplyRules.SENT_BY_APP -> {
                    val what = when {
                        m.channel == "문자" && m.content.startsWith("선생님 안녕하세요") -> "첫 문자 보냄" // 문자 첫 문장은 고정 (문구 규칙)
                        m.channel == "문자" -> "문자 보냄"
                        else -> "당근 메시지 보냄"
                    }
                    out.add(Activity(m.time, "${name}에게 $what", target = target))
                }
                "결과 불명" -> out.add(Activity(m.time, "${name}에게 보낸 ${m.channel} 메시지", "결과 불명", target = target))
            }
        }

        // 게시·30분 지켜보기·마감, 공고 쪽 확인 필요
        postings.forEach { p ->
            val name = postName[p.standingPostId] ?: p.title
            val label = "$name ${p.round}회차"
            p.postedAt?.let { at ->
                if (at in since..now) out.add(Activity(at, "$label 게시", target = Target.Tab(AppTab.POSTS)))
                val watched = at + WATCH_MS
                if (p.watchResult.isNotBlank() && watched in since..now) {
                    if (p.watchResult == "통과") out.add(Activity(watched, "$label 30분 지켜보기 통과", target = Target.Tab(AppTab.POSTS)))
                    else out.add(Activity(watched, "$label 30분 지켜보기", p.watchResult, target = Target.Tab(AppTab.POSTS)))
                }
            }
            p.closedAt?.let { at ->
                if (at in since..now) {
                    out.add(Activity(at, "$label 마감" + (if (p.closedBy.isNotBlank()) " (${p.closedBy})" else ""), target = Target.Tab(AppTab.POSTS)))
                }
            }
            if (p.status == "검사 걸림" || p.status == "결과 불명") {
                val at = p.scheduledAt ?: p.postedAt
                if (at != null && at in since..now) out.add(Activity(at, label, p.status, target = Target.Tab(AppTab.CHECK)))
            }
        }

        // 넘김
        applicants.forEach { a ->
            val at = a.handedOverAt ?: return@forEach
            if (at in since..now) {
                val to = if (a.linearTicket.isNotBlank()) "리니어 ${a.linearTicket}" else "담당자"
                out.add(Activity(at, "${a.name} 넘김", to, tagColor = Palette.GreenDark, target = Target.Person(a.id)))
            }
        }

        // 확인 필요가 생긴 일 — 상태가 바뀐 때는 표에 따로 없어서, 그 일을 부른 메시지 시각(없으면 지원 시각)으로 봄
        applicants.filter { it.status in ApplicantView.CHECK }.forEach { a ->
            val mine = messages.filter { it.applicantId == a.id && it.sender == "지원자" }
            val at = when {
                a.status == ApplicantStatus.STAFF_CHECK -> (mine.lastOrNull { it.appHandling == "넘김" } ?: mine.maxByOrNull { it.time })?.time
                a.reason.startsWith(HoldReason.POST_NOT_FOUND) -> a.appliedAt
                else -> mine.maxByOrNull { it.time }?.time
            } ?: a.lastMessageAt ?: a.appliedAt
            if (at !in since..now) return@forEach
            val (text, tag) = when {
                a.status == ApplicantStatus.STAFF_CHECK -> "${a.name} ${a.reason.removePrefix("예외 주제: ").ifBlank { "예외" }}".trim() to "담당자 확인"
                a.reason.startsWith(HoldReason.POST_NOT_FOUND) -> "${a.name} 지원" to HoldReason.POST_NOT_FOUND
                a.reason.contains(HoldReason.MERGE) || a.sameCandidate.isNotBlank() -> "${a.name} 같은 번호" to HoldReason.MERGE
                else -> a.name to "보류"
            }
            out.add(Activity(at, text, tag, target = Target.Tab(AppTab.CHECK)))
        }

        return out.sortedByDescending { it.time }.take(MAX_ACTIVITIES)
    }
}
