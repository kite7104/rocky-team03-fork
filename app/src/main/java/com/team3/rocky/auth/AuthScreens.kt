// AI-generated with Claude Code, 2026-10-15
package com.team3.rocky.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team3.rocky.Demo
import com.team3.rocky.Page
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- 로그인 ----------
@Composable
fun LoginScreen(auth: AuthRepository, onLoggedIn: (User) -> Unit, onSignUp: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        focus.clearFocus()
        busy = true
        error = null
        scope.launch {
            when (val r = auth.login(email, password)) {
                is AuthResult.Success -> onLoggedIn(r.user)
                is AuthResult.Failure -> error = r.message
                is AuthResult.Invalid -> error = r.errors.values.first()
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("ROCKY", fontSize = 48.sp, fontWeight = FontWeight.Black, color = cs.primary)
        Text("어떤 곡이든, 우리 밴드 악보로", fontSize = 16.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))

        AuthField(
            value = email, onValueChange = { email = it; error = null }, label = "이메일",
            keyboardType = KeyboardType.Email, tag = "login_email",
            onNext = { focus.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value = password, onValueChange = { password = it; error = null }, label = "비밀번호",
            imeAction = ImeAction.Done, tag = "login_password", onDone = { submit() },
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = cs.error, fontSize = 13.sp, modifier = Modifier.testTag("login_error"))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("login_submit"),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onPrimary)
            else Text("로그인", fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSignUp, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("go_signup"),
        ) { Text("이메일로 회원가입") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Google · Apple 로그인은 준비 중이에요",
            fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------- 회원가입 ----------
@Composable
fun SignUpScreen(auth: AuthRepository, onBack: () -> Unit, onSignedUp: (User) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var form by remember { mutableStateOf(SignUpForm()) }
    var errors by remember { mutableStateOf<Map<SignUpValidator.Field, String>>(emptyMap()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var termsDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 값을 고치면 그 칸의 오류 메시지를 지운다.
    fun edit(field: SignUpValidator.Field?, change: (SignUpForm) -> SignUpForm) {
        form = change(form)
        if (field != null) errors = errors - field
        failure = null
    }

    fun submit() {
        if (busy) return
        focus.clearFocus()
        val local = SignUpValidator.validate(form)
        if (local.isNotEmpty()) { errors = local; return }
        busy = true
        scope.launch {
            when (val r = auth.signUp(form)) {
                is AuthResult.Success -> onSignedUp(r.user)
                is AuthResult.Invalid -> errors = r.errors
                is AuthResult.Failure -> failure = r.message
            }
            busy = false
        }
    }

    Page("회원가입", onBack, bottom = {
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("signup_submit"),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onPrimary)
            else Text("가입하기", fontSize = 17.sp)
        }
    }) {
        Column(Modifier.fillMaxWidth().weight(1f).imePadding().verticalScroll(rememberScrollState())) {
            Text("ROCKY와 함께\n밴드 악보를 만들어요", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Spacer(Modifier.height(24.dp))

            SectionLabel("계정")
            AuthField(
                value = form.email, onValueChange = { v -> edit(SignUpValidator.Field.EMAIL) { it.copy(email = v) } },
                label = "이메일", keyboardType = KeyboardType.Email, tag = "signup_email",
                error = errors[SignUpValidator.Field.EMAIL], hint = "로그인할 때 아이디로 써요",
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(8.dp))
            PasswordField(
                value = form.password, onValueChange = { v -> edit(SignUpValidator.Field.PASSWORD) { it.copy(password = v) } },
                label = "비밀번호", tag = "signup_password", error = errors[SignUpValidator.Field.PASSWORD],
                hint = "영문과 숫자를 포함해 ${SignUpValidator.PASSWORD_MIN}자 이상",
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(8.dp))
            PasswordField(
                value = form.passwordConfirm,
                onValueChange = { v -> edit(SignUpValidator.Field.PASSWORD_CONFIRM) { it.copy(passwordConfirm = v) } },
                label = "비밀번호 확인", tag = "signup_password_confirm",
                error = errors[SignUpValidator.Field.PASSWORD_CONFIRM],
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("프로필")
            AuthField(
                value = form.nickname, onValueChange = { v -> edit(SignUpValidator.Field.NICKNAME) { it.copy(nickname = v) } },
                label = "닉네임", tag = "signup_nickname", error = errors[SignUpValidator.Field.NICKNAME],
                hint = "${SignUpValidator.NICKNAME_MIN}~${SignUpValidator.NICKNAME_MAX}자, 한글·영문·숫자·_",
                imeAction = ImeAction.Done, onDone = { focus.clearFocus() },
            )
            Spacer(Modifier.height(16.dp))
            Text("연주하는 악기 (선택)", fontWeight = FontWeight.Bold)
            Text(
                "새 악보를 만들 때 이 악기들이 미리 선택돼요. 나중에 바꿀 수 있어요.",
                fontSize = 12.sp, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Demo.INSTRUMENTS.forEach { inst ->
                    val on = inst in form.instruments
                    InstrumentChip(inst, on, Modifier.weight(1f)) {
                        edit(null) { it.copy(instruments = if (on) it.instruments - inst else it.instruments + inst) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("약관 동의")
            val all = form.over14 && form.agreeTerms && form.agreePrivacy
            AgreementRow("전체 동의", all, bold = true, tag = "agree_all") { v ->
                edit(SignUpValidator.Field.AGREEMENT) { it.copy(over14 = v, agreeTerms = v, agreePrivacy = v) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = cs.surfaceVariant)
            AgreementRow("(필수) 만 14세 이상이에요", form.over14, tag = "agree_age") { v ->
                edit(SignUpValidator.Field.AGREEMENT) { it.copy(over14 = v) }
            }
            AgreementRow("(필수) 서비스 이용약관", form.agreeTerms, tag = "agree_terms", onView = { termsDialog = TERMS }) { v ->
                edit(SignUpValidator.Field.AGREEMENT) { it.copy(agreeTerms = v) }
            }
            AgreementRow("(필수) 개인정보 수집·이용", form.agreePrivacy, tag = "agree_privacy", onView = { termsDialog = PRIVACY }) { v ->
                edit(SignUpValidator.Field.AGREEMENT) { it.copy(agreePrivacy = v) }
            }
            (errors[SignUpValidator.Field.AGREEMENT] ?: failure)?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.testTag("signup_error"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    termsDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { termsDialog = null },
            confirmButton = { TextButton(onClick = { termsDialog = null }) { Text("닫기") } },
            title = { Text(title) },
            text = { Text(body, fontSize = 14.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
        )
    }
}

// ---------- 내 정보 · 로그아웃 ----------
@Composable
fun AccountScreen(user: User, onBack: () -> Unit, onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    Page("내 정보", onBack, bottom = {
        OutlinedButton(
            onClick = { confirm = true },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("logout"),
        ) { Text("로그아웃") }
    }) {
        Text(user.nickname, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(user.email, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        InfoRow("연주하는 악기", user.instruments.joinToString(" · ") { Demo.kor(it) }.ifEmpty { "선택 안 함" })
        InfoRow("로그인 방법", when (user.provider) {
            AuthProvider.EMAIL -> "이메일"; AuthProvider.GOOGLE -> "Google"; AuthProvider.APPLE -> "Apple"
        })
        InfoRow("가입일", SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(user.createdAt)))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            confirmButton = {
                TextButton(onClick = { confirm = false; onLogout() }, modifier = Modifier.testTag("logout_confirm")) {
                    Text("로그아웃")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
            title = { Text("로그아웃할까요?") },
            text = { Text("저장한 악보는 지워지지 않아요. 다시 로그인하면 볼 수 있어요.") },
        )
    }
}

// ---------- 공통 입력 요소 ----------
@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    error: String? = null,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "숨기기" else "보기", fontSize = 12.sp) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun InstrumentChip(inst: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (on) cs.primary else cs.surface)
            .border(BorderStroke(1.dp, if (on) cs.primary else cs.surfaceVariant), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("instrument_$inst"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Demo.emoji(inst), fontSize = 22.sp)
        Text(
            Demo.kor(inst), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (on) cs.onPrimary else cs.onSurface,
        )
    }
}

@Composable
private fun AgreementRow(
    label: String,
    checked: Boolean,
    tag: String,
    bold: Boolean = false,
    onView: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (onView != null) TextButton(onClick = onView) { Text("보기", fontSize = 13.sp) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(cs.surface).padding(16.dp),
    ) {
        Column {
            Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

// 시연용 요약문. 정식 약관은 서비스 공개 전에 따로 작성해야 한다.
private val TERMS = "서비스 이용약관" to """
    1. ROCKY는 사용자가 올린 음원을 밴드 악보(피아노·드럼·베이스·기타)로 바꾸고, 원곡에 없는 악기 파트를 AI로 만들어 줍니다.
    2. 사용자는 본인이 이용 권리를 가진 음원만 올려야 하며, 만든 악보는 개인 연습과 비상업적 목적으로만 쓸 수 있습니다.
    3. AI가 만든 채보·편곡 결과는 원곡과 다를 수 있으며, ROCKY는 결과의 정확성을 보증하지 않습니다.
    4. 다른 사람의 계정을 쓰거나 서비스를 방해하는 행위는 금지됩니다.
""".trimIndent()

private val PRIVACY = "개인정보 수집·이용" to """
    • 수집 항목: 이메일, 비밀번호(복원할 수 없는 형태로 암호화해 저장), 닉네임, 연주하는 악기(선택), 약관 동의 시각
    • 이용 목적: 회원 식별과 로그인, 악보 보관함 제공, 악보 만들기 기본 설정
    • 보관 기간: 회원 탈퇴 시까지
    • 현재 버전은 회원 정보를 이 기기 안에만 저장하며 외부로 보내지 않습니다.
    • 동의를 거부할 수 있으나, 거부하면 가입할 수 없습니다.
""".trimIndent()
