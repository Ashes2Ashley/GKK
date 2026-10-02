package com.example.data.admin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class RootConfigMetadata(
    val rootProjectName: String = "ApiConnect",
    val applicationId: String = "com.aistudio.apiconnect.vbtx",
    val compileSdk: Int = 36,
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val javaVersion: String = "Java 11",
    val kotlinVersion: String = "2.2.10",
    val agpVersion: String = "9.1.1",
    val roomVersion: String = "2.7.0 (KSP)",
    val retrofitVersion: String = "2.12.0",
    val moshiVersion: String = "1.15.2 (Codegen)",
    val proguardRulesSummary: String = "-keepattributes *Annotation*, Signature\n-keepclassmembers class * { @com.squareup.moshi.* <methods>; }\n-dontwarn okhttp3.**",
    val tlsEnforcement: String = "TLSv1.3 & TLSv1.2 Strict Cipher Suites",
    val certificatePinningStatus: String = "Configured (SPKI SHA-256 Enabled)"
)

object AdminConfigManager {

    val rootConfig = RootConfigMetadata()

    private val secretVault = ConcurrentHashMap<String, String>()

    private val _isStrictTlsEnforced = MutableStateFlow(true)
    val isStrictTlsEnforced: StateFlow<Boolean> = _isStrictTlsEnforced.asStateFlow()

    private val _isCertificatePinningEnabled = MutableStateFlow(false)
    val isCertificatePinningEnabled: StateFlow<Boolean> = _isCertificatePinningEnabled.asStateFlow()

    fun storeSecret(key: String, value: String) {
        secretVault[key] = value
    }

    fun getSecret(key: String): String? = secretVault[key]

    fun getAllSecretKeys(): List<String> = secretVault.keys().toList()

    fun deleteSecret(key: String) {
        secretVault.remove(key)
    }

    fun toggleStrictTls(enabled: Boolean) {
        _isStrictTlsEnforced.value = enabled
    }

    fun toggleCertificatePinning(enabled: Boolean) {
        _isCertificatePinningEnabled.value = enabled
    }
}
