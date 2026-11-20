package com.berdik.letmedowngrade

import android.util.Log
import com.berdik.letmedowngrade.hookers.PackageManagerServiceHooker
import com.berdik.letmedowngrade.hookers.SignatureVerificationHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

const val TAG = "Let Me Downgrade"

class LetMeDowngrade : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)

        try {
            PackageManagerServiceHooker.hook(param, this)
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "ERROR: $e")
        }

        try {
            SignatureVerificationHooker.hook(param, this)
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "ERROR: $e")
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        /*
            Runs in the old module generation before the new APK is loaded. Clear refs so the old
            class loader is not pinned. This module has no worker threads or native hooks.
            Returning false would make autoHotReload a no-op; the default would also block reload.
         */
        PackageManagerServiceHooker.clearModuleRef()
        SignatureVerificationHooker.clearModuleRef()
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        /*
            onSystemServerStarting is not replayed after hot reload. The default implementation
            would unhook every old handle before we can replaceHook them with the new generation.
         */
        try {
            if (param.oldHookHandles.isEmpty()) {
                log(Log.ERROR, TAG, "ERROR: hot reload delivered no hook handles to replace")
                return
            }
            val handles = param.oldHookHandles
            val claimed = mutableSetOf<XposedInterface.HookHandle>()
            claimed.addAll(PackageManagerServiceHooker.replaceHooks(handles, this))
            claimed.addAll(SignatureVerificationHooker.replaceHooks(handles, this))
            for (handle in handles) {
                if (handle !in claimed) {
                    log(Log.WARN, TAG, "Unhooking stale handle: ${handle.id ?: handle.executable.name}")
                    handle.unhook()
                }
            }
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "ERROR: $e")
        }
    }
}
