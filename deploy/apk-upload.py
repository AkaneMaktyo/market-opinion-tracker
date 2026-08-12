import argparse
import os
import posixpath
import shlex
import sys
import time

import paramiko

UPLOAD_ATTEMPTS = 8


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--user", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--json", required=True)
    parser.add_argument("--remote-dir", required=True)
    parser.add_argument("--apk-name", required=True)
    return parser.parse_args()


def connect(args):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        args.host,
        port=args.port,
        username=args.user,
        password=os.environ["MOT_SSH_PASSWORD"],
        timeout=30,
        banner_timeout=30,
        auth_timeout=30,
    )
    client.get_transport().set_keepalive(15)
    return client


def run(client, command, timeout=120):
    _, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip(), flush=True)
    if err.strip():
        print(err.strip(), file=sys.stderr, flush=True)
    if code != 0:
        raise RuntimeError(f"Remote command failed ({code}): {command}")


def upload_once(args):
    client = connect(args)
    apk_name = args.apk_name
    json_name = "apk.json"
    remote_apk = posixpath.join(args.remote_dir, apk_name)
    remote_json = posixpath.join(args.remote_dir, json_name)
    temp_apk = f"{remote_apk}.upload"
    temp_json = f"{remote_json}.upload"
    try:
        run(client, f"mkdir -p {shlex.quote(args.remote_dir)}")
        sftp = client.open_sftp()
        try:
            sftp.get_channel().settimeout(120)
            for local_path, remote_path in ((args.apk, temp_apk), (args.json, temp_json)):
                print(f"upload {os.path.basename(local_path)}", flush=True)
                sftp.put(local_path, remote_path, confirm=True)
                if sftp.stat(remote_path).st_size != os.path.getsize(local_path):
                    raise RuntimeError(f"Upload size mismatch: {remote_path}")
        finally:
            sftp.close()
        command = " && ".join(
            (
                f"chmod 644 {shlex.quote(temp_apk)} {shlex.quote(temp_json)}",
                f"mv -f {shlex.quote(temp_apk)} {shlex.quote(remote_apk)}",
                f"mv -f {shlex.quote(temp_json)} {shlex.quote(remote_json)}",
                f"ls -l {shlex.quote(remote_apk)} {shlex.quote(remote_json)}",
            )
        )
        run(client, command)
    finally:
        client.close()


def main():
    args = parse_args()
    for attempt in range(1, UPLOAD_ATTEMPTS + 1):
        try:
            print(f"SSH publish attempt {attempt}/{UPLOAD_ATTEMPTS}", flush=True)
            upload_once(args)
            return
        except Exception as error:
            if attempt == UPLOAD_ATTEMPTS:
                raise
            print(f"SSH publish interrupted, retrying: {error}", file=sys.stderr, flush=True)
            time.sleep(attempt * 5)


if __name__ == "__main__":
    main()
