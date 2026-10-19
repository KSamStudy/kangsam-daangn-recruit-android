package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangsam.daangnrecruit.DbBackup
import com.kangsam.daangnrecruit.DbOpen
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import java.time.ZoneId

/**
 * DB를 못 열었을 때 탭 대신 보이는 안내 한 장 (사용자 프로세스 '예외 처리' — 앱 업데이트 뒤 기록을 새 표 구조로 못 옮김).
 * 기록은 지우지 않았고 전체 멈춤이 켜져 있음. 폰 안 사본과 오류 한 줄을 보여 주고 '다시 열어 보기'만 둠
 */
@Composable
fun DbErrorScreen(failed: DbOpen.State.Failed, backups: List<DbBackup.Copy>, onRetry: () -> Unit) {
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("기록을 열지 못했어요", "앱이 하는 일은 모두 멈췄어요 (전체 멈춤)")
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteBox(
                "앱을 새로 깐 뒤 폰 안 기록(지원자·대화·공고)을 새 표 구조로 옮기지 못했어요. 기록은 지우지 않았어요. " +
                    "지원 감지·당근 가져오기·응대 모두 멈춰 있어요.",
                Tone.RED,
            )
            AppCard(radius = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("전체 멈춤 중", Tone.RED, leading = AppIcons.Stop)
                }
                ValueRow("멈춘 때", formatTime(failed.at))
                ValueRow(
                    "폰 안 사본",
                    backups.firstOrNull()?.let { "${backups.size}개 · 마지막 ${formatTime(it.time(zone))} (${it.kind.label})" } ?: "없음",
                )
                Text("오류: ${failed.error}", style = AppText.Caption.copy(lineHeight = 17.sp), color = Palette.Muted)
            }
            Text(
                "개발 담당에게 이 화면을 보여 주세요. 옮기는 코드를 고친 앱을 깔면 기록이 그대로 옮겨져요. " +
                    "'다시 열어 보기'로 한 번 더 해 볼 수 있고, 열리면 설정에서 전체 멈춤을 풀어 주세요.",
                style = AppText.Caption.copy(lineHeight = 17.sp),
                color = Palette.Muted,
            )
            AppButton("다시 열어 보기", onClick = onRetry, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.DARK, height = 48.dp)
        }
    }
}
