package com.berdik.letmedowngrade.hookers

import com.berdik.letmedowngrade.BuildConfig
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class PackageManagerServiceHooker {
    companion object {
        fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
            /*
            Hook both classes in which checkDowngrade() may appear. The first is the Android 12 AOSP
            variant and the second is the Android 13 AOSP variant. In a perfect world, a conditional
            check would be used here to determine which one should be hooked, but since OEMs tend to
            to deviate from AOSP, attempting to hook in both classes is the safest option.
             */
            genericCheckDowngradeHook(lpparam, "com.android.server.pm.PackageManagerService")
            genericCheckDowngradeHook(lpparam, "com.android.server.pm.PackageManagerServiceUtils")
        }

        private fun genericCheckDowngradeHook(lpparam: XC_LoadPackage.LoadPackageParam, className: String) {
            findAllMethods(lpparam.classLoader.loadClass(className)) {
                name == "checkDowngrade"
            }.hookMethod {
                var packageName = ""
                var isHookActive = false

                before { param ->
                    // Get the package name of the app being processed.
                    packageName = XposedHelpers.getObjectField(param.args[1], "packageName") as String

                    // Get the active state of the hook.
                    val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID)
                    isHookActive = prefs.getBoolean("hookActive", false)

                    // If the hook is active, log a block of the downgrade check and bypass the real function.
                    if (isHookActive) {
                        XposedBridge.log("[Let Me Downgrade] Blocked downgrade check on package: $packageName")
                        param.result = null
                    }
                }

                after {
                    // If the hook is not active, make a log entry after the real function executes indicating so.
                    if (!isHookActive) {
                        XposedBridge.log("[Let Me Downgrade] Allowed downgrade check on package: $packageName")
                    }
                }
            }
        }
    }
}