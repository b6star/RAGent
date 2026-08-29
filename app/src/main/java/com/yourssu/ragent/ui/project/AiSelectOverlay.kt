package com.yourssu.ragent.ui.project

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

enum class AiSelectionKind { Text, Image }

@Composable
fun AiSelectOverlay(
    onDismiss: () -> Unit,
    onSelectionChanged: (Rect) -> Unit = {},
    onAskExisting: (Rect, AiSelectionKind) -> Unit,
    onAskNew: (Rect, AiSelectionKind) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var selection by remember { mutableStateOf<Rect?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var previousSelection by remember { mutableStateOf<Rect?>(null) }
    var selectionKind by remember { mutableStateOf(AiSelectionKind.Text) }
    val textSize = 12.sp
    val pulse by rememberInfiniteTransition(label = "ai-select-pulse").animateFloat(
        initialValue = 0.43f,
        targetValue = 0.63f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "ai-select-overlay"
    )
    val borderRotation by rememberInfiniteTransition(label = "ai-select-border").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Restart),
        label = "ai-select-border-rotation"
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    previousSelection = selection
                    dragStart = it
                    selection = Rect(it, it)
                },
                onDrag = { change, _ ->
                    val start = dragStart ?: return@detectDragGestures
                    selection = Rect(start, change.position)
                    change.consume()
                },
                onDragEnd = {
                    dragStart = null
                    val candidate = selection
                    val start = dragStart
                    if (candidate != null && start != null && previousSelection != null &&
                        !previousSelection!!.contains(start) && candidate.width < 12f && candidate.height < 12f
                    ) {
                        selection = null
                        onSelectionChanged(Rect.Zero)
                    } else {
                        candidate?.let(onSelectionChanged)
                    }
                    previousSelection = null
                }
            )
        }) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF070B18).copy(alpha = pulse),
                        Color(0xFF170A2A).copy(alpha = pulse * 0.8f),
                        Color(0xFF061B24).copy(alpha = pulse)
                    )
                )
            )
            selection?.let {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    brush = run {
                        val radians = borderRotation * PI.toFloat() / 180f
                        val direction = Offset(cos(radians), sin(radians))
                        val center = it.center
                        val radius = maxOf(it.size.width, it.size.height)
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6EE7FF), Color(0xFF8B5CF6), Color(0xFFFF5CC8)),
                            start = center - direction * radius,
                            end = center + direction * radius
                        )
                    },
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        selection?.let { rect ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Keep the type selector inside the content area; a negative
                    // offset lets the Scaffold top bar draw over it.
                    .offset { IntOffset(rect.center.x.roundToInt(), (rect.top - 56.dp.toPx()).coerceAtLeast(16.dp.toPx()).roundToInt()) }
                    .zIndex(2f)
                    .graphicsLayer { translationX = -size.width / 2f },
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(4.dp), Arrangement.spacedBy(4.dp)) {
                    SelectionKindButton("텍스트", selectionKind == AiSelectionKind.Text, textSize = textSize) {
                        selectionKind = AiSelectionKind.Text
                    }
                    SelectionKindButton("이미지", selectionKind == AiSelectionKind.Image, textSize = textSize) {
                        selectionKind = AiSelectionKind.Image
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(rect.center.x.roundToInt(), (rect.bottom + 8.dp.toPx()).roundToInt()) }
                    .zIndex(2f)
                    .graphicsLayer { translationX = -size.width / 2f },
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), Arrangement.spacedBy(8.dp)) {
                    Text("기존 대화", Modifier.clickable { onAskExisting(rect, selectionKind) }.padding(4.dp),  fontSize = textSize)
                    VerticalDivider(
                        modifier = Modifier.height(20.dp).align(Alignment.CenterVertically),
                        thickness = 0.5.dp
                    )
                    Text("새 대화", Modifier.clickable { onAskNew(rect, selectionKind) }.padding(4.dp),  fontSize = textSize)
                }
            }
        }
    }
}

@Composable
private fun SelectionKindButton(
    label: String,
    selected: Boolean,
    textSize: TextUnit,
    onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.Black else Color.Transparent
    ) {
        Text(
            text = label,
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            fontSize = textSize,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}
