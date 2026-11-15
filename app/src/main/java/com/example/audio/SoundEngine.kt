package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.R
import com.example.model.MapEnvironment
import com.example.model.SurfaceType
import com.example.model.TractorModel
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class SfxType {
    CLICK,
    COIN,
    FUEL,
    CHECKPOINT,
    BUMP,
    CRASH,
    MISSION_WIN,
    STARTER
}

private data class ActiveTone(
    val type: SfxType,
    var elapsed: Float = 0f,
    val duration: Float
)

internal object WavPcmCodec {
    fun parseMonoPcm16Wav(input: InputStream): FloatArray? {
        return try {
            val bytes = input.readBytes()
            if (bytes.size < 44) return null
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
                bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) return null
            if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
                bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
            ) return null

            var channels = 1
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0
            var pos = 12
            while (pos + 8 <= bytes.size) {
                val id0 = bytes[pos].toInt().toChar()
                val id1 = bytes[pos + 1].toInt().toChar()
                val id2 = bytes[pos + 2].toInt().toChar()
                val id3 = bytes[pos + 3].toInt().toChar()
                val chunkSize = bb.getInt(pos + 4)
                val bodyPos = pos + 8
                if (id0 == 'f' && id1 == 'm' && id2 == 't' && id3 == ' ') {
                    if (bodyPos + 16 <= bytes.size) {
                        channels = bb.getShort(bodyPos + 2).toInt() and 0xFFFF
                        bitsPerSample = bb.getShort(bodyPos + 14).toInt() and 0xFFFF
                    }
                } else if (id0 == 'd' && id1 == 'a' && id2 == 't' && id3 == 'a') {
                    dataOffset = bodyPos
                    dataSize = chunkSize.coerceAtMost(bytes.size - bodyPos)
                    break
                }
                val padded = chunkSize + (chunkSize and 1)
                pos = bodyPos + padded.coerceAtLeast(0)
            }
            if (dataOffset < 0 || dataSize < 2 || bitsPerSample != 16 || channels < 1) return null
            val frameCount = dataSize / (2 * channels)
            if (frameCount <= 0) return null
            val out = FloatArray(frameCount)
            val invScale = 1f / 32768f
            var byteIdx = dataOffset
            for (i in 0 until frameCount) {
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += bb.getShort(byteIdx).toFloat() * invScale
                    byteIdx += 2
                }
                out[i] = sum / channels.toFloat()
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun sampleLoopLinear(pcm: FloatArray, normPos: Float): Float {
        val n = pcm.size
        if (n <= 0) return 0f
        var u = normPos % 1f
        if (u < 0f) u += 1f
        val idxF = u * n
        val i0 = idxF.toInt().coerceIn(0, n - 1)
        val i1 = if (i0 + 1 >= n) 0 else i0 + 1
        val frac = idxF - i0
        return pcm[i0] + (pcm[i1] - pcm[i0]) * frac
    }

    fun sampleClampedLinear(pcm: FloatArray, samplePos: Float): Float {
        val n = pcm.size
        if (n <= 0 || samplePos < 0f || samplePos >= n - 1) return 0f
        val i0 = samplePos.toInt()
        val frac = samplePos - i0
        return pcm[i0] + (pcm[i0 + 1] - pcm[i0]) * frac
    }
}

/** Clean RBJ Biquad filter for realistic tractor exhaust stack formants & hiss-free low-pass shaping */
private class BiquadFilter {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun processBandPass(input: Float, centerHz: Float, q: Float, sampleRate: Float): Float {
        val w0 = (2.0 * PI * centerHz / sampleRate).toFloat().coerceIn(0.005f, 2.8f)
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2f * q.coerceAtLeast(0.4f))
        val invA0 = 1f / (1f + alpha)

        val b0 = (sinW0 * 0.5f) * invA0
        val b2 = -b0
        val a1 = (-2f * cosW0) * invA0
        val a2 = (1f - alpha) * invA0

        val out = b0 * input + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = out.coerceIn(-8f, 8f)
        return y1
    }

    fun processLowPass(input: Float, cutoffHz: Float, q: Float, sampleRate: Float): Float {
        val w0 = (2.0 * PI * cutoffHz / sampleRate).toFloat().coerceIn(0.005f, 2.8f)
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2f * q.coerceAtLeast(0.5f))
        val invA0 = 1f / (1f + alpha)

        val b1 = (1f - cosW0) * invA0
        val b0 = b1 * 0.5f
        val a1 = (-2f * cosW0) * invA0
        val a2 = (1f - alpha) * invA0

        val out = b0 * input + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = out.coerceIn(-8f, 8f)
        return y1
    }
}

class SoundEngine(context: Context? = null) {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TWO_PI = (2.0 * PI).toFloat()
    }

    private val appContext: Context? = context?.applicationContext

    @Volatile var musicEnabled: Boolean = true
    @Volatile var sfxEnabled: Boolean = true
    @Volatile var inGameplay: Boolean = false
    @Volatile var isPaused: Boolean = false

    @Volatile var activeTractor: TractorModel = TractorModel.EICHER_241
    @Volatile var currentMap: MapEnvironment = MapEnvironment.INDIAN_VILLAGE
    @Volatile var engineRpm: Float = 620f
    @Volatile var throttle: Float = 0f
    @Volatile var vehicleSpeed: Float = 0f
    @Volatile var loadStrain: Float = 0f
    @Volatile var currentSurface: SurfaceType = SurfaceType.DIRT
    @Volatile var hornActive: Boolean = false

    private val sfxQueue = ConcurrentLinkedQueue<SfxType>()
    private val activeTones = mutableListOf<ActiveTone>()

    @Volatile private var running = false
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        audioThread = Thread({
            runSynthesisLoop(track)
        }, "AnshTraX-Audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun playSfx(type: SfxType) {
        if (sfxEnabled) {
            sfxQueue.offer(type)
        }
    }

    fun stop() {
        running = false
        try {
            audioThread?.join(350)
        } catch (_: InterruptedException) {
        }
        audioThread = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun loadRawWav(resId: Int): FloatArray? {
        val ctx = appContext ?: return null
        return try {
            ctx.resources.openRawResource(resId).use { stream ->
                WavPcmCodec.parseMonoPcm16Wav(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun runSynthesisLoop(track: AudioTrack) {
        val chunkSamples = 1024
        val buffer = ShortArray(chunkSamples)
        val dt = 1f / SAMPLE_RATE

        // Load real Eicher 241 PCM loops off the UI thread once at audio startup
        val eicherIdlePcm = loadRawWav(R.raw.eicher_idle_loop)
        val eicherRevPcm = loadRawWav(R.raw.eicher_rev_loop)
        val eicherStarterPcm = loadRawWav(R.raw.eicher_starter)

        var beatPhase = 0f
        var prevBeatPhase = 0f
        var crankPhase = 0f
        var prevCrankPhase = 0f
        var cylGainJitter = 1.0f
        var governorRateJitter = 1.0f
        var lopePhase = 0f
        var pulseAge = 1.0f
        var exhaustEnergy = 0f
        var valveShockEnv = 0f
        var puffEnv = 0f
        var subThumpPhase = 0f
        var subThumpEnv = 0f
        var pipeResPhase = 0f
        var exhaustDcBlock = 0f
        var knockPhase1 = 0f
        var knockPhase2 = 0f
        var knockEnergy = 0f
        var rockerPhase = 0f
        var rockerEnv = 0f
        var exhaustLp1 = 0f
        var exhaustLp2 = 0f
        var exhaustLp3 = 0f
        var knockLp1 = 0f
        var knockLp2 = 0f
        var gearHumPhase = 0f
        var turboPhase = 0f
        var hornPhase1 = 0f
        var hornPhase2 = 0f
        var roadLp1 = 0f
        var roadLp2 = 0f
        var rainLp1 = 0f
        var rainLp2 = 0f
        var musicClock = 0f
        var musicNotePhase = 0f
        var bassPhase = 0f
        var smoothedRpm = 620f
        var smoothedSpeed = 0f
        var smoothedThrottle = 0f

        val stackFormant1 = BiquadFilter()
        val stackFormant2 = BiquadFilter()
        val stackFormant3 = BiquadFilter()
        val puffLowPass = BiquadFilter()
        val srFloat = SAMPLE_RATE.toFloat()

        val rng = Random(1337)
        val melodyNotes = floatArrayOf(
            261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 392.00f, 329.63f, 293.66f,
            261.63f, 329.63f, 392.00f, 523.25f, 440.00f, 392.00f, 329.63f, 293.66f
        )

        while (running) {
            while (true) {
                val ev = sfxQueue.poll() ?: break
                val dur = when (ev) {
                    SfxType.CLICK -> 0.035f
                    SfxType.COIN -> 0.16f
                    SfxType.FUEL -> 0.32f
                    SfxType.CHECKPOINT -> 0.42f
                    SfxType.BUMP -> 0.18f
                    SfxType.CRASH -> 0.38f
                    SfxType.MISSION_WIN -> 0.85f
                    SfxType.STARTER -> {
                        if (activeTractor == TractorModel.EICHER_241 && eicherStarterPcm != null) {
                            (eicherStarterPcm.size / srFloat).coerceIn(0.6f, 1.8f)
                        } else {
                            0.72f
                        }
                    }
                }
                if (activeTones.size < 8) {
                    activeTones.add(ActiveTone(ev, 0f, dur))
                }
            }

            val tractor = activeTractor
            val playEngine = sfxEnabled && inGameplay && !isPaused
            // Play menu music in menus ONLY so driving features raw authentic tractor diesel audio
            val playMenuMusic = musicEnabled && !inGameplay && !isPaused
            val map = currentMap

            for (i in 0 until chunkSamples) {
                var sample = 0f

                smoothedRpm += (engineRpm - smoothedRpm) * 0.00065f
                smoothedSpeed += (vehicleSpeed - smoothedSpeed) * 0.0012f
                smoothedThrottle += (throttle - smoothedThrottle) * 0.0009f

                // Duck engine briefly during real Eicher starter cranking so cranking -> idle catch is clean
                var starterEngineDuck = 1f
                if (tractor == TractorModel.EICHER_241 && eicherStarterPcm != null) {
                    for (tIdx in 0 until activeTones.size) {
                        val tTone = activeTones[tIdx]
                        if (tTone.type == SfxType.STARTER) {
                            val crankFadeIn = ((tTone.elapsed - 0.88f) / 0.34f).coerceIn(0f, 1f)
                            starterEngineDuck = crankFadeIn
                            break
                        }
                    }
                }

                if (playEngine && smoothedRpm > 50f) {
                    val strain = loadStrain.coerceIn(0f, 1f)
                    val throt = smoothedThrottle.coerceIn(0f, 1f)
                    val white = rng.nextFloat() * 2f - 1f
                    pulseAge += dt

                    // Slow mechanical Bosch governor lope at idle
                    lopePhase = (lopePhase + 1.35f * dt) % 1f
                    val idleLope = if (tractor == TractorModel.EICHER_241 && throt < 0.20f) {
                        1.0f + sin(TWO_PI * lopePhase) * 0.025f * (1f - throt * 5f)
                    } else 1.0f

                    // Firing frequency from real 4-stroke cycle (Eicher 1-cyl fires once per 2 revs)
                    val baseFiringHz = when (tractor) {
                        TractorModel.EICHER_241 -> (smoothedRpm / 120f).coerceIn(4.4f, 19.5f)
                        TractorModel.MAHINDRA_575 -> (smoothedRpm / 30f).coerceIn(20f, 86f)
                        TractorModel.SONALIKA_750 -> (smoothedRpm / 30f).coerceIn(20f, 90f)
                    }
                    val firingHz = baseFiringHz * governorRateJitter * idleLope

                    prevBeatPhase = beatPhase
                    beatPhase = (beatPhase + firingHz * dt) % 8f
                    if (beatPhase.toInt() != prevBeatPhase.toInt()) {
                        governorRateJitter = if (tractor == TractorModel.EICHER_241) {
                            1.0f + (rng.nextFloat() * 2f - 1f) * (0.024f * (1f - throt * 0.65f))
                        } else 1.0f
                    }

                    prevCrankPhase = crankPhase
                    crankPhase = (crankPhase + firingHz * dt) % 1f

                    // Trigger TDC combustion & upright exhaust stack pressure slap (for synthetic fallback / Mahindra / Sonalika)
                    if (crankPhase < prevCrankPhase) {
                        val uneven = if (tractor == TractorModel.EICHER_241) 0.07f else 0.11f
                        cylGainJitter = 1.0f + (rng.nextFloat() * 2f - 1f) * uneven
                        pulseAge = 0f

                        val fuelLoad = (0.80f + throt * 0.68f + strain * 0.36f) * cylGainJitter
                        exhaustEnergy = fuelLoad
                        valveShockEnv = (1.0f + throt * 0.60f + strain * 0.25f) * cylGainJitter
                        puffEnv = (0.90f + throt * 0.65f + strain * 0.30f) * cylGainJitter
                        subThumpEnv = (0.84f + throt * 0.58f + strain * 0.28f) * cylGainJitter
                        subThumpPhase = 0f
                        pipeResPhase = 0f

                        val knockLoad = if (tractor == TractorModel.EICHER_241) {
                            (0.42f + throt * 0.78f + strain * 0.28f) * cylGainJitter
                        } else {
                            (0.32f + throt * 0.55f + strain * 0.22f) * cylGainJitter
                        }
                        knockEnergy = knockLoad
                        knockPhase1 = 0f
                        knockPhase2 = 0f
                    }

                    if (tractor == TractorModel.EICHER_241 && prevCrankPhase < 0.50f && crankPhase >= 0.50f) {
                        rockerEnv = (0.14f + throt * 0.10f) * cylGainJitter
                        rockerPhase = 0f
                    }

                    if (tractor == TractorModel.EICHER_241 && eicherIdlePcm != null && eicherRevPcm != null) {
                        // === REAL UPLOADED EICHER 241 RECORDING (PHASE-LOCKED 8-BEAT IDLE + REV MORPH) ===
                        val u = beatPhase / 8f
                        val idleSample = WavPcmCodec.sampleLoopLinear(eicherIdlePcm, u)
                        // Match rev RMS loudness (~0.204) to idle RMS loudness (~0.146) so accelerating never jumps volume!
                        val revSample = WavPcmCodec.sampleLoopLinear(eicherRevPcm, u) * 0.75f
                        val tBlend = ((smoothedRpm - 740f) / 860f).coerceIn(0f, 1f)
                        val smoothBlend = tBlend * tBlend * (3f - 2f * tBlend)

                        val realSignal = idleSample * (1f - smoothBlend) + revSample * smoothBlend
                        val driveGain = (0.92f + throt * 0.08f + strain * 0.06f) * starterEngineDuck
                        sample += realSignal * driveGain
                    } else if (tractor == TractorModel.EICHER_241) {
                        // === SYNTHETIC FALLBACK EICHER 241 "FATT... FATT... FATT..." STACK SLAP ===
                        valveShockEnv *= exp(-260f * dt)
                        puffEnv *= exp((-62f + throt * 10f) * dt)

                        val warmGas = puffLowPass.processLowPass(white, 1180f + throt * 280f, 0.75f, srFloat)
                        val stackExcite = valveShockEnv * 2.4f + warmGas * puffEnv * 1.15f

                        val f1 = stackFormant1.processBandPass(stackExcite, 215f + throt * 45f, 2.0f, srFloat)
                        val f2 = stackFormant2.processBandPass(stackExcite, 510f + throt * 115f, 2.3f, srFloat)
                        val f3 = stackFormant3.processBandPass(stackExcite, 910f + throt * 140f, 2.6f, srFloat)
                        val stackCrack = tanh((f1 * 1.30f + f2 * 1.55f + f3 * 0.85f) * 1.35f)

                        val instBassHz = (76f + throt * 16f) * (1f + 1.1f * exp(-pulseAge * 68f))
                        subThumpPhase = (subThumpPhase + instBassHz * dt) % 1f
                        subThumpEnv *= exp(-46f * dt)
                        val tightThud = sin(TWO_PI * subThumpPhase) * subThumpEnv * 0.34f

                        knockEnergy *= exp(-175f * dt)
                        val mode1Hz = 520f + throt * 90f
                        knockPhase1 = (knockPhase1 + mode1Hz * dt) % 1f
                        knockPhase2 = (knockPhase2 + mode1Hz * 1.62f * dt) % 1f
                        val ironTap = (sin(TWO_PI * knockPhase1) * 0.65f + sin(TWO_PI * knockPhase2) * 0.35f) * knockEnergy * 0.24f

                        rockerEnv *= exp(-180f * dt)
                        rockerPhase = (rockerPhase + 380f * dt) % 1f
                        val rockerTick = sin(TWO_PI * rockerPhase) * rockerEnv * 0.22f

                        val rpmNorm = ((smoothedRpm - 580f) / 1800f).coerceIn(0f, 1f)
                        gearHumPhase = (gearHumPhase + ((smoothedRpm / 60f) * 2.5f) * dt) % 1f
                        val mechBody = sin(TWO_PI * gearHumPhase) * (0.01f + rpmNorm * 0.04f)

                        val eicherMix = tanh((stackCrack * 1.15f + tightThud + ironTap + rockerTick + mechBody) * 0.95f)
                        sample += eicherMix * 0.76f * starterEngineDuck
                    } else {
                        // === MAHINDRA 575 & SONALIKA 750 4-CYLINDER DIESEL ===
                        val baseThumpHz = if (tractor == TractorModel.MAHINDRA_575) 96f + throt * 25f else 84f + throt * 24f
                        val instThumpFreq = baseThumpHz * (1.0f + 0.75f * exp(-pulseAge * 55f))
                        subThumpPhase = (subThumpPhase + instThumpFreq * dt) % 1f
                        pipeResPhase = (pipeResPhase + instThumpFreq * 2.02f * dt) % 1f

                        val thumpDecay = if (tractor == TractorModel.MAHINDRA_575) 36f - throt * 6f else 32f - throt * 6f
                        subThumpEnv *= exp(-thumpDecay * dt)
                        val p1 = sin(TWO_PI * subThumpPhase)
                        val p2 = sin(TWO_PI * pipeResPhase)
                        val exhaustBodyThump = (p1 * 0.82f + p2 * 0.30f) * subThumpEnv

                        val decayRate = if (tractor == TractorModel.MAHINDRA_575) 42f - throt * 7f else 36f - throt * 6f
                        exhaustEnergy *= exp(-decayRate * dt)

                        val rawExhaustPulse = exhaustEnergy * (0.65f * p2 + 0.35f * white)
                        val exhaustCutoff = if (tractor == TractorModel.MAHINDRA_575) 0.021f + throt * 0.014f else 0.019f + throt * 0.013f
                        exhaustLp1 += (rawExhaustPulse - exhaustLp1) * exhaustCutoff
                        exhaustLp2 += (exhaustLp1 - exhaustLp2) * (exhaustCutoff * 1.25f)
                        exhaustLp3 += (exhaustLp2 - exhaustLp3) * (exhaustCutoff * 1.45f)
                        exhaustDcBlock += (exhaustLp3 - exhaustDcBlock) * 0.003f
                        val cleanExhaustChug = exhaustLp3 - exhaustDcBlock

                        knockEnergy *= exp(-115f * dt)
                        val mode1Hz = if (tractor == TractorModel.MAHINDRA_575) 560f + throt * 110f else 510f + throt * 100f
                        knockPhase1 = (knockPhase1 + mode1Hz * dt) % 1f
                        knockPhase2 = (knockPhase2 + mode1Hz * 1.66f * dt) % 1f
                        knockLp1 += (white - knockLp1) * 0.028f
                        knockLp2 += (knockLp1 - knockLp2) * 0.042f
                        val ironKnock = (sin(TWO_PI * knockPhase1) * 0.62f + sin(TWO_PI * knockPhase2) * 0.34f + knockLp2 * 0.65f) * knockEnergy

                        val rpmNorm = ((smoothedRpm - 580f) / 1800f).coerceIn(0f, 1f)
                        gearHumPhase = (gearHumPhase + ((smoothedRpm / 60f) * 3.0f) * dt) % 1f
                        val mechanicalHum = sin(TWO_PI * gearHumPhase) * (0.012f + rpmNorm * 0.05f)

                        var turboResonance = 0f
                        if (tractor == TractorModel.SONALIKA_750 && smoothedRpm > 1400f && throt > 0.35f) {
                            val spool = ((smoothedRpm - 1400f) / 950f).coerceIn(0f, 1f) * throt
                            turboPhase = (turboPhase + (920f + smoothedRpm * 0.28f) * dt) % 1f
                            turboResonance = sin(TWO_PI * turboPhase) * spool * 0.014f
                        }

                        val engineGain = if (tractor == TractorModel.MAHINDRA_575) 0.72f else 0.76f
                        val mixedEngine = tanh(
                            (exhaustBodyThump * 0.42f + cleanExhaustChug * 1.45f + ironKnock * 0.30f + mechanicalHum) * engineGain
                        ) + turboResonance
                        sample += mixedEngine * 0.72f
                    }

                    // 7. Warm 2-pole Low-Pass Tyre Surface Rumble (< 400 Hz, zero 'see-see' hiss)
                    val absSpd = abs(smoothedSpeed)
                    if (absSpd > 0.45f) {
                        val alpha = when (currentSurface) {
                            SurfaceType.ASPHALT -> 0.016f
                            SurfaceType.DIRT -> 0.028f
                            SurfaceType.SAND -> 0.036f
                            SurfaceType.MUD -> 0.022f
                            SurfaceType.WET -> 0.044f
                            SurfaceType.ROCK -> 0.040f
                        }
                        roadLp1 += (white - roadLp1) * alpha
                        roadLp2 += (roadLp1 - roadLp2) * (alpha * 1.2f)
                        val roadGain = (absSpd / 11f).coerceIn(0f, 1f) * when (currentSurface) {
                            SurfaceType.WET -> 0.13f
                            SurfaceType.MUD -> 0.11f
                            else -> 0.08f
                        }
                        sample += roadLp2 * roadGain
                    }

                    // 8. Soft Low-Filtered Rain Patter
                    if (map.hasRain) {
                        rainLp1 += (white - rainLp1) * 0.042f
                        rainLp2 += (rainLp1 - rainLp2) * 0.042f
                        sample += rainLp2 * 0.042f
                    }

                    // 9. Authentic Indian Tractor Dual-Tone Horn
                    if (hornActive) {
                        hornPhase1 = (hornPhase1 + 405f * dt) % 1f
                        hornPhase2 = (hornPhase2 + 510f * dt) % 1f
                        val h1 = tanh(sin(TWO_PI * hornPhase1) * 2.5f)
                        val h2 = tanh(sin(TWO_PI * hornPhase2) * 2.5f)
                        sample += (h1 * 0.52f + h2 * 0.48f) * 0.15f
                    }
                }

                // Menu background music only (stops during drive so engine audio is pure & realistic)
                if (playMenuMusic) {
                    val stepDur = 0.30f
                    val stepIdx = ((musicClock / stepDur).toInt()) % melodyNotes.size
                    val localT = musicClock % stepDur
                    val freq = melodyNotes[stepIdx]
                    musicNotePhase = (musicNotePhase + freq * dt) % 1f
                    bassPhase = (bassPhase + (if (stepIdx % 4 == 0) 130.81f else 98.0f) * dt) % 1f

                    val pluckEnv = exp(-localT * 8.5f)
                    val bassEnv = if (stepIdx % 2 == 0) exp(-localT * 7.0f) else 0f
                    val melodyTone = (sin(TWO_PI * musicNotePhase) + 0.25f * sin(TWO_PI * ((musicNotePhase * 2f) % 1f))) * pluckEnv
                    val bassTone = sin(TWO_PI * bassPhase) * bassEnv
                    sample += (melodyTone * 0.55f + bassTone * 0.45f) * 0.11f

                    musicClock = (musicClock + dt) % (stepDur * melodyNotes.size)
                }

                // Mix active one-shot SFX
                if (sfxEnabled && activeTones.isNotEmpty()) {
                    var idx = activeTones.size - 1
                    while (idx >= 0) {
                        val tone = activeTones[idx]
                        tone.elapsed += dt
                        if (tone.elapsed >= tone.duration) {
                            activeTones.removeAt(idx)
                        } else {
                            val starterPcm = if (tractor == TractorModel.EICHER_241) eicherStarterPcm else null
                            sample += renderSfxSample(tone, rng, starterPcm)
                        }
                        idx--
                    }
                }

                val clamped = tanh(sample * 0.95f).coerceIn(-0.98f, 0.98f)
                buffer[i] = (clamped * 32767f).toInt().toShort()
            }

            if (running) {
                track.write(buffer, 0, chunkSamples)
            }
        }
    }

    private fun renderSfxSample(
        tone: ActiveTone,
        rng: Random,
        eicherStarterPcm: FloatArray?
    ): Float {
        val t = tone.elapsed
        val p = t / tone.duration
        return when (tone.type) {
            SfxType.CLICK -> sin(TWO_PI * 620f * t) * exp(-p * 6f) * 0.14f
            SfxType.COIN -> {
                val freq = if (p < 0.42f) 987.77f else 1318.51f
                sin(TWO_PI * freq * t) * exp(-p * 4.2f) * 0.18f
            }
            SfxType.FUEL -> sin(TWO_PI * (300f + p * 400f) * t) * exp(-p * 3.0f) * 0.20f
            SfxType.CHECKPOINT -> {
                val freq = when {
                    p < 0.33f -> 523.25f
                    p < 0.66f -> 659.25f
                    else -> 783.99f
                }
                sin(TWO_PI * freq * t) * exp(-((p % 0.33f) * 8f)) * 0.20f
            }
            SfxType.BUMP -> {
                val thud = sin(TWO_PI * (85f - p * 45f) * t) * exp(-p * 6f)
                val crunch = (rng.nextFloat() * 2f - 1f) * exp(-p * 9f) * 0.3f
                (thud + crunch) * 0.26f
            }
            SfxType.CRASH -> {
                val thud = sin(TWO_PI * (72f - p * 38f) * t) * exp(-p * 4f)
                val noise = (rng.nextFloat() * 2f - 1f) * exp(-p * 5f)
                (thud * 0.7f + noise * 0.6f) * 0.32f
            }
            SfxType.MISSION_WIN -> {
                val freq = when {
                    p < 0.22f -> 523.25f
                    p < 0.44f -> 659.25f
                    p < 0.66f -> 783.99f
                    else -> 1046.50f
                }
                (sin(TWO_PI * freq * t) + 0.25f * sin(TWO_PI * freq * 2f * t)) * exp(-((p % 0.22f) * 4.5f)) * 0.22f
            }
            SfxType.STARTER -> {
                if (eicherStarterPcm != null) {
                    WavPcmCodec.sampleClampedLinear(eicherStarterPcm, t * SAMPLE_RATE) * 0.85f
                } else {
                    val crankCycle = (t * 4.6f) % 1f
                    val solenoid = sin(TWO_PI * 240f * t) * 0.065f
                    val compression = tanh(sin(TWO_PI * 64f * t) * 2.4f) * exp(-crankCycle * 7.5f) * 0.28f
                    val firePop = if (p > 0.68f) {
                        val fT = p - 0.68f
                        (sin(TWO_PI * (92f - fT * 45f) * t) + 0.55f * sin(TWO_PI * 184f * t)) * exp(-fT * 7f) * 0.30f
                    } else 0f
                    (solenoid + compression + firePop) * (1f - p * 0.2f)
                }
            }
        }
    }
}
