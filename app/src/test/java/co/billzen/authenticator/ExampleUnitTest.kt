package co.billzen.authenticator

import co.billzen.authenticator.core.Base32
import co.billzen.authenticator.core.TotpGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun base32_decodesStandardVectorCorrectly() {
        val decoded = Base32.decode("MZXW6===")
        assertArrayEquals("foo".toByteArray(Charsets.US_ASCII), decoded)
        assertTrue(Base32.isValid("JBSWY3DPEHPK3PXP"))
    }
    @Test
    fun totp_matchesOfficialRfc6238TestVector() {

        val rfcSecretBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val codeAt59s = TotpGenerator.generateFromSecretBytes(
            secretBytes = rfcSecretBytes,
            timestampSeconds = 59L,
            periodSeconds = 30,
            digits = 6
        )
        assertEquals("287082", codeAt59s)
    }
}