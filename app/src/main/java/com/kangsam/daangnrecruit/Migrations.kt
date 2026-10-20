package com.kangsam.daangnrecruit

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 표 구조가 바뀔 때 폰 기록을 지우지 않고 새 구조로 옮기는 코드 (DB 설계 '폰 안 사본·표 구조가 바뀔 때').
 *
 * 표를 바꿀 때마다 (Entities.kt를 고친 뒤):
 *  1. AppDatabase의 version을 1 올림
 *  2. 여기에 '옛 버전 → 새 버전'을 하나 적고 ALL에 넣음 — SQL은 Room이 새로 만드는 표와 똑같은 모양이 되게
 *     (새 열이면 Entities의 @ColumnInfo(defaultValue)와 같은 DEFAULT)
 *  3. 시험: 옛 버전 표에 옮기기를 돌려 새 버전 표와 같은지 (build/claude_tmp 시험 틀) → 폰에서 Run (Room이 한 번 더 대조)
 *
 * 옮기다 실패하면 Room이 그 자리에서 되돌려 기록은 그대로 남고, DbOpen이 전체 멈춤을 켬 (지우지 않음)
 */
object Migrations {
    /** 3 → 4 (10/19): 게시물에 '마감 이유' 칸 — 옛 줄은 빈 칸 */
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `postings` ADD COLUMN `closeReason` TEXT NOT NULL DEFAULT ''")
        }
    }

    /** 4 → 5 (10/20): 응대 할 일 표 (reply_tasks — 실제 발송 시험이 다음에 그 사람 방을 볼 차례를 저장). 옛 기록은 그대로 */
    val FROM_4_TO_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reply_tasks` (`applicantId` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, " +
                    "`why` TEXT NOT NULL, `tries` INTEGER NOT NULL, `problem` TEXT NOT NULL, `touchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`applicantId`))"
            )
        }
    }

    /** 앱이 아는 옮기기 전부 (AppDatabase가 Room에 넘김) */
    val ALL: Array<Migration> = arrayOf(FROM_3_TO_4, FROM_4_TO_5)
}
