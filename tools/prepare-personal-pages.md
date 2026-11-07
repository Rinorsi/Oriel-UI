# Personal pages across dated development runs

This local launcher helper preserves personal previews when the external workspace uses a new run directory each day. It does not register official templates, modify the mod, or start a Minecraft process.

Run from the repository:

    node tools/prepare-personal-pages.mjs --workspace ../Oriel-UI-Workspace --run-root ../Oriel-UI-Workspace/daily/YYYY-MM-DD/run --target fabric-26.1 --report ../Oriel-UI-Workspace/daily/YYYY-MM-DD/logs/personal-pages.json

The helper seeds missing page directories from the newest dated client for the same loader/version, followed by the independent snapshot designated by workspace archive/ui-templates/LATEST.json. Existing page directories are preserved in full, including user edits. It verifies copied file hashes before renaming the temporary directory into place. It rejects links, unsupported targets and destinations outside workspace daily folders. Dates containing restored or sourced personal pages receive a KEEP marker, honored by the workspace cleanup script.

This is a one-time preparation before launch, not a background copy watcher. Oriel's native my-pages hot reload handles subsequent editing. Reopen the template center after adding new files to a running client. Official smoke tests remain isolated. Direct Gradle launches do not call this helper automatically: run it explicitly, or use the workspace launcher.

Snapshots remain local and must not be included in public source commits. The helper does not implement cloud backups, disk-failure protection, or automatic merging of page edits. Update independent snapshots when preserving a new accepted version. Never delete older runtime files on the assumption that an earlier snapshot contains later changes.

Test with ORIEL_TEST_TMP pointing to a workspace daily scratch directory:

    node --test tools/prepare-personal-pages.test.mjs

The numbered official templates are now packaged resources. Exact historical originals listed in tools/templates/previous-personal-pages.json are not re-seeded into new daily clients; modified personal copies still restore. Existing folders are never removed.
