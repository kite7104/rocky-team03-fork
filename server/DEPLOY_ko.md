# ROCKY 서버 SGS 배포·시연 안내 (처음 하는 사람용)

앱(APK) → 노트북 → SGS 서버(rocky-api) → GPU Pod(unified.py) → CPU Pod(PDF·MP3) → 앱에 결과.
서버 주소는 앱 기본값 `http://localhost:8000` 을 쓴다 (폰을 노트북에 USB로 연결 + `adb reverse`).

## 0. 준비물 (노트북 한 대에 한 번만)
| 필요한 것 | 확인 명령 | 없으면 |
|---|---|---|
| kubectl, kubelogin | `kubectl version --client` | SGS 매뉴얼(https://sgs-docs.snucse.org)대로 설치 |
| SGS 로그인 (팀 워크스페이스, 계정 gpu05) | `kubectl auth whoami` | https://sgs.snucse.org 워크스페이스 페이지에서 kubeconfig 받아 `~/.kube/config` 로 저장 |
| adb | `adb version` | 아래 한 줄 실행 (Android Studio에 들어 있음) |

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"   # 터미널을 새로 열 때마다 (또는 ~/.zshrc 에 추가)
```
- 계정을 바꿀 때 토큰이 섞이면 `Forbidden` 이 난다 → `rm -rf ~/.kube/cache/oidc-login` 후 다시 로그인

## 1. 코드 받기
```bash
git clone -b feature/app-server-v2 https://github.com/kite7104/rocky-team03-fork rocky-app-server
cd rocky-app-server
```

## 2. 권한 확인
```bash
kubectl auth can-i create pods
kubectl auth can-i create roles
kubectl auth can-i create rolebindings
```
- 셋 다 `yes` → 3번으로
- `roles`/`rolebindings` 가 `no` → 서버 Pod가 GPU Pod를 만들 권한을 줄 수 없음. 담당자(채연)에게 알려서 "노트북에서 서버 실행" 방식으로 바꾼다

## 3. 서버 올리기 (한 번만, 코드가 바뀌면 다시)
```bash
# 파일 옮기는 Pod가 없으면 띄우기 (이미 있으면 AlreadyExists, 괜찮음)
kubectl get pod rocky-files || kubectl apply -f server/k8s/files-pod.yaml
kubectl wait --for=condition=Ready pod/rocky-files --timeout=120s

# 서버 코드를 볼륨(/data/server)으로 복사
export COPYFILE_DISABLE=1
tar -C server -cf - rocky_server requirements.txt | kubectl exec -i rocky-files -- bash -c 'mkdir -p /data/server && tar -C /data/server -xf -'

# 파이프라인이 최신(BS-RoFormer)인지 확인: 1 이상이면 OK
kubectl exec rocky-files -- grep -c bsroformer /data/pipeline/unified.py

# 권한 + 서버 Pod
kubectl apply -f server/k8s/rbac.yaml
kubectl apply -f server/k8s/api-pod.yaml
kubectl logs -f rocky-api      # "Uvicorn running on http://0.0.0.0:8000" 이 보이면 Ctrl+C
```
- 코드를 다시 올릴 때: 위 `tar` 줄 → `kubectl delete pod rocky-api` → `kubectl apply -f server/k8s/api-pod.yaml`

## 4. 폰 연결 (시연 때마다)
터미널 1 (켜 둔다):
```bash
kubectl port-forward pod/rocky-api 8000:8000
```
터미널 2:
```bash
curl localhost:8000/health          # {"ok":true}
adb devices                         # 폰이 device 로 보여야 함 (폰에서 USB 디버깅 허용)
adb reverse tcp:8000 tcp:8000       # 폰 두 대면: adb -s <기기번호> reverse tcp:8000 tcp:8000 를 각각
```
- 폰 두 대를 노트북 한 대에 꽂아도 된다 (`adb devices` 에 두 줄)
- 케이블이 빠지거나 폰을 재부팅하면 `adb reverse` 를 다시. port-forward 가 끊기면 터미널 1 을 다시

## 5. 앱에서 확인
1. 새 악보 → 우리 밴드 구성 → 음원 업로드 화면 맨 아래 **서버 설정: http://localhost:8000** → **연결 확인** → `연결됨 ✓`
2. 짧은 곡(30초~1분)을 골라 → 원곡 구성 → 악보 만들기 시작 → 진행상황 탭
3. 완료되면 카드를 눌러 악보(PDF)·듣기(MP3)·내보내기 확인

## 6. 문제가 생기면
| 증상 | 확인 |
|---|---|
| 앱: "서버에 연결할 수 없어요" | 터미널 1의 port-forward, `adb reverse`, `curl localhost:8000/health` |
| 진행상황이 "GPU를 다른 작업이 쓰고 있어서 기다리는 중" | 팀원이 GPU Pod를 쓰는 중. `kubectl get pods` 로 확인, 팀에 비워 달라고 요청 |
| "GPU 차례를 기다리는 중" 이 오래 | ferrari 노드 GPU를 다른 팀이 쓰는 중 (11/1 저녁 약 40분 대기 사례) |
| 앱: "서버에서 처리하다 멈췄어요 (...)" | `kubectl logs rocky-api` 와 `kubectl exec rocky-files -- cat /data/rocky-unified/app/<작업번호>.log` |
| 시연 중 서버가 안 되면 | 진행상황 카드의 **데모 결과 보기**, 또는 업로드 화면의 **데모 곡으로 체험하기** |

- 곡마다 결과는 `/data/rocky-unified/runs/<작업번호>/` 에 남는다 (분리 음원은 끝나면 지움, 볼륨 여유가 적어서)
- 시연 전에 실제 곡 2~3개를 미리 돌려 두고 각 폰 보관함에 저장해 두면, 평가자가 기다리지 않고 결과를 볼 수 있다
