package com.kangsam.daangnrecruit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 운영폰 앱 알림 (사용자 프로세스 '예외 처리'의 '알림' — 받을 곳(Slack 등)은 담당자 확인사항 17 뒤, 지금은 폰 알림만).
 * 전체 멈춤(멈춤 글자) · DB를 못 옮김 · 나의 당근 닉네임이 앱 계정과 달라 당근 가져오기 멈춤 ·
 * 앱이 대화를 이어 가지 못해 담당자 확인으로 넘김 · 응대 할 일이 있는데 화면이 꺼짐·잠김 / 접근성이 꺼짐 (10/20). 누르면 앱 첫 화면.
 * 안드로이드 13부터는 알림 권한이 있어야 보임 — 첫 화면에서 한 번 물음 (MainActivity). 없으면 기록(로그)만 남김
 */
object AppAlerts {
    private const val TAG = "AppAlerts"
    private const val CHANNEL = "alerts"
    private const val ID_ALL_STOP = 1001
    private const val ID_DB = 1002
    private const val ID_NICKNAME = 1003
    private const val ID_SCREEN = 1004
    private const val ID_ACCESSIBILITY = 1005
    private const val ID_STAFF_CHECK = 2000 // + 지원자 ID (사람마다 한 알림)

    /** 당근에 멈춤 글자가 보여 전체 멈춤 */
    fun allStop(context: Context, word: String, notice: String) = notify(
        context, ID_ALL_STOP, "전체 멈춤 — 당근에 '$word'",
        (notice.ifBlank { "보안 인증·이용 제한 같은 화면이 보였어요." } + "\n앱이 하는 일을 모두 멈췄어요. 당근 화면을 확인한 뒤 설정에서 '다시 시작'을 눌러 주세요.").trim(),
    )

    /** 업데이트 뒤 기록을 새 표 구조로 못 옮겨 전체 멈춤 (DbOpen) */
    fun dbFailed(context: Context) = notify(
        context, ID_DB, "기록을 열지 못해 전체 멈춤",
        "앱을 새로 깐 뒤 기록을 새 구조로 옮기지 못했어요. 기록은 지우지 않았어요 — 앱을 열어 안내를 보세요.",
    )

    /** 나의 당근 닉네임이 앱 계정과 달라 당근 가져오기를 멈춤 (사용자 프로세스 C2 — 정할 때까지 사람이 연 화면도 안 맞춤) */
    fun nicknameMismatch(context: Context, seen: String) = notify(
        context, ID_NICKNAME, "당근 가져오기 멈춤 — 계정이 다름",
        "나의 당근 닉네임이 앱에 등록된 계정과 달라요 (나의 당근: '$seen'). 앱의 확인 탭(또는 설정 '계정')에서 같은 계정인지 다른 계정인지 정해 주세요.",
    )

    /** 앱이 시작한 대화를 이어 가지 못해 담당자 확인으로 넘김 (실제 발송 시험 — 끝은 연락처 확보 또는 담당자 확인, 10/20 재걸) */
    fun staffCheck(context: Context, applicantId: Long, name: String, reason: String) = notify(
        context, ID_STAFF_CHECK + (applicantId % 100_000).toInt(), "확인 필요 — $name",
        "$reason\n앱의 확인 탭에서 볼 수 있어요.",
    )

    /** 응대 할 일이 있는데 화면이 꺼져 있거나 잠겨 있음 (앱은 화면이 켜지고 잠금이 풀려야 당근을 다룸) — 한 번만 울림 */
    fun screenNeeded(context: Context) = notify(
        context, ID_SCREEN, "앱이 당근에서 할 일이 있어요",
        "운영폰 화면을 켜고 잠금을 풀어 주세요. 잠금을 풀면 10초 뒤 앱이 당근을 다뤄요 — 그동안 폰을 내려놓아 주세요.",
        once = true,
    )

    fun clearScreenNeeded(context: Context) = cancel(context, ID_SCREEN)

    /** 응대 할 일이 있는데 접근성(당근 화면 읽기)이 꺼져 있음 — 앱을 새로 깔면 꺼짐 */
    fun accessibilityNeeded(context: Context) = notify(
        context, ID_ACCESSIBILITY, "앱이 할 일이 있는데 접근성이 꺼져 있어요",
        "폰 설정 → 접근성 → 설치된 앱에서 '당근 화면 읽기 (Kangsam)'를 켜 주세요. 켜면 이어서 해요.",
        once = true,
    )

    fun clearAccessibilityNeeded(context: Context) = cancel(context, ID_ACCESSIBILITY)

    private fun cancel(context: Context, id: Int) {
        runCatching { context.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(id) }
    }

    /** 알림 권한이 필요한지 (안드로이드 13부터, 아직 허용 안 함) */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** once = 같은 알림이 떠 있는 동안 다시 보내도 소리·진동 없이 내용만 바꿈 */
    private fun notify(context: Context, id: Int, title: String, text: String, once: Boolean = false) {
        runCatching {
            val app = context.applicationContext
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "앱 알림 (멈춤·기록 문제)", NotificationManager.IMPORTANCE_HIGH))
            if (needsPermission(app)) {
                Log.w(TAG, "알림 권한이 없어 못 보냄: $title")
                return
            }
            val open = PendingIntent.getActivity(
                app, id,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(once)
                .build()
            nm.notify(id, n)
        }.onFailure { Log.e(TAG, "알림 보내기 오류: $title", it) }
    }
}
