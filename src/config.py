"""Central configuration for the AlignFit intelligent (ML) component.

All paths, dataset schema constants, the 10 UI-PRMD exercises, the joint layout,
and the exercise -> body-region -> AlignFit-pattern mapping live here so every
other module shares one source of truth.
"""
from __future__ import annotations

import os

# ----------------------------------------------------------------------------
# Paths
# ----------------------------------------------------------------------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DATA_DIR = os.path.join(ROOT, "data")
RAW_DIR = os.path.join(DATA_DIR, "raw")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
SAMPLES_DIR = os.path.join(RAW_DIR, "uiprmd_samples")        # real per-exercise seeds
AVAKANSKI_DIR = os.path.join(RAW_DIR, "avakanski")           # real deep-squat reduced set

MODELS_DIR = os.path.join(ROOT, "models")
RESULTS_DIR = os.path.join(ROOT, "results")
DB_DIR = os.path.join(ROOT, "db")
REPORT_DIR = os.path.join(ROOT, "report")
LOGS_DIR = os.path.join(ROOT, "logs")

FEATURES_DB = os.path.join(DB_DIR, "features.sqlite")
RECOMMENDATION_DB = os.path.join(DB_DIR, "recommendation.sqlite")

for _d in (PROCESSED_DIR, MODELS_DIR, RESULTS_DIR, DB_DIR, REPORT_DIR, LOGS_DIR):
    os.makedirs(_d, exist_ok=True)

# ----------------------------------------------------------------------------
# Dataset schema
# ----------------------------------------------------------------------------
# 22 joints x 3 Euler angles = 66 angle dimensions per frame (UI-PRMD Kinect format).
N_JOINTS = 22
N_ANGLES_PER_JOINT = 3
N_ANGLE_DIMS = N_JOINTS * N_ANGLES_PER_JOINT  # 66

RANDOM_SEED = 42

# Synthetic-dataset generation parameters (grounded on real per-exercise seeds).
N_SUBJECTS = 10            # s01..s10  (matches UI-PRMD)
N_REPS_PER_CLASS = 10      # correct reps and incorrect reps per subject per exercise

# ----------------------------------------------------------------------------
# The 10 UI-PRMD exercises (m01..m10)
# ----------------------------------------------------------------------------
EXERCISES = {
    "m01": "Deep squat",
    "m02": "Hurdle step",
    "m03": "Inline lunge",
    "m04": "Side lunge",
    "m05": "Sit to stand",
    "m06": "Standing active straight leg raise",
    "m07": "Standing shoulder abduction",
    "m08": "Standing shoulder extension",
    "m09": "Standing shoulder internal-external rotation",
    "m10": "Standing shoulder scaption",
}
EXERCISE_IDS = list(EXERCISES.keys())            # ordered m01..m10
EXERCISE_TO_INDEX = {m: i for i, m in enumerate(EXERCISE_IDS)}

# ----------------------------------------------------------------------------
# Body-region mapping (EXPLICIT DESIGN CHOICE — documented in report limitations).
# Per project spec: shoulders/upper body = m07..m10, lower body/trunk = m01..m06.
# ----------------------------------------------------------------------------
REGION_LOWER_TRUNK = "lower_trunk"
REGION_UPPER_SHOULDER = "upper_shoulder"

EXERCISE_TO_REGION = {
    "m01": REGION_LOWER_TRUNK, "m02": REGION_LOWER_TRUNK, "m03": REGION_LOWER_TRUNK,
    "m04": REGION_LOWER_TRUNK, "m05": REGION_LOWER_TRUNK, "m06": REGION_LOWER_TRUNK,
    "m07": REGION_UPPER_SHOULDER, "m08": REGION_UPPER_SHOULDER,
    "m09": REGION_UPPER_SHOULDER, "m10": REGION_UPPER_SHOULDER,
}

# Map each exercise/region to an AlignFit posture pattern keyword used to query the
# recommendation DB (links the model output back to the homecare app concept).
EXERCISE_TO_ALIGNFIT_PATTERN = {
    "m01": "lower_body_alignment",
    "m02": "single_leg_balance",
    "m03": "lower_body_alignment",
    "m04": "lateral_pelvis_stability",
    "m05": "trunk_hip_strength",
    "m06": "hip_mobility",
    "m07": "shoulder_asymmetry",
    "m08": "shoulder_mobility",
    "m09": "shoulder_rotation",
    "m10": "shoulder_asymmetry",
}

# ----------------------------------------------------------------------------
# Joint layout (our interpretation of the 22-joint UI-PRMD Kinect skeleton).
# Index = joint; each joint owns angle dims [3*idx, 3*idx+1, 3*idx+2].
# Used for left/right symmetry features and upper/lower feature-group ablation.
# ----------------------------------------------------------------------------
JOINT_NAMES = [
    "spine_base",       # 0
    "spine_mid",        # 1
    "neck",             # 2
    "head",             # 3
    "shoulder_left",    # 4
    "elbow_left",       # 5
    "wrist_left",       # 6
    "hand_left",        # 7
    "shoulder_right",   # 8
    "elbow_right",      # 9
    "wrist_right",      # 10
    "hand_right",       # 11
    "hip_left",         # 12
    "knee_left",        # 13
    "ankle_left",       # 14
    "foot_left",        # 15
    "hip_right",        # 16
    "knee_right",       # 17
    "ankle_right",      # 18
    "foot_right",       # 19
    "spine_shoulder",   # 20
    "pelvis",           # 21
]
assert len(JOINT_NAMES) == N_JOINTS

# Left/right symmetric joint pairs (by joint index) for symmetry-score features.
LEFT_RIGHT_PAIRS = [
    (4, 8),    # shoulder
    (5, 9),    # elbow
    (6, 10),   # wrist
    (7, 11),   # hand
    (12, 16),  # hip
    (13, 17),  # knee
    (14, 18),  # ankle
    (15, 19),  # foot
]

# Upper-body vs lower-body joint index groups (for ablation feature groups).
UPPER_BODY_JOINTS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 20]
LOWER_BODY_JOINTS = [0, 1, 12, 13, 14, 15, 16, 17, 18, 19, 21]


def angle_dims_for_joints(joint_indices):
    """Return the flat angle-dimension indices owned by the given joint indices."""
    dims = []
    for j in joint_indices:
        base = j * N_ANGLES_PER_JOINT
        dims.extend([base, base + 1, base + 2])
    return dims
