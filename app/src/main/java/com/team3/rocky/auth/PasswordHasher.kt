// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 비밀번호를 PBKDF2-HMAC-SHA256으로 해시한다. 비밀번호 원문은 어디에도 저장하지 않는다.
 * 저장 형식: "pbkdf2-sha256$<반복 횟수>$<salt Base64>$<hash Base64>".
 * 반복 횟수를 함께 저장하므로 나중에 횟수를 올려도 기존 회원은 그대로 로그인된다.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2-sha256"
    const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    /** 느린 연산(기기에서 수백 ms)이므로 메인 스레드가 아닌 곳에서 부른다. */
    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = derive(password, salt, iterations)
        val b64 = Base64.getEncoder()
        return "$PREFIX$$iterations$${b64.encodeToString(salt)}$${b64.encodeToString(key)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return false
        // 비교 시간이 일치한 바이트 수에 따라 달라지지 않도록 MessageDigest.isEqual로 비교한다.
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
