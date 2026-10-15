// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

/** 로그인 방법. 지금은 EMAIL만 쓰고, GOOGLE·APPLE은 소셜 로그인을 붙일 때 사용한다. */
enum class AuthProvider { EMAIL, GOOGLE, APPLE }

/** 화면에 필요한 회원 정보. 비밀번호 해시는 [UserDatabase] 밖으로 내보내지 않는다. */
data class User(
    val id: Long,
    val email: String,
    val nickname: String,
    /** 연주하는 악기 (piano, drums, bass, guitar). 새 악보를 만들 때 기본으로 선택된다. */
    val instruments: List<String>,
    val provider: AuthProvider,
    val createdAt: Long,
)

/** 회원가입 화면에서 받는 값. */
data class SignUpForm(
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val nickname: String = "",
    val instruments: Set<String> = emptySet(),
    val over14: Boolean = false,
    val agreeTerms: Boolean = false,
    val agreePrivacy: Boolean = false,
)
