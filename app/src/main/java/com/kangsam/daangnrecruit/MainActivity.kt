package com.kangsam.daangnrecruit

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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