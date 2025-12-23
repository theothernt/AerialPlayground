package com.neilturner.mediaprovidertest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (result) {
                    is MediaRepository.QueryResult.Success -> {
                        Text("Media count: ${result.count}", color = Color.White)
                        if (result.sampleUrl != null) {
                            Text("Sample URL: ${result.sampleUrl}", color = Color.White)
                        } else {
                            Text("No URL found in random row", color = Color.White)
                        }
                        if (result.samplePath != null) {
                            Text("Sample Path: ${result.samplePath}", color = Color.White)
                        }
                        if (result.mimeType != null) {
                            Text("MIME Type: ${result.mimeType}", color = Color.White)
                        }
                        if (result.resolvedFilename != null) {
                            Text("Filename: ${result.resolvedFilename}", color = Color.White)
                        }

                        // Display image loading status
                        if (imageLoadStatus !is MediaValidator.ValidationStatus.Idle) {
                            Text("", color = Color.White) // spacer
                            Text("Image Status:", color = Color.Cyan)
                            when (imageLoadStatus) {
                                is MediaValidator.ValidationStatus.Loading -> {
                                    Text("Loading...", color = Color.Yellow)
                                    CircularProgressIndicator(color = Color.White)
                                }
                                is MediaValidator.ValidationStatus.Success -> {
                                    Text("✓ Successfully loaded", color = Color.Green)
                                }
                                is MediaValidator.ValidationStatus.Error -> {
                                    Text("✗ Failed to load", color = Color.Red)
                                    Text(imageLoadStatus.message, color = Color.Red)
                                }
                            }
                        }

                        // Display video loading status
                        if (videoLoadStatus !is MediaValidator.ValidationStatus.Idle) {
                            Text("", color = Color.White) // spacer
                            Text("Video Status:", color = Color.Cyan)
                            when (videoLoadStatus) {
                                is MediaValidator.ValidationStatus.Loading -> {
                                    Text("Loading...", color = Color.Yellow)
                                    CircularProgressIndicator(color = Color.White)
                                }
                                is MediaValidator.ValidationStatus.Success -> {
                                    Text("✓ Successfully loaded", color = Color.Green)
                                }
                                is MediaValidator.ValidationStatus.Error -> {
                                    Text("✗ Failed to load", color = Color.Red)
                                    Text(videoLoadStatus.message, color = Color.Red)
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
