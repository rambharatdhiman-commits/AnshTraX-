package com.example.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

class ProceduralTextures {
    val earthGrainBrush: ShaderBrush
    val roadGritBrush: ShaderBrush

    init {
        earthGrainBrush = ShaderBrush(
            ImageShader(
                image = createNoiseTile(size = 128, seed = 101, pebbleDensity = 0.16f),
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
        )
        roadGritBrush = ShaderBrush(
            ImageShader(
                image = createNoiseTile(size = 96, seed = 202, pebbleDensity = 0.25f),
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
        )
    }

    private fun createNoiseTile(size: Int, seed: Int, pebbleDensity: Float): ImageBitmap {
        val rng = Random(seed)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val r = rng.nextFloat()
            val alpha = when {
                r < pebbleDensity * 0.4f -> (38 + rng.nextInt(45)) // dark grit
                r < pebbleDensity -> (24 + rng.nextInt(32)) // light mineral fleck
                else -> (8 + rng.nextInt(14)) // subtle organic grain
            }
            val shade = if (r < pebbleDensity * 0.4f) 0x0A else 0xE6
            pixels[i] = (alpha shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp.asImageBitmap()
    }
}
