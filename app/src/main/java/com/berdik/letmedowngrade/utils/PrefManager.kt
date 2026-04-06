package com.berdik.letmedowngrade.utils

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.berdik.letmedowngrade.BuildConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

object PrefManager {
    private var prefs: SharedPreferences? = null
    private val hookActiveLiveData = MutableLiveData<Boolean>()

    fun loadPrefs() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                XposedChecker.flagAsEnabled()
                prefs = service.getRemotePreferences(BuildConfig.APPLICATION_ID)
                hookActiveLiveData.value = prefs!!.getBoolean("hookActive", false)
            }

            override fun onServiceDied(service: XposedService) {}
        })
    }

    fun isHookOn(): Boolean {
        if (!XposedChecker.isEnabled()) {
            return false
        }
        hookActiveLiveData.value?.let { return it }
        return prefs?.getBoolean("hookActive", false) ?: false
    }

    fun toggleHookState() {
        if (XposedChecker.isEnabled()) {
            setHookState(!isHookOn())
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun setHookState(prefVal: Boolean) {
        if (XposedChecker.isEnabled() && prefs != null) {
            hookActiveLiveData.value = prefVal
            prefs!!.edit().putBoolean("hookActive", prefVal).commit()
        }
    }

    fun getHookActiveAsLiveData(): MutableLiveData<Boolean> {
        return hookActiveLiveData
    }
}