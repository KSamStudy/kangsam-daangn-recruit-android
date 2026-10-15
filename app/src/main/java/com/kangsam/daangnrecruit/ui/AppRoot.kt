package com.kangsam.daangnrecruit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.kangsam.daangnrecruit.AppDatabase

/** 아래 탭 5개 (화면 설계 '화면 구성') */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    APPLICANTS("지원자", Icons.Filled.Person),
    POSTS("공고", Icons.Filled.Edit),
    CHECK("확인", Icons.Filled.Warning),
    SETTINGS("설정", Icons.Filled.Settings),
}

/** 앱 전체 틀: 아래 탭을 누르면 가운데 화면이 바뀜 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(refreshTick: Int) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    val context = LocalContext.current
    // 확인 탭 숫자 = 확인 필요 건수 (화면 설계 공통)
    val checkCount by remember { AppDatabase.get(context).checkDao().observeCount() }.collectAsState(initial = 0)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == AppTab.CHECK && checkCount > 0) {
                                BadgedBox(badge = { Badge { Text("$checkCount") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (currentTab) {
            AppTab.HOME -> HomeScreen(modifier)
            AppTab.APPLICANTS -> ApplicantsScreen(onGoCheck = { currentTab = AppTab.CHECK }, modifier = modifier)
            AppTab.POSTS -> PostsScreen(modifier)
            AppTab.CHECK -> CheckScreen(modifier)
            AppTab.SETTINGS -> SettingsScreen(refreshTick, modifier)
        }
    }
}