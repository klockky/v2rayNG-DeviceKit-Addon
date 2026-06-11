package com.v2ray.devicekit

import java.math.BigInteger

/**
 * Self-contained IETF ChaCha20-Poly1305 AEAD (RFC 8439) decryption.
 *
 * Implemented from scratch so it works on every supported API level
 * (javax.crypto's "ChaCha20-Poly1305" is only available from API 28).
 *
 * Only the decrypt path is needed by the happ:// crypt5 link format.
 */
internal object HappChaCha20Poly1305 {

    private val P130_5: BigInteger = BigInteger.TWO.pow(130).subtract(BigInteger.valueOf(5))
    private val MASK_128: BigInteger = BigInteger.TWO.pow(128).subtract(BigInteger.ONE)
    private val CLAMP: BigInteger =
        BigInteger("0ffffffc0ffffffc0ffffffc0fffffff", 16)

    /**
     * Decrypts and authenticates [cipherWithTag] (ciphertext followed by the 16-byte
     * Poly1305 tag) with no associated data. Returns the plaintext, or null if the
     * tag does not verify or the input is malformed.
     */
    fun decrypt(key: ByteArray, nonce: ByteArray, cipherWithTag: ByteArray): ByteArray? {
        if (key.size != 32 || nonce.size != 12) return null
        if (cipherWithTag.size < 16) return null

        val ciphertext = cipherWithTag.copyOfRange(0, cipherWithTag.size - 16)
        val tag = cipherWithTag.copyOfRange(cipherWithTag.size - 16, cipherWithTag.size)

        val otk = poly1305KeyGen(key, nonce)
        val expectedTag = poly1305Mac(buildMacData(ciphertext), otk)
        if (!constantTimeEquals(tag, expectedTag)) return null

        return chacha20(key, 1, nonce, ciphertext)
    }

    // ----- AEAD MAC framing (AAD is always empty for crypt5) -----

    private fun buildMacData(ciphertext: ByteArray): ByteArray {
        val padCt = (16 - (ciphertext.size % 16)) % 16
        val out = ByteArray(ciphertext.size + padCt + 16)
        System.arraycopy(ciphertext, 0, out, 0, ciphertext.size)
        // le64(aad_len = 0) is already zero-filled
        writeLe64(out, ciphertext.size + padCt + 8, ciphertext.size.toLong())
        return out
    }

    private fun writeLe64(dst: ByteArray, offset: Int, value: Long) {
        var v = value
        for (i in 0 until 8) {
            dst[offset + i] = (v and 0xff).toByte()
            v = v ushr 8
        }
    }

    // ----- Poly1305 (RFC 8439 §2.5) -----

    private fun poly1305KeyGen(key: ByteArray, nonce: ByteArray): ByteArray {
        val block = chacha20Block(key, 0, nonce)
        return block.copyOfRange(0, 32)
    }

    private fun poly1305Mac(message: ByteArray, otk: ByteArray): ByteArray {
        val r = leBytesToBigInt(otk, 0, 16).and(CLAMP)
        val s = leBytesToBigInt(otk, 16, 16)
        var acc = BigInteger.ZERO

        var i = 0
        while (i < message.size) {
            val len = minOf(16, message.size - i)
            // n = block bytes (LE) with an extra high "1" bit appended
            val nBytes = ByteArray(len + 1)
            System.arraycopy(message, i, nBytes, 0, len)
            nBytes[len] = 1
            val n = leBytesToBigInt(nBytes, 0, nBytes.size)
            acc = (acc.add(n)).multiply(r).mod(P130_5)
            i += 16
        }

        acc = acc.add(s).and(MASK_128)
        return bigIntToLeBytes(acc, 16)
    }

    // ----- ChaCha20 (RFC 8439 §2.3-2.4) -----

    private fun chacha20(key: ByteArray, counter: Int, nonce: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var blockCounter = counter
        var offset = 0
        while (offset < input.size) {
            val keystream = chacha20Block(key, blockCounter, nonce)
            val len = minOf(64, input.size - offset)
            for (j in 0 until len) {
                out[offset + j] = (input[offset + j].toInt() xor keystream[j].toInt()).toByte()
            }
            offset += 64
            blockCounter++
        }
        return out
    }

    private fun chacha20Block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = readLe32(key, i * 4)
        state[12] = counter
        for (i in 0 until 3) state[13 + i] = readLe32(nonce, i * 4)

        val working = state.copyOf()
        repeat(10) {
            // column rounds
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)
            // diagonal rounds
            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }

        val out = ByteArray(64)
        for (i in 0 until 16) {
            writeLe32(out, i * 4, working[i] + state[i])
        }
        return out
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = rotl(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = rotl(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = rotl(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = rotl(s[b] xor s[c], 7)
    }

    private fun rotl(v: Int, n: Int): Int = (v shl n) or (v ushr (32 - n))

    // ----- helpers -----

    private fun readLe32(src: ByteArray, offset: Int): Int =
        (src[offset].toInt() and 0xff) or
            ((src[offset + 1].toInt() and 0xff) shl 8) or
            ((src[offset + 2].toInt() and 0xff) shl 16) or
            ((src[offset + 3].toInt() and 0xff) shl 24)

    private fun writeLe32(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xff).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xff).toByte()
        dst[offset + 2] = ((value ushr 16) and 0xff).toByte()
        dst[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    private fun leBytesToBigInt(src: ByteArray, offset: Int, len: Int): BigInteger {
        val be = ByteArray(len + 1) // leading zero keeps it positive
        for (i in 0 until len) be[len - i] = src[offset + i]
        return BigInteger(be)
    }

    private fun bigIntToLeBytes(value: BigInteger, len: Int): ByteArray {
        val out = ByteArray(len)
        var v = value
        val mask = BigInteger.valueOf(0xff)
        for (i in 0 until len) {
            out[i] = v.and(mask).toInt().toByte()
            v = v.shiftRight(8)
        }
        return out
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
