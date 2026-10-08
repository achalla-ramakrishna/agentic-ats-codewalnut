#!/usr/bin/env node
// Run BEFORE Vercel imports/builds the project: routing configuration is read before build.
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const [origin, target] = process.argv.slice(2);
if (!['production', 'preview'].includes(target)) throw new Error('Usage: node scripts/deploy/configure-vercel.mjs https://origin-host production|preview');
const url = new URL(origin);
if (url.protocol !== 'https:' || url.pathname !== '/' || url.search || url.hash || url.username || url.password
    || url.port || !/^[a-z0-9.-]+$/.test(url.hostname) || !url.hostname.includes('.')
    || /\.(invalid|test|localhost)$/.test(url.hostname)) throw new Error('Supply a real HTTPS origin hostname without path, credentials or port');
const paths = ['/api/:path*', '/oauth2/:path*', '/login/oauth2/:path*', '/webhooks/:path*'];
writeFileSync(fileURLToPath(new URL('../../frontend/vercel.json', import.meta.url)), JSON.stringify({
  $schema: 'https://openapi.vercel.sh/vercel.json',
  framework: 'vite', buildCommand: 'npm run build', outputDirectory: 'dist', installCommand: 'npm ci',
  git: { deploymentEnabled: false },
  headers: paths.map(source => ({ source, headers: [
    { key: 'Cache-Control', value: 'private, no-store' },
    { key: 'CDN-Cache-Control', value: 'no-store' },
    { key: 'Vercel-CDN-Cache-Control', value: 'no-store' },
  ] })),
  rewrites: [...paths.map(source => ({ source, destination: `${url.origin}${source}` })),
    { source: '/((?!assets/).*)', destination: '/index.html' }],
}, null, 2) + '\n');
console.log(`Generated frontend/vercel.json for ${target}. Deploy only to the matching isolated Vercel project.`);
