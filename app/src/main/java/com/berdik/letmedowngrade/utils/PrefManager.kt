package com.berdik.letmedowngrade.utils

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.InstructionsActivity
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class PrefManager {
    companion object {
        private var prefs: SharedPreferences? = null
        private var hookActive: Boolean? = null

        fun loadPrefs(context: Context) {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    XposedChecker.flagAsEnabled()
                    prefs = service.getRemotePreferences(BuildConfig.APPLICATION_ID)
                }

                override fun onServiceDied(service: XposedService) {}
            })
            markTileRevealAsDone()
            toggleModuleIcon(context)
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

        private fun toggleModuleIcon(context: Context) {
            // Assume that the state to set is disabling the icon, and flip it to enabling the
            // icon if no Xposed is detected.
            var stateToSet = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (!XposedChecker.isEnabled()) {
                stateToSet = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            context.packageManager.setComponentEnabledSetting(ComponentName(context,
                InstructionsActivity::class.java), stateToSet, PackageManager.DONT_KILL_APP)
        }
    }
}