#!/usr/bin/env bash
# Compile the complete matrix, then visit gallery/catalog in each client.
# Usage: tools/smoke-all.sh [run-root] [--online] [-- extra-gradle-args...]
set -uo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
cd -- "$repo" || exit 2
run_root=${ORIEL_RUN_DIR:-}
offline=(--offline)
extra=()
while (($#)); do
  case $1 in
    --online) offline=(); shift ;;
    --) shift; extra=("$@"); break ;;
    --help|-h) echo 'Usage: tools/smoke-all.sh [run-root] [--online] [-- extra-gradle-args...]'; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; exit 2 ;;
    *) run_root=$1; shift ;;
  esac
done
if [[ -z $run_root ]]; then
  common_dir=$(git rev-parse --path-format=absolute --git-common-dir) || exit 2
  main_repo=$(dirname -- "$common_dir")
  run_root="$(dirname -- "$main_repo")/Oriel-UI-Workspace/daily/$(date +%F)/run-smoke"
fi
if command -v cygpath >/dev/null 2>&1; then run_root=$(cygpath -u "$run_root"); fi
run_root=$(realpath -m -- "$run_root") || exit 2
case ${run_root,,}/ in
  "${repo,,}/"*) echo 'The run root must be outside the repository.' >&2; exit 2 ;;
esac
mkdir -p -- "$run_root/logs" || exit 2
export ORIEL_RUN_DIR=$run_root
if command -v cygpath >/dev/null 2>&1; then export ORIEL_RUN_DIR=$(cygpath -m "$run_root"); fi
# A path override also lets the script's regression test use a fake Gradle process.
gradle=${ORIEL_GRADLE:-$repo/gradlew}
if [[ ! -f $gradle ]]; then echo "Gradle wrapper not found: $gradle" >&2; exit 2; fi
if ! timeout --version 2>/dev/null | grep -q 'GNU coreutils'; then
  echo 'GNU timeout is required; run this script in Git Bash.' >&2
  exit 2
fi
# No eval: callers may pass proxy flags after --, or use the workspace PROXY_ARGS with --online.
if ((${#offline[@]} == 0)) && [[ -n ${PROXY_ARGS:-} ]]; then
  read -r -a proxy_args <<< "$PROXY_ARGS"
  extra+=("${proxy_args[@]}")
fi
args=("${offline[@]}" --console=plain "${extra[@]}")
compile_log=$run_root/logs/compile.log
summary=$run_root/summary.tsv
printf 'Target\tCompile\tStarted\tScreenshots\tFailedChecks\tPageErrors\tExit\tGallery\tCatalog\n' >"$summary"
echo "Compiling all targets (log: $compile_log)"
bash "$gradle" "${args[@]}" compileJava --continue >"$compile_log" 2>&1
compile_exit=$?
failed=0
for loader in fabric neoforge; do
  for version in 26.1 26.2 26.3; do
    target=$loader-$version
    run=$run_root/$target
    log=$run_root/logs/$target.log
    marker=$run_root/logs/$target.started
    gallery=$run/screenshots/oriel_smoke_gallery.png
    catalog=$run/screenshots/oriel_smoke_catalog.png
    compile=FAIL; started=NO; screenshots=NO; failures=unknown; page_errors=unknown; code=skipped
    task=$(grep -F "> Task :$loader:$version:compileJava" "$compile_log" | tail -n 1)
    if ((compile_exit == 0)) || { [[ -n $task && $task != *FAILED* && $task != *SKIPPED* ]]; }; then
      compile=PASS
      echo "Running $target (600s limit; log: $log)"
      touch -- "$marker"
      timeout 600 bash "$gradle" "${args[@]}" ":$loader:$version:runClient" -Pautopilot=smoke >"$log" 2>&1
      code=$?
      grep -q 'Oriel smoke started' "$log" && started=YES
      if [[ -s $gallery && -s $catalog && $gallery -nt $marker && $catalog -nt $marker ]]; then screenshots=YES; fi
      result=$(sed -n 's/.*Oriel autopilot finished: [0-9][0-9]* checks, \([0-9][0-9]*\) failed.*/\1/p' "$log" | tail -n 1)
      [[ -n $result ]] && failures=$result
      result=$(sed -n 's/.*Oriel smoke finished: \([0-9][0-9]*\) page errors.*/\1/p' "$log" | tail -n 1)
      [[ -n $result ]] && page_errors=$result
    else
      echo "Skipping $target: compilation failed (see $compile_log)" >"$log"
    fi
    if [[ $compile != PASS || $started != YES || $screenshots != YES || $failures != 0 || $page_errors != 0 || $code != 0 ]]; then failed=1; fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$target" "$compile" "$started" "$screenshots" "$failures" "$page_errors" "$code" "$gallery" "$catalog" >>"$summary"
  done
done
((compile_exit != 0)) && failed=1
echo
printf '%-17s %-8s %-8s %-12s %-13s %-11s %s\n' Target Compile Started Screenshots FailedChecks PageErrors Exit
while IFS=$'\t' read -r target compile started screenshots failures page_errors code gallery catalog; do
  [[ $target == Target ]] && continue
  printf '%-17s %-8s %-8s %-12s %-13s %-11s %s\n' "$target" "$compile" "$started" "$screenshots" "$failures" "$page_errors" "$code"
done <"$summary"
echo "Summary and screenshot paths: $summary"
exit "$failed"
