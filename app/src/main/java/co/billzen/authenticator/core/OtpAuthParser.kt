package co.billzen.authenticator.core

import android.net.Uri

data class TotpAccount(
    val nit: String,
    val companyName: String,
    val secret: String,
    val issuer: String = "BillZen",
    val digits: Int = TotpGenerator.DEFAULT_DIGITS,
    val periodSeconds: Int = TotpGenerator.DEFAULT_PERIOD_SECONDS
)

object OtpAuthParser {
    fun parse(rawUri: String): TotpAccount {
        val trimmed = rawUri.trim()
        val uri = Uri.parse(trimmed)
        require(uri.scheme?.lowercase() == "otpauth") {
            "El código QR no corresponde a un autenticador (esquema otpauth:// requerido)."
        }
        require(uri.host?.lowercase() == "totp") {
            "Solo se admiten códigos de tipo TOTP (otpauth://totp/...)."
        }

        val rawSecret = uri.getQueryParameter("secret")
            ?.trim()
            ?.uppercase()
            ?.replace(" ", "")
            ?: throw IllegalArgumentException("El código QR no contiene el parámetro 'secret'.")
        require(Base32.isValid(rawSecret)) {
            "La clave secreta contenida en el QR no es un Base32 válido."
        }

        val rawPath = uri.path?.removePrefix("/")?.trim().orEmpty()
        require(rawPath.isNotEmpty()) {
            "El código QR no incluye el identificador (NIT) de la empresa."
        }
        val issuerParam = uri.getQueryParameter("issuer")?.trim().orEmpty()
        val (labelIssuer, accountIdentifier) = if (":" in rawPath) {
            val parts = rawPath.split(":", limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            issuerParam.ifEmpty { "BillZen" } to rawPath
        }
        val finalIssuer = issuerParam.ifEmpty { labelIssuer.ifEmpty { "BillZen" } }
        val companyName = uri.getQueryParameter("company")?.trim().orEmpty().ifEmpty { finalIssuer }
        val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: TotpGenerator.DEFAULT_DIGITS
        val period = uri.getQueryParameter("period")?.toIntOrNull() ?: TotpGenerator.DEFAULT_PERIOD_SECONDS
        return TotpAccount(
            nit = accountIdentifier,
            companyName = companyName,
            secret = rawSecret,
            issuer = finalIssuer,
            digits = digits,
            periodSeconds = period
        )
    }
}