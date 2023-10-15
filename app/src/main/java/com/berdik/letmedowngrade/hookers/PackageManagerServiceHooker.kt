package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.utils.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Method

class PackageManagerServiceHooker {
    companion object {
        var module: XposedModule? = null

        @SuppressLint("PrivateApi")
        fun hook(param: SystemServerLoadedParam, module: XposedModule) {
            this.module = module

            /*
                Attempt to hook one of two different classes in which checkDowngrade() may appear.
                The first is the Android 12 AOSP variant and the second is the Android 13+ AOSP
                variant. In a perfect world, a conditional check would be used here to determine
                which one should be hooked, but since OEMs tend to deviate from AOSP, attempting
                to hook in one class and failing over to the other class is the safest option.
             */
            try {
                // Android 12 AOSP variant.
                val pmClass = param.classLoader.loadClass("com.android.server.pm.PackageManagerService")
                val checkDowngradeMethod = findCheckDowngradeMethod(pmClass.methods) as Method
                module.hook(checkDowngradeMethod, DowngradeCheckerGenericHooker::class.java)
            } catch (e: Exception) {
                // Android 13+ AOSP variant.
                val pmClass = param.classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
                val checkDowngradeMethod = findCheckDowngradeMethod(pmClass.methods) as Method
                module.hook(checkDowngradeMethod, DowngradeCheckerGenericHooker::class.java)
            }
        }

        private fun findCheckDowngradeMethod (methods: Array<Method>): Method? {
            for (method in methods) {
                if (method.name == "checkDowngrade") {
                    return method
                }
            }
            return null
        }

        @XposedHooker
        private class DowngradeCheckerGenericHooker(private val isHookActive: Boolean, private val packageName: String) : XposedInterface.Hooker {
            companion object {
                @JvmStatic
                @BeforeInvocation
                fun beforeInvocation(callback: BeforeHookCallback): DowngradeCheckerGenericHooker {
                    val prefs = module?.getRemotePreferences(BuildConfig.APPLICATION_ID)
                    val isHookActive = prefs?.getBoolean("hookActive", false)
                    val packageName = XposedHelpers.getObjectField(callback.args[1], "packageName") as String

                    if (isHookActive!!) {
                        module?.log("[Let Me Downgrade] Blocked downgrade check on package: $packageName")
                        callback.returnAndSkip(null)
                    }

                    return DowngradeCheckerGenericHooker(isHookActive, packageName)
                }

                @JvmStatic
                @AfterInvocation
                fun afterInvocation(callback: AfterHookCallback, context: DowngradeCheckerGenericHooker) {
                    if (!context.isHookActive) {
                        module?.log("[Let Me Downgrade] Allowed downgrade check on package: ${context.packageName}")
                    }
                }
            }
        }
    }
}