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
    val currentSurface: SurfaceType = SurfaceType.DIRT,
    val isStuckInMud: Boolean = false,
    val mudBogLevel: Float = 0f,
    val tyreSlipRatio: Float = 0f,
    val tiltDanger: Float = 0f
)

class PhysicsWorld(
    val tractor: TractorModel,
    val driver: DriverCharacter,
    val trailer: TrailerType,
    val cargo: CargoType,
    val map: MapEnvironment,
    val upgrades: TractorUpgrades,
    val mission: MissionDef?,
    private val soundEngine: SoundEngine?,
    terrainSeed: Long = (System.nanoTime() xor 0x5EED241L)
) {
    val terrain = TerrainGenerator(map, terrainSeed)
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
    var rearWheelOmega: Float = 0f
    var frontWheelOmega: Float = 0f
    var rearWheelGrounded: Boolean = true
    var frontWheelGrounded: Boolean = true
    var engineRpm: Float = 620f
    var currentGear: Int = 1
    var driverLean: Float = 0f
    var currentSurface: SurfaceType = map.primarySurface
    var isStuckInMud: Boolean = false
    var mudBogLevel: Float = 0f
    var tyreSlipRatio: Float = 0f
    var chassisMudCoat: Float = 0f
    var tiltDanger: Float = 0f

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

        // Compute exact silencer mouth & raised drawbar hitch offsets from sprite proportions
        // Hitch vertical height sits cleanly above rear axle hub so drawbar connects high & straight
        val coords = when (tractor) {
            TractorModel.EICHER_241 -> floatArrayOf(0.315f, 0.677f, 0.774f, 0.635f, 0.063f, 0.095f, 0.641f)
            TractorModel.MAHINDRA_575 -> floatArrayOf(0.243f, 0.701f, 0.753f, 0.670f, 0.115f, 0.015f, 0.665f)
            TractorModel.SONALIKA_750 -> floatArrayOf(0.242f, 0.690f, 0.757f, 0.644f, 0.040f, 0.018f, 0.654f)
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
        tongueLocalY = ((restTractorY + hitchLocalY) - restTrailerY).coerceIn(-0.22f, 0.25f)

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
        // Fuel rack response: smooth diesel injection build-up on press, immediate fuel cut on release
        val throtRate = if (rawThrottle > smoothedThrottle) 2.1f else 13.5f
        smoothedThrottle += (rawThrottle - smoothedThrottle) * min(1f, dt * throtRate)
        smoothedBrake += (brakeInput - smoothedBrake) * min(1f, dt * 8.5f)
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
        // Transmission gearbox with load hysteresis so heavy climbs/mud stay in high-torque low gears
        val rearWx = x + rearAnchorLx * cosA
        val rearMudTrap = terrain.isMudTrapAt(rearWx)
        val rearMudDepth = terrain.mudDepthAt(rearWx)
        val inMudZone = rearResult.grounded && rearResult.surface == SurfaceType.MUD
        val inDeepBog = inMudZone && (rearMudTrap || rearMudDepth >= 0.42f)

        currentGear = when {
            forwardSpeed < -0.30f || (effBrake > 0.25f && forwardSpeed < 0.22f) -> 0
            inDeepBog || forwardSpeed < 2.9f -> 1
            forwardSpeed < 5.7f -> 2
            forwardSpeed < 8.5f -> 3
            else -> 4
        }

        val idleRpm = if (tractor == TractorModel.EICHER_241) 580f else 680f
        val redlineRpm = 2380f
        val mudLoadBonus = when {
            inDeepBog -> 0.58f
            currentSurface == SurfaceType.MUD -> 0.40f
            currentSurface == SurfaceType.SAND -> 0.20f
            currentSurface == SurfaceType.WET -> 0.14f
            else -> 0f
        }
        val slopeLoad = (sinA * 0.48f).coerceIn(0f, 0.45f)
        val loadDragFactor = ((activeCargoMassKg / 2400f) + mudLoadBonus + slopeLoad).coerceIn(0f, 1.25f)

        // Real Diesel Torque Curve peaking at mid-band RPM (1350..1750 RPM)
        val rpmNorm = ((engineRpm - idleRpm) / (redlineRpm - idleRpm)).coerceIn(0f, 1f)
        val dieselTorqueCurve = (0.78f + 0.54f * sin(rpmNorm * PI.toFloat() * 0.85f) - 0.16f * rpmNorm * rpmNorm)
            .coerceIn(0.42f, 1.32f)
        val gearMechAdvantage = when (currentGear) {
            0 -> 1.35f
            1 -> 1.46f
            2 -> 1.22f
            3 -> 1.05f
            else -> 0.94f
        }

        // Transmission-coupled wheel speed feeds back into engine RPM (including wheelspin in mud!)
        val wheelSurfaceSpeed = abs(rearWheelOmega) * tractor.rearWheelRadius
        val effectiveDrivetrainSpeed = max(abs(forwardSpeed), wheelSurfaceSpeed * 0.72f)
        val gearRpmScale = when (currentGear) {
            0 -> 240f
            1 -> 420f
            2 -> 280f
            3 -> 200f
            else -> 160f
        }
        val targetRpm = if (fuel <= 0f) 0f else {
            val powerRpmBoost = if (powerModeInput) 190f * effThrottle else 0f
            val slipRevExtra = (tyreSlipRatio * 340f * effThrottle).coerceAtMost(420f)
            val governedBand = idleRpm + effThrottle * 580f + powerRpmBoost + slipRevExtra
            val coupledClimb = effectiveDrivetrainSpeed * gearRpmScale * (if (rawThrottle > 0.05f) 0.65f else 0.22f)
            (governedBand + coupledClimb - loadDragFactor * 95f * effThrottle)
                .coerceIn(idleRpm - 30f, redlineRpm)
        }
        // Gradual flywheel pickup under diesel load when accelerating; immediate drop when pedal is released
        val rpmRate = when {
            rawThrottle < 0.05f && targetRpm <= engineRpm -> 9.4f
            targetRpm > engineRpm -> (1.24f - loadDragFactor * 0.24f + if (powerModeInput) 0.22f else 0f).coerceAtLeast(0.68f)
            else -> 7.6f
        }
        engineRpm += (targetRpm - engineRpm) * min(1f, dt * rpmRate)

        var stepBogLevel = 0f
        var stepSlipRatio = 0f

        if (rearResult.grounded || frontResult.grounded) {
            val surf = if (rearResult.grounded) rearResult.surface else frontResult.surface
            val tx = if (rearResult.grounded) rearResult.tx else frontResult.tx
            val ty = if (rearResult.grounded) rearResult.ty else frontResult.ty
            val tangentSpeed = vx * tx + vy * ty

            // True rear-axle normal load determines traction available on road/mud
            val rearNormalForce = if (rearResult.grounded) {
                sqrt(rearResult.fx * rearResult.fx + rearResult.fy * rearResult.fy)
            } else massT * gravity * 0.35f
            val frontNormalForce = if (frontResult.grounded) {
                sqrt(frontResult.fx * frontResult.fx + frontResult.fy * frontResult.fy)
            } else 0f

            // Power Boost engages 4WD front axle + differential lock cleat bite in mud!
            val effectiveNormalWeight = if (powerModeInput) {
                (rearNormalForce + frontNormalForce * 0.85f).coerceAtLeast(massT * gravity * 1.02f)
            } else {
                (rearNormalForce + frontNormalForce * 0.42f).coerceAtLeast(massT * gravity * 0.88f)
            }

            // Deep random mud traps lose tyre bite unless upgraded Lug Tyres (>=1.38) or POWER mode is turned ON!
            val mudTractionPenalty = when {
                inDeepBog && powerModeInput -> 1.24f // Diff-lock + 4WD bites solidly into mud!
                inDeepBog && gripMult < 1.38f -> (0.40f - 0.14f * rearMudDepth).coerceIn(0.24f, 0.36f)
                inDeepBog -> 0.82f // Upgraded max Lug Tyres dig cleanly through mud pits
                else -> 1.0f
            }
            val powerGripBonus = when {
                powerModeInput && surf == SurfaceType.MUD -> 1.72f
                powerModeInput -> 1.28f
                else -> 1.0f
            }
            val surfaceGrip = (surf.baseGrip * gripMult * powerGripBonus * mudTractionPenalty).coerceIn(0.16f, 1.75f)
            val maxTractionForce = effectiveNormalWeight * surfaceGrip * 1.22f

            var driveForce = 0f
            var requestedAxleForce = 0f
            if (effThrottle > 0.01f && !handbrakeInput) {
                val powerTorqueBonus = if (powerModeInput) 1.52f else 1.0f
                val effectiveTopSpeed = maxSpeedMs * (if (powerModeInput) 1.18f else 1.0f)
                val speedRatio = (tangentSpeed / effectiveTopSpeed).coerceIn(0f, 1.08f)
                val governorTaper = (1.20f - 0.86f * speedRatio * speedRatio).coerceAtLeast(0.08f)

                requestedAxleForce = massT * 8.6f * powerMult * torqueMult * powerTorqueBonus *
                    gearMechAdvantage * dieselTorqueCurve * governorTaper * effThrottle

                driveForce += min(requestedAxleForce, maxTractionForce)

                // Realistic torque squat proportional to transmitted ground traction
                torque += driveForce * 0.056f
            }

            if (effBrake > 0.01f) {
                if (tangentSpeed > 0.45f) {
                    val brakeForce = massT * 13.5f * brakeMult * effBrake * surfaceGrip.coerceAtLeast(0.55f)
                    driveForce -= brakeForce
                    torque -= brakeForce * 0.070f
                } else {
                    val revLimit = -4.5f
                    if (tangentSpeed > revLimit) {
                        val revForce = massT * 5.8f * torqueMult * effBrake * surfaceGrip.coerceAtLeast(0.60f)
                        driveForce -= revForce
                    }
                }
            }

            if (handbrakeInput) {
                val hbForce = (-tangentSpeed * massT * 13f).coerceIn(-massT * 16f, massT * 16f)
                driveForce += hbForce * surfaceGrip.coerceAtLeast(0.65f)
            }

            // Viscous mud trap bog suction holds tractor back unless POWER 4WD breaks suction!
            val mudBogResist = if (inDeepBog && !powerModeInput && gripMult < 1.38f) {
                val trapHold = massT * (2.6f + 2.4f * rearMudDepth)
                if (abs(tangentSpeed) < 1.4f) -tangentSpeed * massT * 6.8f else -signum(tangentSpeed) * trapHold * 0.45f
            } else 0f

            // Real compression engine braking when accelerator is released + surface rolling drag
            val engineBraking = if (rawThrottle < 0.05f && effBrake < 0.05f && abs(tangentSpeed) > 0.25f) {
                0.75f + 0.55f * gearMechAdvantage
            } else 0.10f
            val mudViscousScale = when {
                inDeepBog && (powerModeInput || gripMult >= 1.38f) -> 12f
                inDeepBog -> 42f
                surf == SurfaceType.MUD -> 26f
                else -> 18f
            }
            val rollingResist = -tangentSpeed * massT * (surf.rollingDrag * mudViscousScale + engineBraking) + mudBogResist
            val totalTangential = driveForce + rollingResist

            forceX += tx * totalTangential
            forceY += ty * totalTangential

            // Real rotational wheel physics: excess torque above road grip spins rear tyre independently!
            val groundOmega = tangentSpeed / tractor.rearWheelRadius
            val torqueExcess = (requestedAxleForce - maxTractionForce).coerceAtLeast(0f)
            val tractionBreakSlip = if (requestedAxleForce > 1f) {
                (torqueExcess / maxTractionForce.coerceAtLeast(100f)).coerceIn(0f, 2.2f)
            } else 0f

            val stuckSpinBoost = if (inDeepBog && !powerModeInput && effThrottle > 0.12f) {
                // Tyre stalls in deep mud & spins furiously forward throwing kichad!
                (6.5f + effThrottle * 11.5f) * (1f + 0.35f * rearMudDepth)
            } else 0f

            val targetRearOmega = when {
                handbrakeInput -> 0f
                effBrake > 0.2f && tangentSpeed > 0.5f -> groundOmega * 0.45f
                else -> groundOmega + tractionBreakSlip * (5.5f + effThrottle * 8.0f) + stuckSpinBoost
            }
            rearWheelOmega += (targetRearOmega - rearWheelOmega) * min(1f, dt * 11f)
            frontWheelOmega = tangentSpeed / tractor.frontWheelRadius

            rearWheelRot += rearWheelOmega * dt
            frontWheelRot += frontWheelOmega * dt

            // Compute wheel slip velocity & mud bog severity for UI warnings and kichad spray
            val wheelRimSpeed = rearWheelOmega * tractor.rearWheelRadius
            val slipSpeed = abs(wheelRimSpeed - tangentSpeed)
            stepSlipRatio = (slipSpeed / max(2.2f, abs(tangentSpeed) + 1.8f)).coerceIn(0f, 2.0f)
            stepBogLevel = if (inDeepBog) {
                if (!powerModeInput && gripMult < 1.38f) (0.72f + 0.28f * rearMudDepth).coerceIn(0f, 1f)
                else 0.30f
            } else 0f

            // Dynamic Kichad / Water / Dust emission driven by actual tyre rim slip & speed!
            val shouldEmit = abs(tangentSpeed) > 1.0f || (slipSpeed > 1.0f && (surf == SurfaceType.MUD || surf == SurfaceType.WET || surf == SurfaceType.SAND))
            if (shouldEmit) {
                val slipIntensity = if (surf == SurfaceType.MUD || surf == SurfaceType.WET) {
                    slipSpeed * 5.2f + if (inDeepBog && effThrottle > 0.15f) 18f else 0f
                } else {
                    slipSpeed * 2.4f
                }
                dustAccum += dt * (2.8f + abs(tangentSpeed) * 1.05f + slipIntensity)
                val spawnCount = dustAccum.toInt().coerceIn(0, 3)
                if (spawnCount > 0) {
                    dustAccum -= spawnCount
                    val pKind = when (surf) {
                        SurfaceType.MUD -> 2
                        SurfaceType.WET -> 3
                        else -> 1
                    }
                    val wx = x + rearAnchorLx * cosA
                    val wy = terrain.heightAt(wx) + 0.04f
                    repeat(spawnCount) {
                        val flingBack = (if (surf == SurfaceType.MUD) 2.8f else 0.9f) + slipSpeed * 0.55f + rng.nextFloat() * 2.4f
                        val flingUp = (if (surf == SurfaceType.MUD) 1.3f else 0.6f) + slipSpeed * 0.26f + rng.nextFloat() * 1.9f
                        spawnParticle(
                            px = wx - 0.15f + (rng.nextFloat() - 0.5f) * 0.35f,
                            py = wy + rng.nextFloat() * 0.12f,
                            pvx = -vx * 0.30f - flingBack,
                            pvy = flingUp,
                            size = (if (surf == SurfaceType.MUD) 0.16f else 0.13f) + rng.nextFloat() * 0.15f,
                            life = 0.44f + rng.nextFloat() * 0.32f,
                            kind = pKind
                        )
                    }
                }
            }
        } else {
            // Airborne pitch control & wheel spin
            torque += (effThrottle * 0.45f - effBrake * 0.42f) * massT * 1.7f
            rearWheelOmega += ((effThrottle * 11f) - rearWheelOmega) * min(1f, dt * 6f)
            rearWheelRot += rearWheelOmega * dt
            frontWheelRot += (vx / tractor.frontWheelRadius) * dt * 0.5f
        }

        isStuckInMud = inDeepBog && !powerModeInput && gripMult < 1.38f && abs(vx) < 1.55f
        mudBogLevel += (stepBogLevel - mudBogLevel) * min(1f, dt * 8f)
        tyreSlipRatio += (stepSlipRatio - tyreSlipRatio) * min(1f, dt * 10f)
        chassisMudCoat = if (inMudZone) {
            min(1f, chassisMudCoat + dt * (0.28f + tyreSlipRatio * 0.45f))
        } else {
            max(0f, chassisMudCoat - dt * 0.035f)
        }

        // Realistic chassis angular damping (allows genuine tipping on extreme slopes/crests while stable normally)
        angularVel *= (1f - dt * 2.85f)

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

        // Tractor hood & driver/ROPS roof impact collision when tipping/overturning onto side or upside down!
        if (abs(angle) > 1.12f) {
            val hoodTopWx = x + 0.75f * cosA - 1.05f * sinA
            val hoodTopWy = y + 0.75f * sinA + 1.05f * cosA
            val hoodGround = terrain.heightAt(hoodTopWx)
            val cabTopWx = x - 0.30f * cosA - 1.45f * sinA
            val cabTopWy = y - 0.30f * sinA + 1.45f * cosA
            val cabGround = terrain.heightAt(cabTopWx)
            val maxPen = max(hoodGround - hoodTopWy, cabGround - cabTopWy)
            if (maxPen > 0f) {
                y += maxPen * 0.85f
                if (vy < -1.8f && bumpCooldown <= 0f) {
                    soundEngine?.playSfx(SfxType.BUMP)
                    camShake = 0.28f
                    bumpCooldown = 0.32f
                    spawnParticle(cabTopWx, cabGround + 0.1f, (rng.nextFloat() - 0.5f) * 2.4f, 1.8f, 0.22f, 0.55f, 1)
                }
                if (vy < 0f) vy = -vy * 0.08f
                vx *= 0.90f
                angularVel *= 0.84f
            }
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

        val targetLean = (-effThrottle * 0.16f + effBrake * 0.18f - sinA * 0.24f).coerceIn(-0.34f, 0.34f)
        driverLean += (targetLean - driverLean) * (dt * 6f)

        // Realistic Diesel Exhaust Plumes straight from the exact Silencer Tip (heavier black soot in kichad/load!)
        val exhaustRate = 7.5f + effThrottle * 22f + loadDragFactor * effThrottle * 11f
        exhaustAccum += dt * exhaustRate
        if (exhaustAccum >= 1f && fuel > 0f) {
            exhaustAccum = 0f
            val exWorldX = x + exhaustLocalX * cosA - exhaustLocalY * sinA
            val exWorldY = y + exhaustLocalX * sinA + exhaustLocalY * cosA
            val loadSoot = (0.28f + effThrottle * 0.48f + loadDragFactor * 0.24f).coerceIn(0.2f, 0.98f)
            spawnParticle(
                px = exWorldX,
                py = exWorldY,
                pvx = vx * 0.25f - 0.55f + (rng.nextFloat() - 0.5f) * 0.45f,
                pvy = 1.9f + effThrottle * 2.1f + rng.nextFloat() * 0.5f,
                size = 0.08f + effThrottle * 0.09f + (if (currentSurface == SurfaceType.MUD) 0.03f else 0f),
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
            val mudTrailerMult = if (trWheelRes.surface == SurfaceType.MUD) 24f else 16f
            val drag = -tSpeed * totalMassTr * (trWheelRes.surface.rollingDrag * mudTrailerMult + 0.16f + trailerBrake)
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

        // Curved circular tyre footprint with realistic tyre sinkage into soft kichad mud & river sand
        val requiredCenterY = sampleRequiredWheelCenterY(maxWheelX, wheelRadius, nx, surf)
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

    private fun sampleRequiredWheelCenterY(wx: Float, r: Float, nx: Float, surf: SurfaceType): Float {
        val slopeDx = (-nx * r).coerceIn(-r * 0.62f, r * 0.62f)
        val halfSpan = r * 0.42f
        val d0 = terrain.heightAt(wx) + r
        val dL = terrain.heightAt(wx - halfSpan) + sqrt(max(0f, r * r - halfSpan * halfSpan))
        val dR = terrain.heightAt(wx + halfSpan) + sqrt(max(0f, r * r - halfSpan * halfSpan))
        val dS = terrain.heightAt(wx + slopeDx) + sqrt(max(0f, r * r - slopeDx * slopeDx))
        val mudDepth = terrain.mudDepthAt(wx)
        val mudSink = when (surf) {
            SurfaceType.MUD -> 0.085f + mudDepth * 0.085f
            SurfaceType.SAND -> 0.038f
            SurfaceType.WET -> 0.024f
            else -> 0f
        }
        return max(max(d0, dL), max(dR, dS)) - mudSink
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

        tiltDanger = ((abs(angle) - 0.62f) / 0.68f).coerceIn(0f, 1f)
        if (abs(angle) > 2.15f) {
            overturnTimer += dt * 3.5f
            if (overturnTimer >= 0.40f) {
                triggerGameOver(GameOverReason.OVERTURNED)
                return
            }
        } else if (abs(angle) > 1.32f) {
            overturnTimer += dt
            if (overturnTimer >= 0.45f) {
                triggerGameOver(GameOverReason.OVERTURNED)
                return
            }
        } else {
            overturnTimer = max(0f, overturnTimer - dt * 2.2f)
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
        val leadOffset = (if (hasTrailer) 0.55f else 1.80f) + (vx * 0.18f).coerceIn(-1.0f, 2.5f)
        val targetCamX = x + leadOffset
        val targetCamY = y + 0.62f
        camX += (targetCamX - camX) * (dt * 6.8f)
        camY += (targetCamY - camY) * (dt * 4.8f)
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
        val surf = terrain.surfaceAt(maxWheelX)
        val (nx, _) = terrain.normalAt(maxWheelX)
        val requiredCenterY = sampleRequiredWheelCenterY(maxWheelX, wheelRadius, nx, surf)
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
            val mudStrain = if (currentSurface == SurfaceType.MUD) 0.28f + mudBogLevel * 0.25f else 0f
            val powerStrain = if (powerModeInput && smoothedThrottle > 0.1f) 0.18f else 0f
            audio.loadStrain = (slopeStrain + cargoStrain + mudStrain + powerStrain).coerceIn(0f, 1f)
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
            currentSurface = currentSurface,
            isStuckInMud = isStuckInMud,
            mudBogLevel = mudBogLevel,
            tyreSlipRatio = tyreSlipRatio,
            tiltDanger = tiltDanger
        )
    }

    private fun signum(v: Float): Float = when {
        v > 0f -> 1f
        v < 0f -> -1f
        else -> 0f
    }

    private fun normalizeAngle(a: Float): Float {
        var r = a
        while (r > PI) r -= (2 * PI).toFloat()
        while (r < -PI) r += (2 * PI).toFloat()
        return r
    }
}
