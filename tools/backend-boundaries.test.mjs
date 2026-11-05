import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const walk = dir => fs.readdirSync(dir, {withFileTypes:true}).flatMap(e => e.isDirectory() ? walk(path.join(dir,e.name)) : [path.join(dir,e.name)]);

test('one access-widener line may change without repinning Loom output', {skip: process.env.ORIEL_TEST_LOOM !== '1'}, () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to an external test directory');
  assert.ok(process.env.ORIEL_TEST_GW, 'Set ORIEL_TEST_GW to the workspace gw.sh');
  const widener = path.join(root, 'common/src/main/resources/oriel.accesswidener');
  const original = fs.readFileSync(widener);
  const line = 'accessible field net/minecraft/client/gui/GuiGraphicsExtractor scissorStack Lnet/minecraft/client/gui/GuiGraphicsExtractor$ScissorStack;';
  assert.ok(original.toString().includes(line), 'The fixture must change a real widening rule');
  const log = path.join(process.env.ORIEL_TEST_TMP, 'loom-widener-regression.log');
  try {
    fs.writeFileSync(widener, original.toString().replace(line, '# ' + line));
    const result = spawnSync('bash', [process.env.ORIEL_TEST_GW, '-p', root, '--no-configuration-cache',
      ...['26.1', '26.2', '26.3'].map(v => ':fabric:' + v + ':verifyMinecraftContent')],
      {encoding:'utf8', env:{...process.env, GW_OFFLINE:'1'}, timeout:600000, maxBuffer:16 * 1024 * 1024});
    fs.writeFileSync(log, (result.stdout ?? '') + (result.stderr ?? '') + (result.error ?? ''));
    assert.equal(result.status, 0, 'A source edit must not require generated-output pins; see ' + log);
  } finally {
    fs.writeFileSync(widener, original);
  }
});

function linuxTrustGaps(xml) {
  const missing = [];
  for (const part of xml.split('<component ').slice(1)) {
    const component = part.split('</component>')[0];
    const opening = component.split('>')[0];
    if (!/group=['"]org[.]lwjgl['"]/.test(opening)
        && !(/group=['"]com[.]mojang['"]/.test(opening) && /name=['"]jtracy['"]/.test(opening))) continue;
    const names = component.split('<artifact ').slice(1).map(a => a.match(/name=['"]([^'"]+)['"]/)[1]);
    for (const name of names.filter(n => n.endsWith('-natives-windows.jar'))) {
      const linux = name.replace('-natives-windows.jar', '-natives-linux.jar');
      if (!names.includes(linux)) missing.push(linux);
    }
  }
  return missing;
}

test('native dependency trust covers Linux as well as Windows', () => {
  const xml = fs.readFileSync(path.join(root, 'gradle/verification-metadata.xml'), 'utf8');
  assert.ok(xml.includes('-natives-windows.jar'), 'The platform coverage check must not be vacuous');
  assert.deepEqual(linuxTrustGaps(xml), [], 'A local refresh must not drop the CI platform classifiers');
  const windowsOnly = "<component group='org.lwjgl' name='lwjgl' version='3.4.1'><artifact name='lwjgl-3.4.1-natives-windows.jar'></artifact></component>";
  assert.deepEqual(linuxTrustGaps(windowsOnly), ['lwjgl-3.4.1-natives-linux.jar']);
});

test('generated Minecraft exceptions cover only local Loom module shapes and reviewed versions', () => {
  const pins = JSON.parse(fs.readFileSync(path.join(root, 'gradle/generated-minecraft.json'), 'utf8'));
  assert.equal(pins.schemaVersion, 2);
  assert.equal(pins.policy, 'exclude-local-loom-output');
  assert.deepEqual(pins.entries.map(p => p.version).sort(), ['26.1.2', '26.2', '26.3']);
  const xml = fs.readFileSync(path.join(root, 'gradle/verification-metadata.xml'), 'utf8');
  const trusts = xml.split('<trusted-artifacts>')[1].split('</trusted-artifacts>')[0].split('<trust ').slice(1);
  assert.equal(trusts.length, pins.entries.length);
  const attr = (text, name) => text.match(new RegExp(name + "=['\"]([^'\"]+)['\"]"))[1];
  for (const trust of trusts) {
    assert.equal(attr(trust, 'regex'), 'true');
    const pin = pins.entries.find(p => ['group', 'name', 'version', 'file'].every(k =>
      (['group', 'version'].includes(k) ? p[k].replaceAll('.', '[.]') : p[k]) === attr(trust, k)));
    assert.ok(pin, 'No wildcard or unrelated artifact may bypass raw archive verification');
    assert.equal(pin.group, 'net.minecraft');
    assert.equal(pin.name, 'minecraft-merged-[0-9a-f]{10}');
    assert.equal(pin.file, pin.name + '-' + pin.version.replaceAll('.', '[.]') + '[.]jar');
    const matches = (key, value) => new RegExp('^(?:' + attr(trust, key) + ')$').test(value);
    assert.ok(matches('name', 'minecraft-merged-012345abcd'));
    for (const name of ['minecraft', 'minecraft-merged-deobf', 'minecraft-merged-012345abcd-sources']) assert.ok(!matches('name', name));
    assert.ok(!matches('group', 'netXminecraft'));
    assert.ok(!matches('version', '26X3'));
    assert.ok(!matches('file', 'minecraft-merged-012345abcd-' + pin.version + '-sources.jar'));
  }
});

test('engine, kit and preview have no game, graphics or loader imports', () => {
  const failures = [];
  for (const module of ['engine','kit','preview']) for (const file of walk(path.join(root,module,'src')).filter(f=>f.endsWith('.java'))) {
    const lines=fs.readFileSync(file,'utf8').split(String.fromCharCode(10));
    lines.forEach((line,index)=>{
      const text=line.trim().replace('import static ','import ');
      if (['net.minecraft.','com.mojang.','org.lwjgl.','net.fabricmc.','net.neoforged.','net.minecraftforge.']
          .some(prefix=>text.startsWith('import '+prefix))) failures.push(path.relative(root,file)+':'+(index+1)+': '+text);
    });
  }
  assert.deepEqual(failures,[]);
});

test('the mod facade packages contain only the declared downstream API', () => {
  const api = new Set(['DocumentDriver','OrielAutomation','OrielContainerScreen','OrielEntities','OrielHud','OrielScreen','OrielScreens','OrielServer','OrielSession','ForgeMenu']);
  const dir=path.join(root,'common/src/main/java/dev/oriel/mod');
  const outsiders=walk(dir).filter(f=>f.endsWith('.java')&&!f.includes(path.sep+'internal'+path.sep)
    && path.basename(f)!=='package-info.java'&&!api.has(path.basename(f,'.java')));
  assert.deepEqual(outsiders,[]);
});

test('every declared GameTest has an executable test instance', () => {
  const declarations=new Set();
  for(const file of walk(path.join(root,'common/src/main/java/dev/oriel/mod/internal/gametest')).filter(f=>f.endsWith('.java')))
    for(const part of fs.readFileSync(file,'utf8').split('GameTests.add("').slice(1)) declarations.add(part.split('"')[0]);
  const instances=path.join(root,'common/src/main/resources/data/oriel/test_instance');
  const functions=new Set(walk(instances).filter(f=>f.endsWith('.json')).map(f=>JSON.parse(fs.readFileSync(f,'utf8')).function?.replace('oriel:','')));
  assert.deepEqual([...declarations].filter(name=>!functions.has(name)),[], 'Registered tests must actually run on 26.x');
  assert.deepEqual([...functions].filter(name=>!declarations.has(name)),[], 'Stale test instances are forbidden');
});
