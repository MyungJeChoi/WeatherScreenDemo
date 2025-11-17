# app.py — FastAPI server that generates a video and serves it back
import os, datetime as dt
from time import time
from typing import Optional
from pathlib import Path

from fastapi import FastAPI, Request, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from dotenv import load_dotenv
from weather import fetch_hourly, build_storyboard
from prompts import build_prompt
from veo_client import generate_clip
from postprocess import make_lockscreen_friendly

load_dotenv()

app = FastAPI(title="WeatherScreen Backend", version="1.0.0")
jobs = {}
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class GenerateIn(BaseModel):
    prompt: Optional[str] = None
    aspect: str = "9:16"
    durationSec: int = 8
    lat: float = 37.5
    lon: float = 126.95
    subject: Optional[str] = None
    place: Optional[str] = None

class GenerateOut(BaseModel):
    jobId: str
    downloadUrl: str

@app.get("/ping")
def health():
    return {"ok": True, "now": dt.datetime.now().isoformat()}

@app.get("/env-check")
def env_check():
    return {
        "GOOGLE_API_KEY": bool(os.getenv("GOOGLE_API_KEY")),
        "GEMINI_API_KEY": bool(os.getenv("GEMINI_API_KEY")),
    }

@app.post("/generateVideo", response_model=GenerateOut)
def generate_video(req: GenerateIn, request: Request, background_tasks: BackgroundTasks):
    print("Lets make looplock!")

    current_time = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    jobs[current_time] = {"status": "queued"}  # 상태 등록

    # 백그라운드에서 실제 생성 실행
    background_tasks.add_task(_generate_async, current_time, req, str(request.base_url).rstrip("/"))

    # 요청 즉시 반환
    base = str(request.base_url).rstrip("/")
    return GenerateOut(jobId=current_time, downloadUrl=f"{base}/download/{current_time}")

# -----------------------------
# 실제 생성 로직 (비동기)
# -----------------------------
def _generate_async(job_id: str, req: GenerateIn, base: str):
    try:
        today = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime("%Y-%m-%d")
        hourly = fetch_hourly(req.lat, req.lon, today)
        storyboard = build_storyboard(hourly)
        subject = req.subject or "a cute small standing Pomeranian in a blue shirt"
        place = req.place or "the Arc de Triomphe in Paris"
        prompt = build_prompt(subject, place, storyboard)
        print(f"[{job_id}] prompt build done")

        out_dir = Path("output") / job_id
        out_dir.mkdir(parents=True, exist_ok=True)
        mp4_path = out_dir / "out.mp4"

        mp4_result = generate_clip(
            prompt,
            resolution="1080p",
            aspect_ratio=req.aspect,
            duration=req.durationSec,
            out_path=str(mp4_path),
        )

        print(f"[{job_id}] clip generation complete at: {mp4_result}")

        lock_path = out_dir / "lockscreen_raw.mp4"
        lock_result = make_lockscreen_friendly(
            str(mp4_path),
            dst=str(lock_path)
        )

        print(f"[{job_id}] postprocess done: {lock_result}")

        # 완료 표시
        jobs[job_id] = {"status": "ready", "path": lock_path}
        print(f"[{job_id}] job ready")

    except Exception as e:
        jobs[job_id] = {"status": "error", "detail": str(e)}
        print(f"[{job_id}] job failed: {e}")

@app.get("/status/{job_id}")
def status(job_id: str):
    return jobs.get(job_id, {"status": "unknown"})

# @app.post("/generateVideo", response_model=GenerateOut)
# def generate_video(req: GenerateIn, request: Request):
#     # fetch hourly weather and storyboard for *today* (KST)
#     print("Lets make looplock!")
#     today = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime("%Y-%m-%d")
#     hourly = fetch_hourly(req.lat, req.lon, today)
#     storyboard = build_storyboard(hourly)
#     subject = req.subject or "a cute small standing Pomeranian in a blue shirt"
#     place = req.place or "the Arc de Triomphe in Paris"
#     prompt = build_prompt(subject, place, storyboard)
#     print("prompt build done")

#     current_time = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
#     out_dir = os.path.join("output", current_time)
#     os.makedirs(out_dir, exist_ok=True)

#     # t0 = time()
#     mp4_path = generate_clip(prompt, resolution="1080p", aspect_ratio=req.aspect,
#                         duration=req.durationSec, out_path=os.path.join(out_dir, "out.mp4"))
#     # print(f"Clip making time: {time()-t0:.2f}s")

#     _ = make_lockscreen_friendly(mp4_path, dst=os.path.join(out_dir, "lockscreen_raw.mp4"))
#     # Build absolute download URL
#     base = str(request.base_url).rstrip("/")
#     return GenerateOut(jobId=current_time, downloadUrl=f"{base}/download/{current_time}")

@app.get("/download/{job_id}")
def download(job_id: str):
    job = jobs.get(job_id)
    if not job or job.get("status") != "ready":
        return {"error": "Not ready yet"}
    path = job["path"]
    if not os.path.exists(path):
        return {"error": "File not found"}
    return FileResponse(path, media_type="video/mp4", filename="lockscreen_raw.mp4")

if __name__ == "__main__":
    # Optional: run with uvicorn when executed directly
    import uvicorn
    port = int(os.getenv("PORT", "8750"))
    uvicorn.run("app:app", host="0.0.0.0", port=port, reload=False)
