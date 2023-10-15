package com.berdik.letmedowngrade

import com.berdik.letmedowngrade.hookers.SystemUIHooker
import com.berdik.letmedowngrade.hookers.PackageManagerServiceHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam

private lateinit var module: LetMeDowngrade

class LetMeDowngrade(base: XposedInterface, param: ModuleLoadedParam) : XposedModule(base, param) {
    init {
        module = this
    }

    override fun onSystemServerLoaded(param: SystemServerLoadedParam) {
        super.onSystemServerLoaded(param)

        try {
            PackageManagerServiceHooker.hook(param, module)
        } catch (e: Exception) {
            module.log("[Let Me Downgrade] ERROR: $e")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        when (param.packageName) {
            "com.android.systemui" -> {
                val prefs = getRemotePreferences(BuildConfig.APPLICATION_ID)
                if (!prefs.getBoolean("tileRevealDone", false)) {
                    try {
                        module.log("[Let Me Downgrade] Hooking System UI to add and reveal quick settings tile.")
                        SystemUIHooker.hook(param, module)
                    } catch (e: Exception) {
                        module.log("[Let Me Downgrade] ERROR: $e")
                    }
                }
            }
        }
    }
}