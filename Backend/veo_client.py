# veo_client.py
import time, os
from google import genai
from google.genai import types

def generate_clip(prompt: str, refs: list = None,
                  first_image=None, last_image=None,
                  resolution="1080p", aspect_ratio="9:16",
                  duration=8, out_path="out.mp4"):
    api_key = os.getenv("GOOGLE_API_KEY")
    if not api_key:
        raise RuntimeError("Missing API key: set GOOGLE_API_KEY or GEMINI_API_KEY in .env")

    client = genai.Client(api_key=api_key) 
    cfg = types.GenerateVideosConfig(
        resolution=resolution, aspect_ratio=aspect_ratio, duration_seconds=str(duration)
    )
    
    print("start generating video...")
    # print("sleep start for 10 minutes")
    # time.sleep(600)
    # print("sleep end")

    if refs:
        cfg.reference_images = [
            types.VideoGenerationReferenceImage(image=img, reference_type="asset")
            for img in refs[:3]
        ]
    if first_image is not None and last_image is not None:
        operation = client.models.generate_videos(
            model="veo-3.1-generate-preview",
            prompt=prompt,
            image=first_image,
            config=types.GenerateVideosConfig(
                last_frame=last_image, resolution=resolution, aspect_ratio=aspect_ratio
            ),
        )
    else:
        operation = client.models.generate_videos(
            model="veo-3.1-generate-preview",
            prompt=prompt,
            config=cfg
        )
    
    while not operation.done:
        time.sleep(10)
        operation = client.operations.get(operation)
    print("operation done")
    video = operation.response.generated_videos[0]
    print("generated_videos[0] done")
    client.files.download(file=video.video)
    print("download done")
    video.video.save(out_path)
    print(f"video saved to: {out_path}")
    return out_path
