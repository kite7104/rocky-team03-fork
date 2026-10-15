// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    // 테스트 속도를 위해 반복 횟수를 줄인다. 저장된 값에 횟수가 들어 있어 검증은 그대로 된다.
    private fun hash(p: String) = PasswordHasher.hash(p, iterations = 1_000)

    @Test fun verifiesCorrectPassword() = assertTrue(PasswordHasher.verify("band2026", hash("band2026")))

    @Test fun rejectsWrongPassword() = assertFalse(PasswordHasher.verify("band2027", hash("band2026")))

    @Test fun doesNotStorePlainText() = assertFalse(hash("band2026").contains("band2026"))

    @Test fun samePasswordGetsDifferentSalt() = assertNotEquals(hash("band2026"), hash("band2026"))

    @Test fun defaultIterationsRoundTrip() = assertTrue(PasswordHasher.verify("band2026", PasswordHasher.hash("band2026")))

    @Test fun rejectsMalformedStoredValues() {
        listOf("", "plain", "pbkdf2-sha256\$x\$aa\$bb", "md5\$1000\$aa\$bb", "pbkdf2-sha256\$1000\$!!\$!!").forEach {
            assertFalse(it, PasswordHasher.verify("band2026", it))
        }
    }
}
