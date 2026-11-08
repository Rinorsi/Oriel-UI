import fs from 'node:fs';
import path from 'node:path';
import { ROOT } from './core.mjs';
import { properties, variantVersions } from '../../check-release.mjs';

export function distributionVersions() {
  const props = properties(fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8'));
  const versions = variantVersions(props.version, props.oriel_full_patch);
  const [major, minor] = versions.slim.split('.').map(Number);
  return { ...versions, next: `${major}.${minor + 1}.0` };
}
