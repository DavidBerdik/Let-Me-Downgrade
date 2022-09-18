package com.berdik.letmedowngrade

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

class PrefManager {
    companion object {
        private var prefs: SharedPreferences? = null
        private var hookActive: Boolean? = null
        private var noXposed = false

        // Since we are an Xposed module, we don't care about MODE_WORLD_READABLE being deprecated.
        // In fact, we need to use it despite being deprecated because without it, the Xposed
        // hooking mechanism cannot access the preference value.
        @SuppressLint("WorldReadableFiles")
        @Suppress("DEPRECATION")
        fun loadPrefs(context: Context) {
            try {
                if (prefs == null) {
                    prefs = context.getSharedPreferences(
                        BuildConfig.APPLICATION_ID,
                        Context.MODE_WORLD_READABLE
                    )
                }
            } catch (e: SecurityException) {
                noXposed = true
            }
            markTileRevealAsDone()
            toggleModuleIcon(context)
        }

        fun isHookOn(): Boolean {
            if (noXposed) {
                return false
            }

            if (hookActive == null) {
                hookActive = prefs!!.getBoolean("hookActive", false)
            }
            return hookActive as Boolean
        }

        fun toggleHookState() {
            if (!noXposed) {
                hookActive = !isHookOn()
                val prefEdit = prefs!!.edit()
                prefEdit.putBoolean("hookActive", hookActive!!)
                prefEdit.apply()
            }
        }

        private fun markTileRevealAsDone() {
            if (!noXposed) {
                val prefEdit = prefs!!.edit()
                prefEdit.putBoolean("tileRevealDone", true)
                prefEdit.apply()
            }
        }

        private fun toggleModuleIcon(context: Context) {
            // Assume that the state to set is disabling the icon, and flip it to enabling the
            // icon if no Xposed is detected.
            var stateToSet = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (noXposed) {
                stateToSet = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            context.packageManager.setComponentEnabledSetting(ComponentName(context,
                InstructionsActivity::class.java), stateToSet, PackageManager.DONT_KILL_APP)
        }
    }
}