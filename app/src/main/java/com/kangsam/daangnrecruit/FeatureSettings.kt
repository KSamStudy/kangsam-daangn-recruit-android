package com.kangsam.daangnrecruit

import android.content.Context

/**
 * 기능 켜기·끄기 값 (사용자 프로세스 '기능 켜기·끄기'. DB 설계: 표가 아니라 앱 설정에 둠).
 * 처음엔 모두 꺼짐. 아직 응대·게시 기능을 만들기 전이라 값만 저장함.
 */
object FeatureSettings {
    private const val PREFS = "features"

    const val DAANGN_REPLY = "daangn_reply"  // 당근 응대 (B2~B3)
    const val SMS_REPLY = "sms_reply"        // 문자 응대 (B4-1~B6)
    const val AUTO_POST = "auto_post"        // 자동 게시 (A1~A8)
    const val ALL_STOP = "all_stop"          // 전체 멈춤

    fun isOn(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false)

    fun set(context: Context, key: String, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, on)
            .apply()
    }
}
