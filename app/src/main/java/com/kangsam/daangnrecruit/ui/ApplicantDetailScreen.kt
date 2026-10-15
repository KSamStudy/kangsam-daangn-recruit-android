package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.Message
import kotlinx.coroutines.launch

// 3. 지원자 상세 — 한 사람의 정보와 대화 (화면 설계 '화면별 기능')

/** 받은 정보 5개 (문자 문구에서 받는 항목) */
private val INFO_LABELS = listOf("성함", "대학·학과", "교육 경험", "가능 요일·시간대", "가능 기간")

private fun infoValues(a: Applicant) =
    listOf(a.realName, a.school, a.experience, a.availableTime, a.availablePeriod)

/** 대화 묶음: 이어진 당근 메시지는 접어서 개수만, 문자는 하나씩 */
private sealed interface Block {
    val key: String

    data class Daangn(val messages: List<Message>) : Block {
        override val key = "d${messages.first().id}"
    }

    data class Single(val message: Message) : Block {
        override val key = "m${message.id}"
    }
}

private fun toBlocks(messages: List<Message>): List<Block> {
    val blocks = mutableListOf<Block>()
    var run = mutableListOf<Message>()
    for (m in messages) {
        if (m.channel == "당근") {
            run.add(m)
        } else {
            if (run.isNotEmpty()) {
                blocks.add(Block.Daangn(run))
                run = mutableListOf()
            }
            blocks.add(Block.Single(m))
        }
    }
    if (run.isNotEmpty()) blocks.add(Block.Daangn(run))
    return blocks
}

@Composable
fun ApplicantDetailScreen(applicantId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val applicant by remember(applicantId) { db.applicantDao().observe(applicantId) }.collectAsState(initial = null)
    val messages by remember(applicantId) { db.messageDao().observeFor(applicantId) }.collectAsState(initial = emptyList())
    var expanded by remember(applicantId) { mutableStateOf(setOf<String>()) }

    val a = applicant
    if (a == null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← 지원자") }
            Text("불러오는 중…", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val samePerson by remember(a.phoneKey, a.id) { db.applicantDao().observeSamePerson(a.phoneKey, a.id) }
        .collectAsState(initial = emptyList())
    val blocks = toBlocks(messages)

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        // 위: 이름, 동네·지원한 공고, 상태
        item(key = "top") {
            Column {
                TextButton(onClick = onBack) { Text("← 지원자") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    StatusChip(a.status)
                    if (a.isSample) {
                        Text("  예시", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    listOf(a.area, a.postTitle.ifBlank { "공고명 없음" }).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "지원 ${formatTime(a.appliedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // 정보: 연락처, 받은 정보 5개 중 받은 것, 같은 사람, 넘김이면 구인 티켓
        item(key = "info") {
            Column {
                SectionTitle("정보")
                InfoRow("연락처", a.phone.ifBlank { "아직 없음" }, note = "DB에만 저장")
                val values = infoValues(a)
                InfoRow("받은 정보", "${values.count { it.isNotBlank() }} / ${values.size}")
                INFO_LABELS.zip(values).forEach { (label, value) -> InfoRow(label, value.ifBlank { "—" }) }
                if (a.linearTicket.isNotBlank()) {
                    InfoRow("구인 티켓", a.linearTicket)
                }
                val same = samePerson.map { "${it.name} (${it.status})" } +
                    listOfNotNull(a.sameCandidate.ifBlank { null }?.let { "후보: $it" })
                if (same.isNotEmpty()) {
                    InfoRow("같은 사람", same.joinToString(", "))
                }
                if (a.chatRoom.isNotBlank()) {
                    InfoRow("채팅방", a.chatRoom)
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // 지금 상태 + 직접 연락하기
        item(key = "now") {
            Column {
                SectionTitle("지금 상태")
                Text(nowState(a), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (a.contactingMark.isBlank()) {
                    Button(onClick = {
                        scope.launch {
                            db.applicantDao().setContactingMark(a.id, "직접 연락하기 · ${formatTime(System.currentTimeMillis())}")
                        }
                    }) {
                        Text("직접 연락하기")
                    }
                    Text(
                        "누르면 '연락 중'으로 표시되고, 이 지원자에겐 앱이 자동으로 보내지 않아요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "연락 중 (${a.contactingMark}) — 이 지원자에겐 앱이 자동으로 보내지 않아요",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { scope.launch { db.applicantDao().setContactingMark(a.id, "") } }) {
                        Text("연락 중 풀기 (임시 — 푸는 곳은 화면 설계 '정할 것')")
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("대화 ${messages.size}")
                if (messages.isEmpty()) {
                    Text("아직 대화 없음", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 대화: 오른쪽 우리, 왼쪽 지원자. 이어진 당근 메시지는 접어서 개수만
        items(blocks, key = { it.key }) { block ->
            when (block) {
                is Block.Daangn -> {
                    val open = block.key in expanded
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { expanded = if (open) expanded - block.key else expanded + block.key }) {
                            Text(if (open) "당근 메시지 ${block.messages.size}개 접기" else "당근 메시지 ${block.messages.size}개 펼치기")
                        }
                        if (open) {
                            block.messages.forEach { Bubble(it) }
                        }
                    }
                }
                is Block.Single -> Bubble(block.message)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoRow(label: String, value: String, note: String = "") {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (note.isNotBlank()) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 말풍선: 우리 메시지는 오른쪽(보낸 방법), 지원자 메시지는 왼쪽(앱이 답하지 않았을 때만 앱 처리) */
@Composable
private fun Bubble(m: Message) {
    val ours = m.sender == "우리"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (ours) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (ours) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (ours) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    m.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Text(bubbleMeta(m, ours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun bubbleMeta(m: Message, ours: Boolean): String {
    val parts = mutableListOf(m.channel, formatTime(m.time))
    if (ours && m.sendMethod.isNotBlank()) parts.add(m.sendMethod)
    if (!ours && m.appHandling != "답함") parts.add("앱 처리: ${m.appHandling.ifBlank { "처리 전" }}")
    return parts.joinToString(" · ")
}

/** 지금 상태: 앱이 기다리는 것 또는 넘김 결과 */
private fun nowState(a: Applicant): String {
    val missing = INFO_LABELS.zip(infoValues(a)).filter { it.second.isBlank() }.map { it.first }
    return when (a.status) {
        ApplicantStatus.NEW -> "첫 메시지 보낼 차례 (당근 응대가 켜져 있으면 앱이 보냄)"
        ApplicantStatus.FIRST_SENT -> "당근에서 번호 답을 기다림"
        ApplicantStatus.CONTACT -> "첫 문자 보낼 차례 (문자 응대가 켜져 있으면 앱이 보냄)"
        ApplicantStatus.SMS ->
            if (missing.isEmpty()) "받을 정보를 다 받음 — 가능 시간 확보로" else "기다리는 것: ${missing.joinToString(", ")} 답"
        ApplicantStatus.TIME -> "담당자에게 넘길 차례"
        ApplicantStatus.HANDED_OVER -> "넘김 결과: " +
            listOf(a.linearTicket, a.handedOverAt?.let { formatTime(it) } ?: "").filter { it.isNotBlank() }
                .joinToString(" · ").ifBlank { "넘김" }
        ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK, ApplicantStatus.STAFF_HANDLING ->
            a.reason.ifBlank { a.status } +
                (if (a.statusBeforeHold.isNotBlank()) " — 풀리면 '${a.statusBeforeHold}'(으)로 돌아감" else "")
        else -> a.reason.ifBlank { "끝남" }
    }
}
