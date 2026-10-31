# AlignFit 배포 가이드 (GitHub Actions → GHCR → Argo CD → k3s)

Kanglish와 같은 구조다. 이 문서와 `k3s/`, `deploy/argocd/`, workflow는 **준비만 된 상태**이며
실제 클러스터·Caddy·Argo CD에는 아직 적용하지 않았다.

## 현재 운영 상태 (변경 금지 대상)

- namespace `alignfit`, Backend Pod 1개, Service NodePort `8000:30081`
- Caddy가 `alignfit-api.leekanghyun.co.kr` HTTPS를 종료하고 노드:30081로 프록시
- 실행 중 이미지: `ghcr.io/lkh599810/alignfit-backend:mobile-submission` (사진 분석만, `/predict` 없음)

## 구성 요소

| 파일 | 내용 |
|---|---|
| `backend/Dockerfile` | python:3.11-slim-bookworm, CPU torch, 단일 OpenCV, 비루트(uid 10001), 모델·DB 내장, HEALTHCHECK `/health` |
| `.github/workflows/backend-image.yml` | `main` push 시: pytest → 이미지 빌드 → 컨테이너 smoke test(`/health`, `/analyze/image`, `/predict`) → GHCR `:<commit SHA>` push → `k3s/backend.yaml` 태그 갱신 커밋 |
| `k3s/backend.yaml` | Deployment `alignfit-backend` + Service `alignfit-backend` (전환기 동안 NodePort 30081 유지) |
| `k3s/httproute.yaml` | `default/web-gateway` 리스너 `web`에 붙는 HTTPRoute (host `alignfit-api.leekanghyun.co.kr`) |
| `deploy/argocd/alignfit.yaml` | Argo CD Application (`main`의 `k3s/`, 처음엔 수동 sync) |

이미지 push만으로는 배포되지 않는다. `k3s/backend.yaml`의 태그가 바뀐 커밋을 Argo CD가
동기화할 때 배포된다. CI 루프 방지: GITHUB_TOKEN push는 workflow를 트리거하지 않고,
`k3s/**`는 workflow paths 밖이며, 커밋 메시지에 `[skip ci]`가 붙는다.
`k3s/backend.yaml`의 초기 태그 `0000…`은 placeholder다. 첫 CI 실행이 실제 SHA로 바꾼다.

## 모델 파일 제공 방식

체크포인트(각 ~75KB)와 MediaPipe 모델(5.8MB)은 `backend/models/`에 Git으로 커밋하고
이미지에 그대로 포함한다(볼륨·런타임 다운로드 없음 → 재현 가능, 오프라인 기동).
`models/SHA256SUMS`로 CI(`sha256sum -c`)와 앱 기동 시(`torch.load` 전) 두 번 검증한다.
검증 실패 시 `/predict`만 503, `/analyze/image`는 정상, `/health`의
`video_model_loaded=false`. 모델을 바꿀 때는 파일과 `SHA256SUMS`를 같은 커밋에서 갱신한다.
나중에 모델이 커지면 GitHub Release asset + 빌드 시 다운로드 + 같은 해시 검증으로 옮긴다.

## 최초 배포 절차 (사람이 순서대로)

1. **기존 리소스 확인** — 이름이 다르면 매니페스트를 맞추거나 기존 것을 정리할 계획을 세운다.
   ```bash
   kubectl -n alignfit get deploy,svc,pods -o wide
   kubectl -n alignfit get svc -o jsonpath='{range .items[*]}{.metadata.name} {.spec.type} {.spec.ports[*].nodePort}{"\n"}{end}'
   kubectl get gateway -n default web-gateway -o yaml | grep -A8 allowedRoutes
   ```
   - 기존 Service가 다른 이름으로 30081을 쓰고 있으면, 새 Service(30081)는 포트 충돌로 생성 실패한다
     (운영에는 영향 없음). 이 경우 첫 sync 전에 `k3s/backend.yaml` Service 이름을 기존 이름으로 맞추거나,
     전환 기간 동안 nodePort 줄을 빼고 ClusterIP로 시작한다.
   - 기존 Deployment selector가 다르면 Pod가 두 벌 뜬다. 첫 sync 후 기존 Deployment를 수동 삭제.
2. **GitHub 정리** — `integration` 검증 후 `main` 생성, 기본 브랜치를 `main`으로 변경
   (현재 기본 브랜치는 오래된 `feature/backend-mvp`). main 보호 규칙을 걸면 Actions 봇 push 예외 필요.
3. **첫 CI** — `main` push → Actions 성공 → GHCR에 `alignfit-backend:<sha>` 생성 확인.
   패키지가 private이면 `alignfit` namespace에 `ghcr-pull` Secret 필요:
   `kubectl -n alignfit create secret docker-registry ghcr-pull --docker-server=ghcr.io --docker-username=lkh599810 --docker-password=<read:packages PAT>`
4. **Gateway 허용** — `web-gateway` 리스너 `web`의 `allowedRoutes.namespaces`가 `alignfit`을
   허용하는지 확인(Gateway를 관리하는 저장소에서 변경).
5. **Argo CD** — `kubectl apply -n argocd -f deploy/argocd/alignfit.yaml` → UI에서 diff 확인 후 수동 Sync.
   확인: `kubectl -n alignfit rollout status deploy/alignfit-backend`, `curl https://alignfit-api.leekanghyun.co.kr/health`
   → `{"status":"ok","video_model_loaded":true}`. 이 시점까지 Caddy → NodePort 경로 그대로.
6. 안정화 후 Application의 `automated` 주석 해제.

## NodePort 30081 → Traefik Gateway 전환

1. HTTPRoute가 Accepted인지: `kubectl -n alignfit get httproute alignfit -o yaml` (status.parents[].conditions)
2. Gateway 경유 테스트(Caddy 변경 전): Traefik 진입점으로 Host 헤더를 지정해 호출
   `curl -H 'Host: alignfit-api.leekanghyun.co.kr' http://<node>:<traefik web port>/health`
3. 큰 업로드·긴 처리 허용 확인 — `/predict`는 최대 100MB, CPU에서 ~1분 이상:
   - Caddy: `reverse_proxy`의 `transport http { read_timeout 180s write_timeout 180s }`, `request_body max_size 110MB`
   - Traefik: entryPoint `respondingTimeouts.readTimeout`(기본 60s)을 180s 이상으로
4. Caddy upstream을 노드:30081 → Traefik(Kanglish와 같은 upstream)으로 변경, Host 헤더 유지
5. 앱 사진/영상 분석 E2E 확인 후 `k3s/backend.yaml` Service를 `type: ClusterIP`로 바꾸고 `nodePort` 줄 삭제 → 커밋 → sync

롤백: Caddy upstream을 노드:30081로 되돌린다(전환 완료 전까지 NodePort 유지 이유).
이미지 롤백은 `k3s/backend.yaml` 태그를 이전 SHA로 되돌리는 커밋. DB/스키마 없음(stateless).

## 리소스

로컬(Windows) 측정: 모델 로드 직후 ~300MB, 영상 1건 + 12MP 사진 1건 후 ~350MB
→ 요청 512Mi, 제한 1536Mi. 홈서버(Linux)에서 첫 배포 후 `kubectl top pod`로 재확인. 영상 추론은 전역 lock으로 한 번에
하나씩 처리한다(동시 요청은 대기). 처리 시간(데스크톱 CPU): 8초 540×960 영상 8.7초, 38초 1080×1920 영상 68초. 홈서버 미측정.
