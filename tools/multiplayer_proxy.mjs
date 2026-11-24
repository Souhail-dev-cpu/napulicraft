import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';

// Local TCP transport with bounded jitter and ordered, back-pressure-aware forwarding.
const options = Object.fromEntries(Array.from({length: Math.floor((process.argv.length - 2) / 2)}, (_, i) =>
  [process.argv[2 + i * 2].replace(/^--/, ''), process.argv[3 + i * 2]]));
const listen = Number(options.listen ?? 25576), target = Number(options.target ?? 25575);
const latency = Number(options['latency-ms'] ?? 80), jitter = Number(options['jitter-ms'] ?? 25);
const reportPath = options.report ?? '.gradle/real-multiplayer/proxy.json';
const stopFile = options['stop-file'] ?? '.gradle/real-multiplayer/stop-proxy';
if (![listen,target,latency,jitter].every(Number.isFinite) || latency < 0 || jitter < 0)
  throw new Error('Invalid proxy ports or latency');
let seed = 37, active = 0, finished = false;
const sockets = new Set(), timers = new Set(), started = Date.now();
const report = {connections:0,bytes:0,latency_ms:latency,jitter_ms:jitter,connection_errors:0};
function finish() {
  if (finished) return;
  finished = true;
  clearInterval(watchdog);
  for (const timer of timers) clearTimeout(timer);
  for (const socket of sockets) socket.destroy();
  report.elapsed_seconds = (Date.now() - started) / 1000;
  fs.mkdirSync(path.dirname(reportPath), {recursive:true});
  fs.writeFileSync(reportPath, JSON.stringify(report,null,2)+'\n');
  server.close();
  console.log('[PROXY] complete', JSON.stringify(report));
}
function forward(source, destination) {
  let due = 0, queued = 0, timer, blocked = false;
  const pending = [];
  const schedule = () => {
    if (timer || blocked || pending.length === 0 || destination.destroyed) return;
    timer = setTimeout(flush,Math.max(1,pending[0].due - Date.now()));
    timers.add(timer);
  };
  const flush = () => {
    timers.delete(timer); timer = undefined;
    if (destination.destroyed) { pending.length = 0; return; }
    // A single FIFO owns writes: overdue timers in different timer buckets must
    // never reorder TCP byte segments when the machine is under heavy load.
    while (pending.length && pending[0].due <= Date.now()) {
      const {buffer} = pending.shift(); queued -= buffer.length;
      report.bytes += buffer.length;
      if (!destination.write(buffer)) {
        blocked = true; source.pause();
        destination.once('drain', () => {
          blocked = false;
          if (queued < 4 * 1048576) source.resume();
          schedule();
        });
        return;
      }
    }
    if (queued < 4 * 1048576) source.resume();
    schedule();
  };
  source.on('data', buffer => {
    seed = (Math.imul(seed,1664525) + 1013904223) >>> 0;
    const delay = Math.max(0,latency + (seed / 0xffffffff * 2 - 1) * jitter);
    due = Math.max(Date.now() + delay, due + 1);
    queued += buffer.length;
    if (queued > 8 * 1048576) source.pause();
    pending.push({buffer,due}); schedule();
  });
}
const server = net.createServer(client => {
  report.connections++; active++;
  const upstream = net.connect({host:'127.0.0.1',port:target});
  let closed = false;
  sockets.add(client); sockets.add(upstream);
  const close = () => {
    if (closed) return;
    closed = true; active--;
    client.destroy(); upstream.destroy(); sockets.delete(client); sockets.delete(upstream);
    if (report.connections >= 3 && active === 0) setTimeout(() => { if (active === 0) finish(); },5000);
  };
  for (const socket of [client,upstream]) {
    socket.setNoDelay(true);
    socket.on('error', () => { report.connection_errors++; close(); });
    socket.on('end',close); socket.on('close',close);
  }
  forward(client,upstream); forward(upstream,client);
});
server.on('error',error => { console.error('[PROXY]',error); finish(); process.exitCode=1; });
server.listen(listen,'127.0.0.1',() => console.log(`[PROXY] READY 127.0.0.1:${listen} -> ${target}; ${latency}±${jitter} ms per direction`));
const watchdog = setInterval(() => {
  if (fs.existsSync(stopFile) || Date.now() - started > Number(options.duration ?? 1800) * 1000) finish();
},1000);
process.on('SIGINT',finish); process.on('SIGTERM',finish);
