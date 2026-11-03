# Setting up

How to depend on Oriel, and which parts of its API are stable. [All docs](../index.md)

## Depending on Oriel

To start a new mod, copy one of the [templates](../../template/README.md). Otherwise, Oriel is on
[maven.maxleiter.com](https://maven.maxleiter.com):

```groovy
repositories {
    maven {
        url = "https://maven.maxleiter.com"
        content { includeGroup("dev.oriel") }
    }
}

// common/ (compiles against vanilla): the API, plus the engine types it exposes
dependencies { compileOnly("dev.oriel:oriel-common-26.3:0.9.0") }

// neoforge/ and fabric/: the loader jar, so dev runs load Oriel as a mod (it bundles the engine and Rhino)
dependencies { implementation("dev.oriel:oriel-neoforge-26.3:0.9.0") }   // or oriel-fabric-26.3
```

To build against an unreleased Oriel, run `./gradlew publishToMavenLocal` in a Oriel checkout and add
`mavenLocal()` to your repositories.

Each Minecraft version has its own artifacts, all at the same Oriel version:

| Minecraft | Common | NeoForge | Fabric |
|---|---|---|---|
| 26.3 | `dev.oriel:oriel-common-26.3` | `dev.oriel:oriel-neoforge-26.3` | `dev.oriel:oriel-fabric-26.3` |
| 26.2 | `dev.oriel:oriel-common-26.2` | `dev.oriel:oriel-neoforge-26.2` | `dev.oriel:oriel-fabric-26.2` |
| 1.21.1 | `dev.oriel:oriel-common-1.21.1` | `dev.oriel:oriel-neoforge-1.21.1` | `dev.oriel:oriel-fabric-1.21.1` |

On 1.21.1 the Fabric jar is remapped to intermediary names like any Fabric mod for an obfuscated version: depend on it
with `modImplementation`, and compile against Mojang's mappings, as `oriel-common-1.21.1` is. The API is the same on
every version, except:

- `OrielEntities.registerPortraitState` is 26.3 only (1.21.1 has no entity render states; see
  [Entity render states](elements.md#entity-render-states)).
- `DocumentDriver.onKey` handlers get Oriel's `dev.oriel.mod.client.input.KeyEvent` on 1.21.1, a record with
  the shape of 26.3's `net.minecraft.client.input.KeyEvent` (`key()`, `modifiers()`, `isEscape()`,
  `hasShiftDown()`...) whose `key()` is a GLFW key code and whose `scancode()` replaces `keycode()`.
- On 26.2 the input events are Minecraft's own, as on 26.3, but 26.2 uses GLFW: `KeyEvent.key()` is a GLFW key code and
  `scancode()` replaces 26.3's `keycode()`; its render API is in `com.mojang.blaze3d.*` where 26.3's is in
  `com.mojang.renderpearl.api.*`.
- `OrielScreen` and `OrielContainerScreen` override 1.21.1's screen methods (`render`, `mouseClicked(double, double,
  int)`...), so subclasses override those there.

The recommended setup is a required dependency: `[[dependencies.<modid>]] modId="oriel" type="required"` in
`neoforge.mods.toml`, `"depends": {"oriel": ">=0.9.0"}` in `fabric.mod.json`, and a required dependency on your
Modrinth and CurseForge pages, so launchers install it with your mod. Players then get Oriel fixes without waiting for
your next release.

You can also bundle Oriel in your jar, so players install one file:

```groovy
// NeoForge (ModDevGradle)
dependencies { jarJar(implementation("dev.oriel:oriel-neoforge-26.3:0.9.0")) }
// Fabric (Loom); on 1.21.1, modImplementation instead of implementation
dependencies { include(implementation("dev.oriel:oriel-fabric-26.3:0.9.0")) }
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

Oriel's payloads are optional on both loaders: a server with Oriel accepts clients without it, and the other way
round. `OrielServer.open` then returns a session that is already closed.

## Stability

The API is `dev.oriel.mod.server.OrielServer`, `OrielSession`, the settings file's keys, and `dev.oriel.mod.client.OrielScreens`,
`OrielScreen`, `OrielContainerScreen`, `OrielHud`, `OrielEntities`, `OrielAutomation` and `DocumentDriver`'s
public methods. Other classes are
internal. Oriel is at 0.x: expect changes, which will be listed in the changelog.
