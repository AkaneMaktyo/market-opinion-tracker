import argparse
import os
import posixpath
import sys

import paramiko

SFTP_STALL_TIMEOUT_SECONDS = 300
UPLOAD_PROGRESS_STEP = 10


def parser():
    result = argparse.ArgumentParser()
    result.add_argument("--host", required=True)
    result.add_argument("--port", type=int, required=True)
    result.add_argument("--user", required=True)
    result.add_argument("--remote-dir", required=True)
    result.add_argument("--jar", required=True)
    result.add_argument("--archive", required=True)
    result.add_argument("--script", required=True)
    result.add_argument("--mux-script", required=True)
    return result


def connect(args):
    password = os.environ.get("MOT_SSH_PASSWORD", "")
    if not password:
        raise RuntimeError("Missing MOT_SSH_PASSWORD.")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        args.host,
        port=args.port,
        username=args.user,
        password=password,
        timeout=30,
        banner_timeout=30,
        auth_timeout=30,
    )
    return client


def run(client, command, timeout=300):
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out:
        print(out, end="")
    if err:
        print(err, end="", file=sys.stderr)
    if code != 0:
        raise RuntimeError(f"Remote command failed ({code}): {command}")


def upload(sftp, local_path, remote_path):
    print(f"upload {os.path.basename(local_path)} -> {remote_path}")
    total_size = os.path.getsize(local_path)
    reported_percent = -UPLOAD_PROGRESS_STEP

    def report_progress(transferred, total):
        nonlocal reported_percent
        expected_total = total or total_size
        percent = 100 if not expected_total else int(transferred * 100 / expected_total)
        if percent >= reported_percent + UPLOAD_PROGRESS_STEP or percent == 100:
            reported_percent = percent
            print(f"upload {os.path.basename(local_path)}: {percent}%")

    sftp.put(local_path, remote_path, callback=report_progress)


def main():
    args = parser().parse_args()
    remote_jar = posixpath.join(args.remote_dir, "market-opinion-tracker-0.1.0.jar")
    remote_archive = posixpath.join(args.remote_dir, "frontend-dist.tar.gz")
    remote_script = posixpath.join(args.remote_dir, "apply-release.sh")
    remote_mux = posixpath.join(args.remote_dir, "ssh_http_mux.py")

    client = connect(args)
    try:
        run(client, f"mkdir -p {args.remote_dir}", timeout=60)
        sftp = client.open_sftp()
        try:
            sftp.get_channel().settimeout(SFTP_STALL_TIMEOUT_SECONDS)
            upload(sftp, args.jar, remote_jar)
            upload(sftp, args.archive, remote_archive)
            upload(sftp, args.script, remote_script)
            upload(sftp, args.mux_script, remote_mux)
        finally:
            sftp.close()
        run(client, f"bash {remote_script} {remote_jar} {remote_archive}", timeout=300)
    finally:
        client.close()


if __name__ == "__main__":
    main()
