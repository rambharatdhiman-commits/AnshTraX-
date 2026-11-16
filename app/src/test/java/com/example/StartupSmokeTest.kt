package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.audio.SoundEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w800dp-h400dp-xxhdpi")
class StartupSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndNavigatesAllScreensWithoutCrashing() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("play_now_button").assertExists()

        composeRule.onNodeWithTag("garage_button").performClick()
        repeat(25) { composeRule.mainClock.advanceTimeBy(16L) }
        composeRule.onNodeWithTag("garage_back_button").assertExists()
        composeRule.onNodeWithTag("garage_back_button").performClick()
        repeat(25) { composeRule.mainClock.advanceTimeBy(16L) }

        composeRule.onNodeWithTag("maps_button").performClick()
        repeat(25) { composeRule.mainClock.advanceTimeBy(16L) }
        composeRule.onNodeWithTag("maps_back_button").assertExists()
        composeRule.onNodeWithTag("maps_back_button").performClick()
        repeat(25) { composeRule.mainClock.advanceTimeBy(16L) }

        composeRule.onNodeWithTag("play_now_button").performClick()
        repeat(25) { composeRule.mainClock.advanceTimeBy(16L) }
        composeRule.onNodeWithTag("throttle_button").assertExists()
        composeRule.onNodeWithTag("power_button").assertExists()
    }

    @Test
    fun soundEngineStartAndStopHandlesCleanLifecycle() {
        val engine = SoundEngine(null)
        engine.start()
        engine.inGameplay = true
        engine.engineRpm = 1200f
        Thread.sleep(30)
        engine.stop()
    }
}
