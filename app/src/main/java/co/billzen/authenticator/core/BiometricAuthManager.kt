package co.billzen.authenticator.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings

/**
 * Estado tipado de capacidad biométrica o bloqueo seguro del dispositivo.
 */
enum class BiometricCapabilityStatus {
    /** El dispositivo cuenta con bloqueo seguro (PIN/Patrón) y/o biometría activa. */
    READY,

    /** El dispositivo o emulador no tiene huella digital, rostro ni PIN configurado. */
    NONE_ENROLLED,

    /** El dispositivo o emulador carece de hardware biométrico y de bloqueo activo. */
    NO_HARDWARE,

    /** El subsistema biométrico no está disponible temporalmente. */
    UNAVAILABLE
}

/**
 * Gestor de Autenticación Biométrica 100 % Nativo del SDK de Android para BillZen Mobile ERP.
 *
 * Utiliza exclusivamente las APIs nativas del framework (`android.hardware.biometrics.BiometricPrompt`,
 * `android.hardware.biometrics.BiometricManager` y `android.app.KeyguardManager`) sobre `android.app.Activity`
 * (`ComponentActivity`), eliminando cualquier dependencia de `androidx.biometric` o `FragmentActivity`.
 */
class BiometricAuthManager(private val activity: Activity) {

    /**
     * Evalúa si el teléfono o emulador tiene bloqueo de pantalla (`KeyguardManager.isDeviceSecure`)
     * y sensores biométricos configurados antes de invocar `BiometricPrompt`.
     */
    fun checkCapability(): BiometricCapabilityStatus {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isDeviceSecure != true) {
            return BiometricCapabilityStatus.NONE_ENROLLED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val biometricManager = activity.getSystemService(BiometricManager::class.java)
                ?: return BiometricCapabilityStatus.READY
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return when (biometricManager.canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.READY
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NONE_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus.NO_HARDWARE
                else -> BiometricCapabilityStatus.UNAVAILABLE
            }
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val biometricManager = activity.getSystemService(BiometricManager::class.java)
                ?: return BiometricCapabilityStatus.READY
            @Suppress("DEPRECATION")
            return when (biometricManager.canAuthenticate()) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapabilityStatus.READY
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapabilityStatus.NONE_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapabilityStatus.NO_HARDWARE
                else -> BiometricCapabilityStatus.UNAVAILABLE
            }
        }

        return BiometricCapabilityStatus.READY
    }

    /**
     * Lanza el diálogo biométrico nativo del sistema (`android.hardware.biometrics.BiometricPrompt`)
     * únicamente si el dispositivo tiene seguridad configurada. Si `isDeviceSecure == false`,
     * retorna de inmediato al estado de respaldo sin bloquear el emulador.
     */
    @SuppressLint("MissingPermission")
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (errorMessage: String, isUnconfiguredDevice: Boolean) -> Unit
    ) {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isDeviceSecure != true) {
            onError(
                "Este dispositivo no tiene huella ni PIN configurado. " +
                    "Activa un bloqueo en los Ajustes de Android o continúa con el acceso de respaldo.",
                true
            )
            return
        }

        when (checkCapability()) {
            BiometricCapabilityStatus.NONE_ENROLLED,
            BiometricCapabilityStatus.NO_HARDWARE,
            BiometricCapabilityStatus.UNAVAILABLE -> {
                onError(
                    "Este dispositivo no tiene huella ni PIN configurado. " +
                        "Configura la seguridad en los Ajustes del sistema o utiliza el acceso de respaldo.",
                    true
                )
                return
            }
            BiometricCapabilityStatus.READY -> Unit
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(PROMPT_TITLE)
                    .setSubtitle(PROMPT_SUBTITLE)
                    .setConfirmationRequired(false)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    builder.setNegativeButton(
                        "Usar respaldo",
                        activity.mainExecutor
                    ) { _, _ ->
                        onError(
                            "Verificación cancelada. Puedes reintentar o usar el acceso de respaldo.",
                            true
                        )
                    }
                }

                val biometricPrompt = builder.build()
                val cancellationSignal = CancellationSignal()

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        when (errorCode) {
                            BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
                            BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                                onError(
                                    "Este dispositivo no tiene huella ni PIN configurado.",
                                    true
                                )
                            }
                            BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
                            BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> {
                                onError(
                                    "Verificación cancelada. Presiona \"Verificar identidad\" para desbloquear tu empresa.",
                                    false
                                )
                            }
                            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                                onError(
                                    "Demasiados intentos biométricos fallidos. Desbloquea con el PIN del dispositivo o espera 30 segundos.",
                                    false
                                )
                            }
                            else -> {
                                val message = errString?.toString()?.ifBlank { null }
                                    ?: "No fue posible verificar la identidad del Administrador."
                                onError(message, false)
                            }
                        }
                    }
                }

                biometricPrompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    callback
                )
            } catch (e: Exception) {
                onError(
                    e.message ?: "No fue posible iniciar el lector biométrico nativo.",
                    true
                )
            }
        } else {
            onError(
                "Versión de Android sin soporte BiometricPrompt nativo. Usa el acceso de respaldo.",
                true
            )
        }
    }

    /**
     * Abre la pantalla de configuración de seguridad/biometría de Android para que el Administrador
     * pueda registrar su huella digital o PIN.
     */
    fun openSecuritySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
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

    companion object {
        const val PROMPT_TITLE = "Acceso Administrador BillZen"
        const val PROMPT_SUBTITLE = "Verifica tu huella digital o rostro para desbloquear tu empresa"
    }
}
