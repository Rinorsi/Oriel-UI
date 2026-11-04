import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const walk = dir => fs.readdirSync(dir, {withFileTypes:true}).flatMap(e => e.isDirectory() ? walk(path.join(dir,e.name)) : [path.join(dir,e.name)]);

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
