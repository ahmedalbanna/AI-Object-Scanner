package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GeminiRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.ScanReport
import com.example.data.ScanFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Processing : ScanUiState
    data class Success(val report: ScanReport) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.scanReportDao()

    val allReports: StateFlow<List<ScanReport>> = dao.getAllReports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCollections: StateFlow<List<String>> = dao.getAllCollections()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _customApiKey = MutableStateFlow<String>("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    init {
        // Load custom API key if previously saved in SharedPreferences
        val prefs = application.getSharedPreferences("scanner_prefs", Application.MODE_PRIVATE)
        _customApiKey.value = prefs.getString("gemini_key", "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        val prefs = getApplication<Application>().getSharedPreferences("scanner_prefs", Application.MODE_PRIVATE)
        prefs.edit().putString("gemini_key", key).apply()
    }

    fun updateReport(report: ScanReport) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateReport(report)
        }
    }

    private val feedbackDao = AppDatabase.getDatabase(application).scanFeedbackDao()

    fun saveFeedback(reportId: Int, isPositive: Boolean, correction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            feedbackDao.insertFeedback(ScanFeedback(reportId = reportId, isPositive = isPositive, correction = correction))
        }
    }

    fun getFeedbackForReport(reportId: Int) = feedbackDao.getFeedbackForReport(reportId)

    private suspend fun fetchWikipediaDetails(title: String): String? {
        return try {
            val response = com.example.api.WikipediaClient.service.getSummary(title)
            response.query?.pages?.values?.firstOrNull()?.extract
        } catch (e: Exception) {
            null
        }
    }

    fun formatReportForSharing(report: ScanReport): String {
        return """
            --- AI Object Scan Report ---
            Object: ${report.title}
            Category: ${report.category}
            
            [Analysis Results]
            Material: ${report.primaryMaterial}
            Dimensions: ${report.dimensions}
            Color: ${report.color}
            Est. Value: ${report.estimatedValue}
            Weight: ${report.weight}
            
            [Description]
            ${report.description}
            
            ${if (report.userNotes.isNotBlank()) "[User Notes]\n${report.userNotes}" else ""}
            ${if (report.userTags.isNotBlank()) "[Tags] ${report.userTags}" else ""}
            ---
        """.trimIndent()
    }

    private fun getEffectiveApiKey(): String {
        val custom = _customApiKey.value.trim()
        if (custom.isNotEmpty()) {
            return custom
        }
        val def = BuildConfig.GEMINI_API_KEY
        if (def != "MY_GEMINI_API_KEY" && def.isNotEmpty()) {
            return def
        }
        return ""
    }

    fun resetState() {
        _scanState.value = ScanUiState.Idle
    }

    fun deleteReport(report: ScanReport) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteReport(report)
            // Delete associated file if it's stored locally
            if (report.imageUrl != null && !report.imageUrl.startsWith("http")) {
                try {
                    val file = File(report.imageUrl)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "Error deleting local image file", e)
                }
            }
        }
    }

    fun scanLocalImage(uri: Uri) {
        viewModelScope.launch {
            _scanState.value = ScanUiState.Processing
            try {
                val context = getApplication<Application>()
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }

                if (bitmap == null) {
                    _scanState.value = ScanUiState.Error("Failed to decode picked image.")
                    return@launch
                }

                // Save bitmap to our safe cache directory
                val savedPath = withContext(Dispatchers.IO) {
                    saveBitmapToCache(bitmap)
                }

                analyzeBitmap(bitmap, savedPath)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error("Failed to process picked image: ${e.localizedMessage}")
            }
        }
    }

    fun scanCapturedImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanState.value = ScanUiState.Processing
            try {
                // Save bitmap to local file cache
                val savedPath = withContext(Dispatchers.IO) {
                    saveBitmapToCache(bitmap)
                }
                analyzeBitmap(bitmap, savedPath)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error("Failed to save captured photo: ${e.localizedMessage}")
            }
        }
    }

    fun scanRemoteDemoImage(urlStr: String, titlePlaceholder: String) {
        viewModelScope.launch {
            _scanState.value = ScanUiState.Processing
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    downloadBitmap(urlStr)
                }

                if (bitmap == null) {
                    _scanState.value = ScanUiState.Error("Failed to load demo image.")
                    return@launch
                }

                // Save bitmap to local file cache so we can display it offline
                val savedPath = withContext(Dispatchers.IO) {
                    saveBitmapToCache(bitmap)
                }

                analyzeBitmap(bitmap, savedPath)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error("Failed to process demo sample: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun analyzeBitmap(bitmap: Bitmap, imageUriStr: String) = withContext(Dispatchers.Default) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty()) {
            _scanState.value = ScanUiState.Error(
                "Gemini API key is not configured.\n\n" +
                        "Please go to the Settings tab (top right icon) to input your own API Key, or configure it via the AI Studio Secrets panel."
            )
            return@withContext
        }

        val resizedBitmap = resizeBitmapForApi(bitmap, 800)
        val imageBase64 = resizedBitmap.toBase64()

        val prompt = "You are an expert AI Object Scanner.\n" +
                "Analyze the provided image of an object.\n" +
                "Identify the main object, extract its key properties, and generate a structural analysis.\n" +
                "You MUST output a valid raw JSON object. Do NOT wrap it in any ```json code blocks or extra text. Return ONLY the raw JSON object matching the following structure:\n" +
                "{\n" +
                "  \"title\": \"A concise common/specific name of the object\",\n" +
                "  \"category\": \"A single word category (e.g., Tech, Kitchen, Botany, Tool, Apparel, Household, Office)\",\n" +
                "  \"primaryMaterial\": \"Core composition material(s) of the object (e.g., Brushed Steel, Ceramic, Ceramic Glaze, Polycarbonate)\",\n" +
                "  \"dimensions\": \"Estimated dimensions (e.g. '15 x 8 x 8 cm' or 'Estimated: 40mm diameter')\",\n" +
                "  \"color\": \"Primary and accent colors\",\n" +
                "  \"estimatedValue\": \"Fair estimated price range or utility value (e.g. '$15 - $25 USD')\",\n" +
                "  \"weight\": \"Rough estimation of weight (e.g., '350g' or '1.2kg')\",\n" +
                "  \"description\": \"A professional, insightful 3-4 sentence evaluation highlighting historical context, key uses, maintenance guidelines, or design characteristics.\"\n" +
                "}"

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = imageBase64))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val rawResponse = response.candidates?.getOrNull(0)?.content?.parts?.getOrNull(0)?.text

            if (rawResponse.isNullOrBlank()) {
                _scanState.value = ScanUiState.Error("Gemini generated an empty response. Let's try again!")
                return@withContext
            }

            // Parse response json
            val cleanJson = rawResponse
                .trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            val jsonObject = JSONObject(cleanJson)
            val title = jsonObject.optString("title", "Scanned Object")
            val category = jsonObject.optString("category", "Unspecified")
            val primaryMaterial = jsonObject.optString("primaryMaterial", "Unspecified")
            val dimensions = jsonObject.optString("dimensions", "Estimated dimensions unavailable")
            val color = jsonObject.optString("color", "Unspecified")
            val estimatedValue = jsonObject.optString("estimatedValue", "Unspecified")
            val weight = jsonObject.optString("weight", "Unspecified")
            val description = jsonObject.optString("description", "No narrative detailed analysis returned.")

            val report = ScanReport(
                title = title,
                category = category,
                timestamp = System.currentTimeMillis(),
                imageUrl = imageUriStr,
                primaryMaterial = primaryMaterial,
                dimensions = dimensions,
                color = color,
                estimatedValue = estimatedValue,
                weight = weight,
                description = description
            )

            // Persist report into Room database
            val id = dao.insertReport(report)
            val persistedReport = report.copy(id = id.toInt())

            _scanState.value = ScanUiState.Success(persistedReport)
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error analyzing image", e)
            _scanState.value = ScanUiState.Error("Analysis fails: ${e.localizedMessage}")
        }
    }

    private fun resizeBitmapForApi(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source

        val ratio = width.toFloat() / height.toFloat()
        val (targetWidth, targetHeight) = if (width > height) {
            Pair(maxDimension, (maxDimension / ratio).toInt())
        } else {
            Pair((maxDimension * ratio).toInt(), maxDimension)
        }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String {
        val context = getApplication<Application>()
        val filename = "scanned_${UUID.randomUUID()}.jpg"
        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Failed to download image", e)
            null
        }
    }
}
