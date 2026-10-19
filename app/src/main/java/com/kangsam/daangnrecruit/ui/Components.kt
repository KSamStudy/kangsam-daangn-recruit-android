package com.kangsam.daangnrecruit.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangsam.daangnrecruit.ApplicantStatus
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette

// 화면 공통 부품 — 화면 시안 v0.7의 모양을 한 곳에 둠 (카드·알약·스위치·버튼·윗줄·목록 줄·입력칸·개발용 칸).
// 모양을 바꿀 때는 여기만 고치면 모든 화면이 같이 바뀜. 색은 theme/Color.kt, 글자는 theme/Type.kt, 아이콘은 theme/AppIcons.kt

/** 버튼을 누른 결과 한 줄을 화면 아래 띠로 보여 줌 (AppRoot가 채움) */
val LocalNotice = staticCompositionLocalOf<(String) -> Unit> { {} }

// ---------- 카드

/** 흰 카드 (테두리 1, 둥근 모서리). onClick이 있으면 카드 전체를 누를 수 있음 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    border: Color = Palette.Line,
    background: Color = Palette.Surface,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    spacing: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** 개발 중에만 있는 칸 (화면 설계 '개발 중에만 있는 것' — 시안에는 없음): 점선 카드 + '개발용' 표시 */
@Composable
fun DevCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.DevBg)
            .dashedBorder(Palette.Placeholder, 14.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("개발용", Tone.GRAY, small = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = AppText.SmallStrong, color = Palette.Ink)
                if (subtitle.isNotBlank()) Text(subtitle, style = AppText.Caption, color = Palette.Muted)
            }
            trailing?.invoke(this)
        }
        content()
    }
}

/** 점선 테두리 (개발용 칸·'직접 올린 공고 등록' 버튼·아직 안 받은 정보) */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx() - stroke / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** 줄 사이 선 */
@Composable
fun HLine(color: Color = Palette.LineSoft, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

// ---------- 알약 (상태·켜짐·회차 표시)

/** 알약 색 묶음 — 화면 설계 '공통'의 색 뜻대로 */
enum class Tone(val bg: Color, val fg: Color, val line: Color? = null) {
    GREEN(Palette.GreenSoft, Palette.GreenDark),                          // 켜짐·정상
    GREEN_LINE(Palette.Surface, Palette.GreenDark, Palette.GreenLine),     // 앱이 당근 단계에서 진행 중 (첫 메시지 보냄)
    GREEN_SOLID(Palette.Green, Palette.Surface),                           // 넘김
    PURPLE(Palette.PurpleSoft, Palette.Purple),                            // 문자 단계
    AMBER(Palette.AmberSoft, Palette.Amber),                               // 사람이 볼 것 (보류·담당자 확인)
    AMBER_LINE(Palette.Surface, Palette.Amber, Palette.AmberLineStrong),   // 사람이 맡은 것 (담당자 응대 중)
    GRAY(Palette.GraySoft, Palette.Muted2),                                // 끝남·꺼짐
    GRAY_LINE(Palette.Surface, Palette.Ink2, Palette.LineStrong),
    RED(Palette.RedSoft, Palette.Red),                                     // 전체 멈춤
    DARK(Palette.Ink, Palette.Surface),
}

@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier, small: Boolean = false, leading: ImageVector? = null) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .clip(shape)
            .background(tone.bg)
            .then(if (tone.line != null) Modifier.border(1.dp, tone.line, shape) else Modifier)
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 1.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leading != null) Icon(leading, contentDescription = null, tint = tone.fg, modifier = Modifier.size(14.dp))
        Text(text, style = if (small) AppText.Tiny else AppText.CaptionStrong, color = tone.fg, maxLines = 1)
    }
}

/** 지원자 상태 색 (DB 설계 '상태 흐름 (지원자)'의 값을 그대로 보여 줌) */
fun statusTone(status: String): Tone = when (status) {
    ApplicantStatus.NEW, ApplicantStatus.CONTACT -> Tone.GREEN
    ApplicantStatus.FIRST_SENT -> Tone.GREEN_LINE
    ApplicantStatus.SMS, ApplicantStatus.TIME -> Tone.PURPLE
    ApplicantStatus.HANDED_OVER -> Tone.GREEN_SOLID
    ApplicantStatus.HOLD, ApplicantStatus.STAFF_CHECK -> Tone.AMBER
    ApplicantStatus.STAFF_HANDLING -> Tone.AMBER_LINE
    else -> Tone.GRAY // 미응답·종료·합침
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) = Pill(status, statusTone(status), modifier)

/** 작은 네모 표시 ('연락 중' 등) */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = Palette.Ink2, line: Color = Palette.LineButton) {
    Text(
        text,
        style = AppText.Tiny,
        color = color,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, line, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

// ---------- 스위치

/** 켜기·끄기 스위치 (시안: 44×26 막대, 누르는 칸 52×44) */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) Palette.Green else Palette.LineButton, label = "track")
    val thumbX by animateDpAsState(if (checked) 21.dp else 3.dp, label = "thumb")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label + if (checked) " 켜짐" else " 꺼짐" }
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape).background(track)) {
            Box(
                Modifier
                    .offset(x = thumbX, y = 3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Palette.Surface)
            )
        }
    }
}

/** 기능 하나 켜기·끄기 줄: 이름 · 한 줄 설명 · 스위치 */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AppText.BodyStrong, color = Palette.Ink)
            Text(description, style = AppText.Caption, color = Palette.Muted)
        }
        Toggle(checked, onChange, label = title, enabled = enabled)
    }
}

// ---------- 버튼

enum class ButtonKind { PRIMARY, DARK, OUTLINE, DANGER, DASHED }

/** 시안의 버튼: 초록(주요)·검정(진행)·흰색(보조)·빨강(전체 멈춤)·점선(추가). 높이 44, 아래 고정 칸은 48 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.OUTLINE,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    icon: ImageVector? = null,
    textStyle: TextStyle = AppText.BodyStrong,
) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> Palette.Green to Palette.Surface
        ButtonKind.DARK -> Palette.Ink to Palette.Surface
        ButtonKind.OUTLINE -> Palette.Surface to Palette.Ink
        ButtonKind.DANGER -> Palette.Red to Palette.Surface
        ButtonKind.DASHED -> Color.Transparent to Palette.Ink
    }
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bg)
            .then(
                when (kind) {
                    ButtonKind.OUTLINE -> Modifier.border(1.dp, Palette.LineButton, shape)
                    ButtonKind.DASHED -> Modifier.dashedBorder(Palette.LineStrong, 12.dp)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = textStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** 작은 글자 버튼 (개발용 칸 안의 '기록 보기' 등) */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Green, enabled: Boolean = true) {
    Text(
        text,
        style = AppText.SmallStrong,
        color = color,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    )
}

/** 필터 알약 (고르면 검정) */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) Palette.Ink else Palette.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Palette.LineInput, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (selected) AppText.SmallStrong else AppText.SmallMedium,
            color = if (selected) Palette.Surface else Palette.Ink2,
            maxLines = 1,
        )
    }
}

/** 나눔 버튼 (공고 편집 '올리는 때' 등): 고른 칸만 초록 */
@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.dp, Palette.LineInput, shape),
    ) {
        options.forEachIndexed { i, option ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.LineInput))
            val on = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) Palette.Green else Palette.Surface)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = if (on) AppText.SmallStrong else AppText.SmallMedium,
                    color = if (on) Palette.Surface else Palette.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- 윗줄

/** 탭 화면 제목 (예: 지원자 / 오늘 7명 지원 · 확인 필요 3건) */
@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = if (trailing != null) 16.dp else 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AppText.ScreenTitle, color = Palette.Ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = AppText.Small, color = Palette.Muted)
        }
        trailing?.invoke(this)
    }
}

/** 탭 안에서 들어간 화면의 윗줄: ‹ 뒤로 · 제목 · 한 줄 설명 · 오른쪽 표시 (흰 바탕이 폰 상태 표시줄까지) */
@Composable
fun DetailTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "뒤로",
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(Palette.Surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Back, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = AppText.DetailTitle, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = AppText.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke(this)
        }
        HLine(Palette.Line)
    }
}

/** 설정의 묶음 이름 (작은 회색 글자) */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppText.SmallStrong, color = Palette.Muted, modifier = modifier.padding(horizontal = 4.dp))
}

/** 값 하나를 보여 주는 줄: 이름 · 오른쪽 값 · › (설정) */
@Composable
fun SettingRow(label: String, value: AnnotatedString, onClick: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = AppText.Body, color = Palette.Ink, maxLines = 1)
            Text(
                value,
                style = AppText.Small,
                color = Palette.Muted,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = Palette.LineStrong, modifier = Modifier.size(18.dp))
        }
        if (divider) HLine()
    }
}

/** 카드 안 정보 한 줄: 왼쪽 이름(64) · 오른쪽 내용 (지원자 상세) */
@Composable
fun InfoRow(label: String, modifier: Modifier = Modifier, alignTop: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 26.dp),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = AppText.Small,
            color = Palette.Muted,
            modifier = Modifier.width(64.dp).padding(top = if (alignTop) 4.dp else 0.dp),
        )
        content()
    }
}

/** 안내 상자 (빨강 = 지금 안 되는 것, 노랑 = 사람이 볼 것, 초록 = 됨) */
@Composable
fun NoteBox(text: String, tone: Tone, modifier: Modifier = Modifier, action: @Composable (ColumnScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, line) = when (tone) {
        Tone.RED -> Palette.RedSoft to Palette.RedLine
        Tone.AMBER, Tone.AMBER_LINE -> Palette.AmberCard to Palette.AmberLine
        Tone.GREEN, Tone.GREEN_LINE, Tone.GREEN_SOLID -> Palette.GreenSoft to Palette.GreenLine
        else -> Palette.Bg to Palette.Line
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = AppText.Caption.copy(lineHeight = 18.sp), color = tone.fg)
        action?.invoke(this)
    }
}

/** 빈 목록 안내 */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        Text(text, style = AppText.Small, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ---------- 입력칸

/** 시안의 입력칸: 테두리 1 · 둥근 10 · 높이 40 (여러 줄이면 늘어남). 비어 있으면 회색 안내 글자 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = AppText.Body,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle.copy(color = Palette.Ink),
        cursorBrush = SolidColor(Palette.Green),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(Palette.Surface)
                    .border(1.dp, if (focused) Palette.Green else Palette.LineInput, shape)
                    .padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 8.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = textStyle, color = Palette.Placeholder, maxLines = if (singleLine) 1 else minLines)
                }
                inner()
            }
        },
    )
}

/** 이름이 위에 붙은 입력칸 (공고 편집) */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = AppText.Body,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = AppText.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AppTextField(value, onValueChange, placeholder = placeholder, singleLine = singleLine, minLines = minLines, keyboardType = keyboardType, textStyle = textStyle)
        if (hint.isNotBlank()) Text(hint, style = AppText.Caption, color = Palette.Muted)
    }
}

/** 오른쪽에 굵은 값이 있는 줄 — 값을 누르면 그 자리에서 고침 (공고 편집 '게시 규칙') */
@Composable
fun InlineEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = AppText.Small, color = Palette.Muted, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = AppText.BodyStrong.copy(color = Palette.Ink, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Palette.Green),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (focused) Palette.Bg else Color.Transparent)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = AppText.BodyStrong, color = Palette.Ink, textAlign = TextAlign.End, maxLines = 1)
                        }
                        inner()
                    }
                    if (suffix.isNotEmpty() && value.isNotEmpty()) {
                        Text(" $suffix", style = AppText.BodyStrong, color = Palette.Ink)
                    }
                }
            },
        )
    }
}

/** 오른쪽에 값만 보여 주는 줄 (고칠 수 없는 값 — 예: 업체 표기 [정할 것]) */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Palette.Ink) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = AppText.Small, color = Palette.Muted, maxLines = 1)
        Text(
            value,
            style = AppText.BodyStrong,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).widthIn(min = 40.dp),
        )
    }
}
