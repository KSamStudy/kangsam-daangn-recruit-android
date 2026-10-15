package com.kangsam.daangnrecruit

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 지원자 (DB 설계 '5. 지원자' — 지원 건 1개 = 한 줄).
 * 1차(지원 감지·기록)에 쓰는 열만 먼저 둠. 연락처·인적사항 같은 나머지 열은 그 단계에서 추가.
 */
@Entity(
    tableName = "applicants",
    indices = [Index(value = ["sourceKey"], unique = true)],
)
data class Applicant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 지원자ID
    val name: String,       // 당근 이름 (알림에 온 그대로)
    val postTitle: String,  // 알림 공고명 (알림에 없으면 빈 칸)
    val status: String,     // 상태 (DB 설계 '상태 흐름' 값: 새 지원 …)
    val appliedAt: Long,    // 지원 시각 (알림 시각)
    val sourceKey: String,  // 같은 알림을 두 번 저장하지 않으려고 쓰는 값
    val rawText: String,    // 알림 원문 (감지 규칙 확인용)
)

/** 오류 기록 (티켓 1차 완료 기준 4번) */
@Entity(tableName = "error_logs")
data class ErrorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,         // 시각
    val place: String,      // 어디서 (예: 지원 감지)
    val message: String,    // 무슨 오류
)

@Dao
interface ApplicantDao {
    /** 같은 sourceKey가 이미 있으면 저장하지 않고 -1을 돌려줌 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(applicant: Applicant): Long

    /** 지원자 목록 (최근 지원이 위로). DB가 바뀌면 화면이 자동으로 다시 그려짐 */
    @Query("SELECT * FROM applicants ORDER BY appliedAt DESC")
    fun observeAll(): Flow<List<Applicant>>
}

@Dao
interface ErrorLogDao {
    @Insert
    suspend fun insert(error: ErrorLog)

    @Query("SELECT * FROM error_logs ORDER BY time DESC LIMIT 50")
    fun observeRecent(): Flow<List<ErrorLog>>
}

/** 폰 안 DB (파일 이름 recruit.db, 이 앱 전용 저장소에만 있음) */
@Database(entities = [Applicant::class, ErrorLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun applicantDao(): ApplicantDao
    abstract fun errorLogDao(): ErrorLogDao

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