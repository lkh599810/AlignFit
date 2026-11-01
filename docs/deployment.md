# AlignFit 배포 가이드 (GitHub Actions → GHCR → Argo CD → k3s)

Kanglish와 같은 구조다. 2026-11-01 운영 적용 완료.

## 현재 운영 상태 (2026-11-01)

- Caddy(`alignfit-api.leekanghyun.co.kr` HTTPS 종료) → `192.168.0.60:30080`(Traefik) → Gateway `default/web-gateway`
  → HTTPRoute `alignfit/alignfit` → Service/Deployment `alignfit-api`
- 이미지: `ghcr.io/lkh599810/alignfit-backend:<main 커밋 SHA>` (`k3s/backend.yaml`). 사진 분석 정상,
  영상 `/predict`는 가중치 미포함으로 503 (`/health`의 `video_model_loaded=false`가 정상)
- Argo CD Application `alignfit`: Synced/Healthy, **수동 sync** (Auto Sync 미활성)
- 롤백용 유지: 기존 수동 리소스 Deployment `alignfit-backend` + NodePort `30081` (`:mobile-submission`, 사진 분석만)
- 공용 Gateway는 `homelab-infra` 저장소/Application이 관리하며 allowedRoutes에 `alignfit` 허용됨

## 구성 요소

| 파일 | 내용 |
|---|---|
| `backend/Dockerfile` | python:3.11-slim-bookworm, CPU torch, 단일 OpenCV, 비루트(uid 10001), 모델·DB 내장, HEALTHCHECK `/health` |
| `.github/workflows/backend-image.yml` | `main` push 시: pytest → 이미지 빌드 → 컨테이너 smoke test(`/health` false, `/analyze/image` 400, `/predict` 503 = 가중치 미포함 확인) → GHCR `:<commit SHA>` push → `k3s/backend.yaml` 태그 갱신 커밋 |
| `k3s/backend.yaml` | Deployment `alignfit-api` + ClusterIP Service `alignfit-api` (기존 30081 리소스와 이름·라벨이 겹치지 않는 새 스택) |
| `k3s/httproute.yaml` | `default/web-gateway` 리스너 `web`에 붙는 HTTPRoute (host `alignfit-api.leekanghyun.co.kr`) |
| `deploy/argocd/alignfit.yaml` | Argo CD Application (`main`의 `k3s/`, 처음엔 수동 sync) |
| `deploy/caddy/alignfit.Caddyfile.example` | Caddy upstream 30081 → 30080 변경 예시 (본문 110MB, 응답 대기 300s) |
| `deploy/rollback/legacy-nodeport-30081.yaml` | 비상용: `:mobile-submission` 이미지로 30081 스택 재생성 (Argo 미관리) |

이미지 push만으로는 배포되지 않는다. `k3s/backend.yaml`의 태그가 바뀐 커밋을 Argo CD가
동기화할 때 배포된다. CI 루프 방지: GITHUB_TOKEN push는 workflow를 트리거하지 않고,
`k3s/**`는 workflow paths 밖이며, 커밋 메시지에 `[skip ci]`가 붙는다.
CI는 `main`의 `backend/**` 변경 push 또는 수동 실행(`workflow_dispatch`)으로 돈다. 첫 실행은 수동 실행이었다(새 브랜치 생성 push로는 트리거되지 않음).

## 모델 파일 제공 방식

MediaPipe 모델(5.8MB)과 `models/SHA256SUMS`는 Git에 커밋하고 이미지에 포함한다.
학습된 MLP 가중치(`mobi_mlp_*.pt`)는 라이선스 문제로 **저장소와 공개 이미지에서 제외**했다(`.gitignore`).
그래서 운영 `/predict`는 503, `/analyze/image`는 정상이다. 영상 기능을 켤 때는 가중치를 Pod의
`/app/models/`에 마운트하면 기동 시 `SHA256SUMS`로 검증 후 로드된다. CI는 `sha256sum -c --ignore-missing`.

## 배포 절차

실제 서버 작업 순서·확인 명령·롤백은 **[deployment-handoff.md](deployment-handoff.md)** 한 곳에 정리했다.
완료(2026-11-01): push / main 생성 / CI·GHCR → allowedRoutes에 `alignfit` 추가(homelab-infra) →
Argo CD로 새 스택 생성(기존 30081 유지) → Traefik 30080 경유 테스트 → Caddy upstream 전환.
남음: 안정화 후 Auto Sync 활성화 → 기존 30081 리소스 정리 (handoff 문서 10장).

## 리소스

로컬(Windows) 측정: 모델 로드 직후 ~300MB, 영상 1건 + 12MP 사진 1건 후 ~350MB
→ 요청 512Mi, 제한 1536Mi. 홈서버(Linux)에서 첫 배포 후 `kubectl top pod`로 재확인. 영상 추론은 전역 lock으로 한 번에
하나씩 처리한다(동시 요청은 대기). 처리 시간(데스크톱 CPU): 8초 540×960 영상 8.7초, 38초 1080×1920 영상 68초. 홈서버 미측정.
