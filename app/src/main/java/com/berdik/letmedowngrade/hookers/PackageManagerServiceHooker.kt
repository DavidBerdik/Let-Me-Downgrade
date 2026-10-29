package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.Log
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.TAG
import com.berdik.letmedowngrade.utils.XposedHelpers
import io.github.libxposed.api.XposedInterface
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
            val builder = module.hook(checkDowngradeMethod)
            /*
                setId is API 102-only; on API 101 frameworks the compile-time API is still 102 but
                getApiVersion() returns the framework LIB_API so this guard is a real 101/102 split.
             */
            if (module.apiVersion >= XposedInterface.API_102) {
                builder.setId("checkDowngrade")
            }
            builder.intercept(createCheckDowngradeHooker(module))
        }

        fun createCheckDowngradeHooker(module: XposedModule): XposedInterface.Hooker =
            object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val prefs = module.getRemotePreferences(BuildConfig.APPLICATION_ID)
                    val isHookActive = prefs.getBoolean("hookActive", false)
                    val packageName =
                        XposedHelpers.getObjectField(chain.args.last(), "packageName") as String

                    return if (isHookActive) {
                        module.log(Log.INFO, TAG, "Blocked downgrade check on package: $packageName")
                        null
                    } else {
                        module.log(Log.INFO, TAG, "Allowed downgrade check on package: $packageName")
                        chain.proceed()
                    }
                }
            }

        fun replaceHooks(oldHandles: List<XposedInterface.HookHandle>, module: XposedModule) {
            this.module = module
            val hooker = createCheckDowngradeHooker(module)
            for (handle in oldHandles) {
                handle.replaceHook(hooker)
            }
        }

        fun clearModuleRef() {
            module = null
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
