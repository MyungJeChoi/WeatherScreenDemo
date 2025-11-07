# app.py (CLI 예시)
import argparse, os
from dotenv import load_dotenv
from weather import fetch_hourly, detect_transition, build_storyboard
from prompts import build_prompt
from veo_client import generate_clip
from postprocess import make_lockscreen_friendly
import datetime as dt
from time import time

def main():
    load_dotenv()
    ap = argparse.ArgumentParser()
    ap.add_argument("--lat", type=float, default=37.5)
    ap.add_argument("--lon", type=float, default=126.95)
    ap.add_argument("--subject", default="a cute small standing Pomeranian in a blue shirt")
    ap.add_argument("--place", default="the Arc de Triomphe in Paris")
    args = ap.parse_args()

    today = dt.date.today().isoformat()
    hourly = fetch_hourly(args.lat, args.lon, today)
    storyboard = build_storyboard(hourly)
    
    prompt = build_prompt(args.subject, args.place, storyboard)
    current_time = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    os.makedirs(f"output/{current_time}")
    t0 = time()
    mp4 = generate_clip(prompt, resolution="1080p", aspect_ratio="9:16",
                        out_path=f"output/{current_time}/out.mp4")
    print(f"Clip making time: {time() - t0:2f}")
    out = make_lockscreen_friendly(mp4, dst=f"output/{current_time}/lockscreen.mp4")
    print(f"[OK] Saved: {out}")

if __name__ == "__main__":
    main()
