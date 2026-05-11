from pydantic import BaseModel
from typing import List


class AnalysisResponse(BaseModel):
    landmark_detected: bool
    landmark_count: int
    shoulder_height_difference: float
    hip_height_difference: float
    simple_summary: str
    recommendations: List[str]
    caution_message: str
