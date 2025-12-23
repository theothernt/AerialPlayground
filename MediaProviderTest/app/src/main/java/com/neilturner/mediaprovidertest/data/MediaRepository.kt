package com.neilturner.mediaprovidertest.data

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.neilturner.mediaprovidertest.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class MediaRepository(private val context: Context) {

    companion object {
        private const val TAG = "MediaRepository"
        private const val CONTENT_PROVIDER_AUTHORITY = "com.neilturner.aerialviews.media"
    }

    data class MediaSample(
        val url: String,
        val path: String?,
        val mimeType: String?,
        val filename: String?
    )

    sealed class QueryResult {
        data class Success(
            val count: Int,
            val columns: List<String>,
            val uri: String,
            val imageSample: MediaSample?,
            val videoSample: MediaSample?
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

                    var imageSample: MediaSample? = null
                    var videoSample: MediaSample? = null

                    if (count > 0) {
                        // Create a list of indices and shuffle them to pick random items
                        val indices = (0 until count).toList().shuffled()
                        
                        val urlColumnIndex = c.getColumnIndex("url")
                        val dataColumnIndex = c.getColumnIndex("_data")
                        val mimeTypeColumnIndex = c.getColumnIndex("mime_type")

                        for (i in indices) {
                            if (imageSample != null && videoSample != null) break // Found both

                            if (c.moveToPosition(i)) {
                                var sampleUrl: String? = null
                                var samplePath: String? = null
                                var sampleMimeType: String? = null

                                if (dataColumnIndex != -1) samplePath = c.getString(dataColumnIndex)
                                if (mimeTypeColumnIndex != -1) sampleMimeType = c.getString(mimeTypeColumnIndex)

                                // STRATEGY 1: Check 'url' column
                                if (urlColumnIndex != -1) {
                                    sampleUrl = c.getString(urlColumnIndex)
                                }

                                // STRATEGY 2: If 'url' didn't yield a content URI, scan ALL columns
                                // This handles cases where 'url' might be a file path, but another column holds the content URI
                                if (sampleUrl == null || !sampleUrl.startsWith("content://")) {
                                    for (col in 0 until c.columnCount) {
                                        try {
                                            val value = c.getString(col)
                                            if (value != null && value.startsWith("content://")) {
                                                Log.d(TAG, "Found content URI in column '$col': $value")
                                                sampleUrl = value
                                                break
                                            }
                                        } catch (e: Exception) { /* ignore */ }
                                    }
                                }

                                if (sampleUrl != null) {
                                    // Try to determine type
                                    val determinedType = MediaType.fromUrl(sampleUrl, sampleMimeType)
                                    
                                    // If we haven't found a sample of this type yet, try to resolve details and save it
                                    if ((determinedType == MediaType.IMAGE && imageSample == null) ||
                                        (determinedType == MediaType.VIDEO && videoSample == null)) {
                                        
                                        var resolvedFilename: String? = null
                                        if (sampleUrl.startsWith("content://")) {
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

                                        val sample = MediaSample(sampleUrl, samplePath, sampleMimeType, resolvedFilename)
                                        
                                        if (determinedType == MediaType.IMAGE) {
                                            imageSample = sample
                                            Log.d(TAG, "Found Image Sample: $sampleUrl")
                                        } else if (determinedType == MediaType.VIDEO) {
                                            videoSample = sample
                                            Log.d(TAG, "Found Video Sample: $sampleUrl")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "queryMediaCount: SUCCESS! Count: $count. Found Image: ${imageSample != null}, Video: ${videoSample != null}")
                    QueryResult.Success(count, columnNames, uriStr, imageSample, videoSample)
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
