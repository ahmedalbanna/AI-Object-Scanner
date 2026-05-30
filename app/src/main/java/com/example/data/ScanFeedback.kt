package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "scan_feedback",
    foreignKeys = [
        ForeignKey(
            entity = ScanReport::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["reportId"])]
)
data class ScanFeedback(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reportId: Int,
    val isPositive: Boolean,
    val correction: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ScanFeedbackDao {
    @Insert
    suspend fun insertFeedback(feedback: ScanFeedback)

    @Query("SELECT * FROM scan_feedback WHERE reportId = :reportId")
    fun getFeedbackForReport(reportId: Int): Flow<ScanFeedback?>
}
