package com.astraedus.nudge.domain.nuke

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The Nuke key: generation, hashing and verification. Pure JVM (`java.security`, `java.util`), no
 * Android.
 *
 * ## What is stored
 * Only [hash] of the payload — lowercase hex SHA-256 — never the payload. A backup, a debug log or a
 * curious look at the preferences file must not hand anyone the code that ends Nuke; the whole
 * product is "the key lives somewhere inconvenient", and a copy on the phone would defeat it.
 *
 * ## Why SHA-256 and not a password hash
 * A password hash (bcrypt/scrypt) exists to slow down guessing a LOW-entropy secret. A generated key
 * is 32 random bytes (256 bits), which no amount of hashing speed makes guessable. A scanned product
 * barcode is low entropy — but its threat model is "a user who has the phone in hand and wants out",
 * and that user can simply walk to the cupboard, or type the emergency code, far faster than they
 * could brute-force a digest. The hash is protecting the payload from being READ, not from being
 * guessed, and SHA-256 is enough for that.
 */
object NukeKey {

    /** Every generated token starts with this, so a Nudge QR is recognisable in a camera roll. */
    const val TOKEN_PREFIX = "nudge-nuke:"

    /** Random bytes in a generated token: 256 bits. */
    const val TOKEN_RANDOM_BYTES = 32

    /**
     * Longest scanned payload accepted as a key. A scan is UNTRUSTED text (any QR in the house, up
     * to ~4KB from the scanner); anything longer is refused rather than hashed, so a hostile or
     * corrupt code cannot become a key nobody can reproduce.
     */
    const val MAX_PAYLOAD_LENGTH = 4096

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    private const val HEX = "0123456789abcdef"

    /**
     * A fresh random token: [TOKEN_PREFIX] + [TOKEN_RANDOM_BYTES] bytes, base64url without padding.
     *
     * @param fillRandom fills the given array with random bytes. Injectable so a test can prove the
     *   bytes really come from the source it hands in (and so the format is testable exactly);
     *   production is [SecureRandom], never `kotlin.random` or `Math.random`.
     */
    fun generateToken(fillRandom: (ByteArray) -> Unit = { secureRandom.nextBytes(it) }): String {
        val bytes = ByteArray(TOKEN_RANDOM_BYTES)
        fillRandom(bytes)
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * The canonical form a payload is hashed and compared in: surrounding whitespace trimmed.
     *
     * Scanners disagree about trailing newlines and padding on some symbologies; a key that stopped
     * matching because a different camera app appended `\n` would lock the user into the 64-character
     * escape for no reason. Internal characters are left alone — a barcode's content IS its identity.
     */
    fun normalize(payload: String): String = payload.trim()

    /**
     * Lowercase hex SHA-256 of [normalize]d [payload], or null for a blank payload (a blank scan is
     * never a key: pairing it would make an empty camera frame the thing that ends Nuke) or one
     * longer than [MAX_PAYLOAD_LENGTH]. The payload itself is never logged or stored anywhere.
     */
    fun hashOrNull(payload: String?): String? {
        val normalized = payload?.let(::normalize)
        if (normalized.isNullOrEmpty() || normalized.length > MAX_PAYLOAD_LENGTH) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        // Hand-rolled rather than `"%02x".format`: String.format consults the default locale, and
        // a stored hash must be the same string on every phone in every language.
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val v = byte.toInt() and 0xff
                append(HEX[v ushr 4]).append(HEX[v and 0x0f])
            }
        }
    }

    /**
     * Does [payload] match the key whose hash is [storedHash]?
     *
     * False for a null/blank payload, a null/blank stored hash (no key paired), and any mismatch.
     * Compared with [MessageDigest.isEqual], constant-time, as every secret comparison in this app
     * should be — it costs nothing here and removes the question entirely.
     */
    fun matches(payload: String?, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val candidate = hashOrNull(payload) ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.US_ASCII),
            storedHash.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }

    /** Whether [payload] looks like a token Nudge generated (display only; never a gate). */
    fun isGeneratedToken(payload: String?): Boolean =
        payload?.let(::normalize)?.startsWith(TOKEN_PREFIX) == true
}
