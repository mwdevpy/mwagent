"""Downloads videos, audio and pictures from links: TikTok directly, everything else through yt-dlp.

Each call returns a JSON string. When a site serves video and audio separately (YouTube), both files are
downloaded and the app joins them with Android's MediaMuxer, since there is no ffmpeg on the phone.
"""

import http.cookiejar
import json
import os
import re
import ssl
import urllib.parse
import urllib.request

UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/130.0.0.0 Mobile Safari/537.36")
TIKTOK_HOSTS = ("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
CHUNK = 1 << 16
UNSAFE = re.compile(r'[\\/:*?"<>|\r\n\t]+')


class TooLarge(Exception):
    pass


def _safe(name, limit=80):
    name = UNSAFE.sub(" ", name or "").strip(" .")
    return name[:limit].rstrip(" .") or "video"


def download(url, folder, audio_only=False, max_height=1080, max_mb=500):
    try:
        os.makedirs(folder, exist_ok=True)
        host = (urllib.parse.urlparse(url).hostname or "").lower()
        if host in TIKTOK_HOSTS:
            result = _tiktok(url, folder, bool(audio_only), int(max_mb))
        else:
            result = _ytdlp(url, folder, bool(audio_only), int(max_height), int(max_mb))
        return json.dumps(result, ensure_ascii=False)
    except TooLarge as error:
        return json.dumps({"error": str(error)})
    except Exception as error:
        message = re.sub(r"\x1b\[[0-9;]*m", "", str(error)).replace("ERROR: ", "")
        return json.dumps({"error": "%s: %s" % (type(error).__name__, message[:500])}, ensure_ascii=False)


# ---- TikTok ----

def _tls():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()


def _opener():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), urllib.request.HTTPSHandler(context=_tls()))
    opener.addheaders = [("User-Agent", UA), ("Accept", "*/*"), ("Accept-Language", "en-US,en;q=0.9")]
    return opener


def _fetch_to(opener, url, path, max_mb, referer):
    request = urllib.request.Request(url, headers={"Referer": referer})
    limit = max_mb * 1024 * 1024
    with opener.open(request, timeout=60) as response:
        size = int(response.headers.get("Content-Length") or 0)
        if size > limit:
            raise TooLarge("The file is %d MB, more than the %d MB limit" % (size >> 20, max_mb))
        written = 0
        with open(path, "wb") as out:
            while True:
                chunk = response.read(CHUNK)
                if not chunk:
                    break
                written += len(chunk)
                if written > limit:
                    break
                out.write(chunk)
    if written > limit:
        os.remove(path)
        raise TooLarge("The file is larger than the %d MB limit" % max_mb)
    return path


def _script_json(html, script_id):
    match = re.search(r'<script[^>]+id="%s"[^>]*>(.*?)</script>' % re.escape(script_id), html, re.S)
    if not match:
        return {}
    try:
        return json.loads(match.group(1))
    except ValueError:
        return {}


def _tiktok_detail(html):
    """The video details, from the page layout TikTok serves now (api-data) or the older one (rehydration data)."""
    detail = _script_json(html, "api-data").get("videoDetail")
    if detail:
        return detail
    scope = _script_json(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__").get("__DEFAULT_SCOPE__", {})
    return scope.get("webapp.video-detail") or {}


def _tiktok(url, folder, audio_only, max_mb):
    opener = _opener()
    with opener.open(url, timeout=30) as response:
        page_url = response.geturl()
        html = response.read().decode("utf-8", "replace")
    detail = _tiktok_detail(html)
    item = (detail.get("itemInfo") or {}).get("itemStruct")
    if not item:
        message = detail.get("statusMsg") or detail.get("statusMessage")
        raise RuntimeError("TikTok says: %s" % message if message else
                           "TikTok did not return the video; it may be private, deleted or blocked in your region")
    author = (item.get("author") or {}).get("uniqueId") or ""
    base = os.path.join(folder, "%s [%s]" % (_safe(item.get("desc") or "tiktok"), item.get("id", "")))
    music = (item.get("music") or {}).get("playUrl")
    files = []
    if audio_only:
        if not music:
            raise RuntimeError("This TikTok has no separate sound to download")
        files.append(_fetch_to(opener, music, base + ".mp3", max_mb, page_url))
    elif item.get("imagePost"):
        for index, image in enumerate(item["imagePost"].get("images") or [], 1):
            links = (image.get("imageURL") or {}).get("urlList") or []
            if links:
                files.append(_fetch_to(opener, links[0], "%s %02d.jpg" % (base, index), max_mb, page_url))
        if music:
            files.append(_fetch_to(opener, music, base + ".mp3", max_mb, page_url))
    else:
        video = item.get("video") or {}
        candidates = [link for rate in video.get("bitrateInfo") or [] for link in (rate.get("PlayAddr") or {}).get("UrlList") or []]
        candidates += [video.get("playAddr"), video.get("downloadAddr")]
        last = None
        for link in [c for c in candidates if c]:
            try:
                files.append(_fetch_to(opener, link, base + ".mp4", max_mb, page_url))
                break
            except TooLarge:
                raise
            except Exception as error:
                last = error
        if not files:
            raise RuntimeError("Could not download the TikTok video: %s" % last)
    return {"title": item.get("desc") or "", "author": author, "site": "TikTok", "files": files}


# ---- Everything else: yt-dlp ----

def _has(fmt, key):
    return (fmt.get(key) or "none") != "none"


def _ytdlp(url, folder, audio_only, max_height, max_mb):
    import yt_dlp

    base = {
        "outtmpl": os.path.join(folder, "%(title).80B [%(id)s].f%(format_id)s.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "js_runtimes": {},
        "max_filesize": max_mb * 1024 * 1024,
        "windowsfilenames": True,
    }
    with yt_dlp.YoutubeDL(base) as probe:
        info = probe.extract_info(url, download=False)
    if info.get("_type") == "playlist":
        entries = [e for e in info.get("entries") or [] if e]
        if not entries:
            raise RuntimeError("The link is a playlist without videos")
        info = entries[0]
    formats = info.get("formats") or [info]

    separate_video = any(_has(f, "vcodec") and not _has(f, "acodec") and f.get("ext") == "mp4"
                         and (f.get("vcodec") or "").startswith("avc1") for f in formats)
    separate_audio = any(f.get("ext") == "m4a" and _has(f, "acodec") and not _has(f, "vcodec") for f in formats)
    if audio_only:
        plan = ["ba[ext=m4a]/ba/b"]
    elif separate_video and separate_audio:
        plan = ["bv*[ext=mp4][vcodec^=avc1][height<=%d]/bv*[ext=mp4][vcodec^=avc1]" % max_height, "ba[ext=m4a]"]
    else:
        plan = ["b[ext=mp4][height<=%d]/b[height<=%d]/b" % (max_height, max_height)]

    files = []
    for spec in plan:
        # A fresh extraction per stream: YouTube links stop working when reused across sessions.
        with yt_dlp.YoutubeDL(dict(base, format=spec)) as ydl:
            done = ydl.extract_info(info.get("webpage_url") or url, download=True)
        found = [d.get("filepath") for d in done.get("requested_downloads") or []]
        found = [path for path in found if path and os.path.exists(path)]
        if not found:
            raise TooLarge("Nothing was downloaded; the file may be larger than the %d MB limit" % max_mb)
        files += found
    result = {"title": info.get("title") or "", "author": info.get("uploader") or "",
              "site": info.get("extractor_key") or "", "duration": info.get("duration")}
    if len(plan) == 2:
        result["merge"] = {"video": files[0], "audio": files[1],
                           "output": os.path.join(folder, "%s [%s].mp4" % (_safe(info.get("title")), info.get("id", "")))}
        result["files"] = []
    else:
        result["files"] = files
    return result
