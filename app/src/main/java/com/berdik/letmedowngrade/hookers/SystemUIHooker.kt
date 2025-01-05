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
        var classLoader: ClassLoader? = null
        private const val TILE_ID = "custom(${BuildConfig.APPLICATION_ID}/.QuickTile)"
        private var tileRevealed = false

        @SuppressLint("PrivateApi")
        fun hook(param: PackageLoadedParam, module: XposedModule) {
            this.module = module
            classLoader = param.classLoader

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
                @SuppressLint("PrivateApi")
                @JvmStatic
                @BeforeInvocation
                fun beforeInvocation(callback: BeforeHookCallback): TileSetterHooker {
                    if (!tileRevealed) {
                        val tileHost = XposedHelpers.getObjectField(callback.thisObject, "mHost") as Any
                        val tileHostClass = tileHost.javaClass as Class<*>

                        /*
                            The range of supported Android versions for Let Me Downgrade (12 through 15 QPR 1 as of this comment) use
                            several different approaches for adding tiles to the tile drawer. This collection of try-catch blocks
                            accounts for the different approaches that are used. Ideally, using conditional checks to identify the
                            Android version would be preferred, but since different OEMs may use different variations across the same
                            Android version, using try-catch blocks is safer.
                         */
                        try {
                            val tileSpecClass = classLoader!!.loadClass("com.android.systemui.qs.pipeline.shared.TileSpec\$Companion")
                            val createMethod = tileSpecClass.getDeclaredMethod("create", String::class.java)
                            val tileSpecObject = createMethod.invoke(null, TILE_ID) as Any
                            val componentName = XposedHelpers.getObjectField(tileSpecObject, "componentName") as Any

                            tileHostClass.getDeclaredMethod("addTile",
                                classLoader!!.loadClass("android.content.ComponentName"),
                                Boolean::class.javaPrimitiveType)
                                .invoke(tileHost, componentName, true)

                            module?.log("[Let Me Downgrade] Tile added to quick settings panel.")
                        }
                        catch (t: Throwable) {
                            try {
                                tileHostClass.getDeclaredMethod("addTile", Int::class.java, String::class.java)
                                    .invoke(tileHost, -1, TILE_ID)
                                module?.log("[Let Me Downgrade] Tile added to quick settings panel.")
                            }
                            catch (t: Throwable) {
                                tileHostClass.getDeclaredMethod("addTile", String::class.java, Int::class.java)
                                    .invoke(tileHost, TILE_ID, -1)
                                module?.log("[Let Me Downgrade] Tile added to quick settings panel.")
                            }
                        }
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
                        tilesToReveal.add(TILE_ID)
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