package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.Applicant
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ApplicantView
import com.kangsam.daangnrecruit.FeatureSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 2. 지원자 — 목록과 응대 켜기·끄기 (화면 설계 '화면별 기능')
// 10/18: 위 공고 칸으로 공고별로 나눠 보고, 끝난 지원(종료·합침·미응답)은 안 보임 — 보일 지원자를 고르는 규칙은 ApplicantView

/** 목록 필터 (고른 공고 안에서) */
private enum class ApplicantFilter(val label: String) {
    TO_HANDLE("처리할 것"), CHECK("확인 필요"), HANDED_OVER("넘김")
}

private fun matches(a: Applicant, f: ApplicantFilter): Boolean = when (f) {
    ApplicantFilter.TO_HANDLE -> ApplicantView.toHandle(a)
    ApplicantFilter.CHECK -> a.status in ApplicantView.CHECK
    ApplicantFilter.HANDED_OVER -> a.status == ApplicantStatus.HANDED_OVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantsScreen(onGoCheck: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val applicants by remember { db.applicantDao().observeAll() }.collectAsState(initial = emptyList())
    val posts by remember { db.postDao().observeStandingPosts() }.collectAsState(initial = emptyList())
    val postings by remember { db.postDao().observePostings() }.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(ApplicantFilter.TO_HANDLE) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var daangnOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.DAANGN_REPLY)) }
    var smsOn by remember { mutableStateOf(FeatureSettings.isOn(context, FeatureSettings.SMS_REPLY)) }

    val opened = applicants.firstOrNull { it.id == openedId }
    if (opened != null) {
        // 폰 뒤로가기 → 목록
        BackHandler { openedId = null }
        ApplicantDetailScreen(applicantId = opened.id, onBack = { openedId = null }, modifier = modifier)
    } else {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("지원자", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // 위 스위치 2개 (기능 끄는 곳)
            FeatureSwitch("당근 응대", "첫 메시지·번호 받기", daangnOn) {
                daangnOn = it
                FeatureSettings.set(context, FeatureSettings.DAANGN_REPLY, it)
            }
            FeatureSwitch("문자 응대", "인적사항·가능 시간 받기·넘기기", smsOn) {
                smsOn = it
                FeatureSettings.set(context, FeatureSettings.SMS_REPLY, it)
            }
            Text(
                "응대 기능은 아직 만들기 전이라 켜고 끈 값만 저장돼요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // 공고 칸: 공고마다 보이는 지원자 수 (끝난 지원 빼고 — 당근 지원자 목록의 수와 같음). 처음엔 지금 올라가 있는 공고
            val groups = ApplicantView.groups(applicants, posts, postings)
            val group = groups.firstOrNull { it.key == groupKey } ?: groups.firstOrNull()
            val inGroup = group?.applicants.orEmpty()
            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { g ->
                        FilterChip(
                            selected = g.key == group?.key,
                            onClick = { groupKey = g.key },
                            label = { Text("${g.label} ${g.applicants.size}명") }
                        )
                    }
                }
            }

            // 필터 (고른 공고 안에서): 처리할 것 · 확인 필요(보류·담당자 확인) · 넘김
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplicantFilter.entries.forEach { f ->
                    val count = inGroup.count { matches(it, f) }
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} $count") }
                    )
                }
            }
            Text(
                "끝난 지원(종료·합침·미응답)은 안 보여요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            val shown = inGroup.filter { matches(it, filter) }
            if (shown.isEmpty()) {
                Text(
                    if (groups.isEmpty()) "아직 없음 — 지원 알림이 오거나 설정에서 '당근에서 가져오기'를 하면 보여요"
                    else "이 공고에 해당하는 지원자가 없어요",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(shown, key = { it.id }) { a ->
                        ApplicantListRow(a) {
                            // 보류·담당자 확인은 확인 필요 탭으로, 나머지는 지원자 상세로
                            if (a.status in ApplicantView.CHECK) {
                                onGoCheck()
                            } else {
                                openedId = a.id
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 기능 하나 켜기·끄기 줄 */
@Composable
internal fun FeatureSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 목록 한 줄: 이름·동네, 상태, 지원한 공고와 지금 하는 일, 마지막 대화 시각 */
@Composable
private fun ApplicantListRow(a: Applicant, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, style = MaterialTheme.typography.titleMedium)
            if (a.area.isNotBlank()) {
                Text(" · ${a.area}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val tag = when {
                a.isSample -> "예시"
                isTestRow(a) -> "테스트"
                else -> ""
            }
            if (tag.isNotBlank()) {
                Text("  $tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            StatusChip(a.status)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${a.postTitle.ifBlank { "공고명 없음" }} · ${nowDoing(a)}" + (if (a.contactingMark.isNotBlank()) " · 연락 중" else ""),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            if (a.lastMessageAt != null) "마지막 대화 ${formatTime(a.lastMessageAt)}" else "지원 ${formatTime(a.appliedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 상태 색 (화면 설계 공통): 초록 정상 진행, 보라 문자 단계, 노랑 사람이 볼 것, 회색 끝 */
@Composable
fun StatusChip(status: String) {
    val (fg, bg) = when (status) {
        ApplicantStatus.SMS, ApplicantStatus.TIME -> Color(0xFF6A1B9A) to Color(0xFFF3E5F5)
        ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK, ApplicantStatus.STAFF_HANDLING -> Color(0xFF7A5C00) to Color(0xFFFFF1BF)
        ApplicantStatus.HANDED_OVER, ApplicantStatus.NO_REPLY, ApplicantStatus.ENDED, ApplicantStatus.MERGED -> Color(0xFF4F4F4F) to Color(0xFFE4E4E4)
        else -> Color(0xFF1B5E20) to Color(0xFFDCEFDD)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            status,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** 상태로 본 '지금 하는 일' 한 줄 */
private fun nowDoing(a: Applicant): String = when (a.status) {
    ApplicantStatus.NEW -> "첫 메시지 보낼 차례"
    ApplicantStatus.FIRST_SENT -> "당근에서 번호 기다림"
    ApplicantStatus.CONTACT -> "첫 문자 보낼 차례"
    ApplicantStatus.SMS -> "문자로 인적사항·가능 시간 받는 중"
    ApplicantStatus.TIME -> "담당자에게 넘길 차례"
    ApplicantStatus.HANDED_OVER -> if (a.linearTicket.isNotBlank()) "넘김 · ${a.linearTicket}" else "담당자에게 넘김"
    ApplicantStatus.STAFF_HANDLING -> "담당자가 응대 중"
    else -> a.reason.ifBlank { "끝" }
}

private fun isTestRow(a: Applicant): Boolean =
    a.sourceKey.startsWith("com.android.shell|") || a.rawText.startsWith("테스트")

private val TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

internal fun formatTime(time: Long): String = TIME_FORMAT.format(Date(time))
