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
    val description: String
)
