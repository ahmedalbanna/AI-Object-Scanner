package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanReportDao {
    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<ScanReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ScanReport): Long

    @Update
    suspend fun updateReport(report: ScanReport)

    @Query("SELECT DISTINCT collectionName FROM scan_reports WHERE collectionName IS NOT NULL")
    fun getAllCollections(): Flow<List<String>>

    @Delete
    suspend fun deleteReport(report: ScanReport)

    @Query("DELETE FROM scan_reports")
    suspend fun deleteAllReports()
}
