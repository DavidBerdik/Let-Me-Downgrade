package com.berdik.letmedowngrade

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

class PrefManager {
    companion object {
        private var prefs: SharedPreferences? = null
        private var hookActive: Boolean? = null

        // Since we are an Xposed module, we don't care about MODE_WORLD_READABLE being deprecated.
        // In fact, we need to use it despite being deprecated because without it, the Xposed
        // hooking mechanism cannot access the preference value.
        @SuppressLint("WorldReadableFiles")
        @Suppress("DEPRECATION")
        fun loadPrefs(context: Context) {
            if (prefs == null) {
                prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_WORLD_READABLE)
                markTileRevealAsDone()
            }
        }

        fun isHookOn(): Boolean {
            if (hookActive == null) {
                hookActive = prefs!!.getBoolean("hookActive", false)
            }
            return hookActive as Boolean
        }

        fun toggleHookState() {
            hookActive = !isHookOn()
            val prefEdit = prefs!!.edit()
            prefEdit.putBoolean("hookActive", hookActive!!)
            prefEdit.apply()
        }

        private fun markTileRevealAsDone() {
            val prefEdit = prefs!!.edit()
            prefEdit.putBoolean("tileRevealDone", true)
            prefEdit.apply()
        }
    }
}