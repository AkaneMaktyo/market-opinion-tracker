import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const distDir = path.resolve(process.argv[2] ?? 'dist');
const indexPath = path.join(distDir, 'index.html');

if (!existsSync(indexPath)) {
  throw new Error(`Missing index.html in dist: ${indexPath}`);
}

const indexHtml = readFileSync(indexPath, 'utf8');
const jsMatches = [...indexHtml.matchAll(/<script[^>]+src="([^"]+\.js)"/g)];
const cssMatches = [...indexHtml.matchAll(/<link[^>]+href="([^"]+\.css)"/g)];

if (jsMatches.length === 0) {
  throw new Error('Missing JS entry reference in dist/index.html');
}

for (const [, assetRef] of jsMatches) {
  const assetPath = resolveAssetPath(distDir, assetRef);
  if (!existsSync(assetPath)) {
    throw new Error(`Missing JS asset: ${assetRef}`);
  }
  execFileSync(process.execPath, ['--check', assetPath], { stdio: 'pipe' });
}

for (const [, assetRef] of cssMatches) {
  const assetPath = resolveAssetPath(distDir, assetRef);
  if (!existsSync(assetPath)) {
    throw new Error(`Missing CSS asset: ${assetRef}`);
  }
}

const assetsDir = path.join(distDir, 'assets');
if (!existsSync(assetsDir)) {
  throw new Error(`Missing assets directory in dist: ${assetsDir}`);
}

const assetNames = readdirSync(assetsDir);
if (!assetNames.some((name) => /^index-.*\.js$/.test(name))) {
  throw new Error('Missing entry JS bundle in dist/assets');
}

console.log(`Frontend dist validation passed: ${distDir}`);

function resolveAssetPath(rootDir, assetRef) {
  const cleanRef = assetRef.replace(/^https?:\/\/[^/]+/i, '');
  const normalized = cleanRef.replace(/^\/+/, '');
  const withoutBase = normalized.replace(/^[^/]+\/assets\//, 'assets/');
  return path.join(rootDir, withoutBase);
}
