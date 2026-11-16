package co.billzen.authenticator.core

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun decode(encoded: String): ByteArray {
        val clean = encoded
            .trim()
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)

        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (char in clean) {
            val value = ALPHABET.indexOf(char)
            require(value !=-1) { "Caracter base32 invalido: '$char'" }

            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output[index++] = ((buffer shr bitsLeft) and 0xFF).toByte()
            }
        }

        return output
    }

    fun isValid(encoded: String): Boolean{
        return try {
            decode(encoded).isNotEmpty()
        } catch (_: IllegalArgumentException){
            false
        }
    }
}