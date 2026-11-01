# AlignFit

AI-based posture analysis and homecare exercise recommendation app
(Android + FastAPI + MediaPipe + PyTorch).

- **Photo posture analysis** — pick pain areas and body-map regions, upload a
  full-body photo; the backend detects 33 pose landmarks and reports shoulder /
  hip height, head tilt, trunk centerline and foot direction, with an annotated
  image, a consultation guide and homecare exercises.
- **Video movement evaluation** — upload a short exercise video; trained MLPs
  judge movement quality (correct / incorrect) and the exercise type (9 classes),
  then recommend homecare exercises from a SQLite DB.

> AlignFit is not a medical device and does not diagnose. Results are
> educational reference only; consult a professional if pain continues.

Built for a Mobile Programming course (app + photo API) and extended in an
Intelligent Multimedia Systems course (video ML pipeline).

## Repository layout

```text
android/            Kotlin + Jetpack Compose app (Retrofit/OkHttp)
backend/            FastAPI service = everything that ships in the Docker image
  app/              API, photo analysis services, video inference service
  models/           inference checkpoints + MediaPipe model + SHA256SUMS
  data/             recommendation.sqlite (read-only at runtime)
  tests/            API contract tests (pytest)
k3s/                Kubernetes manifests synced by Argo CD (Deployment, Service, HTTPRoute)
deploy/             Argo CD Application, Caddy example, emergency rollback manifest
.github/workflows/  CI: test -> image build + container smoke test -> GHCR -> manifest bump
src/                research / training code (not deployed): datasets, features,
                    baselines, MLP / 1D-CNN training, Optuna tuning, ablation
db/ results/ figures/ report/   research outputs (feature DBs, metrics, plots, report draft)
docs/               deployment guide, research notes, app screenshots
```

Production inference (`backend/app/services/ml_quality_service.py`) is a
self-contained port of the research feature extractor; it never imports `src/`.

## Features and API

| Endpoint | Input | Output |
|---|---|---|
| `POST /analyze/image` | `image` (JPEG/PNG, ≤ 20MB) | alignment metrics, summary, tips, `annotated_image_base64` |
| `POST /predict` | `video` (mp4/mov/…, ≤ 100MB, ≤ 60s) | movement quality, exercise type, recommendations |
| `GET /health` | – | `{"status": "ok", "video_model_loaded": true}` |
| `GET /demo` | – | browser test page for `/predict` |

Details, error codes and response examples: [backend/README.md](backend/README.md).
Production base URL: `https://alignfit-api.leekanghyun.co.kr/` (`/docs` for Swagger).

## Video model results (MobiPhysio, subject-wise held-out test set)

| Task | Majority | Nearest centroid | **MLP (deployed)** |
|---|---|---|---|
| Movement quality (2 classes), macro-F1 | 0.33 | 0.61 | **0.70** |
| Exercise type (9 classes), macro-F1 | 0.03 | 0.74 | **0.88** |

More: `results/mobiphysio_comparison.md`, `report/`.

## Run locally

Backend (Python 3.10/3.11):

```bash
cd backend
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements-dev.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
python -m pytest -q tests
```

Android (JDK 17, Android SDK 35): open `android/` in Android Studio. Build variants:

| Flavor | API URL | applicationId |
|---|---|---|
| `prod` (default) | `https://alignfit-api.leekanghyun.co.kr/` | `com.alignfit.app` |
| `local` | `http://127.0.0.1:8000/` (USB phone: `adb reverse tcp:8000 tcp:8000`) | `com.alignfit.app.local` |

Emulator or LAN backend: `./gradlew assembleLocalDebug -Palignfit.localApiUrl=http://10.0.2.2:8000/`.
Cleartext HTTP is allowed only in the `local` flavor.

## Deployment

GitHub Actions → GHCR → Argo CD → k3s, same structure as the Kanglish project.
See [docs/deployment.md](docs/deployment.md) and the step-by-step server runbook
[docs/deployment-handoff.md](docs/deployment-handoff.md) (NodePort 30081 →
Traefik Gateway/HTTPRoute migration, completed 2026-11-01; final state and rollback in section 10).

## Model and data files

| What | Where | In Git | In image |
|---|---|---|---|
| Pose model (`pose_landmarker_lite.task`) + `SHA256SUMS` | `backend/models/` | yes | yes |
| Trained MLP weights (`mobi_mlp_*.pt`, ~75KB each) | `backend/models/` (local only) | no (licence, gitignored) | no — `/predict` answers 503 until mounted |
| Recommendation DB | `backend/data/recommendation.sqlite` | yes | yes |
| Research checkpoints (`models/*.pt` at repo root) | regenerable via `src/` | no (gitignored) | no |
| Raw third-party datasets (UI-PRMD, MobiPhysio videos) | `data/raw/`, `*.mp4` | no — licence not verified for redistribution | no |

Raw data remains in earlier Git history only; obtain it from the dataset owners
(see `docs/research/` and `report/data_provenance.md`).

## Tech stack

Android: Kotlin, Jetpack Compose, Material 3, Retrofit, OkHttp ·
Backend: Python, FastAPI, MediaPipe (Pose + Tasks PoseLandmarker), OpenCV, NumPy, PyTorch (CPU), SQLite ·
Ops: Docker, GitHub Actions, GHCR, Argo CD, k3s, Traefik Gateway API, Caddy.
