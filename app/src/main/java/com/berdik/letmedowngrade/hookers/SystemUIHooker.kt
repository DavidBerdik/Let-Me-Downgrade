package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.ArraySet
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.utils.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

class SystemUIHooker {
    companion object {
        var module: XposedModule? = null
        private const val tileId = "custom(${BuildConfig.APPLICATION_ID}/.QuickTile)"
        private var tileRevealed = false

        @SuppressLint("PrivateApi")
        fun hook(param: PackageLoadedParam, module: XposedModule) {
            this.module = module

            module.hook(
                param.classLoader.loadClass("com.android.systemui.qs.QSPanelControllerBase")
                    .getDeclaredMethod("setTiles"),
                TileSetterHooker::class.java
            )

            module.hook(
                param.classLoader.loadClass("com.android.systemui.qs.QSTileRevealController\$1")
                    .getDeclaredMethod("run"),
                TileRevealAnimHooker::class.java
            )
        }

        @XposedHooker
        private class TileSetterHooker : XposedInterface.Hooker {
            companion object {
                @JvmStatic
                @BeforeInvocation
                fun beforeInvocation(callback: BeforeHookCallback): TileSetterHooker {
                    if (!tileRevealed) {
                        val tileHost = XposedHelpers.getObjectField(callback.thisObject, "mHost") as Any
                        val tileHostClass = tileHost.javaClass as Class<*>

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
                            tileHostClass.getDeclaredMethod("addTile", Int::class.java, String::class.java)
                                .invoke(tileHost, -1, tileId)
                        }
                        catch (t: Throwable) {
                            // Used by Android 12, and possibly some Android 13 distros.
                            tileHostClass.getDeclaredMethod("addTile", String::class.java, Int::class.java)
                                .invoke(tileHost, tileId, -1)
                        }

                        module?.log("[Let Me Downgrade] Tile added to quick settings panel.")
                    }

                    return TileSetterHooker()
                }
            }
        }

        @XposedHooker
        private class TileRevealAnimHooker : XposedInterface.Hooker {
            companion object {
                @JvmStatic
                @BeforeInvocation
                fun beforeInvocation(callback: BeforeHookCallback): TileRevealAnimHooker {
                    if (!tileRevealed) {
                        // Properly fixing the unchecked cast warning with Kotlin adds more performance overhead than it is worth,
                        // so we are suppressing the warning instead.
                        @Suppress("UNCHECKED_CAST")
                        val tilesToReveal = XposedHelpers.getObjectField(XposedHelpers.getSurroundingThis(callback.thisObject),
                            "mTilesToReveal") as ArraySet<String>
                        tilesToReveal.add(tileId)
                        tileRevealed = true
                        module?.log("[Let Me Downgrade] Tile quick settings panel animation played. " +
                                "Let Me Downgrade will not hook SystemUI on next reboot.")
                    }

                    return TileRevealAnimHooker()
                }
            }
        }
    }
}