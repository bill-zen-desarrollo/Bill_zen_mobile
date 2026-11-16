package co.billzen.authenticator.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpGenerator {
    const val DEFAULT_PERIOD_SECONDS = 30
    const val DEFAULT_DIGITS = 6
    private const val HMAC_ALGORITHM = "HmacSHA1"

    fun generateCode(
        base32Secret: String,
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS
    ): String {
        val secretBytes = Base32.decode(base32Secret)
        require(secretBytes.isNotEmpty()) { "La Base32 no puede estar vacía." }
        return generateFromSecretBytes(
            secretBytes = secretBytes,
            timestampSeconds = timestampMillis / 1000L,
            periodSeconds = periodSeconds,
            digits = digits
        )
    }

    fun generateFromSecretBytes(
        secretBytes: ByteArray,
        timestampSeconds: Long,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS
    ): String {

        val counter = timestampSeconds / periodSeconds
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretBytes, HMAC_ALGORITHM))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)


        val modulo = 10.0.pow(digits).toInt()
        val otp = binary % modulo
        return otp.toString().padStart(digits, '0')
    }

    fun secondsRemaining(
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS
    ): Int {
        val elapsedInWindow = ((timestampMillis / 1000L) % periodSeconds).toInt()
        return periodSeconds - elapsedInWindow
    }

    fun progressRemaining(
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS
    ): Float {
        val totalWindowMillis = periodSeconds * 1000f
        val elapsedMillis = (timestampMillis % (periodSeconds * 1000L)).toFloat()
        return ((totalWindowMillis - elapsedMillis) / totalWindowMillis).coerceIn(0f, 1f)
    }
}