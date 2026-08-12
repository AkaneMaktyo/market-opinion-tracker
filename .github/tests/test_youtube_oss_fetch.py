import importlib.util
import pathlib
import sys
import types
import unittest
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).parents[1] / "scripts" / "youtube_oss_fetch.py"
sys.modules.setdefault("oss2", types.SimpleNamespace(exceptions=types.SimpleNamespace(NoSuchKey=Exception)))
SPEC = importlib.util.spec_from_file_location("youtube_oss_fetch", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class YouTubeOssFetchTest(unittest.TestCase):
    def test_timestamp_iso(self):
        self.assertEqual("2026-08-11T00:00:00+00:00", MODULE.timestamp_iso(1786406400))
        self.assertEqual("", MODULE.timestamp_iso(None))

    def test_feed_result_is_preferred(self):
        feed = [{"videoId": "feed"}]
        with patch.object(MODULE, "fetch_feed", return_value=feed), patch.object(
            MODULE, "fetch_channel_page"
        ) as fallback:
            self.assertEqual(feed, MODULE.discover_videos("UC123", 3))
            fallback.assert_not_called()

    def test_http_feed_failure_uses_channel_page(self):
        failure = MODULE.urllib.error.HTTPError("url", 404, "missing", {}, None)
        fallback = [{"videoId": "page"}]
        with patch.object(MODULE, "fetch_feed", side_effect=failure), patch.object(
            MODULE, "fetch_channel_page", return_value=fallback
        ):
            self.assertEqual(fallback, MODULE.discover_videos("UC123", 3))


if __name__ == "__main__":
    unittest.main()
