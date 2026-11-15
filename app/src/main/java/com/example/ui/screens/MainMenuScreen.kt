package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PlayerProgress
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CanopyCard
import com.example.ui.theme.CreamInk
import com.example.ui.theme.CropEmerald
import com.example.ui.theme.DieselCharcoal
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.MutedWheat
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TractorCrimson

@Composable
fun MainMenuScreen(
    progress: PlayerProgress,
    onPlayClick: () -> Unit,
    onGarageClick: () -> Unit,
    onMapsClick: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleSfx: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed Indian Tractor Hero Artwork
        Image(
            painter = painterResource(id = R.drawable.img_menu_hero_1794724492964),
            contentDescription = "AnshTraX countryside tractor key art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Rich cinematic gradient scrim so typography & controls pop clearly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            DieselCharcoal.copy(alpha = 0.90f),
                            DieselCharcoal.copy(alpha = 0.62f),
                            DieselCharcoal.copy(alpha = 0.35f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Logo & Audio/Coin Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TractorCrimson)
                            .border(2.dp, HarvestGold, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AT",
                            style = MaterialTheme.typography.titleLarge,
                            color = HarvestAmber,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "Ansh",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
                                color = CreamInk
                            )
                            Text(
                                text = "TraX",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
                                color = HarvestGold
                            )
                        }
                        Text(
                            text = "INDIAN TRACTOR HILL CLIMB & CARGO HAULER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedWheat
                        )
                    }
                }

                // Right Top Bar: Coins + Music + SFX Toggles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = AsphaltSurface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, HarvestGold)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Coins",
                                tint = HarvestGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${progress.coins}",
                                style = MaterialTheme.typography.titleLarge,
                                color = CreamInk
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleMusic,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AsphaltSurface.copy(alpha = 0.85f))
                            .border(1.dp, SteelBorder, CircleShape)
                            .testTag("toggle_music_button")
                    ) {
                        Icon(
                            imageVector = if (progress.musicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = "Toggle Music",
                            tint = if (progress.musicEnabled) HarvestGold else MutedWheat
                        )
                    }

                    IconButton(
                        onClick = onToggleSfx,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AsphaltSurface.copy(alpha = 0.85f))
                            .border(1.dp, SteelBorder, CircleShape)
                            .testTag("toggle_sfx_button")
                    ) {
                        Icon(
                            imageVector = if (progress.sfxEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Sound Effects",
                            tint = if (progress.sfxEnabled) CropEmerald else MutedWheat
                        )
                    }
                }
            }

            // Middle / Bottom Split: Action Buttons Left + Active Rig Card Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left Action Column
                Column(
                    modifier = Modifier.width(300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TractorCrimson,
                            contentColor = CreamInk
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .border(2.dp, HarvestGold, RoundedCornerShape(14.dp))
                            .testTag("play_now_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DRIVE NOW", style = MaterialTheme.typography.headlineMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onGarageClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CanopyCard.copy(alpha = 0.94f),
                                contentColor = CreamInk
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, SteelBorder, RoundedCornerShape(12.dp))
                                .testTag("garage_button")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = HarvestGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GARAGE", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = onMapsClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CanopyCard.copy(alpha = 0.94f),
                                contentColor = CreamInk
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .border(1.dp, SteelBorder, RoundedCornerShape(12.dp))
                                .testTag("maps_button")
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = CropEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MAPS & MISSIONS", style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
                        }
                    }
                }

                // Right Summary Card showing Active Driver (with Real Photo!), Tractor, Trailer, Cargo & Map
                Card(
                    colors = CardDefaults.cardColors(containerColor = AsphaltSurface.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SteelBorder),
                    modifier = Modifier.width(340.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = progress.selectedDriver.faceDrawable),
                                contentDescription = "${progress.selectedDriver.displayName} driver portrait",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, HarvestGold, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DRIVER: ${progress.selectedDriver.displayName.uppercase()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = HarvestGold
                                )
                                Text(
                                    text = progress.selectedTractor.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CreamInk
                                )
                                Text(
                                    text = "${progress.selectedTrailer.displayName} • ${progress.selectedCargo.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedWheat
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CanopyCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MAP: ${progress.selectedMap.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CropEmerald
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = HarvestGold, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${progress.completedMissions.size}/10",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CreamInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
