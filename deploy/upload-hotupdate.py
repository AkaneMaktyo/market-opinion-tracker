import os, sys, paramiko, time, tarfile, io, glob

HOST = '103.236.98.149'
PORT = 29453
USER = 'root'
PASSWORD = os.environ.get('MOT_SSH_PASSWORD', 'Love512914')
REMOTE_DIR = '/var/www/market-opinion-tracker/market'
LOCAL_DIST = r'D:\_code\personal\market-opinion-tracker\frontend\dist'

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

sftp = client.open_sftp()

# List local files to upload
files_to_upload = []
for root, dirs, files in os.walk(LOCAL_DIST):
    for f in files:
        local_path = os.path.join(root, f)
        rel_path = os.path.relpath(local_path, LOCAL_DIST).replace('\\', '/')
        remote_path = f'{REMOTE_DIR}/{rel_path}'
        files_to_upload.append((local_path, remote_path))

print(f'共 {len(files_to_upload)} 个文件需要上传')

# Create remote directories
dirs = set()
for _, remote in files_to_upload:
    dirs.add(os.path.dirname(remote))
for d in sorted(dirs):
    try:
        sftp.mkdir(d)
    except:
        pass
    # Ensure ownership
    try:
        client.exec_command(f'chown www-data:www-data {d} 2>/dev/null; chmod 755 {d} 2>/dev/null')
    except:
        pass

# Upload files
success = 0
failed = 0
for local, remote in files_to_upload:
    try:
        sftp.put(local, remote)
        # Set ownership
        stdin, stdout, stderr = client.exec_command(f'chown www-data:www-data {remote}')
        stdout.read()
        success += 1
        if success % 5 == 0:
            print(f'  已上传 {success}/{len(files_to_upload)}...')
    except Exception as e:
        print(f'  失败: {local} -> {remote}: {e}')
        failed += 1

print(f'上传完成: {success} 成功, {failed} 失败')

# Verify manifest
stdin, stdout, stderr = client.exec_command(f'cat {REMOTE_DIR}/live-update.json')
manifest_content = stdout.read().decode('utf-8')
print(f'远程 live-update.json:\n{manifest_content}')

# Verify updates dir
stdin, stdout, stderr = client.exec_command(f'ls -la {REMOTE_DIR}/updates/')
print(f'远程 updates:\n{stdout.read().decode("utf-8")}')

# Nginx reload
stdin, stdout, stderr = client.exec_command('nginx -t 2>&1 && nginx -s reload 2>&1')
result = stdout.read().decode('utf-8') + stderr.read().decode('utf-8')
print(f'Nginx reload:\n{result}')

# Clean old updates (keep only latest 3)
stdin, stdout, stderr = client.exec_command(f'ls -t {REMOTE_DIR}/updates/web-*.zip | tail -n +4 | xargs -r rm -v 2>&1')
clean_result = stdout.read().decode('utf-8') + stderr.read().decode('utf-8')
print(f'清理旧包:\n{clean_result}')

sftp.close()
client.close()
print('\n部署完成!')
