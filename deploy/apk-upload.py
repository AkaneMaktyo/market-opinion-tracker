import argparse
import os
import paramiko

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', required=True)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--user', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--json', required=True)
    parser.add_argument('--remote-dir', required=True)
    args = parser.parse_args()

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(args.host, port=args.port, username=args.user,
                   password=os.environ['MOT_SSH_PASSWORD'], timeout=15)
    try:
        sftp = client.open_sftp()
        try:
            run(client, f'mkdir -p {args.remote_dir}')
            sftp.put(args.apk, f'{args.remote_dir}/market-opinion-tracker.apk')
            sftp.put(args.json, f'{args.remote_dir}/apk.json')
        finally:
            sftp.close()
        run(client, f'chmod 644 {args.remote_dir}/market-opinion-tracker.apk {args.remote_dir}/apk.json')
        run(client, f'ls -l {args.remote_dir}')
    finally:
        client.close()


def run(client, command, timeout=60):
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode(errors='replace')
    err = stderr.read().decode(errors='replace')
    if out.strip():
        print(out.strip())
    if err.strip():
        print('[err]', err.strip())
    code = stdout.channel.recv_exit_status()
    if code != 0:
        raise SystemExit(f'命令失败({code}): {command}')


if __name__ == '__main__':
    main()
