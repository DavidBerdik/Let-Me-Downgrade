package com.berdik.letmedowngrade

import com.berdik.letmedowngrade.hookers.SystemUIHooker
import com.berdik.letmedowngrade.hookers.PackageManagerServiceHooker
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LetMeDowngrade : IXposedHookZygoteInit, IXposedHookLoadPackage {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelperInit.initZygote(startupParam)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam != null) {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            EzXHelperInit.setLogTag("Let Me Downgrade")
            EzXHelperInit.setToastTag("Let Me Downgrade")
            
            when (lpparam.packageName) {
                "android" -> {
                    try {
                        PackageManagerServiceHooker.hook(lpparam)
                    } catch (e: Exception) {
                        XposedBridge.log("[Let Me Downgrade] ERROR: $e")
                    }
                }
            }

            when (lpparam.packageName) {
                "com.android.systemui" -> {
                    val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID)
                    if (!prefs.getBoolean("tileRevealDone", false)) {
                        try {
                            XposedBridge.log("[Let Me Downgrade] Hooking System UI to add and reveal quick settings tile.")
                            SystemUIHooker.hook(lpparam)
                        } catch (e: Exception) {
                            XposedBridge.log("[Let Me Downgrade] ERROR: $e")
                        }
                    }
                }
            }
        }
    }
}