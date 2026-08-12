import argparse
import base64
import concurrent.futures
import gzip
import json
import os
import pathlib
import re
import urllib.parse
import urllib.request


CONFIG_MARKERS = ("proxies:", "proxy-providers:", "proxy-groups:")
BLOCKED_PREFIXES = ("剩余流量：", "套餐到期：")
SKIP_TYPES = {"selector", "urltest", "fallback", "direct", "reject"}
TEST_URLS = (
    "https://www.youtube.com/generate_204",
    "https://www.gstatic.com/generate_204",
)


def decode_config(payload):
    if not payload:
        return ""
    candidates = [payload]
    try:
        candidates.append(base64.b64decode(b"".join(payload.split()), validate=True))
    except Exception:
        pass
    for candidate in candidates:
        try:
            if candidate.startswith(b"\x1f\x8b"):
                candidate = gzip.decompress(candidate)
            text = candidate.decode("utf-8-sig").strip()
            if any(re.search(rf"(?m)^\s*{re.escape(marker)}", text) for marker in CONFIG_MARKERS):
                return text
        except Exception:
            continue
    return ""


def set_root_value(config, key, value):
    line = f"{key}: {value}"
    pattern = re.compile(rf"(?m)^{re.escape(key)}\s*:.*$")
    if pattern.search(config):
        return pattern.sub(line, config, count=1)
    return line + "\n" + config


def prepare_config(subscription_file, fallback_b64, output_file):
    subscription_payload = pathlib.Path(subscription_file).read_bytes() if subscription_file else b""
    config = decode_config(subscription_payload)
    source = "subscription"
    if not config:
        config = decode_config(fallback_b64.encode("utf-8"))
        source = "static fallback"
    if not config:
        raise SystemExit("No valid Mihomo YAML was found in subscription or static fallback.")
    config = set_root_value(config, "mixed-port", "7897")
    config = set_root_value(config, "external-controller", "127.0.0.1:9090")
    pathlib.Path(output_file).write_text(config + "\n", encoding="utf-8")
    print(f"mihomo_config_source={source}")


def request_json(controller, path, timeout=6):
    with urllib.request.urlopen(controller + path, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def selectable_group(proxies, preferred):
    preferred_item = proxies.get(preferred, {})
    if preferred_item.get("all"):
        return preferred
    groups = [
        (name, item)
        for name, item in proxies.items()
        if str(item.get("type", "")).lower() == "selector" and item.get("all")
    ]
    groups.sort(key=lambda pair: (pair[0].upper() == "GLOBAL", -len(pair[1].get("all", []))))
    if not groups:
        raise SystemExit("No selectable Mihomo proxy group was found.")
    return groups[0][0]


def proxy_candidates(proxies, group):
    items = proxies.get(group, {}).get("all", [])
    return [
        name
        for name in items
        if str(proxies.get(name, {}).get("type", "")).lower() not in SKIP_TYPES
        and not any(name.startswith(prefix) for prefix in BLOCKED_PREFIXES)
    ]


def measure_delay(controller, name):
    for url in TEST_URLS:
        query = urllib.parse.urlencode({"timeout": "5000", "url": url})
        path = "/proxies/" + urllib.parse.quote(name, safe="") + "/delay?" + query
        try:
            delay = request_json(controller, path).get("delay")
            if isinstance(delay, int) and delay > 0:
                return delay, name, url
        except Exception:
            continue
    return None, name, ""


def select_proxy(controller, preferred_group):
    proxies = request_json(controller, "/proxies").get("proxies", {})
    group = selectable_group(proxies, preferred_group)
    names = proxy_candidates(proxies, group)
    print(f"mihomo_group={group.encode('unicode_escape').decode('ascii')} candidates={len(names)}")
    if not names:
        raise SystemExit("Mihomo proxy group contains no usable node candidates.")
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(24, len(names))) as pool:
        results = list(pool.map(lambda name: measure_delay(controller, name), names))
    usable = sorted(result for result in results if isinstance(result[0], int))
    if not usable:
        types = sorted({str(proxies.get(name, {}).get("type", "unknown")) for name in names})
        raise SystemExit(
            f"No Mihomo nodes passed YouTube or gstatic delay tests; candidates={len(names)} types={types}."
        )
    delay, selected, test_url = usable[0]
    payload = json.dumps({"name": selected}).encode("utf-8")
    path = "/proxies/" + urllib.parse.quote(group, safe="")
    request = urllib.request.Request(
        controller + path,
        data=payload,
        method="PUT",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=6) as response:
        if response.status != 204:
            raise SystemExit(f"Failed to select Mihomo proxy: HTTP {response.status}")
    safe_name = selected.encode("unicode_escape").decode("ascii")
    print(f"selected_proxy={safe_name} delay_ms={delay} test_url={test_url}")


def parse_args():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("--subscription-file", default="")
    prepare.add_argument("--output", required=True)
    select = commands.add_parser("select")
    select.add_argument("--controller", default="http://127.0.0.1:9090")
    select.add_argument("--group", default="红杏云")
    return parser.parse_args()


def main():
    args = parse_args()
    if args.command == "prepare":
        prepare_config(
            args.subscription_file,
            os.environ.get("MIHOMO_CONFIG_GZ_B64", "").strip(),
            args.output,
        )
        return
    select_proxy(args.controller, args.group)


if __name__ == "__main__":
    main()
