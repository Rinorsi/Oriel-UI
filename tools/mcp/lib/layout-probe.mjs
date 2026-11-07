// The probe owns no build or process runner; the MCP server uses its existing preview workflow.
export function layoutProbeOptions(args, page, image, javaOptions = '') {
  const backend = args.backend ?? 'old';
  const width = args.width ?? 640, height = args.height ?? 360, frames = args.frames ?? 1;
  if (!['old', 'new', 'both'].includes(backend)) throw new Error('backend must be old, new or both');
  if (args.geometry !== undefined && typeof args.geometry !== 'boolean') throw new Error('geometry must be a boolean');
  if (![width, height, frames].every(Number.isInteger) || width < 1 || width > 1920 || height < 1 || height > 1080 || frames < 1 || frames > 600) {
    throw new Error('width/height/frames must be integers within 1..1920/1..1080/1..600');
  }
  const selector = args.selector ?? 'body';
  if (typeof selector !== 'string' || !selector.trim() || selector.length > 2000) throw new Error('selector must be nonempty and at most 2000 characters');
  return {
    command: [page, '--size', `${width}x${height}`, '--scale', '1', '--frames', String(frames), '--snapshot', image, '--boxes', selector],
    env: { JAVA_TOOL_OPTIONS: `${javaOptions} -Doriel.layout.block=${backend} -Doriel.layoutTrace=true -Doriel.layoutGeometry=${args.geometry === true}`.trim() },
  };
}

export function layoutProbeRecords(output, geometry = false) {
  const records = [];
  let complete;
  for (const line of output.split(/\r?\n/)) {
    if (!line.startsWith('{')) continue;
    let record;
    try { record = JSON.parse(line); } catch { continue; }
    if (record.schemaVersion === 1 && ['why-size', 'layout-geometry'].includes(record.kind)) records.push(record);
    if (record.schemaVersion === 1 && record.kind === 'layout-complete') complete = record.data;
  }
  const whyCount = records.filter(r => r.kind === 'why-size').length;
  const geometryCount = records.filter(r => r.kind === 'layout-geometry').length;
  if (!complete || whyCount === 0 || complete.whySizeRecords !== whyCount
      || complete.geometryRecords !== geometryCount || geometryCount !== (geometry ? 1 : 0)) {
    throw new Error('Layout output incomplete or selector matched no elements; narrow the page/selector or disable geometry');
  }
  return records;
}
