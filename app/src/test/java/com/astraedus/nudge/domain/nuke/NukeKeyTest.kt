package com.astraedus.nudge.domain.nuke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * L1: the key's format, its hash, and its verification. The only secret in Nuke Mode is the key, so
 * the properties that matter are "the payload is never what is stored", "the right key matches and
 * nothing else does", and "the token's randomness comes from the source we hand it".
 */
class NukeKeyTest {

    @Test
    fun `a generated token is the prefix plus 32 bytes of base64url with no padding`() {
        val token = NukeKey.generateToken()
        assertTrue(token.startsWith(NukeKey.TOKEN_PREFIX))
        val body = token.removePrefix(NukeKey.TOKEN_PREFIX)
        // 32 bytes -> ceil(32 * 4 / 3) = 43 base64 chars without padding.
        assertEquals(43, body.length)
        assertTrue("base64url alphabet only, no padding: $body", body.matches(Regex("[A-Za-z0-9_-]+")))
        assertEquals(NukeKey.TOKEN_RANDOM_BYTES, Base64.getUrlDecoder().decode(body).size)
    }

    @Test
    fun `the token's bytes come from the injected random source, exactly`() {
        val token = NukeKey.generateToken { bytes -> bytes.indices.forEach { bytes[it] = it.toByte() } }
        val expected = ByteArray(32) { it.toByte() }
        assertEquals(
            NukeKey.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(expected),
            token
        )
    }

    @Test
    fun `the default source is random - two tokens never collide`() {
        val tokens = (1..50).map { NukeKey.generateToken() }.toSet()
        assertEquals(50, tokens.size)
    }

    @Test
    fun `hash is lowercase hex SHA-256 of the payload`() {
        // FIPS 180-2 test vector for "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            NukeKey.hashOrNull("abc")
        )
    }

    @Test
    fun `the stored hash never contains the payload`() {
        val token = NukeKey.generateToken()
        val hash = NukeKey.hashOrNull(token)!!
        assertFalse(hash.contains(token.removePrefix(NukeKey.TOKEN_PREFIX)))
        assertFalse(hash.contains(NukeKey.TOKEN_PREFIX))
        assertEquals(64, hash.length)
    }

    @Test
    fun `surrounding whitespace is not part of the key`() {
        assertEquals(NukeKey.hashOrNull("4006381333931"), NukeKey.hashOrNull("  4006381333931\n"))
        assertTrue(NukeKey.matches("4006381333931\r\n", NukeKey.hashOrNull("4006381333931")))
    }

    @Test
    fun `internal characters are the key's identity`() {
        assertNotEquals(NukeKey.hashOrNull("40063 81333931"), NukeKey.hashOrNull("4006381333931"))
        assertNotEquals(NukeKey.hashOrNull("ABC"), NukeKey.hashOrNull("abc"))
    }

    @Test
    fun `a blank scan is never a key`() {
        assertNull(NukeKey.hashOrNull(""))
        assertNull(NukeKey.hashOrNull("   \n"))
        assertNull(NukeKey.hashOrNull(null))
    }

    @Test
    fun `an over-long scan is never a key`() {
        val atCap = "x".repeat(NukeKey.MAX_PAYLOAD_LENGTH)
        val overCap = "x".repeat(NukeKey.MAX_PAYLOAD_LENGTH + 1)
        assertTrue(NukeKey.hashOrNull(atCap) != null)
        assertNull(NukeKey.hashOrNull(overCap))
        assertFalse(NukeKey.matches(overCap, NukeKey.hashOrNull(atCap)))
    }

    @Test
    fun `the right key matches`() {
        val token = NukeKey.generateToken()
        assertTrue(NukeKey.matches(token, NukeKey.hashOrNull(token)))
    }

    @Test
    fun `a stored hash in upper case still matches`() {
        val hash = NukeKey.hashOrNull("abc")!!.uppercase()
        assertTrue(NukeKey.matches("abc", hash))
    }

    @Test
    fun `the wrong key does not match`() {
        val paired = NukeKey.hashOrNull(NukeKey.generateToken())
        assertFalse(NukeKey.matches(NukeKey.generateToken(), paired))
        assertFalse(NukeKey.matches("4006381333931", paired))
    }

    @Test
    fun `nothing matches when no key is paired`() {
        assertFalse(NukeKey.matches("abc", null))
        assertFalse(NukeKey.matches("abc", ""))
        assertFalse(NukeKey.matches("", ""))
        assertFalse(NukeKey.matches(null, null))
    }

    @Test
    fun `a blank scan never matches even a paired key`() {
        assertFalse(NukeKey.matches("", NukeKey.hashOrNull("abc")))
        assertFalse(NukeKey.matches(null, NukeKey.hashOrNull("abc")))
    }

    @Test
    fun `generated tokens are recognisable, other codes are not`() {
        assertTrue(NukeKey.isGeneratedToken(NukeKey.generateToken()))
        assertTrue(NukeKey.isGeneratedToken(" " + NukeKey.generateToken()))
        assertFalse(NukeKey.isGeneratedToken("4006381333931"))
        assertFalse(NukeKey.isGeneratedToken(null))
    }
}
