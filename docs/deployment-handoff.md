# AlignFit 홈서버 배포 인수인계

작성: 2026-11-01, 기준 커밋: `integration` 브랜치 HEAD (push 전).
**상태: 2026-11-01 운영 적용 완료. 최종 결과·운영·롤백은 10장.** 0~8장은 실행 기록으로 남긴다.
이 문서는 실제 서버 작업을 하는 사람이 처음부터 끝까지 따라 할 수 있도록 쓴다.
각 단계는 **확인 → 변경 → 검증 → (문제 시) 롤백** 순서다.

표기:
- ✅ 코드/로컬에서 이미 검증됨
- 🔍 서버에서 직접 확인해야 함 (아직 아무도 확인 안 함)
- ⚠️ 미검증 (환경이 없어 못 함)

## 0. 목표 구조

```text
현재:  휴대폰 → Caddy(192.168.0.11, HTTPS) → 192.168.0.60:30081 (NodePort, 수동 생성 리소스, 사진 분석만)
목표:  휴대폰 → Caddy(192.168.0.11, HTTPS) → 192.168.0.60:30080 (Traefik) → Gateway default/web-gateway
                                            → HTTPRoute alignfit/alignfit → Service alignfit/alignfit-api → Pod
```

전환 원칙: **새 스택을 기존 스택 옆에 띄운다.** 새 리소스 이름은 `alignfit-api`(Deployment/Service),
`alignfit`(HTTPRoute)이고 라벨은 `app.kubernetes.io/*`만 쓴다. 기존 30081 리소스 이름을 몰라도
충돌하지 않게 일부러 바꾼 것이다. Caddy upstream 한 줄을 바꾸기 전까지 사용자 트래픽은 기존 스택으로 간다.

## 1. 이미 검증된 사항 (코드/로컬)

| 항목 | 결과 |
|---|---|
| ✅ 백엔드 pytest | 13/13 통과 (사진 실데이터, 영상 실데이터, 잘못된 입력 415/413/400, 포즈 없음, 모델 실패 503, 해시 불일치) |
| ✅ 모델 무결성 | 커밋된 blob의 SHA256 = `backend/models/SHA256SUMS`, Linux(WSL) `sha256sum -c` OK. MLP 가중치(`mobi_mlp_*.pt`)는 라이선스 문제로 저장소·이미지에서 제외(9장) |
| ✅ 영상 추론 동일성 | 38초 영상: 원본 intelligent 구현과 완전히 같은 결과 (570프레임, incorrect 0.565, E01 0.638) |
| ✅ 사진 API 회귀 없음 | 같은 사진을 새 백엔드와 **현재 운영 서버**에 보내 응답 필드 20개가 전부 동일 |
| ✅ 이미지 내용 | Dockerfile이 COPY하는 것(`app/ models/ data/`)만 git archive로 뽑아 그 폴더에서 서버 기동 → `/health` `video_model_loaded:true`, 두 API 정상 (저장소 다른 경로 의존 없음) |
| ✅ Linux 의존성 해석 | py3.11 manylinux: `torch 2.3.1+cpu`, CUDA 패키지 0개, OpenCV 1개 |
| ✅ 메모리 | 기동 ~300MB, 영상+12MP 사진 처리 후 ~350MB (Windows 측정) → requests 512Mi / limits 1536Mi |
| ✅ YAML | k3s/*, deploy/*, workflow 파싱 OK, workflow smoke 스크립트 bash/python 문법 OK |
| ✅ GHCR/저장소 공개 | 저장소 public, `ghcr.io/lkh599810/alignfit-backend` 익명 pull 가능 → `ghcr-pull` Secret 불필요 |
| ✅ Android 빌드 | prodDebug / localDebug / prodRelease(unsigned) 빌드 성공, prod APK에 운영 URL만 존재 |
| ✅ Android 에뮬레이터 E2E | prod 앱 → **운영 서버** 사진 분석 성공(1080p, 24MP 18MB 사진: 업로드 340KB로 축소, 크래시 없음) / local 앱 → 로컬 백엔드 영상 평가 네이티브 결과 화면 / 서버 다운·404 오류 문구 / prod·local 앱 동시 설치 |
| ⚠️ Docker 이미지 실제 빌드·컨테이너 실행 | 이 PC에 Docker 없음. GitHub Actions `image` job이 빌드 + smoke test를 수행하므로 **첫 CI 결과로 검증** |
| ⚠️ 홈서버 CPU 처리 시간 | 데스크톱: 8초 영상 8.7초, 38초 1080p 영상 68초. 서버는 미측정 |
| ⚠️ 실기기(휴대폰) | 에뮬레이터만 테스트 |

## 2. 서버에서 먼저 확인할 것 (변경 없음, 읽기만)

k3s 노드(192.168.0.60)에서:

```bash
# 2-1 기존 AlignFit 리소스: 이름/selector/이미지 기록 (롤백 기준)
kubectl -n alignfit get deploy,svc,pods,httproute -o wide
kubectl -n alignfit get deploy -o jsonpath='{range .items[*]}{.metadata.name}{"  "}{.spec.selector.matchLabels}{"  "}{..image}{"\n"}{end}'
kubectl -n alignfit get svc -o jsonpath='{range .items[*]}{.metadata.name}{"  "}{.spec.type}{"  "}{.spec.selector}{"  "}{.spec.ports[*].nodePort}{"\n"}{end}'
kubectl -n alignfit get deploy,svc -o yaml > ~/alignfit-legacy-backup-$(date +%F).yaml   # 백업 파일

# 2-2 이름 충돌 없어야 함: alignfit-api / HTTPRoute alignfit 이 "없음"이어야 정상
kubectl -n alignfit get deploy/alignfit-api svc/alignfit-api httproute/alignfit 2>&1 | grep -v NotFound

# 2-3 기존 Service selector가 새 Pod 라벨(app.kubernetes.io/name=alignfit-api)을 잡지 않는지
#     (2-1 출력의 selector에 app.kubernetes.io/name: alignfit-api 가 없으면 OK)

# 2-4 Gateway API / Traefik
kubectl get crd | grep gateway.networking.k8s.io
kubectl get gatewayclass
kubectl -n default get gateway web-gateway -o yaml        # listeners[].name/port/protocol/allowedRoutes, status
kubectl get httproute -A                                  # kanglish 등 기존 route
kubectl -n kube-system get svc traefik -o wide            # 80→30080 인지 확인 (web entrypoint)
kubectl -n kube-system get deploy traefik -o jsonpath='{..image}{"\n"}'
kubectl -n kube-system get helmchartconfig traefik -o yaml 2>/dev/null   # k3s 번들 Traefik 설정

# 2-5 Gateway 소유자: Helm/Argo가 관리하면 kubectl patch가 되돌려진다
kubectl -n default get gateway web-gateway -o jsonpath='{.metadata.labels}{"\n"}{.metadata.annotations}{"\n"}'

# 2-6 Argo CD
kubectl -n argocd get applications
kubectl -n argocd get application kanglish -o jsonpath='{.spec.source}{"\n"}' 2>/dev/null

# 2-7 노드 여유 (새 Pod ~0.5GB + 이미지 ~1.5GB)
kubectl top node 2>/dev/null; df -h /var/lib/rancher
```

Caddy 서버(192.168.0.11)에서 (읽기만):

```bash
grep -n -A12 'alignfit-api.leekanghyun.co.kr' /etc/caddy/Caddyfile   # 현재 upstream 30081, 옵션 기록
grep -n -A12 'kanglish' /etc/caddy/Caddyfile                         # 30080으로 가는 기존 예시
```

판단 기준:
- 2-2에서 무언가 나오면 이름이 겹친 것 → 진행 중단, `k3s/*.yaml` 이름 변경 후 재시도.
- 2-4 listener `allowedRoutes.namespaces.from`이 `Same`(또는 생략=Same)이면 **4단계 필요**.

## 3. GitHub: push → main → CI → GHCR

`C:\Alignfit_integration\AlignFit-integration`에서 (사용자 PC):

```bash
git status                       # 깨끗한지
git log --oneline -8
git push -u origin integration   # 백업용. workflow는 main/PR 대상이라 실행 안 됨
git push origin integration:main # main 생성 → backend-image workflow 실행
```

기본 브랜치 변경 (현재 `feature/backend-mvp`):

```bash
gh auth login                                        # 미로그인 상태였음
gh repo edit lkh599810/AlignFit --default-branch main
# 또는 GitHub → Settings → General → Default branch → main
```

workflow가 안 돌았으면(새 브랜치 push 시 paths 필터 판정 때문일 수 있음):

```bash
gh workflow run backend-image.yml --ref main
```

CI 확인:

```bash
gh run list -R lkh599810/AlignFit -L 5
gh run watch -R lkh599810/AlignFit            # test → image(빌드+smoke) → manifest 순서
```

- `image` job의 "Smoke test the container" 성공 = ⚠️였던 Docker 빌드/컨테이너/모델 로드/두 API 검증 완료.
- `manifest` job이 main에 `chore(k3s): deploy backend <sha7> [skip ci]` 커밋을 push한다.
  403이면 Settings → Actions → General → Workflow permissions를 "Read and write"로.
- 로컬 반영: `git pull origin main` (integration 브랜치에서는 `git fetch && git log origin/main -1`).

GHCR 확인 (어디서든):

```bash
git pull origin main                                          # manifest 커밋 받기
grep -o 'alignfit-backend:[0-9a-f]\{40\}' k3s/backend.yaml   # 배포될 이미지 태그(=빌드한 커밋 SHA)
TOKEN=$(curl -s "https://ghcr.io/token?scope=repository:lkh599810/alignfit-backend:pull" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.v2+json' \
  https://ghcr.io/v2/lkh599810/alignfit-backend/manifests/<40자리 SHA>     # 200이면 OK
```

선택: k3s 노드에서 미리 pull (첫 롤아웃 시간 단축): `sudo k3s crictl pull ghcr.io/lkh599810/alignfit-backend:<SHA>`

롤백(이 단계): 운영 영향 없음. main을 지우지 말고 그냥 둔다. 기존 `mobile-submission` 브랜치/`:mobile-submission` 이미지는 그대로다.

## 4. Gateway allowedRoutes (HTTPRoute가 alignfit namespace에 있으므로)

Gateway `default/web-gateway`의 listener `web`이 `alignfit` namespace route를 받아야 한다.
**`from: Same` → `Selector`로 바꿀 때 `default`도 꼭 포함**(빠지면 Kanglish route가 끊긴다).

확인한 listener 순서로 index를 정한다(아래는 `web`이 0번일 때):

```bash
kubectl -n default get gateway web-gateway -o jsonpath='{range .spec.listeners[*]}{.name}{"\n"}{end}'
kubectl -n default get gateway web-gateway -o yaml > ~/web-gateway-backup-$(date +%F).yaml

kubectl -n default patch gateway web-gateway --type=json -p '[
  {"op":"add","path":"/spec/listeners/0/allowedRoutes","value":{
    "namespaces":{"from":"Selector","selector":{"matchExpressions":[
      {"key":"kubernetes.io/metadata.name","operator":"In","values":["default","alignfit"]}]}}}}]'
```

- Gateway가 Git/Argo/Helm으로 관리되면(2-5) patch 대신 **그 소스**에 같은 내용을 넣는다
  (k3s HelmChartConfig로 만든 Gateway면 Traefik chart의 `gateway.listeners.web.namespacePolicy` 값 — chart 버전별 형식 확인).
- 검증: `kubectl get httproute -A` 후 kanglish route가 계속 `Accepted`,
  `curl -s -o /dev/null -w '%{http_code}\n' https://kanglish.leekanghyun.co.kr/` 정상.
- 롤백: `kubectl apply -f ~/web-gateway-backup-<날짜>.yaml` (또는 `allowedRoutes` 항목 remove patch).

대안(공용 Gateway를 건드리기 싫을 때): HTTPRoute를 `default` namespace에 두고
`alignfit` namespace에 ReferenceGrant(HTTPRoute@default → Service@alignfit)를 만드는 방식.
Traefik 버전의 ReferenceGrant 지원 확인 필요 → 지금 파일에는 넣지 않았다.

## 5. Argo CD 최초 연결 (새 스택 생성, 트래픽 영향 없음)

전제: 3단계 manifest 커밋으로 `k3s/backend.yaml` 이미지 태그가 `0000…`이 아닌 실제 SHA.
(placeholder 상태로 sync하면 ImagePullBackOff — 운영 영향은 없지만 의미 없음)

```bash
# 저장소 public → Argo repo 자격증명 불필요
kubectl apply -n argocd -f deploy/argocd/alignfit.yaml      # Application 생성 (자동 sync 꺼짐)
kubectl -n argocd get application alignfit -o jsonpath='{.status.sync.status} {.status.health.status}{"\n"}'
# Argo UI에서 diff 확인: Deployment/Service alignfit-api, HTTPRoute alignfit 3개만 "생성"이어야 함
argocd app sync alignfit        # CLI 있으면. 없으면 UI Sync, 또는:
kubectl -n argocd patch application alignfit --type merge -p '{"operation":{"sync":{}}}'
```

검증:

```bash
kubectl -n alignfit rollout status deploy/alignfit-api --timeout=300s
kubectl -n alignfit get pods -l app.kubernetes.io/name=alignfit-api
kubectl -n alignfit logs deploy/alignfit-api | grep -i -E 'error|unavailable' || echo "no model errors"
kubectl -n alignfit get httproute alignfit -o jsonpath='{range .status.parents[*].conditions[*]}{.type}={.status} {.reason}{"\n"}{end}'
#   Accepted=True, ResolvedRefs=True 이어야 함 (Accepted=False NotAllowedByListeners → 4단계 미완)

# 기존 경로는 그대로인지
curl -s -o /dev/null -w 'legacy 30081 docs: %{http_code}\n' http://192.168.0.60:30081/docs
```

Traefik 경유 직접 테스트 (Caddy 변경 전, LAN에서):

```bash
H='Host: alignfit-api.leekanghyun.co.kr'
curl -s -H "$H" http://192.168.0.60:30080/health
#   {"status":"ok","video_model_loaded":false}   (가중치 미포함 → 정상, 9장)
curl -s -H "$H" -F 'image=@posture.jpg;type=image/jpeg' http://192.168.0.60:30080/analyze/image | head -c 300; echo
curl -s -H "$H" -F 'video=@exercise.mp4;type=video/mp4' -m 300 -w '\n%{http_code} %{time_total}s\n' \
  http://192.168.0.60:30080/predict | tail -c 400
kubectl top pod -n alignfit 2>/dev/null     # 메모리 실측 (limits 1536Mi 대비)
```

롤백(이 단계): 운영 트래픽은 아직 30081 → 영향 없음. 새 스택 제거:
`kubectl -n argocd delete application alignfit` 후
`kubectl -n alignfit delete deploy/alignfit-api svc/alignfit-api httproute/alignfit`.

## 6. Caddy 전환 30081 → 30080 (사용자 트래픽 이동)

```bash
sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak-$(date +%F-%H%M)
# alignfit-api 블록의 upstream만 변경: 192.168.0.60:30081 → 192.168.0.60:30080
# Caddy는 Host 헤더를 기본 유지하므로 HTTPRoute hostname 매칭이 된다.
# 영상(최대 100MB, 처리 ~1분)용 권장 옵션은 deploy/caddy/alignfit.Caddyfile.example 참고.
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

검증 (외부 네트워크/휴대폰 LTE에서도):

```bash
curl -s https://alignfit-api.leekanghyun.co.kr/health
#   새 스택: {"status":"ok","video_model_loaded":false}   (가중치 미포함 → 정상)
#   기존 스택이면 404 (구버전엔 /health 없음) → 전환 안 된 것
curl -s https://alignfit-api.leekanghyun.co.kr/openapi.json | python3 -c 'import sys,json;print(list(json.load(sys.stdin)["paths"]))'
#   ['/health', '/analyze/image', '/predict', '/demo']
```

앱 확인: prodDebug APK 설치 → 사진 분석 → 추천 운동. 재활운동 평가(영상)는 가중치 마운트 전까지 503 안내가 정상.

롤백: `sudo cp /etc/caddy/Caddyfile.bak-<시각> /etc/caddy/Caddyfile && sudo systemctl reload caddy`
→ 즉시 기존 30081 스택으로 복귀(기존 리소스를 지우지 않았으므로).

### Traefik 타임아웃 (영상 업로드가 느릴 때만)

Traefik v3 entrypoint `readTimeout` 기본값은 60s로 알려져 있다(요청 본문을 다 받는 시간).
느린 모바일 회선에서 큰 영상 업로드가 60s를 넘으면 끊길 수 있다. 증상이 있으면
k3s의 `HelmChartConfig traefik`(2-4에서 본 기존 값과 **병합**)에:

```yaml
spec:
  valuesContent: |-
    ports:
      web:
        transport:
          respondingTimeouts:
            readTimeout: 300s
```

이 변경은 Kanglish 포함 전체 Traefik 재시작을 유발하므로 별도 시간에 한다. 🔍 Traefik 버전별 기본값 확인.

## 7. 안정화 후 정리 (며칠 운영 후)

```bash
# Argo 자동 sync 켜기: deploy/argocd/alignfit.yaml 의 automated 주석 해제 후
kubectl apply -n argocd -f deploy/argocd/alignfit.yaml

# 기존 수동 리소스 제거 (2-1에서 기록한 이름으로, 백업 파일 있는지 먼저 확인)
ls ~/alignfit-legacy-backup-*.yaml
kubectl -n alignfit delete deploy/<기존이름> svc/<기존이름>
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.0.60:30081/docs   # 이제 연결 실패가 정상
```

## 8. 이후 배포/롤백 (GitOps)

- 배포: `backend/**` 변경을 main에 push → CI가 이미지 push + `k3s/backend.yaml` 태그 커밋 → Argo sync.
  (자동 sync 켜기 전에는 Argo UI에서 Sync)
- 이미지 롤백: `k3s/backend.yaml` 태그를 이전 SHA로 되돌리는 커밋을 main에 push
  (`git log --oneline -- k3s/backend.yaml`로 이전 SHA 확인) → sync. DB 없음(stateless)이라 태그만 되돌리면 된다.
  급할 때: `argocd app rollback alignfit` (자동 sync 켜져 있으면 다시 덮어쓰므로 끄고 사용).
- 전체 비상 복귀(기존 리소스까지 지운 뒤 새 스택이 망가졌을 때):
  `kubectl apply -f deploy/rollback/legacy-nodeport-30081.yaml` (`:mobile-submission` 이미지, 사진 분석만)
  → Caddy upstream 30081 → reload.

## 9. 알려진 제약 / 결정 필요

- 영상 추론은 전역 lock으로 동시 1건. 두 번째 요청은 대기(앱 read timeout 180s).
- 추천 DB 내용·`caution_message`는 영어(연구 DB 그대로), 인용 `verify_citation` TODO.
- 저장소·GHCR 이미지가 **public**이라 MobiPhysio 학습 가중치(`backend/models/mobi_mlp_*.pt`)는
  push 전에 히스토리에서 제거하고 `.gitignore` 처리했다(2026-11-01). 그래서 CI 이미지에는 가중치가 없고
  `/predict`는 503, `/health`는 `video_model_loaded:false`가 정상이다. 영상 기능은 나중에 확장할 때
  가중치를 Pod의 `/app/models/`에 마운트(hostPath 등, SHA256SUMS로 검증)하면 켜진다. 사진 분석은 영향 없음.
  원본 영상(48MB)·UI-PRMD 원본은 이미
  `origin/sleep-auto-001` 히스토리로 공개된 상태(이번 작업에서 tip에서는 추적 해제).
- `prodRelease`는 서명 설정 없음(배포용 APK는 prodDebug 사용 중).

## 10. 최종 배포 결과 (2026-11-01)

운영 값은 사용자가 서버에서 확인해 보고한 것이다. GHCR/CI/HTTPS `/health` 응답은 로컬에서 공개 API로 재확인했다.

### 이전 → 최종

| 항목 | 이전 | 최종 |
|---|---|---|
| 경로 | Caddy → `192.168.0.60:30081` (NodePort, 수동 리소스 `alignfit-backend`) | Caddy → `192.168.0.60:30080` (Traefik) → Gateway `default/web-gateway` → HTTPRoute `alignfit/alignfit` → Service `alignfit-api` |
| 배포 | 수동 | GitHub Actions → GHCR `:<SHA>` → `k3s/backend.yaml` 태그 커밋 → Argo CD `alignfit` (수동 sync) |
| 이미지 | `:mobile-submission` (사진만) | `:d94783560326a0b66dfbaa0ca117912c32dd8b01` (사진 + `/health`, 영상은 가중치 미포함 503) |
| Gateway 소유 | LKH_Portfolio `portfolio` App | `homelab-infra` App (Private 저장소, `Prune=false,Delete=false`), allowedRoutes `default`, `alignfit` |
| `imagePullSecrets: ghcr-pull` | 있음(대비용) | 제거 (GHCR 패키지 public, 익명 pull) |

롤백용으로 **유지**: Deployment `alignfit-backend` + NodePort Service `30081` (Argo CD 미관리).

### Argo CD 관리 범위

- `alignfit` (이 저장소 `main` / `k3s/`): Deployment·Service `alignfit-api`, HTTPRoute `alignfit`. **수동 sync**, Auto Sync 미활성.
- `homelab-infra`: GatewayClass `traefik`, Gateway `default/web-gateway`.
- `portfolio`, `kanglish`: 각자 서비스와 HTTPRoute만.
- `deploy/`(Argo App 정의, Caddy 예시, 롤백 매니페스트)는 어떤 App도 sync하지 않는다.

### 검증 결과

- CI: `Backend image` `workflow_dispatch` @ `d947835` 성공 → GHCR `alignfit-backend:d947835…` manifest 존재(익명 조회 200) → 봇 커밋 `8e64b16`이 태그 갱신.
- Argo CD `alignfit` Synced/Healthy (서버 보고).
- `https://alignfit-api.leekanghyun.co.kr/health` → 200 `{"status":"ok","video_model_loaded":false}` (구 스택엔 `/health` 없음 → 신규 스택 응답).
- Portfolio, Kanglish, AlignFit HTTPS 모두 200 (서버 보고).

### 이후 배포 절차

1. `backend/**` 변경을 `main`에 push (또는 Actions에서 Run workflow).
2. CI: pytest → 이미지 빌드·smoke → GHCR push → `k3s/backend.yaml` 태그 커밋(`[skip ci]`).
3. Argo CD `alignfit` → Diff 확인(이미지 태그만) → **Sync** (수동).
4. 확인: `curl -s https://alignfit-api.leekanghyun.co.kr/health`, 앱 사진 분석.

### 장애 시 롤백

| 상황 | 복구 |
|---|---|
| 새 이미지 문제 | `k3s/backend.yaml` 태그를 이전 SHA로 되돌리는 커밋 → Argo Sync. 급하면 Argo UI History → Rollback. |
| 신규 스택 전체 문제 | Caddy AlignFit upstream `30080` → `30081` (백업 → 수정 → `caddy validate` → `systemctl reload caddy`). 유지 중인 `alignfit-backend`로 즉시 복귀(사진 분석만). |
| 30081 리소스까지 지운 뒤 | `kubectl apply -f deploy/rollback/legacy-nodeport-30081.yaml` → Caddy 30081. |
| Gateway/Route 문제 | homelab-infra `docs/rollback.md` "최종 상태 기준 롤백". |

### 추후 정리할 작업

1. 며칠 안정 운영 후 `alignfit` Auto Sync 활성화 (`deploy/argocd/alignfit.yaml`의 `automated` 주석 해제 → `kubectl apply -n argocd`).
2. 그 후 롤백용 `alignfit-backend` Deployment + NodePort 30081 제거 (7장).
3. 원격 `integration` 브랜치 삭제 (main에 포함됨).
4. 영상 기능을 켤 때: 가중치 라이선스 확인 후 Pod에 `/app/models/mobi_mlp_*.pt` 마운트, Traefik/Caddy 타임아웃 검토.
5. 홈서버 메모리 실측(`kubectl top pod -n alignfit`)으로 requests/limits 재조정.
