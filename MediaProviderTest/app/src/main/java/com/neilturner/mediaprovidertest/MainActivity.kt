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
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import androidx.compose.material3.CircularProgressIndicator
import coil3.imageLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

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

    sealed class ImageLoadStatus {
        data object Idle : ImageLoadStatus()
        data object Loading : ImageLoadStatus()
        data class Success(val url: String) : ImageLoadStatus()
        data class Error(val message: String) : ImageLoadStatus()
    }

    sealed class VideoLoadStatus {
        data object Idle : VideoLoadStatus()
        data object Loading : VideoLoadStatus()
        data class Success(val url: String) : VideoLoadStatus()
        data class Error(val message: String) : VideoLoadStatus()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<MainActivity.Result?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var imageLoadStatus by remember { mutableStateOf<MainActivity.ImageLoadStatus>(MainActivity.ImageLoadStatus.Idle) }
    var videoLoadStatus by remember { mutableStateOf<MainActivity.VideoLoadStatus>(MainActivity.VideoLoadStatus.Idle) }

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
                imageLoadStatus = MainActivity.ImageLoadStatus.Idle
                videoLoadStatus = MainActivity.VideoLoadStatus.Idle
                MainActivity.queryMediaCount(context) { result ->
                    when (result) {
                        is MainActivity.Result.Success -> {
                            Log.i("MediaProviderTest", "Query result received: ${result.count} items")
                            // Try to load media if we have a valid URL
                            if (result.sampleUrl != null) {
                                val mediaType = getMediaType(result.sampleUrl)
                                when (mediaType) {
                                    MediaType.IMAGE -> {
                                        imageLoadStatus = MainActivity.ImageLoadStatus.Loading
                                        loadImageWithCoil(context, result.sampleUrl) { loadStatus ->
                                            imageLoadStatus = loadStatus
                                        }
                                    }
                                    MediaType.VIDEO -> {
                                        videoLoadStatus = MainActivity.VideoLoadStatus.Loading
                                        loadVideoWithExoPlayer(context, result.sampleUrl) { loadStatus ->
                                            videoLoadStatus = loadStatus
                                        }
                                    }
                                    MediaType.UNKNOWN -> {
                                        Log.w("MediaProviderTest", "Unknown media type for: ${result.sampleUrl}")
                                    }
                                }
                            }
                        }
                        is MainActivity.Result.Error -> {
                            Log.e("MediaProviderTest", "Query error received: ${result.message}")
                            imageLoadStatus = MainActivity.ImageLoadStatus.Idle
                            videoLoadStatus = MainActivity.VideoLoadStatus.Idle
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
            imageLoadStatus = imageLoadStatus,
            videoLoadStatus = videoLoadStatus,
            onDismiss = {
                showDialog = false
                queryResult = null
                imageLoadStatus = MainActivity.ImageLoadStatus.Idle
                videoLoadStatus = MainActivity.VideoLoadStatus.Idle
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomAlertDialog(
    result: MainActivity.Result,
    imageLoadStatus: MainActivity.ImageLoadStatus = MainActivity.ImageLoadStatus.Idle,
    videoLoadStatus: MainActivity.VideoLoadStatus = MainActivity.VideoLoadStatus.Idle,
    onDismiss: () -> Unit
) {
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
            Text(text = if (result is MainActivity.Result.Success) "Query Result" else "Error", color = Color.White)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (result) {
                    is MainActivity.Result.Success -> {
                        Text("Media count: ${result.count}", color = Color.White)
                        if (result.sampleUrl != null) {
                            Text("Sample URL: ${result.sampleUrl}", color = Color.White)
                        } else {
                            Text("No URL found in random row", color = Color.White)
                        }
                        if (result.samplePath != null) {
                            Text("Sample Path: ${result.samplePath}", color = Color.White)
                        }
                        if (result.resolvedFilename != null) {
                            Text("Filename: ${result.resolvedFilename}", color = Color.White)
                        }

                        // Display image loading status
                        if (imageLoadStatus != MainActivity.ImageLoadStatus.Idle) {
                            Text("", color = Color.White) // spacer
                            Text("Image Status:", color = Color.Cyan)
                            when (imageLoadStatus) {
                                is MainActivity.ImageLoadStatus.Loading -> {
                                    Text("Loading...", color = Color.Yellow)
                                    CircularProgressIndicator(color = Color.White)
                                }
                                is MainActivity.ImageLoadStatus.Success -> {
                                    Text("✓ Successfully loaded", color = Color.Green)
                                }
                                is MainActivity.ImageLoadStatus.Error -> {
                                    Text("✗ Failed to load", color = Color.Red)
                                    Text(imageLoadStatus.message, color = Color.Red)
                                }
                                is MainActivity.ImageLoadStatus.Idle -> {}
                            }
                        }

                        // Display video loading status
                        if (videoLoadStatus != MainActivity.VideoLoadStatus.Idle) {
                            Text("", color = Color.White) // spacer
                            Text("Video Status:", color = Color.Cyan)
                            when (videoLoadStatus) {
                                is MainActivity.VideoLoadStatus.Loading -> {
                                    Text("Loading...", color = Color.Yellow)
                                    CircularProgressIndicator(color = Color.White)
                                }
                                is MainActivity.VideoLoadStatus.Success -> {
                                    Text("✓ Successfully loaded", color = Color.Green)
                                }
                                is MainActivity.VideoLoadStatus.Error -> {
                                    Text("✗ Failed to load", color = Color.Red)
                                    Text(videoLoadStatus.message, color = Color.Red)
                                }
                                is MainActivity.VideoLoadStatus.Idle -> {}
                            }
                        }
                    }
                    is MainActivity.Result.Error -> {
                        Text("Error occurred:", color = Color.White)
                        Text(result.message, color = Color.White)
                    }
                }
            }
        },
        containerColor = Color.DarkGray
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MediaProviderTestTheme {
        MainScreen()
    }
}

/**
 * Load an image using Coil without displaying it visually.
 * This validates that the URL can be loaded successfully.
 */
fun loadImageWithCoil(
    context: Context,
    imageUrl: String,
    onStatusUpdate: (MainActivity.ImageLoadStatus) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("MediaProviderTest", "Starting Coil image load for URL: $imageUrl")

            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            val result = context.imageLoader.execute(request)

            Log.d("MediaProviderTest", "Coil result: ${result.image}")
            withContext(Dispatchers.Main) {
                onStatusUpdate(MainActivity.ImageLoadStatus.Success(imageUrl))
            }
        } catch (e: Exception) {
            Log.e("MediaProviderTest", "Error loading image with Coil", e)
            withContext(Dispatchers.Main) {
                onStatusUpdate(MainActivity.ImageLoadStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }
}

/**
 * Load a video using Media3 ExoPlayer without displaying it visually.
 * This validates that the video URL can be loaded successfully.
 */
@UnstableApi
fun loadVideoWithExoPlayer(
    context: Context,
    videoUrl: String,
    onStatusUpdate: (MainActivity.VideoLoadStatus) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        var exoPlayer: ExoPlayer? = null
        try {
            Log.d("MediaProviderTest", "Starting ExoPlayer video load for URL: $videoUrl")

            exoPlayer = ExoPlayer.Builder(context).build()
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // Set a listener to detect when the video is ready or fails to load
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        Log.d("MediaProviderTest", "Video is ready to play")
                        onStatusUpdate(MainActivity.VideoLoadStatus.Success(videoUrl))
                        exoPlayer.release()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("MediaProviderTest", "ExoPlayer error: ${error.message}")
                    onStatusUpdate(MainActivity.VideoLoadStatus.Error(error.message ?: "Unknown error"))
                    exoPlayer.release()
                }
            }

            exoPlayer.addListener(listener)
        } catch (e: Exception) {
            Log.e("MediaProviderTest", "Error loading video with ExoPlayer", e)
            onStatusUpdate(MainActivity.VideoLoadStatus.Error(e.message ?: "Unknown error"))
            exoPlayer?.release()
        }
    }
}

/**
 * Determine the media type based on file extension
 */
enum class MediaType {
    IMAGE, VIDEO, UNKNOWN
}

fun getMediaType(url: String): MediaType {
    val urlLower = url.lowercase()
    return when {
        urlLower.endsWith(".jpg") || urlLower.endsWith(".jpeg") ||
        urlLower.endsWith(".png") || urlLower.endsWith(".gif") ||
        urlLower.endsWith(".webp") -> MediaType.IMAGE
        urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") ||
        urlLower.endsWith(".avi") || urlLower.endsWith(".mov") ||
        urlLower.endsWith(".flv") || urlLower.endsWith(".wmv") ||
        urlLower.endsWith(".webm") -> MediaType.VIDEO
        else -> MediaType.UNKNOWN
    }
}
