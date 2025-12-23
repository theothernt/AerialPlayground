package com.neilturner.mediaprovidertest.data

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MediaRepository(private val context: Context) {

    companion object {
        private const val TAG = "MediaRepository"
        private const val CONTENT_PROVIDER_AUTHORITY = "com.neilturner.aerialviews.media"
    }

    sealed class QueryResult {
        data class Success(
            val count: Int,
            val columns: List<String>,
            val uri: String,
            val sampleUrl: String?,
            val samplePath: String?,
            val mimeType: String?,
            val resolvedFilename: String?
        ) : QueryResult()
        data class Error(val message: String) : QueryResult()
    }

    suspend fun queryMediaCount(): QueryResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "queryMediaCount: Starting query...")

        // Check if provider package is installed (diagnostic only)
        try {
            val pm = context.packageManager
            val providerInfo = pm.resolveContentProvider(CONTENT_PROVIDER_AUTHORITY, 0)
            if (providerInfo != null) {
                Log.d(TAG, "Provider found: ${providerInfo.packageName}, exported: ${providerInfo.exported}")
            } else {
                Log.w(TAG, "Provider NOT found - app may not be installed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check provider info: ${e.message}")
        }

        try {
            val uriStr = "content://$CONTENT_PROVIDER_AUTHORITY/local"
            val uri = uriStr.toUri()
            Log.d(TAG, "queryMediaCount: Querying URI: $uri")

            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            if (cursor != null) {
                cursor.use { c ->
                    val count = c.count
                    val columnNames = c.columnNames?.toList() ?: emptyList()

                    var sampleUrl: String? = null
                    var samplePath: String? = null
                    var sampleMimeType: String? = null
                    
                    if (count > 0) {
                        val randomPos = Random.nextInt(count)
                        if (c.moveToPosition(randomPos)) {
                            // Try to find a URL column
                            val urlColumnIndex = c.getColumnIndex("url")
                            if (urlColumnIndex != -1) {
                                sampleUrl = c.getString(urlColumnIndex)
                            }

                            // Try to find _data column
                            val dataColumnIndex = c.getColumnIndex("_data")
                            if (dataColumnIndex != -1) {
                                samplePath = c.getString(dataColumnIndex)
                            }

                            // Try to find mime_type column
                            val mimeTypeColumnIndex = c.getColumnIndex("mime_type")
                            if (mimeTypeColumnIndex != -1) {
                                sampleMimeType = c.getString(mimeTypeColumnIndex)
                            }

                            // Fallback
                            if (sampleUrl == null) {
                                for (i in 0 until c.columnCount) {
                                    try {
                                        val value = c.getString(i)
                                        if (value != null && (value.startsWith("http") || value.startsWith("content"))) {
                                            sampleUrl = value
                                            break
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }

                    var resolvedFilename: String? = null
                    if (sampleUrl != null && sampleUrl.startsWith("content://")) {
                        try {
                            val contentUri = sampleUrl.toUri()
                            // Try to get type from ContentResolver if not found in cursor
                            if (sampleMimeType == null) {
                                sampleMimeType = context.contentResolver.getType(contentUri)
                            }

                            context.contentResolver.query(contentUri, null, null, null, null)?.use { metaCursor ->
                                if (metaCursor.moveToFirst()) {
                                    val nameIndex = metaCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1) {
                                        resolvedFilename = metaCursor.getString(nameIndex)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to resolve filename or type for $sampleUrl", e)
                        }
                    }

                    Log.d(TAG, "queryMediaCount: SUCCESS! Count: $count, MimeType: $sampleMimeType")
                    QueryResult.Success(count, columnNames, uriStr, sampleUrl, samplePath, sampleMimeType, resolvedFilename)
                }
            } else {
                Log.e(TAG, "queryMediaCount: Cursor is null")
                QueryResult.Error("Content provider returned null cursor. Check if provider is exported and handles /local path.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "queryMediaCount: SecurityException", e)
            QueryResult.Error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "queryMediaCount: Exception", e)
            QueryResult.Error("Error: ${e.message}")
        }
    }
}
