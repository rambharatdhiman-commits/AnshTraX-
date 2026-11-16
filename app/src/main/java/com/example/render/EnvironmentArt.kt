package com.example.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.engine.BackgroundLandmark
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
    private val roofPath = Path()
    private val pinePath = Path()
    private val rayPath = Path()
    private val wirePath = Path()
    private val templePath = Path()

    private var cachedKey: Long = -1L
    private var cachedSkyBrush: Brush? = null
    private var cachedSunBrush: Brush? = null
    private var cachedMistBrush: Brush? = null
    private var cachedFarBrush: Brush? = null
    private var cachedNearBrush: Brush? = null
    private var cachedGroundBrush: Brush? = null
    private var cachedSunCenter: Offset = Offset.Zero
    private var cachedSunRad: Float = 1f

    private fun ensureCachedBrushes(map: MapEnvironment, w: Float, h: Float) {
        val key = (map.ordinal.toLong() shl 40) xor (w.toInt().toLong() shl 20) xor h.toInt().toLong()
        if (key == cachedKey && cachedSkyBrush != null) return
        cachedKey = key

        val horizonColor = map.skyBottom
        val zenithColor = map.skyTop
        val midSky = Color(
            red = (zenithColor.red * 0.45f + horizonColor.red * 0.55f),
            green = (zenithColor.green * 0.45f + horizonColor.green * 0.55f),
            blue = (zenithColor.blue * 0.45f + horizonColor.blue * 0.55f)
        )
        cachedSkyBrush = Brush.verticalGradient(
            0.0f to zenithColor,
            0.52f to midSky,
            0.84f to horizonColor,
            1.0f to horizonColor
        )
        val sunPos = Offset(w * 0.77f, h * (if (map.isNightOrDusk) 0.34f else 0.21f))
        cachedSunCenter = sunPos
        cachedSunRad = h * 0.38f
        cachedSunBrush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFF8DC).copy(alpha = 0.92f),
                Color(0xFFFFD88A).copy(alpha = 0.38f),
                Color.Transparent
            ),
            center = sunPos,
            radius = cachedSunRad
        )
        cachedMistBrush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, horizonColor.copy(alpha = 0.32f), Color.Transparent),
            startY = h * 0.46f,
            endY = h * 0.74f
        )
        cachedFarBrush = Brush.verticalGradient(
            colors = listOf(map.hillFarColor, horizonColor.copy(alpha = 0.78f)),
            startY = h * 0.32f,
            endY = h * 0.78f
        )
        cachedNearBrush = Brush.verticalGradient(
            colors = listOf(map.hillNearColor, map.hillNearColor.copy(alpha = 0.90f)),
            startY = h * 0.50f,
            endY = h * 0.85f
        )
        cachedGroundBrush = Brush.verticalGradient(
            colors = listOf(
                map.groundFillColor,
                Color(
                    red = map.groundFillColor.red * 0.52f,
                    green = map.groundFillColor.green * 0.52f,
                    blue = map.groundFillColor.blue * 0.52f
                ),
                Color(0xFF0D0B09)
            ),
            startY = h * 0.48f,
            endY = h
        )
    }

    fun DrawScope.drawRealisticAtmosphere(
        map: MapEnvironment,
        landmarks: List<BackgroundLandmark>,
        camX: Float,
        w: Float,
        h: Float,
        timeSec: Float
    ) {
        ensureCachedBrushes(map, w, h)
        cachedSkyBrush?.let { drawRect(brush = it, size = size) }

        if (map.hasRain) {
            val flashCycle = (timeSec * 0.22f) % 1f
            if (flashCycle in 0.88f..0.91f || flashCycle in 0.92f..0.94f) {
                drawRect(Color(0xFFDCEEFF).copy(alpha = 0.24f), size = size)
            }
        } else {
            cachedSunBrush?.let {
                drawCircle(brush = it, radius = cachedSunRad, center = cachedSunCenter)
            }
            // Subtle warm sunbeams radiating into morning/dusk haze
            val rayCol = if (map.isNightOrDusk) Color(0xFFFFB266).copy(alpha = 0.055f) else Color(0xFFFFF8DC).copy(alpha = 0.075f)
            for (r in 0..2) {
                val baseAng = -0.35f + r * 0.42f + sin(timeSec * 0.25f + r) * 0.03f
                rayPath.reset()
                rayPath.moveTo(cachedSunCenter.x, cachedSunCenter.y)
                rayPath.lineTo(cachedSunCenter.x + cos(baseAng + 1.9f) * h * 0.72f, h * 0.65f)
                rayPath.lineTo(cachedSunCenter.x + cos(baseAng + 2.18f) * h * 0.72f, h * 0.65f)
                rayPath.close()
                drawPath(rayPath, rayCol)
            }
            drawCircle(
                color = if (map.isNightOrDusk) Color(0xFFFF9F4D) else Color(0xFFFFFCE6),
                radius = h * 0.044f,
                center = cachedSunCenter
            )
            // Twinkling dusk/night stars on night maps
            if (map.isNightOrDusk) {
                for (st in 0..10) {
                    val sx = ((st * 173f + 41f) % w)
                    val sy = h * (0.05f + ((st * 37) % 24) / 100f)
                    val twinkle = 0.35f + 0.55f * abs(sin(timeSec * 1.8f + st))
                    drawCircle(Color.White.copy(alpha = twinkle), radius = 1.8f, center = Offset(sx, sy))
                }
            }
        }

        // Layered billowing cumulus clouds with bright top & shaded base (zero allocations)
        val cloudHi = if (map.hasRain) Color(0xFF3E4A56) else Color(0xFFFFFCFA)
        val cloudLo = if (map.hasRain) Color(0xFF212932) else Color(0xFFDDE4EB)
        val cloudAlpha = if (map.hasRain) 0.62f else 0.48f
        for (i in 0..5) {
            val cx = ((i * 290f - camX * 2.4f + timeSec * 4.5f) % (w + 360f) + (w + 360f)) % (w + 360f) - 180f
            val cy = h * (0.09f + (i % 3) * 0.072f)
            val cw = h * (0.30f + (i % 2) * 0.14f)
            val ch = h * 0.082f
            drawOval(cloudLo.copy(alpha = cloudAlpha * 0.65f), Offset(cx - cw * 0.50f, cy - ch * 0.25f), Size(cw, ch * 0.78f))
            drawOval(cloudHi.copy(alpha = cloudAlpha), Offset(cx - cw * 0.40f, cy - ch * 0.55f), Size(cw * 0.72f, ch * 0.72f))
            drawOval(cloudHi.copy(alpha = cloudAlpha * 0.85f), Offset(cx - cw * 0.12f, cy - ch * 0.68f), Size(cw * 0.48f, ch * 0.60f))
        }

        // Distant V-flock of flying birds drifting gently in sky
        if (!map.hasRain) {
            val birdCol = if (map.isNightOrDusk) Color(0xFF2B2228) else Color(0xFF2E3842).copy(alpha = 0.62f)
            for (b in 0..4) {
                val bx = ((b * 230f - camX * 4.5f + timeSec * 22f) % (w + 200f) + (w + 200f)) % (w + 200f) - 100f
                val by = h * (0.16f + (b % 3) * 0.055f) + sin(timeSec * 3.5f + b) * 5f
                val flap = sin(timeSec * 8.5f + b * 1.4f) * 4.5f
                drawLine(birdCol, Offset(bx - 8f, by - flap), Offset(bx, by), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(birdCol, Offset(bx, by), Offset(bx + 8f, by - flap), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }

        // Far Parallax Ridge (with optional snow caps on Mountain Roads)
        farPath.reset()
        farPath.apply {
            moveTo(0f, h)
            var sx = 0f
            while (sx <= w + 36f) {
                val wx = sx * 0.0085f + camX * 0.028f
                val ridge = when (map) {
                    MapEnvironment.MOUNTAIN_ROADS -> abs(sin(wx * 1.3f)) * h * 0.22f + sin(wx * 3.4f) * h * 0.04f
                    MapEnvironment.DESERT -> sin(wx * 0.9f) * h * 0.07f + cos(wx * 1.6f) * h * 0.03f
                    else -> sin(wx) * h * 0.08f + cos(wx * 2.3f) * h * 0.035f
                }
                lineTo(sx, h * 0.55f - ridge)
                sx += 36f
            }
            lineTo(w, h)
            close()
        }
        cachedFarBrush?.let { drawPath(path = farPath, brush = it) }

        if (map == MapEnvironment.MOUNTAIN_ROADS) {
            drawPath(
                path = farPath,
                color = Color(0xFFEAF4FB).copy(alpha = 0.28f),
                style = Stroke(width = 4f)
            )
        }

        // Midground Parallax Background Landmarks (water tanks, brick kilns, forts, pylons, silos!)
        val parallaxPxPerMeter = h * 0.036f
        for (lm in landmarks) {
            val sx = w * 0.5f + (lm.x - camX) * parallaxPxPerMeter
            if (sx < -160f || sx > w + 160f) continue
            val sy = h * 0.635f - sin(sx * 0.012f + camX * 0.04f) * h * 0.028f
            drawBackgroundLandmark(map, lm, sx, sy, h * 0.115f, timeSec)
        }

        // Near Parallax Ridge
        nearPath.reset()
        nearPath.apply {
            moveTo(0f, h)
            var sx = 0f
            while (sx <= w + 28f) {
                val wx = sx * 0.014f + camX * 0.075f + 5.1f
                val ridge = sin(wx) * h * 0.055f + cos(wx * 2.1f) * h * 0.025f + sin(wx * 7.5f) * h * 0.008f
                lineTo(sx, h * 0.65f - ridge)
                sx += 28f
            }
            lineTo(w, h)
            close()
        }
        cachedNearBrush?.let { drawPath(path = nearPath, brush = it) }

        // Atmospheric horizon mist band
        cachedMistBrush?.let {
            drawRect(brush = it, topLeft = Offset(0f, h * 0.46f), size = Size(w, h * 0.28f))
        }
    }

    private fun DrawScope.drawBackgroundLandmark(
        map: MapEnvironment,
        lm: BackgroundLandmark,
        sx: Float,
        sy: Float,
        unit: Float,
        timeSec: Float
    ) {
        val s = unit * lm.scale
        val silDark = Color(
            red = map.hillNearColor.red * 0.72f,
            green = map.hillNearColor.green * 0.72f,
            blue = map.hillNearColor.blue * 0.75f
        ).copy(alpha = 0.78f)
        val silAccent = Color(
            red = map.hillFarColor.red * 0.85f,
            green = map.hillFarColor.green * 0.85f,
            blue = map.hillFarColor.blue * 0.85f
        ).copy(alpha = 0.75f)

        when (map.propTheme) {
            "village", "farm", "rainy" -> {
                when (lm.kind % 4) {
                    0 -> {
                        // Iconic Brick Kiln Chimney ("Bhatta") with faint curling smoke
                        drawRect(silDark, Offset(sx - s * 0.55f, sy - s * 0.42f), Size(s * 1.1f, s * 0.42f))
                        templePath.reset()
                        templePath.moveTo(sx - s * 0.16f, sy - s * 0.42f)
                        templePath.lineTo(sx - s * 0.08f, sy - s * 1.95f)
                        templePath.lineTo(sx + s * 0.08f, sy - s * 1.95f)
                        templePath.lineTo(sx + s * 0.16f, sy - s * 0.42f)
                        templePath.close()
                        drawPath(templePath, silDark)
                        val puffDrift = (timeSec * 12f + lm.x) % 28f
                        drawCircle(Color.White.copy(alpha = 0.18f), s * 0.14f, Offset(sx - puffDrift * 0.6f, sy - s * 2.08f - puffDrift * 0.4f))
                    }
                    1 -> {
                        // Rural Overhead Water Tank ("Paani ki Tanki")
                        drawLine(silDark, Offset(sx - s * 0.24f, sy), Offset(sx - s * 0.18f, sy - s * 1.1f), strokeWidth = 3f)
                        drawLine(silDark, Offset(sx + s * 0.24f, sy), Offset(sx + s * 0.18f, sy - s * 1.1f), strokeWidth = 3f)
                        drawLine(silDark, Offset(sx - s * 0.20f, sy - s * 0.55f), Offset(sx + s * 0.20f, sy - s * 0.55f), strokeWidth = 2f)
                        drawRoundRect(silDark, Offset(sx - s * 0.36f, sy - s * 1.52f), Size(s * 0.72f, s * 0.45f), CornerRadius(4f))
                        drawRoundRect(silAccent, Offset(sx - s * 0.40f, sy - s * 1.60f), Size(s * 0.80f, s * 0.12f), CornerRadius(3f))
                    }
                    2 -> {
                        // Distant Village Temple Shikhara with Flag
                        drawRect(silDark, Offset(sx - s * 0.40f, sy - s * 0.65f), Size(s * 0.80f, s * 0.65f))
                        templePath.reset()
                        templePath.moveTo(sx - s * 0.30f, sy - s * 0.65f)
                        templePath.quadraticTo(sx - s * 0.16f, sy - s * 1.35f, sx, sy - s * 1.58f)
                        templePath.quadraticTo(sx + s * 0.16f, sy - s * 1.35f, sx + s * 0.30f, sy - s * 0.65f)
                        templePath.close()
                        drawPath(templePath, silDark)
                        drawLine(silDark, Offset(sx, sy - s * 1.58f), Offset(sx, sy - s * 1.88f), strokeWidth = 2f)
                        drawRect(Color(0xFFD96424).copy(alpha = 0.72f), Offset(sx, sy - s * 1.88f), Size(s * 0.22f, s * 0.13f))
                    }
                    else -> {
                        // Farm Windpump / Grain Silo Silhouette
                        drawRect(silDark, Offset(sx - s * 0.34f, sy - s * 1.05f), Size(s * 0.68f, s * 1.05f))
                        drawArc(silDark, 180f, 180f, true, Offset(sx - s * 0.34f, sy - s * 1.35f), Size(s * 0.68f, s * 0.60f))
                    }
                }
            }
            "highway", "city", "bazaar" -> {
                // High-Voltage Transmission Pylon Tower + City Blocks
                if (lm.kind % 2 == 0) {
                    drawLine(silDark, Offset(sx - s * 0.28f, sy), Offset(sx - s * 0.08f, sy - s * 1.9f), strokeWidth = 2.6f)
                    drawLine(silDark, Offset(sx + s * 0.28f, sy), Offset(sx + s * 0.08f, sy - s * 1.9f), strokeWidth = 2.6f)
                    drawLine(silDark, Offset(sx - s * 0.48f, sy - s * 1.38f), Offset(sx + s * 0.48f, sy - s * 1.38f), strokeWidth = 2.2f)
                    drawLine(silDark, Offset(sx - s * 0.36f, sy - s * 1.72f), Offset(sx + s * 0.36f, sy - s * 1.72f), strokeWidth = 2.2f)
                    drawLine(silDark, Offset(sx - s * 0.20f, sy - s * 0.8f), Offset(sx + s * 0.20f, sy - s * 0.8f), strokeWidth = 2f)
                } else {
                    val h1 = s * 1.45f
                    val h2 = s * 1.85f
                    drawRect(silDark, Offset(sx - s * 0.55f, sy - h1), Size(s * 0.48f, h1))
                    drawRect(silAccent, Offset(sx, sy - h2), Size(s * 0.52f, h2))
                    if (map.isNightOrDusk) {
                        drawRect(Color(0xFFFFCE67).copy(alpha = 0.45f), Offset(sx + s * 0.14f, sy - h2 + s * 0.22f), Size(s * 0.22f, s * 0.10f))
                    }
                }
            }
            "desert" -> {
                // Distant Sandstone Rajasthani Fort Ramparts
                drawRect(silDark, Offset(sx - s * 0.85f, sy - s * 0.85f), Size(s * 1.70f, s * 0.85f))
                drawArc(silDark, 180f, 180f, true, Offset(sx - s * 0.35f, sy - s * 1.35f), Size(s * 0.70f, s * 0.62f))
                for (c in -3..3) {
                    drawRect(silDark, Offset(sx + c * s * 0.24f - s * 0.08f, sy - s * 0.98f), Size(s * 0.15f, s * 0.14f))
                }
            }
            "construction" -> {
                // Distant Batching Silos & Crane Boom
                drawRect(silDark, Offset(sx - s * 0.45f, sy - s * 1.35f), Size(s * 0.35f, s * 1.35f))
                drawRect(silDark, Offset(sx + s * 0.05f, sy - s * 1.65f), Size(s * 0.38f, s * 1.65f))
                drawLine(silDark, Offset(sx - s * 0.6f, sy - s * 1.55f), Offset(sx + s * 0.9f, sy - s * 1.80f), strokeWidth = 3f)
            }
            else -> {
                // Forest Fire Watchtower / Mountain Pine Peak Lodge
                drawLine(silDark, Offset(sx - s * 0.22f, sy), Offset(sx - s * 0.12f, sy - s * 1.45f), strokeWidth = 2.8f)
                drawLine(silDark, Offset(sx + s * 0.22f, sy), Offset(sx + s * 0.12f, sy - s * 1.45f), strokeWidth = 2.8f)
                drawRect(silDark, Offset(sx - s * 0.28f, sy - s * 1.72f), Size(s * 0.56f, s * 0.28f))
            }
        }
    }

    fun DrawScope.drawRealisticTerrain(
        map: MapEnvironment,
        visiblePts: List<TerrainPoint>,
        textures: ProceduralTextures,
        worldToScreenX: (Float) -> Float,
        worldToScreenY: (Float) -> Float,
        h: Float,
        ppm: Float,
        timeSec: Float
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

        // Base stratified earth fill using cached brush + tileable grain texture
        cachedGroundBrush?.let { drawPath(path = groundPath, brush = it) }
        drawPath(path = groundPath, brush = textures.earthGrainBrush, alpha = 0.62f)

        // Subsurface geological strata ribbon & roadside grass verge
        for (i in 0 until visiblePts.size - 1) {
            val p0 = visiblePts[i]
            val p1 = visiblePts[i + 1]
            val x0 = worldToScreenX(p0.x)
            val y0 = worldToScreenY(p0.y)
            val x1 = worldToScreenX(p1.x)
            val y1 = worldToScreenY(p1.y)

            val crustColor = when (p0.surface) {
                SurfaceType.MUD -> if (p0.isMudTrap) Color(0xFF2B190B) else Color(0xFF3A2413)
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
            val deepStrataColor = Color(
                red = map.groundFillColor.red * 0.35f,
                green = map.groundFillColor.green * 0.35f,
                blue = map.groundFillColor.blue * 0.35f
            )

            // Deeper soil strata band
            drawLine(
                color = deepStrataColor,
                start = Offset(x0, y0 + ppm * 0.42f),
                end = Offset(x1, y1 + ppm * 0.42f),
                strokeWidth = ppm * 0.24f,
                cap = StrokeCap.Round
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
                start = Offset(x0, y0 + ppm * 0.06f),
                end = Offset(x1, y1 + ppm * 0.06f),
                strokeWidth = ppm * 0.14f,
                cap = StrokeCap.Round
            )

            // Crisp twin tractor lug-rut highlight on rural roads
            if (p0.surface == SurfaceType.DIRT || p0.surface == SurfaceType.SAND) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(x0, y0 + ppm * 0.04f),
                    end = Offset(x1, y1 + ppm * 0.04f),
                    strokeWidth = ppm * 0.032f
                )
            }

            // Deep wet Kichad (mud) bog layer & shimmering slurry ruts (extra deep & glossy for Mud Traps!)
            if (p0.surface == SurfaceType.MUD) {
                val depthBoost = if (p0.isMudTrap) 1.38f else 1.0f
                drawLine(
                    color = Color(0xFF1F1105),
                    start = Offset(x0, y0 + ppm * 0.07f),
                    end = Offset(x1, y1 + ppm * 0.07f),
                    strokeWidth = ppm * 0.26f * depthBoost,
                    cap = StrokeCap.Round
                )
                val sheenAlpha = if (p0.isMudTrap) 0.72f + 0.18f * sin(timeSec * 3.2f + p0.x) else 0.56f
                drawLine(
                    color = Color(0xFF7D6044).copy(alpha = sheenAlpha.coerceIn(0.3f, 0.9f)),
                    start = Offset(x0, y0 - ppm * 0.015f),
                    end = Offset(x1, y1 - ppm * 0.015f),
                    strokeWidth = ppm * 0.060f * depthBoost,
                    cap = StrokeCap.Round
                )
                val clodX = (x0 + x1) * 0.5f
                val clodY = (y0 + y1) * 0.5f + ppm * 0.05f
                drawOval(Color(0xFF361F0D), Offset(clodX - ppm * 0.12f, clodY - ppm * 0.055f), Size(ppm * 0.24f, ppm * 0.11f))
                if (p0.isMudTrap) {
                    // Glossy waterlogged puddle meniscus on bog traps
                    drawOval(
                        color = Color(0xFF9E8A70).copy(alpha = 0.40f),
                        topLeft = Offset(clodX - ppm * 0.14f, clodY - ppm * 0.09f),
                        size = Size(ppm * 0.28f, ppm * 0.055f)
                    )
                }
            }

            // Embedded gravel pebble flecks & natural roadside wild grass + wildflowers
            val hash = abs(sin(p0.x * 17.31f) * 43758.5f) % 1f
            if (hash > 0.52f) {
                drawCircle(
                    color = if (p0.surface == SurfaceType.ROCK) Color(0xFF8A857C) else Color(0xFF281A10).copy(alpha = 0.45f),
                    radius = ppm * 0.038f,
                    center = Offset((x0 + x1) * 0.5f, (y0 + y1) * 0.5f + ppm * 0.24f)
                )
            }
            if ((p0.surface == SurfaceType.DIRT || p0.surface == SurfaceType.MUD) && hash > 0.34f) {
                val grassCol = if (map == MapEnvironment.FARMING_FIELDS) Color(0xFF438232) else Color(0xFF5D7236)
                val gx = (x0 + x1) * 0.5f
                val gy = (y0 + y1) * 0.5f - ppm * 0.03f
                drawLine(grassCol, Offset(gx, gy), Offset(gx - ppm * 0.055f, gy - ppm * 0.17f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                drawLine(grassCol, Offset(gx + ppm * 0.04f, gy), Offset(gx + ppm * 0.085f, gy - ppm * 0.15f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                if (hash > 0.80f && p0.surface == SurfaceType.DIRT) {
                    // Tiny wildflower dots on grass shoulder
                    drawCircle(Color(0xFFFFD54F), ppm * 0.025f, Offset(gx - ppm * 0.05f, gy - ppm * 0.18f))
                }
            }

            if (p0.surface == SurfaceType.ASPHALT && (p0.x.toInt() / 4) % 2 == 0) {
                drawLine(
                    color = Color(0xFFE5BA42).copy(alpha = 0.78f),
                    start = Offset(x0, y0 + ppm * 0.03f),
                    end = Offset(x1, y1 + ppm * 0.03f),
                    strokeWidth = ppm * 0.034f
                )
            }

            if (p0.surface == SurfaceType.WET) {
                drawLine(
                    color = Color(0xFFAAD7EB).copy(alpha = 0.62f),
                    start = Offset(x0, y0 - ppm * 0.02f),
                    end = Offset(x1, y1 - ppm * 0.02f),
                    strokeWidth = ppm * 0.045f
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
                when (prop.kind % 8) {
                    0 -> {
                        // Terracotta Mud-Brick Hut + wooden window + earthen matka
                        drawRect(Color(0xFFB59A77), Offset(sx - s * 1.15f, sy - s * 1.30f), Size(s * 2.30f, s * 1.30f))
                        drawRect(Color(0xFF9C815F), Offset(sx - s * 1.15f, sy - s * 0.56f), Size(s * 2.30f, s * 0.56f))
                        drawRect(Color.Black.copy(alpha = 0.24f), Offset(sx - s * 1.15f, sy - s * 1.30f), Size(s * 2.30f, s * 0.16f))
                        roofPath.reset()
                        roofPath.moveTo(sx - s * 1.38f, sy - s * 1.24f)
                        roofPath.lineTo(sx, sy - s * 2.08f)
                        roofPath.lineTo(sx + s * 1.38f, sy - s * 1.24f)
                        roofPath.close()
                        drawPath(roofPath, Color(0xFF8C351B))
                        drawPath(roofPath, Color(0xFF5C2110), style = Stroke(width = 2.5f, join = StrokeJoin.Round))
                        drawRect(Color(0xFF332013), Offset(sx - s * 0.28f, sy - s * 0.80f), Size(s * 0.56f, s * 0.80f))
                        drawRect(Color(0xFF4A3120), Offset(sx + s * 0.48f, sy - s * 0.92f), Size(s * 0.36f, s * 0.36f))
                        drawCircle(Color(0xFFB04F2B), s * 0.11f, Offset(sx - s * 0.82f, sy - s * 0.11f))
                    }
                    1 -> {
                        // Lush Layered Banyan / Neem Tree with hanging roots
                        drawRect(Color(0xFF4D321C), Offset(sx - s * 0.16f, sy - s * 1.78f), Size(s * 0.32f, s * 1.78f))
                        drawRect(Color(0xFF362111), Offset(sx - s * 0.16f, sy - s * 1.78f), Size(s * 0.11f, s * 1.78f))
                        drawLine(Color(0xFF5D3F26), Offset(sx - s * 0.55f, sy - s * 1.80f), Offset(sx - s * 0.55f, sy - s * 0.35f), strokeWidth = 2.2f)
                        drawLine(Color(0xFF5D3F26), Offset(sx + s * 0.52f, sy - s * 1.72f), Offset(sx + s * 0.52f, sy - s * 0.42f), strokeWidth = 2.0f)
                        val darkLeaf = if (map.hasRain) Color(0xFF15361C) else Color(0xFF1C4F23)
                        val midLeaf = if (map.hasRain) Color(0xFF214F2B) else Color(0xFF2D6B31)
                        val hiLeaf = if (map.hasRain) Color(0xFF2B6137) else Color(0xFF42803D)
                        drawOval(darkLeaf, Offset(sx - s * 1.12f, sy - s * 2.70f), Size(s * 2.24f, s * 1.18f))
                        drawOval(midLeaf, Offset(sx - s * 0.88f, sy - s * 2.92f), Size(s * 1.76f, s * 0.98f))
                        drawOval(hiLeaf.copy(alpha = 0.76f), Offset(sx - s * 0.46f, sy - s * 3.00f), Size(s * 1.08f, s * 0.64f))
                    }
                    2 -> {
                        // Concrete Electric Pole with Dual Overhead Sagging Wires
                        drawRect(Color(0xFF7B8086), Offset(sx - s * 0.08f, sy - s * 2.72f), Size(s * 0.16f, s * 2.72f))
                        drawRect(Color(0xFF464A4F), Offset(sx - s * 0.54f, sy - s * 2.48f), Size(s * 1.08f, s * 0.09f))
                        drawCircle(Color(0xFFD6E0E8), s * 0.045f, Offset(sx - s * 0.46f, sy - s * 2.52f))
                        drawCircle(Color(0xFFD6E0E8), s * 0.045f, Offset(sx + s * 0.46f, sy - s * 2.52f))
                        wirePath.reset()
                        wirePath.moveTo(sx - s * 3.6f, sy - s * 2.42f)
                        wirePath.quadraticTo(sx - s * 1.8f, sy - s * 2.15f, sx - s * 0.46f, sy - s * 2.52f)
                        wirePath.moveTo(sx + s * 0.46f, sy - s * 2.52f)
                        wirePath.quadraticTo(sx + s * 2.2f, sy - s * 2.15f, sx + s * 4.0f, sy - s * 2.42f)
                        drawPath(wirePath, Color(0xFF202428).copy(alpha = 0.68f), style = Stroke(width = 1.6f))
                    }
                    3 -> {
                        // Village Handpump ("Nal") with Concrete Platform & Red Handle
                        drawRect(Color(0xFF8C8F94), Offset(sx - s * 0.55f, sy - s * 0.18f), Size(s * 1.10f, s * 0.18f))
                        drawRect(Color(0xFF2E6B40), Offset(sx - s * 0.08f, sy - s * 0.96f), Size(s * 0.16f, s * 0.78f))
                        drawLine(Color(0xFF1F4D2C), Offset(sx - s * 0.06f, sy - s * 0.68f), Offset(sx - s * 0.30f, sy - s * 0.60f), strokeWidth = 4f, cap = StrokeCap.Round)
                        drawLine(Color(0xFFC23224), Offset(sx + s * 0.02f, sy - s * 0.92f), Offset(sx + s * 0.44f, sy - s * 0.65f), strokeWidth = 3.5f, cap = StrokeCap.Round)
                    }
                    4 -> {
                        // Bhoosa Haystack Mound + Leaning Bullock Cart Wooden Wheel
                        drawArc(Color(0xFFA87823), 180f, 180f, true, Offset(sx - s * 0.95f, sy - s * 1.10f), Size(s * 1.90f, s * 2.20f))
                        drawArc(Color(0xFFC99834), 205f, 120f, true, Offset(sx - s * 0.78f, sy - s * 0.95f), Size(s * 1.56f, s * 1.75f))
                        val wx = sx + s * 0.72f
                        val wy = sy - s * 0.40f
                        drawCircle(Color(0xFF5D3A1F), s * 0.40f, Offset(wx, wy), style = Stroke(width = 4.5f))
                        drawCircle(Color(0xFF5D3A1F), s * 0.08f, Offset(wx, wy))
                    }
                    5 -> {
                        // Yellow & White Village KM Milestone + Wooden Fence
                        for (f in -1..1) {
                            drawRect(Color(0xFF6B4729), Offset(sx + f * s * 0.52f - s * 0.06f, sy - s * 0.62f), Size(s * 0.12f, s * 0.62f))
                        }
                        drawRect(Color(0xFF825936), Offset(sx - s * 0.72f, sy - s * 0.44f), Size(s * 1.44f, s * 0.09f))
                        drawRoundRect(Color(0xFFF2F2EE), Offset(sx - s * 0.24f, sy - s * 0.72f), Size(s * 0.48f, s * 0.72f), CornerRadius(6f))
                        drawRoundRect(Color(0xFFE0A51F), Offset(sx - s * 0.24f, sy - s * 0.72f), Size(s * 0.48f, s * 0.26f), CornerRadius(6f))
                    }
                    6 -> {
                        // Brick Water Well ("Kuan") with Rope Pulley Arch
                        drawRect(Color(0xFF9C4B33), Offset(sx - s * 0.68f, sy - s * 0.58f), Size(s * 1.36f, s * 0.58f))
                        drawRect(Color(0xFF6B2F1D), Offset(sx - s * 0.74f, sy - s * 0.64f), Size(s * 1.48f, s * 0.10f))
                        drawLine(Color(0xFF54351C), Offset(sx - s * 0.52f, sy - s * 0.62f), Offset(sx - s * 0.52f, sy - s * 1.45f), strokeWidth = 4f)
                        drawLine(Color(0xFF54351C), Offset(sx + s * 0.52f, sy - s * 0.62f), Offset(sx + s * 0.52f, sy - s * 1.45f), strokeWidth = 4f)
                        drawLine(Color(0xFF54351C), Offset(sx - s * 0.55f, sy - s * 1.45f), Offset(sx + s * 0.55f, sy - s * 1.45f), strokeWidth = 4.5f)
                        drawCircle(Color(0xFF3E444A), s * 0.14f, Offset(sx, sy - s * 1.30f))
                    }
                    else -> {
                        // Swaying Sarson (Yellow Mustard) & Green Crop Patch
                        val sway = sin(timeSec * 2.2f + prop.x) * s * 0.09f
                        for (k in -4..4) {
                            val base = sx + k * s * 0.14f
                            drawLine(
                                color = if (k % 2 == 0) Color(0xFF2D6E2E) else Color(0xFF568A35),
                                start = Offset(base, sy),
                                end = Offset(base + sway, sy - s * 1.25f),
                                strokeWidth = s * 0.07f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(Color(0xFFE6BE2E), s * 0.075f, Offset(base + sway, sy - s * 1.28f))
                        }
                    }
                }
            }
            "farm" -> {
                when (prop.kind % 4) {
                    0 -> {
                        val sway = sin(timeSec * 2.0f + prop.x) * s * 0.08f
                        for (k in -4..4) {
                            val base = sx + k * s * 0.15f
                            drawLine(
                                color = if (k % 2 == 0) Color(0xFF2A612B) else Color(0xFF517D31),
                                start = Offset(base, sy),
                                end = Offset(base + sway, sy - s * 1.55f),
                                strokeWidth = s * 0.08f,
                                cap = StrokeCap.Round
                            )
                            if (k % 2 == 0) drawCircle(Color(0xFFEBC334), s * 0.06f, Offset(base + sway, sy - s * 1.56f))
                        }
                    }
                    1 -> {
                        drawArc(Color(0xFFA87823), 180f, 180f, true, Offset(sx - s * 0.90f, sy - s * 1.05f), Size(s * 1.80f, s * 2.10f))
                    }
                    2 -> {
                        // Canal Irrigation Tube-well Shed
                        drawRect(Color(0xFFD6CEC0), Offset(sx - s * 0.65f, sy - s * 0.95f), Size(s * 1.30f, s * 0.95f))
                        drawRect(Color(0xFF2C5A7A), Offset(sx - s * 0.72f, sy - s * 1.08f), Size(s * 1.44f, s * 0.16f))
                        drawRect(Color(0xFF3E454C), Offset(sx + s * 0.70f, sy - s * 0.55f), Size(s * 0.35f, s * 0.12f))
                    }
                    else -> {
                        // Neem Tree
                        drawRect(Color(0xFF4D321C), Offset(sx - s * 0.14f, sy - s * 1.70f), Size(s * 0.28f, s * 1.70f))
                        drawOval(Color(0xFF1E5726), Offset(sx - s * 1.0f, sy - s * 2.65f), Size(s * 2.0f, s * 1.15f))
                        drawOval(Color(0xFF347A37), Offset(sx - s * 0.7f, sy - s * 2.85f), Size(s * 1.4f, s * 0.85f))
                    }
                }
            }
            "bazaar" -> {
                if (prop.kind % 3 == 0) {
                    // Chai / Dhaba Stall with Glowing Bulb
                    drawRect(Color(0xFFA89882), Offset(sx - s * 1.15f, sy - s * 1.50f), Size(s * 2.30f, s * 1.50f))
                    drawRect(Color(0xFF281D18), Offset(sx - s * 0.85f, sy - s * 0.95f), Size(s * 1.70f, s * 0.95f))
                    val awningCol = if (prop.kind % 2 == 0) Color(0xFFB32424) else Color(0xFFC77514)
                    drawRoundRect(awningCol, Offset(sx - s * 1.30f, sy - s * 1.62f), Size(s * 2.60f, s * 0.36f), CornerRadius(s * 0.05f))
                    drawCircle(Color(0xFFFFD54F), s * 0.08f, Offset(sx, sy - s * 1.15f))
                } else if (prop.kind % 3 == 1) {
                    // Festive Bazaar Arch Pole + Buntings
                    drawRect(Color(0xFF5D4037), Offset(sx - s * 0.06f, sy - s * 2.4f), Size(s * 0.12f, s * 2.4f))
                    for (b in 0..4) {
                        val bx = sx + b * s * 0.26f
                        val col = if (b % 2 == 0) Color(0xFFFF9800) else Color(0xFFE91E63)
                        drawCircle(col, s * 0.09f, Offset(bx, sy - s * 2.2f + abs(b - 2) * s * 0.08f))
                    }
                } else {
                    // Stacked Market Crates & Handcart ("Thela")
                    drawRect(Color(0xFF7D5029), Offset(sx - s * 0.85f, sy - s * 0.62f), Size(s * 1.70f, s * 0.24f))
                    drawCircle(Color(0xFF26292E), s * 0.24f, Offset(sx - s * 0.50f, sy - s * 0.24f))
                    drawCircle(Color(0xFF26292E), s * 0.24f, Offset(sx + s * 0.50f, sy - s * 0.24f))
                    drawOval(Color(0xFFE08E26), Offset(sx - s * 0.55f, sy - s * 0.92f), Size(s * 1.10f, s * 0.36f))
                }
            }
            "highway" -> {
                when (prop.kind % 3) {
                    0 -> {
                        drawRect(Color(0xFF636A70), Offset(sx - s * 0.07f, sy - s * 2.25f), Size(s * 0.14f, s * 2.25f))
                        drawRoundRect(Color(0xFF135C31), Offset(sx - s * 0.80f, sy - s * 2.35f), Size(s * 1.60f, s * 0.72f), CornerRadius(s * 0.06f))
                        drawRoundRect(Color.White, Offset(sx - s * 0.76f, sy - s * 2.31f), Size(s * 1.52f, s * 0.64f), CornerRadius(s * 0.04f), style = Stroke(2f))
                    }
                    1 -> {
                        drawRect(Color(0xFF727A82), Offset(sx - s * 0.06f, sy - s * 2.75f), Size(s * 0.12f, s * 2.75f))
                        drawLine(Color(0xFF727A82), Offset(sx, sy - s * 2.75f), Offset(sx + s * 0.60f, sy - s * 2.52f), strokeWidth = s * 0.08f)
                    }
                    else -> {
                        // Highway KM Stone + Tree Shade
                        drawRoundRect(Color(0xFFF2F2EE), Offset(sx - s * 0.28f, sy - s * 0.75f), Size(s * 0.56f, s * 0.75f), CornerRadius(6f))
                        drawRoundRect(Color(0xFFDB9B1A), Offset(sx - s * 0.28f, sy - s * 0.75f), Size(s * 0.56f, s * 0.28f), CornerRadius(6f))
                    }
                }
            }
            "forest" -> {
                if (prop.kind % 2 == 0) {
                    drawRect(Color(0xFF392516), Offset(sx - s * 0.17f, sy - s * 2.75f), Size(s * 0.34f, s * 2.75f))
                    drawOval(Color(0xFF123615), Offset(sx - s * 1.1f, sy - s * 3.45f), Size(s * 2.2f, s * 1.25f))
                    drawOval(Color(0xFF1D4F21), Offset(sx - s * 0.85f, sy - s * 3.70f), Size(s * 1.7f, s * 0.95f))
                } else {
                    // Stacked Felled Timber Logs & Forest Signboard
                    for (r in 0..2) {
                        drawCircle(Color(0xFF5D391D), s * 0.22f, Offset(sx + (r - 1) * s * 0.42f, sy - s * 0.22f))
                        drawCircle(Color(0xFFB38456), s * 0.14f, Offset(sx + (r - 1) * s * 0.42f, sy - s * 0.22f))
                    }
                    drawCircle(Color(0xFF5D391D), s * 0.22f, Offset(sx, sy - s * 0.56f))
                    drawCircle(Color(0xFFB38456), s * 0.14f, Offset(sx, sy - s * 0.56f))
                }
            }
            "desert" -> {
                when (prop.kind % 3) {
                    0 -> {
                        drawLine(Color(0xFF395C2C), Offset(sx, sy), Offset(sx, sy - s * 1.35f), strokeWidth = s * 0.20f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF395C2C), Offset(sx, sy - s * 0.80f), Offset(sx - s * 0.38f, sy - s * 1.12f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF395C2C), Offset(sx, sy - s * 0.60f), Offset(sx + s * 0.38f, sy - s * 0.94f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                    }
                    1 -> {
                        // Desert Date Palm Tree
                        drawLine(Color(0xFF6B4828), Offset(sx, sy), Offset(sx + s * 0.18f, sy - s * 2.2f), strokeWidth = s * 0.18f, cap = StrokeCap.Round)
                        for (fr in -2..2) {
                            drawLine(Color(0xFF346B2E), Offset(sx + s * 0.18f, sy - s * 2.2f), Offset(sx + s * 0.18f + fr * s * 0.35f, sy - s * 1.85f + abs(fr) * s * 0.12f), strokeWidth = s * 0.10f, cap = StrokeCap.Round)
                        }
                    }
                    else -> {
                        drawRect(Color(0xFF9F6F3D), Offset(sx - s * 0.36f, sy - s * 1.12f), Size(s * 0.72f, s * 1.12f))
                    }
                }
            }
            "construction" -> {
                when (prop.kind % 3) {
                    0 -> {
                        drawRect(Color(0xFFBA7E16), Offset(sx - s * 0.16f, sy - s * 3.3f), Size(s * 0.32f, s * 3.3f))
                        drawRect(Color(0xFFBA7E16), Offset(sx - s * 0.55f, sy - s * 3.3f), Size(s * 2.4f, s * 0.22f))
                        drawLine(Color(0xFF303336), Offset(sx + s * 1.5f, sy - s * 3.1f), Offset(sx + s * 1.5f, sy - s * 1.8f), strokeWidth = 2f)
                    }
                    1 -> {
                        drawRect(Color(0xFFB52F1A), Offset(sx - s * 0.68f, sy - s * 0.56f), Size(s * 1.36f, s * 0.56f))
                        drawRect(Color(0xFFD6D2C9), Offset(sx - s * 0.22f, sy - s * 0.56f), Size(s * 0.44f, s * 0.56f))
                    }
                    else -> {
                        // Stacked Cement Pipes
                        drawCircle(Color(0xFF7D8287), s * 0.32f, Offset(sx - s * 0.34f, sy - s * 0.32f))
                        drawCircle(Color(0xFF282B2E), s * 0.20f, Offset(sx - s * 0.34f, sy - s * 0.32f))
                        drawCircle(Color(0xFF7D8287), s * 0.32f, Offset(sx + s * 0.34f, sy - s * 0.32f))
                        drawCircle(Color(0xFF282B2E), s * 0.20f, Offset(sx + s * 0.34f, sy - s * 0.32f))
                    }
                }
            }
            "city" -> {
                val bh = s * (2.3f + (prop.kind % 3) * 0.85f)
                drawRect(Color(0xFF283040), Offset(sx - s * 0.95f, sy - bh), Size(s * 1.90f, bh))
                drawRect(Color(0xFF151920), Offset(sx - s * 0.25f, sy - bh - s * 0.38f), Size(s * 0.50f, s * 0.38f))
                for (wy in 1..3) {
                    drawRect(Color(0xFFFFCA5A).copy(alpha = 0.68f), Offset(sx - s * 0.58f, sy - bh + wy * s * 0.52f), Size(s * 0.36f, s * 0.26f))
                    drawRect(Color(0xFFFFCA5A).copy(alpha = 0.68f), Offset(sx + s * 0.22f, sy - bh + wy * s * 0.52f), Size(s * 0.36f, s * 0.26f))
                }
            }
            "mountain" -> {
                if (prop.kind % 3 != 2) {
                    drawRect(Color(0xFF362617), Offset(sx - s * 0.11f, sy - s * 0.9f), Size(s * 0.22f, s * 0.9f))
                    for (tier in 0..2) {
                        val base = sy - s * (0.70f + tier * 0.58f)
                        val halfW = s * (0.78f - tier * 0.16f)
                        pinePath.reset()
                        pinePath.moveTo(sx - halfW, base)
                        pinePath.lineTo(sx, base - s * 0.90f)
                        pinePath.lineTo(sx + halfW, base)
                        pinePath.close()
                        drawPath(pinePath, Color(0xFF1B402D))
                    }
                } else {
                    // Hilltop Warning Chevron Board + Wooden Guardrail
                    drawRect(Color(0xFF42474D), Offset(sx - s * 0.06f, sy - s * 1.45f), Size(s * 0.12f, s * 1.45f))
                    drawRect(Color(0xFFFFC107), Offset(sx - s * 0.44f, sy - s * 1.55f), Size(s * 0.88f, s * 0.44f))
                    drawRect(Color(0xFF1C1F24), Offset(sx - s * 0.24f, sy - s * 1.55f), Size(s * 0.18f, s * 0.44f))
                }
            }
        }
    }
}
