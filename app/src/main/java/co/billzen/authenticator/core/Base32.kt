package co.billzen.authenticator.core

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(encoded: String): ByteArray {
        val clean = encoded.trim().uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        return ByteArray(0)
    }
}