# weather.py
import requests, datetime as dt
from collections import namedtuple

OPEN_METEO = "https://api.open-meteo.com/v1/forecast"

def fetch_hourly(lat: float, lon: float, date: str):
    params = {
        "latitude": lat, "longitude": lon, "timezone": "Asia/Seoul",
        "start_date": date, "end_date": date,
        "hourly": "precipitation_probability,weathercode,cloudcover"
    }
    # if hour > 21: 
    #     params["start_date"] = params["end_date"] = tomorrow
        
    r = requests.get(OPEN_METEO, params=params, timeout=10)
    r.raise_for_status()
    data = r.json()["hourly"]
    return data  # dict of lists

def detect_transition(hourly, p_rain_hi=50, p_rain_lo=20):
    # 매우 단순한 휴리스틱: 어떤 시각 H에서는 비 가능성↑, H+1에서는 ↓ 이면 'RAIN->CLEAR'
    probs = hourly["precipitation_probability"]
    for i in range(len(probs) - 1):
        if probs[i] >= p_rain_hi and probs[i+1] <= p_rain_lo:
            return ("RAIN", "CLEAR", i)
    # 없으면 구름→맑음 후보
    clouds = hourly["cloudcover"]
    for i in range(len(clouds)-1):
        if clouds[i] >= 70 and clouds[i+1] <= 30:
            return ("CLOUDY", "SUNNY", i)
    return ("SUNNY", "SUNNY", 0)


RAIN_CODES = {51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99}
Episode = namedtuple("Episode", "start end duration kind max_intensity")

def build_storyboard(hourly):
    # hourly: dict with keys ["time", "precipitation_probability", "weathercode", "cloudcover"]
    times  = [dt.datetime.fromisoformat(t) for t in hourly["time"]]
    pprob  = hourly.get("precipitation_probability", [0]*len(times))
    wcode  = hourly.get("weathercode", [0]*len(times))
    cloud  = hourly.get("cloudcover", [0]*len(times))
    rainmm = hourly.get("rain", None)  # optional

    def is_rain(i):
        code_flag = wcode[i] in RAIN_CODES
        prob_flag = pprob[i] >= 50
        return code_flag or prob_flag

    # 1) 연속 비 시간대 → 에피소드
    episodes = []
    i = 0
    while i < len(times):
        if not is_rain(i):
            i += 1
            continue
        j = i + 1
        # 연속 구간 확장
        while j < len(times) and is_rain(j):
            j += 1
        # 병합 허용 간격(<=1h) 확인
        k = j
        while k+1 < len(times) and (times[k] - times[k-1]).seconds == 3600 and not is_rain(k):
            # 1시간 공백이면 병합 후보
            if (times[k+1] - times[k]).seconds == 3600 and is_rain(k+1):
                # 병합
                k += 1
                while k+1 < len(times) and is_rain(k+1):
                    k += 1
                j = k + 1
                break
            else:
                break

        # 에피소드 기록
        start, end = times[i], times[j-1]
        duration_h = int((end - start).seconds/3600) + 1  # 포함형
        # 강도
        max_int = None
        if rainmm is not None:
            max_int = max(rainmm[i:j]) if j > i else 0
            if   max_int >= 4: kind = "prolonged-strong"
            elif max_int >= 2: kind = "moderate"
            else:               kind = "light"
        else:
            heavy_codes = {65,75,82,99}
            kind = "prolonged-strong" if any(c in heavy_codes for c in wcode[i:j]) else "light/moderate"

        episodes.append(Episode(start, end, duration_h, kind, max_int))
        i = j

    total_rain_h = sum(ep.duration for ep in episodes)

    # 2) 하루 요약 타입
    story_type = "Dry day"
    if total_rain_h < 1:
        story_type = "Dry day"
    elif any(ep.duration >= 4 for ep in episodes) or total_rain_h >= 6:
        story_type = "Prolonged rain"
    elif len(episodes) >= 2 and 2 <= total_rain_h <= 4:
        story_type = "Intermittent showers"
    else:
        # 2h 미만 한 번이고 이후 맑아짐(40% 이하)이면 Passing→Clearing
        last_rain_end = max((ep.end for ep in episodes), default=None)
        clears = [c for t,c in zip(times, cloud) if last_rain_end and t > last_rain_end]
        if episodes and len(episodes) == 1 and episodes[0].duration < 2 and clears and min(clears) < 40:
            story_type = "Passing shower then clearing"
        else:
            story_type = "Showers"

    # 3) 씬(아침/오후/저녁) 요약
    def dominant(block_start, block_end):
        idx = [i for i,t in enumerate(times) if block_start <= t.hour <= block_end]
        if not idx: return "—"
        rain_hours = sum(1 for i in idx if is_rain(i))
        avg_cloud = sum(cloud[i] for i in idx)/len(idx)
        if rain_hours/len(idx) >= 0.5:  # 절반 이상 비
            return "Rain"
        if avg_cloud >= 70:
            return "Overcast"
        if avg_cloud >= 40:
            return "Cloudy"
        return "Clear"

    scenes = [
        ("Morning",   dominant(0, 11)),
        ("Afternoon", dominant(12, 17)),
        ("Evening",   dominant(18, 23)),
    ]

    # 4) 뇌우 효과 플래그
    stormy = any(c in {95,96,99} for c in wcode)

    return {
        "story_type": story_type,
        "episodes":   [ep._asdict() for ep in episodes],
        "scenes":     scenes,
        "stormy":     stormy,
    }