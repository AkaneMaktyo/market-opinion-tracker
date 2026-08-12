import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


SCRIPT = Path(__file__).parents[2] / "deploy" / "apk-upload.py"
SPEC = importlib.util.spec_from_file_location("apk_upload", SCRIPT)
apk_upload = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = apk_upload
SPEC.loader.exec_module(apk_upload)


class ApkUploadTest(unittest.TestCase):
    def test_main_retries_interrupted_publish(self):
        args = Mock()
        with (
            patch.object(apk_upload, "parse_args", return_value=args),
            patch.object(
                apk_upload,
                "upload_once",
                side_effect=[ConnectionResetError("reset"), None],
            ) as upload_once,
            patch.object(apk_upload.time, "sleep") as sleep,
        ):
            apk_upload.main()

        self.assertEqual(upload_once.call_count, 2)
        sleep.assert_called_once_with(5)

    def test_upload_uses_temporary_files_before_replace(self):
        args = Mock(
            apk="release.apk",
            json="apk.json",
            remote_dir="/srv/apk",
            apk_name="market-opinion-tracker-4.apk",
        )
        client = Mock()
        sftp = Mock()
        client.open_sftp.return_value = sftp
        sftp.stat.side_effect = [Mock(st_size=10), Mock(st_size=5)]

        with (
            patch.object(apk_upload, "connect", return_value=client),
            patch.object(apk_upload, "run") as run,
            patch.object(apk_upload.os.path, "getsize", side_effect=[10, 5]),
        ):
            apk_upload.upload_once(args)

        self.assertEqual(
            [call.args[1] for call in sftp.put.call_args_list],
            [
                "/srv/apk/market-opinion-tracker-4.apk.upload",
                "/srv/apk/apk.json.upload",
            ],
        )
        publish_command = run.call_args_list[-1].args[1]
        self.assertIn("mv -f /srv/apk/market-opinion-tracker-4.apk.upload", publish_command)
        self.assertIn("mv -f /srv/apk/apk.json.upload", publish_command)


if __name__ == "__main__":
    unittest.main()
