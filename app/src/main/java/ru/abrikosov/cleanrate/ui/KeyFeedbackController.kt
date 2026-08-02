package ru.abrikosov.cleanrate.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.abrikosov.cleanrate.R

internal class KeyFeedbackController(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        appContext.getSystemService(Vibrator::class.java)
    }
    private val vibrationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var soundId = 0
    @Volatile
    private var soundLoaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            soundLoaded = status == 0
        }
        soundId = soundPool.load(appContext, R.raw.key_click, 1)
    }

    fun perform(soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (soundEnabled) playSound()
        if (vibrationEnabled) vibrate()
    }

    private fun playSound() {
        if (!soundLoaded) return
        soundPool.play(soundId, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1f)
    }

    private fun vibrate() {
        val deviceVibrator = vibrator?.takeIf(Vibrator::hasVibrator) ?: return
        // Media usage keeps this explicit in-app feedback separate from Android's
        // optional system touch-feedback setting. It still respects interruption policy.
        val effect = VibrationEffect.createOneShot(PULSE_DURATION_MILLIS, PULSE_AMPLITUDE)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                deviceVibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA),
                )
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(effect, vibrationAudioAttributes)
            }
        }
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val SOUND_VOLUME = 1f
        const val PULSE_DURATION_MILLIS = 32L
        const val PULSE_AMPLITUDE = 170
    }
}

@Composable
internal fun rememberKeyFeedbackController(): KeyFeedbackController {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) { KeyFeedbackController(context) }
    DisposableEffect(controller) {
        onDispose(controller::release)
    }
    return controller
}
