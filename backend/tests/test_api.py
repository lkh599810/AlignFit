"""API smoke/contract tests.  Run from backend/:  python -m pytest -q tests

Real-media cases need sample files that are NOT committed (dataset licence):
  ALIGNFIT_TEST_IMAGE=path/to/person.jpg  ALIGNFIT_TEST_VIDEO=path/to/exercise.mp4
They are skipped when the variables are unset.
"""
import os
import tempfile

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient

from app import main
from app.services import ml_quality_service as ml

TEST_IMAGE = os.environ.get("ALIGNFIT_TEST_IMAGE")
TEST_VIDEO = os.environ.get("ALIGNFIT_TEST_VIDEO")
# Trained MLP weights are not committed (licence); video-model tests need them locally.
WEIGHTS_PRESENT = all(os.path.exists(os.path.join(ml.MODELS_DIR, f))
                      for f in (ml.BINARY_MODEL_FILE, ml.EXERCISE_MODEL_FILE))
needs_weights = pytest.mark.skipif(not WEIGHTS_PRESENT, reason="video model weights not present")


@pytest.fixture(scope="module")
def client():
    with TestClient(main.app) as c:  # context manager runs lifespan (model preload)
        yield c


def _blank_video(seconds=2, fps=10):
    path = os.path.join(tempfile.mkdtemp(), "blank.mp4")
    w = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*"mp4v"), fps, (160, 120))
    for _ in range(seconds * fps):
        w.write(np.zeros((120, 160, 3), np.uint8))
    w.release()
    with open(path, "rb") as f:
        return f.read()


def _post_video(client, data, name="clip.mp4", ctype="video/mp4"):
    return client.post("/predict", files={"video": (name, data, ctype)})


def test_health_reports_model_state(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "video_model_loaded": WEIGHTS_PRESENT}


def test_image_invalid_bytes_400(client):
    r = client.post("/analyze/image", files={"image": ("x.jpg", b"not an image", "image/jpeg")})
    assert r.status_code == 400


def test_image_too_large_413(client, monkeypatch):
    monkeypatch.setattr(main, "MAX_IMAGE_BYTES", 10)
    r = client.post("/analyze/image", files={"image": ("x.jpg", b"0" * 11, "image/jpeg")})
    assert r.status_code == 413


@pytest.mark.skipif(not TEST_IMAGE, reason="ALIGNFIT_TEST_IMAGE not set")
def test_image_real_photo(client):
    with open(TEST_IMAGE, "rb") as f:
        r = client.post("/analyze/image", files={"image": ("upload.jpg", f.read(), "image/jpeg")})
    assert r.status_code == 200
    d = r.json()
    assert d["landmark_detected"] is True and d["landmark_count"] == 33
    assert d["annotated_image_base64"] and d["recommendations"] and d["caution_message"]


def test_video_wrong_type_415(client):
    assert _post_video(client, b"hello", "notes.txt", "text/plain").status_code == 415


def test_video_empty_400(client):
    assert _post_video(client, b"").status_code == 400


def test_video_garbage_400(client):
    assert _post_video(client, b"not a video" * 100).status_code == 400


def test_video_too_large_413(client, monkeypatch):
    monkeypatch.setattr(main, "MAX_VIDEO_BYTES", 10)
    assert _post_video(client, b"0" * 11).status_code == 413


def test_video_too_long_400(client, monkeypatch):
    monkeypatch.setattr(main, "MAX_VIDEO_SECONDS", 1)
    r = _post_video(client, _blank_video(seconds=2))
    assert r.status_code == 400 and "너무 깁니다" in r.json()["detail"]


@needs_weights
def test_video_no_pose_returns_safe_response(client):
    r = _post_video(client, _blank_video())
    assert r.status_code == 200
    d = r.json()
    assert d["pose_detected"] is False
    assert d["movement_quality"] is None and d["predicted_exercise"] is None
    assert d["message"] and d["caution_message"] and d["recommendations"] == []


def test_video_model_unavailable_503(client, monkeypatch):
    def boom():
        raise ml.ModelUnavailableError("simulated")
    monkeypatch.setattr(ml, "load_models", boom)
    assert _post_video(client, _blank_video()).status_code == 503


def test_checksum_mismatch_rejected():
    with pytest.raises(ml.ModelUnavailableError):
        ml._read_verified(ml.BINARY_MODEL_FILE, {ml.BINARY_MODEL_FILE: "0" * 64})


@needs_weights
@pytest.mark.skipif(not TEST_VIDEO, reason="ALIGNFIT_TEST_VIDEO not set")
def test_video_real_exercise(client):
    with open(TEST_VIDEO, "rb") as f:
        r = _post_video(client, f.read())
    assert r.status_code == 200
    d = r.json()
    assert d["pose_detected"] is True and d["frames_used"] >= 3
    assert d["movement_quality"]["label"] in ("correct", "incorrect")
    assert d["predicted_exercise"]["exercise_id"] in ml.EXERCISE_NAMES
    assert d["recommendations"] and d["caution_message"]
