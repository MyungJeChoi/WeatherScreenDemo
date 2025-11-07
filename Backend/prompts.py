# prompts.py
SAFE_ZONE_NOTE = "bottom 20% of frame relatively static for lockscreen UI"

def build_prompt(subject: str, place: str, storyboard: dict):
    # 예: subject="yellow raincoat bear", place="Arc de Triomphe, Paris"
    story_type = storyboard["story_type"]
    
    weather_action = {
        "Prolonged rain":
            f"{subject} standing {place}, under continuous rain that lasts all day. "
            "The scene is covered with thick gray clouds, wet reflections on the ground, "
            "raindrops falling steadily, creating a calm and melancholic atmosphere. "
            "gentle motion.",
        "Intermittent showers":
            f"{subject} standing {place}, experiencing changing weather, alternating between brief showers and sunshine. "
            "Clouds move quickly across the sky, puddles form and dry, light flickers between bright and dim. "
            "dynamic lighting, expressive sky.",
        "Passing shower then clearing":
            f"{subject} standing {place}, after a short passing shower. "
            "The rain stops and sunlight breaks through the clouds, creating a rainbow in the distance. "
            "Wet surfaces shimmer, the air feels fresh and hopeful. "
            "soft sunlight, bright mood, post-rain glow.",
        "Showers":
            f"{subject} standing {place}, caught in a sudden heavy shower. "
            "Strong raindrops splash on the ground, dark clouds move fast, wind and thunder in the distance. "
            "Dramatic and moody atmosphere, energetic movement."
    }.get(story_type, f"{subject} in {place}, calm ambient loop.")
    
    style = ("Cinematic but subtle; loopable 7–8s clip; minimal camera motion; "
             "no text overlay; no cut; natural weather transition; "
             f"{SAFE_ZONE_NOTE}.")
    return f"{weather_action} {style}"

