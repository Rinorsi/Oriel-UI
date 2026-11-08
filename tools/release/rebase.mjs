export function validateRebase(source, head, core, changelog) {
  if (!/^[a-f0-9]{40}$/.test(source ?? '') || source !== head)
    throw Error('Unreleased core rebase requires the exact current 40-character source commit');
  if (!/^\d+\.\d+\.0$/.test(core) || !changelog.split(/\r?\n/).some(line =>
    line.startsWith('## ' + core + ' slim / ') && line.endsWith(' (unreleased)')))
    throw Error('Core rebase is restricted to an explicitly unreleased minor');
}

export function rebasePins(text, results, source) {
  const targets = ['fabric','neoforge'].flatMap(loader => ['26.1.2','26.2','26.3'].map(mc => loader+'-'+mc));
  if (results.length !== targets.length * 2 || new Set(results.map(r => r.minor)).size !== 1)
    throw Error('Core rebase requires exactly six slim/full pairs from one minor');
  for (const target of targets) {
    const pair = results.filter(r => r.target === target);
    if (pair.length !== 2 || new Set(pair.map(r => r.variant)).size !== 2
        || !pair.some(r => r.variant === 'slim') || !pair.some(r => r.variant === 'full')
        || pair.some(r => r.failures.length || !/^[a-f0-9]{64}$/.test(r.coreHash)) || pair[0].coreHash !== pair[1].coreHash)
      throw Error('Core rebase requires validated matching pair: ' + target);
  }
  const replacements = new Map(results.map(r => [r.minor+'.'+r.target,r.coreHash]));
  const previous = [];
  const lines = text.trimEnd().split(/\r?\n/).map(line => {
    const index = line.indexOf('='), key = line.slice(0,index);
    if (!replacements.has(key)) return line;
    previous.push('# previous ' + line);
    const next = key+'='+replacements.get(key);
    replacements.delete(key);
    return next;
  });
  return lines.join('\n')+'\n# Unreleased core rebase source='+source+'\n'+previous.join('\n')+'\n'
    + (replacements.size ? [...replacements].map(([k,v]) => k+'='+v).join('\n')+'\n' : '');
}
