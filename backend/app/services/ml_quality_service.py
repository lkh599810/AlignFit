"""Video movement-quality inference (MobiPhysio MLPs).

Production port of the research pipeline in the repo-root `src/` package
(`src/mobiphysio_*.py`, `mobiphysio_colab_extract_v2.py` Cell 4):

    video -> MediaPipe PoseLandmarker (Tasks API, lite, IMAGE mode, every 2nd frame)
          -> per-frame joint angles
          -> 69-d summary vector (15 angles x mean/std/range/max + 9 symmetry)
          -> two trained MLPs (checkpoints carry their own mu/sd standardizer)
               * mobi_mlp_binary_correctness.pt -> correct / incorrect
               * mobi_mlp_exercise_9class.pt    -> exercise E01..E09
          -> exercise -> body region + posture pattern -> recommendation SQLite

This module is self-contained (no import of the research `src/` package, no
pandas) so the backend Docker image only needs `backend/`. Feature extraction and
the MLP definition are copied verbatim from the research code so the input
distribution matches what the checkpoints were trained on -- do not change the
angle definitions, FRAME_STRIDE, or frame resolution without re-validating.

Model assets live in ALIGNFIT_MODELS_DIR (default: backend/models) and are
verified against models/SHA256SUMS before loading (torch.load unpickles).
"""
from __future__ import annotations

import hashlib
import logging
import os
import sqlite3
import threading

import cv2
import numpy as np
import torch
import torch.nn as nn

import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

log = logging.getLogger(__name__)

_BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS_DIR = os.environ.get("ALIGNFIT_MODELS_DIR", os.path.join(_BACKEND_DIR, "models"))
RECOMMENDATION_DB = os.environ.get(
    "ALIGNFIT_RECOMMENDATION_DB", os.path.join(_BACKEND_DIR, "data", "recommendation.sqlite")
)

POSE_MODEL_FILE = "pose_landmarker_lite.task"
BINARY_MODEL_FILE = "mobi_mlp_binary_correctness.pt"
EXERCISE_MODEL_FILE = "mobi_mlp_exercise_9class.pt"

N_FEATURES = 69
N_EXERCISE_CLASSES = 9
MIN_VISIBILITY = 0.5
FRAME_STRIDE = 2

CAUTION_BANNER = (
    "This is a simple posture/movement analysis for educational purposes, not a "
    "medical diagnosis. If pain continues or worsens, consult a medical professional."
)


class ModelUnavailableError(RuntimeError):
    """Model assets missing, corrupted, or failed to load."""


# ---- Cell 4 port: landmark indices, angle triplets, stats, symmetry ---------
L = dict(
    nose=0, l_ear=7, r_ear=8, l_sho=11, r_sho=12, l_elb=13, r_elb=14,
    l_wri=15, r_wri=16, l_idx=19, r_idx=20, l_hip=23, r_hip=24,
    l_kne=25, r_kne=26, l_ank=27, r_ank=28,
)
ANGLES = [
    ("l_elbow", ("l_sho", "l_elb", "l_wri"), "upper"),
    ("r_elbow", ("r_sho", "r_elb", "r_wri"), "upper"),
    ("l_shoulder", ("l_hip", "l_sho", "l_elb"), "upper"),
    ("r_shoulder", ("r_hip", "r_sho", "r_elb"), "upper"),
    ("l_armpit", ("l_elb", "l_sho", "r_sho"), "upper"),
    ("r_armpit", ("r_elb", "r_sho", "l_sho"), "upper"),
    ("l_wrist", ("l_elb", "l_wri", "l_idx"), "upper"),
    ("r_wrist", ("r_elb", "r_wri", "r_idx"), "upper"),
    ("l_hip", ("l_sho", "l_hip", "l_kne"), "lower"),
    ("r_hip", ("r_sho", "r_hip", "r_kne"), "lower"),
    ("l_knee", ("l_hip", "l_kne", "l_ank"), "lower"),
    ("r_knee", ("r_hip", "r_kne", "r_ank"), "lower"),
    ("neck_tilt", ("l_ear", "nose", "r_ear"), "upper"),
    ("trunk_l", ("l_sho", "l_hip", "r_hip"), "lower"),
    ("trunk_r", ("r_sho", "r_hip", "l_hip"), "lower"),
]
STATS = ["mean", "std", "range", "max"]
SYM_PAIRS = [
    ("sym_elbow", "l_elbow", "r_elbow"), ("sym_shoulder", "l_shoulder", "r_shoulder"),
    ("sym_armpit", "l_armpit", "r_armpit"), ("sym_wrist", "l_wrist", "r_wrist"),
    ("sym_hip", "l_hip", "r_hip"), ("sym_knee", "l_knee", "r_knee"),
    ("sym_trunk", "trunk_l", "trunk_r"),
]

# Feature order f000..f068, identical to the trained feature DB layout.
FEATURE_ORDER: list[str] = []
for _name, _, _ in ANGLES:
    for _stat in STATS:
        FEATURE_ORDER.append(f"{_name}_{_stat}")
for _sym, _, _ in SYM_PAIRS:
    FEATURE_ORDER.append(_sym)
FEATURE_ORDER += ["sym_shoulder_h", "sym_hip_h"]
assert len(FEATURE_ORDER) == N_FEATURES

# Exercise id -> names / region / pattern (src/mobiphysio_dataset.py + extractor Cell 3).
EXERCISE_NAMES = {
    "E01": "Abduction", "E02": "Adduction", "E03": "Lateral Rotation",
    "E04": "Medial Rotation", "E05": "Circumduction", "E06": "Wrist Extension",
    "E07": "Hip Joint Flexion", "E08": "Lumber Flexion", "E09": "Back Extension",
}
EXERCISE_NAMES_KO = {
    "E01": "어깨 벌림 동작", "E02": "어깨 모음 동작", "E03": "어깨 바깥 돌림 동작",
    "E04": "어깨 안쪽 돌림 동작", "E05": "어깨 휘돌리기 동작", "E06": "손목 폄 동작",
    "E07": "고관절 굽힘 동작", "E08": "허리 굽힘 동작", "E09": "등 폄 동작",
}
EXERCISE_TO_REGION = {
    "E01": "upper_shoulder", "E02": "upper_shoulder", "E03": "upper_shoulder",
    "E04": "upper_shoulder", "E05": "upper_shoulder", "E06": "upper_wrist",
    "E07": "lower_hip", "E08": "low_back", "E09": "low_back",
}
EXERCISE_TO_PATTERN = {
    "E01": "shoulder_asymmetry", "E02": "shoulder_mobility", "E03": "shoulder_rotation",
    "E04": "shoulder_rotation", "E05": "shoulder_mobility", "E06": "wrist_mobility",
    "E07": "hip_mobility", "E08": "low_back_mobility", "E09": "low_back_mobility",
}
REGION_KO = {
    "upper_shoulder": "어깨/견갑 부위", "upper_wrist": "손목/팔 부위",
    "lower_hip": "고관절 부위", "low_back": "허리 부위", "lower_trunk": "몸통/하체 부위",
}
PATTERN_KO = {
    "shoulder_asymmetry": "좌우 어깨 움직임 비대칭 가능성",
    "shoulder_mobility": "어깨 가동성 저하 가능성",
    "shoulder_rotation": "어깨 회전 움직임 제한 가능성",
    "wrist_mobility": "손목 가동성 저하 가능성",
    "hip_mobility": "고관절 가동성 저하 가능성",
    "low_back_mobility": "허리 가동성 저하 가능성",
}


class MLP(nn.Module):
    """Same definition as src/models.py::MLP (state_dict keys net.0/net.3/net.6)."""

    def __init__(self, in_dim, n_classes, hidden_sizes=(128, 64), dropout=0.3):
        super().__init__()
        layers = []
        prev = in_dim
        for h in hidden_sizes:
            layers += [nn.Linear(prev, h), nn.ReLU(), nn.Dropout(dropout)]
            prev = h
        layers.append(nn.Linear(prev, n_classes))
        self.net = nn.Sequential(*layers)

    def forward(self, x):
        return self.net(x)


def _angle(a, b, c):
    """Angle (degrees) at vertex b for points a-b-c; NaN if a point is degenerate."""
    ba, bc = a - b, c - b
    nba, nbc = np.linalg.norm(ba), np.linalg.norm(bc)
    if nba < 1e-6 or nbc < 1e-6:
        return np.nan
    return np.degrees(np.arccos(np.clip(np.dot(ba, bc) / (nba * nbc), -1, 1)))


def clip_features(coords):
    """Per-frame landmark coords -> 69-d feature dict (verbatim Cell 4 logic)."""
    arr = np.stack(coords, 0)
    T = arr.shape[0]
    ang = {n: np.full(T, np.nan) for n, _, _ in ANGLES}
    for t in range(T):
        xy, vis = arr[t, :, :2], arr[t, :, 2]
        for n, (a, b, c), _ in ANGLES:
            ia, ib, ic = L[a], L[b], L[c]
            if min(vis[ia], vis[ib], vis[ic]) >= MIN_VISIBILITY:
                ang[n][t] = _angle(xy[ia], xy[ib], xy[ic])
    f = {}
    for n, _, _ in ANGLES:
        v = ang[n]
        ok = np.any(~np.isnan(v))
        f[f"{n}_mean"] = np.nanmean(v) if ok else 0.0
        f[f"{n}_std"] = np.nanstd(v) if ok else 0.0
        f[f"{n}_range"] = (np.nanmax(v) - np.nanmin(v)) if ok else 0.0
        f[f"{n}_max"] = np.nanmax(v) if ok else 0.0
    for sn, ln, rn in SYM_PAIRS:
        d = np.abs(ang[ln] - ang[rn])
        f[sn] = np.nanmean(d) if np.any(~np.isnan(d)) else 0.0
    f["sym_shoulder_h"] = float(np.nanmean(np.abs(arr[:, L["l_sho"], 1] - arr[:, L["r_sho"], 1])))
    f["sym_hip_h"] = float(np.nanmean(np.abs(arr[:, L["l_hip"], 1] - arr[:, L["r_hip"], 1])))
    return f, T


# ---------------------------------------------------------------------------
# Model loading (once, verified against SHA256SUMS)
# ---------------------------------------------------------------------------
# ponytail: one global lock serializes video inference (PoseLandmarker is not
# thread-safe); use a landmarker pool / worker queue if concurrent load grows.
_lock = threading.Lock()
_landmarker = None
_binary = None
_exercise = None


def _expected_hashes() -> dict[str, str]:
    path = os.path.join(MODELS_DIR, "SHA256SUMS")
    if not os.path.exists(path):
        raise ModelUnavailableError(f"missing {path}")
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                digest, name = line.split(maxsplit=1)
                out[name.strip().lstrip("*")] = digest
    return out


def _read_verified(filename: str, hashes: dict[str, str]) -> bytes:
    path = os.path.join(MODELS_DIR, filename)
    if not os.path.exists(path):
        raise ModelUnavailableError(f"missing model file {path}")
    with open(path, "rb") as f:
        data = f.read()
    if hashlib.sha256(data).hexdigest() != hashes.get(filename):
        raise ModelUnavailableError(f"checksum mismatch for {filename}")
    return data


def _load_mlp(data: bytes, n_classes: int):
    import io

    ckpt = torch.load(io.BytesIO(data), map_location="cpu")
    model = MLP(N_FEATURES, n_classes,
                hidden_sizes=tuple(ckpt["hidden_sizes"]), dropout=ckpt["dropout"])
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    return model, ckpt["mu"], ckpt["sd"]


def load_models():
    """Load all video-model assets. Raises ModelUnavailableError on any failure."""
    global _landmarker, _binary, _exercise
    with _lock:
        if _landmarker is not None:
            return
        try:
            hashes = _expected_hashes()
            binary = _load_mlp(_read_verified(BINARY_MODEL_FILE, hashes), 2)
            exercise = _load_mlp(_read_verified(EXERCISE_MODEL_FILE, hashes), N_EXERCISE_CLASSES)
            # Model passed as a buffer: MediaPipe Tasks mishandles absolute Windows paths.
            opts = vision.PoseLandmarkerOptions(
                base_options=mp_python.BaseOptions(
                    model_asset_buffer=_read_verified(POSE_MODEL_FILE, hashes)),
                running_mode=vision.RunningMode.IMAGE, num_poses=1,
                min_pose_detection_confidence=0.5, min_pose_presence_confidence=0.5,
            )
            landmarker = vision.PoseLandmarker.create_from_options(opts)
        except ModelUnavailableError:
            raise
        except Exception as e:  # corrupted checkpoint, incompatible torch/mediapipe, ...
            raise ModelUnavailableError(f"model load failed: {e}") from e
        _binary, _exercise, _landmarker = binary, exercise, landmarker


def models_loaded() -> bool:
    return _landmarker is not None


@torch.no_grad()
def _predict(bundle, vec):
    model, mu, sd = bundle
    x = (vec[None, :] - mu) / sd
    prob = torch.softmax(model(torch.tensor(x, dtype=torch.float32)), dim=1)[0].numpy()
    idx = int(prob.argmax())
    return idx, float(prob[idx])


def _extract_features(cap, max_frames: int):
    """Run PoseLandmarker over frames; return (69-d vector or None, frames with pose)."""
    coords = []
    fi = 0
    while fi < max_frames:
        ok, fr = cap.read()
        if not ok:
            break
        if fi % FRAME_STRIDE == 0:
            rgb = cv2.cvtColor(fr, cv2.COLOR_BGR2RGB)
            res = _landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
            if res.pose_landmarks:
                lm = res.pose_landmarks[0]
                coords.append(np.array([[p.x, p.y, p.visibility] for p in lm]))
        fi += 1
    if len(coords) < 3:
        return None, len(coords)
    feats, _ = clip_features(coords)
    return np.array([feats.get(k, 0.0) for k in FEATURE_ORDER], dtype=np.float32), len(coords)


def _recommend(pattern: str, region: str, limit: int = 3) -> list[dict]:
    """Pattern match first, region fallback (same as src.recommendation_db.recommend_by_pattern)."""
    conn = sqlite3.connect(f"file:{RECOMMENDATION_DB}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    cols = "exercise_name, related_body_part, reason, cautions, youtube_query, verify_citation"
    try:
        rows = conn.execute(
            f"SELECT {cols} FROM recommendation WHERE posture_pattern = ? LIMIT ?",
            (pattern, limit)).fetchall()
        if not rows:
            rows = conn.execute(
                f"SELECT {cols} FROM recommendation WHERE target_region = ? LIMIT ?",
                (region, limit)).fetchall()
    finally:
        conn.close()
    return [dict(r) for r in rows]


def predict_and_recommend(cap, max_frames: int) -> dict:
    """Full chain on an opened cv2.VideoCapture. Caller validates the video first."""
    load_models()
    with _lock:
        vec, n_frames = _extract_features(cap, max_frames)
    if vec is None:
        return {
            "pose_detected": False,
            "frames_used": n_frames,
            "message": ("영상에서 자세를 충분히 인식하지 못했습니다. "
                        "전신이 밝고 선명하게 보이는 영상으로 다시 시도해 주세요."),
            "recommendations": [],
            "caution_message": CAUTION_BANNER,
        }

    b_idx, b_conf = _predict(_binary, vec)
    e_idx, e_conf = _predict(_exercise, vec)
    exercise_id = f"E{e_idx + 1:02d}"
    region = EXERCISE_TO_REGION[exercise_id]
    pattern = EXERCISE_TO_PATTERN[exercise_id]

    if b_idx == 1:
        status = "needs_attention"
        message = ("이번 분석에서 좌우 비대칭이나 가동범위 제한 신호가 보였습니다. "
                   "아래 홈케어 동작이 도움이 될 수 있습니다. 이는 의학적 진단이 아닙니다.")
    else:
        status = "looks_ok"
        message = ("움직임이 대체로 균형 있어 보였습니다. "
                   "아래 동작은 일반적인 유지·관리 목적의 참고용입니다.")

    return {
        "pose_detected": True,
        "frames_used": n_frames,
        "movement_quality": {
            "label": "incorrect" if b_idx == 1 else "correct",
            "status": status,
            "confidence": round(b_conf, 3),
        },
        "predicted_exercise": {
            "exercise_id": exercise_id,
            "exercise_name": EXERCISE_NAMES[exercise_id],
            "exercise_name_ko": EXERCISE_NAMES_KO[exercise_id],
            "confidence": round(e_conf, 3),
            "target_region": region,
            "target_region_ko": REGION_KO[region],
            "posture_pattern": pattern,
            "posture_pattern_ko": PATTERN_KO[pattern],
        },
        "message": message,
        "recommendations": _recommend(pattern, region),
        "caution_message": CAUTION_BANNER,
    }
