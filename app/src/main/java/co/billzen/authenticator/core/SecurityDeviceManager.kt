package co.billzen.authenticator.core

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Resultado tipado de la validación de sesión única del dispositivo contra el backend de BillZen.
 */
sealed class DeviceBindingValidationResult {
    /** El servidor confirmó que este UUID es el único dispositivo autorizado para el NIT. */
    data class Authorized(val deviceUuid: String) : DeviceBindingValidationResult()

    /**
     * HTTP 403 Forbidden: El servidor rechazó este UUID porque el dueño tiene otra sesión
     * vinculada en un dispositivo distinto.
     */
    data class RejectedForbidden(val serverMessage: String) : DeviceBindingValidationResult()

    /** Error de red o servidor no disponible temporalmente. */
    data class NetworkError(val reason: String) : DeviceBindingValidationResult()
}

class SecurityDeviceManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateBoundDeviceUuid(): String {
        val existingEncrypted = prefs.getString(KEY_ENCRYPTED_DEVICE_UUID, null)
        if (existingEncrypted != null) {
            try {
                return decrypt(existingEncrypted)
            } catch (_: Exception) {
                // Si el Keystore fue invalidado por restablecimiento de fábrica, se regenera
            }
        }

        val freshUuid = UUID.randomUUID().toString()
        val encrypted = encrypt(freshUuid)
        prefs.edit().putString(KEY_ENCRYPTED_DEVICE_UUID, encrypted).apply()
        return freshUuid
    }
    fun buildDeviceBindingPayload(nit: String): JSONObject {
        val deviceUuid = getOrCreateBoundDeviceUuid()
        return JSONObject().apply {
            put("nit", nit.trim())
            put("device_uuid", deviceUuid)
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_sdk", Build.VERSION.SDK_INT)
            put("timestamp_utc", System.currentTimeMillis())
        }
    }

    fun validateSessionWithServer(
        endpointUrl: String,
        nit: String,
        bearerToken: String? = null
    ): DeviceBindingValidationResult {
        val payload = buildDeviceBindingPayload(nit)
        val deviceUuid = payload.getString("device_uuid")

        return try {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-BillZen-Device-UUID", deviceUuid)
                if (!bearerToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    DeviceBindingValidationResult.Authorized(deviceUuid)
                }
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    // HTTP 403: Existe otra sesión activa vinculada a un UUID distinto
                    DeviceBindingValidationResult.RejectedForbidden(
                        "Sesión bloqueada (HTTP 403): Este NIT ya tiene una sesión activa en otro dispositivo autorizado."
                    )
                }
                else -> {
                    DeviceBindingValidationResult.NetworkError("Respuesta inesperada del servidor: HTTP $statusCode")
                }
            }
        } catch (e: Exception) {
            DeviceBindingValidationResult.NetworkError(
                e.message ?: "No fue posible verificar la sesión con el servidor."
            )
        }
    }

    private fun getOrCreateHardwareKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) return existingEntry.secretKey

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

    private fun encrypt(plainUuid: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateHardwareKey())
        val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encryptedBytes = cipher.doFinal(plainUuid.toByteArray(Charsets.UTF_8))
        val cipherBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return "$ivBase64:$cipherBase64"
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "Payload de UUID corrupto." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateHardwareKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "billzen_device_binding_key_v1"
        private const val PREFS_FILE = "billzen_device_binding_prefs"
        private const val KEY_ENCRYPTED_DEVICE_UUID = "encrypted_bound_device_uuid"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}