package com.example.engine

import com.example.audio.SfxType
import com.example.audio.SoundEngine
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.MapEnvironment
import com.example.model.MissionDef
import com.example.model.SurfaceType
import com.example.model.TractorModel
import com.example.model.TractorUpgrades
import com.example.model.TrailerType
import com.example.model.UpgradeType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class RunPhase {
    RUNNING,
    PAUSED,
    UNLOADING,
    MISSION_COMPLETE,
    GAME_OVER
}

enum class GameOverReason(val title: String, val subtitle: String) {
    OUT_OF_FUEL("OUT OF DIESEL!", "Your tractor ran dry. Upgrade Diesel Tank or grab green cans!"),
    OVERTURNED("TRACTOR OVERTURNED!", "Careful on steep crests and heavy loads!"),
    TIME_UP("TIME RAN OUT!", "Upgrade Engine Power or Gear Torque for faster deliveries!")
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    val kind: Int, // 0=diesel smoke, 1=dust, 2=mud, 3=water splash, 4=sparkle
    val darkSoot: Float = 0.5f // 0..1 soot darkness for realistic diesel exhaust
)

data class CargoPiece(
    var lx: Float,
    var ly: Float,
    var lvx: Float = 0f,
    var lvy: Float = 0f,
    var angle: Float = 0f,
    var spilled: Boolean = false
)

data class RainDrop(
    var nx: Float,
    var ny: Float,
    var speed: Float
)

data class HudSnapshot(
    val speedKmh: Int = 0,
    val rpm: Int = 620,
    val gear: Int = 1,
    val distanceMeters: Int = 0,
    val coinsEarned: Int = 0,
    val fuel: Float = 100f,
    val maxFuel: Float = 100f,
    val cargoRemainingPct: Int = 100,
    val missionProgressPct: Float = 0f,
    val timeRemainingSec: Int? = null,
    val phase: RunPhase = RunPhase.RUNNING,
    val gameOverReason: GameOverReason? = null,
    val checkpointBannerTimer: Float = 0f,
    val lastCheckpointMeters: Int = 0,
    val driverLean: Float = 0f,
    val currentSurface: SurfaceType = SurfaceType.DIRT
)

class PhysicsWorld(
    val tractor: TractorModel,
    val driver: DriverCharacter,
    val trailer: TrailerType,
    val cargo: CargoType,
    val map: MapEnvironment,
    val upgrades: TractorUpgrades,
    val mission: MissionDef?,
    private val soundEngine: SoundEngine?
) {
    val terrain = TerrainGenerator(map)
    private val rng = Random(System.currentTimeMillis())

    @Volatile var throttleInput: Float = 0f
    @Volatile var brakeInput: Float = 0f
    @Volatile var handbrakeInput: Boolean = false
    @Volatile var powerModeInput: Boolean = false

    private var smoothedThrottle: Float = 0f
    private var smoothedBrake: Float = 0f

    fun setPowerMode(enabled: Boolean) {
        if (powerModeInput != enabled) {
            powerModeInput = enabled
            soundEngine?.playSfx(SfxType.CLICK)
        }
    }

    // Exact suspension & sprite-matched geometry
    private val rearAnchorLx = -tractor.wheelBase * 0.44f
    private val frontAnchorLx = tractor.wheelBase * 0.54f
    private val rearAnchorLy = -0.05f
    // Offset front mount vertically by radius delta so BOTH tyres touch y=0 at angle=0!
    private val frontAnchorLy = rearAnchorLy - (tractor.rearWheelRadius - tractor.frontWheelRadius)
    private val restLen = 0.26f

    // Exact exhaust & hitch coordinates derived from sprite pixels
    val exhaustLocalX: Float
    val exhaustLocalY: Float
    val hitchLocalX: Float
    val hitchLocalY: Float
    val tongueLocalX: Float = trailer.bedLength * 0.50f + 0.68f
    val tongueLocalY: Float

    var x: Float = 6.0f
    var y: Float = 0.05f + tractor.rearWheelRadius + restLen
    var vx: Float = 0f
    var vy: Float = 0f
    var angle: Float = 0f
    var angularVel: Float = 0f

    var rearWheelCompression: Float = restLen
    var frontWheelCompression: Float = restLen
    var rearWheelRot: Float = 0f
    var frontWheelRot: Float = 0f
    var rearWheelGrounded: Boolean = true
    var frontWheelGrounded: Boolean = true
    var engineRpm: Float = 620f
    var currentGear: Int = 1
    var driverLean: Float = 0f
    var currentSurface: SurfaceType = map.primarySurface

    val hasTrailer: Boolean = trailer != TrailerType.NONE
    var trX: Float = 0f
    var trY: Float = 0.05f + trailer.wheelRadius + 0.26f
    var trVx: Float = 0f
    var trVy: Float = 0f
    var trAngle: Float = 0f
    var trAngularVel: Float = 0f
    var trWheelCompression: Float = 0.26f
    var trWheelRot: Float = 0f
    var trDumpAngle: Float = 0f

    val cargoPieces = mutableListOf<CargoPiece>()
    var activeCargoMassKg: Float = 0f

    val particles = ArrayList<Particle>(220)
    val rainDrops = ArrayList<RainDrop>(75)

    var camX: Float = 6.0f
    var camY: Float = 1.4f
    var camShake: Float = 0f

    var phase: RunPhase = RunPhase.RUNNING
    var gameOverReason: GameOverReason? = null
    var maxDistanceMeters: Int = 0
    var runCoins: Int = 0
    var nextDistanceCoinMark: Int = 25
    var maxFuel: Float = 100f
    var fuel: Float = 100f
    var elapsedTimeSec: Float = 0f
    var unloadTimer: Float = 0f
    var overturnTimer: Float = 0f
    var lowSpeedFuelGraceTimer: Float = 0f
    var checkpointBannerTimer: Float = 0f
    var lastCheckpointMeters: Int = 0
    private var nextCheckpointIdx: Int = 0
    private var exhaustAccum: Float = 0f
    private var dustAccum: Float = 0f
    private var bumpCooldown: Float = 0f

    private val powerMult: Float
    private val torqueMult: Float
    private val gripMult: Float
    private val suspMult: Float
    private val brakeMult: Float
    private val maxSpeedMs: Float

    init {
        val engLvl = upgrades.levelOf(UpgradeType.ENGINE)
        val trqLvl = upgrades.levelOf(UpgradeType.TORQUE)
        val tyrLvl = upgrades.levelOf(UpgradeType.TYRES)
        val susLvl = upgrades.levelOf(UpgradeType.SUSPENSION)
        val fuelLvl = upgrades.levelOf(UpgradeType.FUEL)
        val brkLvl = upgrades.levelOf(UpgradeType.BRAKING)

        powerMult = tractor.basePower * (1f + (engLvl - 1) * 0.13f)
        torqueMult = tractor.baseTorque * (1f + (trqLvl - 1) * 0.14f)
        gripMult = tractor.baseGrip * (1f + (tyrLvl - 1) * 0.11f)
        suspMult = tractor.baseSuspension * (1f + (susLvl - 1) * 0.12f)
        maxFuel = tractor.baseFuelTank * (1f + (fuelLvl - 1) * 0.18f)
        fuel = maxFuel
        brakeMult = tractor.baseBraking * (1f + (brkLvl - 1) * 0.15f)
        maxSpeedMs = tractor.baseMaxSpeed * (1f + (engLvl - 1) * 0.08f)

        // Compute exact silencer mouth & drawbar hitch offsets from sprite proportions
        // Hitch vertical height (hNy) aligns cleanly with tractor rear tyre center (rNy)
        val coords = when (tractor) {
            TractorModel.EICHER_241 -> floatArrayOf(0.315f, 0.677f, 0.774f, 0.635f, 0.063f, 0.105f, 0.677f)
            TractorModel.MAHINDRA_575 -> floatArrayOf(0.243f, 0.701f, 0.753f, 0.670f, 0.115f, 0.018f, 0.701f)
            TractorModel.SONALIKA_750 -> floatArrayOf(0.242f, 0.690f, 0.757f, 0.644f, 0.040f, 0.022f, 0.690f)
        }
        val rNx = coords[0]
        val rNy = coords[1]
        val fNx = coords[2]
        val exNx = coords[3]
        val exNy = coords[4]
        val hNx = coords[5]
        val hNy = coords[6]

        val spriteW = (frontAnchorLx - rearAnchorLx) / (fNx - rNx)
        val spriteH = spriteW * (480f / 860f)
        val rearAxleRestY = rearAnchorLy - restLen
        exhaustLocalX = rearAnchorLx + (exNx - rNx) * spriteW
        exhaustLocalY = rearAxleRestY + (rNy - exNy) * spriteH
        hitchLocalX = rearAnchorLx + (hNx - rNx) * spriteW
        hitchLocalY = rearAxleRestY + (rNy - hNy) * spriteH

        val restTractorY = 0.05f + tractor.rearWheelRadius + restLen
        val restTrailerY = 0.05f + trailer.wheelRadius + restLen
        tongueLocalY = ((restTractorY + hitchLocalY) - restTrailerY).coerceIn(-0.42f, 0.12f)

        if (hasTrailer) {
            trX = x + hitchLocalX - tongueLocalX
            trY = restTrailerY
            spawnCargoPieces()
        }

        if (map.hasRain) {
            repeat(75) {
                rainDrops.add(RainDrop(rng.nextFloat(), rng.nextFloat(), 1.2f + rng.nextFloat() * 0.7f))
            }
        }

        soundEngine?.activeTractor = tractor
        soundEngine?.currentMap = map
        soundEngine?.playSfx(SfxType.STARTER)
    }

    private fun spawnCargoPieces() {
        cargoPieces.clear()
        if (!hasTrailer || cargo == CargoType.NONE) {
            activeCargoMassKg = 0f
            return
        }
        val count = trailer.capacityPieces
        val usableLen = trailer.bedLength * 0.78f
        val cols = (count + 1) / 2
        for (i in 0 until count) {
            val col = i % cols
            val row = i / cols
            val lx = -usableLen * 0.5f + (col + 0.5f) * (usableLen / cols)
            val ly = 0.34f + row * 0.28f
            cargoPieces.add(
                CargoPiece(
                    lx = lx,
                    ly = ly,
                    angle = (rng.nextFloat() - 0.5f) * 0.14f
                )
            )
        }
        activeCargoMassKg = cargo.totalMassKg
    }

    fun step(frameDt: Float) {
        if (phase == RunPhase.PAUSED || phase == RunPhase.MISSION_COMPLETE || phase == RunPhase.GAME_OVER) {
            return
        }

        val dt = frameDt.coerceIn(0.001f, 0.045f)
        terrain.ensureGeneratedUpTo(x + 120f)

        if (phase == RunPhase.UNLOADING) {
            unloadTimer += dt
            trDumpAngle = min(0.58f, unloadTimer * 0.45f)
            vx *= 0.88f
            vy = 0f
            cargoPieces.forEach { piece ->
                if (!piece.spilled) {
                    piece.lx -= dt * 1.9f
                    piece.ly += dt * 0.2f
                    if (piece.lx < -trailer.bedLength * 0.58f) {
                        piece.spilled = true
                        spawnParticle(trX - trailer.bedLength * 0.4f, trY + 0.3f, -1.5f, 2.2f, 0.22f, 0.7f, 4)
                    }
                }
            }
            if (unloadTimer >= 1.65f) {
                phase = RunPhase.MISSION_COMPLETE
                soundEngine?.playSfx(SfxType.MISSION_WIN)
            }
            updateParticles(dt)
            updateCamera(dt)
            return
        }

        elapsedTimeSec += dt
        if (bumpCooldown > 0f) bumpCooldown -= dt
        if (checkpointBannerTimer > 0f) checkpointBannerTimer = max(0f, checkpointBannerTimer - dt)

        val substeps = 6
        val subDt = dt / substeps
        repeat(substeps) {
            stepPhysicsSubstep(subDt)
        }

        updateParticles(dt)
        updateRain(dt)
        updateCamera(dt)
        checkCollectiblesAndCheckpoints()
        updateFuelAndMissionStatus(dt)
        syncAudioTelemetry()
    }

    private fun stepPhysicsSubstep(dt: Float) {
        val gravity = 12.2f
        val cosA = cos(angle)
        val sinA = sin(angle)

        val massT = tractor.massKg
        val invMassT = 1f / massT
        val inertiaT = massT * 1.90f
        val invInertiaT = 1f / inertiaT

        var forceX = 0f
        var forceY = -massT * gravity
        var torque = 0f

        val maxLen = 0.48f
        val minLen = 0.06f
        // Well-damped agricultural suspension keeps both tyres planted on crests and bumps
        val rearSpringK = massT * 38.5f * suspMult
        val rearDamperC = massT * 6.4f * sqrt(suspMult)
        val frontSpringK = massT * 31.5f * suspMult
        val frontDamperC = massT * 5.4f * sqrt(suspMult)

        val rawThrottle = if (fuel > 0f) throttleInput else 0f
        smoothedThrottle += (rawThrottle - smoothedThrottle) * min(1f, dt * 4.5f)
        smoothedBrake += (brakeInput - smoothedBrake) * min(1f, dt * 7.5f)
        val effThrottle = smoothedThrottle
        val effBrake = smoothedBrake

        val rearResult = solveWheel(
            anchorLx = rearAnchorLx,
            anchorLy = rearAnchorLy,
            wheelRadius = tractor.rearWheelRadius,
            cosA = cosA,
            sinA = sinA,
            bodyX = x,
            bodyY = y,
            bodyVx = vx,
            bodyVy = vy,
            bodyAngVel = angularVel,
            restLen = restLen,
            minLen = minLen,
            maxLen = maxLen,
            springK = rearSpringK,
            damperC = rearDamperC,
            prevCompression = rearWheelCompression,
            dt = dt
        )
        rearWheelCompression = rearResult.compression
        rearWheelGrounded = rearResult.grounded

        val frontResult = solveWheel(
            anchorLx = frontAnchorLx,
            anchorLy = frontAnchorLy,
            wheelRadius = tractor.frontWheelRadius,
            cosA = cosA,
            sinA = sinA,
            bodyX = x,
            bodyY = y,
            bodyVx = vx,
            bodyVy = vy,
            bodyAngVel = angularVel,
            restLen = restLen,
            minLen = minLen,
            maxLen = maxLen,
            springK = frontSpringK,
            damperC = frontDamperC,
            prevCompression = frontWheelCompression,
            dt = dt
        )
        frontWheelCompression = frontResult.compression
        frontWheelGrounded = frontResult.grounded

        if (rearResult.grounded) {
            forceX += rearResult.fx
            forceY += rearResult.fy
            torque += (rearResult.rx * rearResult.fy - rearResult.ry * rearResult.fx)
            currentSurface = rearResult.surface
        }
        if (frontResult.grounded) {
            forceX += frontResult.fx
            forceY += frontResult.fy
            torque += (frontResult.rx * frontResult.fy - frontResult.ry * frontResult.fx)
        }

        val forwardSpeed = vx * cosA + vy * sinA
        currentGear = when {
            forwardSpeed < -0.35f -> 0
            forwardSpeed < 2.8f -> 1
            forwardSpeed < 5.6f -> 2
            forwardSpeed < 8.4f -> 3
            else -> 4
        }
        val idleRpm = if (tractor == TractorModel.EICHER_241) 580f else 680f
        val loadDragFactor = (activeCargoMassKg / 2400f).coerceIn(0f, 1f)
        val targetRpm = if (fuel <= 0f) 0f else {
            val gearRatio = when (currentGear) {
                0 -> 240f
                1 -> 440f
                2 -> 285f
                3 -> 205f
                else -> 165f
            }
            val powerRpmBoost = if (powerModeInput) 150f * effThrottle else 0f
            val speedRpm = abs(forwardSpeed) * gearRatio
            (idleRpm + effThrottle * 540f + powerRpmBoost + speedRpm * 0.66f - loadDragFactor * 120f * effThrottle)
                .coerceIn(idleRpm - 30f, 2350f)
        }
        // Heavy diesel flywheel inertia prevents sudden rev spikes the moment throttle is tapped
        val rpmRate = if (targetRpm > engineRpm) (2.6f - loadDragFactor * 0.7f) else 3.4f
        engineRpm += (targetRpm - engineRpm) * min(1f, dt * rpmRate)

        if (rearResult.grounded || frontResult.grounded) {
            val surf = if (rearResult.grounded) rearResult.surface else frontResult.surface
            val powerGripBonus = if (powerModeInput) 1.24f else 1.0f
            val surfaceGrip = (surf.baseGrip * gripMult * powerGripBonus).coerceIn(0.45f, 1.55f)
            val tx = if (rearResult.grounded) rearResult.tx else frontResult.tx
            val ty = if (rearResult.grounded) rearResult.ty else frontResult.ty
            val tangentSpeed = vx * tx + vy * ty

            var driveForce = 0f
            if (effThrottle > 0.01f && !handbrakeInput) {
                val powerTorqueBonus = if (powerModeInput) 1.42f else 1.0f
                val effectiveTopSpeed = maxSpeedMs * (if (powerModeInput) 1.18f else 1.0f)
                val speedRatio = (tangentSpeed / effectiveTopSpeed).coerceIn(0f, 1.08f)
                // Low-gear diesel crawler boost for climbing steep hills smoothly with heavy trailers
                val lowGearMult = if (currentGear <= 1) 1.18f else 1.0f
                val torqueCurve = (1.30f - 0.88f * speedRatio).coerceAtLeast(0.06f)
                val rawDrive = massT * 8.6f * powerMult * torqueMult * powerTorqueBonus * lowGearMult * torqueCurve * effThrottle
                val maxTraction = massT * gravity * surfaceGrip * 1.24f
                driveForce += min(rawDrive, maxTraction)

                // Subtle realistic torque squat only proportional to drive load
                torque += driveForce * 0.045f
            }

            if (effBrake > 0.01f) {
                if (tangentSpeed > 0.45f) {
                    val brakeForce = massT * 13.0f * brakeMult * effBrake * surfaceGrip
                    driveForce -= brakeForce
                    torque -= brakeForce * 0.065f
                } else {
                    val revLimit = -4.2f
                    if (tangentSpeed > revLimit) {
                        val revForce = massT * 5.4f * torqueMult * effBrake * surfaceGrip
                        driveForce -= revForce
                    }
                }
            }

            if (handbrakeInput) {
                val hbForce = (-tangentSpeed * massT * 12f).coerceIn(-massT * 15f, massT * 15f)
                driveForce += hbForce * surfaceGrip
            }

            // Natural diesel engine-braking + rolling resistance when coasting
            val coastDrag = if (effThrottle < 0.05f && effBrake < 0.05f) 0.62f else 0.12f
            val rollingResist = -tangentSpeed * massT * (surf.rollingDrag * 22f + coastDrag)
            val totalTangential = driveForce + rollingResist

            forceX += tx * totalTangential
            forceY += ty * totalTangential

            val wheelSpinSlip = if (effThrottle > 0.25f && surf.baseGrip < 0.72f) 1.45f else 1.0f
            rearWheelRot += ((tangentSpeed * wheelSpinSlip) / tractor.rearWheelRadius) * dt
            frontWheelRot += (tangentSpeed / tractor.frontWheelRadius) * dt

            if (abs(tangentSpeed) > 1.0f || (effThrottle > 0.3f && surf == SurfaceType.MUD)) {
                dustAccum += dt * (3f + abs(tangentSpeed) * 1.1f)
                if (dustAccum >= 1f) {
                    dustAccum = 0f
                    val pKind = when (surf) {
                        SurfaceType.MUD -> 2
                        SurfaceType.WET -> 3
                        else -> 1
                    }
                    val wx = x + rearAnchorLx * cosA
                    val wy = terrain.heightAt(wx) + 0.06f
                    spawnParticle(
                        px = wx,
                        py = wy,
                        pvx = -vx * 0.35f - (0.6f + rng.nextFloat() * 1.2f),
                        pvy = 0.5f + rng.nextFloat() * 1.2f,
                        size = 0.14f + rng.nextFloat() * 0.14f,
                        life = 0.45f + rng.nextFloat() * 0.25f,
                        kind = pKind
                    )
                }
            }
        } else {
            // Subtle airborne pitch control
            torque += (effThrottle * 0.35f - effBrake * 0.35f) * massT * 1.6f
            rearWheelRot += (effThrottle * 6f) * dt
            frontWheelRot += (vx / tractor.frontWheelRadius) * dt * 0.5f
        }

        // Front axle ballast stability & angular damping keeps tractor planted realistically
        angularVel *= (1f - dt * 3.8f)

        vx += (forceX * invMassT) * dt
        vy += (forceY * invMassT) * dt
        angularVel += (torque * invInertiaT) * dt

        val bellyH = terrain.heightAt(x)
        if (y - 0.38f < bellyH) {
            val pen = bellyH - (y - 0.38f)
            y += pen * 0.7f
            if (vy < 0f) {
                if (vy < -3.2f && bumpCooldown <= 0f) {
                    soundEngine?.playSfx(SfxType.BUMP)
                    camShake = min(0.25f, abs(vy) * 0.03f)
                    bumpCooldown = 0.28f
                }
                vy = -vy * 0.12f
            }
            vx *= 0.985f
        }

        if (x < -14f && vx < 0f) {
            x = -14f
            vx = 0f
        }

        if (hasTrailer) {
            stepTrailerAndHitch(dt, gravity, massT, invMassT, invInertiaT)
        }

        x += vx * dt
        y += vy * dt
        angle = normalizeAngle(angle + angularVel * dt)

        // Sync wheel suspension travel with post-integration body pose so tyres stay planted cleanly on road
        rearWheelCompression = clampWheelCompression(x, y, angle, rearAnchorLx, rearAnchorLy, tractor.rearWheelRadius, minLen, maxLen)
        frontWheelCompression = clampWheelCompression(x, y, angle, frontAnchorLx, frontAnchorLy, tractor.frontWheelRadius, minLen, maxLen)

        val targetLean = (-effThrottle * 0.16f + effBrake * 0.18f - sinA * 0.22f).coerceIn(-0.32f, 0.32f)
        driverLean += (targetLean - driverLean) * (dt * 6f)

        // Realistic Diesel Exhaust Plumes straight from the exact Silencer Tip!
        val exhaustRate = 7.5f + effThrottle * 22f + (activeCargoMassKg / 2400f) * effThrottle * 8f
        exhaustAccum += dt * exhaustRate
        if (exhaustAccum >= 1f && fuel > 0f) {
            exhaustAccum = 0f
            val exWorldX = x + exhaustLocalX * cosA - exhaustLocalY * sinA
            val exWorldY = y + exhaustLocalX * sinA + exhaustLocalY * cosA
            val loadSoot = (0.28f + effThrottle * 0.52f + (activeCargoMassKg / 2500f) * 0.20f).coerceIn(0.2f, 0.95f)
            spawnParticle(
                px = exWorldX,
                py = exWorldY,
                pvx = vx * 0.25f - 0.55f + (rng.nextFloat() - 0.5f) * 0.45f,
                pvy = 1.9f + effThrottle * 2.1f + rng.nextFloat() * 0.5f,
                size = 0.08f + effThrottle * 0.09f,
                life = 0.72f + rng.nextFloat() * 0.42f,
                kind = 0,
                darkSoot = loadSoot
            )
        }
    }

    private fun stepTrailerAndHitch(
        dt: Float,
        gravity: Float,
        massT: Float,
        invMassT: Float,
        invInertiaT: Float
    ) {
        val totalMassTr = trailer.emptyMassKg + activeCargoMassKg
        val invMassTr = 1f / totalMassTr
        val inertiaTr = totalMassTr * 1.9f
        val invInertiaTr = 1f / inertiaTr

        val cosTr = cos(trAngle)
        val sinTr = sin(trAngle)

        var trFx = 0f
        var trFy = -totalMassTr * gravity
        var trTorque = 0f

        val trWheelRes = solveWheel(
            anchorLx = -trailer.bedLength * 0.08f,
            anchorLy = -0.05f,
            wheelRadius = trailer.wheelRadius,
            cosA = cosTr,
            sinA = sinTr,
            bodyX = trX,
            bodyY = trY,
            bodyVx = trVx,
            bodyVy = trVy,
            bodyAngVel = trAngularVel,
            restLen = 0.26f,
            minLen = 0.08f,
            maxLen = 0.48f,
            springK = totalMassTr * 62f * suspMult,
            damperC = totalMassTr * 7.2f * sqrt(suspMult),
            prevCompression = trWheelCompression,
            dt = dt
        )
        trWheelCompression = trWheelRes.compression

        if (trWheelRes.grounded) {
            trFx += trWheelRes.fx
            trFy += trWheelRes.fy
            trTorque += (trWheelRes.rx * trWheelRes.fy - trWheelRes.ry * trWheelRes.fx)
            val tSpeed = trVx * trWheelRes.tx + trVy * trWheelRes.ty
            val trailerBrake = if (handbrakeInput) 10.5f else if (brakeInput > 0.05f && tSpeed > 0.4f) 3.2f * brakeInput else 0f
            val drag = -tSpeed * totalMassTr * (trWheelRes.surface.rollingDrag * 16f + 0.16f + trailerBrake)
            trFx += trWheelRes.tx * drag
            trFy += trWheelRes.ty * drag
            trWheelRot += (tSpeed / trailer.wheelRadius) * dt
        } else {
            trWheelRot += (trVx / trailer.wheelRadius) * dt * 0.5f
        }

        trVx += (trFx * invMassTr) * dt
        trVy += (trFy * invMassTr) * dt
        trAngularVel = (trAngularVel + (trTorque * invInertiaTr) * dt) * (1f - dt * 3.4f)

        val cosT = cos(angle)
        val sinT = sin(angle)

        val rTx = hitchLocalX * cosT - hitchLocalY * sinT
        val rTy = hitchLocalX * sinT + hitchLocalY * cosT
        val hitchWx = x + rTx
        val hitchWy = y + rTy

        val tongueLx = tongueLocalX
        val tongueLy = tongueLocalY
        val rTrX = tongueLx * cosTr - tongueLy * sinTr
        val rTrY = tongueLx * sinTr + tongueLy * cosTr
        val tongueWx = trX + rTrX
        val tongueWy = trY + rTrY

        val errX = hitchWx - tongueWx
        val errY = hitchWy - tongueWy
        val baumgarte = 20.0f
        val effMassX = invMassT + invMassTr + (rTy * rTy) * invInertiaT + (rTrY * rTrY) * invInertiaTr
        val effMassY = invMassT + invMassTr + (rTx * rTx) * invInertiaT + (rTrX * rTrX) * invInertiaTr

        // 2-iteration sequential impulse solver eliminates hitch slack & bounce on rough hills
        repeat(2) {
            val vHitchX = vx - angularVel * rTy
            val vHitchY = vy + angularVel * rTx
            val vTongueX = trVx - trAngularVel * rTrY
            val vTongueY = trVy + trAngularVel * rTrX

            val relVx = (vTongueX - vHitchX) - errX * baumgarte
            val relVy = (vTongueY - vHitchY) - errY * baumgarte

            val impX = -relVx / effMassX.coerceAtLeast(0.0001f) * 0.65f
            val impY = -relVy / effMassY.coerceAtLeast(0.0001f) * 0.65f

            vx -= impX * invMassT
            vy -= impY * invMassT
            angularVel -= (rTx * impY - rTy * impX) * invInertiaT

            trVx += impX * invMassTr
            trVy += impY * invMassTr
            trAngularVel += (rTrX * impY - rTrY * impX) * invInertiaTr
        }

        val relAngle = normalizeAngle(trAngle - angle)
        val maxRelAngle = 0.58f
        if (abs(relAngle) > maxRelAngle) {
            val excess = relAngle - (if (relAngle > 0f) maxRelAngle else -maxRelAngle)
            trAngularVel -= excess * 18f
            angularVel += excess * 5f
        }

        trX += trVx * dt
        trY += trVy * dt
        trAngle = normalizeAngle(trAngle + trAngularVel * dt)

        val trGround = terrain.heightAt(trX)
        if (trY - 0.25f < trGround) {
            trY = trGround + 0.25f
            if (trVy < 0f) trVy = 0f
        }
        trWheelCompression = clampWheelCompression(trX, trY, trAngle, -trailer.bedLength * 0.08f, -0.05f, trailer.wheelRadius, 0.08f, 0.48f)

        stepCargoPieces(dt, sinTr, cosTr, gravity)
    }

    private fun stepCargoPieces(dt: Float, sinTr: Float, cosTr: Float, gravity: Float) {
        if (cargoPieces.isEmpty()) return
        val halfLen = trailer.bedLength * 0.42f
        val bedTop = trailer.bedHeight * 1.45f
        val gravLocalX = -gravity * sinTr
        val gravLocalY = -gravity * cosTr

        var activeCount = 0
        for (p in cargoPieces) {
            if (p.spilled) continue
            p.lvx += (gravLocalX - trVx * 0.20f) * dt
            p.lvy += (gravLocalY - trVy * 0.40f) * dt
            p.lx += p.lvx * dt
            p.ly += p.lvy * dt

            if (p.ly < 0.30f) {
                p.ly = 0.30f
                if (p.lvy < 0f) p.lvy = -p.lvy * 0.15f
                p.lvx *= 0.82f
            }

            val extremeTilt = abs(trAngle) > 1.18f
            if (p.lx > halfLen) {
                if (extremeTilt && p.ly > trailer.bedHeight * 0.6f) {
                    p.spilled = true
                } else {
                    p.lx = halfLen
                    p.lvx = -abs(p.lvx) * 0.22f
                }
            } else if (p.lx < -halfLen) {
                if (extremeTilt && p.ly > trailer.bedHeight * 0.6f) {
                    p.spilled = true
                } else {
                    p.lx = -halfLen
                    p.lvx = abs(p.lvx) * 0.22f
                }
            }

            if (p.ly > bedTop && extremeTilt) {
                p.spilled = true
            }

            if (!p.spilled) activeCount++
        }

        activeCargoMassKg = if (cargoPieces.isNotEmpty()) {
            cargo.totalMassKg * (activeCount.toFloat() / cargoPieces.size.toFloat())
        } else 0f
    }

    private data class WheelSolveResult(
        val grounded: Boolean,
        val compression: Float,
        val fx: Float,
        val fy: Float,
        val rx: Float,
        val ry: Float,
        val tx: Float,
        val ty: Float,
        val surface: SurfaceType
    )

    private fun solveWheel(
        anchorLx: Float,
        anchorLy: Float,
        wheelRadius: Float,
        cosA: Float,
        sinA: Float,
        bodyX: Float,
        bodyY: Float,
        bodyVx: Float,
        bodyVy: Float,
        bodyAngVel: Float,
        restLen: Float,
        minLen: Float,
        maxLen: Float,
        springK: Float,
        damperC: Float,
        prevCompression: Float,
        dt: Float
    ): WheelSolveResult {
        val rx = anchorLx * cosA - anchorLy * sinA
        val ry = anchorLx * sinA + anchorLy * cosA
        val anchorWx = bodyX + rx
        val anchorWy = bodyY + ry

        val downX = sinA
        val downY = -cosA

        val maxWheelX = anchorWx + downX * maxLen
        val maxWheelY = anchorWy + downY * maxLen

        val surf = terrain.surfaceAt(maxWheelX)
        val (nx, ny) = terrain.normalAt(maxWheelX)
        val tx = ny
        val ty = -nx

        // Curved circular tyre footprint keeps tyre circumference smoothly rolling over bumps & slopes
        val requiredCenterY = sampleRequiredWheelCenterY(maxWheelX, wheelRadius, nx)
        val penetration = requiredCenterY - maxWheelY

        if (penetration <= 0f) {
            val newLen = (prevCompression + (maxLen - prevCompression) * (dt * 12f)).coerceIn(minLen, maxLen)
            return WheelSolveResult(false, newLen, 0f, 0f, rx, ry, tx, ty, surf)
        }

        val currentLen = (maxLen - penetration).coerceIn(minLen, maxLen)
        val compressionAmt = (maxLen - currentLen).coerceAtLeast(0f)

        val anchorVelX = bodyVx - bodyAngVel * ry
        val anchorVelY = bodyVy + bodyAngVel * rx
        val normalVel = anchorVelX * nx + anchorVelY * ny

        var normalForce = compressionAmt * springK - normalVel * damperC
        if (currentLen <= minLen + 0.025f && normalVel < 0f) {
            normalForce += -normalVel * damperC * 1.9f
        }
        normalForce = max(0f, normalForce)

        return WheelSolveResult(
            grounded = true,
            compression = currentLen,
            fx = nx * normalForce,
            fy = ny * normalForce,
            rx = rx,
            ry = ry,
            tx = tx,
            ty = ty,
            surface = surf
        )
    }

    private fun sampleRequiredWheelCenterY(wx: Float, r: Float, nx: Float): Float {
        val slopeDx = (-nx * r).coerceIn(-r * 0.62f, r * 0.62f)
        val halfSpan = r * 0.42f
        val d0 = terrain.heightAt(wx) + r
        val dL = terrain.heightAt(wx - halfSpan) + sqrt(max(0f, r * r - halfSpan * halfSpan))
        val dR = terrain.heightAt(wx + halfSpan) + sqrt(max(0f, r * r - halfSpan * halfSpan))
        val dS = terrain.heightAt(wx + slopeDx) + sqrt(max(0f, r * r - slopeDx * slopeDx))
        return max(max(d0, dL), max(dR, dS))
    }

    private fun checkCollectiblesAndCheckpoints() {
        for (item in terrain.collectibles) {
            if (item.collected) continue
            if (item.x < x - 8f || item.x > x + 8f) continue
            val itemY = terrain.heightAt(item.x) + item.yOffsetAboveGround
            val dx = item.x - x
            val dy = itemY - y
            if (dx * dx + dy * dy < 3.6f) {
                item.collected = true
                if (item.isFuel) {
                    fuel = min(maxFuel, fuel + maxFuel * 0.36f)
                    soundEngine?.playSfx(SfxType.FUEL)
                    spawnParticle(x, y + 0.6f, 0f, 1.8f, 0.22f, 0.6f, 4)
                } else {
                    val coinValue = (10 * cargo.rewardMultiplier).toInt()
                    runCoins += coinValue
                    soundEngine?.playSfx(SfxType.COIN)
                    spawnParticle(item.x, itemY, 0f, 1.6f, 0.16f, 0.45f, 4)
                }
            }
        }

        val distM = max(0, x.toInt())
        if (distM > maxDistanceMeters) {
            maxDistanceMeters = distM
            while (maxDistanceMeters >= nextDistanceCoinMark) {
                runCoins += (12 * cargo.rewardMultiplier).toInt()
                nextDistanceCoinMark += 25
            }
        }

        if (nextCheckpointIdx < terrain.checkpoints.size) {
            val cpX = terrain.checkpoints[nextCheckpointIdx]
            if (x >= cpX) {
                nextCheckpointIdx++
                lastCheckpointMeters = cpX.toInt()
                checkpointBannerTimer = 2.6f
                runCoins += 60
                fuel = min(maxFuel, fuel + maxFuel * 0.25f)
                soundEngine?.playSfx(SfxType.CHECKPOINT)
            }
        }
    }

    private fun updateFuelAndMissionStatus(dt: Float) {
        if (fuel > 0f) {
            val loadFactor = 1f + (activeCargoMassKg / 2600f) * 0.35f
            val powerBurnMult = if (powerModeInput && smoothedThrottle > 0.05f) 1.38f else 1.0f
            val burn = (0.42f + smoothedThrottle * 1.40f) * tractor.fuelBurnRate * loadFactor * powerBurnMult * dt
            fuel = max(0f, fuel - burn)
        }

        if (mission != null) {
            if (maxDistanceMeters >= mission.targetDistanceMeters) {
                if (hasTrailer && cargo != CargoType.NONE) {
                    phase = RunPhase.UNLOADING
                    unloadTimer = 0f
                } else {
                    phase = RunPhase.MISSION_COMPLETE
                    soundEngine?.playSfx(SfxType.MISSION_WIN)
                }
                return
            }
            if (mission.timeLimitSeconds != null && elapsedTimeSec >= mission.timeLimitSeconds) {
                triggerGameOver(GameOverReason.TIME_UP)
                return
            }
        }

        if (fuel <= 0f) {
            if (abs(vx) < 0.45f) {
                lowSpeedFuelGraceTimer += dt
                if (lowSpeedFuelGraceTimer >= 1.5f) {
                    triggerGameOver(GameOverReason.OUT_OF_FUEL)
                    return
                }
            } else {
                lowSpeedFuelGraceTimer = 0f
            }
        }

        if (abs(angle) > 1.95f) {
            overturnTimer += dt
            if (overturnTimer >= 0.75f) {
                triggerGameOver(GameOverReason.OVERTURNED)
                return
            }
        } else {
            overturnTimer = max(0f, overturnTimer - dt * 2f)
        }
    }

    private fun triggerGameOver(reason: GameOverReason) {
        phase = RunPhase.GAME_OVER
        gameOverReason = reason
        soundEngine?.playSfx(SfxType.CRASH)
    }

    private fun spawnParticle(
        px: Float,
        py: Float,
        pvx: Float,
        pvy: Float,
        size: Float,
        life: Float,
        kind: Int,
        darkSoot: Float = 0.5f
    ) {
        if (particles.size >= 200) {
            particles.removeAt(0)
        }
        particles.add(Particle(px, py, pvx, pvy, size, life, life, kind, darkSoot))
    }

    private fun updateParticles(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles.removeAt(i)
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                if (p.kind == 0) {
                    // Diesel smoke billows, slows vertically, and drifts backward with breeze
                    p.size += dt * 0.34f
                    p.vx += (-0.8f - p.vx) * dt * 1.4f
                    p.vy *= 0.94f
                } else if (p.kind == 2 || p.kind == 3) {
                    p.vy -= 9.5f * dt
                }
            }
            i--
        }
    }

    private fun updateRain(dt: Float) {
        if (!map.hasRain) return
        for (drop in rainDrops) {
            drop.nx -= dt * 0.35f * drop.speed
            drop.ny += dt * 1.35f * drop.speed
            if (drop.ny > 1.05f) {
                drop.ny = -0.05f
                drop.nx = rng.nextFloat() * 1.2f
            }
            if (drop.nx < -0.05f) {
                drop.nx += 1.15f
            }
        }
    }

    private fun updateCamera(dt: Float) {
        val leadOffset = (if (hasTrailer) 1.25f else 2.5f) + (vx * 0.22f).coerceIn(-1.2f, 3.2f)
        val targetCamX = x + leadOffset
        val targetCamY = y + 1.00f
        camX += (targetCamX - camX) * (dt * 6.5f)
        camY += (targetCamY - camY) * (dt * 4.5f)
        camShake = max(0f, camShake - dt * 1.4f)
    }

    private fun clampWheelCompression(
        bodyX: Float,
        bodyY: Float,
        bodyAngle: Float,
        anchorLx: Float,
        anchorLy: Float,
        wheelRadius: Float,
        minLen: Float,
        maxLen: Float
    ): Float {
        val cosA = cos(bodyAngle)
        val sinA = sin(bodyAngle)
        val rx = anchorLx * cosA - anchorLy * sinA
        val ry = anchorLx * sinA + anchorLy * cosA
        val anchorWx = bodyX + rx
        val anchorWy = bodyY + ry
        val maxWheelX = anchorWx + sinA * maxLen
        val maxWheelY = anchorWy - cosA * maxLen
        val (nx, _) = terrain.normalAt(maxWheelX)
        val requiredCenterY = sampleRequiredWheelCenterY(maxWheelX, wheelRadius, nx)
        val pen = requiredCenterY - maxWheelY
        return if (pen <= 0f) maxLen else (maxLen - pen).coerceIn(minLen, maxLen)
    }

    private fun syncAudioTelemetry() {
        soundEngine?.let { audio ->
            audio.engineRpm = engineRpm
            audio.throttle = smoothedThrottle
            audio.vehicleSpeed = vx
            audio.currentSurface = currentSurface
            audio.hornActive = false
            val slopeStrain = (sin(angle) * 1.4f).coerceIn(0f, 0.7f)
            val cargoStrain = (activeCargoMassKg / 2400f) * 0.35f
            val powerStrain = if (powerModeInput && smoothedThrottle > 0.1f) 0.18f else 0f
            audio.loadStrain = (slopeStrain + cargoStrain + powerStrain).coerceIn(0f, 1f)
        }
    }

    fun snapshotHud(): HudSnapshot {
        val speedKmh = abs(vx * 3.6f).toInt()
        val cargoPct = if (cargoPieces.isEmpty()) 100 else {
            val alive = cargoPieces.count { !it.spilled }
            ((alive * 100f) / cargoPieces.size).toInt()
        }
        val missionPct = if (mission != null) {
            (maxDistanceMeters.toFloat() / mission.targetDistanceMeters.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val timeRem = mission?.timeLimitSeconds?.let { limit ->
            max(0, limit - elapsedTimeSec.toInt())
        }
        return HudSnapshot(
            speedKmh = speedKmh,
            rpm = engineRpm.toInt(),
            gear = currentGear,
            distanceMeters = maxDistanceMeters,
            coinsEarned = runCoins,
            fuel = fuel,
            maxFuel = maxFuel,
            cargoRemainingPct = cargoPct,
            missionProgressPct = missionPct,
            timeRemainingSec = timeRem,
            phase = phase,
            gameOverReason = gameOverReason,
            checkpointBannerTimer = checkpointBannerTimer,
            lastCheckpointMeters = lastCheckpointMeters,
            driverLean = driverLean,
            currentSurface = currentSurface
        )
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a
        while (r > PI) r -= (2 * PI).toFloat()
        while (r < -PI) r += (2 * PI).toFloat()
        return r
    }
}
