# Rhino

Oriel runs page scripts in [Mozilla Rhino](https://github.com/mozilla/rhino) (MPL-2.0), built from source:
the upstream commit pinned in `gradle.properties` (`rhino_commit`, the 1.9.1 release) with the patches in
`patches/` applied in order. The classes are relocated to `dev.oriel.shadow.rhino`, so they never clash with
another mod's copy of Rhino.

分发时必须提供与二进制版本匹配的修改后源码。JAR内的 META-INF/licenses/rhino/SOURCE.txt 指向
https://github.com/Rinorsi/Oriel-UI 及 rhino/patches；Vellum上游不是当前补丁的下载地址。
保留MPL-2.0原文、Rhino NOTICE及本目录构建/补丁，不以项目MIT声明覆盖Rhino。
正式发布前确认对应版本源码/补丁在该地址可取得；本地构建通过不等于已经发布源码。

`build.gradle` downloads the GitHub archive of the pinned commit once into the Gradle user home
(`caches/oriel-rhino/`), checks it against `rhino_sha256`, applies the patches to the core module
(`rhino/src/main`) and compiles it. It applies them again only when a patch changes. A patch that does not apply
fails the build with its name. This needs `git` on the path.

## The patches

| Patch | What it does |
|---|---|
| `0001` | `let` and `const` get a fresh binding in each loop iteration; `const` is block scoped from ES6 on; `for (const ...)` heads |
| `0002` | Spread arguments in calls and `new`: `f(...a, b)`, `new C(...a)` |
| `0003` | `class`: methods, accessors, statics, `extends`, `super`, fields, static blocks, `new.target` |
| `0004` | `async` functions, arrows and methods, and `await` |
| `0005` | A size limit on BigInt arithmetic and parsing, which Oriel sets from `Limits.maxBigIntBits` |
| `0006` | No CommonJS modules: drops the one use of the `require()` loader outside its package |

The build also leaves three packages of the core module out of the jar, because Oriel never runs them: the
compiler to Java bytecode (`optimizer`; scripts are always interpreted), `serialize`, and `commonjs`. See
`build.gradle`.

The patches hold only Rhino's core module (`rhino/src/main`), the part Oriel builds. Oriel's own tests
(`LanguageConformanceTest`, `NewSyntaxSandboxTest`) cover them through the sandbox.

## Changing the patches

```bash
./gradlew :rhino:applyRhinoPatches    # rhino/work: upstream as one commit tagged "upstream", then one commit per patch
```

`rhino/work` is an ordinary git repository (ignored by Oriel's). Edit it, then fold your change into the commit
of the patch it belongs to (for example `git commit --fixup <commit>` and `git rebase -i --autosquash upstream`), or
add a commit for a new patch. Then write the series back:

```bash
./gradlew :rhino:rebuildRhinoPatches  # regenerates patches/ from the commits after "upstream"
```

The patch files leave out version and diffstat noise, so a change to one shows up as a small diff, and keep only
`rhino/src/main`: tests you write in the working copy stay there. Delete `rhino/work` when you are done;
`applyRhinoPatches` refuses to overwrite it.

## Moving to a newer Rhino

Set `rhino_commit` and `rhino_sha256` (the SHA-256 of `https://github.com/mozilla/rhino/archive/<commit>.zip`),
run `applyRhinoPatches`, resolve whatever `git am` stops on, and run `rebuildRhinoPatches`.
