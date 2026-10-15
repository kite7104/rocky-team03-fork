// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

import com.team3.rocky.auth.SignUpValidator.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpValidatorTest {
    private val valid = SignUpForm(
        email = "rocky@snu.ac.kr", password = "band2026", passwordConfirm = "band2026",
        nickname = "락스타", over14 = true, agreeTerms = true, agreePrivacy = true,
    )

    @Test fun validFormHasNoErrors() = assertTrue(SignUpValidator.validate(valid).isEmpty())

    @Test fun instrumentsAreOptional() = assertTrue(SignUpValidator.validate(valid.copy(instruments = emptySet())).isEmpty())

    @Test fun emailIsTrimmedAndLowercased() = assertEquals("rocky@snu.ac.kr", SignUpValidator.normalizeEmail("  Rocky@SNU.ac.kr "))

    @Test fun rejectsMalformedEmails() {
        listOf("", "rocky", "rocky@", "@snu.ac.kr", "rocky@snu", "ro cky@snu.ac.kr").forEach {
            assertNotNull(it, SignUpValidator.emailError(it))
        }
    }

    @Test fun passwordRules() {
        assertNotNull(SignUpValidator.passwordError("abc123"))      // too short
        assertNotNull(SignUpValidator.passwordError("abcdefgh"))    // no digit
        assertNotNull(SignUpValidator.passwordError("12345678"))    // no letter
        assertNotNull(SignUpValidator.passwordError("band 2026"))   // whitespace
        assertNotNull(SignUpValidator.passwordError("a1".repeat(33)))  // 66 chars
        assertNull(SignUpValidator.passwordError("band2026"))
    }

    @Test fun confirmMustMatch() {
        val errors = SignUpValidator.validate(valid.copy(passwordConfirm = "band2027"))
        assertEquals(setOf(Field.PASSWORD_CONFIRM), errors.keys)
    }

    @Test fun nicknameRules() {
        assertNotNull(SignUpValidator.nicknameError("a"))
        assertNotNull(SignUpValidator.nicknameError("가".repeat(13)))
        assertNotNull(SignUpValidator.nicknameError("rock star"))
        assertNotNull(SignUpValidator.nicknameError("rock!"))
        assertNull(SignUpValidator.nicknameError("  Rocky_03 "))
    }

    @Test fun allRequiredAgreementsAreNeeded() {
        listOf(valid.copy(over14 = false), valid.copy(agreeTerms = false), valid.copy(agreePrivacy = false)).forEach {
            assertEquals(setOf(Field.AGREEMENT), SignUpValidator.validate(it).keys)
        }
    }

    @Test fun emptyFormReportsEveryField() =
        assertEquals(Field.entries.toSet(), SignUpValidator.validate(SignUpForm()).keys)
}
