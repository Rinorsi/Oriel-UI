import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const read = name => fs.readFileSync(new URL('../' + name, import.meta.url), 'utf8');

// Source-level tripwires for the MC host; these do not claim in-game execution coverage.
test('page navigation and reload use the same host resource guard as scripts', () => {
  const driver = read('common/src/main/java/dev/oriel/mod/client/DocumentDriver.java');
  assert.doesNotMatch(driver, /OrielResources[.]loadText[(]/,
    'Direct loading bypasses McHost.readable for navigation into a folder namespace');
  assert.match(driver, /html != null [?] html : host[.]loadText[(]url[)]/);
});

test('server folder reads remain denied and emit a structured diagnostic', () => {
  const host = read('common/src/main/java/dev/oriel/mod/internal/client/McHost.java');
  assert.match(host, /!driver[.]serverSession[(][)] [|][|] !OrielPages[.]isFolderUrl[(]url[)]/);
  assert.match(host, /new Diagnostic[(]Diagnostic[.]Severity[.]ERROR, "server-folder-access"/);
  assert.match(host, /return readable[(]url[)] [?] OrielResources[.]loadText[(]url[)] : null/);
  assert.match(host, /return readable[(]url[)] [?] OrielResources[.]loadBytes[(]url[)] : null/);
});
