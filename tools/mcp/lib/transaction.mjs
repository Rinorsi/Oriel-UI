import fs from 'node:fs';
import path from 'node:path';
import { safePath, fileHash, requireThat, id } from './core.mjs';

// File transaction only; authorization lives in integration.mjs, never in a test shortcut.
export function installFiles(files, sourceRoot, targetRoot, backupRoot, copy = fs.copyFileSync) {
  for (const file of files) {
    requireThat(safePath(targetRoot, file.target ?? file.file) === file.destination && fileHash(file.destination) === file.previous, 'TARGET_CONFLICT', '目标文件在计划后发生变化：' + file.file);
    requireThat(fileHash(safePath(sourceRoot, file.file)) === file.hash, 'SNAPSHOT_CHANGED', '待安装资源与验收内容不一致。');
  }
  fs.mkdirSync(backupRoot, { recursive: true });
  const touched = [], staged = [];
  try {
    for (let index = 0; index < files.length; index++) {
      const file = files[index];
      requireThat(fileHash(file.destination) === file.previous, 'TARGET_CONFLICT', '写入前检测到并行修改：' + file.file);
      if (file.previous !== null) fs.copyFileSync(file.destination, path.join(backupRoot, String(index)));
      fs.mkdirSync(path.dirname(file.destination), { recursive: true });
      const temp = file.destination + '.' + id('oriel') + '.tmp'; staged.push(temp);
      copy(safePath(sourceRoot, file.file), temp);
      requireThat(fileHash(temp) === file.hash, 'COPY_MISMATCH', '暂存资源摘要不一致。');
      requireThat(fileHash(file.destination) === file.previous, 'TARGET_CONFLICT', '暂存期间检测到并行修改：' + file.file);
      fs.renameSync(temp, file.destination);
      touched.push({ ...file, index });
    }
  } catch (error) {
    const conflicts = [];
    for (const file of touched.reverse()) {
      if (fileHash(file.destination) !== file.hash) { conflicts.push(file.file); continue; }
      if (file.previous === null) fs.unlinkSync(file.destination);
      else fs.copyFileSync(path.join(backupRoot, String(file.index)), file.destination);
    }
    error.rollbackConflicts = conflicts;
    throw error;
  } finally {
    for (const temp of staged) if (fs.existsSync(temp)) fs.unlinkSync(temp);
  }
}
