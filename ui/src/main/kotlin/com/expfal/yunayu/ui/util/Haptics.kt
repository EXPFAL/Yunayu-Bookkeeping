package com.expfal.yunayu.ui.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * 记账成功后的轻量触觉反馈（30ms 单次震动）。
 *
 * 包裹 [runCatching] 且经 [Vibrator.hasVibrator] 兜底：无震动硬件或权限缺失时静默忽略。
 */
fun Context.vibrateSuccess() {
    runCatching {
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            ?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
