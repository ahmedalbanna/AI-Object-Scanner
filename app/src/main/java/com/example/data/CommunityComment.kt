package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "community_comments",
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
data class CommunityComment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reportId: Int,
    val authorName: String,
    val commentText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CommunityCommentDao {
    @Insert
    suspend fun insertComment(comment: CommunityComment)

    @Query("SELECT * FROM community_comments WHERE reportId = :reportId ORDER BY timestamp ASC")
    fun getCommentsForReport(reportId: Int): Flow<List<CommunityComment>>

    @Query("SELECT COUNT(*) FROM community_comments WHERE reportId = :reportId")
    fun getCommentCount(reportId: Int): Flow<Int>
}
