package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AvatarBadge(
    name: String?,
    address: String,
    photoUri: String? = null,
    size: Dp = 48.dp,
    colorHex: String? = null,
    modifier: Modifier = Modifier
) {
    val initial = when {
        !name.isNullOrBlank() -> {
            val parts = name.trim().split(" ")
            if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                name.take(2).uppercase()
            }
        }
        address.isNotBlank() -> address.takeLast(2).uppercase()
        else -> "?"
    }

    val backgroundColor = try {
        if (!colorHex.isNullOrBlank()) {
            Color(android.graphics.Color.parseColor(colorHex))
        } else {
            val palette = listOf(
                Color(0xFF2563EB),
                Color(0xFF7C3AED),
                Color(0xFFDB2777),
                Color(0xFF059669),
                Color(0xFFD97706),
                Color(0xFF0891B2),
                Color(0xFF4F46E5)
            )
            palette[kotlin.math.abs(address.hashCode()) % palette.size]
        }
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .testTag("avatar_badge"),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name ?: address,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = if (size > 40.dp) 16.sp else 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
