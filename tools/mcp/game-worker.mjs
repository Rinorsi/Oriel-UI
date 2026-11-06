import fs from 'node:fs';
import path from 'node:path';
import { loadRecord, saveRecord, writeJson, readJson, gradle, now, stopProcess, fileHash, safePath } from './lib/core.mjs';

const job = loadRecord('jobs', process.argv[2]);
const taskFile = path.join(job.directory, 'game-task.json');
const receiptFile = path.join(job.directory, 'game-receipt.json');
writeJson(taskFile, { jobId: job.id, hash: job.hash, pageUrl: job.pageUrl, data: job.fixture, actions: job.actions, receiptFile });
job.status = 'running'; job.pid = process.pid; job.startedAt = now(); saveRecord('jobs', job);
let child;
const cancellation = setInterval(() => { if (fs.existsSync(path.join(job.directory, 'cancel.json'))) stopProcess(child); }, 500);
try {
  const version = job.profile.minecraft === '26.1.2' ? '26.1' : job.profile.minecraft;
  if (job.pagesCopy) {
    // The folder target: the client serves config/oriel/my-pages as oriel_mine:, so its own run directory gets the same files.
    const pages = path.join(job.directory, 'run', job.profile.loader + '-' + version, 'client', 'config', 'oriel', 'my-pages');
    fs.mkdirSync(pages, { recursive: true });
    for (const f of job.pagesCopy) {
      if (fileHash(f.from) !== f.hash) throw new Error('文件夹里的页面文件已变化：' + f.to);
      const to = safePath(pages, f.to); fs.mkdirSync(path.dirname(to), { recursive: true }); fs.copyFileSync(f.from, to);
    }
  }
  const result = await gradle([':' + job.profile.loader + ':' + version + ':runClient', '-Pautopilot=mcp', '-PorielMcpTask=' + taskFile.replaceAll('\\', '/'), '-Poriel_run_dir=' + path.join(job.directory, 'run').replaceAll('\\', '/'), '--console=plain'], {
    timeout: 600000, logFile: path.join(job.directory, 'game.log'), onSpawn: process => { child = process; },
  });
  job.exitCode = result.code; job.timedOut = result.timedOut; job.receipt = fs.existsSync(receiptFile) ? readJson(receiptFile) : null;
  const cancelled = fs.existsSync(path.join(job.directory, 'cancel.json'));
  job.status = cancelled ? 'cancelled' : result.code === 0 && job.receipt?.passed && job.receipt?.jobId === job.id && job.receipt?.hash === job.hash && job.receipt?.pageUrl === job.pageUrl && job.receipt?.minecraft === job.profile.minecraft && fs.existsSync(job.receipt?.screenshot || '') ? 'passed' : 'failed';
  if (job.status === 'passed') {
    const project = loadRecord('projects', job.projectId);
    project.game = { hash: job.hash, jobId: job.id, passed: true, profile: job.profile, receipt: job.receipt, at: now() }; saveRecord('projects', project);
  }
  job.log = path.join(job.directory, 'game.log');
} catch (error) { job.status = 'failed'; job.error = error.message; }
finally { clearInterval(cancellation); job.finishedAt = now(); saveRecord('jobs', job); }
