package com.neilturner.mediaprovidertest.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.neilturner.mediaprovidertest.data.MediaRepository
import com.neilturner.mediaprovidertest.domain.MediaType
import com.neilturner.mediaprovidertest.domain.MediaValidator
import com.neilturner.mediaprovidertest.ui.components.ResultDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Dependencies
    val repository = remember { MediaRepository(context) }
    val validator = remember { MediaValidator(context) }

    // State
    var showDialog by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<MediaRepository.QueryResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var imageLoadStatus by remember { mutableStateOf<MediaValidator.ValidationStatus>(MediaValidator.ValidationStatus.Idle) }
    var videoLoadStatus by remember { mutableStateOf<MediaValidator.ValidationStatus>(MediaValidator.ValidationStatus.Idle) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                Log.d("MainScreen", "Button clicked - initiating query")
                isLoading = true
                imageLoadStatus = MediaValidator.ValidationStatus.Idle
                videoLoadStatus = MediaValidator.ValidationStatus.Idle
                
                scope.launch {
                    val result = repository.queryMediaCount()
                    
                    if (result is MediaRepository.QueryResult.Success && result.sampleUrl != null) {
                        Log.i("MainScreen", "Query result received: ${result.count} items. Validating URL: ${result.sampleUrl}, MimeType: ${result.mimeType}")
                        
                        val mediaType = MediaType.fromUrl(result.sampleUrl, result.mimeType)
                        when (mediaType) {
                            MediaType.IMAGE -> {
                                imageLoadStatus = MediaValidator.ValidationStatus.Loading
                                imageLoadStatus = validator.validateImage(result.sampleUrl)
                            }
                            MediaType.VIDEO -> {
                                videoLoadStatus = MediaValidator.ValidationStatus.Loading
                                videoLoadStatus = validator.validateVideo(result.sampleUrl)
                            }
                            MediaType.UNKNOWN -> {
                                Log.w("MainScreen", "Unknown media type for: ${result.sampleUrl}")
                            }
                        }
                    } else if (result is MediaRepository.QueryResult.Error) {
                        Log.e("MainScreen", "Query error received: ${result.message}")
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
        ResultDialog(
            result = queryResult!!,
            imageLoadStatus = imageLoadStatus,
            videoLoadStatus = videoLoadStatus,
            onDismiss = {
                showDialog = false
                queryResult = null
                imageLoadStatus = MediaValidator.ValidationStatus.Idle
                videoLoadStatus = MediaValidator.ValidationStatus.Idle
            }
        )
    }
}
