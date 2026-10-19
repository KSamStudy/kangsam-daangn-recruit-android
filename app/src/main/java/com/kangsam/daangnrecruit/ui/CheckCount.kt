package com.kangsam.daangnrecruit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kangsam.daangnrecruit.AppDatabase
import com.kangsam.daangnrecruit.sync.DaangnSync

/**
 * 확인 필요 건수 (화면 설계 공통 — 확인 탭 숫자 = 홈 '오늘'의 확인 필요 = 지원자 탭 위 줄).
 * DB의 보류·담당자 확인 지원자 + 검사 걸림·결과 불명 게시물 (CheckDao) + 닉네임이 달라 멈춘 당근 가져오기 1건 (앱 설정)
 */
@Composable
fun rememberCheckCount(): Int {
    val context = LocalContext.current
    val fromDb by remember { AppDatabase.get(context).checkDao().observeCount() }.collectAsState(initial = 0)
    val mismatch by remember { DaangnSync.mismatchFlow(context) }.collectAsState()
    return fromDb + if (mismatch != null) 1 else 0
}
