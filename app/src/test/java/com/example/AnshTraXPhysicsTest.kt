package com.example

import com.example.engine.PhysicsWorld
import com.example.engine.RunPhase
import com.example.engine.TerrainGenerator
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.GameCatalog
import com.example.model.MapEnvironment
import com.example.model.TractorModel
import com.example.model.TractorUpgrades
import com.example.model.TrailerType
import com.example.model.UpgradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class AnshTraXPhysicsTest {

    @Test
    fun allTenMapsGenerateSmoothEndlessTerrainWithPickups() {
        for (map in MapEnvironment.entries) {
            val gen = TerrainGenerator(map)
            gen.ensureGeneratedUpTo(1200f)

            var prevY = gen.heightAt(0f)
            var x = 1f
            while (x <= 1000f) {
                val y = gen.heightAt(x)
                assertFalse("NaN height in ${map.id}", y.isNaN())
                assertTrue("Excessive vertical jump in ${map.id} at x=$x", abs(y - prevY) < 1.3f)
                prevY = y
                x += 1f
            }

            assertTrue("Missing coins in ${map.id}", gen.collectibles.any { !it.isFuel })
            assertTrue("Missing fuel cans in ${map.id}", gen.collectibles.any { it.isFuel })
            assertTrue("Missing checkpoints in ${map.id}", gen.checkpoints.isNotEmpty())
        }
    }

    @Test
    fun allThreeTractorsDriveBrakeAndReverse() {
        for (tractor in TractorModel.entries) {
            val world = PhysicsWorld(
                tractor = tractor,
                driver = DriverCharacter.ANSH,
                trailer = TrailerType.NONE,
                cargo = CargoType.NONE,
                map = MapEnvironment.INDIAN_VILLAGE,
                upgrades = TractorUpgrades(),
                mission = null,
                soundEngine = null
            )

            // Accelerate for 3 seconds
            world.throttleInput = 1f
            repeat(180) { world.step(1f / 60f) }

            assertTrue("${tractor.displayName} failed to move forward", world.x > 12f)
            assertTrue("${tractor.displayName} RPM did not climb", world.engineRpm > 900f)
            assertTrue("${tractor.displayName} rear wheel did not rotate", world.rearWheelRot > 1f)

            // Brake & reverse for 3 seconds
            world.throttleInput = 0f
            world.brakeInput = 1f
            val peakX = world.x
            repeat(180) { world.step(1f / 60f) }
            assertTrue("${tractor.displayName} failed to slow/reverse", world.vx < 1f || world.x < peakX + 5f)
        }
    }

    @Test
    fun trailerHitchRemainsConnectedAndHeavyCargoAffectsPhysics() {
        fun driveWithCargo(cargo: CargoType): PhysicsWorld {
            val world = PhysicsWorld(
                tractor = TractorModel.MAHINDRA_575,
                driver = DriverCharacter.VANSH,
                trailer = TrailerType.HEAVY_TIPPER,
                cargo = cargo,
                map = MapEnvironment.FARMING_FIELDS,
                upgrades = TractorUpgrades(),
                mission = null,
                soundEngine = null
            )
            world.throttleInput = 1f
            repeat(240) { world.step(1f / 60f) }
            return world
        }

        val lightRun = driveWithCargo(CargoType.NONE)
        val heavyRun = driveWithCargo(CargoType.STONES)

        // Heavy boulders must noticeably reduce acceleration/distance compared to empty bed
        assertTrue(
            "Heavy stones (${heavyRun.x}) should accelerate slower than empty trailer (${lightRun.x})",
            heavyRun.x < lightRun.x
        )

        // Verify hitch pin stays physically connected (distance between hitch & tongue < 0.35m)
        val cosT = cos(heavyRun.angle)
        val sinT = sin(heavyRun.angle)
        val hWx = heavyRun.x + heavyRun.hitchLocalX * cosT - heavyRun.hitchLocalY * sinT
        val hWy = heavyRun.y + heavyRun.hitchLocalX * sinT + heavyRun.hitchLocalY * cosT

        val cosTr = cos(heavyRun.trAngle)
        val sinTr = sin(heavyRun.trAngle)
        val tWx = heavyRun.trX + heavyRun.tongueLocalX * cosTr - heavyRun.tongueLocalY * sinTr
        val tWy = heavyRun.trY + heavyRun.tongueLocalX * sinTr + heavyRun.tongueLocalY * cosTr

        val hitchGap = hypot(hWx - tWx, hWy - tWy)
        assertTrue("Trailer hitch separated too far: $hitchGap m", hitchGap < 0.35f)
        assertTrue("Trailer tyre suspension bottomed out: ${heavyRun.trWheelCompression}", heavyRun.trWheelCompression > 0.14f)
        assertTrue("Cargo pieces should remain inside trailer during normal driving", heavyRun.cargoPieces.any { !it.spilled })
    }

    @Test
    fun allThreeTractorsStayLevelAndEmitSilencerSmoke() {
        for (tractor in TractorModel.entries) {
            // Flat launchpad check with trailer attached
            val world = PhysicsWorld(
                tractor = tractor,
                driver = DriverCharacter.VANSH,
                trailer = TrailerType.SMALL_TROLLEY,
                cargo = CargoType.SOIL,
                map = MapEnvironment.INDIAN_VILLAGE,
                upgrades = TractorUpgrades(),
                mission = null,
                soundEngine = null
            )

            // Let suspension settle on launchpad
            repeat(60) { world.step(1f / 60f) }
            assertTrue("${tractor.displayName} front wheel lifted at idle", world.frontWheelGrounded)
            assertTrue("${tractor.displayName} rear wheel lifted at idle", world.rearWheelGrounded)
            assertTrue("${tractor.displayName} pitched up at idle: ${world.angle}", abs(world.angle) < 0.08f)

            // Accelerate along launchpad (x = 6m to ~18m is flat)
            world.throttleInput = 1f
            repeat(75) { world.step(1f / 60f) }
            assertTrue(
                "${tractor.displayName} pitched excessively under throttle (wheelie): ${world.angle}",
                abs(world.angle) < 0.14f
            )
            assertTrue(
                "${tractor.displayName} front wheel should remain grounded on flat launch",
                world.frontWheelGrounded
            )

            val smokeParticles = world.particles.filter { it.kind == 0 }
            assertTrue("${tractor.displayName} did not emit diesel smoke from exhaust", smokeParticles.isNotEmpty())
            assertTrue(
                "${tractor.displayName} smoke should emit near top silencer height",
                smokeParticles.any { it.y > world.y + 0.6f }
            )
        }
    }

    @Test
    fun upgradesFuelAndMissionsWorkCorrectly() {
        val baseUpgrades = TractorUpgrades()
        val maxedUpgrades = TractorUpgrades(UpgradeType.entries.associateWith { 5 })

        val baseWorld = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.AKSHAY,
            trailer = TrailerType.SMALL_TROLLEY,
            cargo = CargoType.SOIL,
            map = MapEnvironment.INDIAN_VILLAGE,
            upgrades = baseUpgrades,
            mission = GameCatalog.missions.first(),
            soundEngine = null
        )

        val maxedWorld = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.AKSHAY,
            trailer = TrailerType.SMALL_TROLLEY,
            cargo = CargoType.SOIL,
            map = MapEnvironment.INDIAN_VILLAGE,
            upgrades = maxedUpgrades,
            mission = GameCatalog.missions.first(),
            soundEngine = null
        )

        assertTrue("Upgraded fuel tank must be larger", maxedWorld.maxFuel > baseWorld.maxFuel)

        maxedWorld.throttleInput = 1f
        val initialFuel = maxedWorld.fuel
        var steps = 0
        while (maxedWorld.phase == RunPhase.RUNNING && steps < 4000) {
            maxedWorld.step(1f / 60f)
            steps++
        }

        assertTrue("Fuel should decrease during driving", maxedWorld.fuel < initialFuel || maxedWorld.maxDistanceMeters > 100)
        assertEquals("Reaching target distance should trigger UNLOADING", RunPhase.UNLOADING, maxedWorld.phase)

        // Finish unloading animation
        repeat(150) { maxedWorld.step(1f / 60f) }
        assertEquals("Unloading should transition to MISSION_COMPLETE", RunPhase.MISSION_COMPLETE, maxedWorld.phase)
    }

    @Test
    fun allThreeCharactersHaveDistinctIdentitiesAndDrawables() {
        val names = DriverCharacter.entries.map { it.displayName }
        assertEquals(listOf("Vansh", "Ansh", "Akshay"), names)
        assertEquals(3, DriverCharacter.entries.map { it.faceDrawable }.toSet().size)
    }

    @Test
    fun wavSampleLoaderParsesRealEicherAudioAssetsSafely() {
        val rawFiles = listOf(
            "src/main/res/raw/eicher_idle_loop.wav",
            "src/main/res/raw/eicher_rev_loop.wav",
            "src/main/res/raw/eicher_starter.wav"
        )
        for (relPath in rawFiles) {
            val file = java.io.File(relPath).takeIf { it.exists() }
                ?: java.io.File("app/$relPath")
            assertTrue("Missing audio asset $relPath", file.exists())
            val pcm = file.inputStream().use { com.example.audio.WavPcmCodec.parseMonoPcm16Wav(it) }
            assertTrue("Failed to parse WAV $relPath", pcm != null && pcm.size > 10000)
            val s0 = com.example.audio.WavPcmCodec.sampleLoopLinear(pcm!!, 0.0f)
            val sEnd = com.example.audio.WavPcmCodec.sampleLoopLinear(pcm, 0.9999f)
            val sWrap = com.example.audio.WavPcmCodec.sampleLoopLinear(pcm, -0.0001f)
            assertFalse("NaN sample in $relPath", s0.isNaN() || sEnd.isNaN() || sWrap.isNaN())
            assertTrue("Sample out of bounds in $relPath", abs(s0) <= 1.0f && abs(sEnd) <= 1.0f)
        }
    }

    @Test
    fun hitchRaisedStraightAndPowerBoostAndAsymmetricRpmWork() {
        val normalWorld = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.ANSH,
            trailer = TrailerType.HEAVY_TIPPER,
            cargo = CargoType.STONES,
            map = MapEnvironment.MOUNTAIN_ROADS,
            upgrades = TractorUpgrades(),
            mission = null,
            soundEngine = null
        )
        // Raised hitch vertical offset sits cleanly above rear axle (-0.31m) around -0.21m for a straight horizontal drawbar
        assertTrue(
            "Raised Hitch Y (${normalWorld.hitchLocalY}) should sit cleanly above rear axle (-0.214m ± 0.04m)",
            abs(normalWorld.hitchLocalY - (-0.214f)) < 0.04f
        )
        assertTrue(
            "Trailer tongue Y (${normalWorld.tongueLocalY}) should sit nearly level",
            abs(normalWorld.tongueLocalY) <= 0.25f
        )

        // Verify slow gradual pickup build-up vs swift RPM drop when releasing accelerator
        val initialRpm = normalWorld.engineRpm
        normalWorld.throttleInput = 1f
        repeat(6) { normalWorld.step(1f / 60f) }
        val delta100msClimb = normalWorld.engineRpm - initialRpm
        assertTrue("RPM climbed too abruptly in 100ms: +$delta100msClimb", delta100msClimb < 260f)

        // Rev up for 1.5s then release gas and check quick drop over 0.3s
        repeat(84) { normalWorld.step(1f / 60f) }
        val peakRpm = normalWorld.engineRpm
        normalWorld.throttleInput = 0f
        repeat(18) { normalWorld.step(1f / 60f) }
        val drop300ms = peakRpm - normalWorld.engineRpm
        assertTrue("RPM did not drop briskly on accelerator release: -$drop300ms", drop300ms > 120f)

        // Compare distance with Power Boost OFF vs ON over 3 seconds
        val freshNormal = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.ANSH,
            trailer = TrailerType.HEAVY_TIPPER,
            cargo = CargoType.STONES,
            map = MapEnvironment.MOUNTAIN_ROADS,
            upgrades = TractorUpgrades(),
            mission = null,
            soundEngine = null
        )
        freshNormal.throttleInput = 1f

        val powerWorld = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.ANSH,
            trailer = TrailerType.HEAVY_TIPPER,
            cargo = CargoType.STONES,
            map = MapEnvironment.MOUNTAIN_ROADS,
            upgrades = TractorUpgrades(),
            mission = null,
            soundEngine = null
        )
        powerWorld.setPowerMode(true)
        powerWorld.throttleInput = 1f

        repeat(180) {
            freshNormal.step(1f / 60f)
            powerWorld.step(1f / 60f)
        }
        assertTrue(
            "Power Boost (${powerWorld.x}m) should pull heavier/faster than Normal (${freshNormal.x}m)",
            powerWorld.x > freshNormal.x
        )

        // Verify Kichad (MUD) imposes significantly heavier rolling drag & lower grip than DIRT
        assertTrue(
            "MUD rollingDrag (${com.example.model.SurfaceType.MUD.rollingDrag}) must be > 3x DIRT (${com.example.model.SurfaceType.DIRT.rollingDrag})",
            com.example.model.SurfaceType.MUD.rollingDrag > com.example.model.SurfaceType.DIRT.rollingDrag * 3f
        )
    }

    @Test
    fun randomMudBogTrapsSpinningTyresAndPowerModePullsOut() {
        val world = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.VANSH,
            trailer = TrailerType.SMALL_TROLLEY,
            cargo = CargoType.SOIL,
            map = MapEnvironment.FARMING_FIELDS,
            upgrades = TractorUpgrades(),
            mission = null,
            soundEngine = null,
            terrainSeed = 424242L
        )
        world.terrain.ensureGeneratedUpTo(260f)
        assertTrue("Background landmarks should be populated", world.terrain.landmarks.isNotEmpty())

        // Find the first generated random mud trap past launchpad and position tractor into it
        val pts = world.terrain.getVisiblePoints(30f, 220f)
        val trapPt = pts.firstOrNull { it.isMudTrap }
        assertTrue("Random mud trap should spawn on Farming Fields", trapPt != null)

        val trapCenterX = trapPt!!.x + 2.4f
        world.x = trapCenterX
        world.y = world.terrain.heightAt(trapCenterX) + world.tractor.rearWheelRadius + 0.25f
        world.vx = 0.6f
        world.vy = 0f
        world.trX = world.x + world.hitchLocalX - world.tongueLocalX
        world.trY = world.terrain.heightAt(world.trX) + world.trailer.wheelRadius + 0.25f
        world.trVx = 0.6f
        world.throttleInput = 1f

        // Without POWER mode, tractor bogs down, rear tyre slips fast, and throws mud particles
        repeat(90) { world.step(1f / 60f) }
        val bogSpeed = world.vx
        val mudParticles = world.particles.count { it.kind == 2 }
        assertTrue("Tractor tyre should spin fast when bogged in mud: omega=${world.rearWheelOmega}", world.rearWheelOmega > 4.5f)
        assertTrue("Mud splash particles should fling out behind spinning tyre ($mudParticles)", mudParticles > 0)
        assertTrue("Tractor without POWER should crawl/stick slowly in mud trap: vx=$bogSpeed", bogSpeed < 2.2f)

        // Turning ON POWER mode engages diff-lock / 4WD traction and pulls cleanly forward
        world.setPowerMode(true)
        val xBeforePower = world.x
        repeat(150) { world.step(1f / 60f) }
        assertTrue(
            "POWER mode should pull tractor out of mud trap (moved from $xBeforePower to ${world.x})",
            world.x - xBeforePower > 4.5f
        )
    }

    @Test
    fun tractorOverturnTriggersGameOverDefeat() {
        val world = PhysicsWorld(
            tractor = TractorModel.EICHER_241,
            driver = DriverCharacter.ANSH,
            trailer = TrailerType.NONE,
            cargo = CargoType.NONE,
            map = MapEnvironment.MOUNTAIN_ROADS,
            upgrades = TractorUpgrades(),
            mission = null,
            soundEngine = null,
            terrainSeed = 108241L
        )
        // Simulate tractor flipped onto its roof/side past overturn threshold
        world.angle = 2.45f
        world.angularVel = 0f
        repeat(45) { world.step(1f / 60f) }

        assertEquals("Overturned tractor must trigger GAME_OVER", RunPhase.GAME_OVER, world.phase)
        assertEquals(
            "Overturned tractor must set OVERTURNED reason",
            com.example.engine.GameOverReason.OVERTURNED,
            world.gameOverReason
        )
    }
}
