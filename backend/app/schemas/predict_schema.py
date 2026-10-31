from typing import List, Optional

from pydantic import BaseModel


class MovementQuality(BaseModel):
    label: str          # "correct" | "incorrect"
    status: str         # "looks_ok" | "needs_attention"
    confidence: float   # 0..1, softmax probability of `label`


class PredictedExercise(BaseModel):
    exercise_id: str            # "E01".."E09"
    exercise_name: str          # English name from the MobiPhysio dataset
    exercise_name_ko: str
    confidence: float
    target_region: str          # e.g. "upper_shoulder"
    target_region_ko: str
    posture_pattern: str        # e.g. "shoulder_asymmetry"
    posture_pattern_ko: str


class VideoRecommendation(BaseModel):
    exercise_name: str
    related_body_part: Optional[str] = None
    reason: Optional[str] = None
    cautions: Optional[str] = None
    youtube_query: Optional[str] = None
    verify_citation: Optional[str] = None


class PredictResponse(BaseModel):
    pose_detected: bool
    frames_used: int
    movement_quality: Optional[MovementQuality] = None      # null when pose_detected is false
    predicted_exercise: Optional[PredictedExercise] = None  # null when pose_detected is false
    message: str
    recommendations: List[VideoRecommendation] = []
    caution_message: str
