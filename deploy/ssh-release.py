import argparse
import hashlib
import os
import posixpath
import sys
import time

import paramiko

SFTP_STALL_TIMEOUT_SECONDS = 120
UPLOAD_ATTEMPTS = 3
UPLOAD_CHUNK_SIZE = 128 * 1024
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
    result.add_argument("--runtime-env")
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
    client.get_transport().set_keepalive(15)
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


def file_key(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()[:10]


def upload(sftp, local_path, remote_path, resume=True):
    total_size = os.path.getsize(local_path)
    transferred = 0
    if resume:
        try:
            transferred = min(sftp.stat(remote_path).st_size, total_size)
        except OSError:
            transferred = 0
    print(
        f"upload {os.path.basename(local_path)} -> {remote_path} "
        f"({transferred}/{total_size})",
        flush=True,
    )
    reported_percent = -UPLOAD_PROGRESS_STEP

    def report_progress(current):
        nonlocal reported_percent
        percent = 100 if not total_size else int(current * 100 / total_size)
        if percent >= reported_percent + UPLOAD_PROGRESS_STEP or percent == 100:
            reported_percent = percent
            print(f"upload {os.path.basename(local_path)}: {percent}%", flush=True)

    report_progress(transferred)
    mode = "ab" if transferred else "wb"
    with open(local_path, "rb") as source, sftp.file(remote_path, mode) as target:
        source.seek(transferred)
        while chunk := source.read(UPLOAD_CHUNK_SIZE):
            target.write(chunk)
            transferred += len(chunk)
            report_progress(transferred)
    remote_size = sftp.stat(remote_path).st_size
    if remote_size != total_size:
        raise RuntimeError(f"Upload size mismatch: {remote_path} ({remote_size}/{total_size})")


def stage_files(args, remote_dir, files):
    last_error = None
    for attempt in range(1, UPLOAD_ATTEMPTS + 1):
        client = None
        try:
            print(f"SSH upload attempt {attempt}/{UPLOAD_ATTEMPTS}", flush=True)
            client = connect(args)
            run(client, f"mkdir -p {remote_dir}", timeout=60)
            sftp = client.open_sftp()
            try:
                sftp.get_channel().settimeout(SFTP_STALL_TIMEOUT_SECONDS)
                for local_path, remote_path, resume in files:
                    upload(sftp, local_path, remote_path, resume)
            finally:
                sftp.close()
            return client
        except Exception as error:
            last_error = error
            if client is not None:
                client.close()
            if attempt == UPLOAD_ATTEMPTS:
                raise
            print(f"SSH upload interrupted, retrying: {error}", file=sys.stderr, flush=True)
            time.sleep(attempt * 5)
    raise last_error


def main():
    args = parser().parse_args()
    release_key = "-".join(file_key(path) for path in (args.jar, args.archive, args.script))
    remote_dir = posixpath.join(args.remote_dir, release_key)
    remote_jar = posixpath.join(remote_dir, "market-opinion-tracker-0.1.0.jar")
    remote_archive = posixpath.join(remote_dir, "frontend-dist.tar.gz")
    remote_script = posixpath.join(remote_dir, "apply-release.sh")
    remote_mux = posixpath.join(remote_dir, "ssh_http_mux.py")
    remote_runtime_env = posixpath.join(remote_dir, "runtime.env")
    files = [
        (args.jar, remote_jar, True),
        (args.archive, remote_archive, True),
        (args.script, remote_script, True),
        (args.mux_script, remote_mux, True),
    ]
    if args.runtime_env:
        files.append((args.runtime_env, remote_runtime_env, False))

    client = stage_files(args, remote_dir, files)
    try:
        runtime_arg = f" {remote_runtime_env}" if args.runtime_env else ""
        if args.runtime_env:
            run(client, f"chmod 600 {remote_runtime_env}", timeout=30)
        try:
            run(
                client,
                f"bash {remote_script} {remote_jar} {remote_archive}{runtime_arg}",
                timeout=300,
            )
        finally:
            if args.runtime_env:
                try:
                    run(client, f"rm -f {remote_runtime_env}", timeout=30)
                except Exception:
                    pass
    finally:
        client.close()


if __name__ == "__main__":
    main()
