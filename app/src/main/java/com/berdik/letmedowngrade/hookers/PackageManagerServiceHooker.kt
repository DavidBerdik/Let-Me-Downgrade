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

        fun hook(param: SystemServerLoadedParam, module: XposedModule) {
            this.module = module
            val checkDowngradeMethod = findCheckDowngradeMethod(param.classLoader) as Method
            module.hook(checkDowngradeMethod, DowngradeCheckerGenericHooker::class.java)
        }

        @SuppressLint("PrivateApi")
        private fun findCheckDowngradeMethod(classLoader: ClassLoader): Method? {
            /*
                Using a conditional check here to determine the Android version being used would be
                preferred, but since OEMs tend to deviate from the AOSP source code, it is safer to
                use a series of try-catches instead.
             */

            // Android 12 through Android 12 QPR 1
            try {
                val pmClass = classLoader.loadClass("com.android.server.pm.PackageManagerService")
                return pmClass.getDeclaredMethod("checkDowngrade",
                    classLoader.loadClass("com.android.server.pm.parsing.pkg.AndroidPackage"),
                    classLoader.loadClass("android.content.pm.PackageInfoLite"))
            } catch (_: Exception) {}

            // Android 13 through Android 13 QPR 1
            try {
                val pmClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
                return pmClass.getDeclaredMethod("checkDowngrade",
                    classLoader.loadClass("com.android.server.pm.parsing.pkg.AndroidPackage"),
                    classLoader.loadClass("android.content.pm.PackageInfoLite"))
            } catch (_: Exception) {}

            // Android 14 through Android 15
            try {
                val pmClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
                return pmClass.getDeclaredMethod("checkDowngrade",
                    classLoader.loadClass("com.android.server.pm.pkg.AndroidPackage"),
                    classLoader.loadClass("android.content.pm.PackageInfoLite"))
            } catch (_: Exception) {}

            // Android 15 QPR 1
            try {
                val pmClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
                return pmClass.getDeclaredMethod("checkDowngrade",
                    Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Array<String>::class.java, IntArray::class.java,
                    classLoader.loadClass("android.content.pm.PackageInfoLite"))
            } catch (_: Exception) {}

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
                    val packageName = XposedHelpers.getObjectField(callback.args.last(), "packageName") as String

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