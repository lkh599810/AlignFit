"""ML movement-quality demo service (MobiPhysio).

Ports the Colab feature extractor (`mobiphysio_colab_extract_v2.py`, Cell 4):

    video -> MediaPipe PoseLandmarker (Tasks API, lite) -> per-frame joint angles
          -> 69-d summary aggregation (15 angles x mean/std/range/max + 9 symmetry)

The 69-d vector is built EXACTLY as in the extractor so it matches the
distribution the MobiPhysio MLPs were trained on (the checkpoints carry their own
`mu`/`sd` standardizer). Two trained MLPs are then run:

  * `mobi_mlp_binary_correctness.pt`  -> movement quality (correct / incorrect)
  * `mobi_mlp_exercise_9class.pt`      -> exercise class E01..E09

The predicted exercise is mapped to a body region + AlignFit posture pattern and
linked to homecare items through the shared recommendation DB
(`src.recommendation_db.recommend_by_pattern`) -- NOT the backend's hard-coded
rule list.

NOTE: this deliberately does NOT use `pose_service.py` (single-image MediaPipe
Solutions). It runs the Tasks-API PoseLandmarker over video frames to match the
training pipeline.
"""
from __future__ import annotations

import os
import sys
import urllib.request

import cv2
import numpy as np
import torch

# Make the repo-root `src` package importable from inside backend/ ------------
# .../backend/app/services/ml_quality_service.py -> repo root is 4 levels up.
_ROOT = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
)
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from src import config  # noqa: E402
from src import mobiphysio_dataset as mobi  # noqa: E402
from src import recommendation_db  # noqa: E402
from src.models import MLP  # noqa: E402

import mediapipe as mp  # noqa: E402
from mediapipe.tasks import python as mp_python  # noqa: E402
from mediapipe.tasks.python import vision  # noqa: E402

# ---------------------------------------------------------------------------
# MediaPipe PoseLandmarker (Tasks API) -- same lite model as the Colab extractor
# ---------------------------------------------------------------------------
_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
)
_MODEL_PATH = os.path.join(config.MODELS_DIR, "pose_landmarker_lite.task")

MIN_VISIBILITY = 0.5
FRAME_STRIDE = 2

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
assert len(FEATURE_ORDER) == mobi.N_FEATURES == 69

# Exercise id -> human-readable name (Cell 3 EX_NAME of the extractor).
EXERCISE_NAMES = {
    "E01": "Abduction", "E02": "Adduction", "E03": "Lateral Rotation",
    "E04": "Medial Rotation", "E05": "Circumduction", "E06": "Wrist Extension",
    "E07": "Hip Joint Flexion", "E08": "Lumber Flexion", "E09": "Back Extension",
}


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
# Lazy singletons (model file download + heavy objects loaded once)
# ---------------------------------------------------------------------------
_landmarker = None
_binary = None
_exercise = None


def _ensure_landmarker():
    global _landmarker
    if _landmarker is None:
        if not os.path.exists(_MODEL_PATH):
            urllib.request.urlretrieve(_MODEL_URL, _MODEL_PATH)
        # Pass the model as a byte buffer rather than a path: MediaPipe Tasks
        # mishandles absolute Windows paths (drive-letter colon) and prepends its
        # own resource dir, causing "Unable to open file ... errno=22".
        with open(_MODEL_PATH, "rb") as f:
            model_bytes = f.read()
        opts = vision.PoseLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_buffer=model_bytes),
            running_mode=vision.RunningMode.IMAGE, num_poses=1,
            min_pose_detection_confidence=0.5, min_pose_presence_confidence=0.5,
        )
        _landmarker = vision.PoseLandmarker.create_from_options(opts)
    return _landmarker


def _load_mlp(filename, n_classes):
    ckpt = torch.load(os.path.join(config.MODELS_DIR, filename), map_location="cpu")
    model = MLP(mobi.N_FEATURES, n_classes,
                hidden_sizes=tuple(ckpt["hidden_sizes"]), dropout=ckpt["dropout"])
    model.load_state_dict(ckpt["state_dict"])
    model.eval()
    return model, ckpt["mu"], ckpt["sd"]


def _models():
    global _binary, _exercise
    if _binary is None:
        _binary = _load_mlp("mobi_mlp_binary_correctness.pt", 2)
    if _exercise is None:
        _exercise = _load_mlp("mobi_mlp_exercise_9class.pt", mobi.N_EXERCISE_CLASSES)
    return _binary, _exercise


@torch.no_grad()
def _predict(bundle, vec):
    model, mu, sd = bundle
    x = (vec[None, :] - mu) / sd
    prob = torch.softmax(model(torch.tensor(x, dtype=torch.float32)), dim=1)[0].numpy()
    idx = int(prob.argmax())
    return idx, float(prob[idx])


def extract_features_from_video(video_path):
    """Run PoseLandmarker over the video and return (69-d float32 vector, n_frames).

    Returns (None, n_frames_with_pose) when too few usable frames were found.
    """
    landmarker = _ensure_landmarker()
    coords = []
    cap = cv2.VideoCapture(video_path)
    fi = 0
    while True:
        ok, fr = cap.read()
        if not ok:
            break
        if fi % FRAME_STRIDE == 0:
            rgb = cv2.cvtColor(fr, cv2.COLOR_BGR2RGB)
            mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            res = landmarker.detect(mp_img)
            if res.pose_landmarks:
                lm = res.pose_landmarks[0]
                coords.append(np.array([[p.x, p.y, p.visibility] for p in lm]))
        fi += 1
    cap.release()
    if len(coords) < 3:
        return None, len(coords)
    feats, _T = clip_features(coords)
    vec = np.array([feats.get(k, 0.0) for k in FEATURE_ORDER], dtype=np.float32)
    return vec, len(coords)


def predict_and_recommend(video_path):
    """Full chain: video -> 69-d features -> MLP predictions -> recommendations."""
    if not os.path.exists(config.RECOMMENDATION_DB):
        recommendation_db.build_db()

    vec, n_frames = extract_features_from_video(video_path)
    if vec is None:
        return {
            "pose_detected": False,
            "frames_used": n_frames,
            "message": ("영상에서 자세를 충분히 인식하지 못했습니다. "
                        "전신이 밝고 선명하게 보이는 영상으로 다시 시도해 주세요."),
            "caution_message": recommendation_db.CAUTION_BANNER,
        }

    binary_bundle, exercise_bundle = _models()
    b_idx, b_conf = _predict(binary_bundle, vec)
    e_idx, e_conf = _predict(exercise_bundle, vec)

    exercise_id = f"E{e_idx + 1:02d}"
    region = mobi.MOBI_EXERCISE_TO_REGION[exercise_id]
    pattern = mobi.MOBI_EXERCISE_TO_PATTERN[exercise_id]
    recs = recommendation_db.recommend_by_pattern(pattern, region=region)

    if b_idx == 1:
        quality = "needs_attention"
        note = ("이번 분석에서 좌우 비대칭이나 가동범위 제한 신호가 보였습니다. "
                "아래 홈케어 동작이 도움이 될 수 있습니다. 이는 의학적 진단이 아닙니다.")
    else:
        quality = "looks_ok"
        note = ("움직임이 대체로 균형 있어 보였습니다. "
                "아래 동작은 일반적인 유지·관리 목적의 참고용입니다.")

    return {
        "pose_detected": True,
        "frames_used": n_frames,
        "movement_quality": {
            "label": "incorrect" if b_idx == 1 else "correct",
            "status": quality,
            "confidence": round(b_conf, 3),
        },
        "predicted_exercise": {
            "exercise_id": exercise_id,
            "exercise_name": EXERCISE_NAMES[exercise_id],
            "confidence": round(e_conf, 3),
            "target_region": region,
            "posture_pattern": pattern,
        },
        "note": note,
        "recommendations": recs,
        "caution_message": recommendation_db.CAUTION_BANNER,
    }
