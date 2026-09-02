package com.personal.triptrail.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureRecognitionSettings(context: Context) {
    private val preferences = context.getSharedPreferences("smart-recognition", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false) && activeApiKey.isNotBlank()
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

    var apiKey: String
        get() = zhipuApiKey
        set(value) {
            zhipuApiKey = value
            if (value.trim().isBlank()) enabled = false
        }

    var provider: Provider
        get() = Provider.entries.firstOrNull { it.name == preferences.getString("provider", null) } ?: Provider.ZHIPU
        set(value) { preferences.edit().putString("provider", value.name).apply() }
    var zhipuApiKey: String
        get() = decrypt(preferences.getString("zhipu-api-key", preferences.getString("api-key", null)).orEmpty())
        set(value) { saveKey("zhipu-api-key", value) }
    var deepSeekApiKey: String
        get() = decrypt(preferences.getString("deepseek-api-key", null).orEmpty())
        set(value) { saveKey("deepseek-api-key", value) }
    val activeApiKey: String get() = if (provider == Provider.ZHIPU) zhipuApiKey else deepSeekApiKey

    enum class Provider { ZHIPU, DEEPSEEK }

    private fun saveKey(name: String, value: String) {
        val trimmed = value.trim()
        preferences.edit().putString(name, if (trimmed.isBlank()) "" else encrypt(trimmed)).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.getOrDefault("")

    private fun decrypt(value: String): String = runCatching {
        if (value.isBlank()) return@runCatching ""
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrDefault("")

    private companion object { const val KEY_ALIAS = "triptrail-smart-recognition" }
}
