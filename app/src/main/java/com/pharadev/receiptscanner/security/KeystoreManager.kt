package com.pharadev.receiptscanner.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor() {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_KEY_ALIAS = "receipt_scanner_key"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val KEY_LENGTH = 256
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    init {
        generateKeyIfNeeded()
    }
    
    private fun generateKeyIfNeeded() {
        if (!keyStore.containsAlias(ENCRYPTION_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH)
                .setUserAuthenticationRequired(false) // Set to true if biometric auth needed
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    fun encryptData(plaintext: String): EncryptedData? {
        return try {
            val secretKey = keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val ciphertext = cipher.doFinal(plaintext.toByteArray())
            val iv = cipher.iv
            
            EncryptedData(ciphertext, iv)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun decryptData(encryptedData: EncryptedData): String? {
        return try {
            val secretKey = keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(AES_MODE)
            val ivParameterSpec = IvParameterSpec(encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
            
            val plaintext = cipher.doFinal(encryptedData.ciphertext)
            String(plaintext)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun deleteKey(): Boolean {
        return try {
            keyStore.deleteEntry(ENCRYPTION_KEY_ALIAS)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}