package com.kangsam.daangnrecruit

import android.app.Application
import com.kangsam.daangnrecruit.reply.LiveSettings
import com.kangsam.daangnrecruit.reply.ReplyScope

/**
 * 앱(프로세스)이 켜질 때 한 번 — 화면·알림 리스너·접근성 서비스보다 먼저 (AndroidManifest의 application android:name).
 * 순수 코드가 폰 앱 설정을 읽는 법을 넣어 줌: '앱이 맡은 지원'(ReplyScope)은 지금 실제 발송 시험 설정으로 (LiveSettings)
 */
class KangsamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val app = this
        ReplyScope.settings = { LiveSettings.config(app) }
        DbOpen.onFailed = { ctx -> AppAlerts.dbFailed(ctx) } // DB를 못 옮기면 폰 알림 (6단계)
    }
}
