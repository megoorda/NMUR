package com.nmur.reelnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.Knowledge
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

private class Graph(val nodes: List<Note>, val pos: Array<Offset>, val edges: List<Triple<Int, Int, Double>>)

/** Force-directed layout: connected notes pull together, everything else pushes apart. */
private fun layout(notes: List<Note>): Graph {
    val n = notes.size
    val feats = notes.map { Knowledge.features(it) }
    val edges = ArrayList<Triple<Int, Int, Double>>()
    for (i in 0 until n) for (j in i + 1 until n) {
        val s = Knowledge.similarity(feats[i], feats[j], notes[i].category == notes[j].category)
        if (s >= 2.0) edges += Triple(i, j, s)
    }
    val rnd = Random(42)
    val pos = Array(n) { Offset(rnd.nextFloat() * 1000f, rnd.nextFloat() * 1000f) }
    val k = sqrt(1_000_000f / max(1, n))
    var temp = 100f
    repeat(300) {
        val disp = Array(n) { Offset.Zero }
        for (i in 0 until n) for (j in i + 1 until n) {
            val d = pos[i] - pos[j]
            val dist = max(d.getDistance(), 0.01f)
            val f = d / dist * (k * k / dist)
            disp[i] += f; disp[j] -= f
        }
        for ((a, b, w) in edges) {
            val d = pos[a] - pos[b]
            val dist = max(d.getDistance(), 0.01f)
            val f = d / dist * (dist * dist / k) * minOf(w.toFloat() / 3f, 2f)
            disp[a] -= f; disp[b] += f
        }
        // Gentle pull to the centre so unconnected notes don't fly away.
        for (i in 0 until n) {
            disp[i] += (Offset(500f, 500f) - pos[i]) * 0.02f
            val len = max(disp[i].getDistance(), 0.01f)
            pos[i] += disp[i] / len * minOf(len, temp)
        }
        temp *= 0.985f
    }
    return Graph(notes, pos, edges)
}

private val palette = listOf(
    0xFF7B2FF7, 0xFFF107A3, 0xFF00A6FB, 0xFF06D6A0, 0xFFFFB703, 0xFFEF476F, 0xFF8338EC, 0xFF3A86FF, 0xFFFB5607,
).map { Color(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val ctx = LocalContext.current
    val graph by produceState<Graph?>(null) {
        value = withContext(Dispatchers.Default) {
            layout(AppDatabase.get(ctx).notes().allNow().filter { it.status == Status.DONE }.take(250))
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val edgeColor = MaterialTheme.colorScheme.outlineVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge graph") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val g = graph
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                g == null -> Text("Building graph…", Modifier.padding(16.dp))
                g.nodes.isEmpty() -> Text("No notes yet.", Modifier.padding(16.dp))
                else -> {
                    val cats = g.nodes.map { it.category }.distinct()
                    val minX = g.pos.minOf { it.x }; val maxX = g.pos.maxOf { it.x }
                    val minY = g.pos.minOf { it.y }; val maxY = g.pos.maxOf { it.y }
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, p, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.3f, 6f); pan += p
                                }
                            }
                            .pointerInput(g) {
                                detectTapGestures { tap ->
                                    val fit = fitter(size.width.toFloat(), size.height.toFloat(), minX, maxX, minY, maxY)
                                    val hit = g.pos.indices.minByOrNull { (fit(g.pos[it]) * scale + pan - tap).getDistance() }
                                    if (hit != null && (fit(g.pos[hit]) * scale + pan - tap).getDistance() < 60f) {
                                        onOpenNote(g.nodes[hit].id)
                                    }
                                }
                            },
                    ) {
                        val fit = fitter(size.width, size.height, minX, maxX, minY, maxY)
                        fun p(i: Int) = fit(g.pos[i]) * scale + pan
                        for ((a, b, w) in g.edges) {
                            drawLine(edgeColor, p(a), p(b), strokeWidth = minOf(1f + w.toFloat() / 2f, 6f))
                        }
                        val degree = IntArray(g.nodes.size)
                        g.edges.forEach { (a, b, _) -> degree[a]++; degree[b]++ }
                        val paint = android.graphics.Paint().apply {
                            color = textColor; textSize = 28f; isAntiAlias = true
                        }
                        g.nodes.forEachIndexed { i, note ->
                            val c = palette[cats.indexOf(note.category) % palette.size]
                            drawCircle(c, radius = 14f + minOf(degree[i], 8) * 3f, center = p(i))
                            if (scale > 0.8f || degree[i] >= 3) {
                                drawIntoCanvas {
                                    val label = note.title.take(28)
                                    it.nativeCanvas.drawText(label, p(i).x + 20f, p(i).y + 8f, paint)
                                }
                            }
                        }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text("Pinch to zoom · drag to move · tap a dot to open", style = MaterialTheme.typography.labelSmall)
                        cats.take(9).forEachIndexed { i, c ->
                            Text("● $c", color = palette[i % palette.size], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/** Maps layout coordinates into the screen with a margin. */
private fun fitter(w: Float, h: Float, minX: Float, maxX: Float, minY: Float, maxY: Float): (Offset) -> Offset {
    val margin = 80f
    val sx = (w - 2 * margin) / max(maxX - minX, 1f)
    val sy = (h - 2 * margin) / max(maxY - minY, 1f)
    val s = minOf(sx, sy)
    val ox = (w - (maxX - minX) * s) / 2f
    val oy = (h - (maxY - minY) * s) / 2f
    return { o -> Offset(ox + (o.x - minX) * s, oy + (o.y - minY) * s) }
}
