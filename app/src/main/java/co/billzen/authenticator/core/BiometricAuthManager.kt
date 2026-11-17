package co.billzen.authenticator.core

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Estado tipado de capacidad biométrica o credencial del dispositivo (PIN / Patrón / Contraseña).
 */
enum class BiometricCapabilityStatus {
    /** El dispositivo cuenta con biometría o credencial de pantalla configurada y lista. */
    READY,

    /** El hardware existe pero el usuario no ha registrado huella, rostro ni PIN en Android. */
    NONE_ENROLLED,

    /** El dispositivo o emulador no dispone de sensor biométrico ni bloqueo activo. */
    NO_HARDWARE,

    /** El sensor no está disponible temporalmente. */
    UNAVAILABLE
}

/**
 * Gestor de Autenticación Biométrica y Credencial de Dispositivo para BillZen Mobile ERP.
 *
 * Responsabilidades:
 * 1. Evaluar si el dispositivo físico o emulador posee biometría (Huella / Rostro) o credencial
 *    del sistema (`BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL`).
 * 2. Desplegar el diálogo nativo `BiometricPrompt` en español para proteger el acceso al
 *    Panel de Administrador cuando la empresa ya está vinculada.
 * 3. Proveer diagnóstico claro y ruta de respaldo controlada cuando se ejecuta en un emulador
 *    o teléfono sin bloqueo de pantalla configurado (`BIOMETRIC_ERROR_NONE_ENROLLED` / `NO_HARDWARE`).
 */
class BiometricAuthManager(private val activity: FragmentActivity) {

    /**
     * Determina la máscara de autenticadores permitidos según el nivel de API de Android.
     * En API 30+ combina `BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL`.
     * En API 26-29 utiliza `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` por restricción de `BiometricPrompt`.
     */
    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    /**
     * Verifica la disponibilidad de autenticación biométrica o PIN/Patrón en el dispositivo.
     */
    fun checkCapability(): BiometricCapabilityStatus {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(allowedAuthenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                if (isDeviceSecure(activity)) {
                    BiometricCapabilityStatus.READY
                } else {
                    BiometricCapabilityStatus.NONE_ENROLLED
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                if (isDeviceSecure(activity)) {
                    BiometricCapabilityStatus.READY
                } else {
                    BiometricCapabilityStatus.NO_HARDWARE
                }
            }
            else -> {
                if (isDeviceSecure(activity)) {
                    BiometricCapabilityStatus.READY
                } else {
                    BiometricCapabilityStatus.UNAVAILABLE
                }
            }
        }
    }

    /**
     * Lanza el `BiometricPrompt` oficial de Android para desbloquear la sesión de Administrador.
     *
     * @param onSuccess Callback invocado únicamente cuando el usuario valida su identidad con éxito.
     * @param onError Callback con mensaje descriptivo en español e indicador `isUnconfiguredDevice`
     *                cuando el dispositivo/emulador carece de huella o PIN registrado.
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (errorMessage: String, isUnconfiguredDevice: Boolean) -> Unit
    ) {
        when (checkCapability()) {
            BiometricCapabilityStatus.NONE_ENROLLED -> {
                onError(
                    "No hay huella digital, rostro ni PIN configurado en este dispositivo. " +
                        "Activa un bloqueo de pantalla en los Ajustes de Android o utiliza el acceso de respaldo.",
                    true
                )
                return
            }
            BiometricCapabilityStatus.NO_HARDWARE,
            BiometricCapabilityStatus.UNAVAILABLE -> {
                onError(
                    "Este dispositivo o emulador no tiene hardware biométrico ni PIN activo. " +
                        "Configura un PIN en los Ajustes del sistema o utiliza el modo de respaldo.",
                    true
                )
                return
            }
            BiometricCapabilityStatus.READY -> Unit
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE -> {
                        onError(
                            "El dispositivo no tiene huella digital, rostro ni PIN registrado en el sistema.",
                            true
                        )
                    }
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onError(
                            "Verificación cancelada. Presiona \"Verificar identidad\" para desbloquear tu empresa.",
                            false
                        )
                    }
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError(
                            "Demasiados intentos biométricos fallidos. Desbloquea con el PIN del dispositivo o intenta en 30 segundos.",
                            false
                        )
                    }
                    else -> {
                        val message = errString.toString().ifBlank {
                            "No fue posible verificar la identidad del Administrador."
                        }
                        onError(message, false)
                    }
                }
            }
        }

        return try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(PROMPT_TITLE)
                .setSubtitle(PROMPT_SUBTITLE)
                .setAllowedAuthenticators(allowedAuthenticators())
                .setConfirmationRequired(false)
                .build()

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(
                e.message ?: "Error al iniciar el lector biométrico del dispositivo.",
                !isDeviceSecure(activity)
            )
        }
    }

    /**
     * Abre la pantalla de configuración de seguridad/biometría de Android para que el usuario
     * pueda registrar su huella digital o PIN sin quedar atrapado en el bloqueo.
     */
    fun openSecuritySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, allowedAuthenticators())
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                // Ignorado si el entorno restringe abrir Ajustes
            }
        }
    }

    private fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    companion object {
        const val PROMPT_TITLE = "Acceso Administrador BillZen"
        const val PROMPT_SUBTITLE = "Verifica tu huella digital o rostro para desbloquear tu empresa"
    }
}
