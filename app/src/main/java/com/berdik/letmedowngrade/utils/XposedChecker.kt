package com.berdik.letmedowngrade.utils

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf

class XposedChecker {
    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val enabledState = mutableStateOf(false)

        fun flagAsEnabled() {
            mainHandler.post { enabledState.value = true }
        }

        fun isEnabled(): Boolean {
            return enabledState.value
        }
    }
}