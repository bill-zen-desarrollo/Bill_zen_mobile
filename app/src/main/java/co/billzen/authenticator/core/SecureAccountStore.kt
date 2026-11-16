package co.billzen.authenticator.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    fun getAccounts(): List<TotpAccount> {
        val encryptedPayload = prefs.getString(KEY_ACCOUNTS_BLOB, null) ?: return emptyList()
        return try {
            val jsonText = decrypt(encryptedPayload)
            deserializeAccounts(jsonText)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAccount(account: TotpAccount): List<TotpAccount> {
        val current = getAccounts().toMutableList()
        val existingIndex = current.indexOfFirst { it.nit.equals(account.nit, ignoreCase = true) }
        if (existingIndex >= 0) {
            current[existingIndex] = account
        } else {
            current.add(account)
        }
        persistList(current)
        return current
    }

    fun removeAccount(nit: String): List<TotpAccount> {
        val updated = getAccounts().filterNot { it.nit.equals(nit, ignoreCase = true) }
        persistList(updated)
        return updated
    }
    private fun persistList(accounts: List<TotpAccount>) {
        val jsonText = serializeAccounts(accounts)
        val encryptedPayload = encrypt(jsonText)
        prefs.edit().putString(KEY_ACCOUNTS_BLOB, encryptedPayload).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$ivBase64:$cipherBase64"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "Formato cifrado inválido." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
    private fun serializeAccounts(accounts: List<TotpAccount>): String {
        val array = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("nit", acc.nit)
                put("companyName", acc.companyName)
                put("secret", acc.secret)
                put("issuer", acc.issuer)
                put("digits", acc.digits)
                put("periodSeconds", acc.periodSeconds)
            }
            array.put(obj)
        }
        return array.toString()
    }
    private fun deserializeAccounts(jsonText: String): List<TotpAccount> {
        val array = JSONArray(jsonText)
        val list = mutableListOf<TotpAccount>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                TotpAccount(
                    nit = obj.getString("nit"),
                    companyName = obj.optString("companyName", "BillZen"),
                    secret = obj.getString("secret"),
                    issuer = obj.optString("issuer", "BillZen"),
                    digits = obj.optInt("digits", TotpGenerator.DEFAULT_DIGITS),
                    periodSeconds = obj.optInt("periodSeconds", TotpGenerator.DEFAULT_PERIOD_SECONDS)
                )
            )
        }
        return list
    }
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "billzen_totp_master_key_v1"
        private const val PREFS_FILE_NAME = "billzen_secure_accounts"
        private const val KEY_ACCOUNTS_BLOB = "encrypted_accounts_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}