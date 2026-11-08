package dev.oriel.kit;

import dev.oriel.engine.host.PageControllers;
import dev.oriel.kit.preset.*;

/** Registration for the optional reference distribution; absent from slim JARs. */
final class ReferenceKit {
    private ReferenceKit() {}

    static void register() {
        PageControllers.register(OrielKit.GALLERY, GalleryPreset::new);
        PageControllers.register(OrielKit.INVENTORY, InventoryPreset::new);
        PageControllers.register(OrielKit.FORGE, ForgePreset::new);
        PageControllers.register(OrielKit.MUSIC, MusicPreset::new);
        PageControllers.register(OrielKit.SETTINGS, SettingsPreset::new);
        PageControllers.register(OrielKit.POTIONS, PotionHudPreset::new);
        PageControllers.register(OrielKit.INVENTORY_EFFECTS, InventoryEffectsPreset::new);
        PageControllers.register(OrielKit.EDITOR, EditorPreset::new);
        PageControllers.register(OrielKit.CATALOG, CatalogPreset::new);
        PageControllers.register(OrielKit.ICONS, IconsPreset::new);
        PageControllers.register(OrielKit.STYLES, () -> new StylesPreset(true));
        PageControllers.register(OrielKit.FORGE_STATION, ForgeStationPreset::new);
        for (String look : StylesPreset.PAGES) {
            PageControllers.register(OrielKit.page("styles/" + look), () -> new StylesPreset(false));
            PageControllers.register(TemplateCatalog.page(look), () -> new StylesPreset(false));
        }
    }
}
