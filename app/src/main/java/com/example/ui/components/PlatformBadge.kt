package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreamingPlatforms

@Composable
fun PlatformBadge(
    platformName: String,
    modifier: Modifier = Modifier
) {
    val info = StreamingPlatforms.getByName(platformName)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(info.color.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = platformName,
                tint = info.color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = info.name,
                color = info.color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
