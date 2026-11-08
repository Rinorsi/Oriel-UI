import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Audit only our own coordinates: third-party dependencies belong to their upstream repositories.
export function auditMaven(directory) {
  const root = path.resolve(directory), failures = [];
  const files = fs.readdirSync(root, { recursive: true, withFileTypes: true })
    .filter(e => e.isFile() && /\.(pom|module)$/.test(e.name))
    .map(e => path.join(e.parentPath, e.name));
  if (!files.length) failures.push('No POM/module metadata found');
  let dependencies = 0;
  const check = (file, group, artifact, version) => {
    if (group !== 'dev.oriel' && !group?.startsWith('dev.oriel.')) return;
    dependencies++;
    if (!/^[\w.-]+$/.test(artifact ?? '') || !/^\d+\.\d+\.\d+$/.test(version ?? '')) {
      failures.push(`${path.relative(root, file)}: unsupported internal coordinate ${group}:${artifact}:${version}`);
      return;
    }
    const base = path.join(root, ...group.split('.'), artifact, version, `${artifact}-${version}`);
    // POM-only publications (e.g. relocated Rhino) are valid Gradle/Maven dependencies.
    for (const extension of ['pom', 'jar']) if (!fs.existsSync(`${base}.${extension}`))
      failures.push(`${path.relative(root, file)}: missing ${group}:${artifact}:${version} (.${extension})`);
  };
  const publication = (file, group, version) => {
    if ((group === 'dev.oriel' || group?.startsWith('dev.oriel.')) && !/^\d+\.\d+\.0$/.test(version ?? ''))
      failures.push(`${path.relative(root, file)}: Maven accepts only slim X.Y.0 publications, found ${version}`);
  };
  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    if (file.endsWith('.module')) {
      const metadata = JSON.parse(text);
      publication(file, metadata.component?.group, metadata.component?.version);
      for (const variant of metadata.variants ?? []) {
        for (const dep of [...(variant.dependencies ?? []), ...(variant.dependencyConstraints ?? [])])
          check(file, dep.group, dep.module, dep.version?.strictly ?? dep.version?.requires ?? dep.version?.prefers);
        if (variant['available-at']) {
          const dep = variant['available-at'];
          check(file, dep.group, dep.module, dep.version);
        }
      }
    } else {
      // Gradle-generated POMs use literal coordinates, without parent/property interpolation.
      const field = (xml, name) => xml.match(new RegExp(`<${name}>\\s*([^<]+?)\\s*</${name}>`))?.[1];
      publication(file, field(text, 'groupId'), field(text, 'version'));
      for (const match of text.matchAll(/<dependency>\s*([\s\S]*?)<\/dependency>/g))
        check(file, field(match[1], 'groupId'), field(match[1], 'artifactId'), field(match[1], 'version'));
    }
  }
  return { directory: root, metadataFiles: files.length, internalDependencies: dependencies, failures };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3) throw Error('Usage: node tools/check-maven.mjs <local Maven directory>');
  const result = auditMaven(process.argv[2]);
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = result.failures.length ? 1 : 0;
}
