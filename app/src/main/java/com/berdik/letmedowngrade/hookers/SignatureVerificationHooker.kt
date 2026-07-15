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
        private const val PERMISSION_CAPABILITY = 4
        private const val AUTH_CAPABILITY = 16

        var module: XposedModule? = null
            private set

        private val keySetBypass = ThreadLocal<Boolean>()

        fun hook(param: SystemServerStartingParam, module: XposedModule) {
            this.module = module
            val classLoader = param.classLoader

            hookSigningDetails(classLoader, module)
            hookStrictJarVerifier(classLoader, module)
            hookPackageManagerServiceUtils(classLoader, module)
            hookKeySetManagerService(classLoader, module)
            hookMessageDigest(classLoader, module)
            hookApkSigningBlockUtils(classLoader, module)
            hookApkSignatureVerifier(classLoader, module)
            hookScanPackageUtils(classLoader, module)
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

            try {
                val signaturesMatchExactlyMethod = signingDetailsClass.getDeclaredMethod(
                    "signaturesMatchExactly", signingDetailsClass
                )
                module.hook(signaturesMatchExactlyMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        module.log(Log.INFO, TAG, "Bypassed signaturesMatchExactly")
                        return@intercept true
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook signaturesMatchExactly: $e")
            }

            try {
                val hasCommonAncestorMethod = signingDetailsClass.getDeclaredMethod(
                    "hasCommonAncestor", signingDetailsClass
                )
                module.hook(hasCommonAncestorMethod).intercept { chain ->
                    if (isBypassActive(module) && isCalledFromVerifySignatures()) {
                        module.log(Log.INFO, TAG, "Bypassed hasCommonAncestor")
                        return@intercept true
                    }
                    chain.proceed()
                }
            } catch (_: Exception) {}
        }

        @SuppressLint("PrivateApi")
        private fun hookStrictJarVerifier(classLoader: ClassLoader, module: XposedModule) {
            try {
                val strictJarVerifierClass = classLoader.loadClass("android.util.jar.StrictJarVerifier")

                val verifyMessageDigestMethod = strictJarVerifierClass.declaredMethods.first { method ->
                    method.name == "verifyMessageDigest" && method.returnType == Boolean::class.javaPrimitiveType
                }
                module.hook(verifyMessageDigestMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

                val verifyMethod = strictJarVerifierClass.declaredMethods.first { method ->
                    method.name == "verify" && method.returnType == Boolean::class.javaPrimitiveType
                }
                module.hook(verifyMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

                val constructor = strictJarVerifierClass.declaredConstructors.first()
                val rollbackField = strictJarVerifierClass.declaredFields.first { field ->
                    field.name == "signatureSchemeRollbackProtectionsEnforced"
                }
                rollbackField.isAccessible = true
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    if (isBypassActive(module)) {
                        rollbackField.set(chain.thisObject, false)
                    }
                    result
                }

                hookVerifyBytes(classLoader, module, strictJarVerifierClass)
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook StrictJarVerifier: $e")
            }
        }

        @SuppressLint("PrivateApi")
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
        private fun hookPackageManagerServiceUtils(classLoader: ClassLoader, module: XposedModule) {
            try {
                val utilsClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
                val verifySignaturesMethod = utilsClass.declaredMethods.first { method ->
                    method.name == "verifySignatures" && method.returnType == Boolean::class.javaPrimitiveType
                }
                module.deoptimize(verifySignaturesMethod)
                module.hook(verifySignaturesMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        module.log(Log.INFO, TAG, "Bypassed verifySignatures")
                        return@intercept false
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook verifySignatures: $e")
            }
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

        private fun hookMessageDigest(classLoader: ClassLoader, module: XposedModule) {
            try {
                val messageDigestClass = classLoader.loadClass("java.security.MessageDigest")
                val isEqualMethod = messageDigestClass.getDeclaredMethod(
                    "isEqual", ByteArray::class.java, ByteArray::class.java
                )
                module.hook(isEqualMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept true
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook MessageDigest.isEqual: $e")
            }
        }

        @SuppressLint("PrivateApi")
        private fun hookApkSigningBlockUtils(classLoader: ClassLoader, module: XposedModule) {
            try {
                val utilsClass = classLoader.loadClass("android.util.apk.ApkSigningBlockUtils")

                val parseVerityMethod = utilsClass.declaredMethods.first { method ->
                    method.name == "parseVerityDigestAndVerifySourceLength"
                }
                module.hook(parseVerityMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        val digest = chain.args[0] as ByteArray
                        return@intercept digest.copyOfRange(0, minOf(32, digest.size))
                    }
                    chain.proceed()
                }

                val verifyIntegrityMethod = utilsClass.declaredMethods.first { method ->
                    method.name == "verifyIntegrityForVerityBasedAlgorithm"
                }
                module.hook(verifyIntegrityMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept null
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook ApkSigningBlockUtils: $e")
            }
        }

        @SuppressLint("PrivateApi")
        private fun hookApkSignatureVerifier(classLoader: ClassLoader, module: XposedModule) {
            try {
                val verifierClass = classLoader.loadClass("android.util.apk.ApkSignatureVerifier")
                val getMinSchemeMethod = verifierClass.getDeclaredMethod(
                    "getMinimumSignatureSchemeVersionForTargetSdk", Int::class.javaPrimitiveType
                )
                module.hook(getMinSchemeMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept 0
                    }
                    chain.proceed()
                }
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "Failed to hook ApkSignatureVerifier: $e")
            }
        }

        @SuppressLint("PrivateApi")
        private fun hookScanPackageUtils(classLoader: ClassLoader, module: XposedModule) {
            try {
                val scanUtilsClass = classLoader.loadClass("com.android.server.pm.ScanPackageUtils")
                val assertMethod = scanUtilsClass.declaredMethods.first { method ->
                    method.name == "assertMinSignatureSchemeIsValid"
                }
                module.hook(assertMethod).intercept { chain ->
                    if (isBypassActive(module)) {
                        return@intercept null
                    }
                    chain.proceed()
                }
            } catch (_: Exception) {}
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

        private fun isCalledFromVerifySignatures(): Boolean {
            return Arrays.stream(Thread.currentThread().stackTrace)
                .anyMatch { element -> element.methodName == "verifySignatures" }
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
