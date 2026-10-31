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
| `k3s/backend.yaml` | Deployment `alignfit-api` + ClusterIP Service `alignfit-api` (기존 30081 리소스와 이름·라벨이 겹치지 않는 새 스택) |
| `k3s/httproute.yaml` | `default/web-gateway` 리스너 `web`에 붙는 HTTPRoute (host `alignfit-api.leekanghyun.co.kr`) |
| `deploy/argocd/alignfit.yaml` | Argo CD Application (`main`의 `k3s/`, 처음엔 수동 sync) |
| `deploy/caddy/alignfit.Caddyfile.example` | Caddy upstream 30081 → 30080 변경 예시 (본문 110MB, 응답 대기 300s) |
| `deploy/rollback/legacy-nodeport-30081.yaml` | 비상용: `:mobile-submission` 이미지로 30081 스택 재생성 (Argo 미관리) |

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

## 배포 절차

실제 서버 작업 순서·확인 명령·롤백은 **[deployment-handoff.md](deployment-handoff.md)** 한 곳에 정리했다.
요약: 서버 상태 확인 → push / main 생성 / CI·GHCR 확인 → Gateway allowedRoutes에 `alignfit` 추가 →
Argo CD로 새 스택 생성(기존 30081 유지) → Traefik 30080 경유 테스트 → Caddy upstream 전환 →
안정화 후 기존 리소스 정리.

## 리소스

로컬(Windows) 측정: 모델 로드 직후 ~300MB, 영상 1건 + 12MP 사진 1건 후 ~350MB
→ 요청 512Mi, 제한 1536Mi. 홈서버(Linux)에서 첫 배포 후 `kubectl top pod`로 재확인. 영상 추론은 전역 lock으로 한 번에
하나씩 처리한다(동시 요청은 대기). 처리 시간(데스크톱 CPU): 8초 540×960 영상 8.7초, 38초 1080×1920 영상 68초. 홈서버 미측정.
