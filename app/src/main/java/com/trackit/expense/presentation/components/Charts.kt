package com.trackit.expense.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackit.expense.data.local.dao.CategoryTotal

/**
 * Custom Pie Chart using Compose Canvas API.
 * Draws sequential arcs for each category proportional to their spend.
 *
 * @param data List of category totals.
 * @param modifier Modifier for the chart container.
 */
@Composable
fun CategoryPieChart(
    data: List<CategoryTotal>,
    modifier: Modifier = Modifier
) {
    val totalSpend = remember(data) { data.sumOf { it.total }.coerceAtLeast(1.0) }
    
    // Generate distinct colors for categories
    val categoryColors = remember(data) {
        data.mapIndexed { index, _ ->
            val hue = (index * 137.5f) % 360f // Golden angle approximation for variety
            Color.hsl(hue, 0.65f, 0.6f)
        }
    }

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size.minDimension
            val strokeWidth = 50.dp.toPx()
            val radius = (canvasSize - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            var currentAngle = -90f // Start from top
            
            data.forEachIndexed { index, item ->
                val sweepAngle = (item.total / totalSpend * 360f).toFloat()
                
                drawArc(
                    color = categoryColors[index],
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                
                currentAngle += sweepAngle
            }
            
            // If empty, draw a background ring
            if (data.isEmpty()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        
        // Inner total text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Total",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                "₹${"%,.0f".format(totalSpend)}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
