package com.neilturner.mediaprovidertest.domain

enum class MediaType {
    IMAGE, VIDEO, UNKNOWN;

    companion object {
        fun fromUrl(url: String, mimeType: String? = null): MediaType {
            // Priority 1: Check MIME type
            if (mimeType != null) {
                return when {
                    mimeType.startsWith("image/") -> IMAGE
                    mimeType.startsWith("video/") -> VIDEO
                    else -> UNKNOWN
                }
            }

            // Priority 2: Check File Extension
            val urlLower = url.lowercase()
            return when {
                urlLower.endsWith(".jpg") || urlLower.endsWith(".jpeg") ||
                        urlLower.endsWith(".png") || urlLower.endsWith(".gif") ||
                        urlLower.endsWith(".webp") -> IMAGE
                urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") ||
                        urlLower.endsWith(".avi") || urlLower.endsWith(".mov") ||
                        urlLower.endsWith(".flv") || urlLower.endsWith(".wmv") ||
                        urlLower.endsWith(".webm") -> VIDEO
                else -> UNKNOWN
            }
        }
    }
}
