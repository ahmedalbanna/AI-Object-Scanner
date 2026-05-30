package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_reports")
data class ScanReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String,
    val timestamp: Long,
    val imageUrl: String?, // Local file URI, Picked URI, or Remote sample URL
    val primaryMaterial: String,
    val dimensions: String,
    val color: String,
    val estimatedValue: String,
    val weight: String,
    val description: String,
    // Custom user modifications
    val userTags: String = "",
    val userNotes: String = "",
    val userRating: Int = 0, // 0-5 user rating for correctness flag
    val collectionName: String? = null, // Collection grouping
    val dynamicDetail: String = "", // Context-specific (e.g. Care instructions)
    val knowledgeBit: String = "", // Interesting fact
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPublic: Boolean = false,
    val authorName: String = "Explorer"
)

