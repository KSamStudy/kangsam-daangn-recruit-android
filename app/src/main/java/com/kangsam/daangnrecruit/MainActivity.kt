package com.kangsam.daangnrecruit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.kangsam.daangnrecruit.ui.AppRoot
import com.kangsam.daangnrecruit.ui.theme.KangsamDaangnRecruitTheme

class MainActivity : ComponentActivity() {

    // 설정 화면 등에서 돌아올 때마다 숫자를 올려서, 화면이 권한 상태와 기록을 다시 읽게 함
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 화면은 밝은 바탕 하나라서(화면 시안), 폰 상태 표시줄·아래 막대 글자를 늘 어둡게
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            KangsamDaangnRecruitTheme {
                AppRoot(refreshTick = refreshTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }
}
