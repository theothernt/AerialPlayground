package com.neilturner.mediaprovidertest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.mediaprovidertest.data.MediaRepository
import com.neilturner.mediaprovidertest.domain.MediaValidator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResultDialog(
    result: MediaRepository.QueryResult,
    imageLoadStatus: MediaValidator.ValidationStatus = MediaValidator.ValidationStatus.Idle,
    videoLoadStatus: MediaValidator.ValidationStatus = MediaValidator.ValidationStatus.Idle,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.9f),
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = if (result is MediaRepository.QueryResult.Success) "Query Result" else "Error",
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (result) {
                    is MediaRepository.QueryResult.Success -> {
                        Text("Total Media count: ${result.count}", color = Color.White)
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // IMAGE SECTION
                            Column(modifier = Modifier.weight(1f)) {
                                if (result.imageSample != null) {
                                    Text("Image Sample:", color = Color.Cyan, fontSize = 16.sp)
                                    Text("URL: ${result.imageSample.url}", color = Color.White, fontSize = 12.sp)
                                    if (result.imageSample.mimeType != null) {
                                        Text("MIME: ${result.imageSample.mimeType}", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                    
                                    when (imageLoadStatus) {
                                        is MediaValidator.ValidationStatus.Loading -> {
                                            Text("Status: Loading...", color = Color.Yellow)
                                        }
                                        is MediaValidator.ValidationStatus.Success -> {
                                            Text("Status: ✓ Loaded (Coil)", color = Color.Green)
                                        }
                                        is MediaValidator.ValidationStatus.Error -> {
                                            Text("Status: ✗ Failed", color = Color.Red)
                                            Text(imageLoadStatus.message, color = Color.Red, fontSize = 12.sp)
                                        }
                                        else -> {}
                                    }
                                } else {
                                    Text("No Image found in sample set", color = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // VIDEO SECTION
                            Column(modifier = Modifier.weight(1f)) {
                                if (result.videoSample != null) {
                                    Text("Video Sample:", color = Color.Cyan, fontSize = 16.sp)
                                    Text("URL: ${result.videoSample.url}", color = Color.White, fontSize = 12.sp)
                                     if (result.videoSample.mimeType != null) {
                                        Text("MIME: ${result.videoSample.mimeType}", color = Color.LightGray, fontSize = 12.sp)
                                    }

                                    when (videoLoadStatus) {
                                        is MediaValidator.ValidationStatus.Loading -> {
                                            Text("Status: Loading...", color = Color.Yellow)
                                        }
                                        is MediaValidator.ValidationStatus.Success -> {
                                            Text("Status: ✓ Loaded (ExoPlayer)", color = Color.Green)
                                        }
                                        is MediaValidator.ValidationStatus.Error -> {
                                            Text("Status: ✗ Failed", color = Color.Red)
                                            Text(videoLoadStatus.message, color = Color.Red, fontSize = 12.sp)
                                        }
                                        else -> {}
                                    }
                                } else {
                                    Text("No Video found in sample set", color = Color.Gray)
                                }
                            }
                        }
                    }
                    is MediaRepository.QueryResult.Error -> {
                        Text("Error occurred:", color = Color.White)
                        Text(result.message, color = Color.White)
                    }
                }
            }
        },
        containerColor = Color.DarkGray
    )
}
