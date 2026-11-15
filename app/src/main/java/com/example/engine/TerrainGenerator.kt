package com.example.engine

import com.example.model.MapEnvironment
import com.example.model.SurfaceType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class TerrainPoint(
    val x: Float,
    val y: Float,
    val surface: SurfaceType
)

data class CollectibleItem(
    val id: Int,
    val x: Float,
    val yOffsetAboveGround: Float,
    val isFuel: Boolean,
    var collected: Boolean = false
)

data class RoadsideProp(
    val x: Float,
    val kind: Int, // 0..4 variant per map theme
    val scale: Float
)

class TerrainGenerator(
    private val map: MapEnvironment,
    seed: Long = 108241L
) {
    companion object {
        const val STEP_X = 1.2f // Fine 1.2m terrain segments for smooth tyre-road contact
    }

    private val rng = Random(seed + map.ordinal * 7919L)
    private val points = ArrayList<TerrainPoint>(512)
    val collectibles = ArrayList<CollectibleItem>(256)
    val props = ArrayList<RoadsideProp>(256)
    val checkpoints = ArrayList<Float>(64)

    private var nextCollectibleId = 1
    private var nextFuelX = 165f
    private var nextCoinClusterX = 48f
    private var nextCheckpointX = 250f
    private var nextPropX = -10f

    // Smooth procedural wave accumulators
    private var lastY = 0f
    private var slopeBias = 0f
    private val phaseA = rng.nextFloat() * 100f
    private val phaseB = rng.nextFloat() * 100f
    private val phaseC = rng.nextFloat() * 100f

    init {
        // Generate initial buffer from -30m to +180m
        var x = -30f
        while (x <= 180f) {
            appendPoint(x)
            x += STEP_X
        }
    }

    fun ensureGeneratedUpTo(targetX: Float) {
        var lastX = points.last().x
        while (lastX < targetX) {
            lastX += STEP_X
            appendPoint(lastX)
        }
    }

    private fun appendPoint(x: Float) {
        val y: Float
        val surface: SurfaceType

        if (x <= 24f) {
            // Smooth flat launchpad for tractor + trailer spawn
            y = 0f
            surface = if (map.primarySurface == SurfaceType.MUD) SurfaceType.DIRT else map.primarySurface
        } else {
            // Progressive difficulty scaling gently with distance
            val distFactor = ((x - 24f) / 900f).coerceIn(0f, 1.35f)
            val amp = map.hillAmplitude * (0.55f + 0.55f * distFactor)
            val bump = map.bumpiness * (0.5f + 0.55f * distFactor)

            // Long sweeping terrain macro-hills + medium rollers + fine bumps
            val w1 = sin((x * 0.026f) + phaseA) * amp * 0.55f
            val w2 = sin((x * 0.068f) + phaseB) * amp * 0.30f
            val w3 = cos((x * 0.155f) + phaseC) * (0.42f * bump)
            val w4 = if (map == MapEnvironment.INDIAN_BAZAAR || map == MapEnvironment.CITY_OUTSKIRTS) {
                // Periodic speed breakers / asphalt humps
                val mod = x % 75f
                if (mod in 36f..40f) sin(((mod - 36f) / 4f) * PI.toFloat()) * 0.28f else 0f
            } else 0f

            // Gradual gentle uphill bias on mountain/desert maps
            if (map == MapEnvironment.MOUNTAIN_ROADS && x > 80f) {
                slopeBias += 0.039f
            } else if (map == MapEnvironment.DESERT && x > 80f) {
                slopeBias += 0.009f * sin(x * 0.015f)
            }

            // Smooth blend out of launchpad over first 20m
            val blend = ((x - 24f) / 22f).coerceIn(0f, 1f)
            val rawY = (w1 + w2 + w3 + w4 + slopeBias) * blend

            // Clamp maximum step slope to guarantee physically traversable hills (no vertical walls)
            val maxStepDy = when (map) {
                MapEnvironment.MOUNTAIN_ROADS -> 0.68f
                MapEnvironment.CONSTRUCTION_AREA -> 0.62f
                else -> 0.54f
            }
            val dy = (rawY - lastY).coerceIn(-maxStepDy, maxStepDy)
            y = lastY + dy
            lastY = y

            // Surface zone assignment (mud/puddles/rocky patches)
            val zoneHash = abs(sin(floor((x / 22f).toDouble()) * 12.9898)).toFloat()
            surface = when {
                zoneHash < map.mudChance -> if (map.hasRain) SurfaceType.WET else SurfaceType.MUD
                map == MapEnvironment.CONSTRUCTION_AREA && zoneHash < 0.45f -> SurfaceType.ROCK
                else -> map.primarySurface
            }
        }

        points.add(TerrainPoint(x, y, surface))

        // Spawn props, coins, fuel, checkpoints deterministically ahead
        if (x >= nextPropX) {
            props.add(
                RoadsideProp(
                    x = x,
                    kind = rng.nextInt(5),
                    scale = 0.85f + rng.nextFloat() * 0.4f
                )
            )
            nextPropX = x + when (map) {
                MapEnvironment.FOREST -> 10f + rng.nextFloat() * 10f
                MapEnvironment.INDIAN_BAZAAR, MapEnvironment.CITY_OUTSKIRTS -> 14f + rng.nextFloat() * 12f
                MapEnvironment.DESERT -> 22f + rng.nextFloat() * 24f
                else -> 15f + rng.nextFloat() * 16f
            }
        }

        if (x >= nextFuelX) {
            collectibles.add(
                CollectibleItem(
                    id = nextCollectibleId++,
                    x = x,
                    yOffsetAboveGround = 1.05f,
                    isFuel = true
                )
            )
            nextFuelX = x + 175f + rng.nextFloat() * 35f
        }

        if (x >= nextCoinClusterX) {
            val count = 3 + rng.nextInt(3)
            for (i in 0 until count) {
                val cx = x + i * 3.4f
                val arc = sin((i.toFloat() / (count - 1).coerceAtLeast(1)) * PI.toFloat()) * 0.8f
                collectibles.add(
                    CollectibleItem(
                        id = nextCollectibleId++,
                        x = cx,
                        yOffsetAboveGround = 1.05f + arc,
                        isFuel = false
                    )
                )
            }
            nextCoinClusterX = x + 42f + rng.nextFloat() * 32f
        }

        if (x >= nextCheckpointX) {
            checkpoints.add(nextCheckpointX)
            nextCheckpointX += 250f
        }
    }

    fun getPointIndex(x: Float): Int {
        val idx = ((x - points[0].x) / STEP_X).toInt()
        return idx.coerceIn(0, points.size - 2)
    }

    fun heightAt(x: Float): Float {
        if (points.isEmpty()) return 0f
        val startX = points[0].x
        if (x <= startX) return points[0].y
        val fIdx = (x - startX) / STEP_X
        val i0 = fIdx.toInt().coerceIn(0, points.size - 2)
        val frac = (fIdx - i0).coerceIn(0f, 1f)
        val y0 = points[i0].y
        val y1 = points[i0 + 1].y
        return y0 + (y1 - y0) * frac
    }

    fun surfaceAt(x: Float): SurfaceType {
        if (points.isEmpty()) return map.primarySurface
        val idx = getPointIndex(x)
        return points[idx].surface
    }

    /** Returns outward unit normal (nx, ny) pointing UP from terrain (world +Y is UP) */
    fun normalAt(x: Float): Pair<Float, Float> {
        val dx = 0.6f
        val hL = heightAt(x - dx)
        val hR = heightAt(x + dx)
        val tx = dx * 2f
        val ty = hR - hL
        val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
        return (-ty / len) to (tx / len)
    }

    fun getVisiblePoints(minX: Float, maxX: Float): List<TerrainPoint> {
        if (points.isEmpty()) return emptyList()
        val startIdx = (((minX - points[0].x) / STEP_X).toInt() - 1).coerceIn(0, points.size - 1)
        val endIdx = (((maxX - points[0].x) / STEP_X).toInt() + 2).coerceIn(0, points.size - 1)
        return points.subList(startIdx, endIdx + 1)
    }
}
