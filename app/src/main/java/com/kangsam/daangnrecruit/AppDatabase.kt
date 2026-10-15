package com.kangsam.daangnrecruit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 폰 안 DB (파일 이름 recruit.db, 이 앱 전용 저장소에만 있음).
 * 표는 DB 설계 v0.6 그대로: 1단계 게시물 DB(계정·계정 상태 기록·상시 공고·게시물) + 2단계 지원 DB(지원자·대화), 그리고 오류 기록.
 * 표 정의는 Entities.kt, 읽고 쓰는 곳은 Daos.kt.
 */
@Database(
    entities = [
        Account::class, AccountEvent::class, StandingPost::class, Posting::class,
        Applicant::class, Message::class, ErrorLog::class,
    ],
    version = 3, // 10/15 3: 대화 표에 메시지 키 (개발 중이라 표가 바뀌면 지우고 새로 — 가져오기로 다시 채움)
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun postDao(): PostDao
    abstract fun applicantDao(): ApplicantDao
    abstract fun messageDao(): MessageDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun checkDao(): CheckDao
    abstract fun sampleDao(): SampleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recruit.db",
                )
                    // 개발 중에는 표 구조가 바뀌면 DB를 지우고 새로 만듦 (실제 운영 전에 바꿀 것)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
