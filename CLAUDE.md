# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Project name: AlignFit

AlignFit is an AI-based body posture analysis and homecare recommendation app.

The backend MVP receives a body posture image, extracts pose landmarks using MediaPipe Pose, performs simple posture asymmetry analysis, and returns basic homecare exercise recommendations.

This project must not claim medical diagnosis.

## Current MVP Scope

Implement only the backend MVP first.

Current MVP flow:

1. User uploads a body posture image.
2. Backend receives the image.
3. Backend extracts pose landmarks.
4. Backend analyzes simple posture asymmetry.
5. Backend returns posture analysis and basic exercise recommendations.

For the current MVP:

- Do not create Android code yet.
- Do not implement login.
- Do not implement a database.
- Do not implement complex segmentation.
- Do not implement complex ML training.
- Do not implement LLM integration yet.
- Do not implement deployment automation yet.

## Working Directory Rules

- Only work inside `C:\AlignFit`.
- Do not access files outside this project directory.
- For the current MVP task, work only inside `backend/`.
- Do not read secrets, SSH keys, browser files, or environment files.
- Ask before running install commands, delete commands, or `git push`.

## Coding Style

- Use `snake_case` for all file names, directories, variables, and functions.
- Keep code simple and readable.
- Prefer small modules with clear responsibilities.
- Do not add unnecessary abstractions.
- Do not add features that were not requested.
- Match the existing project style if files already exist.

## Backend Tech Stack

- Language: Python
- Framework: FastAPI
- Image upload: FastAPI UploadFile
- Pose estimation: MediaPipe Pose
- Image processing: OpenCV and NumPy
- API format: JSON response
- Segmentation: placeholder module only for now
- Recommendation: simple rule-based module only
- Database: not used in this MVP
- Authentication/login: not used in this MVP
- Android app: not implemented yet
- Deployment: Dockerfile included for backend containerization
- Local run: `uvicorn app.main:app --reload`

## Required Endpoint

POST /analyze/image

Input:

- image file

Output JSON fields:

```json
{
  "landmark_detected": true,
  "landmark_count": 33,
  "shoulder_height_difference": 0.0,
  "hip_height_difference": 0.0,
  "simple_summary": "string",
  "recommendations": ["string"],
  "caution_message": "string"
}
```

## Suggested Backend Structure

Use this structure unless there is a strong reason not to:

```
backend/
  app/
    __init__.py
    main.py
    services/
      __init__.py
      pose_service.py
      posture_analysis_service.py
      segmentation_service.py
      recommendation_service.py
    schemas/
      __init__.py
      analysis_schema.py
  requirements.txt
  Dockerfile
  README.md
```

## Module Responsibilities

### main.py

- Create FastAPI app.
- Define POST /analyze/image.
- Receive uploaded image.
- Call pose extraction.
- Call posture analysis.
- Call recommendation logic.
- Return response JSON.

### pose_service.py

- Decode uploaded image.
- Convert image to RGB.
- Run MediaPipe Pose.
- Return landmarks.

### posture_analysis_service.py

- Calculate shoulder height difference.
- Calculate hip height difference.
- Generate simple posture summary.

Use MediaPipe landmark indices:

- left_shoulder: 11
- right_shoulder: 12
- left_hip: 23
- right_hip: 24

### segmentation_service.py

Placeholder only.

Do not implement complex segmentation yet.

### recommendation_service.py

Use simple rule-based recommendations only.

Example:

If shoulder difference is large:

- Recommend shoulder mobility exercise.
- Recommend gentle upper back stretching.

If hip difference is large:

- Recommend hip flexor stretch.
- Recommend glute bridge.

Always include a caution message.

## Medical Safety Rule

Do not describe the result as a diagnosis.

Allowed wording:

- This result is a simple posture analysis for educational purposes.
- It is not a medical diagnosis.
- If pain continues or worsens, consult a medical professional.

Avoid wording like:

- You have scoliosis.
- You have a spinal disease.
- This detects medical conditions.
- This app diagnoses posture disorders.
- This treatment will fix your pain.

## Behavioral Guidelines

### 1. Think Before Coding

Before editing files:

- Restate the task briefly.
- State assumptions.
- Identify the files that need to be created or modified.
- If the requirement is ambiguous, ask before implementing.

Do not silently guess when there are multiple possible interpretations.

### 2. Simplicity First

Implement the minimum code needed for the MVP.

Do not add:

- Authentication
- Database
- Android code
- Complex segmentation
- Complex ML recommendation logic
- Background jobs
- User management
- Deployment automation
- Unrequested config systems

If the implementation becomes unnecessarily large, simplify it.

### 3. Surgical Changes

Touch only files directly related to the requested task.

When editing existing code:

- Do not refactor unrelated code.
- Do not reformat unrelated files.
- Do not rename unrelated files.
- Do not remove existing code unless the current task requires it.
- Remove only unused imports, variables, or functions created by your own changes.

Every changed line should directly support the requested task.

### 4. Goal-Driven Execution

Define success criteria before implementation.

For this MVP, success means:

- FastAPI app starts successfully.
- POST /analyze/image accepts an uploaded image.
- MediaPipe Pose runs on the image.
- The response contains all required JSON fields.
- If no pose is detected, the API returns a safe response with `landmark_detected: false`.
- README explains how to run locally and how to test with curl.
- Dockerfile can be used to build and run the backend.

### 5. Verify Before Completion

After implementation, run the most relevant available checks.

Preferred check:

```
python -m compileall .
```

If dependencies are installed:

```
uvicorn app.main:app --reload
```

If running commands is not possible, explain exactly what was not verified and why.

Do not say the task is complete unless verification was attempted or the limitation is clearly explained.

## Dependency Rule

Do not install packages without asking.

It is okay to create requirements.txt, but ask before running:

```
pip install
```

## Git Rule

Do not run:

```
git push
```

without asking.

It is okay to inspect git status or diff if needed.

## Final Response Format

After completing work, summarize:

```
Created/modified files:
- path
- path

What was implemented:
- item
- item

Verification:
- command run
- result

Notes:
- limitation or next step
```
