package com.yourssu.ragent.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.yourssu.ragent.model.AiApiProvider
import com.yourssu.ragent.model.AiApiKeyState

/**
 * API 키 원문은 저장하지 않고 Android Keystore의 AES 키로 암호화한 값만 보관한다.
 * 암호화 키는 앱 프로세스 밖으로 추출할 수 없으며, SharedPreferences 파일은 백업에서 제외한다.
 */
class AiApiKeyStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE
    )

    fun getState(): AiApiKeyState {
        val provider = preferences.getString(ProviderKey, null)
            ?.let { saved -> AiApiProvider.entries.firstOrNull { it.name == saved } }
            ?: AiApiProvider.Gemini

        val savedModelId = preferences.getString(ModelKey, null)
        val selectedModelId = provider.models
            .firstOrNull { it.id == savedModelId }
            ?.id
            ?: provider.defaultModelId

        if (savedModelId != selectedModelId) {
            preferences.edit().putString(ModelKey, selectedModelId).apply()
        }

        return AiApiKeyState(
            provider = provider,
            selectedModelId = selectedModelId,
            hasStoredKey = hasStoredKey(provider)
        )
    }

    fun hasStoredKey(provider: AiApiProvider): Boolean {
        val suffix = provider.requestValue
        val hasCurrentKey = preferences.contains(encryptedKey(suffix)) &&
            preferences.contains(initializationVectorKey(suffix))
        val hasLegacyKey = legacyValue(EncryptedKey, provider) != null &&
            legacyValue(InitializationVectorKey, provider) != null
        return (hasCurrentKey || hasLegacyKey) &&
            androidKeyStore().containsAlias(KeyAlias)
    }

    fun updateModel(modelId: String) {
        val provider = getState().provider
        val safeModelId = provider.models
            .firstOrNull { it.id == modelId }
            ?.id
            ?: provider.defaultModelId
        preferences.edit().putString(ModelKey, safeModelId).apply()
    }

    @Throws(Exception::class)
    fun save(provider: AiApiProvider, apiKey: String) {
        require(apiKey.isNotBlank()) { "API 키를 입력해 주세요." }

        val suffix = provider.requestValue
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(StandardCharsets.UTF_8))

        val saved = preferences.edit()
            .putString(ProviderKey, provider.name)
            .putString(ModelKey, provider.defaultModelId)
            .putString(initializationVectorKey(suffix), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(encryptedKey(suffix), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()

        if (!saved) throw IOException("API 키 설정을 기기에 저장하지 못했습니다.")
    }

    /** SDK 연동 시에만 호출하고 반환값을 로그에 남기지 않는다. */
    @Throws(Exception::class)
    fun readApiKey(provider: AiApiProvider): String? {
        val suffix = provider.requestValue
        val initializationVector = preferences.getString(
            initializationVectorKey(suffix),
            legacyValue(InitializationVectorKey, provider)
        )
            ?: return null
        val encrypted = preferences.getString(
            encryptedKey(suffix),
            legacyValue(EncryptedKey, provider)
        ) ?: return null

        val secretKey = androidKeyStore().getKey(KeyAlias, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GcmTagLengthBits, Base64.decode(initializationVector, Base64.NO_WRAP))
        )
        return String(
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
            StandardCharsets.UTF_8
        )
    }

    fun clear(provider: AiApiProvider) {
        val suffix = provider.requestValue
        preferences.edit()
            .remove(encryptedKey(suffix))
            .remove(initializationVectorKey(suffix))
            .apply {
                if (preferences.getString(ProviderKey, null) == provider.name) {
                    remove(ProviderKey).remove(ModelKey)
                    remove(EncryptedKey).remove(InitializationVectorKey)
                }
            }
            .commit()
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun legacyValue(key: String, provider: AiApiProvider): String? {
        return if (preferences.getString(ProviderKey, null) == provider.name) {
            preferences.getString(key, null)
        } else {
            null
        }
    }

    private fun encryptedKey(provider: String): String = "${EncryptedKey}_$provider"

    private fun initializationVectorKey(provider: String): String =
        "${InitializationVectorKey}_$provider"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = androidKeyStore()
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance(AndroidKeyStore).apply {
        load(null)
    }

    private companion object {
        const val PreferencesName = "ragent_ai_api_settings"
        const val ProviderKey = "provider"
        const val ModelKey = "selected_model"
        const val InitializationVectorKey = "initialization_vector"
        const val EncryptedKey = "encrypted_api_key"
        const val KeyAlias = "ragent_ai_api_key_encryption"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128
    }
}
