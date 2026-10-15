// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

/** 회원가입 입력 검사. 안드로이드 API를 쓰지 않아 JVM 단위 테스트로 검사할 수 있다. */
object SignUpValidator {
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    const val NICKNAME_MIN = 2
    const val NICKNAME_MAX = 12

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    private val NICKNAME = Regex("^[가-힣A-Za-z0-9_]+$")

    fun normalizeEmail(email: String) = email.trim().lowercase()

    fun emailError(email: String): String? {
        val e = normalizeEmail(email)
        return when {
            e.isEmpty() -> "이메일을 입력해 주세요"
            e.length > 254 || !EMAIL.matches(e) -> "이메일 형식이 올바르지 않아요"
            else -> null
        }
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "비밀번호를 입력해 주세요"
        password.length < PASSWORD_MIN -> "비밀번호는 ${PASSWORD_MIN}자 이상이어야 해요"
        password.length > PASSWORD_MAX -> "비밀번호는 ${PASSWORD_MAX}자 이하여야 해요"
        password.any { it.isWhitespace() } -> "비밀번호에 공백은 쓸 수 없어요"
        !password.any { it.isLetter() } || !password.any { it.isDigit() } -> "영문과 숫자를 모두 포함해 주세요"
        else -> null
    }

    fun passwordConfirmError(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "비밀번호를 한 번 더 입력해 주세요"
        confirm != password -> "비밀번호가 일치하지 않아요"
        else -> null
    }

    fun nicknameError(nickname: String): String? {
        val n = nickname.trim()
        return when {
            n.isEmpty() -> "닉네임을 입력해 주세요"
            n.length < NICKNAME_MIN || n.length > NICKNAME_MAX -> "닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 정해 주세요"
            !NICKNAME.matches(n) -> "닉네임은 한글, 영문, 숫자, _만 쓸 수 있어요"
            else -> null
        }
    }

    fun agreementError(form: SignUpForm): String? = when {
        !form.over14 -> "만 14세 이상만 가입할 수 있어요"
        !form.agreeTerms || !form.agreePrivacy -> "필수 약관에 동의해 주세요"
        else -> null
    }

    /** 칸별 오류 메시지. 비어 있으면 가입할 수 있다. */
    fun validate(form: SignUpForm): Map<Field, String> = buildMap {
        emailError(form.email)?.let { put(Field.EMAIL, it) }
        passwordError(form.password)?.let { put(Field.PASSWORD, it) }
        passwordConfirmError(form.password, form.passwordConfirm)?.let { put(Field.PASSWORD_CONFIRM, it) }
        nicknameError(form.nickname)?.let { put(Field.NICKNAME, it) }
        agreementError(form)?.let { put(Field.AGREEMENT, it) }
    }

    enum class Field { EMAIL, PASSWORD, PASSWORD_CONFIRM, NICKNAME, AGREEMENT }
}
