import json
import os
import pathlib
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

import oss2

AUDIO_FORMAT = "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio[ext=mp3]/bestaudio"
COMMON_EXTS = (".m4a", ".mp4", ".webm", ".mp3", ".opus")


def env(name, fallback=""):
    return os.environ.get(name, fallback).strip()


def bridge_key(name):
    prefix = env("YOUTUBE_OSS_BRIDGE_PREFIX", "youtube-bridge").strip("/")
    return f"{prefix}/{name}" if prefix else name


def bucket():
    auth = oss2.Auth(env("ALIYUN_AK_ID"), env("ALIYUN_AK_SECRET"))
    return oss2.Bucket(auth, env("ALIYUN_OSS_ENDPOINT"), env("ALIYUN_OSS_BUCKET"))


def read_json(store, key, fallback):
    try:
        return json.loads(store.get_object(key).read().decode("utf-8"))
    except oss2.exceptions.NoSuchKey:
        return fallback


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def fetch_feed(channel_id, limit):
    url = f"https://www.youtube.com/feeds/videos.xml?channel_id={channel_id}"
    request = urllib.request.Request(url, headers={"User-Agent": "market-opinion-tracker/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        root = ET.fromstring(response.read())
    ns = {"atom": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}
    videos = []
    for entry in root.findall("atom:entry", ns)[:limit]:
        video_id = text(entry.find("yt:videoId", ns))
        if not video_id:
            continue
        videos.append(
            {
                "videoId": video_id,
                "title": text(entry.find("atom:title", ns)),
                "videoUrl": f"https://www.youtube.com/watch?v={video_id}",
                "publishedAt": text(entry.find("atom:published", ns)),
            }
        )
    return videos


def text(node):
    return "" if node is None or node.text is None else node.text.strip()


def existing_audio_key(store, video_id):
    for suffix in COMMON_EXTS:
        key = bridge_key(f"audio/{video_id}{suffix}")
        if store.object_exists(key):
            return key
    return ""


def duration_ms(video_url):
    command = [sys.executable, "-m", "yt_dlp", "--dump-single-json", "--no-playlist", video_url]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=90)
        return max(0, round(float(json.loads(result.stdout).get("duration") or 0) * 1000))
    except Exception:
        return 0


def download_audio(video):
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        output = root / f"{video['videoId']}.%(ext)s"
        command = [
            sys.executable,
            "-m",
            "yt_dlp",
            "--no-playlist",
            "--quiet",
            "--no-warnings",
            "-f",
            AUDIO_FORMAT,
            "-o",
            str(output),
            video["videoUrl"],
        ]
        subprocess.run(command, check=True, timeout=600)
        files = sorted(path for path in root.glob(f"{video['videoId']}.*") if not path.name.endswith(".part"))
        if not files:
            raise RuntimeError(f"audio missing: {video['videoId']}")
        path = files[0]
        return path.suffix or ".m4a", path.read_bytes()


def attach_audio(store, video):
    audio_key = existing_audio_key(store, video["videoId"])
    if not audio_key:
        suffix, payload = download_audio(video)
        audio_key = bridge_key(f"audio/{video['videoId']}{suffix}")
        store.put_object(audio_key, payload)
    video["audioObjectKey"] = audio_key
    video["audioDurationMs"] = duration_ms(video["videoUrl"])
    return video


def main():
    store = bucket()
    channel_doc = read_json(store, bridge_key("channels.json"), {"channels": []})
    max_videos = max(1, int(env("YOUTUBE_FETCH_MAX_VIDEOS", "1")))
    manifest = {"generatedAt": utc_now(), "channels": []}
    for channel in channel_doc.get("channels", []):
        channel_id = (channel.get("channelId") or "").strip()
        if not channel_id:
            continue
        fetched = []
        for video in fetch_feed(channel_id, max_videos):
            try:
                fetched.append(attach_audio(store, video))
            except Exception as error:
                print(f"skip {video.get('videoId')}: {error}", file=sys.stderr)
        manifest["channels"].append({**channel, "videos": fetched})
    payload = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    store.put_object(bridge_key("latest.json"), payload)
    print(f"channels={len(manifest['channels'])} videos={sum(len(c['videos']) for c in manifest['channels'])}")


if __name__ == "__main__":
    main()
