package com.endyaris.pythonidev02

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

    
class PythonAnywhereApi(
    private val apiKey: String,
    private val username: String
) {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    suspend fun executePythonCode(code: String): String {
        return withContext(Dispatchers.IO) {
            val json = """
                {
                    "code": ${code.toJsonString()},
                    "timeout": 10
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(getConsoleUrl())
                .post(json.toRequestBody(mediaType))
                .addHeader("Authorization", "Token $apiKey")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext "Error: ${response.code} - ${response.message}"
                }
                response.body?.string() ?: "No response"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun String.toJsonString(): String {
        return "\"${this.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")}\""
    }
    
    private fun getConsoleUrl() = 
        "https://www.pythonanywhere.com/api/v0/user/$username/consoles/"
    
}