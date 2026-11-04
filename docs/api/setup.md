# Setting up

How to depend on Oriel, and which parts of its API are stable. [All docs](../index.md)

## Depending on Oriel

To start a new mod, copy one of the [templates](../../template/README.md). Build this Oriel checkout and publish its
artifacts locally with `./gradlew publishToMavenLocal`. No public Oriel Maven endpoint is promised yet; do not use
the upstream Vellum repository for this fork:

```groovy
repositories {
    mavenLocal()
}

// common/ (compiles against vanilla): the API, plus the engine types it exposes
dependencies { compileOnly("dev.oriel:oriel-common-26.3:0.10.0") }

// neoforge/ and fabric/: the loader jar, so dev runs load Oriel as a mod (it bundles the engine and Rhino)
dependencies { implementation("dev.oriel:oriel-neoforge-26.3:0.10.0") }   // or oriel-fabric-26.3
```

To build against an unreleased Oriel, run `./gradlew publishToMavenLocal` in a Oriel checkout and add
`mavenLocal()` to your repositories.

Each Minecraft version has its own artifacts, all at the same Oriel version:

| Minecraft | Common | NeoForge | Fabric |
|---|---|---|---|
| 26.3 | `dev.oriel:oriel-common-26.3` | `dev.oriel:oriel-neoforge-26.3` | `dev.oriel:oriel-fabric-26.3` |
| 26.2 | `dev.oriel:oriel-common-26.2` | `dev.oriel:oriel-neoforge-26.2` | `dev.oriel:oriel-fabric-26.2` |
| 26.1.2 | `dev.oriel:oriel-common-26.1.2` | `dev.oriel:oriel-neoforge-26.1.2` | `dev.oriel:oriel-fabric-26.1.2` |

所有目标均使用 Java 25 和未混淆的 Minecraft API。Fabric 使用 `implementation`，无需旧版 remapping Loom。

26.1/26.2 使用 GLFW 输入事件；26.3 使用 SDL。渲染包迁移等差异见 [版本说明](../versions.md)。

The recommended setup is a required dependency: `[[dependencies.<modid>]] modId="oriel" type="required"` in
`neoforge.mods.toml` with `versionRange="[0.10.0,0.11.0)"`, `"depends": {"oriel": ">=0.10.0 <0.11.0"}` in `fabric.mod.json`, and a required dependency on your
Modrinth and CurseForge pages, so launchers install it with your mod. Players then get Oriel fixes without waiting for
your next release.

You can also bundle Oriel in your jar, so players install one file:

```groovy
// NeoForge (ModDevGradle)
dependencies { jarJar(implementation("dev.oriel:oriel-neoforge-26.3:0.10.0")) }
// Fabric (Loom)
dependencies { include(implementation("dev.oriel:oriel-fabric-26.3:0.10.0")) }
```

Keep the required dependency in `neoforge.mods.toml` or `fabric.mod.json` either way. When several mods bundle
Oriel, or a player also installs it, the loader loads only the newest copy, so a bundled Oriel may be newer than
the one you built against. Oriel still shows in the mod list and keeps its own config file. Rhino's license and
notices are already inside Oriel's jar.

If your mod works without Oriel, make it optional instead (`type="optional"`, `"suggests"`), and:

- Check that Oriel is loaded before touching `dev.oriel` classes:
  - NeoForge: `ModList.get().isLoaded("oriel")`
  - Fabric: `FabricLoader.getInstance().isModLoaded("oriel")`
- Put your Oriel-facing code in a separate class. It is then only class-loaded when Oriel is present, and you can
  fall back to a vanilla screen otherwise.

Either way:

- Server calls run on the server thread; client calls run on the render thread.
- A page URL is a resource id: `mymod:oriel/shop.html` is
  `assets/mymod/oriel/shop.html` in your jar (or in a resource pack, which can restyle your UI). Stylesheets,
  scripts and images referenced from a page resolve relative to it, so `<link rel="stylesheet" href="shop.css">`
  loads `assets/mymod/oriel/shop.css`.

A server with Oriel requires every client to have a matching Oriel protocol. Missing or incompatible clients are
disconnected with an explanation on both loaders. Fabric negotiates a version-specific capability; NeoForge also
checks the payload registrar version. Clients with Oriel can still join servers without it for local-only pages.

## Stability

The API is `dev.oriel.mod.server.OrielServer`, `OrielSession`, the settings file's keys, and `dev.oriel.mod.client.OrielScreens`,
`OrielScreen`, `OrielContainerScreen`, `OrielHud`, `OrielEntities`, `OrielAutomation` and `DocumentDriver`'s
public methods, plus `dev.oriel.mod.server.ForgeMenu` and its slot/data contract. Members annotated
`@ApiStatus.Internal` are cross-package implementation bridges, not downstream API. All implementation classes
live under `dev.oriel.mod.internal`; do not import them even if Java visibility is public.

Before 1.0, an incompatible supported-API change increments the minor version (0.9 to 0.10), never just the patch.
This boundary cleanup introduces 0.10.0: the old canvas-specific slot callback becomes DocumentDriver.SlotSink,
and implementation packages are no longer callable API. Current templates therefore require `>=0.10.0 <0.11.0`
/ `[0.10.0,0.11.0)`. Existing 0.9 consumers must stay within the 0.9 range until migrated.
For 0.9 consumers use `>=0.9.0 <0.10.0` / `[0.9.0,0.10.0)`. Jar-in-Jar is optional; a required external dependency
is recommended. Wider ranges require testing and should not be inferred from a successful compile.

`./gradlew check` includes both loader templates compiled against this checkout and per-Minecraft-version API
snapshots in `api/*.txt`. `:common:<version>:checkPublicApi` rejects internal types in supported signatures and
signature drift; after deliberate review, update with `-PupdateApi`. `checkBackendBoundaries` locks game/loader-free
imports in engine, kit and preview. This guards the fork while its internal implementation evolves.
