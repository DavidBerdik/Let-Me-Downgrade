package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.Log
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.TAG
import com.berdik.letmedowngrade.utils.XposedHelpers
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

class PackageManagerServiceHooker {
    companion object {
        var module: XposedModule? = null
            private set

        fun hook(param: SystemServerStartingParam, module: XposedModule) {
            this.module = module
            val checkDowngradeMethod = findCheckDowngradeMethod(param.classLoader) as Method

            module.hook(checkDowngradeMethod).intercept { chain ->
                val prefs = module.getRemotePreferences(BuildConfig.APPLICATION_ID)
                val isHookActive = prefs.getBoolean("hookActive", false)
                val packageName = XposedHelpers.getObjectField(chain.args.last(), "packageName") as String

                if (isHookActive) {
                    module.log(Log.INFO, TAG, "Blocked downgrade check on package: $packageName")
                    return@intercept null
                }

                module.log(Log.INFO, TAG, "Allowed downgrade check on package: $packageName")
                val result = chain.proceed()
                result
            }
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
    }
}