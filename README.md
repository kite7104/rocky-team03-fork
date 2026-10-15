# ROCKY – Iteration 1 Demo (MVP)

> 어떤 곡이든, 우리 밴드 악보로.
> 음원을 올리면 피아노·드럼·베이스·기타 밴드 악보로 바꿔주고, 원곡에 없는 악기 파트는 AI가 새로 만들어주는 안드로이드 앱

Team 3 (영양실조) · 김채연, 박재욱, 조수빈, 홍순형

---

## 1. How to run

### 개발·실행 환경
| 항목 | 버전 |
|---|---|
| OS | macOS |
| IDE | Android Studio (버전 기입) |
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Min SDK | API 24 (Android 7.0) |
| 테스트 기기 | Android Emulator (기기명 기입) / 실제 폰 (기종 기입) |

### 실행 방법
1. 이 저장소를 받아요.
   ```bash
   git clone <저장소 주소>
   cd <저장소 폴더>
   git checkout iteration-1-demo
   ```
2. Android Studio → **Open** → 이 폴더를 선택해요.
3. Gradle Sync가 끝날 때까지 기다려요.
4. 에뮬레이터 또는 USB 디버깅을 켠 안드로이드 폰을 선택하고 **▶ Run**을 눌러요.

---

## 2. What this demo demonstrates

### 구현한 화면 (사용자 흐름)
| # | 화면 | 기능 |
|---|---|---|
| 0 | 로그인 · 회원가입 · 내 정보 | 이메일 회원가입, 로그인, 로그아웃 (아래 "회원 기능" 참고) |
| 1 | 홈 | NEW(새 악보 만들기), STORAGE(보관함) |
| 2 | 악기 선택 | "당신을 위한 악보를 만드세요" – 피아노/드럼/베이스/기타 복수 선택 |
| 3 | 음원 업로드 | 기기에서 음원 파일 선택 또는 데모 곡 선택 |
| 4 | 처리 중 | 채보 중 → 박자 맞추는 중 → 밴드 편곡 중 단계 표시 |
| 5 | 결과 | 밴드 전체 또는 악기별 악보 보기·재생, AI 생성 악기 표시(✨), 저장 |
| 6 | 내보내기 | 악보 PDF·MIDI 파일 공유 (카카오톡, 메일, 드라이브 등) |

### 시연 범위 (중요)
- **화면 흐름과 저장·재생·공유 기능은 실제로 동작**합니다.
- 결과 화면에서 악기 버튼을 누르면 해당 악기의 악보와 음원으로 전환됩니다. 같은 버튼을 다시 누르면 밴드 전체 보기·재생으로 돌아갑니다.
- **AI 처리(채보·박자 정렬·편곡)는 아직 앱과 연결되지 않았습니다.** 어떤 파일을 업로드해도 `app/src/main/assets/demo/`에 미리 넣어 둔 결과가 표시됩니다.
- 미리 넣어 둔 결과 파일은 아래 모델들을 실제로 실행해서 만든 결과입니다. (사용한 곡·모델 기입)

### 이번 Iteration에서 검증한 것 (Technical feasibility)
| 검증 항목 | 결과 |
|---|---|
| MuScriptor로 음원 → 악기별 MIDI 채보 | (결과 기입) |
| MIDI-GPT로 없는 악기 파트 생성 (BabySlakh 20곡 × 4악기) | 80건 중 78건 생성 성공. 화성·조성은 원곡과 대체로 맞지만 음 밀도가 원곡보다 낮음 |
| 채보 MIDI를 악보로 볼 때의 문제 | 채보 결과가 실제 박자와 정렬되지 않아(기본 120 BPM) 셋잇단·붙임줄이 많은 악보가 됨 → 박자 추적(beat_this) 단계 필요 확인 |
| 생성 모델 후보 | REMI-z(밴드 편곡) 적용 검토 중 |

### 다음 Iteration 계획
- 음원 업로드 → 채보 → 박자 정렬 → 밴드 편곡을 서버에서 실제로 실행하고 앱과 연결
- 서버에서 생성한 악기별 악보·음원을 앱에 전달

---

## 3. Demo video
- 영상 링크: [ROCKY Demo video (Google Drive)](https://drive.google.com/file/d/16sLdD9x2Bg739iO0UaESiY7LWRzARTw-/view?usp=drive_link)

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

현재 악기별 악보와 음원은 데모 `band.mid`에서 각 파트를 분리해 MuseScore로 내보낸 파일입니다.

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
