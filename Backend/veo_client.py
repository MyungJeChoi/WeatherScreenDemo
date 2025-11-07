# veo_client.py
import time, os
from google import genai
from google.genai import types

def generate_clip(prompt: str, refs: list = None,
                  first_image=None, last_image=None,
                  resolution="1080p", aspect_ratio="9:16",
                  duration=8, out_path="out.mp4"):
    client = genai.Client()  # GEMINI_API_KEY from env
    cfg = types.GenerateVideosConfig(
        resolution=resolution, aspect_ratio=aspect_ratio, duration_seconds=str(duration)
    )

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

    video = operation.response.generated_videos[0]
    client.files.download(file=video.video)
    video.video.save(out_path)
    return out_path
