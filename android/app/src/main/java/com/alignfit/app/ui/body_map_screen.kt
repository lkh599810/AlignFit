package com.alignfit.app.ui

import android.content.Context
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import org.xmlpull.v1.XmlPullParser
import kotlin.math.min

// ─── Body Map Data ────────────────────────────────────────────────────────────

data class BodyRegion(val id: String, val points: List<Offset>) {
    val path: Path by lazy {
        Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    }
}

data class BodyDecoration(val cx: Float, val cy: Float, val rx: Float, val ry: Float)

data class BodyMap(
    val viewBoxWidth: Float,
    val viewBoxHeight: Float,
    val regions: List<BodyRegion>,
    val decorations: List<BodyDecoration>
)

enum class BodyMapView(val label: String, val fileName: String) {
    FRONT("정면", "body_front.svg"),
    BACK("후면", "body_back.svg"),
    LEFT("좌측면", "body_left.svg"),
    RIGHT("우측면", "body_right.svg")
}

// ─── SVG Parsing ──────────────────────────────────────────────────────────────

// Parses the real SVG asset files. Selectable regions are <polygon> elements
// with an id attribute; <ellipse> elements are decorative (head).
fun loadBodyMap(context: Context, fileName: String): BodyMap {
    context.assets.open("body_maps/$fileName").use { stream ->
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var viewBoxWidth = 200f
        var viewBoxHeight = 440f
        val regions = mutableListOf<BodyRegion>()
        val decorations = mutableListOf<BodyDecoration>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "svg" -> {
                        parser.getAttributeValue(null, "viewBox")?.let { viewBox ->
                            val parts = viewBox.trim().split(Regex("[\\s,]+"))
                            if (parts.size == 4) {
                                viewBoxWidth = parts[2].toFloatOrNull() ?: viewBoxWidth
                                viewBoxHeight = parts[3].toFloatOrNull() ?: viewBoxHeight
                            }
                        }
                    }
                    "polygon" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val pointsAttr = parser.getAttributeValue(null, "points")
                        if (id != null && pointsAttr != null) {
                            val numbers = pointsAttr.trim()
                                .split(Regex("[\\s,]+"))
                                .mapNotNull { it.toFloatOrNull() }
                            val points = (numbers.indices step 2).mapNotNull { i ->
                                if (i + 1 < numbers.size) Offset(numbers[i], numbers[i + 1]) else null
                            }
                            if (points.size >= 3) regions.add(BodyRegion(id, points))
                        }
                    }
                    "ellipse" -> {
                        val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull()
                        val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull()
                        val rx = parser.getAttributeValue(null, "rx")?.toFloatOrNull()
                        val ry = parser.getAttributeValue(null, "ry")?.toFloatOrNull()
                        if (cx != null && cy != null && rx != null && ry != null) {
                            decorations.add(BodyDecoration(cx, cy, rx, ry))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return BodyMap(viewBoxWidth, viewBoxHeight, regions, decorations)
    }
}

// ─── Hit Testing ──────────────────────────────────────────────────────────────

// Standard ray-casting point-in-polygon test.
private fun polygonContains(points: List<Offset>, x: Float, y: Float): Boolean {
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val pi = points[i]
        val pj = points[j]
        if ((pi.y > y) != (pj.y > y) &&
            x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// ─── Page 3: Body Map Selection ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMapSelectionScreen(
    selectedRegions: Set<String>,
    onRegionToggled: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bodyMaps = remember {
        BodyMapView.values().associateWith { view ->
            runCatching { loadBodyMap(context, view.fileName) }.getOrNull()
        }
    }
    var currentView by remember { mutableStateOf(BodyMapView.FRONT) }

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val baseStrokeColor = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val selectedStrokeColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "통증 부위 직접 선택", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "통증이 느껴지는 부위를 그림에서 직접 탭하여 표시해 주세요. 여러 부위를 선택할 수 있습니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            TabRow(selectedTabIndex = currentView.ordinal) {
                BodyMapView.values().forEach { view ->
                    Tab(
                        selected = currentView == view,
                        onClick = { currentView = view },
                        text = { Text(view.label, fontSize = 13.sp) }
                    )
                }
            }

            if (currentView == BodyMapView.FRONT) {
                Text(
                    text = "정면 그림은 거울을 보듯 화면 왼쪽이 내 몸의 왼쪽입니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val map = bodyMaps[currentView]
            if (map == null) {
                Text(
                    text = "바디맵 리소스를 불러오지 못했습니다.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .align(Alignment.CenterHorizontally)
                        .aspectRatio(map.viewBoxWidth / map.viewBoxHeight)
                        .pointerInput(currentView) {
                            detectTapGestures { tap ->
                                val scale = min(
                                    size.width / map.viewBoxWidth,
                                    size.height / map.viewBoxHeight
                                )
                                val dx = (size.width - map.viewBoxWidth * scale) / 2f
                                val dy = (size.height - map.viewBoxHeight * scale) / 2f
                                val vx = (tap.x - dx) / scale
                                val vy = (tap.y - dy) / scale
                                map.regions.lastOrNull { polygonContains(it.points, vx, vy) }
                                    ?.let { onRegionToggled(it.id) }
                            }
                        }
                ) {
                    val scale = min(
                        size.width / map.viewBoxWidth,
                        size.height / map.viewBoxHeight
                    )
                    val dx = (size.width - map.viewBoxWidth * scale) / 2f
                    val dy = (size.height - map.viewBoxHeight * scale) / 2f

                    withTransform({
                        translate(dx, dy)
                        scale(scale, scale, Offset.Zero)
                    }) {
                        map.decorations.forEach { deco ->
                            drawOval(
                                color = baseColor,
                                topLeft = Offset(deco.cx - deco.rx, deco.cy - deco.ry),
                                size = Size(deco.rx * 2, deco.ry * 2)
                            )
                            drawOval(
                                color = baseStrokeColor,
                                topLeft = Offset(deco.cx - deco.rx, deco.cy - deco.ry),
                                size = Size(deco.rx * 2, deco.ry * 2),
                                style = Stroke(width = 1.2f)
                            )
                        }
                        map.regions.forEach { region ->
                            val isSelected = region.id in selectedRegions
                            drawPath(
                                path = region.path,
                                color = if (isSelected) selectedColor else baseColor
                            )
                            drawPath(
                                path = region.path,
                                color = if (isSelected) selectedStrokeColor else baseStrokeColor,
                                style = Stroke(width = 1.2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedRegions.isNotEmpty()) {
                Text(
                    text = "선택한 부위 (탭하면 해제됩니다)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    selectedRegions.sorted().forEach { id ->
                        AssistChip(
                            onClick = { onRegionToggled(id) },
                            label = { Text(HomecareDictionary.svgRegionLabel(id), fontSize = 12.sp) }
                        )
                    }
                }
            } else {
                Text(
                    text = "아직 선택한 부위가 없습니다. 선택하지 않고 다음으로 진행할 수도 있습니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("이전", fontSize = 16.sp)
            }
            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("다음", fontSize = 16.sp)
            }
        }
    }
}
