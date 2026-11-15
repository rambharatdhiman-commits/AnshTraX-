package com.example.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.engine.RoadsideProp
import com.example.engine.TerrainPoint
import com.example.model.MapEnvironment
import com.example.model.SurfaceType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object EnvironmentArt {

    private val farPath = Path()
    private val nearPath = Path()
    private val groundPath = Path()

    fun DrawScope.drawRealisticAtmosphere(
        map: MapEnvironment,
        camX: Float,
        w: Float,
        h: Float,
        timeSec: Float
    ) {
        val horizonColor = map.skyBottom
        val zenithColor = map.skyTop
        val midSky = Color(
            red = (zenithColor.red * 0.45f + horizonColor.red * 0.55f),
            green = (zenithColor.green * 0.45f + horizonColor.green * 0.55f),
            blue = (zenithColor.blue * 0.45f + horizonColor.blue * 0.55f)
        )

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to zenithColor,
                0.55f to midSky,
                0.85f to horizonColor,
                1.0f to horizonColor
            ),
            size = size
        )

        if (map.hasRain) {
            val flashCycle = (timeSec * 0.22f) % 1f
            if (flashCycle in 0.88f..0.91f || flashCycle in 0.92f..0.94f) {
                drawRect(Color(0xFFDCEEFF).copy(alpha = 0.22f), size = size)
            }
        } else {
            val sunPos = Offset(w * 0.77f, h * (if (map.isNightOrDusk) 0.34f else 0.22f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF8DC).copy(alpha = 0.88f),
                        Color(0xFFFFD88A).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = sunPos,
                    radius = h * 0.34f
                ),
                radius = h * 0.34f,
                center = sunPos
            )
            drawCircle(
                color = if (map.isNightOrDusk) Color(0xFFFF9F4D) else Color(0xFFFFFCE6),
                radius = h * 0.042f,
                center = sunPos
            )
        }

        // Layered soft atmospheric cloud bands
        val cloudBase = if (map.hasRain) Color(0xFF28333D) else Color(0xFFF6F7F8)
        for (i in 0..5) {
            val cx = ((i * 290f - camX * 2.5f + timeSec * 5f) % (w + 380f) + (w + 380f)) % (w + 380f) - 190f
            val cy = h * (0.10f + (i % 3) * 0.075f)
            val cw = h * (0.32f + (i % 2) * 0.14f)
            val ch = h * 0.068f
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        cloudBase.copy(alpha = if (map.hasRain) 0.75f else 0.45f),
                        cloudBase.copy(alpha = 0.02f)
                    ),
                    startY = cy - ch,
                    endY = cy + ch
                ),
                topLeft = Offset(cx - cw * 0.5f, cy - ch * 0.5f),
                size = Size(cw, ch * 1.4f)
            )
        }

        // Far Parallax Ridge (craggy mountains / skyline)
        farPath.reset()
        farPath.apply {
            moveTo(0f, h)
            var sx = 0f
            while (sx <= w + 32f) {
                val wx = sx * 0.0085f + camX * 0.028f
                val ridge = when (map) {
                    MapEnvironment.MOUNTAIN_ROADS -> abs(sin(wx * 1.3f)) * h * 0.21f + sin(wx * 3.4f) * h * 0.04f
                    MapEnvironment.DESERT -> sin(wx * 0.9f) * h * 0.07f + cos(wx * 1.6f) * h * 0.03f
                    else -> sin(wx) * h * 0.08f + cos(wx * 2.3f) * h * 0.035f
                }
                lineTo(sx, h * 0.56f - ridge)
                sx += 24f
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            path = farPath,
            brush = Brush.verticalGradient(
                colors = listOf(map.hillFarColor, horizonColor.copy(alpha = 0.80f)),
                startY = h * 0.34f,
                endY = h * 0.78f
            )
        )

        // Near Parallax Ridge with organic treeline / ridge silhouette
        nearPath.reset()
        nearPath.apply {
            moveTo(0f, h)
            var sx = 0f
            while (sx <= w + 20f) {
                val wx = sx * 0.014f + camX * 0.075f + 5.1f
                val ridge = sin(wx) * h * 0.055f + cos(wx * 2.1f) * h * 0.025f + sin(wx * 7.5f) * h * 0.008f
                lineTo(sx, h * 0.65f - ridge)
                sx += 18f
            }
            lineTo(w, h)
            close()
        }
        drawPath(
            path = nearPath,
            brush = Brush.verticalGradient(
                colors = listOf(map.hillNearColor, map.hillNearColor.copy(alpha = 0.88f)),
                startY = h * 0.52f,
                endY = h * 0.85f
            )
        )

        // Atmospheric horizon mist band
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, horizonColor.copy(alpha = 0.28f), Color.Transparent),
                startY = h * 0.48f,
                endY = h * 0.72f
            ),
            topLeft = Offset(0f, h * 0.48f),
            size = Size(w, h * 0.24f)
        )
    }

    fun DrawScope.drawRealisticTerrain(
        map: MapEnvironment,
        visiblePts: List<TerrainPoint>,
        textures: ProceduralTextures,
        worldToScreenX: (Float) -> Float,
        worldToScreenY: (Float) -> Float,
        h: Float,
        ppm: Float
    ) {
        if (visiblePts.size < 2) return

        groundPath.reset()
        groundPath.apply {
            moveTo(worldToScreenX(visiblePts.first().x), h + 60f)
            for (pt in visiblePts) {
                lineTo(worldToScreenX(pt.x), worldToScreenY(pt.y))
            }
            lineTo(worldToScreenX(visiblePts.last().x), h + 60f)
            close()
        }

        // Base stratified earth / rock fill
        drawPath(
            path = groundPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    map.groundFillColor,
                    Color(
                        red = map.groundFillColor.red * 0.50f,
                        green = map.groundFillColor.green * 0.50f,
                        blue = map.groundFillColor.blue * 0.50f
                    ),
                    Color(0xFF0D0B09)
                ),
                startY = h * 0.48f,
                endY = h
            )
        )

        // Tileable procedural soil & aggregate grain texture overlay!
        drawPath(path = groundPath, brush = textures.earthGrainBrush, alpha = 0.55f)

        // Road surface crust, shoulder weeds/grass tufts, tyre ruts & reflective puddles
        for (i in 0 until visiblePts.size - 1) {
            val p0 = visiblePts[i]
            val p1 = visiblePts[i + 1]
            val x0 = worldToScreenX(p0.x)
            val y0 = worldToScreenY(p0.y)
            val x1 = worldToScreenX(p1.x)
            val y1 = worldToScreenY(p1.y)

            val crustColor = when (p0.surface) {
                SurfaceType.MUD -> Color(0xFF3A2413)
                SurfaceType.WET -> Color(0xFF304552)
                SurfaceType.ROCK -> Color(0xFF5D5A54)
                SurfaceType.SAND -> Color(0xFFD1A459)
                else -> map.roadTopColor
            }
            val shoulderColor = Color(
                red = crustColor.red * 0.62f,
                green = crustColor.green * 0.62f,
                blue = crustColor.blue * 0.62f
            )

            drawLine(
                color = shoulderColor,
                start = Offset(x0, y0 + ppm * 0.16f),
                end = Offset(x1, y1 + ppm * 0.16f),
                strokeWidth = ppm * 0.20f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = crustColor,
                start = Offset(x0, y0 + ppm * 0.07f),
                end = Offset(x1, y1 + ppm * 0.07f),
                strokeWidth = ppm * 0.14f,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = textures.roadGritBrush,
                start = Offset(x0, y0 + ppm * 0.07f),
                end = Offset(x1, y1 + ppm * 0.07f),
                strokeWidth = ppm * 0.14f,
                cap = StrokeCap.Round
            )

            // Subtle natural roadside wild grass / dry tufts on rural maps
            val hash = abs(sin(p0.x * 17.31f) * 43758.5f) % 1f
            if (p0.surface == SurfaceType.DIRT || p0.surface == SurfaceType.MUD) {
                if (hash > 0.35f) {
                    val grassCol = if (map == MapEnvironment.FARMING_FIELDS) Color(0xFF427A33) else Color(0xFF5B6B36)
                    val gx = (x0 + x1) * 0.5f
                    val gy = (y0 + y1) * 0.5f - ppm * 0.04f
                    drawLine(grassCol, Offset(gx, gy), Offset(gx - ppm * 0.05f, gy - ppm * 0.16f), strokeWidth = 2f)
                    drawLine(grassCol, Offset(gx + ppm * 0.04f, gy), Offset(gx + ppm * 0.08f, gy - ppm * 0.14f), strokeWidth = 2f)
                }
            }

            if (p0.surface == SurfaceType.ASPHALT && (p0.x.toInt() / 4) % 2 == 0) {
                drawLine(
                    color = Color(0xFFE5BA42).copy(alpha = 0.72f),
                    start = Offset(x0, y0 + ppm * 0.03f),
                    end = Offset(x1, y1 + ppm * 0.03f),
                    strokeWidth = ppm * 0.032f
                )
            }

            if (p0.surface == SurfaceType.WET) {
                drawLine(
                    color = Color(0xFFAAD7EB).copy(alpha = 0.58f),
                    start = Offset(x0, y0 - ppm * 0.02f),
                    end = Offset(x1, y1 - ppm * 0.02f),
                    strokeWidth = ppm * 0.042f
                )
            }
        }
    }

    fun DrawScope.drawRealisticProp(
        map: MapEnvironment,
        prop: RoadsideProp,
        sx: Float,
        sy: Float,
        ppm: Float,
        timeSec: Float
    ) {
        val s = ppm * prop.scale
        drawOval(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(sx - s * 0.9f, sy - s * 0.06f),
            size = Size(s * 1.8f, s * 0.15f)
        )

        when (map.propTheme) {
            "village", "rainy" -> {
                when (prop.kind % 3) {
                    0 -> {
                        drawRect(
                            brush = Brush.verticalGradient(listOf(Color(0xFFD0B896), Color(0xFF9C815F))),
                            topLeft = Offset(sx - s * 1.15f, sy - s * 1.30f),
                            size = Size(s * 2.30f, s * 1.30f)
                        )
                        drawRect(Color.Black.copy(alpha = 0.26f), Offset(sx - s * 1.15f, sy - s * 1.30f), Size(s * 2.30f, s * 0.16f))
                        val roof = Path().apply {
                            moveTo(sx - s * 1.35f, sy - s * 1.25f)
                            lineTo(sx, sy - s * 2.05f)
                            lineTo(sx + s * 1.35f, sy - s * 1.25f)
                            close()
                        }
                        drawPath(roof, Brush.verticalGradient(listOf(Color(0xFFA04226), Color(0xFF6E2A15))))
                        drawRect(Color(0xFF332013), Offset(sx - s * 0.28f, sy - s * 0.80f), Size(s * 0.56f, s * 0.80f))
                    }
                    1 -> {
                        // Organic gnarled trunk & irregular layered foliage crown (not plain round blobs)
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF362111), Color(0xFF5C3D23), Color(0xFF2B1A0D))),
                            topLeft = Offset(sx - s * 0.15f, sy - s * 1.72f),
                            size = Size(s * 0.30f, s * 1.72f)
                        )
                        val darkLeaf = if (map.hasRain) Color(0xFF15361C) else Color(0xFF1C4F23)
                        val midLeaf = if (map.hasRain) Color(0xFF214F2B) else Color(0xFF2D6B31)
                        val hiLeaf = if (map.hasRain) Color(0xFF2B6137) else Color(0xFF42803D)
                        drawOval(darkLeaf, Offset(sx - s * 1.05f, sy - s * 2.65f), Size(s * 2.10f, s * 1.15f))
                        drawOval(midLeaf, Offset(sx - s * 0.85f, sy - s * 2.85f), Size(s * 1.65f, s * 0.95f))
                        drawOval(hiLeaf.copy(alpha = 0.72f), Offset(sx - s * 0.45f, sy - s * 2.95f), Size(s * 1.05f, s * 0.62f))
                    }
                    else -> {
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF676B70), Color(0xFF969BA1), Color(0xFF53575C))),
                            topLeft = Offset(sx - s * 0.08f, sy - s * 2.65f),
                            size = Size(s * 0.16f, s * 2.65f)
                        )
                        drawRect(Color(0xFF464A4F), Offset(sx - s * 0.50f, sy - s * 2.45f), Size(s * 1.0f, s * 0.09f))
                    }
                }
            }
            "farm" -> {
                if (prop.kind % 2 == 0) {
                    val sway = sin(timeSec * 2.0f + prop.x) * s * 0.08f
                    for (k in -3..3) {
                        val base = sx + k * s * 0.15f
                        drawLine(
                            color = if (k % 2 == 0) Color(0xFF2A612B) else Color(0xFF517D31),
                            start = Offset(base, sy),
                            end = Offset(base + sway, sy - s * 1.55f),
                            strokeWidth = s * 0.08f,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawArc(
                        brush = Brush.verticalGradient(listOf(Color(0xFFC99834), Color(0xFF825B17))),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(sx - s * 0.90f, sy - s * 1.05f),
                        size = Size(s * 1.80f, s * 2.10f)
                    )
                }
            }
            "bazaar" -> {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFC2B29C), Color(0xFF8A7B67))),
                    topLeft = Offset(sx - s * 1.15f, sy - s * 1.50f),
                    size = Size(s * 2.30f, s * 1.50f)
                )
                drawRect(Color(0xFF281D18), Offset(sx - s * 0.85f, sy - s * 0.95f), Size(s * 1.70f, s * 0.95f))
                val awningCol = if (prop.kind % 2 == 0) Color(0xFFB32424) else Color(0xFFC77514)
                drawRoundRect(awningCol, Offset(sx - s * 1.30f, sy - s * 1.62f), Size(s * 2.60f, s * 0.36f), CornerRadius(s * 0.05f))
            }
            "highway" -> {
                if (prop.kind % 2 == 0) {
                    drawRect(Color(0xFF636A70), Offset(sx - s * 0.07f, sy - s * 2.25f), Size(s * 0.14f, s * 2.25f))
                    drawRoundRect(Color(0xFF135C31), Offset(sx - s * 0.80f, sy - s * 2.35f), Size(s * 1.60f, s * 0.72f), CornerRadius(s * 0.06f))
                    drawRoundRect(Color.White, Offset(sx - s * 0.76f, sy - s * 2.31f), Size(s * 1.52f, s * 0.64f), CornerRadius(s * 0.04f), style = Stroke(2f))
                } else {
                    drawRect(Color(0xFF727A82), Offset(sx - s * 0.06f, sy - s * 2.75f), Size(s * 0.12f, s * 2.75f))
                    drawLine(Color(0xFF727A82), Offset(sx, sy - s * 2.75f), Offset(sx + s * 0.60f, sy - s * 2.52f), strokeWidth = s * 0.08f)
                }
            }
            "forest" -> {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF26180F), Color(0xFF452E1B), Color(0xFF1F130B))),
                    topLeft = Offset(sx - s * 0.17f, sy - s * 2.7f),
                    size = Size(s * 0.34f, s * 2.7f)
                )
                drawOval(Color(0xFF123615), Offset(sx - s * 1.1f, sy - s * 3.4f), Size(s * 2.2f, s * 1.25f))
                drawOval(Color(0xFF1D4F21), Offset(sx - s * 0.85f, sy - s * 3.65f), Size(s * 1.7f, s * 0.95f))
            }
            "desert" -> {
                if (prop.kind % 2 == 0) {
                    drawLine(Color(0xFF395C2C), Offset(sx, sy), Offset(sx, sy - s * 1.35f), strokeWidth = s * 0.20f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF395C2C), Offset(sx, sy - s * 0.80f), Offset(sx - s * 0.38f, sy - s * 1.12f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF395C2C), Offset(sx, sy - s * 0.60f), Offset(sx + s * 0.38f, sy - s * 0.94f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFB8844A), Color(0xFF825A2E))),
                        topLeft = Offset(sx - s * 0.32f, sy - s * 1.10f),
                        size = Size(s * 0.64f, s * 1.10f)
                    )
                }
            }
            "construction" -> {
                if (prop.kind % 2 == 0) {
                    drawRect(Color(0xFFBA7E16), Offset(sx - s * 0.16f, sy - s * 3.3f), Size(s * 0.32f, s * 3.3f))
                    drawRect(Color(0xFFBA7E16), Offset(sx - s * 0.55f, sy - s * 3.3f), Size(s * 2.4f, s * 0.22f))
                    drawLine(Color(0xFF303336), Offset(sx + s * 1.5f, sy - s * 3.1f), Offset(sx + s * 1.5f, sy - s * 1.8f), strokeWidth = 2f)
                } else {
                    drawRect(Color(0xFFB52F1A), Offset(sx - s * 0.68f, sy - s * 0.56f), Size(s * 1.36f, s * 0.56f))
                    drawRect(Color(0xFFD6D2C9), Offset(sx - s * 0.22f, sy - s * 0.56f), Size(s * 0.44f, s * 0.56f))
                }
            }
            "city" -> {
                val bh = s * (2.3f + (prop.kind % 3) * 0.85f)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF333B4D), Color(0xFF1D222E))),
                    topLeft = Offset(sx - s * 0.95f, sy - bh),
                    size = Size(s * 1.90f, bh)
                )
                drawRect(Color(0xFF151920), Offset(sx - s * 0.25f, sy - bh - s * 0.38f), Size(s * 0.50f, s * 0.38f))
                for (wy in 1..3) {
                    drawRect(Color(0xFFFFCA5A).copy(alpha = 0.65f), Offset(sx - s * 0.58f, sy - bh + wy * s * 0.52f), Size(s * 0.36f, s * 0.26f))
                    drawRect(Color(0xFFFFCA5A).copy(alpha = 0.65f), Offset(sx + s * 0.22f, sy - bh + wy * s * 0.52f), Size(s * 0.36f, s * 0.26f))
                }
            }
            "mountain" -> {
                drawRect(Color(0xFF362617), Offset(sx - s * 0.11f, sy - s * 0.9f), Size(s * 0.22f, s * 0.9f))
                for (tier in 0..2) {
                    val base = sy - s * (0.70f + tier * 0.58f)
                    val halfW = s * (0.78f - tier * 0.16f)
                    val pine = Path().apply {
                        moveTo(sx - halfW, base)
                        lineTo(sx, base - s * 0.90f)
                        lineTo(sx + halfW, base)
                        close()
                    }
                    drawPath(pine, Brush.verticalGradient(listOf(Color(0xFF23523A), Color(0xFF122E1E))))
                }
            }
        }
    }
}
