package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.ArraySet
import com.berdik.letmedowngrade.BuildConfig
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SystemUIHooker {
    companion object {
        private const val tileId = "custom(${BuildConfig.APPLICATION_ID}/.QuickTile)"
        private var tileRevealed = false

        @SuppressLint("PrivateApi")
        fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
            findAllMethods(lpparam.classLoader.loadClass("com.android.systemui.qs.QSPanelControllerBase")) {
                name == "setTiles" && isPublic && paramCount == 0
            }.hookMethod {
                before { param ->
                    if (!tileRevealed) {
                        val tileHost = XposedHelpers.getObjectField(param.thisObject, "mHost")

                        /*
                        According to the AOSP 13 source code, the addTile function was not changed,
                        however, it was not hooking properly on the Android 13 Pixel 4a August 2022
                        Factory Image. Listing the declared methods of the class revealed that the
                        function's parameters had been reversed in the shipped image even though
                        AOSP did not reflect this. To account for this discrepancy, we try calling
                        the Android 13 Pixel variant first, and if it fails, we fall back to the
                        other variant. Ideally, we would check the API version here with a proper
                        conditional, but since it is possible that Android 13 builds will use the
                        old variant of the function, this uglier but safer approach is used instead.
                         */
                        try {
                            // Used by Android 13 Pixel 4a August 2022 Factory Image.
                            XposedHelpers.callMethod(tileHost, "addTile", -1, tileId)
                        }
                        catch (t: Throwable) {
                            // Used by Android 12, and possibly some Android 13 distros.
                            XposedHelpers.callMethod(tileHost, "addTile", tileId)
                        }

                        XposedBridge.log("[Let Me Downgrade] Tile added to quick settings panel.")
                    }
                }
            }

            // Properly fixing the unchecked cast warning with Kotlin adds more performance overhead than it is worth,
            // so we are suppressing the warning instead.
            @Suppress("UNCHECKED_CAST")
            findAllMethods(lpparam.classLoader.loadClass("com.android.systemui.qs.QSTileRevealController\$1")) {
                name == "run"
            }.hookMethod {
                before { param ->
                    if (!tileRevealed) {
                        val tilesToReveal = XposedHelpers.getObjectField(XposedHelpers.getSurroundingThis(param.thisObject),
                            "mTilesToReveal") as ArraySet<String>
                        tilesToReveal.add(tileId)
                        tileRevealed = true
                        XposedBridge.log("[Let Me Downgrade] Tile quick settings panel reveal animation played. " +
                                "Let Me Downgrade will not hook SystemUI on next reboot.")
                    }
                }
            }
        }
    }
}