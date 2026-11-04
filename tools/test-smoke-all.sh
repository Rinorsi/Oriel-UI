#!/usr/bin/env bash
# Regression checks for result accounting; no Minecraft process is launched.
# Usage: bash tools/test-smoke-all.sh <temporary-directory-outside-repository>
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
root=${1:?Supply a temporary directory outside the repository}
if command -v cygpath >/dev/null 2>&1; then root=$(cygpath -u "$root"); fi
root=$(realpath -m -- "$root")
case ${root,,}/ in "${repo,,}/"*) echo 'Test files must stay outside the repository.' >&2; exit 2 ;; esac
mkdir -p -- "$root"
fake=$root/fake-gradle.sh
cat >"$fake" <<'FAKE'
#!/usr/bin/env bash
set -eu
for arg; do
  if [[ $arg == compileJava ]]; then
    for loader in fabric neoforge; do
      for version in 26.1 26.2 26.3; do
        if [[ ${SMOKE_CASE:-} == compile && $loader-$version == fabric-26.2 ]]; then
          echo "> Task :$loader:$version:compileJava FAILED"
        else
          echo "> Task :$loader:$version:compileJava UP-TO-DATE"
        fi
      done
    done
    [[ ${SMOKE_CASE:-} != compile ]]; exit $?
  fi
  if [[ $arg == *:runClient ]]; then target=${arg#:}; target=${target%:runClient}; target=${target/:/-}; fi
done
echo 'Oriel smoke started'
if [[ ${SMOKE_CASE:-} != stale ]]; then
  mkdir -p "$ORIEL_RUN_DIR/$target/screenshots"
  printf PNG >"$ORIEL_RUN_DIR/$target/screenshots/oriel_smoke_gallery.png"
  printf PNG >"$ORIEL_RUN_DIR/$target/screenshots/oriel_smoke_catalog.png"
fi
if [[ ${SMOKE_CASE:-} == incomplete && $target == neoforge-26.3 ]]; then exit 0; fi
echo 'Oriel smoke finished: 0 page errors'
echo 'Oriel autopilot finished: 4 checks, 0 failed'
FAKE
export ORIEL_GRADLE=$fake
SMOKE_CASE=success bash "$repo/tools/smoke-all.sh" "$root/run" >"$root/success.log" 2>&1
[[ $(wc -l <"$root/run/summary.tsv") == 7 ]]
for scenario in stale incomplete compile; do
  if SMOKE_CASE=$scenario bash "$repo/tools/smoke-all.sh" "$root/run" >"$root/$scenario.log" 2>&1; then
    echo "FAIL: $scenario should fail" >&2; exit 1
  fi
  case $scenario in
    stale) grep -q $'fabric-26.1\tPASS\tYES\tNO' "$root/run/summary.tsv" ;;
    incomplete) grep -q $'neoforge-26.3\tPASS\tYES\tYES\tunknown' "$root/run/summary.tsv" ;;
    compile) grep -q $'fabric-26.2\tFAIL\tNO\tNO' "$root/run/summary.tsv" ;;
  esac
done
# Reject an in-repository root before any directory is created, including a Windows drive-path alias.
mkdir -p "$root/guard-bin"
cat >"$root/guard-bin/mkdir" <<'GUARD'
#!/usr/bin/env bash
echo 'Unexpected mkdir before repository guard' >&2
exit 99
GUARD
chmod +x "$root/guard-bin/mkdir"
inside=$repo
if command -v cygpath >/dev/null 2>&1; then inside=$(cygpath -m "$inside"); fi
if PATH="$root/guard-bin:$PATH" bash "$repo/tools/smoke-all.sh" "$inside" >"$root/guard.log" 2>&1; then
  echo 'FAIL: an in-repository root was accepted' >&2; exit 1
fi
grep -q 'The run root must be outside the repository' "$root/guard.log"
echo 'PASS: success, stale screenshots, missing completion, per-target compilation failure, repository guard'
