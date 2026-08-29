package com.yourssu.ragent.ui.project

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
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
    var selectionKind by remember { mutableStateOf(AiSelectionKind.Text) }
    val textSize = 12.sp
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragStart = it; selection = Rect(it, it) },
                onDrag = { change, _ ->
                    val start = dragStart ?: return@detectDragGestures
                    selection = Rect(start, change.position)
                    change.consume()
                },
                onDragEnd = {
                    dragStart = null
                    selection?.let(onSelectionChanged)
                }
            )
        }) {
            drawRect(Color.Black.copy(alpha = 0.16f))
            selection?.let {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(Color(0xFF4F8CFF), it.topLeft, it.size, CornerRadius(20.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
        }
        selection?.let { rect ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(rect.center.x.roundToInt(), (rect.top - 56.dp.toPx()).coerceAtLeast(8.dp.toPx()).roundToInt()) }
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
