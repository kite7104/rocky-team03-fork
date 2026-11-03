// AI-generated with Claude Code, 2026-11-03
package com.team3.rocky.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class EmailVerificationTest {
    private var now = 0L
    private val v = EmailVerification(Random(1)) { now }

    @Test fun codeHasSixDigits() {
        val c = v.send("a@b.com")
        assertEquals(6, c.length)
        assertEquals(true, c.all(Char::isDigit))
    }

    @Test fun rightCodeForSameEmailPasses() {
        val c = v.send("Rocky@SNU.ac.kr")
        assertNull(v.check(" rocky@snu.ac.kr ", c))
    }

    @Test fun wrongCodeOrOtherEmailFails() {
        val c = v.send("a@b.com")
        assertNotNull(v.check("a@b.com", if (c == "000000") "111111" else "000000"))
        assertNotNull(v.check("other@b.com", c))
    }

    @Test fun mustSendFirst() = assertNotNull(v.check("a@b.com", "123456"))

    @Test fun codeExpiresAfterFiveMinutes() {
        val c = v.send("a@b.com")
        now += EmailVerification.VALID_MS + 1
        assertNotNull(v.check("a@b.com", c))
    }
}
