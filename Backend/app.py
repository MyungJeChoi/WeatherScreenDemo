# app.py — FastAPI server that generates a video and serves it back
import os, datetime as dt
from time import time
from typing import Optional

from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from dotenv import load_dotenv
from weather import fetch_hourly, detect_transition, build_storyboard
from prompts import build_prompt
from veo_client import generate_clip
from postprocess import make_lockscreen_friendly

load_dotenv()

app = FastAPI(title="WeatherScreen Backend", version="1.0.0")
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

@app.get("/health")
def health():
    return {"ok": True, "now": dt.datetime.now().isoformat()}

@app.post("/generateVideo", response_model=GenerateOut)
def generate_video(req: GenerateIn, request: Request):
    # fetch hourly weather and storyboard for *today* (KST)
    today = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).strftime("%Y-%m-%d")
    hourly = fetch_hourly(req.lat, req.lon, today)
    storyboard = build_storyboard(hourly)
    subject = req.subject or "a cute small standing Pomeranian in a blue shirt"
    place = req.place or "the Arc de Triomphe in Paris"
    prompt = build_prompt(subject, place, storyboard)

    current_time = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir = os.path.join("output", current_time)
    os.makedirs(out_dir, exist_ok=True)

    # t0 = time()
    mp4_path = generate_clip(prompt, resolution="1080p", aspect_ratio=req.aspect,
                        duration=req.durationSec, out_path=os.path.join(out_dir, "out.mp4"))
    # print(f"Clip making time: {time()-t0:.2f}s")

    _ = make_lockscreen_friendly(mp4_path, dst=os.path.join(out_dir, "lockscreen.mp4"))
    # Build absolute download URL
    base = str(request.base_url).rstrip("/")
    return GenerateOut(jobId=current_time, downloadUrl=f"{base}/download/{current_time}")

@app.get("/download/{job_id}")
def download(job_id: str):
    path = os.path.join("output", job_id, "lockscreen.mp4")
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="Not found")
    return FileResponse(path, media_type="video/mp4", filename="lockscreen.mp4")

if __name__ == "__main__":
    # Optional: run with uvicorn when executed directly
    import uvicorn
    port = int(os.getenv("PORT", "28410"))
    uvicorn.run("app:app", host="0.0.0.0", port=port, reload=False)
