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
    val surface: SurfaceType,
    val isMudTrap: Boolean = false,
    val mudDepth: Float = 0f
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
    val kind: Int, // 0..7 rich variant per map theme
    val scale: Float
)

data class BackgroundLandmark(
    val x: Float,
    val kind: Int, // 0..3 distant parallax landmarks
    val scale: Float
)

class TerrainGenerator(
    private val map: MapEnvironment,
    seed: Long = (System.nanoTime() xor 0x5EED241L)
) {
    companion object {
        const val STEP_X = 1.2f // Fine 1.2m terrain segments for smooth tyre-road contact
    }

    private val rng = Random(seed + map.ordinal * 7919L)
    private val points = ArrayList<TerrainPoint>(512)
    val collectibles = ArrayList<CollectibleItem>(256)
    val props = ArrayList<RoadsideProp>(320)
    val landmarks = ArrayList<BackgroundLandmark>(128)
    val checkpoints = ArrayList<Float>(64)

    private var nextCollectibleId = 1
    private var nextFuelX = 165f
    private var nextCoinClusterX = 48f
    private var nextCheckpointX = 250f
    private var nextPropX = -10f
    private var nextLandmarkX = -20f
    private var nextMudTrapX = 44f + rng.nextFloat() * 34f
    private var activeMudTrapEndX = -1f
    private var activeMudTrapDepth = 0f

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
        var isMudTrap = false
        var mudDepth = 0f

        if (x <= 24f) {
            // Smooth flat launchpad for tractor + trailer spawn
            y = 0f
            surface = if (map.primarySurface == SurfaceType.MUD) SurfaceType.DIRT else map.primarySurface
        } else {
            // Progressive difficulty scaling with unmistakable rolling crests & dips ("unchi-neechi road")
            val distFactor = ((x - 24f) / 750f).coerceIn(0f, 1.45f)
            val amp = map.hillAmplitude * (0.95f + 0.65f * distFactor)
            val bump = map.bumpiness * (0.78f + 0.60f * distFactor)

            // Random sticky kichad bog traps where tyres can get stuck & slip until POWER mode pulls out
            val trapEligible = map != MapEnvironment.DESERT
            if (trapEligible && x >= nextMudTrapX && x > activeMudTrapEndX) {
                val trapLen = 7.2f + rng.nextFloat() * 5.8f
                activeMudTrapEndX = x + trapLen
                activeMudTrapDepth = if (map.mudChance >= 0.25f) {
                    0.74f + rng.nextFloat() * 0.26f
                } else {
                    0.56f + rng.nextFloat() * 0.34f
                }
                val gap = when {
                    map.mudChance >= 0.35f -> 46f + rng.nextFloat() * 44f
                    map.mudChance >= 0.15f -> 60f + rng.nextFloat() * 52f
                    else -> 88f + rng.nextFloat() * 68f
                }
                nextMudTrapX = activeMudTrapEndX + gap
            }

            val inRandomTrap = x <= activeMudTrapEndX && x >= 38f
            val zoneHash = abs(sin(floor((x / 17f).toDouble()) * 12.9898)).toFloat()
            surface = when {
                inRandomTrap -> {
                    isMudTrap = true
                    mudDepth = activeMudTrapDepth
                    SurfaceType.MUD
                }
                zoneHash < map.mudChance -> {
                    mudDepth = if (map.hasRain) 0.35f else 0.45f
                    if (map.hasRain) SurfaceType.WET else SurfaceType.MUD
                }
                map == MapEnvironment.CONSTRUCTION_AREA && zoneHash < 0.45f -> SurfaceType.ROCK
                else -> map.primarySurface
            }

            // Long sweeping climbs + frequent medium unchi-neechi hills + rough country dips
            val w1 = sin((x * 0.032f) + phaseA) * amp * 0.68f
            val w2 = sin((x * 0.088f) + phaseB) * amp * 0.38f
            val w3 = cos((x * 0.195f) + phaseC) * (0.56f * bump)
            val w4 = if (map == MapEnvironment.INDIAN_BAZAAR || map == MapEnvironment.CITY_OUTSKIRTS) {
                val mod = x % 70f
                if (mod in 34f..38f) sin(((mod - 34f) / 4f) * PI.toFloat()) * 0.34f else 0f
            } else 0f
            // Natural sunken muddy dip so kichad sits visibly in low depressions of the road
            val mudDip = when {
                isMudTrap -> -0.46f * mudDepth
                surface == SurfaceType.MUD || surface == SurfaceType.WET -> -0.28f
                else -> 0f
            }

            // Gradual uphill bias on mountain/desert maps
            if (map == MapEnvironment.MOUNTAIN_ROADS && x > 70f) {
                slopeBias += 0.042f
            } else if (map == MapEnvironment.DESERT && x > 70f) {
                slopeBias += 0.012f * sin(x * 0.016f)
            }

            // Smooth blend out of flat launchpad over first 14m
            val blend = ((x - 24f) / 14f).coerceIn(0f, 1f)
            val rawY = (w1 + w2 + w3 + w4 + mudDip + slopeBias) * blend

            // Clamp maximum step slope to guarantee steep yet physically climbable unchi-neechi hills
            val maxStepDy = when (map) {
                MapEnvironment.MOUNTAIN_ROADS -> 0.88f
                MapEnvironment.CONSTRUCTION_AREA -> 0.82f
                else -> 0.74f
            }
            val dy = (rawY - lastY).coerceIn(-maxStepDy, maxStepDy)
            y = lastY + dy
            lastY = y
        }

        points.add(TerrainPoint(x, y, surface, isMudTrap, mudDepth))

        // Spawn roadside props, midground parallax landmarks, coins, fuel, checkpoints
        if (x >= nextPropX) {
            props.add(
                RoadsideProp(
                    x = x,
                    kind = rng.nextInt(8),
                    scale = 0.85f + rng.nextFloat() * 0.45f
                )
            )
            nextPropX = x + when (map) {
                MapEnvironment.FOREST -> 7.5f + rng.nextFloat() * 8f
                MapEnvironment.INDIAN_BAZAAR, MapEnvironment.CITY_OUTSKIRTS -> 10f + rng.nextFloat() * 9f
                MapEnvironment.DESERT -> 16f + rng.nextFloat() * 18f
                else -> 9.5f + rng.nextFloat() * 11f
            }
        }

        if (x >= nextLandmarkX) {
            landmarks.add(
                BackgroundLandmark(
                    x = x,
                    kind = rng.nextInt(4),
                    scale = 0.9f + rng.nextFloat() * 0.55f
                )
            )
            nextLandmarkX = x + 30f + rng.nextFloat() * 26f
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

    fun isMudTrapAt(x: Float): Boolean {
        if (points.isEmpty()) return false
        return points[getPointIndex(x)].isMudTrap
    }

    fun mudDepthAt(x: Float): Float {
        if (points.isEmpty()) return 0f
        return points[getPointIndex(x)].mudDepth
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
