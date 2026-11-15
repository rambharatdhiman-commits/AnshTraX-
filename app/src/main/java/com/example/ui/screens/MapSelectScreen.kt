package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.GameCatalog
import com.example.model.MapEnvironment
import com.example.model.MissionDef
import com.example.model.PlayerProgress
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
fun MapSelectScreen(
    progress: PlayerProgress,
    onBack: () -> Unit,
    onSelectOrUnlockMap: (MapEnvironment) -> Unit,
    onDriveMap: (MapEnvironment) -> Unit,
    onStartMission: (MissionDef) -> Unit
) {
    BackHandler(onBack = onBack)
    var modeTab by remember { mutableIntStateOf(0) } // 0 = 10 Maps, 1 = Cargo Missions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DieselCharcoal)
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Header Row
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
                        .testTag("maps_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CreamInk)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("INDIAN MAPS & CARGO MISSIONS", style = MaterialTheme.typography.headlineMedium, color = HarvestGold)
                    Text("10 Distinct Endless Environments • Procedural Hills & Weather", style = MaterialTheme.typography.bodyMedium, color = MutedWheat)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Mode Toggle Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AsphaltSurface)
                        .border(1.dp, SteelBorder, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("10 MAPS", "MISSIONS").forEachIndexed { idx, label ->
                        val active = modeTab == idx
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) HarvestGold else Color.Transparent)
                                .clickable { modeTab = idx }
                                .padding(horizontal = 14.dp)
                                .testTag("maps_mode_tab_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) DieselCharcoal else CreamInk
                            )
                        }
                    }
                }

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
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (modeTab == 0) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(MapEnvironment.entries) { map ->
                    val unlocked = map.id in progress.unlockedMaps
                    val selected = map == progress.selectedMap
                    val bestDist = progress.bestDistances[map.id] ?: 0
                    MapCard(
                        map = map,
                        unlocked = unlocked,
                        selected = selected,
                        bestDistance = bestDist,
                        onSelectOrUnlock = { onSelectOrUnlockMap(map) },
                        onDrive = { onDriveMap(map) }
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(GameCatalog.missions) { mission ->
                    val completed = mission.id in progress.completedMissions
                    MissionCard(
                        mission = mission,
                        completed = completed,
                        onStart = { onStartMission(mission) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapCard(
    map: MapEnvironment,
    unlocked: Boolean,
    selected: Boolean,
    bestDistance: Int,
    onSelectOrUnlock: () -> Unit,
    onDrive: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) HarvestGold else SteelBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(248.dp)
            .clickable { onSelectOrUnlock() }
            .testTag("map_card_${map.id}")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Illustrated Sky + Hill Swatch Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        Brush.verticalGradient(listOf(map.skyTop, map.skyBottom))
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(map.roadTopColor)
                )
                Surface(
                    color = DieselCharcoal.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = map.difficultyLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = HarvestGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = DieselCharcoal.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = map.primarySurface.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CreamInk,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(map.displayName, style = MaterialTheme.typography.titleLarge, color = CreamInk)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(map.subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedWheat, maxLines = 2)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Best Distance: ${bestDistance}m", style = MaterialTheme.typography.labelSmall, color = CropEmerald)
                }

                if (unlocked) {
                    Button(
                        onClick = onDrive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TractorCrimson,
                            contentColor = CreamInk
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drive_map_${map.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DRIVE MAP", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onSelectOrUnlock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HarvestGold,
                            contentColor = DieselCharcoal
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("unlock_map_${map.id}")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UNLOCK • ${map.unlockCost}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionCard(
    mission: MissionDef,
    completed: Boolean,
    onStart: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (completed) 2.dp else 1.dp,
            color = if (completed) CropEmerald else SteelBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(268.dp)
            .testTag("mission_card_${mission.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = CanopyCard, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            mission.map.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = HarvestGold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    if (completed) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = CropEmerald)
                    }
                }

                Text(mission.title, style = MaterialTheme.typography.titleLarge, color = CreamInk)
                Text(mission.description, style = MaterialTheme.typography.bodyMedium, color = MutedWheat)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = DieselCharcoal, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "Cargo: ${mission.requiredCargo.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamInk,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (mission.timeLimitSeconds != null) {
                        Surface(color = DieselCharcoal, shape = RoundedCornerShape(6.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = HarvestGold, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${mission.timeLimitSeconds}s", style = MaterialTheme.typography.labelSmall, color = HarvestGold)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = HarvestGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reward: +${mission.rewardCoins} Coins", style = MaterialTheme.typography.titleMedium, color = HarvestGold)
                }

                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = CropEmerald, contentColor = DieselCharcoal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_mission_${mission.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (completed) "REPLAY MISSION" else "START MISSION", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
