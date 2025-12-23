package com.neilturner.mediaprovidertest.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.coroutines.resume

class MediaValidator(private val context: Context) {

    companion object {
        private const val TAG = "MediaValidator"
    }

    sealed class ValidationStatus {
        data object Idle : ValidationStatus()
        data object Loading : ValidationStatus()
        data class Success(val url: String) : ValidationStatus()
        data class Error(val message: String) : ValidationStatus()
    }

    /**
     * First validates that we can open a FileDescriptor for the URI.
     * Then proceeds to load the image using Coil.
     */
    suspend fun validateImage(url: String): ValidationStatus {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Validate File Descriptor Access
                validateFileDescriptorAccess(url)

                // Step 2: Load with Coil
                Log.d(TAG, "Starting Coil image load for URL: $url")
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()

                val result = context.imageLoader.execute(request)
                if (result.image != null) {
                    Log.d(TAG, "Coil result: ${result.image}")
                    ValidationStatus.Success(url)
                } else {
                    ValidationStatus.Error("Coil returned null image")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating image", e)
                ValidationStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * First validates that we can open a FileDescriptor for the URI.
     * Then proceeds to load the video using ExoPlayer.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun validateVideo(url: String): ValidationStatus {
        return withContext(Dispatchers.Main) {
            try {
                // Step 1: Validate File Descriptor Access (must be done on IO thread)
                withContext(Dispatchers.IO) {
                    validateFileDescriptorAccess(url)
                }

                // Step 2: Load with ExoPlayer
                Log.d(TAG, "Starting ExoPlayer video load for URL: $url")
                
                var result: ValidationStatus = ValidationStatus.Loading
                val exoPlayer = ExoPlayer.Builder(context).build()
                
                try {
                    val mediaItem = MediaItem.fromUri(url)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()

                    // Wait for result using a suspendCancellableCoroutine if we wanted to be purely suspending,
                    // but for simplicity in this refactor, we'll use a callback-to-suspending bridge or just wait.
                    // Since ExoPlayer is async, we need to wrap it.
                    
                    result = suspendVideoLoad(exoPlayer)
                    
                } finally {
                    exoPlayer.release()
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error validating video", e)
                ValidationStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun validateFileDescriptorAccess(url: String) {
        if (url.startsWith("content://")) {
            val uri = url.toUri()
            Log.d(TAG, "Attempting to open FileDescriptor for: $uri")
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    Log.d(TAG, "Successfully opened FileDescriptor. Size: ${pfd.statSize}")
                    if (pfd.fileDescriptor.valid()) {
                        Log.d(TAG, "FileDescriptor is valid.")
                    } else {
                        throw FileNotFoundException("FileDescriptor is invalid")
                    }
                } ?: throw FileNotFoundException("ContentResolver returned null FileDescriptor")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open FileDescriptor", e)
                throw Exception("Failed to open FileDescriptor: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "URL is not content://, skipping FileDescriptor check: $url")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun suspendVideoLoad(player: ExoPlayer): ValidationStatus {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        Log.d(TAG, "Video is ready to play")
                        if (continuation.isActive) {
                            continuation.resume(ValidationStatus.Success("Video Loaded"))
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(ValidationStatus.Error(error.message ?: "Unknown ExoPlayer error"))
                    }
                }
            }
            player.addListener(listener)
            
            continuation.invokeOnCancellation {
                player.removeListener(listener)
            }
        }
    }
}
