package com.example.render

import android.content.res.Resources
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
        fun load(resources: Resources, tractor: TractorModel, driver: DriverCharacter): TractorArtAssets {
            val (bodyId, rearId, frontId) = when (tractor) {
                TractorModel.EICHER_241 -> Triple(R.drawable.body_eicher, R.drawable.wheel_eicher_rear, R.drawable.wheel_eicher_front)
                TractorModel.MAHINDRA_575 -> Triple(R.drawable.body_mahindra, R.drawable.wheel_mahindra_rear, R.drawable.wheel_mahindra_front)
                TractorModel.SONALIKA_750 -> Triple(R.drawable.body_sonalika, R.drawable.wheel_sonalika_rear, R.drawable.wheel_sonalika_front)
            }
            return TractorArtAssets(
                body = BitmapFactory.decodeResource(resources, bodyId).asImageBitmap(),
                rearWheel = BitmapFactory.decodeResource(resources, rearId).asImageBitmap(),
                frontWheel = BitmapFactory.decodeResource(resources, frontId).asImageBitmap(),
                trailerWheel = BitmapFactory.decodeResource(resources, R.drawable.wheel_trailer).asImageBitmap(),
                face = BitmapFactory.decodeResource(resources, driver.faceDrawable)?.asImageBitmap(),
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
    val gripNx: Float,
    val gripNy: Float,
    val headlightNx: Float,
    val headlightNy: Float,
    val hitchNx: Float,
    val hitchNy: Float
)

object WorldRenderer {

    private fun metricsFor(tractor: TractorModel): TractorSpriteMetrics = when (tractor) {
        TractorModel.EICHER_241 -> TractorSpriteMetrics(
            rearAxleNx = 0.315f, rearAxleNy = 0.677f,
            frontAxleNx = 0.774f, frontAxleNy = 0.808f,
            seatNx = 0.280f, seatNy = 0.365f,
            gripNx = 0.470f, gripNy = 0.258f,
            headlightNx = 0.841f, headlightNy = 0.383f,
            hitchNx = 0.105f, hitchNy = 0.677f
        )
        TractorModel.MAHINDRA_575 -> TractorSpriteMetrics(
            rearAxleNx = 0.243f, rearAxleNy = 0.701f,
            frontAxleNx = 0.753f, frontAxleNy = 0.822f,
            seatNx = 0.305f, seatNy = 0.340f,
            gripNx = 0.455f, gripNy = 0.292f,
            headlightNx = 0.875f, headlightNy = 0.425f,
            hitchNx = 0.018f, hitchNy = 0.701f
        )
        TractorModel.SONALIKA_750 -> TractorSpriteMetrics(
            rearAxleNx = 0.242f, rearAxleNy = 0.690f,
            frontAxleNx = 0.757f, frontAxleNy = 0.788f,
            seatNx = 0.270f, seatNy = 0.305f,
            gripNx = 0.422f, gripNy = 0.270f,
            headlightNx = 0.870f, headlightNy = 0.370f,
            hitchNx = 0.022f, hitchNy = 0.690f
        )
    }

    fun DrawScope.renderWorld(
        world: PhysicsWorld,
        assets: TractorArtAssets,
        timeSec: Float
    ) {
        val w = size.width
        val h = size.height
        val ppm = (h / 9.0f).coerceIn(48f, 118f)

        val shakeX = if (world.camShake > 0.001f) sin(timeSec * 55f) * world.camShake * ppm else 0f
        val shakeY = if (world.camShake > 0.001f) cos(timeSec * 48f) * world.camShake * ppm else 0f

        val camX = world.camX
        val camY = world.camY

        fun worldToScreenX(wx: Float): Float = w * 0.44f + (wx - camX) * ppm + shakeX
        fun worldToScreenY(wy: Float): Float = h * 0.65f - (wy - camY) * ppm + shakeY

        // 1. Atmospheric Sky, Clouds, Parallax Hills & Horizon Mist
        drawRealisticAtmosphere(world.map, camX, w, h, timeSec)

        // 2. Roadside Props
        val minVisX = camX - (w * 0.55f) / ppm - 10f
        val maxVisX = camX + (w * 0.75f) / ppm + 12f
        for (prop in world.terrain.props) {
            if (prop.x < minVisX || prop.x > maxVisX) continue
            val sx = worldToScreenX(prop.x)
            val sy = worldToScreenY(world.terrain.heightAt(prop.x))
            drawRealisticProp(world.map, prop, sx, sy, ppm, timeSec)
        }

        // 3. Textured Ground & Road Surface
        val visiblePts = world.terrain.getVisiblePoints(minVisX, maxVisX)
        drawRealisticTerrain(
            map = world.map,
            visiblePts = visiblePts,
            textures = assets.textures,
            worldToScreenX = ::worldToScreenX,
            worldToScreenY = ::worldToScreenY,
            h = h,
            ppm = ppm
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

        // 6. Ground Contact Shadows
        val tractorGroundY = worldToScreenY(world.terrain.heightAt(world.x))
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent),
                center = Offset(worldToScreenX(world.x), tractorGroundY),
                radius = 2.0f * ppm
            ),
            topLeft = Offset(worldToScreenX(world.x) - 2.0f * ppm, tractorGroundY - 0.26f * ppm),
            size = Size(4.0f * ppm, 0.52f * ppm)
        )
        if (world.hasTrailer) {
            val trGroundY = worldToScreenY(world.terrain.heightAt(world.trX))
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.48f), Color.Transparent),
                    center = Offset(worldToScreenX(world.trX), trGroundY),
                    radius = (world.trailer.bedLength * 0.62f) * ppm
                ),
                topLeft = Offset(worldToScreenX(world.trX) - world.trailer.bedLength * 0.62f * ppm, trGroundY - 0.22f * ppm),
                size = Size(world.trailer.bedLength * 1.24f * ppm, 0.44f * ppm)
            )
        }

        // 7. Billowing Soft Radial Diesel Exhaust Plumes & Road Dust/Mud/Splash
        for (p in world.particles) {
            val sx = worldToScreenX(p.x)
            val sy = worldToScreenY(p.y)
            val lifeFrac = (p.life / p.maxLife).coerceIn(0f, 1f)
            val rad = p.size * ppm
            when (p.kind) {
                0 -> {
                    // Realistic radial-gradient diesel smoke puff fading softly outward
                    val sootShade = (0.30f - p.darkSoot * 0.20f).coerceIn(0.08f, 0.35f)
                    val smokeCol = Color(sootShade, sootShade, sootShade + 0.02f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                smokeCol.copy(alpha = lifeFrac * 0.62f),
                                smokeCol.copy(alpha = lifeFrac * 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(sx, sy),
                            radius = rad * 1.8f
                        ),
                        radius = rad * 1.8f,
                        center = Offset(sx, sy)
                    )
                }
                1 -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF9C7A52).copy(alpha = lifeFrac * 0.42f), Color.Transparent),
                            center = Offset(sx, sy),
                            radius = rad * 1.4f
                        ),
                        radius = rad * 1.4f,
                        center = Offset(sx, sy)
                    )
                }
                2 -> drawCircle(Color(0xFF3A2210).copy(alpha = lifeFrac * 0.85f), rad, Offset(sx, sy))
                3 -> drawCircle(Color(0xFFA9D6EA).copy(alpha = lifeFrac * 0.72f), rad, Offset(sx, sy))
                else -> drawCircle(Color(0xFFFFD54F).copy(alpha = lifeFrac * 0.88f), rad, Offset(sx, sy))
            }
        }

        // 8. Detailed Trailer + A-Frame Drawbar + Physical Cargo
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

        // 9. Realistic Tractor Sprite + Rotating Tyre Sprites + Seated Real-Face Driver
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
            ppm = ppm
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
        val ppm = (h / 4.3f).coerceIn(38f, 92f)

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

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent),
                center = Offset(tractorCenterX, groundY),
                radius = 2.0f * ppm
            ),
            topLeft = Offset(tractorCenterX - 2.0f * ppm, groundY - 0.2f * ppm),
            size = Size(4.0f * ppm, 0.4f * ppm)
        )

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
            val tongueLy = ((restTractorY + hitchLy) - restTrailerY).coerceIn(-0.42f, 0.12f)

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
        ppm: Float
    ) {
        val m = metricsFor(tractor)
        val deg = (-angleRad * 180f / PI.toFloat())
        val wb = tractor.wheelBase * ppm
        val rearX = -wb * 0.44f
        val frontX = wb * 0.54f

        // Match exact level axle vertical geometry from PhysicsWorld!
        val rearAnchorY = 0.05f * ppm
        val frontAnchorY = (0.05f + (tractor.rearWheelRadius - tractor.frontWheelRadius)) * ppm
        val rearWheelY = rearAnchorY + rearComp * ppm
        val frontWheelY = frontAnchorY + frontComp * ppm

        val spriteW = (frontX - rearX) / (m.frontAxleNx - m.rearAxleNx)
        val spriteH = spriteW * (assets.body.height.toFloat() / assets.body.width.toFloat())
        val spriteLeft = rearX - m.rearAxleNx * spriteW
        // Center body sprite vertically using average axle rest offset so suspension moves smoothly around it
        val bodySuspOffset = ((rearComp - 0.26f) + (frontComp - 0.26f)) * 0.5f * ppm
        val spriteTop = (rearAnchorY + 0.26f * ppm + bodySuspOffset) - m.rearAxleNy * spriteH

        withTransform({
            translate(centerScreen.x, centerScreen.y)
            rotate(degrees = deg, pivot = Offset.Zero)
        }) {
            // 1. Suspension Coil Struts
            drawLine(
                color = Color(0xFF32363B),
                start = Offset(rearX, rearAnchorY),
                end = Offset(rearX, rearWheelY),
                strokeWidth = ppm * 0.11f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF42484F),
                start = Offset(frontX, frontAnchorY),
                end = Offset(frontX, frontWheelY),
                strokeWidth = ppm * 0.09f,
                cap = StrokeCap.Round
            )

            // 2A. Seated Driver Back Layer (legs inside cockpit & far arm reaching steering wheel)
            val seatScreen = Offset(spriteLeft + m.seatNx * spriteW, spriteTop + m.seatNy * spriteH)
            val gripScreen = Offset(spriteLeft + m.gripNx * spriteW, spriteTop + m.gripNy * spriteH)
            drawRealisticDriverLegs(
                driver = driver,
                seatPos = seatScreen,
                wheelGripPos = gripScreen,
                leanRad = driverLean,
                ppm = ppm
            )

            // 3. Realistic High-Detail Tractor Body Sprite
            drawImage(
                image = assets.body,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(assets.body.width, assets.body.height),
                dstOffset = IntOffset(spriteLeft.roundToInt(), spriteTop.roundToInt()),
                dstSize = IntSize(spriteW.roundToInt(), spriteH.roundToInt())
            )

            // 3B. Seated Driver Upper Layer (torso, belt, collar, real face, rolled sleeve & hand gripping wheel)
            drawRealisticDriverUpper(
                driver = driver,
                faceBitmap = assets.face,
                seatPos = seatScreen,
                wheelGripPos = gripScreen,
                leanRad = driverLean,
                hasCanopy = tractor == TractorModel.SONALIKA_750,
                ppm = ppm
            )

            // 4. Headlight Cone on Dusk / Rainy Maps
            if (headlightsOn) {
                val hlPos = Offset(spriteLeft + m.headlightNx * spriteW, spriteTop + m.headlightNy * spriteH)
                val cone = Path().apply {
                    moveTo(hlPos.x, hlPos.y)
                    lineTo(hlPos.x + 3.4f * ppm, hlPos.y - 0.65f * ppm)
                    lineTo(hlPos.x + 3.4f * ppm, hlPos.y + 0.85f * ppm)
                    close()
                }
                drawPath(
                    path = cone,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFFF5C8).copy(alpha = 0.34f), Color.Transparent),
                        startX = hlPos.x,
                        endX = hlPos.x + 3.4f * ppm
                    )
                )
            }

            // 5. Rotating Concentric Rear & Front Lug Wheel Sprites
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
        }
    }

    private fun DrawScope.drawWheelSprite(
        bitmap: ImageBitmap,
        center: Offset,
        radius: Float,
        rotationRad: Float
    ) {
        val deg = rotationRad * 180f / PI.toFloat()
        // Keep even pixel size so -sizePx/2 aligns exactly with rotation pivot Offset.Zero
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
        wheelGripPos: Offset,
        leanRad: Float,
        ppm: Float
    ) {
        val leanDeg = leanRad * 180f / PI.toFloat()
        val pantsShadow = Color(
            red = driver.pantsColor.red * 0.65f,
            green = driver.pantsColor.green * 0.65f,
            blue = driver.pantsColor.blue * 0.65f
        )
        val farShirt = Color(
            red = driver.shirtPrimary.red * 0.62f,
            green = driver.shirtPrimary.green * 0.62f,
            blue = driver.shirtPrimary.blue * 0.62f
        )
        withTransform({
            translate(seatPos.x, seatPos.y)
            rotate(leanDeg * 0.5f, pivot = Offset.Zero)
        }) {
            // Far leg & boot (subtle depth silhouette)
            drawLine(pantsShadow, Offset(0.03f * ppm, 0.04f * ppm), Offset(0.32f * ppm, 0.11f * ppm), strokeWidth = 0.17f * ppm, cap = StrokeCap.Round)
            drawLine(pantsShadow, Offset(0.32f * ppm, 0.11f * ppm), Offset(0.41f * ppm, 0.36f * ppm), strokeWidth = 0.15f * ppm, cap = StrokeCap.Round)

            // Near thigh & shin resting toward tractor footboard pedals
            drawLine(driver.pantsColor, Offset(0.01f * ppm, 0.03f * ppm), Offset(0.28f * ppm, 0.12f * ppm), strokeWidth = 0.19f * ppm, cap = StrokeCap.Round)
            drawLine(driver.pantsColor, Offset(0.28f * ppm, 0.12f * ppm), Offset(0.36f * ppm, 0.37f * ppm), strokeWidth = 0.16f * ppm, cap = StrokeCap.Round)
            // Denim seam highlight
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0.05f * ppm, 0.00f), Offset(0.26f * ppm, 0.08f * ppm), strokeWidth = 0.025f * ppm, cap = StrokeCap.Round)
            // Work boot on footrest pedal
            drawRoundRect(
                color = Color(0xFF2B2118),
                topLeft = Offset(0.30f * ppm, 0.34f * ppm),
                size = Size(0.18f * ppm, 0.09f * ppm),
                cornerRadius = CornerRadius(0.035f * ppm)
            )
            drawLine(Color(0xFF4B3A2A), Offset(0.31f * ppm, 0.42f * ppm), Offset(0.47f * ppm, 0.42f * ppm), strokeWidth = 0.022f * ppm)

            // Far (left) arm reaching to far side of steering wheel rim
            val gx = wheelGripPos.x - seatPos.x + 0.05f * ppm
            val gy = wheelGripPos.y - seatPos.y - 0.03f * ppm
            val fElbowX = gx * 0.52f + 0.04f * ppm
            val fElbowY = gy * 0.46f + 0.07f * ppm
            drawLine(farShirt, Offset(0.07f * ppm, -0.33f * ppm), Offset(fElbowX, fElbowY), strokeWidth = 0.11f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFFA77555), Offset(fElbowX, fElbowY), Offset(gx, gy), strokeWidth = 0.085f * ppm, cap = StrokeCap.Round)
            drawCircle(Color(0xFF9C6B4C), 0.052f * ppm, Offset(gx, gy))
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
        val leanDeg = leanRad * 180f / PI.toFloat() + 6f
        val shirtDark = Color(
            red = driver.shirtPrimary.red * 0.74f,
            green = driver.shirtPrimary.green * 0.74f,
            blue = driver.shirtPrimary.blue * 0.74f
        )
        val scale = if (hasCanopy) 0.84f else 0.94f
        withTransform({
            translate(seatPos.x, seatPos.y)
            rotate(leanDeg, pivot = Offset.Zero)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // Hip / lower jeans waistband & leather belt with brass buckle
            drawRoundRect(
                color = driver.pantsColor,
                topLeft = Offset(-0.13f * ppm, -0.02f * ppm),
                size = Size(0.28f * ppm, 0.12f * ppm),
                cornerRadius = CornerRadius(0.05f * ppm)
            )
            drawRect(Color(0xFF3E2614), Offset(-0.13f * ppm, -0.025f * ppm), Size(0.27f * ppm, 0.045f * ppm))
            drawRect(Color(0xFFD2A343), Offset(0.07f * ppm, -0.025f * ppm), Size(0.045f * ppm, 0.045f * ppm))

            // Contoured shirt torso with natural driving slouch & fold shading
            val torso = Path().apply {
                moveTo(-0.14f * ppm, -0.01f * ppm)
                lineTo(-0.17f * ppm, -0.40f * ppm)
                quadraticTo(0.01f * ppm, -0.49f * ppm, 0.15f * ppm, -0.39f * ppm)
                lineTo(0.14f * ppm, -0.01f * ppm)
                close()
            }
            drawPath(
                path = torso,
                brush = Brush.horizontalGradient(
                    colors = listOf(shirtDark, driver.shirtPrimary, shirtDark),
                    startX = -0.17f * ppm,
                    endX = 0.15f * ppm
                )
            )
            // Shirt button placket & chest pocket
            drawLine(driver.shirtAccent, Offset(0.055f * ppm, -0.43f * ppm), Offset(0.065f * ppm, -0.03f * ppm), strokeWidth = 0.028f * ppm)
            drawRoundRect(
                color = shirtDark.copy(alpha = 0.55f),
                topLeft = Offset(0.075f * ppm, -0.33f * ppm),
                size = Size(0.065f * ppm, 0.085f * ppm),
                cornerRadius = CornerRadius(0.012f * ppm)
            )
            // Shirt collar
            val collar = Path().apply {
                moveTo(-0.06f * ppm, -0.44f * ppm)
                lineTo(0.04f * ppm, -0.37f * ppm)
                lineTo(0.11f * ppm, -0.43f * ppm)
                lineTo(0.03f * ppm, -0.47f * ppm)
                close()
            }
            drawPath(collar, driver.shirtPrimary)
            drawPath(collar, shirtDark.copy(alpha = 0.55f), style = Stroke(width = 1.8f))

            // Sculpted neck + chin shadow
            drawRoundRect(
                color = Color(0xFFC28E6B),
                topLeft = Offset(-0.04f * ppm, -0.54f * ppm),
                size = Size(0.11f * ppm, 0.13f * ppm),
                cornerRadius = CornerRadius(0.03f * ppm)
            )
            drawOval(
                color = Color(0xFF7B4E33).copy(alpha = 0.42f),
                topLeft = Offset(-0.03f * ppm, -0.53f * ppm),
                size = Size(0.10f * ppm, 0.06f * ppm)
            )

            // Head & Hair silhouette + seamlessly integrated real driver face
            val headCenter = Offset(0.02f * ppm, -0.66f * ppm)
            val headRad = 0.185f * ppm
            // Natural dark hair cap behind/above head oval
            drawOval(
                color = Color(0xFF1F1A17),
                topLeft = Offset(headCenter.x - headRad * 1.04f, headCenter.y - headRad * 1.14f),
                size = Size(headRad * 2.02f, headRad * 1.85f)
            )
            if (faceBitmap != null) {
                val ovalPath = Path().apply {
                    addOval(
                        Rect(
                            headCenter.x - headRad,
                            headCenter.y - headRad * 1.06f,
                            headCenter.x + headRad,
                            headCenter.y + headRad * 1.06f
                        )
                    )
                }
                clipPath(ovalPath) {
                    drawImage(
                        image = faceBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(faceBitmap.width, faceBitmap.height),
                        dstOffset = IntOffset((headCenter.x - headRad).roundToInt(), (headCenter.y - headRad * 1.06f).roundToInt()),
                        dstSize = IntSize((headRad * 2f).roundToInt(), (headRad * 2.12f).roundToInt())
                    )
                }
            } else {
                drawCircle(Color(0xFFC9936F), headRad, headCenter)
            }
            // Subtle warm rim shadow instead of cartoon black outline
            drawOval(
                color = Color(0xFF4A3122).copy(alpha = 0.38f),
                topLeft = Offset(headCenter.x - headRad, headCenter.y - headRad * 1.06f),
                size = Size(headRad * 2f, headRad * 2.12f),
                style = Stroke(width = 1.6f)
            )

            // Near (right) arm with rolled-up sleeve, wristwatch & contoured hand gripping steering wheel
            val rawGx = (wheelGripPos.x - seatPos.x) / scale
            val rawGy = (wheelGripPos.y - seatPos.y) / scale
            val invLeanRad = -leanDeg * (PI.toFloat() / 180f)
            val cosL = cos(invLeanRad)
            val sinL = sin(invLeanRad)
            val gx = rawGx * cosL - rawGy * sinL
            val gy = rawGx * sinL + rawGy * cosL
            val shoulder = Offset(0.02f * ppm, -0.35f * ppm)
            val elbow = Offset(gx * 0.46f - 0.02f * ppm, gy * 0.42f + 0.09f * ppm)
            // Upper sleeve + folded cuff
            drawLine(driver.shirtPrimary, shoulder, elbow, strokeWidth = 0.135f * ppm, cap = StrokeCap.Round)
            drawCircle(shirtDark, 0.068f * ppm, shoulder)
            drawCircle(driver.shirtAccent.copy(alpha = 0.65f), 0.062f * ppm, elbow)
            // Forearm & metallic watch/kada near wrist
            drawLine(Color(0xFFC8926E), elbow, Offset(gx, gy), strokeWidth = 0.092f * ppm, cap = StrokeCap.Round)
            val wristX = elbow.x + (gx - elbow.x) * 0.80f
            val wristY = elbow.y + (gy - elbow.y) * 0.80f
            drawCircle(Color(0xFFD6DCE2), 0.048f * ppm, Offset(wristX, wristY))
            // Contoured hand & fingers wrapped on steering wheel rim
            drawOval(
                color = Color(0xFFB9805B),
                topLeft = Offset(gx - 0.068f * ppm, gy - 0.055f * ppm),
                size = Size(0.136f * ppm, 0.11f * ppm)
            )
            drawCircle(Color(0xFF9C6846), 0.042f * ppm, Offset(gx + 0.025f * ppm, gy + 0.01f * ppm))
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
            // Heavy-Duty A-Frame Steel Drawbar (Tow Tongue) extending forward from trailer chassis to hitch eye
            val tongueEnd = Offset(tongueLocalX * ppm, -tongueLocalY * ppm)
            val lowerRoot = Offset(len * 0.40f, -0.02f * ppm)
            val upperBraceRoot = Offset(len * 0.47f, -0.20f * ppm)
            val neckMid = Offset(tongueEnd.x - 0.16f * ppm, tongueEnd.y)

            // Triangular steel gusset web inside the A-frame fork
            val gusset = Path().apply {
                moveTo(lowerRoot.x, lowerRoot.y)
                lineTo(upperBraceRoot.x, upperBraceRoot.y)
                lineTo(neckMid.x, neckMid.y)
                close()
            }
            drawPath(gusset, Color(0xFF23272D))

            // Main lower C-channel drawbar arm + upper diagonal brace + bright steel edge highlight
            drawLine(Color(0xFF16181C), lowerRoot, tongueEnd, strokeWidth = 0.135f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF454C56), lowerRoot, tongueEnd, strokeWidth = 0.075f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF1E2228), upperBraceRoot, neckMid, strokeWidth = 0.105f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF535B66), upperBraceRoot, neckMid, strokeWidth = 0.048f * ppm, cap = StrokeCap.Round)

            // Tucked parking prop stand under the drawbar
            val standTop = Offset(len * 0.50f + 0.16f * ppm, tongueEnd.y * 0.55f + 0.02f * ppm)
            val standFoot = Offset(standTop.x - 0.12f * ppm, standTop.y + 0.15f * ppm)
            drawLine(Color(0xFF2D3138), standTop, standFoot, strokeWidth = 0.06f * ppm, cap = StrokeCap.Round)
            drawLine(Color(0xFF69727D), Offset(standFoot.x - 0.06f * ppm, standFoot.y), Offset(standFoot.x + 0.06f * ppm, standFoot.y), strokeWidth = 0.045f * ppm, cap = StrokeCap.Round)

            // Leaf-spring axle hanger & chassis C-channel
            drawLine(Color(0xFF3A3F45), Offset(wheelX, -0.05f * ppm), Offset(wheelX, wheelY), strokeWidth = 0.12f * ppm, cap = StrokeCap.Round)
            if (dumpAngleRad > 0.02f) {
                drawLine(Color(0xFFB5BEC7), Offset(0f, -0.06f * ppm), Offset(0.15f * ppm, -0.5f * ppm * dumpAngleRad * 2f), strokeWidth = 0.09f * ppm)
            }
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF454B52), Color(0xFF1A1D21))),
                topLeft = Offset(-len * 0.48f, -0.11f * ppm),
                size = Size(len * 1.02f, 0.14f * ppm)
            )

            // Tiltable Steel Cargo Bed with Lower Solid Sideboard + Open Top Cage so ALL Cargo is Clearly Visible!
            val dumpDeg = -dumpAngleRad * 180f / PI.toFloat()
            withTransform({
                rotate(dumpDeg, pivot = Offset(-len * 0.45f, -0.08f * ppm))
            }) {
                val lowerWallH = bedH * 0.46f
                val floorY = -0.12f * ppm

                // Back bed interior panel (dark shadow behind cargo)
                drawRect(Color(0xFF17181A), Offset(-len * 0.47f, floorY - bedH * 0.90f), Size(len * 0.94f, bedH * 0.90f))

                // Continuous piled mound under pieces (for ALL cargos, especially visible soil/sand/crops heap!)
                val hasActiveCargo = pieces.any { !it.spilled } && cargo != CargoType.NONE
                if (hasActiveCargo) {
                    val heap = Path().apply {
                        moveTo(-len * 0.46f, floorY)
                        quadraticTo(-len * 0.24f, floorY - bedH * 1.48f, 0f, floorY - bedH * 1.58f)
                        quadraticTo(len * 0.24f, floorY - bedH * 1.42f, len * 0.46f, floorY)
                        close()
                    }
                    val (topCol, botCol) = when (cargo) {
                        CargoType.SAND -> Color(0xFFE5C887) to Color(0xFF9C7E40)
                        CargoType.SOIL -> Color(0xFF7D512B) to Color(0xFF3E2612)
                        CargoType.CROPS -> Color(0xFFE7BA46) to Color(0xFF8C6615)
                        CargoType.BRICKS -> Color(0xFF8C2D19) to Color(0xFF4F170B)
                        CargoType.WOOD -> Color(0xFF6B4223) to Color(0xFF361F0E)
                        CargoType.STONES -> Color(0xFF737A82) to Color(0xFF3B3F44)
                        CargoType.NONE -> Color.Transparent to Color.Transparent
                    }
                    drawPath(heap, Brush.verticalGradient(listOf(topCol, botCol)))
                }

                // Individual bouncing/shifting cargo items clearly visible inside the open top half & heaped above rim!
                for (p in pieces) {
                    if (p.spilled) continue
                    val px = p.lx * ppm
                    val py = floorY - p.ly * ppm
                    drawRealisticCargoPiece(cargo, Offset(px, py), p.angle, ppm)
                }

                // Half-height Solid Steel Lower Sideboard (leaves upper 54% of cargo open to view!)
                val darkBody = Color(
                    red = trailer.bodyColor.red * 0.56f,
                    green = trailer.bodyColor.green * 0.56f,
                    blue = trailer.bodyColor.blue * 0.56f
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(trailer.bodyColor, darkBody)),
                    topLeft = Offset(-len * 0.50f, floorY - lowerWallH),
                    size = Size(len, lowerWallH),
                    cornerRadius = CornerRadius(0.03f * ppm)
                )

                // Open Steel Vertical Stake Ribs + Top Frame Rail (shows cargo clearly through the cage!)
                for (r in 0..4) {
                    val rx = -len * 0.46f + r * (len * 0.22f)
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(trailer.accentColor, darkBody)),
                        topLeft = Offset(rx, floorY - bedH),
                        size = Size(0.065f * ppm, bedH)
                    )
                }
                // Mid horizontal cage bar & top rail
                drawRect(trailer.bodyColor, Offset(-len * 0.51f, floorY - bedH * 0.74f), Size(len * 1.02f, 0.045f * ppm))
                drawRect(Color(0xFF1E2226), Offset(-len * 0.52f, floorY - bedH), Size(len * 1.04f, 0.065f * ppm))
                // Rear safety reflector strip
                drawRect(Color(0xFFE03A2E), Offset(-len * 0.50f, floorY - lowerWallH * 0.75f), Size(0.06f * ppm, lowerWallH * 0.35f))
            }

            // Real Photographic 9.00-16 Agricultural Trolley Tyre cleanly below chassis
            drawWheelSprite(
                bitmap = trailerWheel,
                center = Offset(wheelX, wheelY),
                radius = trailer.wheelRadius * ppm * 1.015f,
                rotationRad = wheelRot
            )
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

        // 1. Slack Steel Safety Chain under the coupling (prevents uncoupling on steep climbs)
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

        // 2. Forged Tractor Rear Hitch Bracket + Lower Clevis Plate (rotates with tractor body)
        withTransform({
            translate(hitchScreen.x, hitchScreen.y)
            rotate(tractorDeg, pivot = Offset.Zero)
        }) {
            // Tractor heavy drawbar arm extending from rear tyre center hub level to clevis fork
            drawLine(
                color = Color(0xFF1A1D22),
                start = Offset(0.46f * ppm, 0f),
                end = Offset(0f, 0f),
                strokeWidth = 0.115f * ppm,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF535B66),
                start = Offset(0.42f * ppm, -0.022f * ppm),
                end = Offset(0.02f * ppm, -0.022f * ppm),
                strokeWidth = 0.028f * ppm,
                cap = StrokeCap.Round
            )
            // Lower clevis jaw plate
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF474E58), Color(0xFF1F2329))),
                topLeft = Offset(-0.10f * ppm, 0.026f * ppm),
                size = Size(0.22f * ppm, 0.044f * ppm),
                cornerRadius = CornerRadius(0.018f * ppm)
            )
        }

        // 3. Trailer Drawbar Neck Collar + Forged Tow-Eye Ring (rotates independently with trailer!)
        withTransform({
            translate(tongueScreen.x, tongueScreen.y)
            rotate(trailerDeg, pivot = Offset.Zero)
        }) {
            // Heavy welded neck sleeve connecting A-frame to tow-eye
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF626A75), Color(0xFF282D34))),
                topLeft = Offset(-0.20f * ppm, -0.052f * ppm),
                size = Size(0.16f * ppm, 0.104f * ppm),
                cornerRadius = CornerRadius(0.025f * ppm)
            )
            // Forged steel tow-eye ring sandwiched inside the clevis slot
            drawCircle(
                color = Color(0xFF1E2228),
                radius = 0.078f * ppm,
                center = Offset.Zero
            )
            drawCircle(
                color = Color(0xFF7D8794),
                radius = 0.058f * ppm,
                center = Offset.Zero,
                style = Stroke(width = 0.028f * ppm)
            )
        }

        // 4. Upper Tractor Clevis Jaw Plate
        withTransform({
            translate(hitchScreen.x, hitchScreen.y)
            rotate(tractorDeg, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF5A626D), Color(0xFF252930))),
                topLeft = Offset(-0.10f * ppm, -0.070f * ppm),
                size = Size(0.22f * ppm, 0.044f * ppm),
                cornerRadius = CornerRadius(0.018f * ppm)
            )
        }

        // 5. Heavy Vertical Steel Locking Pin ("Killa") + Bright Safety Handle + Bottom Cotter Clip
        withTransform({
            translate(pinCenter.x, pinCenter.y)
            rotate((tractorDeg + trailerDeg) * 0.5f, pivot = Offset.Zero)
        }) {
            // Vertical machined steel pin shaft through upper clevis -> tow-eye -> lower clevis
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF6B737D), Color(0xFFD2DAE3), Color(0xFF454B54))),
                topLeft = Offset(-0.026f * ppm, -0.135f * ppm),
                size = Size(0.052f * ppm, 0.255f * ppm),
                cornerRadius = CornerRadius(0.015f * ppm)
            )
            // Red/amber painted pin pull-handle loop at top so driver/player spots the joint clearly
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFF8A33), Color(0xFFC43B16))),
                topLeft = Offset(-0.068f * ppm, -0.175f * ppm),
                size = Size(0.136f * ppm, 0.048f * ppm),
                cornerRadius = CornerRadius(0.022f * ppm)
            )
            drawCircle(
                color = Color(0xFFFFE0B2),
                radius = 0.014f * ppm,
                center = Offset(-0.032f * ppm, -0.154f * ppm)
            )
            // Bottom steel R-clip / lock cotter pin below lower clevis jaw
            drawLine(
                color = Color(0xFFE0B43A),
                start = Offset(-0.048f * ppm, 0.095f * ppm),
                end = Offset(0.052f * ppm, 0.095f * ppm),
                strokeWidth = 0.022f * ppm,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawRealisticCargoPiece(cargo: CargoType, pos: Offset, rotRad: Float, ppm: Float) {
        withTransform({
            translate(pos.x, pos.y)
            rotate(rotRad * 180f / PI.toFloat(), pivot = Offset.Zero)
        }) {
            when (cargo) {
                CargoType.CROPS -> {
                    drawOval(Brush.verticalGradient(listOf(Color(0xFFDDA835), Color(0xFF8A6314))), Offset(-0.25f * ppm, -0.15f * ppm), Size(0.50f * ppm, 0.30f * ppm))
                    drawLine(Color(0xFF345E24), Offset(-0.18f * ppm, 0f), Offset(0.18f * ppm, 0f), strokeWidth = 0.04f * ppm)
                }
                CargoType.SOIL -> drawCircle(Color(0xFF4E3016).copy(alpha = 0.6f), 0.11f * ppm, Offset.Zero)
                CargoType.SAND -> drawCircle(Color(0xFFC2A05C).copy(alpha = 0.55f), 0.10f * ppm, Offset.Zero)
                CargoType.WOOD -> {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF7D4E2A), Color(0xFF3E2411))),
                        topLeft = Offset(-0.34f * ppm, -0.11f * ppm),
                        size = Size(0.68f * ppm, 0.22f * ppm),
                        cornerRadius = CornerRadius(0.09f * ppm)
                    )
                    drawCircle(Color(0xFFC29769), 0.085f * ppm, Offset(0.28f * ppm, 0f))
                }
                CargoType.BRICKS -> {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFB34229), Color(0xFF6E2110))), Offset(-0.22f * ppm, -0.11f * ppm), Size(0.44f * ppm, 0.22f * ppm))
                    drawRect(Color(0xFF331109), Offset(-0.22f * ppm, -0.11f * ppm), Size(0.44f * ppm, 0.22f * ppm), style = Stroke(2f))
                }
                CargoType.STONES -> {
                    val rock = Path().apply {
                        moveTo(-0.22f * ppm, 0.10f * ppm)
                        lineTo(-0.16f * ppm, -0.16f * ppm)
                        lineTo(0.08f * ppm, -0.20f * ppm)
                        lineTo(0.24f * ppm, 0.05f * ppm)
                        lineTo(0.10f * ppm, 0.16f * ppm)
                        close()
                    }
                    drawPath(rock, Brush.verticalGradient(listOf(Color(0xFF838A92), Color(0xFF3F4348))))
                    drawPath(rock, Color(0xFF232529), style = Stroke(2f, join = StrokeJoin.Round))
                }
                CargoType.NONE -> Unit
            }
        }
    }

    private fun DrawScope.drawCheckpointFlag(sx: Float, sy: Float, ppm: Float, color: Color) {
        drawRect(Color(0xFFB0B7BF), Offset(sx - ppm * 0.05f, sy - ppm * 2.4f), Size(ppm * 0.10f, ppm * 2.4f))
        val flag = Path().apply {
            moveTo(sx + ppm * 0.05f, sy - ppm * 2.4f)
            lineTo(sx + ppm * 1.15f, sy - ppm * 2.05f)
            lineTo(sx + ppm * 0.05f, sy - ppm * 1.65f)
            close()
        }
        drawPath(flag, color)
    }

    private fun DrawScope.drawCoin(sx: Float, sy: Float, ppm: Float, phase: Float) {
        val squeeze = (0.35f + 0.65f * abs(cos(phase * 2.2f)))
        val rx = ppm * 0.22f * squeeze
        val ry = ppm * 0.22f
        drawOval(Color(0xFF9E6100), Offset(sx - rx, sy - ry), Size(rx * 2f, ry * 2f))
        drawOval(Color(0xFFE6B43A), Offset(sx - rx * 0.76f, sy - ry * 0.76f), Size(rx * 1.52f, ry * 1.52f))
    }

    private fun DrawScope.drawFuelCan(sx: Float, sy: Float, ppm: Float) {
        val w = ppm * 0.54f
        val h = ppm * 0.66f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF208A42), Color(0xFF105425))),
            topLeft = Offset(sx - w * 0.5f, sy - h * 0.5f),
            size = Size(w, h),
            cornerRadius = CornerRadius(ppm * 0.07f)
        )
        drawRoundRect(Color(0xFFD8E0DA), Offset(sx - w * 0.5f, sy - h * 0.5f), Size(w, h), CornerRadius(ppm * 0.07f), style = Stroke(2f))
        drawRect(Color(0xFFD68E1E), Offset(sx + w * 0.1f, sy - h * 0.64f), Size(w * 0.24f, h * 0.17f))
        drawRect(Color.White, Offset(sx - w * 0.20f, sy - h * 0.06f), Size(w * 0.40f, h * 0.12f))
        drawRect(Color.White, Offset(sx - w * 0.06f, sy - h * 0.20f), Size(w * 0.12f, h * 0.40f))
    }
}
