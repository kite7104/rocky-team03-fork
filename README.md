# ROCKY — Iteration 2 Working Demo

> 어떤 곡이든, 우리 밴드 악보로.
> 밴드 악보 제작을 위한 안드로이드 앱의 로그인, 데모 곡 선택, 악보·오디오 확인, 저장·공유 흐름을 시연합니다.

Team 3 (영양실조) · 김채연, 박재욱, 조수빈, 홍순형

---

## 1. How to run

### 개발·실행 환경
이 데모는 Windows 11에서 Android Studio로 작업했습니다. Android Studio의 정확한 빌드 번호와 촬영 기기 모델은 기록되지 않았습니다. 프로젝트에 고정된 도구·플랫폼 버전은 다음과 같습니다.

| 항목 | 버전·요구사항 |
|---|---|
| 로컬 OS / IDE | Windows 11 / Android Studio |
| 빌드 | Gradle Wrapper 9.5.0, Android Gradle Plugin 9.3.3 |
| 언어 / UI | Kotlin 2.2.10, Jetpack Compose (Material 3), Java 11 소스 호환성 |
| Android SDK | compile/target SDK 37, min SDK 34 (Android 14) |
| 실행 기기 | API 34 이상 Android Emulator 또는 Android 기기 |

### 실행 방법
1. 이 저장소를 받아요.
   ```bash
   git clone https://github.com/snuhcs-course/swpp-2026-project-team-03.git
   cd swpp-2026-project-team-03
   git switch iteration-2-demo
   ```
2. Android Studio에서 저장소 루트 폴더를 열고 Android SDK 37을 설치하거나 설치 안내에 따라 설정합니다. Gradle Sync를 완료합니다. 로컬 SDK 경로는 각자 `local.properties`에 설정하며 이 파일은 Git에 포함하지 않습니다.
3. API 34 이상 에뮬레이터 또는 USB 디버깅된 기기를 선택해 `app`을 실행합니다. 별도 서버·GPU·외부 API 키는 필요하지 않습니다.
4. 새 설치에서는 이메일 회원가입 후 로그인됩니다. 홈에서 **NEW → 악기 선택 → 데모 곡으로 체험하기 → 악보 만들기 시작**을 눌러 결과 화면을 확인합니다.

---

## 2. What this demo demonstrates

### 구현한 화면과 사용자 흐름
| # | 화면 | 기능 |
|---|---|---|
| 0 | 로그인 · 회원가입 · 내 정보 | 이메일 회원가입, 로그인, 로그아웃, 로그인 상태 유지 (아래 "회원 기능" 참고) |
| 1 | 홈 | NEW(새 악보 만들기), STORAGE(보관함) |
| 2 | 악기 선택 | "당신을 위한 악보를 만드세요" – 피아노/드럼/베이스/기타 복수 선택 |
| 3 | 음원 업로드 | 기기에서 음원 파일 선택 또는 Immigrant Song 데모 곡 선택 |
| 4 | 처리 중 | 채보 중 → 박자 맞추는 중 → 밴드 편곡 중 단계 표시 |
| 5 | 결과 | 밴드 전체 또는 악기별 악보 보기·재생, AI 생성 악기 표시(✨), 저장 |
| 6 | 내보내기 | 악보 PDF·MIDI 파일 공유 (카카오톡, 메일, 드라이브 등) |

### Iteration 2의 주요 진전
- 기기 내부 SQLite에 저장하는 이메일 회원가입·로그인과 로그인 상태 유지, 로그아웃 및 회원별 보관함 분리를 추가했습니다. 비밀번호는 원문 대신 salt가 포함된 PBKDF2-HMAC-SHA256 해시로 저장합니다.
- 이전 Fireflies 데모를 Led Zeppelin의 **Immigrant Song** 데모로 교체했습니다. 새 MIDI와 그 곡의 밴드·악기별 악보 이미지, 오디오, 밴드 PDF를 앱 자산으로 묶었습니다.
- Iteration 1의 악기 선택, 결과 악보·악기별 재생, 저장, PDF/MIDI 공유 흐름을 새 로그인 및 데모 곡과 함께 보여줍니다.

### 시연 범위와 한계
- 로그인·회원별 저장·화면 전환·미리 넣은 악보 표시·오디오 재생·파일 공유는 앱에서 동작합니다. 로그인과 사용자 데이터는 **이 기기 안에만** 저장되며 서버와 동기화되지 않습니다.
- 음원 선택은 파일명만 읽습니다. 처리 중 화면은 시뮬레이션이며, 음원 채보·박자 정렬·편곡 모델은 아직 앱에 연결되지 않았습니다. 어떤 파일을 선택해도 `app/src/main/assets/demo/`의 동일한 Immigrant Song 결과가 표시됩니다.
- 데모 MIDI는 별도로 생성한 `muscriptor+midigpt.mid`를 사용했습니다. 앱은 실행 중에 이 MIDI나 악보를 생성하지 않습니다.

---

## 3. Demo video

- [Iteration 2 working demo — 로그인과 Immigrant Song 시연](https://drive.google.com/file/d/1wVii7g0WqUVoUHAEtb1jWcls4gWSXsjV/view?usp=sharing)
- [Iteration 1 demo — 이전 버전 참고 영상](https://drive.google.com/file/d/16sLdD9x2Bg739iO0UaESiY7LWRzARTw-/view?usp=drive_link)

---

## 4. 데모 결과 파일 바꾸는 법
`app/src/main/assets/demo/` 폴더의 파일을 같은 이름으로 바꾸면 앱에 반영됩니다.

| 용도 | 파일 이름 |
|---|---|
| 밴드 악보 | `band-1.png`, `band-2.png`, … (MuseScore → 내보내기 → PNG, 이름 `band`) |
| 전체 재생 | `band.mp3` (또는 `.wav`, `.flac`) |
| 악기별 악보 | `piano-1.png`, `drums-1.png`, `bass-1.png`, `guitar-1.png`, … |
| 악기별 재생 | `piano.mp3`, `drums.mp3`, `bass.mp3`, `guitar.mp3` (또는 지원되는 다른 음원 형식) |
| 내보내기 | `band.pdf`, `band.mid` |

현재 악기별 악보와 음원은 데모 `band.mid`에서 각 파트를 분리해 MuseScore로 내보낸 파일입니다. 사용된 악보 파일은 MuseScore 4.7.5로 저장되었습니다.

데모 곡 제목과 AI 생성 악기 표시는 `app/src/main/java/com/team3/rocky/DemoData.kt`의 `SONG_TITLE`, `AI_GENERATED`에서 바꿉니다.

## 5. 코드 구조
```
app/src/main/java/com/team3/rocky/
├─ MainActivity.kt   앱 시작
├─ RockyApp.kt       화면 이동 (로그인 상태에 따라 로그인 화면 또는 홈에서 시작)
├─ DemoData.kt       데모 설정, 테마, 보관함 저장(회원별), 파일 공유
└─ auth/
   ├─ User.kt             회원 정보, 회원가입 입력값
   ├─ SignUpValidator.kt  회원가입 입력 검사
   ├─ PasswordHasher.kt   비밀번호 해시 (PBKDF2-HMAC-SHA256)
   ├─ UserDatabase.kt     회원 DB (SQLite, rocky_users.db)
   ├─ AuthRepository.kt   회원가입·로그인·로그아웃, 로그인 상태 유지
   └─ AuthScreens.kt      로그인, 회원가입, 내 정보 화면
app/src/main/assets/demo/   미리 넣어 둔 결과 파일
```

## 6. 회원 기능

### 회원가입 때 받는 정보
| 항목 | 필수 | 이유 |
|---|---|---|
| 이메일 | 필수 | 로그인 아이디. 나중에 Google·Apple 계정과 연결할 때도 기준이 됨 |
| 비밀번호 (+ 확인) | 필수 | 영문과 숫자를 포함해 8~64자, 공백 불가 |
| 닉네임 | 필수 | 앱 안에서 보이는 이름. 2~12자, 한글·영문·숫자·_, 다른 회원과 겹칠 수 없음 |
| 연주하는 악기 | 선택 | 피아노·드럼·베이스·기타 중 복수 선택. 새 악보를 만들 때 미리 선택됨 |
| 만 14세 이상 확인 | 필수 | 14세 미만은 개인정보 수집에 법정대리인 동의가 필요해서 지금은 가입을 막음 |
| 이용약관 · 개인정보 수집 동의 | 필수 | 동의한 시각을 저장함. 약관 문구는 시연용 요약이라 공개 전에 정식으로 써야 함 |

실명, 전화번호, 생년월일은 서비스에 쓰이지 않아 받지 않습니다.

### 동작
- 가입하면 바로 로그인되고 홈으로 갑니다.
- 로그인 상태는 앱을 껐다 켜도 유지됩니다. 홈 오른쪽 위 "내 정보"에서 로그아웃합니다.
- 로그인에 실패하면 이메일이 없는 것인지 비밀번호가 틀린 것인지 구분하지 않고 같은 메시지를 보여줍니다.
- 보관함은 회원마다 따로 저장됩니다. 이 기능을 넣기 전에 저장한 악보는 보이지 않습니다.

### 저장 방식과 한계
- 회원 정보는 **이 기기 안의 SQLite DB**(`rocky_users.db`)에만 저장됩니다. 서버가 아직 없어서, 다른 기기에서는 같은 계정으로 로그인할 수 없습니다. 서버가 생기면 `UserDatabase`를 서버 API 호출로 바꾸면 됩니다.
- 비밀번호는 원문을 저장하지 않고 PBKDF2-HMAC-SHA256(반복 120,000회, 회원마다 다른 salt)으로 해시해 저장합니다.
- Google·Apple 로그인을 붙일 수 있도록 DB에 `auth_provider`, `provider_user_id` 칸을 미리 두었습니다. 지금은 모두 `EMAIL`입니다.
- 비밀번호 찾기, 회원 탈퇴, 정보 수정은 아직 없습니다.

### 테스트
```bash
./gradlew :app:testDebugUnitTest            # 입력 검사·비밀번호 해시 (15개)
./gradlew :app:connectedDebugAndroidTest    # 에뮬레이터 필요: 회원 DB 4개 + 화면 흐름 1개
```
화면 흐름 테스트는 회원가입 → 로그아웃 → 틀린 비밀번호 → 로그인 → 앱 재시작 후 로그인 유지까지 확인합니다. 시작할 때 기기의 회원 DB를 지우므로, 직접 만든 계정이 있는 기기에서는 돌리지 마세요.
