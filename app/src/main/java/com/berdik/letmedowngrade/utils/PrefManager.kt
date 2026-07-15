package com.berdik.letmedowngrade.utils

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.berdik.letmedowngrade.BuildConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

object PrefManager {
    private const val HOOK_ACTIVE_KEY = "hookActive"
    private const val SIGNATURE_BYPASS_ACTIVE_KEY = "signatureBypassActive"

    private var prefs: SharedPreferences? = null
    private val hookActiveLiveData = MutableLiveData<Boolean>()
    private val signatureBypassActiveLiveData = MutableLiveData<Boolean>()

    fun loadPrefs() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                XposedChecker.flagAsEnabled()
                prefs = service.getRemotePreferences(BuildConfig.APPLICATION_ID)
                hookActiveLiveData.postValue(prefs!!.getBoolean(HOOK_ACTIVE_KEY, false))
                signatureBypassActiveLiveData.postValue(
                    prefs!!.getBoolean(SIGNATURE_BYPASS_ACTIVE_KEY, false)
                )
            }

            override fun onServiceDied(service: XposedService) {}
        })
    }

    fun isHookOn(): Boolean {
        if (!XposedChecker.isEnabled()) {
            return false
        }
        hookActiveLiveData.value?.let { return it }
        return prefs?.getBoolean(HOOK_ACTIVE_KEY, false) ?: false
    }

    fun toggleHookState() {
        if (XposedChecker.isEnabled()) {
            setHookState(!isHookOn())
        }
    }

    fun isSignatureBypassOn(): Boolean {
        if (!XposedChecker.isEnabled()) {
            return false
        }
        signatureBypassActiveLiveData.value?.let { return it }
        return prefs?.getBoolean(SIGNATURE_BYPASS_ACTIVE_KEY, false) ?: false
    }

    fun toggleSignatureBypassState() {
        if (XposedChecker.isEnabled()) {
            setSignatureBypassState(!isSignatureBypassOn())
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun setHookState(prefVal: Boolean) {
        if (XposedChecker.isEnabled() && prefs != null) {
            hookActiveLiveData.value = prefVal
            prefs!!.edit().putBoolean(HOOK_ACTIVE_KEY, prefVal).commit()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun setSignatureBypassState(prefVal: Boolean) {
        if (XposedChecker.isEnabled() && prefs != null) {
            signatureBypassActiveLiveData.value = prefVal
            prefs!!.edit().putBoolean(SIGNATURE_BYPASS_ACTIVE_KEY, prefVal).commit()
        }
    }

    fun getHookActiveAsLiveData(): MutableLiveData<Boolean> {
        return hookActiveLiveData
    }

    fun getSignatureBypassActiveAsLiveData(): MutableLiveData<Boolean> {
        return signatureBypassActiveLiveData
    }
}
