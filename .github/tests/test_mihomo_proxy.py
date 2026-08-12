import base64
import gzip
import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = pathlib.Path(__file__).parents[1] / "scripts" / "mihomo_proxy.py"
SPEC = importlib.util.spec_from_file_location("mihomo_proxy", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class MihomoProxyTest(unittest.TestCase):
    def test_prepare_prefers_subscription_and_sets_ports(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            subscription = root / "subscription.yaml"
            output = root / "mihomo.yaml"
            subscription.write_text("proxies: []\nmixed-port: 1\n", encoding="utf-8")
            MODULE.prepare_config(str(subscription), "", str(output))
            text = output.read_text(encoding="utf-8")
            self.assertIn("mixed-port: 7897", text)
            self.assertIn("external-controller: 127.0.0.1:9090", text)

    def test_prepare_falls_back_to_gzip_base64(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = pathlib.Path(tmp) / "mihomo.yaml"
            fallback = base64.b64encode(gzip.compress(b"proxy-groups: []\n")).decode()
            MODULE.prepare_config("", fallback, str(output))
            self.assertIn("proxy-groups: []", output.read_text(encoding="utf-8"))

    def test_candidates_exclude_groups_and_account_labels(self):
        proxies = {
            "红杏云": {"type": "Selector", "all": ["HK", "AUTO", "剩余流量： 0 GB"]},
            "HK": {"type": "Shadowsocks"},
            "AUTO": {"type": "URLTest"},
            "剩余流量： 0 GB": {"type": "Shadowsocks"},
        }
        self.assertEqual(["HK"], MODULE.proxy_candidates(proxies, "红杏云"))

    def test_group_falls_back_when_preferred_name_changes(self):
        proxies = {
            "GLOBAL": {"type": "Selector", "all": ["Node"]},
            "节点选择": {"type": "Selector", "all": ["Node", "Node2"]},
        }
        self.assertEqual("节点选择", MODULE.selectable_group(proxies, "missing"))


    def test_rotate_skips_current_and_failed_nodes(self):
        proxies = {
            "main": {"type": "Selector", "all": ["current", "failed", "ready"], "now": "current"},
            "current": {"type": "Vless"},
            "failed": {"type": "Vless"},
            "ready": {"type": "Hysteria2"},
        }
        with patch.object(MODULE, "request_json", return_value={"proxies": proxies}), patch.object(
            MODULE, "measure_delay", return_value=(12, "ready", MODULE.TEST_URLS[0])
        ), patch.object(MODULE.urllib.request, "urlopen") as urlopen:
            urlopen.return_value.__enter__.return_value.status = 204
            selected = MODULE.rotate_proxy("http://controller", "main", ["failed"])
        self.assertEqual("ready", selected)
        self.assertIn(b'"ready"', urlopen.call_args.args[0].data)


if __name__ == "__main__":
    unittest.main()
