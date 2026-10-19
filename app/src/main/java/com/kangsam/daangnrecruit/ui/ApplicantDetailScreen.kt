package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStore
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.Message
import com.kangsam.daangnrecruit.reply.DaangnReply
import com.kangsam.daangnrecruit.reply.MessageCare
import com.kangsam.daangnrecruit.reply.ReplyPlanner
import com.kangsam.daangnrecruit.reply.ReplyRules
import com.kangsam.daangnrecruit.reply.Templates
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import java.time.ZoneId
import kotlinx.coroutines.launch

// 3. 지원자 상세 — 한 사람의 정보와 대화 (화면 설계 '화면별 기능')
// 위(이름·동네·공고·상태) → 정보 → 대화(오른쪽 우리 · 왼쪽 지원자, 이어진 당근 메시지는 접어서) → 지금 상태(다음에 앱이 할 일) → 아래 '직접 연락하기'
// 개발 중에만: 당근 첫 메시지 드라이런 · '연락 중 풀기'

/** 대화 묶음: 날짜가 바뀌는 곳 · 이어진 당근 메시지(접어서 개수만) · 문자 하나 */
private sealed interface Block {
    val key: String

    data class Day(val time: Long) : Block {
        override val key = "day$time"
    }

    data class Daangn(val messages: List<Message>, val followedBySms: Boolean) : Block {
        override val key = "d${messages.first().id}"
    }

    data class Single(val message: Message) : Block {
        override val key = "m${message.id}"
    }
}

private fun toBlocks(messages: List<Message>, now: Long): List<Block> {
    val chunks = mutableListOf<Any>() // List<Message>(당근 묶음) 또는 Message
    var run = mutableListOf<Message>()
    for (m in messages) {
        if (m.channel == "당근") {
            if (run.isNotEmpty() && !sameDay(run.last().time, m.time)) {
                chunks.add(run)
                run = mutableListOf()
            }
            run.add(m)
        } else {
            if (run.isNotEmpty()) {
                chunks.add(run)
                run = mutableListOf()
            }
            chunks.add(m)
        }
    }
    if (run.isNotEmpty()) chunks.add(run)

    val blocks = mutableListOf<Block>()
    var lastDay: Long? = null
    chunks.forEachIndexed { i, c ->
        @Suppress("UNCHECKED_CAST")
        val first = if (c is Message) c else (c as List<Message>).first()
        // 날짜가 바뀌면 날짜 줄 (맨 앞은 오늘이 아닐 때만)
        if ((lastDay == null && !sameDay(first.time, now)) || (lastDay != null && !sameDay(lastDay!!, first.time))) {
            blocks.add(Block.Day(first.time))
        }
        lastDay = first.time
        if (c is Message) {
            blocks.add(Block.Single(c))
        } else {
            @Suppress("UNCHECKED_CAST")
            val list = c as List<Message>
            blocks.add(Block.Daangn(list, followedBySms = chunks.drop(i + 1).any { it is Message }))
        }
    }
    return blocks
}

@Composable
fun ApplicantDetailScreen(applicantId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val applicant by remember(applicantId) { db.applicantDao().observe(applicantId) }.collectAsState(initial = null)
    val messages by remember(applicantId) { db.messageDao().observeFor(applicantId) }.collectAsState(initial = emptyList())
    var expanded by remember(applicantId) { mutableStateOf(setOf<String>()) }

    val a = applicant
    if (a == null) {
        Column(modifier.fillMaxSize()) {
            DetailTopBar(title = "지원자", subtitle = "불러오는 중…", onBack = onBack, backLabel = "지원자 목록으로")
        }
        return
    }
    val samePerson by remember(a.phoneKey, a.id) { db.applicantDao().observeSamePerson(a.phoneKey, a.id) }
        .collectAsState(initial = emptyList())
    // 지원한 공고: 상시 공고 이름 (지원자 목록과 같게), 공고를 못 찾았으면 알림의 공고명
    val standingName by produceState<String?>(initialValue = null, a.standingPostId) {
        value = a.standingPostId?.let { db.postDao().getStandingPost(it)?.name }
    }
    val postLabel = standingName?.let { "${shortPostName(it)} 공고" }
        ?: if (a.postTitle.isNotBlank()) "'${a.postTitle}'" else "공고명 없음"
    val now = System.currentTimeMillis()
    val blocks = toBlocks(messages, now)
    val showFooter = ApplicantView.toHandle(a)

    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = a.name + if (a.isSample) " (예시)" else "",
            subtitle = listOf(a.area, postLabel).filter { it.isNotBlank() }.joinToString(" · "),
            onBack = onBack,
            backLabel = "지원자 목록으로",
        ) { StatusPill(a.status) }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showFooter) Modifier else Modifier.navigationBarsPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "info") { InfoCard(a, samePerson) }

            items(blocks, key = { it.key }) { block ->
                when (block) {
                    is Block.Day -> DaySeparator(block.time)
                    is Block.Single -> MessageItem(block.message)
                    is Block.Daangn -> {
                        val open = block.key in expanded
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DaangnRunCard(block, a, open) {
                                expanded = if (open) expanded - block.key else expanded + block.key
                            }
                            if (open) block.messages.forEach { MessageItem(it) }
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                item(key = "noTalk") {
                    Text(
                        "아직 대화가 없어요",
                        style = AppText.Small,
                        color = Palette.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }

            item(key = "now") { NowStateCard(a, messages) }

            // 개발 중에만: 당근 첫 메시지 드라이런 (예시 지원자엔 없음)
            if (!a.isSample) {
                item(key = "dryrun") { DryRunCard(a.id) }
            }
        }

        // 아래: 직접 연락하기 (앱이 아직 맡을 수 있는 지원자만 — 넘김·끝난 지원엔 없음)
        if (showFooter) ContactFooter(a)
    }
}

/** 정보: 연락처('DB에만 저장'), 받은 정보 5개 중 받은 것, 같은 사람. 넘김이면 가능 시간과 구인 티켓 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(a: Applicant, samePerson: List<Applicant>) {
    AppCard(radius = 14.dp, spacing = 8.dp) {
        InfoRow("연락처") {
            if (a.phone.isNotBlank()) {
                Text(a.phone, style = AppText.BodyStrong, color = Palette.Ink, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clip(CircleShape).background(Palette.Bg).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = Palette.Muted2, modifier = Modifier.size(14.dp))
                    Text("DB에만 저장", style = AppText.Caption, color = Palette.Muted2, modifier = Modifier.padding(start = 4.dp))
                }
            } else {
                Text("아직 없음", style = AppText.Body, color = Palette.Muted, modifier = Modifier.weight(1f))
            }
        }
        if (a.status == ApplicantStatus.HANDED_OVER) {
            InfoRow("가능 시간") {
                Text(
                    listOf(a.availableTime, a.availablePeriod).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                    style = AppText.BodyStrong,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
            }
            InfoRow("구인 티켓") {
                if (a.linearTicket.isNotBlank()) {
                    Icon(AppIcons.Ticket, contentDescription = null, tint = Palette.GreenDark, modifier = Modifier.size(16.dp))
                    Text("리니어 · ${a.linearTicket}", style = AppText.BodyStrong, color = Palette.GreenDark, modifier = Modifier.weight(1f))
                } else {
                    Text("없음", style = AppText.Body, color = Palette.Muted, modifier = Modifier.weight(1f))
                }
            }
        } else {
            InfoRow("받은 정보", alignTop = true) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    INFO_LABELS.zip(infoValues(a)).forEach { (label, value) -> InfoChip(label, received = value.isNotBlank()) }
                }
            }
            InfoRow("같은 사람") {
                val same = samePerson.map { "${it.name} (${it.status})" } +
                    listOfNotNull(a.sameCandidate.ifBlank { null }?.let { "후보: $it" })
                Text(
                    same.joinToString(", ").ifBlank { "없음" },
                    style = AppText.Small,
                    color = Palette.Ink2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 받은 정보 표시: 받았으면 초록, 아직이면 점선 */
@Composable
private fun InfoChip(label: String, received: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    if (received) {
        Text(
            label,
            style = AppText.CaptionMedium,
            color = Palette.GreenDark,
            modifier = Modifier.clip(shape).background(Palette.GreenSoft).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    } else {
        Text(
            label,
            style = AppText.Caption,
            color = Palette.Muted,
            modifier = Modifier.dashedBorder(Palette.Placeholder, 8.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 날짜 줄 (대화가 오늘이 아닐 때·날짜가 바뀔 때) */
@Composable
private fun DaySeparator(time: Long) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            formatDay(time),
            style = AppText.Caption,
            color = Palette.Muted,
            modifier = Modifier.clip(CircleShape).background(Palette.GraySoft).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** 이어진 당근 메시지: 개수·시각 범위와 요약 한 줄. 누르면 펼침 */
@Composable
private fun DaangnRunCard(block: Block.Daangn, a: Applicant, open: Boolean, onToggle: () -> Unit) {
    val first = block.messages.first().time
    val last = block.messages.last().time
    val range = if (first == last) formatHourMinute(first) else "${formatHourMinute(first)}–${formatHourMinute(last)}"
    AppCard(radius = 14.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("당근 메시지 ${block.messages.size}개 · $range", style = AppText.SmallStrong, color = Palette.GreenDark)
                Text(daangnSummary(block, a), style = AppText.Caption, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (open) AppIcons.ChevronUp else AppIcons.ChevronDown,
                contentDescription = if (open) "접기" else "펼치기",
                tint = Palette.LineStrong,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 당근 묶음 요약: 번호 받음 → "우리 마무리 말" → 당근 대화 끝 (문자로 넘어갔으면) */
private fun daangnSummary(block: Block.Daangn, a: Applicant): String {
    val run = block.messages
    val parts = mutableListOf<String>()
    val numberAt = run.indexOfFirst { it.sender == "지원자" && HomeSummary.mentionsNumber(it.content, a) }
    if (numberAt >= 0) {
        parts.add("번호 받음")
        run.drop(numberAt + 1).lastOrNull { it.sender == "우리" }?.let { parts.add("\"${lastSentence(it.content)}\"") }
    } else {
        val last = run.last()
        parts.add((if (last.sender == "우리") "우리" else "지원자") + " · \"${lastSentence(last.content)}\"")
        if (run.none { it.sender == "지원자" }) parts.add("답 기다림")
    }
    if (block.followedBySms) parts.add("당근 대화 끝")
    return parts.joinToString(" → ")
}

/** 메시지의 마지막 문장 (짧게) — '감사합니다! 문자로 연락드릴게요.' → '문자로 연락드릴게요' */
private fun lastSentence(text: String): String {
    val sentence = text.split('.', '!', '?', '\n').map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: text.trim()
    return if (sentence.length <= 22) sentence else sentence.take(22).trimEnd() + "…"
}

/** 메시지 하나: 우리는 오른쪽(보낸 방법), 지원자는 왼쪽. 지원자 메시지에 앱이 답하지 않았으면 아래에 앱 처리 */
@Composable
private fun MessageItem(m: Message) {
    val ours = m.sender == "우리"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxBubble = maxWidth * 0.86f
            val sms = m.channel == "문자"
            val (bg, metaColor, textColor) = when {
                ours && sms -> Triple(Palette.PurpleSoft, Palette.PurpleMeta, Palette.PurpleInk)
                ours -> Triple(Palette.GreenSoft, Palette.Green, Palette.GreenDark)
                else -> Triple(Palette.Surface, Palette.Muted, Palette.Ink)
            }
            val shape = if (ours) {
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
            } else {
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)
            }
            Column(
                modifier = Modifier
                    .align(if (ours) Alignment.CenterEnd else Alignment.CenterStart)
                    .widthIn(max = maxBubble)
                    .clip(shape)
                    .background(bg)
                    .then(if (ours) Modifier else Modifier.border(1.dp, Palette.Line, shape))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(bubbleMeta(m, ours), style = AppText.Tiny, color = metaColor)
                Text(m.content, style = AppText.Message, color = textColor)
            }
        }
        if (!ours && m.appHandling != "답함") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "앱 처리 · ${m.appHandling.ifBlank { "처리 전" }}",
                    style = AppText.CaptionMedium,
                    color = Palette.Muted2,
                    modifier = Modifier.clip(CircleShape).background(Palette.GraySoft).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 말풍선 위 한 줄: 문자 · 앱 자동 · 13:42 / 문자 · 지원자 · 14:38 */
private fun bubbleMeta(m: Message, ours: Boolean): String {
    val who = if (ours) m.sendMethod.ifBlank { "보낸 방법 모름" } else "지원자"
    return "${m.channel} · $who · ${formatHourMinute(m.time)}"
}

/**
 * 지금 상태: 앱이 기다리는 것(또는 넘김 결과)과 다음에 앱이 할 일 (무엇을 언제 보낼지, 지금 안 보내면 그 이유).
 * 계획은 ReplyPlanner (보내지 않음). '자세히'를 누르면 보낼 문구·발송 전 검사까지
 */
@Composable
private fun NowStateCard(a: Applicant, messages: List<Message>) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    var recent by remember { mutableStateOf<List<String>?>(null) }
    var showDetail by remember(a.id) { mutableStateOf(false) }
    LaunchedEffect(a.id, messages.size) {
        recent = db.messageDao().recentByMethod(ReplyRules.SENT_BY_APP, Templates.RECENT_TO_AVOID).map { it.content }
    }
    val avoid = recent
    val owned = appOwns(a)
    val plan = remember(a, messages, avoid, owned) {
        avoid?.let { ReplyPlanner.plan(a, messages, it, DaangnReply.switches(context), ZoneId.systemDefault(), owned) }
    }

    // 색: 문자 단계 보라 · 넘김 초록 · 사람이 볼 것 노랑 · 당근 단계 흰 바탕 · 끝 회색
    val handed = a.status == ApplicantStatus.HANDED_OVER
    val (bg, line, ink, sub) = when (a.status) {
        ApplicantStatus.SMS, ApplicantStatus.TIME -> listOf(Palette.PurpleCard, Palette.PurpleLine, Palette.Purple, Palette.PurpleMeta)
        ApplicantStatus.HANDED_OVER -> listOf(Palette.GreenSoft, Palette.GreenLine, Palette.GreenDark, Palette.Green)
        ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK, ApplicantStatus.STAFF_HANDLING ->
            listOf(Palette.AmberCard, Palette.AmberLine, Palette.Amber, Palette.Amber)
        ApplicantStatus.NEW, ApplicantStatus.FIRST_SENT, ApplicantStatus.CONTACT ->
            listOf(Palette.Surface, Palette.GreenLine, Palette.GreenDark, Palette.Green)
        else -> listOf(Palette.GraySoft, Palette.Line, Palette.Muted2, Palette.Muted)
    }
    AppCard(radius = 14.dp, background = bg, border = line, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (handed) Icon(AppIcons.Ticket, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Text(nowTitle(a, owned), style = AppText.SmallBold, color = ink, modifier = Modifier.weight(1f))
            Text(
                if (handed) a.handedOverAt?.let { formatClock(it) } ?: "" else "지금 상태",
                style = AppText.Caption,
                color = sub,
            )
        }
        if (handed) {
            Text(
                "담당자에게 넘김" + if (a.linearTicket.isNotBlank()) " · 리니어 ${a.linearTicket}" else "",
                style = AppText.Caption.copy(lineHeight = AppText.Message.lineHeight),
                color = ink,
            )
            Text("이후 리니어 구인 티켓에서 2명 선정·학부모 미팅 (사람)", style = AppText.Caption.copy(lineHeight = AppText.Message.lineHeight), color = ink)
        } else {
            Text(
                "다음에 앱이 할 일 · " + (plan?.let { nextStepLine(it) } ?: "불러오는 중…"),
                style = AppText.Caption.copy(lineHeight = AppText.Message.lineHeight),
                color = ink,
            )
            if (plan != null && (plan.sends.isNotEmpty() || plan.notes.isNotEmpty())) {
                TextAction(if (showDetail) "자세히 접기" else "자세히 (보낼 문구·검사)", onClick = { showDetail = !showDetail }, color = sub)
                if (showDetail) {
                    Text("단계 · ${plan.step}", style = AppText.Caption, color = ink)
                    plan.sends.forEach { PlannedSend(it) }
                    plan.notes.forEach { Text("· $it", style = AppText.Caption, color = Palette.Muted) }
                }
            }
        }
    }
}

/** 지금 상태 제목 (앱이 기다리는 것). 앱이 응대하는 상태인데 앱이 안 맡은 지원이면 그렇다고 (이유는 '다음에 앱이 할 일'에) */
private fun nowTitle(a: Applicant, owned: Boolean): String =
    if (a.status in MessageCare.HANDLING && !owned) NOT_OWNED_DOING else statusTitle(a)

private fun statusTitle(a: Applicant): String = when (a.status) {
    ApplicantStatus.NEW -> "첫 메시지 보낼 차례"
    ApplicantStatus.FIRST_SENT -> "번호 답 기다리는 중"
    ApplicantStatus.CONTACT -> "번호 받음 · 첫 문자 보낼 차례"
    ApplicantStatus.SMS -> nowDoing(a)
    ApplicantStatus.TIME -> "가능 시간 확보 · 담당자에게 넘길 차례"
    ApplicantStatus.HANDED_OVER -> "가능 시간 확보 → 넘김"
    ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK, ApplicantStatus.STAFF_HANDLING ->
        "${a.status} — " + a.reason.removePrefix("예외 주제: ").ifBlank { "사람이 볼 차례" }
    else -> "끝난 지원 · " + a.reason.ifBlank { a.status }
}

/** 다음에 앱이 할 일 한 줄: 보낼 것이 있으면 무엇을·언제(·지금 안 보내는 이유), 없으면 계획의 단계 (앞 단계 번호는 뺌) */
private fun nextStepLine(plan: ReplyPlanner.Plan): String {
    if (plan.sends.isEmpty()) return plan.step.replace(Regex("^B[0-9A-Z-]+\\s+"), "")
    return plan.sends.joinToString(" / ") { s ->
        "${whenText(s)} ${s.channel} ${s.what}" + if (s.blocked.isNotEmpty()) " (지금은 안 보냄 — ${s.blocked.first()})" else ""
    }
}

/** 계획에 있는 보낼 것 하나: 어디로·무엇·언제, 문구, 발송 전 검사, 지금 안 보내는 이유 */
@Composable
private fun PlannedSend(s: ReplyPlanner.Send) {
    AppCard(radius = 12.dp, padding = PaddingValues(12.dp), spacing = 4.dp) {
        Text("${s.channel} · ${s.what} · ${whenText(s)}", style = AppText.CaptionStrong, color = Palette.Ink)
        Text(s.text, style = AppText.Message, color = Palette.Ink)
        Text(
            if (s.problems.isEmpty()) "발송 전 검사 통과 (${s.text.length}자 · ${s.text.lines().size}줄)"
            else "발송 전 검사 걸림: ${s.problems.joinToString(", ")}",
            style = AppText.Caption,
            color = if (s.problems.isEmpty()) Palette.Muted else Palette.Red,
        )
        if (s.blocked.isNotEmpty()) {
            Text("지금은 안 보냄: ${s.blocked.joinToString(" · ")}", style = AppText.Caption, color = Palette.Muted)
        }
    }
}

/** 언제 보낼지: '지원 7분 뒤(20:13)' 또는 발송 시간 밖이면 미룬 시각 */
private fun whenText(s: ReplyPlanner.Send): String {
    val minutes = (s.sendAt - s.from) / 60_000
    return if (minutes in 0..60) "${s.fromLabel} ${minutes}분 뒤(${formatClock(s.sendAt)})"
    else "발송 시간(09~21시) 밖이라 ${formatClock(s.sendAt)}"
}

/** 아래 고정 칸: 직접 연락하기 ('연락 중' 표시 → 앱이 그 지원자에게 자동으로 보내지 않음 — 같은 번호 줄에 같이). 표시가 있으면 어디서·언제 */
@Composable
private fun ContactFooter(a: Applicant) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val notice = LocalNotice.current
    Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
        HLine(Palette.Line)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (a.contactingMark.isBlank()) {
                AppButton(
                    "직접 연락하기 · 자동 응답 멈춤",
                    onClick = {
                        scope.launch {
                            val n = ApplicantStore.setContacting(db, a, ApplicantStore.FROM_APP_BUTTON)
                            notice("${a.name}: '연락 중'으로 표시했어요 — 앱은 이 지원자에게 보내지 않아요" + (if (n > 1) " (같은 번호 ${n - 1}건도 같이)" else ""))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                    textStyle = AppText.Section,
                )
                Text(
                    "누르면 '연락 중' 표시가 되고, 앱은 이 지원자에게 보내지 않아요",
                    style = AppText.Caption,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tag("연락 중")
                    Text(
                        "${a.contactingMark} — 앱은 이 지원자에게 보내지 않아요",
                        style = AppText.Caption,
                        color = Palette.Ink2,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 개발 확인용 임시 버튼 (푸는 곳은 화면 설계 '정할 것·확인할 것')
                AppButton(
                    "연락 중 풀기 (개발용)",
                    onClick = { scope.launch { ApplicantStore.setContacting(db, a, null) } },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.DASHED,
                    textStyle = AppText.SmallStrong,
                )
            }
        }
    }
}

/**
 * 개발용: 당근 첫 메시지 드라이런 (사용자 프로세스 B2). 앱이 이 지원자 채팅방까지 가서
 * 보낼 문구·발송 전 검사·실제라면 보낼지만 보여줌. 입력창에 쓰거나 전송하지 않고, 앱 기록도 바꾸지 않음
 */
@Composable
private fun DryRunCard(applicantId: Long) {
    val context = LocalContext.current
    val status by DaangnReply.status.collectAsState()
    var message by remember(applicantId) { mutableStateOf("") }
    var last by remember(applicantId) { mutableStateOf(DaangnReply.last(context, applicantId)) }
    var log by remember(applicantId) { mutableStateOf(DaangnReply.lastLog(context, applicantId)) }
    var showLog by remember(applicantId) { mutableStateOf(false) }
    LaunchedEffect(applicantId, status) {
        last = DaangnReply.last(context, applicantId)
        log = DaangnReply.lastLog(context, applicantId)
    }
    DevCard(
        title = "당근 첫 메시지 드라이런",
        subtitle = "채팅방까지 가서 보낼 문구만 확인 (입력·전송 없음)",
    ) {
        AppButton(
            "채팅방까지 가서 보낼 문구 확인",
            onClick = { message = DaangnReply.startDryRun(context, applicantId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !DaangnReply.running,
        )
        Text(
            "앱이 당근 채팅 탭에서 이 지원자 방을 열고, 보낼 문구와 실제라면 보낼지만 보여줘요. 안 읽은 방은 열지 않아요.",
            style = AppText.Caption,
            color = Palette.Muted,
        )
        if (message.isNotBlank()) Text(message, style = AppText.Caption, color = Palette.Green)
        if (DaangnReply.running && status.isNotBlank()) Text("지금: $status", style = AppText.Caption, color = Palette.Ink)
        last?.let { (time, text) -> Text("마지막: ${formatTime(time)} $text", style = AppText.Caption, color = Palette.Ink) }
        if (log.isNotEmpty()) {
            TextAction(if (showLog) "드라이런 기록 접기" else "드라이런 기록 보기 (${log.size}줄)", onClick = { showLog = !showLog })
            if (showLog) log.forEach { Text(it, style = AppText.Mono, color = Palette.Muted2) }
        }
    }
}
