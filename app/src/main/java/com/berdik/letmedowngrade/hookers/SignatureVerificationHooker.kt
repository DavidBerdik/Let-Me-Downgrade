package com.berdik.letmedowngrade.hookers

import android.annotation.SuppressLint
import android.util.Log
import com.berdik.letmedowngrade.BuildConfig
import com.berdik.letmedowngrade.TAG
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Arrays

class SignatureVerificationHooker {
    companion object {
        private const val PREF_KEY = "signatureBypassActive"

        private const val ID_CHECK_CAPABILITY = "checkCapability"
        private const val ID_CHECK_CAPABILITY_RECOVER = "checkCapabilityRecover"
        private const val ID_VERIFY_BYTES = "verifyBytes"
        private const val ID_SHOULD_CHECK_UPGRADE_KEY_SET = "shouldCheckUpgradeKeySetLocked"
        private const val ID_CHECK_UPGRADE_KEY_SET = "checkUpgradeKeySetLocked"

        private val SIGNATURE_HOOK_IDS = setOf(
            ID_CHECK_CAPABILITY,
            ID_CHECK_CAPABILITY_RECOVER,
            ID_VERIFY_BYTES,
            ID_SHOULD_CHECK_UPGRADE_KEY_SET,
            ID_CHECK_UPGRADE_KEY_SET,
        )

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
            installHooks(param.classLoader, module, null)
        }

        fun replaceHooks(
            oldHandles: List<XposedInterface.HookHandle>,
            module: XposedModule,
        ): Set<XposedInterface.HookHandle> {
            this.module = module
            val claimed = mutableSetOf<XposedInterface.HookHandle>()
            val presentIds = mutableSetOf<String>()

            for (handle in oldHandles) {
                val id = handle.id ?: handle.executable.name
                if (id !in SIGNATURE_HOOK_IDS) {
                    continue
                }
                val hooker = hookerForReplace(id, module, handle) ?: continue
                handle.replaceHook(hooker)
                claimed.add(handle)
                presentIds.add(id)
            }

            val missing = SIGNATURE_HOOK_IDS - presentIds
            if (missing.isNotEmpty() && oldHandles.isNotEmpty()) {
                val classLoader = oldHandles.first().executable.declaringClass.classLoader
                    ?: return claimed
                installHooks(classLoader, module, missing)
            }
            return claimed
        }

        fun clearModuleRef() {
            module = null
        }

        private fun hookerForReplace(
            id: String,
            module: XposedModule,
            handle: XposedInterface.HookHandle,
        ): XposedInterface.Hooker? {
            return when (id) {
                ID_CHECK_CAPABILITY -> createCheckCapabilityHooker(module)
                ID_CHECK_CAPABILITY_RECOVER -> createCheckCapabilityRecoverHooker(module)
                ID_VERIFY_BYTES -> {
                    val classLoader = handle.executable.declaringClass.classLoader
                        ?: return null
                    val reflection = resolveVerifyBytesReflection(classLoader) ?: return null
                    createVerifyBytesHooker(module, reflection)
                }
                ID_SHOULD_CHECK_UPGRADE_KEY_SET -> createShouldCheckUpgradeKeySetHooker(module)
                ID_CHECK_UPGRADE_KEY_SET -> createCheckUpgradeKeySetHooker(module)
                else -> null
            }
        }

        private fun installHooks(
            classLoader: ClassLoader,
            module: XposedModule,
            onlyIds: Set<String>?,
        ) {
            fun shouldInstall(id: String) = onlyIds == null || id in onlyIds

            if (shouldInstall(ID_CHECK_CAPABILITY) || shouldInstall(ID_CHECK_CAPABILITY_RECOVER)) {
                hookSigningDetails(classLoader, module, onlyIds)
            }
            if (shouldInstall(ID_VERIFY_BYTES)) {
                hookStrictJarVerifier(classLoader, module)
            }
            if (shouldInstall(ID_SHOULD_CHECK_UPGRADE_KEY_SET) ||
                shouldInstall(ID_CHECK_UPGRADE_KEY_SET)
            ) {
                hookKeySetManagerService(classLoader, module, onlyIds)
            }
        }

        private fun hookMethod(
            module: XposedModule,
            method: Method,
            id: String,
            hooker: XposedInterface.Hooker,
        ) {
            val builder = module.hook(method)
            /*
                setId is API 102-only; on API 101 frameworks the compile-time API is still 102 but
                getApiVersion() returns the framework LIB_API so this guard is a real 101/102 split.
             */
            if (module.apiVersion >= XposedInterface.API_102) {
                builder.setId(id)
            }
            builder.intercept(hooker)
        }

        private fun isBypassActive(module: XposedModule): Boolean {
            return module.getRemotePreferences(BuildConfig.APPLICATION_ID)
                .getBoolean(PREF_KEY, false)
        }

        private fun createCheckCapabilityHooker(module: XposedModule): XposedInterface.Hooker =
            object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (isBypassActive(module)) {
                        val flags = chain.args[1] as Int
                        if (flags != PERMISSION_CAPABILITY && flags != AUTH_CAPABILITY) {
                            module.log(Log.INFO, TAG, "Bypassed checkCapability")
                            return true
                        }
                    }
                    return chain.proceed()
                }
            }

        private fun createCheckCapabilityRecoverHooker(module: XposedModule): XposedInterface.Hooker =
            object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (isBypassActive(module)) {
                        val flags = chain.args[1] as Int
                        if (flags != PERMISSION_CAPABILITY && flags != AUTH_CAPABILITY) {
                            module.log(Log.INFO, TAG, "Bypassed checkCapabilityRecover")
                            return true
                        }
                    }
                    return chain.proceed()
                }
            }

        private data class VerifyBytesReflection(
            val pkcs7Constructor: Constructor<*>,
            val getSignerInfosMethod: Method,
            val getCertificateChainMethod: Method,
        )

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun resolveVerifyBytesReflection(classLoader: ClassLoader): VerifyBytesReflection? {
            return try {
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
                VerifyBytesReflection(
                    pkcs7Constructor,
                    getSignerInfosMethod,
                    getCertificateChainMethod,
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun createVerifyBytesHooker(
            module: XposedModule,
            reflection: VerifyBytesReflection,
        ): XposedInterface.Hooker = object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                if (isBypassActive(module)) {
                    try {
                        val block = reflection.pkcs7Constructor.newInstance(chain.args[0])
                        val signerInfos = reflection.getSignerInfosMethod.invoke(block) as Array<*>
                        if (signerInfos.isNotEmpty()) {
                            val certs = reflection.getCertificateChainMethod.invoke(
                                signerInfos[0],
                                block,
                            )
                            return certs
                        }
                    } catch (e: Exception) {
                        module.log(Log.ERROR, TAG, "verifyBytes bypass failed: $e")
                    }
                }
                return result
            }
        }

        private fun createShouldCheckUpgradeKeySetHooker(module: XposedModule): XposedInterface.Hooker =
            object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (isBypassActive(module) && isCalledFromInstallFlow()) {
                        keySetBypass.set(true)
                        return true
                    }
                    keySetBypass.set(false)
                    return chain.proceed()
                }
            }

        private fun createCheckUpgradeKeySetHooker(module: XposedModule): XposedInterface.Hooker =
            object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (isBypassActive(module) && keySetBypass.get() == true) {
                        return true
                    }
                    return chain.proceed()
                }
            }

        private fun hookSigningDetails(
            classLoader: ClassLoader,
            module: XposedModule,
            onlyIds: Set<String>?,
        ) {
            val signingDetailsClass = findSigningDetailsClass(classLoader) ?: return

            if (onlyIds == null || ID_CHECK_CAPABILITY in onlyIds) {
                try {
                    val checkCapabilityMethod = signingDetailsClass.getDeclaredMethod(
                        "checkCapability", signingDetailsClass, Int::class.javaPrimitiveType
                    )
                    hookMethod(
                        module,
                        checkCapabilityMethod,
                        ID_CHECK_CAPABILITY,
                        createCheckCapabilityHooker(module),
                    )
                } catch (e: Exception) {
                    module.log(Log.ERROR, TAG, "Failed to hook checkCapability: $e")
                }
            }

            if (onlyIds == null || ID_CHECK_CAPABILITY_RECOVER in onlyIds) {
                try {
                    val checkCapabilityRecoverMethod = signingDetailsClass.getDeclaredMethod(
                        "checkCapabilityRecover", signingDetailsClass, Int::class.javaPrimitiveType
                    )
                    hookMethod(
                        module,
                        checkCapabilityRecoverMethod,
                        ID_CHECK_CAPABILITY_RECOVER,
                        createCheckCapabilityRecoverHooker(module),
                    )
                } catch (e: Exception) {
                    module.log(Log.ERROR, TAG, "Failed to hook checkCapabilityRecover: $e")
                }
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
            strictJarVerifierClass: Class<*>,
        ) {
            try {
                val verifyBytesMethod = strictJarVerifierClass.getDeclaredMethod(
                    "verifyBytes", ByteArray::class.java, ByteArray::class.java
                )
                val reflection = resolveVerifyBytesReflection(classLoader) ?: return
                hookMethod(
                    module,
                    verifyBytesMethod,
                    ID_VERIFY_BYTES,
                    createVerifyBytesHooker(module, reflection),
                )
            } catch (_: Exception) {}
        }

        @SuppressLint("PrivateApi")
        private fun hookKeySetManagerService(
            classLoader: ClassLoader,
            module: XposedModule,
            onlyIds: Set<String>?,
        ) {
            try {
                val keySetClass = classLoader.loadClass("com.android.server.pm.KeySetManagerService")

                if (onlyIds == null || ID_SHOULD_CHECK_UPGRADE_KEY_SET in onlyIds) {
                    val shouldCheckMethod = keySetClass.declaredMethods.first { method ->
                        method.name == ID_SHOULD_CHECK_UPGRADE_KEY_SET &&
                            method.returnType == Boolean::class.javaPrimitiveType
                    }
                    hookMethod(
                        module,
                        shouldCheckMethod,
                        ID_SHOULD_CHECK_UPGRADE_KEY_SET,
                        createShouldCheckUpgradeKeySetHooker(module),
                    )
                }

                if (onlyIds == null || ID_CHECK_UPGRADE_KEY_SET in onlyIds) {
                    val checkUpgradeMethod = keySetClass.declaredMethods.first { method ->
                        method.name == ID_CHECK_UPGRADE_KEY_SET &&
                            method.returnType == Boolean::class.javaPrimitiveType
                    }
                    hookMethod(
                        module,
                        checkUpgradeMethod,
                        ID_CHECK_UPGRADE_KEY_SET,
                        createCheckUpgradeKeySetHooker(module),
                    )
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
                return classLoader.loadClass($$"android.content.pm.PackageParser$SigningDetails")
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