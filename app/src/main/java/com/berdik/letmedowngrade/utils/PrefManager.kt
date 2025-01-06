package com.berdik.letmedowngrade.utils

import android.content.SharedPreferences
import com.berdik.letmedowngrade.BuildConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class PrefManager {
    companion object {
        private var prefs: SharedPreferences? = null
        private var hookActive: Boolean? = null

        fun loadPrefs() {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    XposedChecker.flagAsEnabled()
                    prefs = service.getRemotePreferences(BuildConfig.APPLICATION_ID)
                }

                override fun onServiceDied(service: XposedService) {}
            })
            markTileRevealAsDone()
        }

        fun isHookOn(): Boolean {
            if (!XposedChecker.isEnabled()) {
                return false
            }

            if (hookActive == null) {
                hookActive = prefs!!.getBoolean("hookActive", false)
            }
            return hookActive as Boolean
        }

        fun toggleHookState() {
            if (XposedChecker.isEnabled()) {
                hookActive = !isHookOn()
                val prefEdit = prefs!!.edit()
                prefEdit.putBoolean("hookActive", hookActive!!)
                prefEdit.apply()
            }
        }

        private fun markTileRevealAsDone() {
            if (XposedChecker.isEnabled()) {
                val prefEdit = prefs!!.edit()
                prefEdit.putBoolean("tileRevealDone", true)
                prefEdit.apply()
            }
        }
    }
}