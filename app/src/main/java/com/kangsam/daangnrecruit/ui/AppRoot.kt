package com.kangsam.daangnrecruit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kangsam.daangnrecruit.ui.theme.AppIcons
import com.kangsam.daangnrecruit.ui.theme.AppText
import com.kangsam.daangnrecruit.ui.theme.Palette
import kotlinx.coroutines.launch

/** 아래 탭 5개 (화면 설계 '화면 구성') */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("홈", AppIcons.Home),
    APPLICANTS("지원자", AppIcons.Users),
    POSTS("공고", AppIcons.Megaphone),
    CHECK("확인", AppIcons.Alert),
    SETTINGS("설정", AppIcons.Settings),
}

/**
 * 앱 전체 틀: 아래 탭 5개 + 탭 안에서 들어가는 화면 (화면 설계 '화면 구성').
 * - 탭 안 화면: 지원자 → 지원자 상세, 공고 → 공고 편집, 설정 → 알림 기록 (개발용). 열려 있으면 아래 탭을 숨기고 (시안처럼),
 *   폰 뒤로가기·‹ 를 누르면 그 탭으로 돌아감
 * - 버튼 결과 한 줄은 화면 아래 띠로 (LocalNotice)
 * 탭 이동 라이브러리 없이 고른 탭·열린 화면 값으로 바꿈 (화면을 돌려도 유지)
 */
@Composable
fun AppRoot(refreshTick: Int) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var applicantId by rememberSaveable { mutableStateOf<Long?>(null) }   // 지원자 상세
    var editingPostId by rememberSaveable { mutableStateOf<Long?>(null) } // 공고 편집 (0 = 새 공고)
    var showNotificationLog by rememberSaveable { mutableStateOf(false) } // 알림 기록 (개발용)

    // 확인 탭 숫자 = 확인 필요 건수 (화면 설계 공통 — 홈 '오늘'의 확인 필요와 같은 수)
    val checkCount = rememberCheckCount()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice: (String) -> Unit = remember {
        { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }
    val inDetail = showNotificationLog || editingPostId != null || applicantId != null
    // 탭마다 화면 상태(스크롤·고른 공고 칸·필터)를 기억 — 다른 탭이나 상세에 다녀와도 그대로
    val tabStates = rememberSaveableStateHolder()

    CompositionLocalProvider(LocalNotice provides notice) {
        Scaffold(
            containerColor = Palette.Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // 화면마다 윗줄이 폰 상태 표시줄을 비켜 감
            snackbarHost = {
                SnackbarHost(snackbar, modifier = if (inDetail) Modifier.navigationBarsPadding() else Modifier) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Palette.Ink,
                        contentColor = Palette.Surface,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            bottomBar = {
                if (!inDetail) BottomNav(current = currentTab, checkCount = checkCount, onSelect = { currentTab = it })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    showNotificationLog -> {
                        BackHandler { showNotificationLog = false }
                        NotificationLogScreen(refreshTick = refreshTick, onBack = { showNotificationLog = false })
                    }
                    editingPostId != null -> {
                        BackHandler { editingPostId = null }
                        val id = editingPostId ?: 0L
                        PostEditScreen(
                            postId = if (id > 0L) id else null,
                            onDone = { message ->
                                editingPostId = null
                                if (message.isNotBlank()) notice(message)
                            },
                        )
                    }
                    applicantId != null -> {
                        BackHandler { applicantId = null }
                        ApplicantDetailScreen(applicantId = applicantId ?: 0L, onBack = { applicantId = null })
                    }
                    else -> tabStates.SaveableStateProvider(currentTab.name) {
                        when (currentTab) {
                            AppTab.HOME -> HomeScreen(
                                refreshTick = refreshTick,
                                onOpenTab = { currentTab = it },
                                onOpenApplicant = { applicantId = it },
                            )
                            AppTab.APPLICANTS -> ApplicantsScreen(
                                onOpenApplicant = { applicantId = it },
                                onGoCheck = { currentTab = AppTab.CHECK },
                            )
                            AppTab.POSTS -> PostsScreen(onEditPost = { editingPostId = it })
                            AppTab.CHECK -> CheckScreen(onOpenApplicant = { applicantId = it }, onOpenSettings = { currentTab = AppTab.SETTINGS })
                            AppTab.SETTINGS -> SettingsScreen(
                                refreshTick = refreshTick,
                                onOpenNotificationLog = { showNotificationLog = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 아래 탭 (시안: 높이 72, 흰 바탕, 위 선, 고른 탭은 초록, 확인 탭에 빨간 숫자) */
@Composable
private fun BottomNav(current: AppTab, checkCount: Int, onSelect: (AppTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
        HLine(Palette.Line)
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            AppTab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == current,
                    badge = if (tab == AppTab.CHECK) checkCount else 0,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun NavItem(tab: AppTab, selected: Boolean, badge: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (selected) Palette.Green else Palette.Muted
    Column(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (badge > 0) "${tab.label} — 확인 필요 ${badge}건" else tab.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-5).dp)
                        .heightIn(min = 18.dp)
                        .widthIn(min = 18.dp)
                        .clip(CircleShape)
                        .background(Palette.Red)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (badge > 99) "99+" else "$badge", style = AppText.Tiny, color = Palette.Surface)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = if (selected) AppText.CaptionStrong else AppText.CaptionMedium, color = color)
    }
}
