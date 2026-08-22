package com.daftari.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.domain.Money

@Composable
fun SmoothLineChart(
    data: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) {
        Box(modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val min = data.minOfOrNull { it.second } ?: 0L
    val range = (max - min).coerceAtLeast(1L).toFloat()

    Column(modifier.fillMaxWidth()) {
        Canvas(modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp, bottom = 8.dp)) {
            val width = size.width
            val height = size.height
            val stepX = if (data.size > 1) width / (data.size - 1) else width

            val path = Path()
            val points = data.mapIndexed { index, pair ->
                val x = index * stepX
                val y = height - ((pair.second - min).toFloat() / range) * height * 0.8f // Leave some top padding
                Offset(x, y)
            }

            if (points.size == 1) {
                drawCircle(color = lineColor, radius = 6f, center = points.first())
                return@Canvas
            }

            path.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val controlX = (p1.x + p2.x) / 2
                path.cubicTo(controlX, p1.y, controlX, p2.y, p2.x, p2.y)
            }

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw gradient fill below the line
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.4f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { Text(it.first, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun DoughnutChart(
    data: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFF0F7B5A), Color(0xFFC62828), Color(0xFF1565C0), 
        Color(0xFFF57F17), Color(0xFF6A1B9A), Color(0xFF00838F)
    )
) {
    val total = data.sumOf { it.second }.coerceAtLeast(1L)
    val validData = data.filter { it.second > 0 }

    if (validData.isEmpty()) {
        Box(modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_activity), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(180.dp).fillMaxWidth()) {
            Canvas(Modifier.size(160.dp)) {
                var startAngle = -90f
                validData.forEachIndexed { index, pair ->
                    val sweepAngle = (pair.second.toFloat() / total) * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 30.dp.toPx(), cap = StrokeCap.Butt),
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chart_total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money(total).format(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Legend
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            validData.forEachIndexed { index, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(12.dp)) {
                        drawCircle(colors[index % colors.size])
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(pair.first, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val percent = ((pair.second.toDouble() / total) * 100).toInt()
                    Text("$percent%", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(pair.second).format(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
