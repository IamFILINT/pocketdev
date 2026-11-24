package dev.pocketdev.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyStore {
    fun read(): String?
    fun save(key: String)
    fun clear()
}

class KeystoreApiKeyStore(context: Context) : ApiKeyStore {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? {
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(AUTH_TAG_BITS, Base64.decode(iv, Base64.DEFAULT)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT)), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun save(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API key must not be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        check(
            preferences.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
                .putString(
                    KEY_CIPHERTEXT,
                    Base64.encodeToString(cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8)), Base64.DEFAULT),
                )
                .commit(),
        ) { "Unable to persist API key" }
    }

    override fun clear() {
        check(
            preferences.edit()
                .remove(KEY_IV)
                .remove(KEY_CIPHERTEXT)
                .commit(),
        ) { "Unable to clear API key" }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pocketdev.provider_api_key"
        const val PREFERENCES_NAME = "pocketdev.secrets"
        const val KEY_IV = "provider_api_key.iv"
        const val KEY_CIPHERTEXT = "provider_api_key.ciphertext"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AUTH_TAG_BITS = 128
    }
}
