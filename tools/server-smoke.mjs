#!/usr/bin/env node
// Local dedicated-server acceptance. All writable files live under the caller's external run root.
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import crypto from 'node:crypto';
import {spawn, spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const [loader, requestedRoot, ...flags] = process.argv.slice(2);
if (!['fabric', 'neoforge'].includes(loader) || !requestedRoot || flags.some(f => !['--multiplayer', '--mismatch', '--missing-client', '--jij', '--accept-eula'].includes(f))) {
  console.error('Usage: node tools/server-smoke.mjs fabric|neoforge <external-root> [--multiplayer] [--mismatch] [--missing-client] [--accept-eula]');
  process.exit(2);
}
const LF = String.fromCharCode(10), CR = String.fromCharCode(13), BS = String.fromCharCode(92);
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
let root = path.resolve(requestedRoot);
const insideRepo = candidate => {
  const relative = path.relative(fs.realpathSync(repo), candidate);
  return relative === '' || !path.isAbsolute(relative) && relative !== '..' && !relative.startsWith('..' + path.sep);
};
if (insideRepo(root)) throw Error('Run root must be outside the source repository');
fs.mkdirSync(root, {recursive: true});
root = fs.realpathSync(root);
if (insideRepo(root)) throw Error('Run root resolves inside the source repository');
const jij = flags.includes('--jij');
if (jij && flags.some(f => ['--multiplayer', '--mismatch', '--missing-client'].includes(f))) throw Error('--jij is a separate server-only check');
const serverDir = path.join(root, loader + (jij ? '-26.3' : '-26.1'), 'server');
fs.mkdirSync(serverDir, {recursive: true});
const eula = path.join(serverDir, 'eula.txt');
if (flags.includes('--accept-eula')) fs.writeFileSync(eula, 'eula=true' + LF);
if (!fs.existsSync(eula) || !fs.readFileSync(eula, 'utf8').replaceAll(CR, '').split(LF).includes('eula=true')) {
  throw Error('Minecraft EULA must be accepted by the operator in ' + eula + ' (or pass --accept-eula)');
}
const port = loader === 'fabric' ? 29611 : 29612;
const rconPort = port + 10;
const password = crypto.randomBytes(24).toString('hex');
fs.writeFileSync(path.join(serverDir, 'server.properties'), [
  'server-ip=127.0.0.1', 'server-port=' + port, 'online-mode=false', 'enforce-secure-profile=false',
  'enable-rcon=true', 'rcon.port=' + rconPort, 'rcon.password=' + password,
  'view-distance=2', 'simulation-distance=2', 'pause-when-empty-seconds=0', 'spawn-protection=0',
  'level-type=minecraft:flat', 'generate-structures=false', 'difficulty=peaceful', 'max-players=3',
  'motd=Oriel local server acceptance'
].join(LF) + LF);
if (jij) {
  const version = fs.readFileSync(path.join(repo, 'gradle.properties'), 'utf8').split(LF).find(l => l.startsWith('version=')).slice(8).trim();
  const library = path.join(repo, loader, 'versions/26.3/build/libs', 'oriel-' + loader + '-26.3-' + version + '.jar');
  if (!fs.existsSync(library)) throw Error('Build templateCompile before --jij: ' + library);
  const fixture = spawnSync('java', [path.join(repo, 'tools/compat/BundleFixtures.java'), loader, library, path.join(serverDir, 'mods')], {encoding:'utf8'});
  if (fixture.status !== 0) throw Error('Nested fixture creation failed: ' + fixture.stdout + fixture.stderr);
}

const quote = value => "'" + value.replaceAll("'", "'" + BS + "''") + "'";
function launch(name, task, extra = [], runRoot = root) {
  const log = path.join(root, name + '.log');
  const output = fs.createWriteStream(log);
  const args = [path.join(repo, 'gradlew').replaceAll(BS, '/'), '--offline', '--console=plain', task, ...extra];
  const command = 'exec timeout 900 bash ' + args.map(quote).join(' ');
  const child = spawn(process.env.ORIEL_BASH || (process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash'),
    ['-lc', command], {cwd: repo, env: {...process.env, ORIEL_RUN_DIR: runRoot}, windowsHide: true});
  const run = {name, log, child, text: '', code: undefined};
  child.stdout.on('data', data => { output.write(data); run.text += data; });
  child.stderr.on('data', data => { output.write(data); run.text += data; });
  run.done = new Promise(resolve => {
    child.once('error', error => { run.text += error.message; run.code = -1; output.end(); resolve(-1); });
    child.once('close', code => { run.code = code; output.end(); resolve(code); });
  });
  console.log('Started ' + name + '; log: ' + log);
  return run;
}
async function until(run, predicate, seconds = 180) {
  const deadline = Date.now() + seconds * 1000;
  while (!predicate(run.text)) {
    if (run.code !== undefined) throw Error(run.name + ' exited early (' + run.code + '); see ' + run.log);
    if (Date.now() > deadline) throw Error(run.name + ' timed out; see ' + run.log);
    await new Promise(resolve => setTimeout(resolve, 250));
  }
}

// Normal RCON commands provide shutdown; no process killing or global daemon stop.
function rcon(command) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({host: '127.0.0.1', port: rconPort});
    let buffered = Buffer.alloc(0), authenticated = false, settled = false;
    const fail = error => { if (!settled) { settled = true; socket.destroy(); reject(error); } };
    socket.setTimeout(10000, () => fail(Error('RCON timeout')));
    socket.on('error', fail);
    socket.on('close', () => { if (!settled) fail(Error('RCON closed before replying')); });
    const send = (id, type, text) => {
      const body = Buffer.from(text, 'utf8');
      const packet = Buffer.alloc(body.length + 14);
      packet.writeInt32LE(body.length + 10, 0); packet.writeInt32LE(id, 4); packet.writeInt32LE(type, 8);
      body.copy(packet, 12); socket.write(packet);
    };
    socket.on('connect', () => send(1, 3, password));
    socket.on('data', chunk => {
      buffered = Buffer.concat([buffered, chunk]);
      while (buffered.length >= 4 && buffered.length >= buffered.readInt32LE(0) + 4) {
        const length = buffered.readInt32LE(0), id = buffered.readInt32LE(4), type = buffered.readInt32LE(8);
        const text = buffered.subarray(12, length + 2).toString('utf8');
        buffered = buffered.subarray(length + 4);
        if (id === -1) return fail(Error('RCON authentication failed'));
        if (!authenticated && type === 2) { authenticated = true; send(2, 2, command); }
        else if (authenticated && id === 2) { settled = true; socket.end(); resolve(text); }
      }
    });
  });
}

const runs = [];
let server, ready = false, success = false;
try {
  server = jij ? launch('dedicated', 'runServer', ['-p', path.join(repo, 'template', loader), '-PjijAcceptance',
    '-PjijRunDir=' + serverDir, '-PorielRepository=' + path.join(repo, 'build/template-maven')])
    : launch('dedicated', ':' + loader + ':26.1:runServer', ['-PserverProbe']);
  runs.push(server);
  await until(server, text => text.includes('Done (') && text.includes('RCON running on'), 300);
  ready = true;
  if (!server.text.includes(jij ? 'ORIEL_JIJ_PROBE parents=true,true' : 'ORIEL_SERVER_PROBE loaded'))
    throw Error('Server started without the expected acceptance modules; see ' + server.log);
  if (jij) {
    const version = await rcon('oriel version');
    if (!version.includes('Oriel UI runtime version: 0.10.2')) throw Error('Loader selected the wrong nested library: ' + version);
    const starts = server.text.split('Oriel UI runtime version:').length - 1;
    if (starts !== 1 || !server.text.includes('Oriel UI runtime version: 0.10.2')) throw Error('Expected exactly one 0.10.2 runtime: ' + starts);
    console.log('JAR_IN_JAR PASS two parents, versions 0.10.1/0.10.2, one runtime 0.10.2; /oriel version confirmed');
  }
  if (flags.includes('--mismatch')) {
    const clientRoot = path.join(root, 'mismatch');
    const clientDir = path.join(clientRoot, loader + '-26.1', 'client');
    fs.mkdirSync(clientDir, {recursive: true});
    fs.writeFileSync(path.join(clientDir, 'options.txt'), 'onboardAccessibility:false' + LF + 'skipMultiplayerWarning:true' + LF);
    const client = launch('mismatch-client', ':' + loader + ':26.1:runClient',
      ['-PprobeRole=A', '-PprobeProtocol=999', '-PprobeAddress=127.0.0.1:' + port], clientRoot);
    runs.push(client);
    await until(client, text => text.includes('PASS mismatched protocol disconnected'), 180);
    if (await client.done !== 0) throw Error('Mismatch client did not exit normally');
    if (!server.text.includes('protocol mismatch') || !server.text.includes('协议版本不匹配'))
      throw Error('Server log lacks protocol rejection explanation');
    if (!client.text.includes('protocol mismatch') || !client.text.includes('协议版本不匹配'))
      throw Error('Client log lacks bilingual protocol rejection');
  }
  if (flags.includes('--missing-client')) {
    const clientRoot = path.join(root, 'missing-client');
    const clientDir = path.join(clientRoot, loader + '-26.1', 'client');
    fs.mkdirSync(clientDir, {recursive: true});
    fs.writeFileSync(path.join(clientDir, 'options.txt'), 'onboardAccessibility:false' + LF + 'skipMultiplayerWarning:true' + LF);
    // Suppress all Oriel client channel registration while retaining the test driver and its normal shutdown.
    const client = launch('missing-client', ':' + loader + ':26.1:runClient',
      ['-PprobeRole=A', '-PprobeMissing', '-PprobeAddress=127.0.0.1:' + port], clientRoot);
    runs.push(client);
    await until(client, text => text.includes('PASS missing client disconnected'), 180);
    if (await client.done !== 0) throw Error('Missing-client probe did not exit normally');
    for (const text of [client.text, server.text]) {
      if (!text.includes('requires Oriel UI on the client') || !text.includes('此服务器要求客户端安装'))
        throw Error('Missing-client rejection was not bilingual');
    }
  }
  if (flags.includes('--multiplayer')) {
    for (const role of ['A', 'B']) {
      const clientDir = path.join(root, role, loader + '-26.1', 'client');
      fs.mkdirSync(clientDir, {recursive: true});
      fs.writeFileSync(path.join(clientDir, 'options.txt'),
        ['onboardAccessibility:false', 'skipMultiplayerWarning:true', 'pauseOnLostFocus:false', 'maxFps:60'].join(LF) + LF);
      const client = launch('client-' + role, ':' + loader + ':26.1:runClient',
        ['-PprobeRole=' + role, '-PprobeAddress=127.0.0.1:' + port], path.join(root, role));
      runs.push(client);
      await until(server, text => text.includes('OrielProbe' + role + ' joined the game'));
    }
    console.log('Probe command: ' + await rcon('oriel-probe start OrielProbeA OrielProbeB'));
    await until(server, text => text.includes('ORIEL_SERVER_PROBE PASS'), 90);
    for (const client of runs.slice(1)) {
      await until(client, text => text.includes('ORIEL_CLIENT_PROBE PASS'), 60);
      if (await client.done !== 0 || client.text.includes('ORIEL_CLIENT_PROBE FAIL')) throw Error('Client probe failed: ' + client.log);
    }
  }
  await rcon('stop');
  if (await server.done !== 0 || !server.text.includes('Stopping server')) throw Error('Dedicated server did not shut down normally');
  success = true;
} finally {
  if (!success && ready && server.code === undefined) {
    await rcon('stop').catch(error => console.error('Could not stop server through RCON: ' + error.message));
  }
  const results = await Promise.all(runs.map(async run => ({name: run.name, exit: await run.done, log: run.log})));
  fs.writeFileSync(path.join(root, 'result.json'), JSON.stringify({loader, jij, multiplayer: flags.includes('--multiplayer'), mismatch: flags.includes('--mismatch'), missingClientChannels: flags.includes('--missing-client'), success, results}, null, 2));
}
console.log('PASS: ' + loader + ' dedicated startup, library loading, ' + (flags.includes('--multiplayer') ? 'two-client isolation, ' : '') + 'clean shutdown');
