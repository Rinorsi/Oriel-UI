import { spawnSync } from 'node:child_process';

const t975 = { 2: 4.302653, 3: 3.182446, 4: 2.776445, 5: 2.570582, 6: 2.446912, 7: 2.364624, 8: 2.306004 };
export function confidence95(values) {
  if (!Array.isArray(values) || values.length < 3 || values.length > 9 || !values.every(Number.isFinite)) throw new Error('Paired CI requires 3..9 finite samples');
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const variance = values.reduce((sum, x) => sum + (x - mean) ** 2, 0) / (values.length - 1);
  const half = t975[values.length - 1] * Math.sqrt(variance / values.length);
  return { mean, low: mean - half, high: mean + half, n: values.length, method: 'paired Student-t, two-sided 95%, independent process pairs; normality assumption' };
}
export function externalJava(excluded = []) {
  const observedAt = new Date().toISOString();
  try {
    const windows = process.platform === 'win32';
    const result = windows ? spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      `$p=@(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | ForEach-Object { [pscustomobject]@{pid=$_.ProcessId;parentPid=$_.ParentProcessId;name=$_.Name;minecraftHint=($_.CommandLine -match "minecraft|runClient|fabric-loader|neoforge");createdAt=[string]$_.CreationDate} }); ConvertTo-Json -InputObject $p -Compress`],
      { encoding: 'utf8', windowsHide: true, timeout: 5000, maxBuffer: 2 * 1024 * 1024 }) : spawnSync('ps', ['-eo', 'pid=,ppid=,comm=,args='], { encoding: 'utf8', timeout: 5000, maxBuffer: 2 * 1024 * 1024 });
    if (result.error || result.status !== 0) throw new Error(result.error?.code || 'PROCESS_LIST_FAILED');
    const processes = windows ? JSON.parse(result.stdout.replace(/^\uFEFF/, '').trim() || '[]') : result.stdout.split('\n').flatMap(line => {
      const match = line.trim().match(/^(\d+)\s+(\d+)\s+(\S+)\s+(.*)$/);
      return match && /^java(w)?(?:\.exe)?$/i.test(match[3]) ? [{ pid: Number(match[1]), parentPid: Number(match[2]), name: match[3], minecraftHint: /minecraft|runClient|fabric-loader|neoforge/i.test(match[4]), createdAt: null }] : [];
    });
    return { observedAt, status: 'available', processes: processes.filter(p => !excluded.includes(p.pid) && p.pid !== process.pid), note: 'Minecraft classification is a command-line hint, not verified instance identity; command lines are not retained.' };
  } catch (error) { return { observedAt, status: 'unavailable', processes: [], error: error.message }; }
}
