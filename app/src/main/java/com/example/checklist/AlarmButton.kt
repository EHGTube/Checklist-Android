package com.example.checklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun AlarmButton(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable { onToggle(!enabled) }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // The alarm icon
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = if (enabled) "Notifications enabled" else "Notifications disabled",
            tint = if (enabled) Color(0xFF4CAF50) else Color.Gray
        )

        // Diagonal line when disabled
        if (!enabled) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height)
                }

                drawPath(
                    path = path,
                    color = Color(0xFFE91E63),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }
    }
}