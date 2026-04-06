package com.berdik.letmedowngrade

import android.util.Log
import com.berdik.letmedowngrade.hookers.PackageManagerServiceHooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

private lateinit var module: LetMeDowngrade

const val TAG = "Let Me Downgrade"

class LetMeDowngrade : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        module = this
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)

        try {
            PackageManagerServiceHooker.hook(param, this)
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "ERROR: $e")
        }
    }
}