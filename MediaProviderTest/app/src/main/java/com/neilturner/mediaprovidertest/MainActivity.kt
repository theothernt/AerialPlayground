package com.neilturner.mediaprovidertest

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.mediaprovidertest.ui.theme.MediaProviderTestTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import android.provider.OpenableColumns
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaProviderTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    MainScreen()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaProviderTest"
        private const val CONTENT_PROVIDER_AUTHORITY = "com.neilturner.aerialviews.media"

        fun queryMediaCount(context: Context, onResult: (Result) -> Unit) {
            Log.d(TAG, "queryMediaCount: Starting query...")

            // Check if provider package is installed
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

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uri = "content://$CONTENT_PROVIDER_AUTHORITY/local".toUri()
                    Log.d(TAG, "queryMediaCount: Querying URI: $uri")

                    val cursor = context.contentResolver.query(
                        uri,
                        null,
                        null,
                        null,
                        null
                    )

                    if (cursor != null) {
                        val count = cursor.count
                        val columnNames = cursor.columnNames?.toList() ?: emptyList()

                        var sampleUrl: String? = null
                        var samplePath: String? = null
                        if (count > 0) {
                            val randomPos = Random.nextInt(count)
                            if (cursor.moveToPosition(randomPos)) {
                                // Try to find a URL column
                                val urlColumnIndex = cursor.getColumnIndex("url")
                                if (urlColumnIndex != -1) {
                                    sampleUrl = cursor.getString(urlColumnIndex)
                                }

                                // Try to find _data column (often contains the file path)
                                val dataColumnIndex = cursor.getColumnIndex("_data")
                                if (dataColumnIndex != -1) {
                                    samplePath = cursor.getString(dataColumnIndex)
                                }

                                // Fallback: look for any column with http/https if "url" column didn't yield result
                                if (sampleUrl == null) {
                                    for (i in 0 until cursor.columnCount) {
                                        try {
                                            val value = cursor.getString(i)
                                            if (value != null && (value.startsWith("http") || value.startsWith("content"))) {
                                                sampleUrl = value
                                                break
                                            }
                                        } catch (e: Exception) {
                                            // Ignore errors reading columns
                                        }
                                    }
                                }
                            }
                        }

                        var resolvedFilename: String? = null
                        if (sampleUrl != null && sampleUrl.startsWith("content://")) {
                            try {
                                val contentUri = sampleUrl.toUri()
                                val metadataCursor = context.contentResolver.query(contentUri, null, null, null, null)
                                if (metadataCursor != null) {
                                    if (metadataCursor.moveToFirst()) {
                                        val nameIndex = metadataCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (nameIndex != -1) {
                                            resolvedFilename = metadataCursor.getString(nameIndex)
                                        }
                                    }
                                    metadataCursor.close()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to resolve filename for $sampleUrl", e)
                            }
                        }

                        Log.d(TAG, "queryMediaCount: SUCCESS!")
                        Log.d(TAG, "queryMediaCount: Media count: $count")
                        Log.d(TAG, "queryMediaCount: Column names: ${columnNames.joinToString(", ")}")
                        Log.d(TAG, "queryMediaCount: Sample URL: $sampleUrl")
                        Log.d(TAG, "queryMediaCount: Sample Path: $samplePath")
                        Log.d(TAG, "queryMediaCount: Resolved Filename: $resolvedFilename")

                        cursor.close()

                        withContext(Dispatchers.Main) {
                            onResult(Result.Success(count, columnNames, uri.toString(), sampleUrl, samplePath, resolvedFilename))
                        }
                    } else {
                        Log.e(TAG, "queryMediaCount: Cursor is null - content provider returned null")
                        withContext(Dispatchers.Main) {
                            onResult(Result.Error("Content provider returned null cursor. Check if provider is exported and handles /local path."))
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "queryMediaCount: SecurityException - permission denied", e)
                    withContext(Dispatchers.Main) {
                        onResult(Result.Error("Permission denied: ${e.message}"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "queryMediaCount: Exception occurred", e)
                    withContext(Dispatchers.Main) {
                        onResult(Result.Error("Error: ${e.message}"))
                    }
                }
            }
        }
    }

    sealed class Result {
        data class Success(val count: Int, val columns: List<String>, val uri: String, val sampleUrl: String?, val samplePath: String?, val resolvedFilename: String?) : Result()
        data class Error(val message: String) : Result()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<MainActivity.Result?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                Log.d("MediaProviderTest", "Button clicked - initiating query")
                isLoading = true
                MainActivity.queryMediaCount(context) { result ->
                    when (result) {
                        is MainActivity.Result.Success -> {
                            Log.i("MediaProviderTest", "Query result received: ${result.count} items")
                        }
                        is MainActivity.Result.Error -> {
                            Log.e("MediaProviderTest", "Query error received: ${result.message}")
                        }
                    }
                    queryResult = result
                    isLoading = false
                    showDialog = true
                }
            },
            colors = ButtonDefaults.colors(
                containerColor = Color.LightGray,
                contentColor = Color.Black
            ),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Loading..." else "Query Media")
        }
    }

    if (showDialog && queryResult != null) {
        CustomAlertDialog(
            result = queryResult!!,
            onDismiss = {
                showDialog = false
                queryResult = null
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomAlertDialog(result: MainActivity.Result, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Close")
            }
        },
        title = {
            Text(text = if (result is MainActivity.Result.Success) "Query Result" else "Error")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (result) {
                    is MainActivity.Result.Success -> {
                        Text("Media count: ${result.count}")
                        if (result.sampleUrl != null) {
                            Text("Sample URL: ${result.sampleUrl}")
                        } else {
                            Text("No URL found in random row")
                        }
                        if (result.samplePath != null) {
                            Text("Sample Path: ${result.samplePath}")
                        }
                        if (result.resolvedFilename != null) {
                            Text("Filename: ${result.resolvedFilename}")
                        }
                    }
                    is MainActivity.Result.Error -> {
                        Text("Error occurred:")
                        Text(result.message)
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MediaProviderTestTheme {
        MainScreen()
    }
}