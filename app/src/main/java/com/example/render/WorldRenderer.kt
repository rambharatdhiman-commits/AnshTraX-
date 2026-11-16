package com.example.render

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.engine.CargoPiece
import com.example.engine.PhysicsWorld
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.TractorModel
import com.example.model.TrailerType
import com.example.render.EnvironmentArt.drawRealisticAtmosphere
import com.example.render.EnvironmentArt.drawRealisticProp
import com.example.render.EnvironmentArt.drawRealisticTerrain
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class TractorArtAssets(
    val body: ImageBitmap,
    val rearWheel: ImageBitmap,
    val frontWheel: ImageBitmap,
    val trailerWheel: ImageBitmap,
    val face: ImageBitmap?,
    val textures: ProceduralTextures
) {
    companion object {
        private fun safeDecodeImageBitmap(resources: Resources, resId: Int): ImageBitmap {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = try {
                BitmapFactory.decodeResource(resources, resId, opts)
            } catch (_: Throwable) {
                null
            }
            return (bmp ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)).asImageBitmap()
        }

        private fun safeDecodeOptionalBitmap(resources: Resources, resId: Int): ImageBitmap? {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return try {
                BitmapFactory.decodeResource(resources, resId, opts)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }

        fun load(resources: Resources, tractor: TractorModel, driver: DriverCharacter): TractorArtAssets {
            val (bodyId, rearId, frontId) = when (tractor) {
                TractorModel.EICHER_241 -> Triple(R.drawable.body_eicher, R.drawable.wheel_eicher_rear, R.drawable.wheel_eicher_front)
                TractorModel.MAHINDRA_575 -> Triple(R.drawable.body_mahindra, R.drawable.wheel_mahindra_rear, R.drawable.wheel_mahindra_front)
                TractorModel.SONALIKA_750 -> Triple(R.drawable.body_sonalika, R.drawable.wheel_sonalika_rear, R.drawable.wheel_sonalika_front)
            }
            return TractorArtAssets(
                body = safeDecodeImageBitmap(resources, bodyId),
                rearWheel = safeDecodeImageBitmap(resources, rearId),
                frontWheel = safeDecodeImageBitmap(resources, frontId),
                trailerWheel = safeDecodeImageBitmap(resources, R.drawable.wheel_trailer),
                face = safeDecodeOptionalBitmap(resources, driver.faceDrawable),
                textures = ProceduralTextures()
            )
        }
    }
}

private data class TractorSpriteMetrics(
    val rearAxleNx: Float,
    val rearAxleNy: Float,
    val frontAxleNx: Float,
    val frontAxleNy: Float,
    val seatNx: Float,
    val seatNy: Float,
    val footNx: Float,
    val footNy: Float,
    val gripNx: Float,
    val gripNy: Float,
    val headlightNx: Float,
    val headlightNy: Float,
    val rearLampNx: Float,
    val rearLampNy: Float,
    val hitchNx: Float,
    val hitchNy: Float
)

object WorldRenderer {

    private val conePath = Path()
    private val gussetPath = Path()
    private val heapPath = Path()
    private val torsoPath = Path()
    private val collarPath = Path()
    private val faceOvalPath = Path()
    private val rockPath = Path()
    private val flagPath = Path()
    private val backHairPath = Path()

    private fun metricsFor(tractor: TractorModel): TractorSpriteMetrics = when (tractor) {
        TractorModel.EICHER_241 -> TractorSpriteMetrics(
            rearAxleNx = 0.315f, rearAxleNy = 0.677f,
            frontAxleNx = 0.774f, frontAxleNy = 0.808f,
            seatNx = 0.292f, seatNy = 0.338f,
            footNx = 0.482f, footNy = 0.505f,
            gripNx = 0.470f, gripNy = 0.258f,
            headlightNx = 0.841f, headlightNy = 0.383f,
            rearLampNx = 0.145f, rearLampNy = 0.318f,
            hitchNx = 0.095f, hitchNy = 0.641f
        )
        TractorModel.MAHINDRA_575 -> TractorSpriteMetrics(
            rearAxleNx = 0.243f, rearAxleNy = 0.701f,
            frontAxleNx = 0.753f, frontAxleNy = 0.822f,
            seatNx = 0.308f, seatNy = 0.326f,
            footNx = 0.436f, footNy = 0.535f,
            gripNx = 0.455f, gripNy = 0.292f,
            headlightNx = 0.875f, headlightNy = 0.425f,
            rearLampNx = 0.118f, rearLampNy = 0.375f,
            hitchNx = 0.015f, hitchNy = 0.665f
        )
        TractorModel.SONALIKA_750 -> TractorSpriteMetrics(
            rearAxleNx = 0.242f, rearAxleNy = 0.690f,
            frontAxleNx = 0.757f, frontAxleNy = 0.788f,
            seatNx = 0.275f, seatNy = 0.294f,
            footNx = 0.428f, footNy = 0.515f,
            gripNx = 0.422f, gripNy = 0.270f,
            headlightNx = 0.870f, headlightNy = 0.370f,
            rearLampNx = 0.112f, rearLampNy = 0.368f,
            hitchNx = 0.018f, hitchNy = 0.654f
        )
    }

    fun DrawScope.renderWorld(
        world: PhysicsWorld,
        assets: TractorArtAssets,
        timeSec: Float
    ) {
        val w = size.width
        val h = size.height
        // Larger heroic camera zoom so both tractor and trolley fill the screen richly with sharp detail
        val ppm = (h / 6.7f).coerceIn(66f, 152f)

        val shakeX = if (world.camShake > 0.001f) sin(timeSec * 55f) * world.camShake * ppm else 0f
        val shakeY = if (world.camShake > 0.001f) cos(timeSec * 48f) * world.camShake * ppm else 0f

        val camX = world.camX
        val camY = world.camY
        val anchorScreenFrac = if (world.hasTrailer) 0.51f else 0.42f

        fun worldToScreenX(wx: Float): Float = w * anchorScreenFrac + (wx - camX) * ppm + shakeX
        fun worldToScreenY(wy: Float): Float = h * 0.68f - (wy - camY) * ppm + shakeY

        // 1. Atmospheric Sky, Sunbeams, Clouds, Flying Birds, Parallax Hills & Landmarks
        drawRealisticAtmosphere(world.map, world.terrain.landmarks, camX, w, h, timeSec)

        // 2. Roadside Props
        val minVisX = camX - (w * 0.62f) / ppm - 10f
        val maxVisX = camX + (w * 0.72f) / ppm + 12f
        for (prop in world.terrain.props) {
            if (prop.x < minVisX || prop.x > maxVisX) continue
            val sx = worldToScreenX(prop.x)
            val sy = worldToScreenY(world.terrain.heightAt(prop.x))
            drawRealisticProp(world.map, prop, sx, sy, ppm, timeSec)
        }

        // 3. Textured Ground, Strata & Shimmering Kichad Road Surface
        val visiblePts = world.terrain.getVisiblePoints(minVisX, maxVisX)
        drawRealisticTerrain(
            map = world.map,
            visiblePts = visiblePts,
            textures = assets.textures,
            worldToScreenX = ::worldToScreenX,
            worldToScreenY = ::worldToScreenY,
            h = h,
            ppm = ppm,
            timeSec = timeSec
        )

        // 4. Checkpoints & Finish Arch
        for (cpX in world.terrain.checkpoints) {
            if (cpX in minVisX..maxVisX) {
                drawCheckpointFlag(worldToScreenX(cpX), worldToScreenY(world.terrain.heightAt(cpX)), ppm, Color(0xFFD9911A))
            }
        }
        world.mission?.let { m ->
            val targetX = m.targetDistanceMeters.toFloat()
            if (targetX in minVisX..maxVisX) {
                drawCheckpointFlag(worldToScreenX(targetX), worldToScreenY(world.terrain.heightAt(targetX)), ppm, Color(0xFF288E46))
            }
        }

        // 5. Collectibles
        for (item in world.terrain.collectibles) {
            if (item.collected || item.x < minVisX || item.x > maxVisX) continue
            val bob = sin(timeSec * 4.2f + item.x) * 0.06f
            val sx = worldToScreenX(item.x)
            val sy = worldToScreenY(world.terrain.heightAt(item.x) + item.yOffsetAboveGround + bob)
            if (item.isFuel) drawFuelCan(sx, sy, ppm) else drawCoin(sx, sy, ppm, timeSec + item.x * 0.2f)
        }

        // 6. Crisp Zero-Allocation Dual-Feather Ground Contact Shadows
        val tractorGroundY = worldToScreenY(world.terrain.heightAt(world.x))
        val txScreen = worldToScreenX(world.x)
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(txScreen - 2.1f * ppm, tractorGroundY - 0.24f * ppm), Size(4.2f * ppm, 0.48f * ppm))
        drawOval(Color.Black.copy(alpha = 0.38f), Offset(txScreen - 1.55f * ppm, tractorGroundY - 0.14f * ppm), Size(3.1f * ppm, 0.28f * ppm))
        if (world.hasTrailer) {
            val trGroundY = worldToScreenY(world.terrain.heightAt(world.trX))
            val trScreenX = worldToScreenX(world.trX)
            val halfSh = world.trailer.bedLength * 0.58f * ppm
            drawOval(Color.Black.copy(alpha = 0.20f), Offset(trScreenX - halfSh, trGroundY - 0.20f * ppm), Size(halfSh * 2f, 0.40f * ppm))
            drawOval(Color.Black.copy(alpha = 0.34f), Offset(trScreenX - halfSh * 0.72f, trGroundY - 0.12f * ppm), Size(halfSh * 1.44f, 0.24f * ppm))
        }

        // 7. Zero-Allocation Soft Diesel Exhaust Plumes, Dust & Kichad Mud Splash
        for (p in world.particles) {
            val sx = worldToScreenX(p.x)
            val sy = worldToScreenY(p.y)
            val lifeFrac = (p.life / p.maxLife).coerceIn(0f, 1f)
            val rad = p.size * ppm
            when (p.kind) {
                0 -> {
                    val sootShade = (0.30f - p.darkSoot * 0.22f).coerceIn(0.06f, 0.35f)
                    val smokeCol = Color(sootShade, sootShade, sootShade + 0.02f)
                    drawCircle(smokeCol.copy(alpha = lifeFrac * 0.18f), rad * 1.75f, Offset(sx, sy))
                    drawCircle(smokeCol.copy(alpha = lifeFrac * 0.40f), rad * 1.15f, Offset(sx, sy))
                    drawCircle(smokeCol.copy(alpha = lifeFrac * 0.58f), rad * 0.65f, Offset(sx, sy))
                }
                1 -> {
                    drawCircle(Color(0xFF9C7A52).copy(alpha = lifeFrac * 0.20f), rad * 1.40f, Offset(sx, sy))
                    drawCircle(Color(0xFF9C7A52).copy(alpha = lifeFrac * 0.38f), rad * 0.85f, Offset(sx, sy))
                }
                2 -> {
                    drawCircle(Color(0xFF2D190A).copy(alpha = lifeFrac * 0.88f), rad, Offset(sx, sy))
                    drawCircle(Color(0xFF4B2D15).copy(alpha = lifeFrac * 0.62f), rad * 0.55f, Offset(sx - rad * 0.18f, sy - rad * 0.18f))
                }
                3 -> drawCircle(Color(0xFFA9D6EA).copy(alpha = lifeFrac * 0.74f), rad, Offset(sx, sy))
                else -> drawCircle(Color(0xFFFFD54F).copy(alpha = lifeFrac * 0.88f), rad, Offset(sx, sy))
            }
        }

        // 8. Detailed Trailer + Hydraulic Cylinder + Straight Drawbar + Physical Cargo
        if (world.hasTrailer) {
            drawTrailerWithCargo(
                trailer = world.trailer,
                cargo = world.cargo,
                pieces = world.cargoPieces,
                trailerWheel = assets.trailerWheel,
                centerScreen = Offset(worldToScreenX(world.trX), worldToScreenY(world.trY)),
                angleRad = world.trAngle,
                dumpAngleRad = world.trDumpAngle,
                wheelComp = world.trWheelCompression,
                wheelRot = world.trWheelRot,
                tongueLocalX = world.tongueLocalX,
                tongueLocalY = world.tongueLocalY,
                ppm = ppm
            )
        }

        // 9. Realistic Tractor Sprite + Rotating Tyre Sprites + Side-View Real-Face Driver + Mud Splatter
        drawRealisticTractorWithDriver(
            tractor = world.tractor,
            driver = world.driver,
            assets = assets,
            centerScreen = Offset(worldToScreenX(world.x), worldToScreenY(world.y)),
            angleRad = world.angle,
            rearComp = world.rearWheelCompression,
            frontComp = world.frontWheelCompression,
            rearWheelRot = world.rearWheelRot,
            frontWheelRot = world.frontWheelRot,
            driverLean = world.driverLean,
            headlightsOn = world.map.isNightOrDusk,
            ppm = ppm,
            mudCoat = world.chassisMudCoat,
            bogSprayIntensity = if (world.mudBogLevel > 0.1f || world.tyreSlipRatio > 0.35f) (world.tyreSlipRatio * 0.8f).coerceIn(0f, 1f) else 0f,
            timeSec = timeSec
        )

        // 9.5 Mechanical Tractor-Trolley Coupling Joint (Clevis Hook + Tow Eye + Steel Hitch Pin + Safety Chain)
        if (world.hasTrailer) {
            val cosT = cos(world.angle)
            val sinT = sin(world.angle)
            val hWx = world.x + world.hitchLocalX * cosT - world.hitchLocalY * sinT
            val hWy = world.y + world.hitchLocalX * sinT + world.hitchLocalY * cosT

            val cosTr = cos(world.trAngle)
            val sinTr = sin(world.trAngle)
            val tWx = world.trX + world.tongueLocalX * cosTr - world.tongueLocalY * sinTr
            val tWy = world.trY + world.tongueLocalX * sinTr + world.tongueLocalY * cosTr

            drawTractorTrailerHitchJoint(
                hitchScreen = Offset(worldToScreenX(hWx), worldToScreenY(hWy)),
                tongueScreen = Offset(worldToScreenX(tWx), worldToScreenY(tWy)),
                tractorAngleRad = world.angle,
                trailerAngleRad = world.trAngle,
                ppm = ppm
            )
        }

        // 10. Monsoon Rain
        if (world.map.hasRain) {
            val rainColor = Color(0xFFD0EAFF).copy(alpha = 0.42f)
            for (d in world.rainDrops) {
                val rx = d.nx * w
                val ry = d.ny * h
                drawLine(rainColor, Offset(rx, ry), Offset(rx - 12f, ry + 32f), strokeWidth = 1.8f)
            }
        }
    }

    fun DrawScope.renderGaragePreview(
        tractor: TractorModel,
        driver: DriverCharacter,
        trailer: TrailerType,
        cargo: CargoType,
        assets: TractorArtAssets,
        animTime: Float
    ) {
        val w = size.width
        val h = size.height
        val ppm = (h / 3.6f).coerceIn(48f, 112f)

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF151916), Color(0xFF212823), Color(0xFF131614))
            )
        )

        val groundY = h * 0.80f
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF2F3437), Color(0xFF1A1D1F))),
            topLeft = Offset(0f, groundY),
            size = Size(w, h - groundY)
        )
        drawRect(
            brush = assets.textures.roadGritBrush,
            topLeft = Offset(0f, groundY),
            size = Size(w, h - groundY),
            alpha = 0.45f
        )
        drawLine(Color(0xFFD69122).copy(alpha = 0.5f), Offset(0f, groundY), Offset(w, groundY), strokeWidth = 3f)

        val idleBob = sin(animTime * 16f) * 0.008f
        val tractorCenterX = if (trailer == TrailerType.NONE) w * 0.52f else w * 0.67f
        val tractorCenterY = groundY - (tractor.rearWheelRadius + 0.31f + idleBob) * ppm

        drawOval(Color.Black.copy(alpha = 0.42f), Offset(tractorCenterX - 1.9f * ppm, groundY - 0.16f * ppm), Size(3.8f * ppm, 0.32f * ppm))

        var previewHitchPos: Offset? = null
        var previewTonguePos: Offset? = null
        if (trailer != TrailerType.NONE) {
            val m = metricsFor(tractor)
            val wb = tractor.wheelBase
            val rearAnchorLx = -wb * 0.44f
            val frontAnchorLx = wb * 0.54f
            val spriteW = (frontAnchorLx - rearAnchorLx) / (m.frontAxleNx - m.rearAxleNx)
            val spriteH = spriteW * (480f / 860f)
            val restLen = 0.26f
            val rearAxleRestY = 0.05f - restLen
            val hitchLx = rearAnchorLx + (m.hitchNx - m.rearAxleNx) * spriteW
            val hitchLy = rearAxleRestY + (m.rearAxleNy - m.hitchNy) * spriteH

            val tongueLx = trailer.bedLength * 0.50f + 0.68f
            val restTractorY = 0.05f + tractor.rearWheelRadius + restLen
            val restTrailerY = 0.05f + trailer.wheelRadius + restLen
            val tongueLy = ((restTractorY + hitchLy) - restTrailerY).coerceIn(-0.22f, 0.25f)

            val trailerCenterX = tractorCenterX + (hitchLx - tongueLx) * ppm
            val trailerCenterY = groundY - (trailer.wheelRadius + 0.31f) * ppm
            previewHitchPos = Offset(tractorCenterX + hitchLx * ppm, tractorCenterY - hitchLy * ppm)
            previewTonguePos = Offset(trailerCenterX + tongueLx * ppm, trailerCenterY - tongueLy * ppm)

            val previewPieces = if (cargo == CargoType.NONE) emptyList() else {
                val cols = (trailer.capacityPieces + 1) / 2
                val usableLen = trailer.bedLength * 0.76f
                List(trailer.capacityPieces) { i ->
                    CargoPiece(
                        lx = -usableLen * 0.5f + ((i % cols) + 0.5f) * (usableLen / cols),
                        ly = 0.34f + (i / cols) * 0.28f
                    )
                }
            }
            drawTrailerWithCargo(
                trailer = trailer,
                cargo = cargo,
                pieces = previewPieces,
                trailerWheel = assets.trailerWheel,
                centerScreen = Offset(trailerCenterX, trailerCenterY),
                angleRad = 0f,
                dumpAngleRad = 0f,
                wheelComp = 0.26f,
                wheelRot = animTime * 0.6f,
                tongueLocalX = tongueLx,
                tongueLocalY = tongueLy,
                ppm = ppm
            )
        }

        drawRealisticTractorWithDriver(
            tractor = tractor,
            driver = driver,
            assets = assets,
            centerScreen = Offset(tractorCenterX, tractorCenterY),
            angleRad = 0f,
            rearComp = 0.26f + idleBob,
            frontComp = 0.26f + idleBob,
            rearWheelRot = animTime * 0.6f,
            frontWheelRot = animTime * 1.0f,
            driverLean = 0f,
            headlightsOn = true,
            ppm = ppm
        )

        if (previewHitchPos != null && previewTonguePos != null) {
            drawTractorTrailerHitchJoint(
                hitchScreen = previewHitchPos,
                tongueScreen = previewTonguePos,
                tractorAngleRad = 0f,
                trailerAngleRad = 0f,
                ppm = ppm
            )
        }
    }

    private fun DrawScope.drawRealisticTractorWithDriver(
        tractor: TractorModel,
        driver: DriverCharacter,
        assets: TractorArtAssets,
        centerScreen: Offset,
        angleRad: Float,
        rearComp: Float,
        frontComp: Float,
        rearWheelRot: Float,
        frontWheelRot: Float,
        driverLean: Float,
        headlightsOn: Boolean,
        ppm: Float,
        mudCoat: Float = 0f,
        bogSprayIntensity: Float = 0f,
        timeSec: Float = 0f
    ) {
        val m = metricsFor(tractor)
        val deg = (-angleRad * 180f / PI.toFloat())
        val wb = tractor.wheelBase * ppm
        val rearX = -wb * 0.44f
        val frontX = wb * 0.54f

        val rearAnchorY = 0.05f * ppm
        val frontAnchorY = (0.05f + (tractor.rearWheelRadius - tractor.frontWheelRadius)) * ppm
        val rearWheelY = rearAnchorY + rearComp * ppm
        val frontWheelY = frontAnchorY + frontComp * ppm

        val spriteW = (frontX - rearX) / (m.frontAxleNx - m.rearAxleNx)
        val spriteH = spriteW * (assets.body.height.toFloat() / assets.body.width.toFloat())
        val spriteLeft = rearX - m.rearAxleNx * spriteW
        val bodySuspOffset = ((rearComp - 0.26f) + (frontComp - 0.26f)) * 0.5f * ppm
        val spriteTop = (rearAnchorY + 0.26f * ppm + bodySuspOffset) - m.rearAxleNy * spriteH

        val seatScreen = Offset(spriteLeft + m.seatNx * spriteW, spriteTop + m.seatNy * spriteH)
        val footScreen = Offset(spriteLeft + m.footNx * spriteW, spriteTop + m.footNy * spriteH)
        val gripScreen = Offset(spriteLeft + m.gripNx * spriteW, spriteTop + m.gripNy * spriteH)

        withTransform({
            translate(centerScreen.x, centerScreen.y)
            rotate(degrees = deg, pivot = Offset.Zero)
        }) {
            // 1. Suspension Coil Struts & Mechanical Footstep Link
            drawLine(Color(0xFF32363B), Offset(rearX, rearAnchorY), Offset(rearX, rearWheelY), strokeWidth = ppm * 0.11f, cap = StrokeCap.Round)
            drawLine(Color(0xFF42484F), Offset(frontX, frontAnchorY), Offset(frontX, frontWheelY), strokeWidth = ppm * 0.09f, cap = StrokeCap.Round)

            // 2. Driver Legs & Far Arm drawn FIRST behind wheels & tractor chassis/mudguards
            // Foot lands cleanly in the tractor footwell ahead of the rear wheel arch, NEVER outside or over top of tyres!
            drawRealisticDriverLegs(
                driver = driver,
                seatPos = seatScreen,
                footPos = footScreen,
                wheelGripPos = gripScreen,
                leanRad = driverLean,
                ppm = ppm
            )

            // 3. Dark matte inner fender-well liner + Rotating Concentric Rear & Front Lug Wheels (behind opaque mudguards!)
            val rearLinerRad = tractor.rearWheelRadius * ppm * 1.045f
            drawArc(
                color = Color(0xFF15181C),
                startAngle = 188f,
                sweepAngle = 164f,
                useCenter = true,
                topLeft = Offset(rearX - rearLinerRad, rearWheelY - rearLinerRad),
                size = Size(rearLinerRad * 2f, rearLinerRad * 2f)
            )
            drawWheelSprite(
                bitmap = assets.rearWheel,
                center = Offset(rearX, rearWheelY),
                radius = tractor.rearWheelRadius * ppm * 1.015f,
                rotationRad = rearWheelRot
            )
            drawWheelSprite(
                bitmap = assets.frontWheel,
                center = Offset(frontX, frontWheelY),
                radius = tractor.frontWheelRadius * ppm * 1.015f,
                rotationRad = frontWheelRot
            )

            // Subtle rotating lug-nut hub ring highlights on wheels for extra mechanical realism
            drawCircle(Color(0xFF282D32), tractor.rearWheelRadius * ppm * 0.14f, Offset(rearX, rearWheelY))
            drawCircle(Color(0xFF9AA3AD), tractor.rearWheelRadius * ppm * 0.07f, Offset(rearX, rearWheelY))
            drawCircle(Color(0xFF282D32), tractor.frontWheelRadius * ppm * 0.16f, Offset(frontX, frontWheelY))
            drawCircle(Color(0xFF9AA3AD), tractor.frontWheelRadius * ppm * 0.08f, Offset(frontX, frontWheelY))

            // Dynamic kichad mud churn arc around rear tyre bottom when slipping in mud
            if (bogSprayIntensity > 0.15f) {
                val rMud = tractor.rearWheelRadius * ppm
                val flicker = 0.72f + 0.28f * abs(sin(timeSec * 28f))
                drawArc(
                    color = Color(0xFF331D0B).copy(alpha = (0.68f * bogSprayIntensity * flicker).coerceIn(0f, 0.85f)),
                    startAngle = 25f,
                    sweepAngle = 145f,
                    useCenter = false,
                    topLeft = Offset(rearX - rMud * 1.03f, rearWheelY - rMud * 1.03f),
                    size = Size(rMud * 2.06f, rMud * 2.06f),
                    style = Stroke(width = 0.13f * ppm, cap = StrokeCap.Round)
                )
            }

            // 4. Solid Opaque High-Detail Tractor Body Sprite (mudguards & footwell guard cover thighs & wheel tops cleanly!)
            drawImage(
                image = assets.body,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(assets.body.width, assets.body.height),
                dstOffset = IntOffset(spriteLeft.roundToInt(), spriteTop.roundToInt()),
                dstSize = IntSize(spriteW.roundToInt(), spriteH.roundToInt())
            )

            // Kichad Mud Splatter Coating on Mudguard Lip & Lower Chassis when driving through mud
            if (mudCoat > 0.08f) {
                val splatterCol = Color(0xFF38210E).copy(alpha = (mudCoat * 0.78f).coerceIn(0f, 0.80f))
                val rM = tractor.rearWheelRadius * ppm
                for (sp in 0..4) {
                    val sx = rearX - rM * (0.35f + sp * 0.16f)
                    val sy = rearWheelY - rM * (0.28f - sp * 0.12f)
                    drawCircle(splatterCol, radius = (0.045f + (sp % 2) * 0.025f) * ppm, center = Offset(sx, sy))
                }
            }

            // Mechanical Tractor Detailing: Rear Rubber Mudflap + Reflector + Tail-Lamp Glow + Headlight Glass
            val flapX = spriteLeft + (m.rearAxleNx - 0.145f) * spriteW
            val flapTopY = spriteTop + (m.rearAxleNy + 0.03f) * spriteH
            drawRect(Color(0xFF16191D), Offset(flapX - 0.045f * ppm, flapTopY), Size(0.085f * ppm, 0.24f * ppm))
            drawRect(Color(0xFFD92B2B), Offset(flapX - 0.030f * ppm, flapTopY + 0.14f * ppm), Size(0.055f * ppm, 0.055f * ppm))

            val rearLampPos = Offset(spriteLeft + m.rearLampNx * spriteW, spriteTop + m.rearLampNy * spriteH)
            drawCircle(Color(0xFFE53935).copy(alpha = 0.55f), 0.075f * ppm, rearLampPos)
            drawCircle(Color(0xFFFF8A65), 0.035f * ppm, rearLampPos)

            // Steering wheel spoke detail right at grip position
            drawLine(Color(0xFF1A1D21), Offset(gripScreen.x - 0.05f * ppm, gripScreen.y + 0.08f * ppm), Offset(gripScreen.x + 0.04f * ppm, gripScreen.y - 0.03f * ppm), strokeWidth = 0.042f * ppm, cap = StrokeCap.Round)

            // 5. Seated Side-View Driver Upper Layer (profile torso, belt, collar, side-view face & ear, near arm gripping wheel)
            drawRealisticDriverUpper(
                driver = driver,
                faceBitmap = assets.face,
                seatPos = seatScreen,
                wheelGripPos = gripScreen,
                leanRad = driverLean,
                hasCanopy = tractor == TractorModel.SONALIKA_750,
                ppm = ppm
            )

            // 6. Headlight Glow & Beam Cone on Dusk / Rainy Maps
            val hlPos = Offset(spriteLeft + m.headlightNx * spriteW, spriteTop + m.headlightNy * spriteH)
            drawCircle(Color(0xFFFFF8D6).copy(alpha = if (headlightsOn) 0.72f else 0.22f), 0.07f * ppm, hlPos)
            if (headlightsOn) {
                conePath.reset()
                conePath.moveTo(hlPos.x, hlPos.y)
                conePath.lineTo(hlPos.x + 3.4f * ppm, hlPos.y - 0.65f * ppm)
                conePath.lineTo(hlPos.x + 3.4f * ppm, hlPos.y + 0.85f * ppm)
                conePath.close()
                drawPath(conePath, Color(0xFFFFF5C8).copy(alpha = 0.22f))
            }
        }
    }

    private fun DrawScope.drawWheelSprite(
        bitmap: ImageBitmap,
        center: Offset,
        radius: Float,
        rotationRad: Float
    ) {
        val deg = rotationRad * 180f / PI.toFloat()
        val rawPx = (radius * 2f).roundToInt().coerceAtLeast(8)
        val sizePx = if (rawPx % 2 == 0) rawPx else rawPx + 1
        withTransform({
            translate(center.x, center.y)
            rotate(degrees = deg, pivot = Offset.Zero)
        }) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(-sizePx / 2, -sizePx / 2),
                dstSize = IntSize(sizePx, sizePx)
            )
        }
    }

    private fun DrawScope.drawRealisticDriverLegs(
        driver: DriverCharacter,
        seatPos: Offset,
        footPos: Offset,
        wheelGripPos: Offset,
        leanRad: Float,
        ppm: Float
    ) {
        val leanDeg = leanRad * 180f / PI.toFloat()
        val pantsShadow = Color(
            red = driver.pantsColor.red * 0.62f,
            green = driver.pantsColor.green * 0.62f,
            blue = driver.pantsColor.blue * 0.62f
        )
        val farShirt = Color(
            red = driver.shirtPrimary.red * 0.60f,
            green = driver.shirtPrimary.green * 0.60f,
            blue = driver.shirtPrimary.blue * 0.60f
        )

        // Exact cockpit knee & footwell coordinates relative to seat (keeps shins & boots ahead of rear wheel!)
        val rawFootDx = footPos.x - seatPos.x
        val rawFootDy = footPos.y - seatPos.y
        val kneeOffset = Offset(rawFootDx * 0.72f, rawFootDy * 0.14f)
        val ankleOffset = Offset(rawFootDx * 0.94f, rawFootDy * 0.96f)

        withTransform({
            translate(seatPos.x, seatPos.y)
            rotate(leanDeg * 0.35f, pivot = Offset.Zero)
        }) {
            // Far leg angled into opposite clutch pedal inside footwell
            drawLine(pantsShadow, Offset(0.03f * ppm, 0.03f * ppm), Offset(kneeOffset.x + 0.05f * ppm, kneeOffset.y - 0.02f * ppm), strokeWidth = 0.145f * ppm, cap = StrokeCap.Round)
            drawLine(pantsShadow, Offset(kneeOffset.x + 0.05f * ppm, kneeOffset.y - 0.02f * ppm), Offset(ankleOffset.x + 0.06f * ppm, ankleOffset.y - 0.02f * ppm), strokeWidth = 0.130f * ppm, cap = StrokeCap.Round)

            // Near thigh & shin tucked naturally inside tractor seat & footwell ahead of rear tyre
            drawLine(driver.pantsColor, Offset(-0.02f * ppm, 0.03f * ppm), kneeOffset, strokeWidth = 0.170f * ppm, cap = StrokeCap.Round)
            drawLine(driver.pantsColor, kneeOffset, ankleOffset, strokeWidth = 0.145f * ppm, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0.04f * ppm, 0.00f), Offset(kneeOffset.x * 0.85f, kneeOffset.y - 0.02f * ppm), strokeWidth = 0.022f * ppm, cap = StrokeCap.Round)

            // Work boot resting squarely inside tractor footboard pedal
            drawRoundRect(
                color = Color(0xFF281E15),
                topLeft = Offset(ankleOffset.x - 0.04f * ppm, ankleOffset.y - 0.02f * ppm),
                size = Size(0.165f * ppm, 0.085f * ppm),
                cornerRadius = CornerRadius(0.032f * ppm)
            )
            drawLine(Color(0xFF4B3A2A), Offset(ankleOffset.x - 0.03f * ppm, ankleOffset.y + 0.060f * ppm), Offset(ankleOffset.x + 0.12f * ppm, ankleOffset.y + 0.060f * ppm), strokeWidth = 0.020f * ppm)

            // Far (left) arm reaching forward to grip left side of steering wheel rim
            val gx = wheelGripPos.x - seatPos.x + 0.05f * ppm
            val gy = wheelGripPos.y - seatPos.y - 0.03f * ppm
            val fShoulder = Offset(0.06f * ppm, -0.34f * ppm)
            val fElbow = Offset(gx * 0.50f + 0.04f * ppm, gy * 0.42f + 0.08f * ppm)
            drawLine(farShirt, fShoulder, fElbow, strokeWidth = 0.11f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFFA77555), fElbow, Offset(gx, gy), strokeWidth = 0.082f * ppm, cap = StrokeCap.Round)
            drawCircle(Color(0xFF9C6B4C), 0.050f * ppm, Offset(gx, gy))
        }
    }

    private fun DrawScope.drawRealisticDriverUpper(
        driver: DriverCharacter,
        faceBitmap: ImageBitmap?,
        seatPos: Offset,
        wheelGripPos: Offset,
        leanRad: Float,
        hasCanopy: Boolean,
        ppm: Float
    ) {
        val leanDeg = leanRad * 180f / PI.toFloat() + 12f
        val shirtDark = Color(
            red = driver.shirtPrimary.red * 0.70f,
            green = driver.shirtPrimary.green * 0.70f,
            blue = driver.shirtPrimary.blue * 0.70f
        )
        val scale = if (hasCanopy) 0.84f else 0.94f
        withTransform({
            translate(seatPos.x, seatPos.y)
            rotate(leanDeg, pivot = Offset.Zero)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // Side-view seated hip/pelvis resting directly on seat cushion + leather belt
            drawRoundRect(
                color = driver.pantsColor,
                topLeft = Offset(-0.13f * ppm, -0.02f * ppm),
                size = Size(0.23f * ppm, 0.10f * ppm),
                cornerRadius = CornerRadius(0.05f * ppm)
            )
            drawRect(Color(0xFF3E2614), Offset(-0.13f * ppm, -0.028f * ppm), Size(0.22f * ppm, 0.040f * ppm))
            drawRect(Color(0xFFD2A343), Offset(0.055f * ppm, -0.028f * ppm), Size(0.036f * ppm, 0.040f * ppm))

            // True side-profile shirt torso (curved spine on left -X, chest & button placket along +X)
            torsoPath.reset()
            torsoPath.moveTo(-0.12f * ppm, -0.01f * ppm)
            torsoPath.quadraticTo(-0.17f * ppm, -0.22f * ppm, -0.13f * ppm, -0.39f * ppm)
            torsoPath.quadraticTo(-0.02f * ppm, -0.49f * ppm, 0.10f * ppm, -0.40f * ppm)
            torsoPath.quadraticTo(0.14f * ppm, -0.20f * ppm, 0.10f * ppm, -0.01f * ppm)
            torsoPath.close()
            drawPath(torsoPath, driver.shirtPrimary)
            // Back shadow fold along spine side
            drawLine(shirtDark.copy(alpha = 0.55f), Offset(-0.11f * ppm, -0.05f * ppm), Offset(-0.12f * ppm, -0.36f * ppm), strokeWidth = 0.055f * ppm, cap = StrokeCap.Round)
            // Front button seam & pocket
            drawLine(driver.shirtAccent, Offset(0.095f * ppm, -0.39f * ppm), Offset(0.090f * ppm, -0.03f * ppm), strokeWidth = 0.022f * ppm)
            drawRoundRect(
                color = shirtDark.copy(alpha = 0.48f),
                topLeft = Offset(0.022f * ppm, -0.31f * ppm),
                size = Size(0.058f * ppm, 0.072f * ppm),
                cornerRadius = CornerRadius(0.012f * ppm)
            )

            // Side-view collar
            collarPath.reset()
            collarPath.moveTo(-0.07f * ppm, -0.44f * ppm)
            collarPath.lineTo(0.04f * ppm, -0.37f * ppm)
            collarPath.lineTo(0.11f * ppm, -0.41f * ppm)
            collarPath.lineTo(0.03f * ppm, -0.47f * ppm)
            collarPath.close()
            drawPath(collarPath, driver.shirtPrimary)
            drawPath(collarPath, shirtDark.copy(alpha = 0.55f), style = Stroke(width = 1.6f))

            // Neck angled forward toward road (+X)
            drawRoundRect(
                color = Color(0xFFC28E6B),
                topLeft = Offset(-0.020f * ppm, -0.54f * ppm),
                size = Size(0.100f * ppm, 0.13f * ppm),
                cornerRadius = CornerRadius(0.03f * ppm)
            )

            // True Side-View Head Silhouette: contoured back skull + cropped 3/4-profile face facing +X + prominent side ear & hair crown!
            val headCenter = Offset(0.055f * ppm, -0.66f * ppm)
            val headRx = 0.145f * ppm
            val headRy = 0.185f * ppm

            // Back-of-head & nape hair contour (-X skull)
            backHairPath.reset()
            backHairPath.moveTo(headCenter.x - headRx * 0.30f, headCenter.y + headRy * 0.72f)
            backHairPath.quadraticTo(headCenter.x - headRx * 1.45f, headCenter.y + headRy * 0.25f, headCenter.x - headRx * 1.25f, headCenter.y - headRy * 0.55f)
            backHairPath.quadraticTo(headCenter.x - headRx * 0.40f, headCenter.y - headRy * 1.22f, headCenter.x + headRx * 0.85f, headCenter.y - headRy * 0.85f)
            backHairPath.quadraticTo(headCenter.x + headRx * 0.35f, headCenter.y - headRy * 0.45f, headCenter.x - headRx * 0.15f, headCenter.y - headRy * 0.25f)
            backHairPath.close()
            drawPath(backHairPath, Color(0xFF1A1512))

            if (faceBitmap != null) {
                // Crop tight interior face region (skipping background walls/sky corners) and bias toward forward-facing right half!
                val fw = faceBitmap.width
                val fh = faceBitmap.height
                val srcOff = IntOffset((fw * 0.26f).roundToInt(), (fh * 0.14f).roundToInt())
                val srcSz = IntSize((fw * 0.56f).roundToInt(), (fh * 0.76f).roundToInt())

                faceOvalPath.reset()
                faceOvalPath.addOval(
                    Rect(
                        headCenter.x - headRx * 0.65f,
                        headCenter.y - headRy * 0.96f,
                        headCenter.x + headRx * 1.12f,
                        headCenter.y + headRy * 0.98f
                    )
                )
                clipPath(faceOvalPath) {
                    // Draw shifted forward so the nose/eye/jaw align naturally looking ahead (+X)
                    drawImage(
                        image = faceBitmap,
                        srcOffset = srcOff,
                        srcSize = srcSz,
                        dstOffset = IntOffset((headCenter.x - headRx * 0.85f).roundToInt(), (headCenter.y - headRy * 0.98f).roundToInt()),
                        dstSize = IntSize((headRx * 2.02f).roundToInt(), (headRy * 1.98f).roundToInt())
                    )
                }
            } else {
                drawOval(
                    color = Color(0xFFC9936F),
                    topLeft = Offset(headCenter.x - headRx * 0.65f, headCenter.y - headRy * 0.96f),
                    size = Size(headRx * 1.77f, headRy * 1.94f)
                )
            }

            // Crisp sideburn + contoured side-profile ear blending back skull to cheek
            drawRoundRect(
                color = Color(0xFF1A1512),
                topLeft = Offset(headCenter.x - headRx * 0.82f, headCenter.y - headRy * 0.42f),
                size = Size(headRx * 0.42f, headRy * 0.55f),
                cornerRadius = CornerRadius(0.025f * ppm)
            )
            drawOval(
                color = Color(0xFFC7906C),
                topLeft = Offset(headCenter.x - headRx * 0.72f, headCenter.y - headRy * 0.10f),
                size = Size(headRx * 0.36f, headRy * 0.46f)
            )
            drawOval(
                color = Color(0xFF8C5A3B).copy(alpha = 0.52f),
                topLeft = Offset(headCenter.x - headRx * 0.64f, headCenter.y + headRy * 0.02f),
                size = Size(headRx * 0.20f, headRy * 0.26f)
            )

            // Near (right) arm in true side profile: rolled-up sleeve from shoulder -> bent elbow -> hand on steering wheel
            val rawGx = (wheelGripPos.x - seatPos.x) / scale
            val rawGy = (wheelGripPos.y - seatPos.y) / scale
            val invLeanRad = -leanDeg * (PI.toFloat() / 180f)
            val cosL = cos(invLeanRad)
            val sinL = sin(invLeanRad)
            val gx = rawGx * cosL - rawGy * sinL
            val gy = rawGx * sinL + rawGy * cosL
            val shoulder = Offset(-0.01f * ppm, -0.35f * ppm)
            val elbow = Offset(gx * 0.48f - 0.02f * ppm, gy * 0.40f + 0.11f * ppm)
            drawLine(driver.shirtPrimary, shoulder, elbow, strokeWidth = 0.130f * ppm, cap = StrokeCap.Round)
            drawCircle(shirtDark, 0.066f * ppm, shoulder)
            drawCircle(driver.shirtAccent.copy(alpha = 0.65f), 0.058f * ppm, elbow)
            drawLine(Color(0xFFC8926E), elbow, Offset(gx, gy), strokeWidth = 0.088f * ppm, cap = StrokeCap.Round)
            val wristX = elbow.x + (gx - elbow.x) * 0.80f
            val wristY = elbow.y + (gy - elbow.y) * 0.80f
            drawCircle(Color(0xFFD6DCE2), 0.045f * ppm, Offset(wristX, wristY))
            drawOval(
                color = Color(0xFFB9805B),
                topLeft = Offset(gx - 0.064f * ppm, gy - 0.050f * ppm),
                size = Size(0.128f * ppm, 0.102f * ppm)
            )
            drawCircle(Color(0xFF9C6846), 0.038f * ppm, Offset(gx + 0.025f * ppm, gy + 0.01f * ppm))
        }
    }

    private fun DrawScope.drawTrailerWithCargo(
        trailer: TrailerType,
        cargo: CargoType,
        pieces: List<CargoPiece>,
        trailerWheel: ImageBitmap,
        centerScreen: Offset,
        angleRad: Float,
        dumpAngleRad: Float,
        wheelComp: Float,
        wheelRot: Float,
        tongueLocalX: Float,
        tongueLocalY: Float,
        ppm: Float
    ) {
        val deg = (-angleRad * 180f / PI.toFloat())
        val len = trailer.bedLength * ppm
        val bedH = trailer.bedHeight * ppm
        val wheelX = -len * 0.08f
        val wheelY = (0.05f + wheelComp) * ppm

        withTransform({
            translate(centerScreen.x, centerScreen.y)
            rotate(deg, pivot = Offset.Zero)
        }) {
            // Completely straight horizontal Heavy-Duty Steel Drawbar (Tow Tongue) directly into raised tractor hook!
            val tongueEnd = Offset(tongueLocalX * ppm, -tongueLocalY * ppm)
            val lowerRoot = Offset(len * 0.42f, tongueEnd.y)
            val upperBraceRoot = Offset(len * 0.48f, -0.22f * ppm)
            val neckMid = Offset(tongueEnd.x - 0.18f * ppm, tongueEnd.y)

            drawLine(Color(0xFF1F2329), Offset(len * 0.45f, -0.04f * ppm), lowerRoot, strokeWidth = 0.11f * ppm, cap = StrokeCap.Round)

            gussetPath.reset()
            gussetPath.moveTo(lowerRoot.x, lowerRoot.y)
            gussetPath.lineTo(upperBraceRoot.x, upperBraceRoot.y)
            gussetPath.lineTo(neckMid.x, neckMid.y)
            gussetPath.close()
            drawPath(gussetPath, Color(0xFF23272D))

            // Main straight horizontal lower C-channel drawbar arm + diagonal upper brace + bright steel highlight
            drawLine(Color(0xFF16181C), lowerRoot, tongueEnd, strokeWidth = 0.135f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF454C56), lowerRoot, tongueEnd, strokeWidth = 0.075f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF1E2228), upperBraceRoot, neckMid, strokeWidth = 0.105f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF535B66), upperBraceRoot, neckMid, strokeWidth = 0.048f * ppm, cap = StrokeCap.Round)

            // Hydraulic hose running neatly along drawbar neck into trailer underbody
            drawLine(Color(0xFF0E1012), Offset(len * 0.46f, -0.08f * ppm), Offset(tongueEnd.x - 0.06f * ppm, tongueEnd.y - 0.045f * ppm), strokeWidth = 0.025f * ppm)

            // Tucked parking prop stand under the straight drawbar
            val standTop = Offset(len * 0.50f + 0.16f * ppm, tongueEnd.y + 0.02f * ppm)
            val standFoot = Offset(standTop.x - 0.12f * ppm, standTop.y + 0.15f * ppm)
            drawLine(Color(0xFF2D3138), standTop, standFoot, strokeWidth = 0.06f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF69727D), Offset(standFoot.x - 0.06f * ppm, standFoot.y), Offset(standFoot.x + 0.06f * ppm, standFoot.y), strokeWidth = 0.045f * ppm, cap = StrokeCap.Round)

            // Hydraulic tipping ram cylinder housing + leaf-spring suspension hanger & bolts
            drawLine(Color(0xFF22262C), Offset(0.18f * ppm, 0.02f * ppm), Offset(0.08f * ppm, -0.10f * ppm), strokeWidth = 0.10f * ppm, cap = StrokeCap.Round)
            if (dumpAngleRad > 0.02f) {
                drawLine(Color(0xFFC8D0D8), Offset(0.12f * ppm, -0.06f * ppm), Offset(0.24f * ppm, -0.5f * ppm * dumpAngleRad * 2f), strokeWidth = 0.085f * ppm)
            }
            // Leaf-spring shackle & axle strut
            drawLine(Color(0xFF3A3F45), Offset(wheelX - 0.25f * ppm, -0.04f * ppm), Offset(wheelX + 0.25f * ppm, -0.04f * ppm), strokeWidth = 0.055f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF2E3339), Offset(wheelX, -0.04f * ppm), Offset(wheelX, wheelY), strokeWidth = 0.12f * ppm, cap = StrokeCap.Round)

            // Steel C-channel trailer chassis beam + rivet/bolt line + rear mudflap
            drawRect(Color(0xFF1D2126), Offset(-len * 0.48f, -0.11f * ppm), Size(len * 1.02f, 0.14f * ppm))
            drawRect(Color(0xFF454B52), Offset(-len * 0.48f, -0.11f * ppm), Size(len * 1.02f, 0.055f * ppm))
            for (b in 0..5) {
                val bx = -len * 0.40f + b * (len * 0.16f)
                drawCircle(Color(0xFF98A0AA), 0.018f * ppm, Offset(bx, -0.05f * ppm))
            }
            // Trolley wheel protective steel mudguard arch & rubber mudflap
            val trRad = trailer.wheelRadius * ppm
            drawArc(
                color = Color(0xFF202429),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(wheelX - trRad * 1.12f, wheelY - trRad * 1.12f),
                size = Size(trRad * 2.24f, trRad * 2.24f),
                style = Stroke(width = 0.065f * ppm, cap = StrokeCap.Round)
            )
            drawRect(Color(0xFF14171A), Offset(wheelX - trRad * 1.16f, wheelY - 0.04f * ppm), Size(0.08f * ppm, 0.24f * ppm))

            // Tiltable Steel Cargo Bed with Lower Solid Sideboard + Open Top Cage so ALL Cargo is Clearly Visible!
            val dumpDeg = -dumpAngleRad * 180f / PI.toFloat()
            withTransform({
                rotate(dumpDeg, pivot = Offset(-len * 0.45f, -0.08f * ppm))
            }) {
                val lowerWallH = bedH * 0.46f
                val floorY = -0.12f * ppm

                drawRect(Color(0xFF17181A), Offset(-len * 0.47f, floorY - bedH * 0.90f), Size(len * 0.94f, bedH * 0.90f))

                val hasActiveCargo = pieces.any { !it.spilled } && cargo != CargoType.NONE
                if (hasActiveCargo) {
                    heapPath.reset()
                    heapPath.moveTo(-len * 0.46f, floorY)
                    heapPath.quadraticTo(-len * 0.24f, floorY - bedH * 1.48f, 0f, floorY - bedH * 1.58f)
                    heapPath.quadraticTo(len * 0.24f, floorY - bedH * 1.42f, len * 0.46f, floorY)
                    heapPath.close()
                    val (topCol, botCol) = when (cargo) {
                        CargoType.SAND -> Color(0xFFE5C887) to Color(0xFF9C7E40)
                        CargoType.SOIL -> Color(0xFF7D512B) to Color(0xFF3E2612)
                        CargoType.CROPS -> Color(0xFFE7BA46) to Color(0xFF8C6615)
                        CargoType.BRICKS -> Color(0xFF8C2D19) to Color(0xFF4F170B)
                        CargoType.WOOD -> Color(0xFF6B4223) to Color(0xFF361F0E)
                        CargoType.STONES -> Color(0xFF737A82) to Color(0xFF3B3F44)
                        CargoType.NONE -> Color.Transparent to Color.Transparent
                    }
                    drawPath(heapPath, botCol)
                    drawRect(topCol.copy(alpha = 0.45f), Offset(-len * 0.30f, floorY - bedH * 1.32f), Size(len * 0.60f, bedH * 0.36f))
                }

                for (p in pieces) {
                    if (p.spilled) continue
                    val px = p.lx * ppm
                    val py = floorY - p.ly * ppm
                    drawRealisticCargoPiece(cargo, Offset(px, py), p.angle, ppm)
                }

                val darkBody = Color(
                    red = trailer.bodyColor.red * 0.56f,
                    green = trailer.bodyColor.green * 0.56f,
                    blue = trailer.bodyColor.blue * 0.56f
                )
                drawRoundRect(
                    color = darkBody,
                    topLeft = Offset(-len * 0.50f, floorY - lowerWallH),
                    size = Size(len, lowerWallH),
                    cornerRadius = CornerRadius(0.03f * ppm)
                )
                drawRect(
                    color = trailer.bodyColor,
                    topLeft = Offset(-len * 0.50f, floorY - lowerWallH),
                    size = Size(len, lowerWallH * 0.62f)
                )

                // Welded steel vertical stake ribs with top & bottom bolts
                for (r in 0..4) {
                    val rx = -len * 0.46f + r * (len * 0.22f)
                    drawRect(darkBody, Offset(rx, floorY - bedH), Size(0.065f * ppm, bedH))
                    drawRect(trailer.accentColor, Offset(rx + 0.01f * ppm, floorY - bedH), Size(0.028f * ppm, bedH))
                    drawCircle(Color(0xFFD0D6DE), 0.014f * ppm, Offset(rx + 0.032f * ppm, floorY - bedH + 0.035f * ppm))
                }
                drawRect(trailer.bodyColor, Offset(-len * 0.51f, floorY - bedH * 0.74f), Size(len * 1.02f, 0.045f * ppm))
                drawRect(Color(0xFF1E2226), Offset(-len * 0.52f, floorY - bedH), Size(len * 1.04f, 0.065f * ppm))

                // Rear tail-light & amber side marker reflectors
                drawRect(Color(0xFFE03A2E), Offset(-len * 0.50f, floorY - lowerWallH * 0.75f), Size(0.065f * ppm, lowerWallH * 0.38f))
                drawRect(Color(0xFFFFB300), Offset(len * 0.42f, floorY - lowerWallH * 0.65f), Size(0.055f * ppm, 0.045f * ppm))
                drawRect(Color(0xFFFFB300), Offset(0f, floorY - lowerWallH * 0.65f), Size(0.055f * ppm, 0.045f * ppm))
            }

            // Real Photographic 9.00-16 Agricultural Trolley Tyre + Hub Cap
            drawWheelSprite(
                bitmap = trailerWheel,
                center = Offset(wheelX, wheelY),
                radius = trailer.wheelRadius * ppm * 1.015f,
                rotationRad = wheelRot
            )
            drawCircle(Color(0xFF282D32), trailer.wheelRadius * ppm * 0.16f, Offset(wheelX, wheelY))
            drawCircle(Color(0xFFAAB2BC), trailer.wheelRadius * ppm * 0.08f, Offset(wheelX, wheelY))
        }
    }

    private fun DrawScope.drawTractorTrailerHitchJoint(
        hitchScreen: Offset,
        tongueScreen: Offset,
        tractorAngleRad: Float,
        trailerAngleRad: Float,
        ppm: Float
    ) {
        val tractorDeg = -tractorAngleRad * 180f / PI.toFloat()
        val trailerDeg = -trailerAngleRad * 180f / PI.toFloat()
        val pinCenter = Offset(
            (hitchScreen.x + tongueScreen.x) * 0.5f,
            (hitchScreen.y + tongueScreen.y) * 0.5f
        )

        // 1. Slack Steel Safety Chain under the coupling
        val chainStart = Offset(
            hitchScreen.x + cos(tractorAngleRad) * (0.16f * ppm) + sin(tractorAngleRad) * (0.05f * ppm),
            hitchScreen.y - sin(tractorAngleRad) * (0.16f * ppm) + cos(tractorAngleRad) * (0.05f * ppm)
        )
        val chainEnd = Offset(
            tongueScreen.x - cos(trailerAngleRad) * (0.26f * ppm) + sin(trailerAngleRad) * (0.05f * ppm),
            tongueScreen.y + sin(trailerAngleRad) * (0.26f * ppm) + cos(trailerAngleRad) * (0.05f * ppm)
        )
        val chainMid = Offset(
            (chainStart.x + chainEnd.x) * 0.5f,
            maxOf(chainStart.y, chainEnd.y) + 0.14f * ppm
        )
        for (step in 0..5) {
            val t = step / 5f
            val omt = 1f - t
            val lx = omt * omt * chainStart.x + 2f * omt * t * chainMid.x + t * t * chainEnd.x
            val ly = omt * omt * chainStart.y + 2f * omt * t * chainMid.y + t * t * chainEnd.y
            drawCircle(Color(0xFF1D2126), radius = 0.036f * ppm, center = Offset(lx, ly))
            drawCircle(
                color = if (step % 2 == 0) Color(0xFF8C96A3) else Color(0xFF5D6672),
                radius = 0.025f * ppm,
                center = Offset(lx, ly),
                style = Stroke(width = 0.015f * ppm)
            )
        }

        // 2. Forged Tractor Rear Hitch Bracket + Lower Clevis Plate
        withTransform({
            translate(hitchScreen.x, hitchScreen.y)
            rotate(tractorDeg, pivot = Offset.Zero)
        }) {
            drawLine(Color(0xFF1A1D22), Offset(0.46f * ppm, 0f), Offset(0f, 0f), strokeWidth = 0.115f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF535B66), Offset(0.42f * ppm, -0.022f * ppm), Offset(0.02f * ppm, -0.022f * ppm), strokeWidth = 0.028f * ppm, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFF2B3038), Offset(-0.10f * ppm, 0.026f * ppm), Size(0.22f * ppm, 0.044f * ppm), CornerRadius(0.018f * ppm))
        }

        // 3. Trailer Drawbar Neck Collar + Forged Tow-Eye Ring
        withTransform({
            translate(tongueScreen.x, tongueScreen.y)
            rotate(trailerDeg, pivot = Offset.Zero)
        }) {
            drawRoundRect(Color(0xFF424852), Offset(-0.20f * ppm, -0.052f * ppm), Size(0.16f * ppm, 0.104f * ppm), CornerRadius(0.025f * ppm))
            drawCircle(Color(0xFF1E2228), radius = 0.078f * ppm, center = Offset.Zero)
            drawCircle(Color(0xFF7D8794), radius = 0.058f * ppm, center = Offset.Zero, style = Stroke(width = 0.028f * ppm))
        }

        // 4. Upper Tractor Clevis Jaw Plate
        withTransform({
            translate(hitchScreen.x, hitchScreen.y)
            rotate(tractorDeg, pivot = Offset.Zero)
        }) {
            drawRoundRect(Color(0xFF464D57), Offset(-0.10f * ppm, -0.070f * ppm), Size(0.22f * ppm, 0.044f * ppm), CornerRadius(0.018f * ppm))
        }

        // 5. Heavy Vertical Steel Locking Pin ("Killa") + Orange Safety Handle + Bottom Cotter Clip
        withTransform({
            translate(pinCenter.x, pinCenter.y)
            rotate((tractorDeg + trailerDeg) * 0.5f, pivot = Offset.Zero)
        }) {
            drawRoundRect(Color(0xFF6B737D), Offset(-0.026f * ppm, -0.135f * ppm), Size(0.052f * ppm, 0.255f * ppm), CornerRadius(0.015f * ppm))
            drawRect(Color(0xFFD2DAE3), Offset(-0.012f * ppm, -0.130f * ppm), Size(0.020f * ppm, 0.245f * ppm))
            drawRoundRect(Color(0xFFE65C1C), Offset(-0.068f * ppm, -0.175f * ppm), Size(0.136f * ppm, 0.048f * ppm), CornerRadius(0.022f * ppm))
            drawCircle(Color(0xFFFFE0B2), 0.014f * ppm, Offset(-0.032f * ppm, -0.154f * ppm))
            drawLine(Color(0xFFE0B43A), Offset(-0.048f * ppm, 0.095f * ppm), Offset(0.052f * ppm, 0.095f * ppm), strokeWidth = 0.022f * ppm, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.drawRealisticCargoPiece(cargo: CargoType, pos: Offset, rotRad: Float, ppm: Float) {
        withTransform({
            translate(pos.x, pos.y)
            rotate(rotRad * 180f / PI.toFloat(), pivot = Offset.Zero)
        }) {
            when (cargo) {
                CargoType.CROPS -> {
                    drawOval(Color(0xFFB58622), Offset(-0.25f * ppm, -0.15f * ppm), Size(0.50f * ppm, 0.30f * ppm))
                    drawOval(Color(0xFFE0AC38), Offset(-0.21f * ppm, -0.13f * ppm), Size(0.42f * ppm, 0.16f * ppm))
                    drawLine(Color(0xFF345E24), Offset(-0.18f * ppm, 0f), Offset(0.18f * ppm, 0f), strokeWidth = 0.04f * ppm)
                }
                CargoType.SOIL -> drawCircle(Color(0xFF4E3016).copy(alpha = 0.65f), 0.11f * ppm, Offset.Zero)
                CargoType.SAND -> drawCircle(Color(0xFFC2A05C).copy(alpha = 0.60f), 0.10f * ppm, Offset.Zero)
                CargoType.WOOD -> {
                    drawRoundRect(Color(0xFF5D381D), Offset(-0.34f * ppm, -0.11f * ppm), Size(0.68f * ppm, 0.22f * ppm), CornerRadius(0.09f * ppm))
                    drawRect(Color(0xFF7D4E2A), Offset(-0.32f * ppm, -0.09f * ppm), Size(0.62f * ppm, 0.08f * ppm))
                    drawCircle(Color(0xFFC29769), 0.085f * ppm, Offset(0.28f * ppm, 0f))
                }
                CargoType.BRICKS -> {
                    drawRect(Color(0xFF9C341D), Offset(-0.22f * ppm, -0.11f * ppm), Size(0.44f * ppm, 0.22f * ppm))
                    drawRect(Color(0xFF331109), Offset(-0.22f * ppm, -0.11f * ppm), Size(0.44f * ppm, 0.22f * ppm), style = Stroke(2f))
                }
                CargoType.STONES -> {
                    rockPath.reset()
                    rockPath.moveTo(-0.22f * ppm, 0.10f * ppm)
                    rockPath.lineTo(-0.16f * ppm, -0.16f * ppm)
                    rockPath.lineTo(0.08f * ppm, -0.20f * ppm)
                    rockPath.lineTo(0.24f * ppm, 0.05f * ppm)
                    rockPath.lineTo(0.10f * ppm, 0.16f * ppm)
                    rockPath.close()
                    drawPath(rockPath, Color(0xFF6B7178))
                    drawPath(rockPath, Color(0xFF232529), style = Stroke(2f, join = StrokeJoin.Round))
                }
                CargoType.NONE -> Unit
            }
        }
    }

    private fun DrawScope.drawCheckpointFlag(sx: Float, sy: Float, ppm: Float, color: Color) {
        drawRect(Color(0xFFB0B7BF), Offset(sx - ppm * 0.05f, sy - ppm * 2.4f), Size(ppm * 0.10f, ppm * 2.4f))
        flagPath.reset()
        flagPath.moveTo(sx + ppm * 0.05f, sy - ppm * 2.4f)
        flagPath.lineTo(sx + ppm * 1.15f, sy - ppm * 2.05f)
        flagPath.lineTo(sx + ppm * 0.05f, sy - ppm * 1.65f)
        flagPath.close()
        drawPath(flagPath, color)
    }

    private fun DrawScope.drawCoin(sx: Float, sy: Float, ppm: Float, phase: Float) {
        val squeeze = (0.35f + 0.65f * abs(cos(phase * 2.2f)))
        val rx = ppm * 0.22f * squeeze
        val ry = ppm * 0.22f
        drawCircle(Color(0xFFFFD54F).copy(alpha = 0.20f), ppm * 0.30f, Offset(sx, sy))
        drawOval(Color(0xFF9E6100), Offset(sx - rx, sy - ry), Size(rx * 2f, ry * 2f))
        drawOval(Color(0xFFE6B43A), Offset(sx - rx * 0.78f, sy - ry * 0.78f), Size(rx * 1.56f, ry * 1.56f))
        drawOval(Color(0xFFFFF3B0).copy(alpha = 0.65f), Offset(sx - rx * 0.45f, sy - ry * 0.55f), Size(rx * 0.75f, ry * 0.52f))
    }

    private fun DrawScope.drawFuelCan(sx: Float, sy: Float, ppm: Float) {
        val w = ppm * 0.54f
        val h = ppm * 0.66f
        drawCircle(Color(0xFF4CAF50).copy(alpha = 0.18f), ppm * 0.44f, Offset(sx, sy))
        drawRoundRect(Color(0xFF166B31), Offset(sx - w * 0.5f, sy - h * 0.5f), Size(w, h), CornerRadius(ppm * 0.07f))
        drawRoundRect(Color(0xFFD8E0DA), Offset(sx - w * 0.5f, sy - h * 0.5f), Size(w, h), CornerRadius(ppm * 0.07f), style = Stroke(2f))
        drawRect(Color(0xFFD68E1E), Offset(sx + w * 0.1f, sy - h * 0.64f), Size(w * 0.24f, h * 0.17f))
        drawRect(Color.White, Offset(sx - w * 0.20f, sy - h * 0.06f), Size(w * 0.40f, h * 0.12f))
        drawRect(Color.White, Offset(sx - w * 0.06f, sy - h * 0.20f), Size(w * 0.12f, h * 0.40f))
    }
}
