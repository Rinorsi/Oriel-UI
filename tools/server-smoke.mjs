#!/usr/bin/env node
// Local dedicated-server acceptance. All writable files live under the caller's external run root.
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import crypto from 'node:crypto';
import {spawn, spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {properties, variantVersions, jarMetadata, readJar} from './check-release.mjs';

const [loader, requestedRoot, ...flags] = process.argv.slice(2);
const scenario = flags.find(f => f.startsWith('--jij-scenario='))?.split('=')[1] ?? 'two-parents';
if (!['slim', 'top-full', 'two-parents', 'conflict', 'same-core', 'different-core', 'legacy'].includes(scenario)) throw Error('Unknown JiJ scenario: ' + scenario);
if (!['fabric', 'neoforge'].includes(loader) || !requestedRoot || flags.some(f => !f.startsWith('--jij-scenario=') && !['--multiplayer', '--mismatch', '--missing-client', '--jij', '--accept-eula'].includes(f))) {
  console.error('Usage: node tools/server-smoke.mjs fabric|neoforge <external-root> [--jij --jij-scenario=slim|top-full|two-parents|conflict|same-core|different-core|legacy] [--multiplayer] [--mismatch] [--missing-client] [--accept-eula]; ORIEL_GWS selects the build wrapper, ORIEL_LOG_DIR selects external build logs');
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
const buildProperties = properties(fs.readFileSync(path.join(repo, 'gradle.properties'), 'utf8'));
const versions = variantVersions(buildProperties.version, buildProperties.oriel_full_patch);
if (jij) {
  const library = version => path.join(repo, loader, 'versions/26.3/build/libs', 'oriel-' + loader + '-26.3-' + version + '.jar');
  for (const version of Object.values(versions)) if (!fs.existsSync(library(version))) throw Error('Build both distributions before --jij: ' + library(version));
  const mods = path.join(serverDir, 'mods');
  if (fs.existsSync(mods) && fs.readdirSync(mods).length) throw Error('JiJ acceptance requires an empty mods directory: ' + mods);
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java') : 'java';
  let nested = library(versions.slim), legacyVersion;
  if (scenario === 'different-core') {
    nested = path.join(root, 'fixtures/different-core.jar');
    const changed = spawnSync(java, [path.join(repo, 'tools/compat/DifferentCoreFixture.java'), library(versions.slim), nested,
      path.join(repo, 'api/26.3.txt')], {cwd:repo, encoding:'utf8'});
    if (changed.status !== 0) throw Error('Different-core fixture failed: ' + changed.stdout + changed.stderr);
  }
  if (scenario === 'legacy') {
    if (!process.env.ORIEL_LEGACY_JAR) throw Error('legacy scenario requires ORIEL_LEGACY_JAR pointing to a real older release');
    nested = path.resolve(process.env.ORIEL_LEGACY_JAR);
    const old = readJar(nested);
    if (old.entries.has('META-INF/oriel/manifest.json')) throw Error('Legacy fixture must have no manifest');
    const meta = jarMetadata(old.entries);
    if (meta.id !== 'oriel' || meta.loader !== loader) throw Error('Wrong legacy release target');
    legacyVersion = meta.version;
  }
  const fixture = spawnSync(java, [path.join(repo, 'tools/compat/BundleFixtures.java'), loader, nested, mods, library(versions.full), scenario,
    ...(legacyVersion ? [legacyVersion] : [])], {cwd:repo, encoding:'utf8'});
  if (fixture.status !== 0) throw Error('Nested fixture creation failed: ' + fixture.stdout + fixture.stderr);
}

const quote = value => "'" + value.replaceAll("'", "'" + BS + "''") + "'";
function launch(name, task, extra = [], runRoot = root) {
  const logRoot = path.resolve(process.env.ORIEL_LOG_DIR || root);
  if (insideRepo(logRoot)) throw Error('Log directory must be outside the source repository');
  fs.mkdirSync(logRoot, {recursive:true});
  if (insideRepo(fs.realpathSync(logRoot))) throw Error('Log directory resolves inside the source repository');
  const log = path.join(logRoot, name + '.log');
  const output = fs.createWriteStream(log);
  const projectArg = extra.indexOf('-p');
  const project = projectArg < 0 ? repo : extra[projectArg + 1];
  const forwarded = projectArg < 0 ? extra : extra.filter((_, i) => i !== projectArg && i !== projectArg + 1);
  const wrapper = process.env.ORIEL_GWS;
  const args = wrapper ? [wrapper, project, task, ...forwarded]
    : [path.join(repo, 'gradlew').replaceAll(BS, '/'), '-p', project, '--offline', '--console=plain', task, ...forwarded];
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
    '-PjijRunDir=' + serverDir, '-PorielRepository=' + path.join(repo, 'build/template-maven'), '-Poriel_version=' + versions.slim])
    : launch('dedicated', ':' + loader + ':26.1:runServer', ['-PserverProbe']);
  runs.push(server);
  if (jij && scenario === 'conflict') {
    const code = await server.done;
    const [major, minor] = versions.slim.split('.').map(Number);
    const refused = major + '.' + (minor + 1) + '.0';
    if (code === 0 || !server.text.includes(refused) || !/oriel_bundle_2/i.test(server.text)
        || !/incompatible|incompatib|requires|mandatory|dependency|dependencies|resolution/i.test(server.text)
        || server.text.includes('Done (')) throw Error('Expected loader rejection naming oriel_bundle_2 and ' + refused + '; see ' + server.log);
    success = true;
    console.log('JAR_IN_JAR PASS incompatible ranges rejected');
  } else {
  await until(server, text => text.includes('Done (') && text.includes('RCON running on'), 300);
  ready = true;
  const parents = ['two-parents', 'conflict', 'same-core', 'different-core', 'legacy'].includes(scenario) ? 'true,true' : 'true,false';
  if (!server.text.includes(jij ? 'ORIEL_JIJ_PROBE parents=' + parents : 'ORIEL_SERVER_PROBE loaded'))
    throw Error('Server started without the expected acceptance modules; see ' + server.log);
  if (jij) {
    const version = await rcon('oriel version');
    const expected = scenario === 'slim' ? versions.slim : versions.full;
    if (!version.includes('Oriel UI runtime version: ' + expected)) throw Error('Loader selected the wrong nested library: ' + version);
    const starts = server.text.split('Oriel UI runtime version:').length - 1;
    if (starts !== 1 || !server.text.includes('Oriel UI runtime version: ' + expected)) throw Error('Expected exactly one ' + expected + ' runtime: ' + starts);
    if (!server.text.includes('Oriel nested summary:') || server.text.includes('Oriel nested check failed'))
      throw Error('Nested scan did not complete; see ' + server.log);
    const risk = server.text.split(/\r?\n/).filter(line => line.includes('Oriel nested risk:'));
    if (['different-core', 'legacy'].includes(scenario)) {
      if (risk.length !== 1 || !risk[0].includes('oriel_bundle_1') || !risk[0].includes('oriel_bundle_2') || !risk[0].includes('WARN'))
        throw Error('Expected one nested WARN naming both parent mods; see ' + server.log);
    } else if (risk.length) throw Error('Equal cores must not produce a nested risk WARN; see ' + server.log);
    const cache = JSON.parse(fs.readFileSync(path.join(serverDir, 'config/oriel/nested-check.json'), 'utf8'));
    const expectedCopies = scenario === 'slim' ? 1 : 2;
    if (cache.report.copies.length !== expectedCopies || cache.report.copies.filter(copy => copy.source === cache.report.active.source).length !== 1)
      throw Error('Nested scan must retain all copies and exactly one active copy');
    if (scenario === 'legacy' && !JSON.stringify(cache.report.differences).includes('version-only')) throw Error('Legacy comparison was not version-only');
    if (scenario === 'different-core' && !JSON.stringify(cache.report.differences).includes('assets/oriel/ui/ore.css')) throw Error('Changed core resource was not detected');
    console.log('JAR_IN_JAR PASS ' + scenario + '; one runtime ' + expected + '; /oriel version and nested diagnostics confirmed');
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
  }
} finally {
  if (!success && ready && server.code === undefined) {
    await rcon('stop').catch(error => console.error('Could not stop server through RCON: ' + error.message));
  }
  const results = await Promise.all(runs.map(async run => ({name: run.name, exit: await run.done, log: run.log})));
  fs.writeFileSync(path.join(root, 'result.json'), JSON.stringify({loader, jij, scenario, multiplayer: flags.includes('--multiplayer'), mismatch: flags.includes('--mismatch'), missingClientChannels: flags.includes('--missing-client'), success, results}, null, 2));
}
console.log(jij && scenario === 'conflict'
  ? 'PASS: ' + loader + ' rejected incompatible dependency ranges before startup'
  : 'PASS: ' + loader + ' dedicated startup, library loading, ' + (flags.includes('--multiplayer') ? 'two-client isolation, ' : '') + 'clean shutdown');
