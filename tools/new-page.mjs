#!/usr/bin/env node
// Scaffolds a kit page: the HTML (app shell on the Ore theme), its Java controller, a test, and the registration in
// OrielKit. Usage, from the repository root:  node tools/new-page.mjs <name> "<标题>"
//   e.g. node tools/new-page.mjs bestiary "怪物图鉴"
// Then: ./gradlew :kit:test :preview:checkUi, preview it (docs/zh/making-a-page.md), open it in game with /oriel demo.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path/posix';

const [name, title = name] = process.argv.slice(2);
if (!name || !/^[a-z][a-z0-9_]*$/.test(name)) {
  console.error('usage: node tools/new-page.mjs <name: lower case, a-z 0-9 _> "<title>"');
  process.exit(2);
}
const Name = name.split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join('');
const kit = 'kit/src';
const files = {
  html: join(kit, 'main/resources/assets/oriel/ui', `${name}.html`),
  java: join(kit, 'main/java/dev/oriel/kit/preset', `${Name}Preset.java`),
  test: join(kit, 'test/java/dev/oriel/kit/preset', `${Name}PresetTest.java`),
};
for (const f of Object.values(files)) if (existsSync(f)) { console.error(`${f} exists; pick another name`); process.exit(1); }

writeFileSync(files.html, `<!doctype html>
<html>
<head>
<title>优易库 · ${title}</title>
<link rel="stylesheet" href="ore.css">
<style>
/* ${title}. Layout rules: docs/zh/design.md (three depths: backdrop, window, tiles; no tile in a tile). */
.hello { display: flex; align-items: center; gap: 8px; padding: 8px; }
.hello .o-h1 { color: var(--tier); }
</style>
</head>
<body>
<div class="o-app">
  <div class="o-window">
    <div class="o-titlebar">
      <div class="ore-btn icon" data-action="back" tabindex="0"><img class="o-ico" src="../textures/icons/back.png"></div>
      <img class="o-mark" src="../textures/icons/star.png">
      <h1>${title}</h1>
      <div class="o-grow"></div>
      <div class="ore-btn icon" data-action="close" tabindex="0"><img class="o-ico" src="../textures/icons/close.png"></div>
    </div>
    <div class="o-body">
      <div class="o-main">
        <div class="o-caps">开始</div>
        <div class="hello o-tile r-epic fx-breathe">
          <canvas class="fx" data-fx="glint" data-rate="4"></canvas>
          <item id="minecraft:nether_star"></item>
          <div><div class="o-h1" id="greeting">${title}</div><div class="o-dim" id="count">点击按钮</div></div>
        </div>
        <div><div class="ore-btn hero" id="press" tabindex="0"><img class="o-ico" src="../textures/icons/plus.png">按一下</div></div>
      </div>
    </div>
  </div>
</div>
</body>
</html>
`);

writeFileSync(files.java, `package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.Dom;
import dev.oriel.kit.Shell;

/** ${title}: the page's behaviour. Markup and style live in ui/${name}.html; this only wires them. */
public final class ${Name}Preset implements PageControllers.Controller {
    int presses;

    @Override
    public void attach(Document doc) {
        // Theme, data-go / data-action links and canvas[data-fx] particles.
        Shell.setup(doc);
        Shell.activate(Dom.byId(doc, "press"), () -> {
            presses++;
            Dom.text(Dom.byId(doc, "count"), "按了 " + presses + " 次");
        });
    }
}
`);

writeFileSync(files.test, `package dev.oriel.kit.preset;

import dev.oriel.engine.dom.Document;
import dev.oriel.engine.event.Modifiers;
import dev.oriel.kit.OrielKit;
import org.junit.jupiter.api.Test;

import static dev.oriel.kit.preset.KitHost.click;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ${Name}PresetTest {
    private final KitHost host = new KitHost();
    private final double[] clock = {0};

    @Test
    void theButtonCounts() {
        ${Name}Preset page = new ${Name}Preset();
        Document doc = host.open(OrielKit.${name.toUpperCase()}, page);
        click(doc, "#press", 0, Modifiers.NONE, clock);
        assertEquals(1, page.presses);
        assertEquals("按了 1 次", doc.getElementById("count").textContent());
    }
}
`);

// Register: a constant and the controller in OrielKit.
const kitFile = join(kit, 'main/java/dev/oriel/kit/OrielKit.java');
let src = readFileSync(kitFile, 'utf8');
const constant = `    public static final String ${name.toUpperCase()} = page("${name}");\n`;
src = src.replace(/(    public static final String EDITOR = page\("editor"\);\n)/, `$1${constant}`);
src = src.replace(/(import dev\.oriel\.kit\.preset\.)/, `import dev.oriel.kit.preset.${Name}Preset;\n$1`);
src = src.replace(/(        PageControllers\.register\(EDITOR, EditorPreset::new\);\n)/, `$1        PageControllers.register(${name.toUpperCase()}, ${Name}Preset::new);\n`);
writeFileSync(kitFile, src);

console.log(`created ${files.html}
        ${files.java}
        ${files.test}
registered OrielKit.${name.toUpperCase()}
next: ./gradlew :kit:test :preview:checkUi -Pui=${files.html}
      ./gradlew :preview:run --args="${files.html}"     (live preview, reloads on save)
      in game: /oriel open oriel:ui/${name}.html        (dev client; hot reload keeps it current)`);
