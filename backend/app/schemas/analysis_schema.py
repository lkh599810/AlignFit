from pydantic import BaseModel
from typing import List, Optional


class AnalysisResponse(BaseModel):
    landmark_detected: bool
    landmark_count: int
    shoulder_height_difference: float
    hip_height_difference: float
    simple_summary: str
    recommendations: List[str]
    caution_message: str
    annotated_image_base64: Optional[str] = None

    # Signed directional results. Direction enums:
    # shoulder/hip: "left_higher" | "right_higher" | "balanced" | "not_detected"
    # head/trunk:   "left" | "right" | "balanced" | "not_detected"
    # foot status:  "left_more_outward" | "right_more_outward" | "balanced"
    #               | "uncertain" | "not_detected"
    shoulder_direction: str = "not_detected"
    shoulder_direction_summary: str = ""
    hip_direction: str = "not_detected"
    hip_direction_summary: str = ""

    # Head tilt metric
    head_tilt_detected: bool = False
    head_tilt_angle_degrees: Optional[float] = None
    head_tilt_direction: str = "not_detected"
    head_tilt_summary: str = ""

    # Trunk centerline metric
    trunk_centerline_detected: bool = False
    trunk_centerline_angle_degrees: Optional[float] = None
    trunk_tilt_direction: str = "not_detected"
    trunk_centerline_summary: str = ""

    # Foot direction metric
    foot_direction_detected: bool = False
    left_foot_angle_degrees: Optional[float] = None
    right_foot_angle_degrees: Optional[float] = None
    foot_direction_difference_degrees: Optional[float] = None
    foot_direction_status: str = "not_detected"
    foot_direction_summary: str = ""
