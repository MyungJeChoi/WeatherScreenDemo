# postprocess.py
import ffmpeg, os

def make_lockscreen_friendly(src, dst="lockscreen.mp4", vcodec="libx264"):
    # 무음(오디오 스트림 제거), 빠른 시킹, 합리적 비트레이트(예: 4~6Mbps)로 리패키징
    stream = ffmpeg.input(src)
    out = ffmpeg.output(stream.video, dst,
                        vcodec=vcodec, video_bitrate="5M",
                        movflags="+faststart", an=None)
    ffmpeg.run(out, overwrite_output=True)
    return dst
