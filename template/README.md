# Oriel mod template

模板不会自动转译 H5，也不会替业务生成权限校验与菜单。开发见 [制作流程](../docs/zh/ui-workflow.md)、[Java API](../docs/zh/java-api.md)，完成后按 [验收工具](../docs/zh/validation.md)验证。

Two small mods to start from, one for NeoForge and one for Fabric, on Minecraft 26.3. Each adds a `/notes` command that
opens a notes page: the mod gives the page your notes, and the page sends back what you add and remove.

![The notes page](notes.png)

## Start a mod

Copy the folder for your loader:

```bash
cp -R template/neoforge my-mod
```

(or `template/fabric`). Then in `my-mod`:

1. Set `mod_id`, `mod_name`, `mod_group_id` and the rest in `gradle.properties`.
2. Rename the `com.example.examplemod` package and the `assets/examplemod` folder to match. On NeoForge, `MOD_ID` in
   `ExampleModClient` has to match too.
3. `./gradlew runClient`, open a world and type `/notes`.

On macOS, if the client hangs on its first frame, add `enableVsync:false` to `runs/client/options.txt`.

## What's in it

- `src/main/resources/assets/examplemod/oriel/notes.html` is the page: HTML, CSS and a Vue-style template, with a
  few lines of script.
- `Notes.java` opens the page with its data and handles the messages it sends.
- `ExampleModClient.java` registers `/notes`.
- `build.gradle` gets Oriel from this checkout's `build/template-maven` or `mavenLocal()`. Run the repository
  `./gradlew templateCompile` to publish current artifacts into the private build directory and compile both templates.
  For a copied project, run `publishToMavenLocal` in this repository or pass `-PorielRepository=<local repository>`.
- Supported 0.9 dependency ranges are `>=0.9.0 <0.10.0` (Fabric) and `[0.9.0,0.10.0)` (NeoForge). A pre-1.0
  incompatible change increments the minor version. Required dependencies are recommended; Jar-in-Jar is optional.
  The current API-boundary migration is 0.10.0, so these templates now use `>=0.10.0 <0.11.0` / `[0.10.0,0.11.0)`.
- `neoforge.mods.toml` or `fabric.mod.json` makes Oriel a required dependency. Players install it like any other
  mod, and launchers do it for them when you list it as a required dependency on your Modrinth and CurseForge pages.
  To ship one jar instead, bundle Oriel in yours ([Depending on Oriel](../docs/api/setup.md#depending-on-oriel)).

While `runClient` is running, saving `notes.html` reloads the open page, so you can change it without restarting
the game. The [previewer](../preview/README.md) shows a page without launching Minecraft at all.

## Next

- [Opening pages](../docs/api/pages.md): data, messages, opening pages from the server.
- [Containers](../docs/api/containers.md): inventories with real slots.
- [Scripting](../docs/SCRIPTING.md): the JavaScript and templates.
- [All docs](../docs/index.md)
