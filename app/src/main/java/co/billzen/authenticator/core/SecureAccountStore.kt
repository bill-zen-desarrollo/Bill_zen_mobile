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

data class PersistedMobileSession(
    val nit: String,
    val companyName: String,
    val adminName: String,
    val sessionToken: String,
    val activeBaseUrl: String,
    val fallbackBaseUrl: String?,
    val isRemoteTunnel: Boolean,
    val expiresAtEpochMillis: Long
)

class SecureAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getAccounts(): List<TotpAccount> {
        val encryptedPayload = prefs.getString(KEY_ACCOUNTS_BLOB, null) ?: return emptyList()
        return try {
            val jsonText = decrypt(encryptedPayload)
            deserializeAccounts(jsonText)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
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

    @Synchronized
    fun removeAccount(nit: String): List<TotpAccount> {
        val updated = getAccounts().filterNot { it.nit.equals(nit, ignoreCase = true) }
        persistList(updated)
        return updated
    }
    @Synchronized
    fun saveMobileSession(session: PersistedMobileSession) {
        val jsonText = serializeMobileSession(session)
        val encryptedPayload = encrypt(jsonText)
        prefs.edit().putString(KEY_MOBILE_SESSION_BLOB, encryptedPayload).apply()
    }

    @Synchronized
    fun getValidMobileSession(): PersistedMobileSession? {
        val encryptedPayload = prefs.getString(KEY_MOBILE_SESSION_BLOB, null) ?: return null
        return try {
            val jsonText = decrypt(encryptedPayload)
            val session = deserializeMobileSession(jsonText)
            if (
                session.sessionToken.isBlank() ||
                session.activeBaseUrl.isBlank() ||
                System.currentTimeMillis() >= session.expiresAtEpochMillis
            ) {
                clearMobileSession()
                null
            } else {
                session
            }
        } catch (_: Exception) {
            clearMobileSession()
            null
        }
    }

    @Synchronized
    fun clearMobileSession() {
        prefs.edit().remove(KEY_MOBILE_SESSION_BLOB).apply()
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

    private fun serializeMobileSession(session: PersistedMobileSession): String {
        return JSONObject().apply {
            put("nit", session.nit)
            put("companyName", session.companyName)
            put("adminName", session.adminName)
            put("sessionToken", session.sessionToken)
            put("activeBaseUrl", session.activeBaseUrl)
            put("fallbackBaseUrl", session.fallbackBaseUrl ?: "")
            put("isRemoteTunnel", session.isRemoteTunnel)
            put("expiresAtEpochMillis", session.expiresAtEpochMillis)
        }.toString()
    }

    private fun deserializeMobileSession(jsonText: String): PersistedMobileSession {
        val obj = JSONObject(jsonText)
        val rawFallback = obj.optString("fallbackBaseUrl", "").trim()
        return PersistedMobileSession(
            nit = obj.optString("nit", ""),
            companyName = obj.optString("companyName", "BillZen"),
            adminName = obj.optString("adminName", "Administrador"),
            sessionToken = obj.optString("sessionToken", ""),
            activeBaseUrl = obj.optString("activeBaseUrl", ""),
            fallbackBaseUrl = rawFallback.ifBlank { null },
            isRemoteTunnel = obj.optBoolean("isRemoteTunnel", false),
            expiresAtEpochMillis = obj.optLong("expiresAtEpochMillis", 0L)
        )
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "billzen_totp_master_key_v1"
        private const val PREFS_FILE_NAME = "billzen_secure_accounts"
        private const val KEY_ACCOUNTS_BLOB = "encrypted_accounts_v1"
        private const val KEY_MOBILE_SESSION_BLOB = "encrypted_mobile_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        const val DEFAULT_MOBILE_SESSION_TTL_SECONDS = 43_200L
    }
}
