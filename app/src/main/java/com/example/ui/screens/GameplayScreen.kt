package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.example.engine.HudSnapshot
import com.example.engine.PhysicsWorld
import com.example.engine.RunPhase
import com.example.model.CargoType
import com.example.model.PlayerProgress
import com.example.render.TractorArtAssets
import com.example.render.WorldRenderer.renderWorld
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CanopyCard
import com.example.ui.theme.CreamInk
import com.example.ui.theme.CropEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DieselCharcoal
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.MutedWheat
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TractorCrimson
import com.example.ui.theme.WarningOrange

@Composable
fun GameplayScreen(
    world: PhysicsWorld,
    progress: PlayerProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onOpenGarage: () -> Unit,
    onExitMenu: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleSfx: () -> Unit,
    onRunFinished: (PhysicsWorld) -> Unit
) {
    BackHandler {
        when (world.phase) {
            RunPhase.RUNNING -> onPause()
            RunPhase.PAUSED -> onResume()
            else -> onExitMenu()
        }
    }

    val context = LocalContext.current
    val artAssets = remember(world.tractor, world.driver) {
        TractorArtAssets.load(context.resources, world.tractor, world.driver)
    }

    var hud by remember(world) { mutableStateOf(world.snapshotHud()) }
    val frameTickState = remember(world) { mutableLongStateOf(0L) }
    val elapsedVisualRef = remember(world) { floatArrayOf(0f) }

    var gasPressed by remember(world) { mutableStateOf(false) }
    var brakePressed by remember(world) { mutableStateOf(false) }
    var handbrakePressed by remember(world) { mutableStateOf(false) }
    var powerModeOn by remember(world) { mutableStateOf(world.powerModeInput) }

    // Keep live inputs synced into PhysicsWorld on initial/recomposed frame as fallback
    world.throttleInput = if (gasPressed) 1f else 0f
    world.brakeInput = if (brakePressed) 1f else 0f
    world.handbrakeInput = handbrakePressed
    world.powerModeInput = powerModeOn

    // 60fps Game Loop — ONLY invalidates WorldCanvas DrawScope at 60fps; HUD throttled to ~12Hz!
    LaunchedEffect(world) {
        var lastNs = withFrameNanos { it }
        var hudAccumSec = 0f
        var lastPhase = world.phase
        while (true) {
            val nowNs = withFrameNanos { it }
            val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.045f)
            lastNs = nowNs

            world.step(dt)
            elapsedVisualRef[0] += dt
            frameTickState.longValue++

            hudAccumSec += dt
            if (hudAccumSec >= 0.085f || world.phase != lastPhase) {
                hudAccumSec = 0f
                lastPhase = world.phase
                val snap = world.snapshotHud()
                hud = snap
                if (snap.phase == RunPhase.GAME_OVER || snap.phase == RunPhase.MISSION_COMPLETE) {
                    onRunFinished(world)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Isolated 60fps 2D World Canvas (reads frameTick ONLY inside draw lambda!)
        WorldCanvasLayer(
            world = world,
            assets = artAssets,
            frameTickState = frameTickState,
            elapsedVisualRef = elapsedVisualRef
        )

        // 2. Landscape Top HUD & Bottom Multi-Touch Pedals
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HUD BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Top-Left: Driver Avatar + Tractor + Map/Surface
                Surface(
                    color = DieselCharcoal.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = world.driver.faceDrawable),
                            contentDescription = world.driver.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, HarvestGold, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${world.tractor.displayName} • ${world.driver.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CreamInk
                            )
                            Text(
                                text = "${world.map.displayName} (${hud.currentSurface.name})",
                                style = MaterialTheme.typography.labelSmall,
                                color = HarvestGold
                            )
                        }
                    }
                }

                // Top-Center: Fuel, Distance, Coins & Optional Mission Progress
                Surface(
                    color = DieselCharcoal.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Fuel Bar
                            val fuelFrac = (hud.fuel / hud.maxFuel).coerceIn(0f, 1f)
                            val fuelColor = when {
                                fuelFrac < 0.22f -> DangerRed
                                fuelFrac < 0.45f -> WarningOrange
                                else -> CropEmerald
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalGasStation, contentDescription = "Fuel", tint = fuelColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                LinearProgressIndicator(
                                    progress = { fuelFrac },
                                    color = fuelColor,
                                    trackColor = SteelBorder,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(9.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                )
                            }

                            Text(
                                text = "${hud.distanceMeters} m",
                                style = MaterialTheme.typography.titleLarge,
                                color = CreamInk
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = HarvestGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("+${hud.coinsEarned}", style = MaterialTheme.typography.titleMedium, color = HarvestGold)
                            }
                        }

                        // Mission progress bar
                        world.mission?.let { m ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${m.title}: ${hud.distanceMeters}/${m.targetDistanceMeters}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CropEmerald
                                )
                                LinearProgressIndicator(
                                    progress = { hud.missionProgressPct },
                                    color = CropEmerald,
                                    trackColor = SteelBorder,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                hud.timeRemainingSec?.let { t ->
                                    Text(
                                        text = "${t}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (t <= 15) DangerRed else HarvestGold
                                    )
                                }
                            }
                        }
                    }
                }

                // Top-Right: Analog + Digital Tractor Instrument Cluster + Audio + Pause
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TractorInstrumentCluster(
                        speedKmh = hud.speedKmh,
                        rpm = hud.rpm,
                        gear = hud.gear
                    )

                    IconButton(
                        onClick = onToggleSfx,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DieselCharcoal.copy(alpha = 0.82f))
                            .border(1.dp, SteelBorder, CircleShape)
                            .testTag("hud_sfx_button")
                    ) {
                        Icon(
                            imageVector = if (progress.sfxEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Sound",
                            tint = if (progress.sfxEnabled) CropEmerald else MutedWheat
                        )
                    }

                    IconButton(
                        onClick = onPause,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(TractorCrimson.copy(alpha = 0.92f))
                            .border(1.5.dp, HarvestGold, CircleShape)
                            .testTag("pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = CreamInk)
                    }
                }
            }

            // Center Notification Banners (Checkpoint / Unloading / Low Fuel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    AnimatedVisibility(
                        visible = hud.checkpointBannerTimer > 0f,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = CropEmerald,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, CreamInk)
                        ) {
                            Text(
                                text = "CHECKPOINT ${hud.lastCheckpointMeters}m! +60 COINS & +25% DIESEL",
                                style = MaterialTheme.typography.labelLarge,
                                color = DieselCharcoal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (hud.phase == RunPhase.UNLOADING) {
                        Surface(
                            color = HarvestGold,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, DieselCharcoal)
                        ) {
                            Text(
                                text = "DESTINATION REACHED! UNLOADING ${world.cargo.displayName.uppercase()}...",
                                style = MaterialTheme.typography.titleLarge,
                                color = DieselCharcoal,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    } else if (hud.fuel <= hud.maxFuel * 0.18f && hud.fuel > 0f) {
                        Surface(
                            color = DangerRed.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "LOW DIESEL! GRAB A GREEN CAN!",
                                style = MaterialTheme.typography.labelLarge,
                                color = CreamInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // BOTTOM LANDSCAPE TRANSPARENT TOUCH PEDALS & SWITCHES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left Controls: Transparent Brake Pedal + Transparent Park Brake Switch
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    RealisticFootPedal(
                        label = "BRAKE",
                        subLabel = "REV",
                        isPressed = brakePressed,
                        accentColor = DangerRed,
                        modifier = Modifier
                            .width(96.dp)
                            .height(94.dp)
                            .testTag("brake_button"),
                        onPressedChange = { pressed ->
                            brakePressed = pressed
                            world.brakeInput = if (pressed) 1f else 0f
                        }
                    )

                    RealisticDashSwitch(
                        label = "PARK",
                        isPressed = handbrakePressed,
                        activeColor = WarningOrange,
                        modifier = Modifier
                            .width(68.dp)
                            .height(64.dp)
                            .testTag("handbrake_button"),
                        onPressedChange = { pressed ->
                            handbrakePressed = pressed
                            world.handbrakeInput = pressed
                        }
                    )
                }

                // Right Controls: Transparent Power Boost Switch + Long Transparent Accelerator Pedal
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PowerBoostToggleButton(
                        isEnabled = powerModeOn,
                        modifier = Modifier
                            .width(74.dp)
                            .height(64.dp)
                            .testTag("power_button"),
                        onToggle = { next ->
                            powerModeOn = next
                            world.setPowerMode(next)
                        }
                    )

                    RealisticFootPedal(
                        label = "RACE",
                        subLabel = "GAS",
                        isPressed = gasPressed,
                        accentColor = CropEmerald,
                        isLongThrottle = true,
                        modifier = Modifier
                            .width(94.dp)
                            .height(108.dp)
                            .testTag("throttle_button"),
                        onPressedChange = { pressed ->
                            gasPressed = pressed
                            world.throttleInput = if (pressed) 1f else 0f
                        }
                    )
                }
            }
        }

        // 3. Pause / Game Over / Mission Complete Overlays
        when (hud.phase) {
            RunPhase.PAUSED -> {
                PauseMenuOverlay(
                    musicEnabled = progress.musicEnabled,
                    sfxEnabled = progress.sfxEnabled,
                    onResume = onResume,
                    onRestart = onRestart,
                    onGarage = onOpenGarage,
                    onExitMenu = onExitMenu,
                    onToggleMusic = onToggleMusic,
                    onToggleSfx = onToggleSfx
                )
            }
            RunPhase.GAME_OVER -> {
                GameOverOverlay(
                    hud = hud,
                    world = world,
                    onRetry = onRestart,
                    onGarage = onOpenGarage,
                    onExitMenu = onExitMenu
                )
            }
            RunPhase.MISSION_COMPLETE -> {
                MissionCompleteOverlay(
                    hud = hud,
                    world = world,
                    onReplay = onRestart,
                    onGarage = onOpenGarage,
                    onExitMenu = onExitMenu
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun TractorInstrumentCluster(
    speedKmh: Int,
    rpm: Int,
    gear: Int
) {
    val gearLabel = if (gear == 0) "R" else "$gear"
    Surface(
        color = DieselCharcoal.copy(alpha = 0.86f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, SteelBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Analog Dial Tachometer
            Canvas(modifier = Modifier.size(46.dp)) {
                val r = size.minDimension * 0.48f
                val cx = size.width * 0.5f
                val cy = size.height * 0.54f
                // Dial bezel & dark matte face
                drawCircle(Color(0xFF0E1012), r, Offset(cx, cy))
                drawCircle(Color(0xFF565E66), r, Offset(cx, cy), style = Stroke(2.2f))

                // Tick marks from 140 deg to 400 deg (260 deg sweep)
                val maxRpm = 2400f
                val rpmFrac = (rpm / maxRpm).coerceIn(0f, 1f)
                for (i in 0..8) {
                    val deg = 140f + (i / 8f) * 260f
                    val rad = deg * PI.toFloat() / 180f
                    val inner = r * 0.72f
                    val outer = r * 0.90f
                    val tickCol = if (i >= 7) DangerRed else CreamInk.copy(alpha = 0.8f)
                    drawLine(
                        color = tickCol,
                        start = Offset(cx + cos(rad) * inner, cy + sin(rad) * inner),
                        end = Offset(cx + cos(rad) * outer, cy + sin(rad) * outer),
                        strokeWidth = if (i % 2 == 0) 2.2f else 1.2f,
                        cap = StrokeCap.Round
                    )
                }

                // Needle
                val needleDeg = 140f + rpmFrac * 260f
                val needleRad = needleDeg * PI.toFloat() / 180f
                drawLine(
                    color = TractorCrimson,
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(needleRad) * (r * 0.76f), cy + sin(needleRad) * (r * 0.76f)),
                    strokeWidth = 2.8f,
                    cap = StrokeCap.Round
                )
                drawCircle(HarvestGold, r * 0.14f, Offset(cx, cy))
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "$speedKmh KM/H",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                        color = HarvestGold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (gear == 0) DangerRed else CropEmerald.copy(alpha = 0.25f))
                            .border(1.dp, if (gear == 0) DangerRed else CropEmerald, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = gearLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamInk
                        )
                    }
                }
                Text(
                    text = "$rpm RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedWheat
                )
            }
        }
    }
}

@Composable
private fun WorldCanvasLayer(
    world: PhysicsWorld,
    assets: TractorArtAssets,
    frameTickState: State<Long>,
    elapsedVisualRef: FloatArray
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read frameTick ONLY inside DrawScope so parent GameplayScreen does NOT recompose 60x/sec
        @Suppress("UNUSED_EXPRESSION")
        frameTickState.value
        renderWorld(world = world, assets = assets, timeSec = elapsedVisualRef[0])
    }
}

@Composable
private fun RealisticFootPedal(
    label: String,
    subLabel: String,
    isPressed: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isLongThrottle: Boolean = false,
    onPressedChange: (Boolean) -> Unit
) {
    val latestOnPressed by rememberUpdatedState(onPressedChange)

    // Outer Box keeps a completely stationary touch hitbox (never shifts/scales under finger!)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var wasDown = false
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                            val anyDown = event.changes.any { it.pressed }
                            if (anyDown != wasDown) {
                                wasDown = anyDown
                                latestOnPressed(anyDown)
                            }
                            for (change in event.changes) {
                                change.consume()
                            }
                        }
                    }
                } finally {
                    if (wasDown) {
                        latestOnPressed(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (isPressed) 5f else 0f
                    scaleX = if (isPressed) 0.96f else 1f
                    scaleY = if (isPressed) 0.95f else 1f
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val corner = 12.dp.toPx()

                // Transparent frosted-glass pedal housing so road/wheels remain clearly visible
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(accentColor.copy(alpha = 0.34f), DieselCharcoal.copy(alpha = 0.30f))
                        } else {
                            listOf(DieselCharcoal.copy(alpha = 0.24f), Color.Black.copy(alpha = 0.16f))
                        }
                    ),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(corner, corner)
                )

                // Crisp translucent border ring
                drawRoundRect(
                    color = if (isPressed) accentColor.copy(alpha = 0.95f) else CreamInk.copy(alpha = 0.46f),
                    topLeft = Offset(1.5f, 1.5f),
                    size = Size(w - 3f, h - 3f),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = if (isPressed) 2.5.dp.toPx() else 1.4.dp.toPx())
                )

                // Subtle translucent tread ribs
                val padInset = 8.dp.toPx()
                val padH = h - padInset * 2
                val ribCount = if (isLongThrottle) 7 else 5
                val topRibY = padInset + 10.dp.toPx()
                val ribSpacing = (padH - 22.dp.toPx()) / ribCount
                for (r in 0 until ribCount) {
                    val ry = topRibY + r * ribSpacing
                    drawLine(
                        color = if (isPressed) accentColor.copy(alpha = 0.55f) else CreamInk.copy(alpha = 0.22f),
                        start = Offset(padInset + 8.dp.toPx(), ry),
                        end = Offset(w - padInset - 8.dp.toPx(), ry),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DieselCharcoal.copy(alpha = if (isPressed) 0.55f else 0.34f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = if (isPressed) accentColor else CreamInk
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = CreamInk.copy(alpha = 0.80f)
                )
            }
        }
    }
}

@Composable
private fun RealisticDashSwitch(
    label: String,
    isPressed: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onPressedChange: (Boolean) -> Unit
) {
    val latestOnPressed by rememberUpdatedState(onPressedChange)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var wasDown = false
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                            val anyDown = event.changes.any { it.pressed }
                            if (anyDown != wasDown) {
                                wasDown = anyDown
                                latestOnPressed(anyDown)
                            }
                            for (change in event.changes) {
                                change.consume()
                            }
                        }
                    }
                } finally {
                    if (wasDown) {
                        latestOnPressed(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (isPressed) 3f else 0f
                }
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isPressed) activeColor.copy(alpha = 0.42f) else DieselCharcoal.copy(alpha = 0.24f)
                )
                .border(
                    width = if (isPressed) 2.2.dp else 1.4.dp,
                    color = if (isPressed) activeColor else CreamInk.copy(alpha = 0.44f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                    color = if (isPressed) CreamInk else CreamInk.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun PowerBoostToggleButton(
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val latestEnabled by rememberUpdatedState(isEnabled)
    val latestOnToggle by rememberUpdatedState(onToggle)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var fingerWasDown = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                        val anyDown = event.changes.any { it.pressed }
                        if (anyDown && !fingerWasDown) {
                            fingerWasDown = true
                            latestOnToggle(!latestEnabled)
                        } else if (!anyDown && fingerWasDown) {
                            fingerWasDown = false
                        }
                        for (change in event.changes) {
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isEnabled) WarningOrange.copy(alpha = 0.42f) else DieselCharcoal.copy(alpha = 0.24f)
                )
                .border(
                    width = if (isEnabled) 2.2.dp else 1.4.dp,
                    color = if (isEnabled) HarvestGold else CreamInk.copy(alpha = 0.44f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Tractor Power Boost",
                    tint = if (isEnabled) HarvestGold else CreamInk.copy(alpha = 0.90f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (isEnabled) "PWR ON" else "POWER",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                    color = if (isEnabled) HarvestGold else CreamInk.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun PauseMenuOverlay(
    musicEnabled: Boolean,
    sfxEnabled: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onGarage: () -> Unit,
    onExitMenu: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleSfx: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DieselCharcoal.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, HarvestGold),
            modifier = Modifier.width(380.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("GAME PAUSED", style = MaterialTheme.typography.headlineMedium, color = HarvestGold)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onToggleMusic,
                        colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(if (musicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (musicEnabled) "MUSIC ON" else "MUSIC OFF", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onToggleSfx,
                        colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(if (sfxEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sfxEnabled) "SFX ON" else "SFX OFF", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = CropEmerald, contentColor = DieselCharcoal),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("resume_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESUME DRIVE", style = MaterialTheme.typography.titleLarge)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESTART", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onGarage,
                        colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GARAGE", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onExitMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = TractorCrimson, contentColor = CreamInk),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MAIN MENU", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(
    hud: HudSnapshot,
    world: PhysicsWorld,
    onRetry: () -> Unit,
    onGarage: () -> Unit,
    onExitMenu: () -> Unit
) {
    val reason = hud.gameOverReason
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DieselCharcoal.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, DangerRed),
            modifier = Modifier.width(420.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(reason?.title ?: "RUN ENDED", style = MaterialTheme.typography.headlineMedium, color = DangerRed)
                Text(reason?.subtitle ?: "", style = MaterialTheme.typography.bodyMedium, color = MutedWheat)

                Surface(
                    color = CanopyCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                            Text("${hud.distanceMeters} m", style = MaterialTheme.typography.titleLarge, color = CreamInk)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("COINS EARNED", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                            Text("+${hud.coinsEarned}", style = MaterialTheme.typography.titleLarge, color = HarvestGold)
                        }
                        if (world.hasTrailer && world.cargo != CargoType.NONE) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CARGO KEPT", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                                Text("${hud.cargoRemainingPct}%", style = MaterialTheme.typography.titleLarge, color = CropEmerald)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = CropEmerald, contentColor = DieselCharcoal),
                        modifier = Modifier.weight(1f).height(50.dp).testTag("retry_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RETRY", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onGarage,
                        colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = DieselCharcoal),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UPGRADE", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onExitMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MAIN MENU", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MissionCompleteOverlay(
    hud: HudSnapshot,
    world: PhysicsWorld,
    onReplay: () -> Unit,
    onGarage: () -> Unit,
    onExitMenu: () -> Unit
) {
    val missionReward = world.mission?.rewardCoins ?: 0
    val totalCoins = hud.coinsEarned + missionReward

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DieselCharcoal.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.5.dp, CropEmerald),
            modifier = Modifier.width(430.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = HarvestGold, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DELIVERY COMPLETE!", style = MaterialTheme.typography.headlineMedium, color = CropEmerald)
                }
                Text(world.mission?.title ?: "", style = MaterialTheme.typography.titleMedium, color = CreamInk)

                Surface(
                    color = CanopyCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                            Text("${hud.distanceMeters} m", style = MaterialTheme.typography.titleLarge, color = CreamInk)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MISSION BONUS", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                            Text("+$missionReward", style = MaterialTheme.typography.titleLarge, color = HarvestGold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL COINS", style = MaterialTheme.typography.labelSmall, color = MutedWheat)
                            Text("+$totalCoins", style = MaterialTheme.typography.titleLarge, color = CropEmerald)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onReplay,
                        colors = ButtonDefaults.buttonColors(containerColor = CropEmerald, contentColor = DieselCharcoal),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("REPLAY", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onGarage,
                        colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = DieselCharcoal),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GARAGE", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onExitMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = CanopyCard, contentColor = CreamInk),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MAIN MENU", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
