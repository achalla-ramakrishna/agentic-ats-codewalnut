import { get, put } from '@vercel/blob';
import { buildServer, readConfig } from './server.js';

const config = readConfig(process.env);
const server = buildServer(config, { get, put });
// Dual-stack listening also supports Railway's IPv6 private-network hostnames.
server.listen(config.port, '::', () => {
  console.info('Private document storage bridge listening');
});
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.once(signal, () => {
    server.close(() => process.exit(0));
    setTimeout(() => {
      server.closeAllConnections();
      process.exit(1);
    }, 65_000).unref();
  });
}
