import os
import tempfile

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import HTMLResponse

from app.schemas.analysis_schema import AnalysisResponse
from app.services.annotation_service import annotate_image
from app.services.pose_service import INVALID_IMAGE, NO_POSE_DETECTED, extract_landmarks
from app.services.posture_analysis_service import analyze_posture
from app.services.recommendation_service import CAUTION_MESSAGE, generate_recommendations

app = FastAPI(title="AlignFit Backend")

_NOT_ANALYZABLE = "사진에서 자세를 인식하지 못해 이 항목은 분석하기 어렵습니다."

_NO_POSE_RESPONSE = AnalysisResponse(
    landmark_detected=False,
    landmark_count=0,
    shoulder_height_difference=0.0,
    hip_height_difference=0.0,
    simple_summary=(
        "사진에서 자세를 인식하지 못했습니다. "
        "밝고 선명한 전신 정면 사진으로 다시 시도해 주세요."
    ),
    recommendations=["분석을 위해 밝고 선명한 전신 자세 사진을 업로드해 주세요."],
    caution_message=CAUTION_MESSAGE,
    shoulder_direction="not_detected",
    shoulder_direction_summary=_NOT_ANALYZABLE,
    hip_direction="not_detected",
    hip_direction_summary=_NOT_ANALYZABLE,
    head_tilt_detected=False,
    head_tilt_direction="not_detected",
    head_tilt_summary=_NOT_ANALYZABLE,
    trunk_centerline_detected=False,
    trunk_tilt_direction="not_detected",
    trunk_centerline_summary=_NOT_ANALYZABLE,
    foot_direction_detected=False,
    foot_direction_status="not_detected",
    foot_direction_summary=_NOT_ANALYZABLE,
)


@app.post("/analyze/image", response_model=AnalysisResponse)
async def analyze_image(image: UploadFile = File(...)):
    image_bytes = await image.read()
    landmarks = extract_landmarks(image_bytes)

    if landmarks == INVALID_IMAGE:
        raise HTTPException(
            status_code=400,
            detail="유효하지 않은 이미지 파일입니다. JPEG 또는 PNG 형식의 이미지를 업로드해 주세요.",
        )

    if landmarks == NO_POSE_DETECTED:
        return _NO_POSE_RESPONSE

    analysis = analyze_posture(landmarks)
    recommendations = generate_recommendations(
        analysis["shoulder_height_difference"],
        analysis["hip_height_difference"],
        analysis["shoulder_slope"],
        analysis["hip_slope"],
        analysis["shoulder_hip_center_offset"],
        analysis["low_visibility_landmarks"],
        head_tilt_score=analysis["head_tilt_score"],
        head_center_offset=analysis["head_center_offset"],
        foot_angle_diff=analysis["foot_direction_difference_degrees"],
    )
    annotated_b64 = annotate_image(image_bytes, landmarks, analysis)

    return AnalysisResponse(
        landmark_detected=True,
        landmark_count=len(landmarks),
        shoulder_height_difference=analysis["shoulder_height_difference"],
        hip_height_difference=analysis["hip_height_difference"],
        simple_summary=analysis["simple_summary"],
        recommendations=recommendations["exercises"],
        caution_message=recommendations["caution_message"],
        annotated_image_base64=annotated_b64 or None,
        shoulder_direction=analysis["shoulder_direction"],
        shoulder_direction_summary=analysis["shoulder_direction_summary"],
        hip_direction=analysis["hip_direction"],
        hip_direction_summary=analysis["hip_direction_summary"],
        head_tilt_detected=analysis["head_tilt_detected"],
        head_tilt_angle_degrees=analysis["head_tilt_angle_degrees"],
        head_tilt_direction=analysis["head_tilt_direction"],
        head_tilt_summary=analysis["head_tilt_summary"],
        trunk_centerline_detected=analysis["trunk_centerline_detected"],
        trunk_centerline_angle_degrees=analysis["trunk_centerline_angle_degrees"],
        trunk_tilt_direction=analysis["trunk_tilt_direction"],
        trunk_centerline_summary=analysis["trunk_centerline_summary"],
        foot_direction_detected=analysis["foot_direction_detected"],
        left_foot_angle_degrees=analysis["left_foot_angle_degrees"],
        right_foot_angle_degrees=analysis["right_foot_angle_degrees"],
        foot_direction_difference_degrees=analysis["foot_direction_difference_degrees"],
        foot_direction_status=analysis["foot_direction_status"],
        foot_direction_summary=analysis["foot_direction_summary"],
    )


@app.post("/predict")
async def predict(video: UploadFile = File(...)):
    """Movement-quality ML demo: video -> 69-d features -> MLP -> recommendations."""
    # Imported lazily so the image endpoint still works without the ML deps.
    from app.services.ml_quality_service import predict_and_recommend

    data = await video.read()
    suffix = os.path.splitext(video.filename or "")[1] or ".mp4"
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        tmp.write(data)
        tmp.close()
        return predict_and_recommend(tmp.name)
    finally:
        os.unlink(tmp.name)


_DEMO_HTML = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AlignFit · 재활운동 평가</title>
<style>
  :root {
    --bg: #f4f6f8; --surface: #ffffff; --line: #e6e9ee; --ink: #1f2933;
    --muted: #6b7682; --primary: #2f8f83; --primary-soft: #e4f1ef;
    --warn: #c4761b; --warn-soft: #fbf0e2; --ok: #2f8f83; --ok-soft: #e4f1ef;
  }
  * { box-sizing: border-box; }
  body { font-family: system-ui, -apple-system, sans-serif; color: var(--ink);
         background: var(--bg); max-width: 720px; margin: 0 auto;
         padding: 16px 16px 48px; line-height: 1.55; }
  h1 { font-size: 1.25rem; margin: 8px 0 2px; }
  .lead { color: var(--muted); font-size: .9rem; margin: 0 0 16px; }
  .card { background: var(--surface); border: 1px solid var(--line);
          border-radius: 16px; padding: 16px; margin-top: 14px; }
  .section-title { font-size: .8rem; font-weight: 700; color: var(--muted);
                   letter-spacing: .02em; margin: 0 0 10px; }
  .badge { display: inline-block; padding: 8px 16px; border-radius: 999px;
           font-weight: 700; font-size: 1rem; }
  .badge.warn { background: var(--warn-soft); color: var(--warn); }
  .badge.ok { background: var(--ok-soft); color: var(--ok); }
  .result-msg { margin: 12px 0 0; font-size: .96rem; }
  .kv { display: flex; flex-wrap: wrap; gap: 8px 18px; margin: 4px 0 0; }
  .kv div { font-size: .92rem; }
  .kv b { color: var(--ink); }
  .pill { display: inline-block; background: var(--primary-soft); color: var(--primary);
          border-radius: 8px; padding: 2px 10px; font-size: .85rem; font-weight: 600; }
  .rec { border: 1px solid var(--line); border-radius: 14px; padding: 14px;
         margin-top: 10px; }
  .rec h3 { margin: 0 0 4px; font-size: 1.02rem; }
  .rec .region { font-size: .82rem; color: var(--primary); font-weight: 600; margin-bottom: 8px; }
  .rec .reason { font-size: .9rem; margin: 0 0 6px; }
  .rec .caution { font-size: .84rem; color: var(--muted); margin: 0 0 12px; }
  .yt { display: inline-block; text-decoration: none; background: var(--ink);
        color: #fff; font-size: .88rem; font-weight: 600; padding: 9px 14px;
        border-radius: 10px; }
  .info-row { display: flex; justify-content: space-between; font-size: .88rem;
              color: var(--muted); padding: 4px 0; }
  .info-row b { color: var(--ink); font-weight: 600; }
  .disclaimer { font-size: .82rem; color: var(--muted); margin-top: 14px;
                background: #fafbfc; border: 1px solid var(--line);
                border-radius: 12px; padding: 12px 14px; }
  .controls { background: var(--surface); border: 1px solid var(--line);
              border-radius: 16px; padding: 16px; }
  input[type=file] { font-size: .9rem; }
  button#go { margin-top: 12px; width: 100%; padding: 12px; border: none;
              border-radius: 12px; background: var(--primary); color: #fff;
              font-size: 1rem; font-weight: 700; }
  #status { color: var(--muted); font-size: .9rem; margin-top: 10px; }
</style>
</head>
<body>
<h1>재활운동 평가</h1>
<p class="lead">운동 영상을 올리면 자세를 분석해 움직임 상태를 확인하고
홈케어 동작을 참고로 추천해 드립니다.</p>

<div class="controls">
  <input id="file" type="file" accept="video/*">
  <button id="go">영상 분석하기</button>
  <div id="status"></div>
</div>

<div id="result"></div>

<script>
// ── 모델 라벨 → 사용자 친화 한글 표시 매핑(표시 레이어 전용; API는 그대로) ──
const EX_KO = {
  E01: '어깨 벌림 동작', E02: '어깨 모음 동작', E03: '어깨 바깥 돌림 동작',
  E04: '어깨 안쪽 돌림 동작', E05: '어깨 휘돌리기 동작', E06: '손목 폄 동작',
  E07: '고관절 굽힘 동작', E08: '허리 굽힘 동작', E09: '등 폄 동작'
};
const REGION_KO = {
  upper_shoulder: '어깨/견갑 부위', upper_wrist: '손목/팔 부위',
  lower_hip: '고관절 부위', low_back: '허리 부위', lower_trunk: '몸통/하체 부위'
};
const PATTERN_KO = {
  shoulder_asymmetry: '좌우 어깨 움직임 비대칭 가능성',
  shoulder_mobility: '어깨 가동성 저하 가능성',
  shoulder_rotation: '어깨 회전 움직임 제한 가능성',
  wrist_mobility: '손목 가동성 저하 가능성',
  hip_mobility: '고관절 가동성 저하 가능성',
  low_back_mobility: '허리 가동성 저하 가능성'
};

function pct(x) { return Math.round((Number(x) || 0) * 100) + '%'; }
function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function youtubeUrl(rec) {
  const q = (rec.youtube_query && rec.youtube_query.trim())
    ? rec.youtube_query : ((rec.exercise_name || '') + ' 운동');
  return 'https://www.youtube.com/results?search_query=' + encodeURIComponent(q);
}

const go = document.getElementById('go');
const statusEl = document.getElementById('status');
const resultEl = document.getElementById('result');

go.onclick = async () => {
  const f = document.getElementById('file').files[0];
  if (!f) { statusEl.textContent = '먼저 영상 파일을 선택해 주세요.'; return; }
  statusEl.textContent = '분석 중입니다... (영상 길이에 따라 수십 초 걸릴 수 있어요)';
  resultEl.innerHTML = '';
  const fd = new FormData();
  fd.append('video', f);
  try {
    const res = await fetch('/predict', { method: 'POST', body: fd });
    const d = await res.json();
    statusEl.textContent = '';

    if (!d.pose_detected) {
      resultEl.innerHTML =
        '<div class="card"><p class="result-msg">' +
        esc(d.message || '영상에서 자세를 충분히 인식하지 못했습니다.') +
        '</p></div>' +
        '<div class="disclaimer">' + esc(d.caution_message || '') + '</div>';
      return;
    }

    const attention = (d.movement_quality.status === 'needs_attention'
                       || d.movement_quality.label === 'incorrect');
    const badgeText = attention ? '움직임 주의 필요' : '움직임 양호';
    const badgeClass = attention ? 'warn' : 'ok';

    const ex = d.predicted_exercise;
    const exKo = EX_KO[ex.exercise_id] || ex.exercise_name;
    const regionKo = REGION_KO[ex.target_region] || ex.target_region;
    const patternKo = PATTERN_KO[ex.posture_pattern] || '';

    const mainMsg = attention
      ? ('분석 결과, ' + regionKo + '의 움직임에서 좌우 균형이 다소 흐트러지거나 ' +
         '가동 범위가 제한되는 패턴이 감지되었습니다. 아래 홈케어 운동은 ' +
         regionKo + ' 주변의 움직임 조절에 도움을 줄 수 있습니다. ' +
         '본 결과는 의학적 진단이 아니며, 통증이 심하거나 지속되면 전문가 상담이 필요합니다.')
      : ('분석 결과, ' + regionKo + '의 움직임이 비교적 안정적으로 보였습니다. ' +
         '아래 동작은 현재 상태를 부드럽게 유지하기 위한 홈케어 참고용입니다. ' +
         '본 결과는 의학적 진단이 아닙니다.');

    const recCards = (d.recommendations || []).map(r =>
      '<div class="rec">' +
        '<h3>' + esc(r.exercise_name) + '</h3>' +
        '<div class="region">' + esc(r.related_body_part || regionKo) + '</div>' +
        (r.reason ? '<p class="reason">' + esc(r.reason) + '</p>' : '') +
        (r.cautions ? '<p class="caution">주의: ' + esc(r.cautions) + '</p>' : '') +
        '<a class="yt" href="' + youtubeUrl(r) + '">YouTube에서 동작 보기</a>' +
      '</div>'
    ).join('');

    resultEl.innerHTML =
      // 1) 메인 결과 배지 + 메시지
      '<div class="card">' +
        '<span class="badge ' + badgeClass + '">' + badgeText + '</span>' +
        '<p class="result-msg">' + mainMsg + '</p>' +
      '</div>' +
      // 2) 감지된 동작
      '<div class="card">' +
        '<p class="section-title">감지된 동작</p>' +
        '<div class="kv">' +
          '<div><b>' + esc(exKo) + '</b></div>' +
          '<div><span class="pill">' + esc(regionKo) + '</span></div>' +
        '</div>' +
        (patternKo ? '<p class="result-msg">' + esc(patternKo) + '</p>' : '') +
      '</div>' +
      // 3) 추천 홈케어 운동
      '<div class="card">' +
        '<p class="section-title">추천 홈케어 운동</p>' +
        (recCards || '<p class="result-msg">추천 항목이 없습니다.</p>') +
      '</div>' +
      // 4) 분석 정보(기술 상세, 시각적으로 보조)
      '<div class="card">' +
        '<p class="section-title">분석 정보</p>' +
        '<div class="info-row"><span>움직임 품질 신뢰도</span><b>' +
          pct(d.movement_quality.confidence) + '</b></div>' +
        '<div class="info-row"><span>운동 분류 신뢰도</span><b>' +
          pct(ex.confidence) + '</b></div>' +
        '<div class="info-row"><span>분석 프레임</span><b>' +
          esc(d.frames_used) + '</b></div>' +
      '</div>' +
      // 5) 비진단 안내
      '<div class="disclaimer">' + esc(d.caution_message) + '</div>';
  } catch (e) {
    statusEl.textContent = '분석 중 오류가 발생했습니다: ' + e;
  }
};
</script>
</body>
</html>"""


@app.get("/demo", response_class=HTMLResponse)
async def demo():
    """Minimal HTML page to upload a video and view the ML prediction + recs."""
    return _DEMO_HTML
