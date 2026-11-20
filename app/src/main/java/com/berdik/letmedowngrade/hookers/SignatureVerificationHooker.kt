package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.Log
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.TAG
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.Arrays

class SignatureVerificationHooker {
    companion object {
        private const val PREF_KEY = "signatureBypassActive"

        /*
         * SigningDetails.CertCapabilities values passed as the flags argument to checkCapability
         * and checkCapabilityRecover. When the bypass is on, those hooks still run the real check
         * for these flags so install-time signature skipping does not weaken permission- or
         * auth-related signing checks. On Android 12, 4 is PERMISSION; on Android 13+, 4 is
         * SHARED_USER_ID and PERMISSION is 8 — we exclude 4 and 16 on all supported versions.
         */
        private const val PERMISSION_CAPABILITY = 4
        private const val AUTH_CAPABILITY = 16

        var module: XposedModule? = null
            private set

        /*
         * Per-install-thread flag coordinating KeySetManagerService hooks: shouldCheckUpgradeKeySetLocked
         * sets this when bypassing during an install flow so checkUpgradeKeySetLocked can short-circuit
         * only on that same thread. ThreadLocal avoids leaking bypass state across concurrent installs.
         */
        private val keySetBypass = ThreadLocal<Boolean>()

        fun hook(param: SystemServerStartingParam, module: XposedModule) {
            this.module = module
            val classLoader = param.classLoader

            hookSigningDetails(classLoader, module)
            hookStrictJarVerifier(classLoader, module)
            hookKeySetManagerService(classLoader, module)
        }

        private fun isBypassActive(module: XposedModule): Boolean {
            return module.getRemotePreferences(BuildConfig.APPLICATION_ID)
                .getBoolean(PREF_KEY, false)
        }

        private fun hookSigningDetails(classLoader: ClassLoader, module: XposedModule) {
            val signingDetailsClass = findSigningDetailsClass(classLoader) ?: return

            try {
                val checkCapabilityMethod = signingDetailsClass.getDeclaredMethod(
                    "checkCapability", signingDetailsClass, Int::class.javaPrimitiveType
                )
                module.hook(checkCapabilityMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        val flags = chain.args[1] as Int
                        if (flags != PERMISSION_CAPABILITY && flags != AUTH_CAPABILITY) {
                            module.log(Log.INFO, TAG, "Bypassed checkCapability")
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook checkCapability: $e")
            }

            try {
                val checkCapabilityRecoverMethod = signingDetailsClass.getDeclaredMethod(
                    "checkCapabilityRecover", signingDetailsClass, Int::class.javaPrimitiveType
                )
                module.hook(checkCapabilityRecoverMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        val flags = chain.args[1] as Int
                        if (flags != PERMISSION_CAPABILITY && flags != AUTH_CAPABILITY) {
                            module.log(Log.INFO, TAG, "Bypassed checkCapabilityRecover")
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook checkCapabilityRecover: $e")
            }
        }

        @SuppressLint("PrivateApi")
        private fun hookStrictJarVerifier(classLoader: ClassLoader, module: XposedModule) {
            try {
                val strictJarVerifierClass = classLoader.loadClass("android.util.jar.StrictJarVerifier")
                hookVerifyBytes(classLoader, module, strictJarVerifierClass)
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook StrictJarVerifier: $e")
            }
        }

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun hookVerifyBytes(
            classLoader: ClassLoader,
            module: XposedModule,
            strictJarVerifierClass: Class<*>
        ) {
            try {
                val verifyBytesMethod = strictJarVerifierClass.getDeclaredMethod(
                    "verifyBytes", ByteArray::class.java, ByteArray::class.java
                )
                val pkcs7Class = classLoader.loadClass("sun.security.pkcs.PKCS7")
                val pkcs7Constructor = pkcs7Class.declaredConstructors.first { constructor ->
                    constructor.parameterTypes.size == 1 &&
                        constructor.parameterTypes[0] == ByteArray::class.java
                }
                val getSignerInfosMethod = pkcs7Class.getDeclaredMethod("getSignerInfos")
                val signerInfoClass = classLoader.loadClass("sun.security.pkcs.SignerInfo")
                val getCertificateChainMethod = signerInfoClass.getDeclaredMethod(
                    "getCertificateChain", pkcs7Class
                )

                module.hook(verifyBytesMethod).intercept { chain ->
                    val result = chain.proceed()
                    if (isBypassActive(module)) {
                        try {
                            val block = pkcs7Constructor.newInstance(chain.args[0])
                            val signerInfos = getSignerInfosMethod.invoke(block) as Array<*>
                            if (signerInfos.isNotEmpty()) {
                                val certs = getCertificateChainMethod.invoke(signerInfos[0], block)
                                return@intercept certs
                            }
                        } catch (e: Exception) {
                            module.log(Log.ERROR, TAG, "verifyBytes bypass failed: $e")
                        }
                    }
                    result
                }
            } catch (_: Exception) {}
        }

        @SuppressLint("PrivateApi")
        private fun hookKeySetManagerService(classLoader: ClassLoader, module: XposedModule) {
            try {
                val keySetClass = classLoader.loadClass("com.android.server.pm.KeySetManagerService")

                val shouldCheckMethod = keySetClass.declaredMethods.first { method ->
                    method.name == "shouldCheckUpgradeKeySetLocked" &&
                        method.returnType == Boolean::class.javaPrimitiveType
                }
                module.hook(shouldCheckMethod).intercept { chain ->
                    if (isBypassActive(module) && isCalledFromInstallFlow()) {
                        keySetBypass.set(true)
                        return@intercept true
                    }
                    keySetBypass.set(false)
                    chain.proceed()
                }

                val checkUpgradeMethod = keySetClass.declaredMethods.first { method ->
                    method.name == "checkUpgradeKeySetLocked" &&
                        method.returnType == Boolean::class.javaPrimitiveType
                }
                module.hook(checkUpgradeMethod).intercept { chain ->
                    if (isBypassActive(module) && keySetBypass.get() == true) {
                        return@intercept true
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook KeySetManagerService: $e")
            }
        }

        @SuppressLint("PrivateApi")
        private fun findSigningDetailsClass(classLoader: ClassLoader): Class<*>? {
            try {
                return classLoader.loadClass("android.content.pm.SigningDetails")
            } catch (_: Exception) {}

            try {
                return classLoader.loadClass("android.content.pm.PackageParser\$SigningDetails")
            } catch (_: Exception) {}

            return null
        }

        private fun isCalledFromInstallFlow(): Boolean {
            return Arrays.stream(Thread.currentThread().stackTrace)
                .anyMatch { element ->
                    element.methodName == "preparePackage" ||
                        element.methodName == "reconcileInstallPackages" ||
                        element.methodName == "preparePackageLI" ||
                        element.methodName == "installPackageLI"
                }
        }
    }
}