import importlib.util
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


SCRIPT = Path(__file__).parents[2] / "deploy" / "ssh-release.py"
SPEC = importlib.util.spec_from_file_location("ssh_release", SCRIPT)
ssh_release = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = ssh_release
SPEC.loader.exec_module(ssh_release)


class SshReleaseTest(unittest.TestCase):
    def test_upload_enables_pipelining_and_reports_speed(self):
        sftp = MagicMock()
        target = MagicMock()
        sftp.file.return_value.__enter__.return_value = target
        sftp.stat.return_value.st_size = 4

        with (
            patch.object(ssh_release.os.path, "getsize", return_value=4),
            patch("builtins.open", unittest.mock.mock_open(read_data=b"data")),
            patch.object(ssh_release.time, "monotonic", side_effect=[10.0, 12.0]),
            patch("builtins.print") as output,
        ):
            ssh_release.upload(sftp, "release.jar", "/tmp/release.jar", resume=False)

        target.set_pipelined.assert_called_once_with(True)
        target.write.assert_called_once_with(b"data")
        messages = [call.args[0] for call in output.call_args_list]
        self.assertTrue(any("complete:" in message and "MiB/s" in message for message in messages))

    def test_upload_skips_file_already_present(self):
        sftp = MagicMock()
        sftp.stat.return_value.st_size = 4

        with (
            patch.object(ssh_release.os.path, "getsize", return_value=4),
            patch("builtins.print") as output,
        ):
            ssh_release.upload(sftp, "release.jar", "/tmp/release.jar")

        sftp.file.assert_not_called()
        self.assertTrue(any("already complete" in call.args[0] for call in output.call_args_list))

    def test_upload_resumes_from_remote_size_with_pipelining(self):
        sftp = MagicMock()
        target = MagicMock()
        sftp.file.return_value.__enter__.return_value = target
        sftp.stat.side_effect = [MagicMock(st_size=2), MagicMock(st_size=4)]

        with (
            patch.object(ssh_release.os.path, "getsize", return_value=4),
            patch("builtins.open", return_value=io.BytesIO(b"data")),
            patch.object(ssh_release.time, "monotonic", side_effect=[10.0, 11.0]),
            patch("builtins.print"),
        ):
            ssh_release.upload(sftp, "release.jar", "/tmp/release.jar")

        sftp.file.assert_called_once_with("/tmp/release.jar", "r+b")
        target.set_pipelined.assert_called_once_with(True)
        target.seek.assert_called_once_with(2)
        target.write.assert_called_once_with(b"ta")


if __name__ == "__main__":
    unittest.main()
