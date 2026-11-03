# 11/4 시연 — 재욱 노트북 (gpu05 · 폰 1)

```
폰1 ─USB─ 재욱 노트북 ─(port-forward)─ gpu05 워크스페이스의 rocky-api ─ GPU(ferrari)
폰2 ─USB─ 채연 노트북 ─(port-forward)─ gpu22 워크스페이스의 rocky-api ─ GPU(ford)
```
- 서버(rocky-api)는 gpu05에 **이미 올라가 있다** (11/3 채연이 올리고 끝까지 시험 완료). 노트북에서는 **연결만** 하면 된다
- 앱 서버 주소는 기본값 `http://localhost:8000` 그대로
- 시연 중 gpu05에 **다른 GPU Pod를 띄우지 말 것** (워크스페이스 GPU 1개 → 앱 곡이 기다림)

## 0. 처음 한 번만 (시연 전에 미리)
| 확인 | 명령 | 안 되면 |
|---|---|---|
| kubectl 로그인(gpu05) | `kubectl auth whoami` | 이미 쓰던 kubeconfig 그대로 |
| python3 | `python3 --version` | 3.8 이상이면 OK |
| adb | `adb version` | 아래 PATH 한 줄. 그래도 없으면 Android Studio → Settings → Android SDK → SDK Tools → **Android SDK Platform-Tools** 설치 |
```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"     # 맥, adb가 안 잡힐 때
git clone -b feature/app-server-v2 https://github.com/kite7104/rocky-team03-fork rocky-app-server
```
- 폰1: **USB 디버깅 켜기** (설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7번 → 개발자 옵션 → USB 디버깅), 맥에 꽂고 "항상 허용"
- 폰1에 앱(team3_ROCKY.apk)과 음원(다운로드/band_songs 12곡)은 채연이 넣어 둠

## 1. 시연 직전 — 터미널 탭 3개

**탭 1 (켜 둠) — 서버 연결**
```bash
kubectl get pod rocky-api                      # STATUS Running 이어야 함
kubectl port-forward pod/rocky-api 8000:8000   # "Forwarding from 127.0.0.1:8000" 이 보이면 그대로 둠
```

**탭 2 (켜 둠) — GPU Pod를 띄우는 agent**
```bash
cd ~/rocky-app-server
python3 server/agent/rocky_agent.py
```
"할 일을 기다려요" 가 보이면 정상. 곡이 들어오면 "Pod 생성 → 끝: Succeeded" 가 찍힌다.
(팀 계정은 권한 부여가 안 돼서 서버가 GPU Pod를 직접 못 만들고, 이 agent가 노트북 로그인으로 대신 만든다)

**탭 3 — 확인과 폰 연결**
```bash
curl localhost:8000/health          # {"ok":true}
adb devices                         # 폰이 "device" 로 한 줄
adb reverse tcp:8000 tcp:8000
```

**앱 확인:** 새 악보 → 밴드 구성 → 음원 업로드 화면 맨 아래 **서버 설정 → 연결 확인 → `연결됨 ✓`**

## 2. 시연 중
- 곡 하나 처리: GPU에서 대략 곡 길이의 1~3배 (긴 곡은 10~20분). 평가자에게는 **보관함의 미리 만든 결과**를 먼저 보여주고, 직접 올리기는 짧은 곡으로
- 진행상황 탭 안내 문구
  | 문구 | 뜻 / 할 일 |
  |---|---|
  | GPU를 다른 작업이 쓰고 있어서 기다리는 중 | gpu05에 다른 GPU Pod가 있음 → `kubectl get pods` 로 확인, 지워 달라고 |
  | GPU 차례를 기다리는 중 | ferrari 노드 GPU를 다른 팀이 씀 → 기다리기 |
  | 노트북 연결(agent)을 기다리는 중 | 탭 2 agent가 꺼짐 → 다시 실행 |
  | 서버에 연결할 수 없어요 | 탭 1 port-forward 끊김 또는 `adb reverse` 풀림 → 다시 |
- 서버가 안 되면: 진행상황 카드의 **데모 결과 보기**, 업로드 화면의 **데모 곡으로 체험하기**
- 케이블이 빠지거나 폰을 재부팅하면 → `adb reverse tcp:8000 tcp:8000` 다시
- 로그인이 만료되면 port-forward/agent가 오류 → `kubectl auth whoami` 로 다시 로그인 후 탭 1·2 다시

## 3. 끝나고
- 탭 1·2는 `Ctrl+C` 로 끄면 된다. **rocky-api 는 GPU를 쓰지 않으니 켜 둬도 된다** (끌 때: `kubectl delete pod rocky-api`)
- 혹시 남은 GPU Pod가 있으면 지우기: `kubectl get pods` 에서 `rocky-midi-…` / `rocky-pdf-…` 가 Running 이면 `kubectl delete pod <이름>`

문제 상황 기록: 앱 화면 문구 + 탭 2 마지막 줄 + `kubectl logs rocky-api --tail 30`
