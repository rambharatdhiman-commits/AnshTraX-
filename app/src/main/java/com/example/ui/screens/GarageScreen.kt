package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upgrade
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CargoType
import com.example.model.DriverCharacter
import com.example.model.PlayerProgress
import com.example.model.TractorModel
import com.example.model.TrailerType
import com.example.model.UpgradeType
import com.example.render.TractorArtAssets
import com.example.render.WorldRenderer.renderGaragePreview
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CanopyCard
import com.example.ui.theme.CreamInk
import com.example.ui.theme.CropEmerald
import com.example.ui.theme.DieselCharcoal
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.MutedWheat
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TractorCrimson

@Composable
fun GarageScreen(
    progress: PlayerProgress,
    onBack: () -> Unit,
    onPlayNow: () -> Unit,
    onSelectTractor: (TractorModel) -> Unit,
    onSelectDriver: (DriverCharacter) -> Unit,
    onSelectTrailer: (TrailerType) -> Unit,
    onSelectCargo: (CargoType) -> Unit,
    onPurchaseUpgrade: (UpgradeType) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("TRACTORS", "DRIVERS", "TRAILERS & CARGO", "UPGRADES")

    // Load realistic tractor, wheel & driver face sprites for live workshop preview
    val context = LocalContext.current
    val artAssets = remember(progress.selectedTractor, progress.selectedDriver) {
        TractorArtAssets.load(context.resources, progress.selectedTractor, progress.selectedDriver)
    }

    val animTimeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNs = now
            animTimeState.floatValue += dt
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DieselCharcoal)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AsphaltSurface)
                        .border(1.dp, SteelBorder, CircleShape)
                        .testTag("garage_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamInk)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("ANSHTRAX GARAGE", style = MaterialTheme.typography.headlineMedium, color = HarvestGold)
                    Text("Customize Tractor, Driver, Trailer, Cargo & Upgrades", style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = AsphaltSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, HarvestGold)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = HarvestGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${progress.coins}", style = MaterialTheme.typography.titleLarge, color = CreamInk)
                    }
                }

                Button(
                    onClick = onPlayNow,
                    colors = ButtonDefaults.buttonColors(containerColor = TractorCrimson, contentColor = CreamInk),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("garage_drive_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DRIVE", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Split-Pane Content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left: Live Workshop Canvas Preview + Spec Summary
            Card(
                colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SteelBorder),
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        GaragePreviewCanvas(
                            progress = progress,
                            assets = artAssets,
                            animTimeState = animTimeState
                        )
                        Surface(
                            color = DieselCharcoal.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${progress.selectedTractor.displayName} • ${progress.selectedDriver.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = HarvestGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Bottom Rig Telemetry Strip
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CanopyCard)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val totalKg = (progress.selectedTractor.massKg +
                            progress.selectedTrailer.emptyMassKg +
                            progress.selectedCargo.totalMassKg).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Trailer: ${progress.selectedTrailer.displayName}", style = MaterialTheme.typography.bodyMedium, color = CreamInk)
                            Text("Weight: ${totalKg} kg", style = MaterialTheme.typography.labelSmall, color = HarvestGold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cargo: ${progress.selectedCargo.displayName}", style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                            Text("Bonus: ${progress.selectedCargo.rewardMultiplier}x Coins", style = MaterialTheme.typography.labelSmall, color = CropEmerald)
                        }
                    }
                }
            }

            // Right: Tabs & Scrollable Selection List
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) HarvestGold else CanopyCard)
                                .border(1.dp, if (active) HarvestGold else SteelBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedTab = idx }
                                .testTag("garage_tab_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                                color = if (active) DieselCharcoal else CreamInk,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> TractorTabList(progress, onSelectTractor)
                    1 -> DriverTabList(progress, onSelectDriver)
                    2 -> TrailerAndCargoTabList(progress, onSelectTrailer, onSelectCargo)
                    3 -> UpgradesTabList(progress, onPurchaseUpgrade)
                }
            }
        }
    }
}

@Composable
private fun TractorTabList(progress: PlayerProgress, onSelect: (TractorModel) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(TractorModel.entries) { tractor ->
            val unlocked = tractor.id in progress.unlockedTractors
            val selected = tractor == progress.selectedTractor
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) CanopyCard else AsphaltSurface),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) HarvestGold else SteelBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(tractor) }
                    .testTag("tractor_card_${tractor.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tractor.primaryColor)
                                .border(2.dp, tractor.secondaryColor, RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(tractor.displayName, style = MaterialTheme.typography.titleLarge, color = CreamInk)
                            Text(tractor.tagline, style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpecBadge("PWR ${(tractor.basePower * 100).toInt()}")
                                SpecBadge("TRQ ${(tractor.baseTorque * 100).toInt()}")
                                SpecBadge("${tractor.massKg.toInt()} kg")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    ActionStatusBadge(unlocked = unlocked, selected = selected, cost = tractor.unlockCost)
                }
            }
        }
    }
}

@Composable
private fun DriverTabList(progress: PlayerProgress, onSelect: (DriverCharacter) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(DriverCharacter.entries) { driver ->
            val selected = driver == progress.selectedDriver
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) CanopyCard else AsphaltSurface),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) HarvestGold else SteelBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(driver) }
                    .testTag("driver_card_${driver.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Image(
                            painter = painterResource(id = driver.faceDrawable),
                            contentDescription = "${driver.displayName} face photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (selected) HarvestGold else driver.shirtAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(driver.displayName, style = MaterialTheme.typography.titleLarge, color = CreamInk)
                            Text(driver.roleTitle, style = MaterialTheme.typography.labelSmall, color = HarvestGold)
                            Text("\"${driver.quote}\"", style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                        }
                    }

                    ActionStatusBadge(unlocked = true, selected = selected, cost = 0)
                }
            }
        }
    }
}

@Composable
private fun TrailerAndCargoTabList(
    progress: PlayerProgress,
    onSelectTrailer: (TrailerType) -> Unit,
    onSelectCargo: (CargoType) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Text("TRAILERS & HITCH", style = MaterialTheme.typography.labelLarge, color = HarvestGold)
        }
        items(TrailerType.entries) { trailer ->
            val unlocked = trailer.id in progress.unlockedTrailers
            val selected = trailer == progress.selectedTrailer
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) CanopyCard else AsphaltSurface),
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) HarvestGold else SteelBorder
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTrailer(trailer) }
                    .testTag("trailer_card_${trailer.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trailer.displayName, style = MaterialTheme.typography.titleMedium, color = CreamInk)
                        Text(
                            "${trailer.description} • ${trailer.emptyMassKg.toInt()} kg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedWheat
                        )
                    }
                    ActionStatusBadge(unlocked = unlocked, selected = selected, cost = trailer.unlockCost)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("CARGO LOADS (AFFECTS WEIGHT & COIN BONUS)", style = MaterialTheme.typography.labelLarge, color = CropEmerald)
        }
        items(CargoType.entries) { cargo ->
            val unlocked = cargo.id in progress.unlockedCargos
            val selected = cargo == progress.selectedCargo
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) CanopyCard else AsphaltSurface),
                border = androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) CropEmerald else SteelBorder
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCargo(cargo) }
                    .testTag("cargo_card_${cargo.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (cargo == CargoType.NONE) SteelBorder else cargo.primaryColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(cargo.displayName, style = MaterialTheme.typography.titleMedium, color = CreamInk)
                            Text(
                                "Load: ${cargo.totalMassKg.toInt()} kg • Reward: ${cargo.rewardMultiplier}x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedWheat
                            )
                        }
                    }
                    ActionStatusBadge(unlocked = unlocked, selected = selected, cost = cargo.unlockCost)
                }
            }
        }
    }
}

@Composable
private fun UpgradesTabList(
    progress: PlayerProgress,
    onPurchaseUpgrade: (UpgradeType) -> Unit
) {
    val tractorUpgrades = progress.upgradesFor(progress.selectedTractor)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Text(
                "UPGRADING: ${progress.selectedTractor.displayName.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = HarvestGold
            )
        }
        items(UpgradeType.entries) { upg ->
            val lvl = tractorUpgrades.levelOf(upg)
            val isMax = lvl >= upg.maxLevel
            val cost = upg.upgradeCost(lvl)
            Card(
                colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(upg.displayName, style = MaterialTheme.typography.titleMedium, color = CreamInk)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lv.$lvl/${upg.maxLevel}", style = MaterialTheme.typography.labelSmall, color = HarvestGold)
                        }
                        Text(upg.shortHint, style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { lvl.toFloat() / upg.maxLevel.toFloat() },
                            color = CropEmerald,
                            trackColor = SteelBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onPurchaseUpgrade(upg) },
                        enabled = !isMax,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMax) SteelBorder else HarvestGold,
                            contentColor = DieselCharcoal
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("upgrade_btn_${upg.id}")
                    ) {
                        if (isMax) {
                            Text("MAX", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$cost", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(text: String) {
    Surface(
        color = DieselCharcoal,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = HarvestGold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ActionStatusBadge(unlocked: Boolean, selected: Boolean, cost: Int) {
    when {
        selected -> {
            Surface(color = CropEmerald, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DieselCharcoal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ACTIVE", style = MaterialTheme.typography.labelLarge, color = DieselCharcoal)
                }
            }
        }
        unlocked -> {
            Surface(color = SteelBorder, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "SELECT",
                    style = MaterialTheme.typography.labelLarge,
                    color = CreamInk,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        else -> {
            Surface(color = HarvestGold, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = DieselCharcoal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$cost", style = MaterialTheme.typography.labelLarge, color = DieselCharcoal)
                }
            }
        }
    }
}

@Composable
private fun GaragePreviewCanvas(
    progress: PlayerProgress,
    assets: TractorArtAssets,
    animTimeState: State<Float>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        renderGaragePreview(
            tractor = progress.selectedTractor,
            driver = progress.selectedDriver,
            trailer = progress.selectedTrailer,
            cargo = progress.selectedCargo,
            assets = assets,
            animTime = animTimeState.value
        )
    }
}
