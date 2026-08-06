import { spawnSync } from 'node:child_process';
import {
  createHash,
  createPrivateKey,
  createPublicKey,
  createSign,
} from 'node:crypto';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

function readArgs(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    const key = values[index]?.replace(/^--/, '');
    if (!key || !values[index + 1]) throw new Error(`参数不完整：${values[index]}`);
    result[key] = values[index + 1];
  }
  return result;
}

function normalizePublicKey(pem) {
  return pem.replace(/-----[^-]+-----|\s/g, '');
}

function readPrivateKey() {
  if (process.env.LIVE_UPDATE_PRIVATE_KEY_B64) {
    return Buffer.from(process.env.LIVE_UPDATE_PRIVATE_KEY_B64, 'base64').toString('utf8');
  }
  const fallback = join(process.env.USERPROFILE || '', '.market-opinion-tracker', 'live-update-private.pem');
  const keyPath = process.env.MOT_LIVE_UPDATE_PRIVATE_KEY_PATH || fallback;
  if (!keyPath || !existsSync(keyPath)) {
    throw new Error('未找到在线更新签名私钥。');
  }
  return readFileSync(keyPath, 'utf8');
}

function createZip(source, output) {
  const result = spawnSync('tar', ['-a', '-cf', output, '-C', source, '.'], {
    encoding: 'utf8',
  });
  if (result.status !== 0) {
    throw new Error(`更新包压缩失败：${result.stderr || result.stdout}`);
  }
}

function createContentHash(source) {
  const files = [];
  const visit = (directory) => {
    for (const name of readdirSync(directory).sort()) {
      const path = join(directory, name);
      if (statSync(path).isDirectory()) visit(path);
      else files.push(path);
    }
  };
  visit(source);
  const hash = createHash('sha256');
  for (const path of files) {
    hash.update(relative(source, path).replaceAll('\\', '/'));
    hash.update('\0');
    hash.update(readFileSync(path));
    hash.update('\0');
  }
  return hash.digest('hex');
}

const args = readArgs(process.argv.slice(2));
const sourceDir = resolve(args.source || '');
const publishDir = resolve(args.publish || '');
const baseUrl = (args['base-url'] || '').replace(/\/$/, '');
const nativeVersionCode = args['native-version'] || '1';
if (!existsSync(join(sourceDir, 'index.html'))) throw new Error('更新目录缺少 index.html。');
if (!existsSync(publishDir)) throw new Error('网站发布目录不存在。');
if (!/^https?:\/\//.test(baseUrl)) throw new Error('更新基础地址无效。');

const scriptDir = dirname(fileURLToPath(import.meta.url));
const expectedPublicKey = readFileSync(join(scriptDir, 'live-update-public.pem'), 'utf8');
const privateKey = createPrivateKey(readPrivateKey());
const actualPublicKey = createPublicKey(privateKey).export({ type: 'spki', format: 'pem' });
if (normalizePublicKey(String(actualPublicKey)) !== normalizePublicKey(expectedPublicKey)) {
  throw new Error('签名私钥与应用内公钥不匹配。');
}

const temporaryZip = join(tmpdir(), `mot-live-update-${process.pid}.zip`);
try {
  rmSync(temporaryZip, { force: true });
  createZip(sourceDir, temporaryZip);
  const bundle = readFileSync(temporaryZip);
  const checksum = createHash('sha256').update(bundle).digest('hex');
  const signer = createSign('RSA-SHA256');
  signer.update(bundle);
  const signature = signer.sign(privateKey, 'base64');
  const bundleId = `web-${createContentHash(sourceDir).slice(0, 24)}`;
  const updateDir = join(publishDir, 'updates');
  mkdirSync(updateDir, { recursive: true });
  copyFileSync(temporaryZip, join(updateDir, `${bundleId}.zip`));
  const manifest = {
    bundleId,
    url: `${baseUrl}/updates/${bundleId}.zip`,
    checksum,
    signature,
    nativeVersionCode,
    createdAt: new Date().toISOString(),
  };
  writeFileSync(join(publishDir, 'live-update.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  process.stdout.write(`在线更新包已生成：${bundleId}\n`);
} finally {
  rmSync(temporaryZip, { force: true });
}
