package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import dev.oriel.mod.client.OrielAutomation;
import dev.oriel.mod.client.OrielEntities;
import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.client.OrielScreen;
import dev.oriel.mod.client.OrielScreens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.Window;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.inspect.Inspection;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielConfig;
import dev.oriel.mod.internal.client.replaced.SceneContent;
import dev.oriel.mod.server.OrielServer;
import dev.oriel.mod.server.OrielSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Dev-only visual check ({@code ./gradlew :neoforge:26.3:runClient -Pautopilot}, or {@code :fabric:26.3}, {@code :neoforge:26.1}, {@code :fabric:26.1},
 * i.e. {@code -Doriel.autopilot=true}): creates a superflat creative world, opens the canvas test, every showcase
 * page and demo, screenshots each to {@code runs/client/screenshots/oriel_<name>.png} at GUI scale 2 (the canvas
 * test, the shapes page and the 3D pages also at 3), logs the frame rate of each (and of benchmark pages of 3D content), and quits.
 *
 * <p>It drives pages with {@link OrielAutomation}: hovers the title screen's first button for a burst of screenshots
 * a tick apart, drags a turntable, opens a page under the resting cursor (hovered within its first frames, without a
 * mouse move), hovers items and titles for their tooltips, checks that Chronicle's map pins show their titles at once
 * and what the narrator reads on its conversation, closes a page with a mod's key ({@link DocumentDriver#onKey}),
 * fills in the templates demo, clicks a Mobdex row scrolled out of its list and scrolls the list back, and answers the
 * demo toast overlay through chat. It waits for pages to settle rather than
 * for a fixed time, checks each result ({@link #check}), and logs how many checks failed when it finishes. Its own
 * pages are in {@code assets/oriel/oriel/dev/}: portraits whose armour stands get the autopilot's render state
 * ({@link OrielEntities}: arms, one raised, and no base plate), and a conversation card comparing soft and default
 * gazes at a pointer resting far below the speakers.
 */
public final class DevAutopilot {
    public static final boolean ENABLED = Boolean.getBoolean("oriel.autopilot");
    /** With {@code -Pautopilot=scene}, only the scene showcase's steps run ({@link #scene}), for quick looks. */
    private static final String ONLY = System.getProperty("oriel.autopilot.only", "");
    /**
     * {@code -Pautopilot=play}: hands-on acceptance. The same fresh test world, set up with things to try (an enchanted
     * sword in hand, a few items, effects), the Oriel home page open, and then the player is in control: no pointer
     * taken, no automatic steps, no quitting. With {@code oriel.play.test} it screenshots the ready state and quits
     * (to check the mode itself).
     */
    private static final boolean PLAY = ONLY.equals("play");
    /**
     * The fewest ticks before a page's screenshot, so the frame rate logged with it (counted over a second) is the
     * page's own; and how long to wait for a screen that is not a Oriel page.
     */
    private static final int SETTLE = 20;
    /** The most ticks to wait for a condition ({@link #until}) before going on without it. */
    private static final int MAX_POLL = 200;
    /**
     * The most ticks to wait for a page to settle. Some never do: the showcase HUD's timers keep starting transitions
     * and animations, as a game's HUD would.
     */
    private static final int MAX_SETTLE = 60;
    /** The demo toast's Allow button. */
    private static final String ALLOW = ".actions button:first-child";

    private static boolean started, loaded, finished;
    private static final Deque<Runnable> steps = new ArrayDeque<>();
    private static int wait;
    /** How many render states the autopilot's armour stand function made. */
    private static int standStates;
    /** The checks made so far ({@link #check}), how many of them failed, and the checks left out ({@link #skip}). */
    private static int checks, failures, skipped;
    private static int smokePageErrors;

    private DevAutopilot() {}

    /** Client tick hook; the loaders call this every client tick when {@link #ENABLED}. */
    public static void tick(Minecraft mc) {
        if (!ENABLED || finished) return;
        if (!started) {
            // A fresh run directory first shows the accessibility onboarding screen instead of the title screen.
            if (!(McClient.screen() instanceof TitleScreen || McClient.screen() instanceof AccessibilityOnboardingScreen)) return;
            started = true;
            // The pointer is the autopilot's from here on, wherever the real mouse is (not in play mode: it is yours).
            if (!PLAY) DevPointer.own();
            mc.options.onboardingAccessibilityFinished();
            // The window may never have focus while the autopilot runs; don't pause or show tutorial toasts.
            mc.options.pauseOnLostFocus = false;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            // Uncapped frame rates, so the logged fps measure what the pages cost (play mode keeps your settings).
            if (!PLAY) {
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
            }
            String id = PLAY ? "oriel-play" : "oriel-autopilot";
            LevelSettings settings = new LevelSettings(id, GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(id, settings, new WorldOptions(0, false, false),
                    //? if >=26.2 {
                    WorldPresets::createTestWorldDimensions, McClient.screen());
                    //?} else
                    //WorldPresets::createFlatWorldDimensions, McClient.screen());
            return;
        }
        if (!PLAY) DevPointer.hold(); // after screens the server opened
        if (!loaded) {
            if (mc.player == null || mc.level == null) return;
            loaded = true;
            plan(mc);
            wait = 60;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Runnable step = steps.poll();
        if (step == null && PLAY && !Boolean.getBoolean("oriel.play.test")) {
            finished = true;
            Constants.LOG.info("Oriel play mode: ready, over to you");
            return;
        }
        if (step == null) {
            finished = true;
            String skips = skipped == 0 ? "" : ", " + skipped + " skipped (not ported to this Minecraft version)";
            if (failures == 0) Constants.LOG.info("Oriel autopilot finished: {} checks, {} failed{}", checks, failures, skips);
            else Constants.LOG.error("Oriel autopilot finished: {} checks, {} failed{}", checks, failures, skips);
            mc.stop();
            return;
        }
        try {
            step.run();
        } catch (RuntimeException e) {
            fail("a step failed", e);
        }
        if (!PLAY) DevPointer.hold(); // after screens the step opened, before their first frame
    }

    /** Small cross-version acceptance run: the home page and component catalog, then normal client shutdown. */
    private static void smoke(Minecraft mc) {
        Constants.LOG.info("Oriel smoke started");
        for (String page : List.of("gallery", "catalog")) {
            shoot(mc, "smoke_" + page, () -> OrielClientCommands.preset(page));
            Path screenshot = mc.gameDirectory.toPath().resolve("screenshots/oriel_smoke_" + page + ".png");
            until("smoke " + page + " screenshot saved", ticks -> Files.isRegularFile(screenshot));
            steps.add(() -> {
                Screen screen = McClient.screen();
                DocumentDriver driver = screen == null ? null : DocumentDriver.of(screen);
                Document doc = driver == null ? null : driver.document();
                int errors = driver == null ? 1 : driver.pageErrorCount();
                int warnings = doc == null ? 0 : doc.diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.WARNING)
                        - doc.diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.ERROR);
                smokePageErrors += errors;
                Constants.LOG.info("Oriel smoke page {}: {} errors, {} warnings, screenshot {}",
                        page, errors, warnings, screenshot.toAbsolutePath());
                check(doc != null && doc.url().endsWith("/" + page + ".html"),
                        "smoke {} opened", "smoke {} did not open its document", page);
                check(errors == 0, "smoke {} has no page errors", "smoke {} has page errors", page);
                check(Files.isRegularFile(screenshot), "smoke {} screenshot saved",
                        "smoke {} screenshot is missing", page);
            });
        }
        steps.add(() -> Constants.LOG.info("Oriel smoke finished: {} page errors", smokePageErrors));
    }

    private static void plan(Minecraft mc) {
        OrielEntities.registerPortraitState(EntityTypes.ARMOR_STAND, (stand, partialTick) -> {
            if (!(mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand, partialTick)
                    instanceof ArmorStandRenderState state)) return null;
            standStates++;
            state.showArms = true;
            state.showBasePlate = false;
            state.rightArmPose = new Rotations(-150, 0, 15);
            return state;
        });
        command(mc, "gamerule send_command_feedback false");
        command(mc, "time set 6000");
        awardKills(mc);
        command(mc, "weather clear 100000");
        if (DevTour.ENABLED) {
            DevTour.plan(mc, steps);
            return;
        }
        guiScale(mc, 2);
        if (ONLY.equals("mcp")) {
            mcpPage(mc);
            return;
        }
        if (ONLY.equals("smoke")) {
            smoke(mc);
            return;
        }
        if (ONLY.equals("scene")) {
            scene(mc);
            return;
        }
        if (ONLY.equals("inspector")) {
            inspector(mc);
            return;
        }
        if (ONLY.equals("oriel")) {
            presets(mc);
            return;
        }
        if (ONLY.equals("home")) {
            home(mc);
            return;
        }
        if (ONLY.equals("reload")) {
            hotReload(mc);
            return;
        }
        if (ONLY.equals("text")) {
            text(mc);
            return;
        }
        if (ONLY.equals("effects")) {
            effects(mc);
            return;
        }
        if (ONLY.equals("styles")) {
            styles(mc);
            return;
        }
        if (ONLY.equals("forge")) {
            forgeStation(mc);
            return;
        }
        if (ONLY.equals("particles")) {
            // The catalog's particle demos ({@code -Pautopilot=particles}): their canvases must show particles in game.
            shoot(mc, "particles_catalog", () -> OrielClientCommands.preset("catalog"));
            onPage("the particles section", p -> p.click("#list .item[data-sec=particles]"));
            pause(60);
            grab(mc, "particles_section", 5);
            pause(40);
            grab(mc, "particles_section_later", 5);
            return;
        }
        if (PLAY) {
            play(mc);
            return;
        }
        serverPages(mc);
        shoot(mc, "canvastest", () -> McClient.setScreen(new CanvasTestScreen()));
        guiScale(mc, 3);
        shoot(mc, "canvastest_gui3", () -> {});
        guiScale(mc, 2);
        for (String page : OrielClientCommands.SHOWCASE) {
            if (!page.equals("scene")) shoot(mc, "showcase_" + page, () -> OrielScreens.open(OrielClientCommands.showcaseUrl(page)));
        }
        scene(mc);
        inspector(mc);
        // The 3D pages again at GUI scale 3, where models and entities are drawn at a different resolution, and the
        // turntables a second later, having turned.
        guiScale(mc, 3);
        shoot(mc, "showcase_models_gui3", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("models")));
        shoot(mc, "showcase_models_gui3_later", () -> {});
        shoot(mc, "showcase_mobdex_gui3", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("mobdex"),
                JsonParser.parseString("{\"start\": {\"mob\": \"ghast\"}}")));
        guiScale(mc, 2);
        shoot(mc, "showcase_mobdex_unseen", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("mobdex"),
                JsonParser.parseString("{\"start\": {\"mob\": \"warden\"}}")));
        mobdexList(mc);
        // Dragging the Turntable's big model (rotatable): it turns and tilts.
        open("the models page", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("models")));
        steps.add(() -> check(OrielAutomation.screen().map(page -> page.drag("model[rotatable]", 60, 20)).orElse(false),
                "dragged the Turntable's model", "could not drag the Turntable's model"));
        shoot(mc, "showcase_models_dragged", () -> OrielAutomation.screen().ifPresent(OrielAutomation::leave));
        portraits(mc);
        // What 3D content costs: the logged fps of 48 spinning entities, models and items (2D, for comparison).
        for (String bench : List.of("entity type='minecraft:zombie'", "model block='minecraft:chest'", "item id='minecraft:chest'")) {
            String tag = bench.split(" ")[0];
            String cell = "<" + bench + " style='width: 48px; height: 48px; animation: spin 4s linear infinite'></" + tag + ">";
            String page = "<style>@keyframes spin { to { -mc-yaw: 360deg } } body { display: flex; flex-wrap: wrap }</style>"
                    + cell.repeat(48);
            shoot(mc, "bench_" + tag, () -> OrielScreens.openInline(page, null));
        }
        open("the title screen", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("title")));
        hover(".panel button");
        for (int i = 0; i < 4; i++) grab(mc, "showcase_title_hover_" + i, 0);
        leave(); // or the next pages open hovered
        firstHover(mc);
        folderPages(mc);
        dataPatches();
        tooltips(mc);
        chronicle(mc);
        modKeys();
        for (String demo : OrielClientCommands.DEMOS) {
            if (!demo.equals("hud")) shoot(mc, demo, () -> OrielClientCommands.demo(demo));
        }
        open("the templates demo", () -> OrielClientCommands.demo("templates"));
        onPage("the templates demo", page -> {
            page.click(".counter button:last-child");
            page.click(".counter button:last-child");
            page.click(".add input");
            page.type("Mine diamonds");
            page.key("Enter");
            page.leave(); // no hover or tooltip in the screenshot
            String state = page.eval("[state.count, state.todos.length]").map(Object::toString).orElse("none");
            check(state.equals("[2,3]"), "templates demo input works", "templates demo state is {}, expected [2,3]", state);
        });
        settle("the filled-in templates demo", 0);
        grab(mc, "templates_input", 5);
        shoot(mc, "chest", () -> {
            McClient.setScreen(null);
            command(mc, "oriel showcase chest");
        });
        // The demo HUD's card fades in and out over four seconds, then closes: shoot it a second in, not settled.
        steps.add(() -> {
            McClient.setScreen(null);
            OrielHud.show(OrielClient.DEMO_HUD);
        });
        steps.add(() -> wait = SETTLE);
        grab(mc, "hud", 5);
        steps.add(() -> OrielHud.hide(OrielClient.DEMO_HUD));
        toast(mc);
        // Last, so that nothing after it depends on the time it takes.
        guiScale(mc, 3);
        shoot(mc, "shapes_gui3", () -> OrielScreens.open(devUrl("shapes")));
        guiScale(mc, 2);
        shoot(mc, "shapes", () -> OrielScreens.open(devUrl("shapes")));
    }

    /**
     * The scene showcase: the finished cottage (opened at its last step) at GUI scale 2 and 3, and the build part way
     * through. Checks that it drew its blocks, and that hovering one fires {@code blockhover} and shows its name.
     */
    private static void scene(Minecraft mc) {
        String url = OrielClientCommands.showcaseUrl("scene");
        leave();
        shoot(mc, "showcase_scene", () -> OrielScreens.open(url, JsonParser.parseString("{\"start\": {\"step\": \"done\"}}")));
        steps.add(() -> {
            int quads = sceneQuads();
            check(quads > 300, "the scene drew {} block quads", "the scene drew only {} block quads", quads);
        });
        hover("scene");
        steps.add(() -> {
            String hovered = OrielAutomation.screen().flatMap(p -> p.eval("window.lastHover || ''"))
                    .map(JsonElement::getAsString).orElse("");
            check(!hovered.isEmpty(), "hovering the scene fired blockhover for {}", "hovering the scene fired no blockhover",
                    hovered);
        });
        steps.add(() -> check(shownNow(), "the hovered block's name shows", "no tooltip over the scene's block"));
        grab(mc, "showcase_scene_hover", 0);
        leave();
        shoot(mc, "showcase_scene_step", () -> OrielScreens.open(url, JsonParser.parseString("{\"start\": {\"step\": 4}}")));
        guiScale(mc, 3);
        shoot(mc, "showcase_scene_gui3", () -> OrielScreens.open(url, JsonParser.parseString("{\"start\": {\"step\": \"done\"}}")));
        guiScale(mc, 2);
    }

    /**
     * The element inspector (F12) over the shop: it opens docked on the right and lists the page, picking highlights
     * what is under the pointer, a click on a card selects it or what is in it (and ends picking), dragging the panel's
     * edge widens it, and F12 closes it again.
     */
    private static void inspector(Minecraft mc) {
        leave();
        shoot(mc, "inspector_shop", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("shop")));
        onPage("the inspector", page -> page.key("F12"));
        until("the inspector inspects the page", ticks -> inspection().map(i -> i.document() != null).orElse(false));
        steps.add(() -> inspection().ifPresent(i -> i.pick(true)));
        hover(".wares .card");
        grab(mc, "inspector_pick", 5);
        onPage("picking", page -> page.click(".wares .card"));
        steps.add(() -> {
            Element picked = inspection().map(Inspection::highlighted).orElse(null);
            boolean picking = inspection().map(Inspection::picking).orElse(true);
            Element card = picked;
            while (card != null && !card.classes().contains("card")) card = card.parentElement();
            check(card != null && !picking, "a click picked {} in a card", "picking a card selected {}", picked);
        });
        leave();
        grab(mc, "inspector_selected", 10);
        steps.add(() -> {
            Screen screen = McClient.screen();
            DocumentDriver driver = screen == null ? null : DocumentDriver.of(screen);
            DevInspector inspector = driver == null ? null : (DevInspector) driver.inspector();
            if (inspector == null) {
                fail("no inspector to resize");
                return;
            }
            int edge = inspector.split(), to = edge - 60, y = 100;
            driver.mouseClicked(new MouseButtonEvent(edge, y, new MouseButtonInfo(0, 0)));
            driver.mouseMoved(to, y);
            driver.mouseReleased(new MouseButtonEvent(to, y, new MouseButtonInfo(0, 0)));
            check(inspector.split() == to, "dragging the panel's edge moved it to {}",
                    "dragging the panel's edge left it at {}", inspector.split());
        });
        grab(mc, "inspector_wider", 5);
        onPage("closing the inspector", page -> page.key("F12"));
        steps.add(() -> check(inspection().isEmpty(), "F12 closed the inspector", "the inspector is still open"));
    }

    /** The open screen's inspector, while it is open. */
    private static Optional<Inspection> inspection() {
        Screen screen = McClient.screen();
        DocumentDriver driver = screen == null ? null : DocumentDriver.of(screen);
        return Optional.ofNullable(driver == null ? null : driver.inspector()).map(i -> ((DevInspector) i).inspection);
    }

    /** How many block quads the open page's scene drew in its last frame. */
    private static int sceneQuads() {
        Screen screen = McClient.screen();
        DocumentDriver driver = screen == null ? null : DocumentDriver.of(screen);
        Document doc = driver == null ? null : driver.document();
        Element scene = doc == null ? null : doc.querySelector("scene");
        return scene != null && scene.replaced instanceof SceneContent content ? content.drawnQuads() : 0;
    }

    /**
     * The autopilot's portraits page, the conversation card, and the Turntable's portraits watching the pointer over
     * the stage's caption, at GUI scales 2 and 3 (where they are sharper).
     */
    private static void portraits(Minecraft mc) {
        for (int scale : new int[] {2, 3}) {
            String suffix = scale == 2 ? "" : "_gui3";
            guiScale(mc, scale);
            shoot(mc, "portraits" + suffix, () -> OrielScreens.open(devUrl("portraits")));
            // The conversation card with the pointer on its reply, far below the speakers.
            steps.add(() -> OrielScreens.open(devUrl("conversation")));
            settle("portraits_conversation" + suffix, SETTLE);
            hover(".card button");
            grab(mc, "portraits_conversation" + suffix, 5);
            open("the models page", () -> OrielScreens.open(OrielClientCommands.showcaseUrl("models")));
            hover(".stage p");
            grab(mc, "showcase_models_portraits" + suffix, 5);
        }
        guiScale(mc, 2);
        steps.add(() -> check(standStates > 0, "armour stands drawn from the registered render state",
                "the registered armour stand render state was never used"));
    }

    /**
     * Opens the first hover page ({@code dev/first_hover.html}) with the cursor resting at the window's centre, as a
     * screen opening leaves it: the mouse handler has the position, and no move event reaches the screen (Minecraft
     * drops the first move after a screen opens). {@link DevPointer} keeps it there as the screen opens, wherever the
     * real mouse is. The button must be hovered within the first frames, with no mouse movement; half a second later
     * its title tooltip shows, and the screenshot has both.
     */
    private static void firstHover(Minecraft mc) {
        steps.add(() -> {
            Window w = mc.getWindow();
            McClient.moveMouse(w.getScreenWidth() / 2.0, w.getScreenHeight() / 2.0); // the position without a move event
            OrielScreens.open(devUrl("first_hover"));
        });
        // The page may take a tick or two to load and draw; judge its first frames once it has drawn a few.
        until("the first hover page drew its first frames", ticks -> OrielAutomation.screen()
                .flatMap(page -> page.eval("frames")).map(JsonElement::getAsInt).orElse(0) >= 4);
        onPage("the first hover page", page -> {
            int at = page.eval("hoveredAt").map(JsonElement::getAsInt).orElse(-1);
            check(at >= 1 && at <= 3, "a page opened under a resting cursor is hovered from frame {}",
                    "the button under the resting cursor was not hovered in its first frames (hovered at frame {})", at);
        });
        settle("the first hover's tooltip", 0);
        grab(mc, "first_hover", 0);
        leave();
    }

    /**
     * Tooltips on the shop row page ({@code dev/shop_row.html}): an {@code <item tooltip>} in a row with a
     * {@code title-json} (the item's lines, then the row's, in one box, none wrapped); the same row's title alone
     * beside the item (wrapped at 170 px); a {@code title-nowrap} title; and the showcase shop's wares, whose items
     * show their tooltip with the card's rarity and price.
     */
    private static void tooltips(Minecraft mc) {
        open("the shop row", () -> OrielScreens.open(devUrl("shop_row")));
        hover("#row item");
        steps.add(() -> check(OrielAutomation.screen().map(OrielAutomation::tooltipShown).orElse(false),
                "the item's tooltip shows with the row's lines", "no tooltip over the shop row's item"));
        grab(mc, "tooltip_item_title", 0);
        hover("#row");
        grab(mc, "tooltip_title_wrapped", 0);
        hover("#nowrap");
        grab(mc, "tooltip_title_nowrap", 0);
        steps.add(() -> OrielScreens.open(OrielClientCommands.showcaseUrl("shop")));
        settle("the shop", SETTLE);
        hover(".card:first-child item");
        grab(mc, "showcase_shop_tooltip", 0);
        leave();
    }

    /**
     * Chronicle's map and conversation ({@code dev/chronicle.html}). Tooltips: a map pin shows its title on the first
     * frame after the pointer arrives (the map's {@code -mc-tooltip-delay: 0ms}), while a row with the default delay
     * shows nothing then and its title half a second later. Narration, recorded rather than heard
     * ({@link OrielAutomation#recordNarration}): the screen reads the page's title, then a hovered reply as vanilla
     * reads a button ("Reply 1: About the letter button"); the log reads its last line when the page opens, then each
     * new line, the reply's first and the answer a moment later; the aria-hidden flourish is never read.
     */
    private static void chronicle(Minecraft mc) {
        String greeting = "Emperor Cualius. Greetings, stranger! Have you done what I asked?";
        String answer = "Emperor Cualius. Then take it to Candacona, and quickly.";
        open("the Chronicle page", () -> {
            OrielAutomation.recordNarration().clear();
            OrielScreens.open(devUrl("chronicle"));
        });
        moveOnto("#rauca");
        steps.add(() -> check(shownNow(), "a map pin's title shows on the first frame (-mc-tooltip-delay: 0ms)",
                "a map pin's title did not show at once"));
        moveOnto("#note");
        steps.add(() -> check(!shownNow(), "a row with the default delay shows no title on the first frame",
                "a row with the default delay showed its title at once"));
        settle("the note's tooltip", 0);
        steps.add(() -> check(shownNow(), "and shows it once its half second is up", "the note's title never showed"));
        grab(mc, "chronicle_tooltip", 0);
        onPage("the Chronicle page", page -> {
            String narration = page.narration().orElse("");
            check(narration.startsWith("Emperor Cualius"), "the narrator reads the page's title: '{}'",
                    "the narration '{}' does not start with the page's title", narration);
        });
        hover("#reply");
        onPage("the Chronicle page", page -> {
            String reply = Component.translatable("gui.narrate.button", "Reply 1: About the letter").getString();
            String narration = page.narration().orElse("");
            check(narration.contains(reply) && !narration.contains("~"), "the narration '{}' reads the hovered reply as '{}'",
                    "the narration '{}' has no '{}', or reads the aria-hidden flourish", narration, reply);
            page.click("#reply");
        });
        until("the log's answer was narrated", ticks -> OrielAutomation.recordNarration().contains(answer));
        steps.add(() -> {
            List<String> recorded = OrielAutomation.recordNarration();
            check(recorded.equals(List.of(greeting, "You. About the letter.", answer)),
                    "the log reads its last line on opening, then each new line: {}",
                    "the log narrated {}, expected its greeting, the reply and the answer", recorded);
            OrielAutomation.stopRecordingNarration();
        });
        leave();
    }

    /** Moves the pointer onto the first element of the open page matching {@code selector}, without waiting. */
    private static void moveOnto(String selector) {
        steps.add(() -> {
            if (!OrielAutomation.screen().map(page -> page.hover(selector)).orElse(false)) fail("nothing to hover at {}", selector);
        });
    }

    /** Whether the open page's last frame showed a tooltip. */
    private static boolean shownNow() {
        return OrielAutomation.screen().map(OrielAutomation::tooltipShown).orElse(false);
    }

    /**
     * A mod's key on Chronicle's book ({@code dev/mod_keys.html}, {@link DocumentDriver#onKey}): a handler that
     * closes the page on J. Typed into a focused field, J is text and the handler is not asked; neither is it for a
     * key the page cancels. With nothing focused, J reaches it and the page closes.
     */
    private static void modKeys() {
        OrielScreen[] book = new OrielScreen[1];
        List<String> asked = new ArrayList<>();
        open("the mod keys page", () -> {
            book[0] = OrielScreens.open(devUrl("mod_keys"));
            book[0].driver().onKey(e -> {
                String key = KeyNames.key(e);
                asked.add(key);
                if (!key.equals("j")) return false;
                book[0].onClose();
                return true;
            });
        });
        onPage("the mod keys page", page -> {
            page.click("#name");
            page.type("j");
            String typed = page.eval("document.getElementById('name').value").map(JsonElement::getAsString).orElse("");
            page.click("p"); // nothing focused
            page.leave();
            page.key("ArrowLeft");
            check(typed.equals("j") && asked.isEmpty() && McClient.screen() == book[0],
                    "keys the page uses (typing, a cancelled key) don't reach the mod's handler",
                    "the field has '{}', the mod's handler was asked about {}", typed, asked);
            page.key("j");
            check(McClient.screen() != book[0] && asked.equals(List.of("j")), "the mod's key handler closed the page",
                    "J left the page open; the mod's handler was asked about {}", asked);
        });
    }

    /**
     * In the open Mobdex: clicks the list's last row, which is scrolled out of the list, so the click has to scroll it
     * into view to land; then turns the wheel over the list and scrolls its first row back into view.
     */
    private static void mobdexList(Minecraft mc) {
        String last = "#list .row:last-child";
        onPage("the Mobdex", page -> {
            float[] list = page.rect("#list").orElse(null), row = page.rect(last).orElse(null);
            boolean hidden = list != null && row != null && row[1] >= list[1] + list[3];
            boolean clicked = page.click(last);
            page.leave();
            boolean selected = page.eval("state.sel === filtered()[filtered().length - 1].id")
                    .map(JsonElement::getAsBoolean).orElse(false);
            check(hidden && clicked && selected, "clicking a scrolled-out row works",
                    "Mobdex row click: scrolled out {}, clicked {}, selected {}", hidden, clicked, selected);
        });
        settle("the Mobdex selection", 0);
        grab(mc, "showcase_mobdex_last", 0);
        onPage("the Mobdex", page -> {
            if (!page.wheel("#list", -2)) fail("no wheel over the Mobdex list");
            page.leave();
        });
        settle("the Mobdex list's scroll", 0);
        onPage("the Mobdex", page -> {
            double wheeled = scrollTop(page);
            boolean shown = page.scrollIntoView("#list .row:first-child");
            double top = scrollTop(page);
            check(wheeled < listMax(page) && shown && top == 0, "wheel and scrollIntoView work (list scrolled to {}, then 0)",
                    "Mobdex list at {} after the wheel, {} after scrollIntoView ({})", wheeled, top, shown);
        });
    }

    private static double scrollTop(OrielAutomation page) {
        return page.eval("document.getElementById('list').scrollTop").map(JsonElement::getAsDouble).orElse(-1.0);
    }

    /** How far the Mobdex list scrolls: where clicking its last row left it. */
    private static double listMax(OrielAutomation page) {
        return page.eval("document.getElementById('list').scrollHeight - document.getElementById('list').clientHeight")
                .map(JsonElement::getAsDouble).orElse(-1.0);
    }

    /**
     * The demo toast overlay takes the pointer only while chat is open: hovers and clicks its Allow button through
     * {@link OrielAutomation#hud} with chat open (and checks that it takes none without), screenshots the hover and
     * its tooltip, checks the overlay narrates the hovered button, and checks the answer arrives and the toast closes.
     */
    private static void toast(Minecraft mc) {
        Supplier<Optional<OrielAutomation>> toast = () -> OrielAutomation.hud(OrielClient.DEMO_TOAST);
        String[] answer = new String[1];
        String allow = Component.translatable("gui.narrate.button", "Allow").getString();
        steps.add(() -> OrielAutomation.recordNarration().clear());
        steps.add(() -> {
            McClient.setScreen(null);
            OrielHud.show(OrielClient.DEMO_TOAST).onMessage("answer", value -> answer[0] = value.getAsString());
        });
        settle("the toast", toast, 0);
        steps.add(() -> {
            check(!toast.get().map(page -> page.hover(ALLOW)).orElse(false), "the toast takes no pointer with no screen open",
                    "the toast took the pointer with no screen open");
            McClient.setScreen(chatScreen());
        });
        until("the toast took the pointer over chat", ticks -> toast.get().map(page -> page.hover(ALLOW)).orElse(false));
        settle("the toast's hover and tooltip", toast, 0);
        steps.add(() -> check(toast.get().flatMap(page -> page.eval("document.querySelector('" + ALLOW + "').matches(':hover')"))
                .map(JsonElement::getAsBoolean).orElse(false), "the toast's Allow button is hovered",
                "the toast's Allow button is not hovered"));
        grab(mc, "toast_hover", 0);
        // The chat screen under the toast knows nothing of it, so the overlay reads its hovered button itself.
        until("the toast's hovered button was narrated",
                ticks -> OrielAutomation.recordNarration().stream().anyMatch(s -> s.startsWith(allow)));
        steps.add(() -> {
            List<String> said = OrielAutomation.recordNarration();
            check(said.stream().anyMatch(s -> s.startsWith(allow)), "the toast overlay narrated {}, reading its hovered '{}'",
                    "the toast overlay narrated {}, nothing starting '{}'", said, allow);
            OrielAutomation.stopRecordingNarration();
        });
        steps.add(() -> {
            if (!toast.get().map(page -> page.click(ALLOW)).orElse(false)) fail("could not click the toast's Allow button");
        });
        until("the toast closed", ticks -> !OrielHud.isShown(OrielClient.DEMO_TOAST));
        steps.add(() -> {
            check("once".equals(answer[0]), "HUD overlay input works", "the toast answered {}, expected once", answer[0]);
            if (OrielHud.isShown(OrielClient.DEMO_TOAST)) OrielHud.hide(OrielClient.DEMO_TOAST);
            McClient.setScreen(null);
        });
    }

    /**
     * Folder pages ({@link OrielPages}): a page, its stylesheet and an image written to a folder as a player would,
     * a stylesheet linked into the folder from outside it (which must be refused), and an edit, which must reload the
     * open page.
     */
    private static void folderPages(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("oriel-autopilot-folder");
        Path page = dir.resolve("page.html");
        steps.add(() -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(page, folderPage("first"));
                Files.writeString(dir.resolve("style.css"), "p { color: rgb(0, 128, 0); }");
                Path outside = dir.resolveSibling("oriel-autopilot-outside.css");
                Files.writeString(outside, "p { font-weight: bold; }");
                Path link = dir.resolve("escape.css");
                Files.deleteIfExists(link);
                Files.createSymbolicLink(link, outside);
                try (InputStream in = mc.getResourceManager().getResource(Constants.id("icon.png")).orElseThrow().open()) {
                    Files.write(dir.resolve("pic.png"), in.readAllBytes());
                }
            } catch (IOException | UnsupportedOperationException e) {
                fail("cannot write the folder page: {}", e.toString());
                return;
            }
            if (!OrielPages.isFolder(FOLDER)) OrielPages.folder(FOLDER, dir);
            check(refused(() -> OrielPages.folder(FOLDER, dir)) && refused(() -> OrielPages.folder(Constants.MOD_ID, dir))
                            && refused(() -> OrielPages.folder("minecraft", dir)),
                    "a folder can't take a namespace that is registered or belongs to a mod",
                    "a folder took a namespace that is registered or belongs to a mod");
            OrielScreens.open(FOLDER + ":page.html");
        });
        settle("the folder page", 0);
        onPage("the folder page", p -> {
            String text = p.eval("document.querySelector('p').textContent").map(JsonElement::getAsString).orElse("");
            String color = p.eval("getComputedStyle(document.querySelector('p')).color").map(JsonElement::getAsString).orElse("");
            String weight = p.eval("getComputedStyle(document.querySelector('p')).fontWeight").map(JsonElement::getAsString).orElse("");
            double image = p.eval("document.querySelector('img').getBoundingClientRect().width").map(JsonElement::getAsDouble).orElse(0.0);
            check(text.equals("first") && color.equals("rgb(0, 128, 0)") && image > 0,
                    "a folder page loaded with its stylesheet and image ('{}', {}, {} px)",
                    "the folder page didn't load with its stylesheet and image: text '{}', color {}, image {} px", text, color, image);
            check(!weight.equals("bold") && !weight.equals("700"), "a stylesheet linked from outside the folder was refused",
                    "a stylesheet linked from outside the folder was read (font-weight {})", weight);
        });
        grab(mc, "folder_page", 0);
        steps.add(() -> {
            try {
                Files.writeString(page, folderPage("second"));
            } catch (IOException e) {
                fail("cannot edit the folder page: {}", e.toString());
            }
        });
        until("the folder page reloaded after an edit", ticks -> folderText().equals("second"));
        onPage("the edited folder page", p -> check(folderText().equals("second"), "saving a folder page reloaded it",
                "saving a folder page didn't reload it: it still says '{}'", folderText()));
    }

    private static final String FOLDER = "oriel_autopilot";

    /**
     * {@link DocumentDriver#patch}: one field of a page's data changes in place, its binding and 'patch' listener see
     * it, and the page has it again after {@link DocumentDriver#reloadAll}, as {@code /oriel reload} and hot reload do.
     */
    private static void dataPatches() {
        String html = "<p id=hp>{{ hp }} {{ name }}</p><script>var seen = []; oriel.on('patch', p => seen.push(p.key));"
                + " oriel.on('data', (d, key) => seen.push('data ' + key))</script>";
        open("the page to patch", () -> OrielScreens.openInline(html, JsonParser.parseString("{\"hp\": 20, \"name\": \"Steve\"}")));
        steps.add(() -> {
            if (McClient.screen() instanceof OrielScreen v) v.driver().patch("hp", new JsonPrimitive(7));
            wait = 2;
        });
        onPage("the patched page", p -> {
            String text = patchedText(p), seen = p.eval("seen.join()").map(JsonElement::getAsString).orElse("");
            check(text.equals("7 Steve") && seen.equals("hp,data hp"), "a patch updated its binding and told the page ('{}', {})",
                    "a patch didn't reach the page as it should: text '{}', listeners saw {}", text, seen);
        });
        steps.add(DocumentDriver::reloadAll);
        settle("the reloaded patched page", 0);
        onPage("the reloaded patched page", p -> check(patchedText(p).equals("7 Steve"), "a patch was kept across a reload",
                "after a reload the patched page says '{}'", patchedText(p)));
        steps.add(() -> McClient.setScreen(null));
    }

    private static String patchedText(OrielAutomation page) {
        return page.eval("document.getElementById('hp').textContent").map(JsonElement::getAsString).orElse("");
    }

    private static String folderPage(String text) {
        return "<!doctype html><link rel=\"stylesheet\" href=\"style.css\"><link rel=\"stylesheet\" href=\"escape.css\">"
                + "<body class=\"mc-center\"><p>" + text + "</p><img src=\"pic.png\" style=\"height: 32px\"></body>";
    }

    private static String folderText() {
        return OrielAutomation.screen().flatMap(p -> p.eval("document.querySelector('p') ? document.querySelector('p').textContent : ''"))
                .map(JsonElement::getAsString).orElse("");
    }

    private static boolean refused(Runnable register) {
        try {
            register.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ---- Steps ----

    /** One of the autopilot's own pages, in {@code assets/oriel/oriel/dev/}. */
    private static String devUrl(String page) {
        return "oriel:oriel/dev/" + page + ".html";
    }

    /** Runs {@code open} and waits for the page it opens to settle. */
    private static void open(String what, Runnable open) {
        steps.add(open);
        settle(what, 0);
    }

    /**
     * The Oriel UI presets ({@code -Pautopilot=oriel}): the gallery and each preset, before and after some
     * interaction, screenshotted for acceptance. Animated pages never settle, so the waits are timed.
     */
    private static void presets(Minecraft mc) {
        shoot(mc, "preset_gallery", () -> OrielClientCommands.preset("gallery"));
        hover(".card[data-go=forge]");
        pause(8);
        grab(mc, "preset_gallery_hover", 5);
        onPage("the zoom into the forge", p -> p.click(".card.forge"));
        pause(3);
        grab(mc, "preset_gallery_zoom", 5);

        shoot(mc, "preset_catalog", () -> OrielClientCommands.preset("catalog"));
        onPage("the rarity section", p -> p.click("#list .item[data-sec=rarity]"));
        pause(30);
        grab(mc, "preset_catalog_rarity", 5);
        onPage("the 3D section", p -> p.click("#list .item[data-sec=models]"));
        pause(30);
        grab(mc, "preset_catalog_models", 5);
        onPage("the motion section", p -> {
            p.click("#list .item[data-sec=motion]");
            p.click("#acc-head");
        });
        pause(20);
        grab(mc, "preset_catalog_motion", 5);

        shoot(mc, "preset_inventory", () -> OrielClientCommands.preset("inventory"));
        hover("[data-i='0']");
        pause(6);
        grab(mc, "preset_inventory_tooltip", 5);
        onPage("the inventory drag", p -> p.drag("[data-i='3']", 60, 20));
        pause(10);
        grab(mc, "preset_inventory_dragged", 5);
        onPage("the inventory search", p -> {
            p.click("#search");
            p.type("面包");
        });
        pause(16);
        grab(mc, "preset_inventory_search", 5);

        shoot(mc, "preset_forge", () -> OrielClientCommands.preset("forge"));
        for (int i = 0; i < 3; i++) {
            onPage("a forge strike", p -> p.key(" "));
            pause(i < 2 ? 14 : 3);
        }
        grab(mc, "preset_forge_strike", 5);
        onPage("the quench", p -> p.click("#quench"));
        pause(30);
        grab(mc, "preset_forge_result", 5);

        shoot(mc, "preset_music", () -> OrielClientCommands.preset("music"));
        onPage("play", p -> p.click("#play"));
        pause(50);
        grab(mc, "preset_music_playing", 5);
        onPage("the playlist drag", p -> p.drag(".row", 0, 44));
        pause(10);
        grab(mc, "preset_music_reordered", 5);

        shoot(mc, "preset_settings", () -> OrielClientCommands.preset("settings"));
        onPage("keyboard focus", p -> {
            p.key("Tab");
            p.key("Tab");
        });
        pause(6);
        grab(mc, "preset_settings_focus", 5);
        onPage("the dropdown", p -> p.click("[data-key=graphics] .value"));
        pause(8);
        grab(mc, "preset_settings_dropdown", 5);
        // IME: a composition in two segments (the second being converted) arrives as Minecraft sends it, shows inline,
        // then commits as typed text.
        onPage("the name field", p -> {
            p.key("Escape");
            p.click("[data-tab=控制]");
        });
        pause(6);
        onPage("focus the name", p -> p.click("[data-key=name] input"));
        pause(4);
        steps.add(() -> {
            if (McClient.screen() != null) McClient.screen().preeditUpdated(new net.minecraft.client.input.PreeditEvent("中文输入", 4,
                    java.util.List.of("中文", "输入"), 1));
        });
        pause(6);
        grab(mc, "preset_settings_ime", 5);
        steps.add(() -> {
            if (McClient.screen() != null) McClient.screen().preeditUpdated(null);
        });
        onPage("commit", p -> p.type("中文输入"));
        pause(6);
        grab(mc, "preset_settings_ime_committed", 5);

        shoot(mc, "preset_editor", () -> OrielClientCommands.preset("editor"));
        onPage("an editor selection", p -> p.click(".node[data-id='7']"));
        pause(6);
        grab(mc, "preset_editor_selected", 5);

        shoot(mc, "preset_gallery_light", () -> OrielClientCommands.preset("gallery"));
        onPage("the light theme", p -> p.click("[data-action=theme]"));
        pause(10);
        grab(mc, "preset_gallery_light", 5);
        steps.add(() -> {
            dev.oriel.kit.Shell.setStyle("smooth");
            OrielClientCommands.preset("gallery");
        });
        pause(20);
        grab(mc, "preset_gallery_smooth_light", 5);
        onPage("smooth and dark", p -> p.click("[data-action=theme]"));
        pause(10);
        grab(mc, "preset_gallery_smooth", 5);
        shoot(mc, "preset_catalog_smooth", () -> OrielClientCommands.preset("catalog"));
        steps.add(() -> {
            dev.oriel.kit.Shell.setStyle("ore");
            dev.oriel.kit.Shell.setTheme("light");
        });
        shoot(mc, "preset_inventory_light", () -> OrielClientCommands.preset("inventory"));
        steps.add(() -> dev.oriel.kit.Shell.setTheme("dark"));

    }

    /**
     * {@code -Pautopilot=home}: the launcher at a large and a small GUI (with a sword in hand, as in play mode), the
     * title bar tabs mid-transition and after, and the window mode switch.
     */
    private static void home(Minecraft mc) {
        command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]");
        command(mc, "effect give @s minecraft:speed 600 1 true");
        guiScale(mc, 2);
        shoot(mc, "home_gui2", () -> OrielClientCommands.preset("gallery"));
        onPage("the catalog tab", p -> p.click(".o-apptabs [data-go=catalog]"));
        pause(4);
        grab(mc, "home_tab_mid", 1);
        pause(20);
        grab(mc, "home_tab_catalog", 5);
        onPage("the segment demo", p -> {
            p.click("#list .item[data-sec=tabs]");
        });
        pause(10);
        onPage("a demo tab", p -> {
            p.click(".sec.on .ore-tabs .ore-tab:nth-child(2)");
            p.click("#seg-demo span:nth-child(3)");
        });
        pause(10);
        grab(mc, "home_catalog_demo", 5);
        onPage("the list jumps to a section", p -> p.click("#list .item[data-sec=models]"));
        pause(30);
        grab(mc, "home_catalog_jump", 5);
        onPage("the select demo", p -> {
            p.click("#list .item[data-sec=inputs]");
        });
        pause(30);
        onPage("open the select", p -> p.click(".sec .ore-select .value"));
        pause(8);
        grab(mc, "home_catalog_select", 5);
        onPage("pick an option", p -> p.click(".sec .ore-select .option:nth-child(2)"));
        onPage("the window switch", p -> p.click("[data-action=window]"));
        pause(20);
        grab(mc, "home_catalog_mini", 5);
        onPage("the home tab", p -> p.click(".o-apptabs [data-go=gallery]"));
        pause(30);
        grab(mc, "home_mini", 5);
        onPage("maximize", p -> p.click("[data-action=window]"));
        pause(10);
        onPage("the editor tab", p -> p.click(".o-apptabs [data-go=editor]"));
        pause(30);
        onPage("new canvas", p -> p.click("#new"));
        pause(12);
        grab(mc, "home_editor_new", 5);
        onPage("cancel", p -> p.click("#new-cancel"));
        onPage("select the slot", p -> p.click(".node.k-slot"));
        pause(4);
        onPage("open the picker", p -> p.click("#picked"));
        pause(10);
        onPage("search", p -> {
            p.click("#pick-q");
            p.type("剑");
        });
        pause(12);
        grab(mc, "home_editor_picker", 5);
        onPage("close the picker", p -> p.key("Escape"));
        shoot(mc, "home_inventory", () -> OrielClientCommands.preset("inventory"));
        onPage("the effects tab", p -> p.click("#info-tabs [data-pane=effects]"));
        pause(12);
        grab(mc, "home_inventory_effects", 5);
        onPage("fold the info drawer", p -> p.click("#info .o-drawer-handle"));
        pause(16);
        grab(mc, "home_inventory_drawer", 5);
        shoot(mc, "home_music", () -> OrielClientCommands.preset("music"));
        onPage("play", p -> p.click("#play"));
        pause(60);
        grab(mc, "home_music_playing", 5);
        guiScale(mc, 3);
        shoot(mc, "home_gui3", () -> OrielClientCommands.preset("gallery"));
        onPage("light", p -> p.click("[data-action=theme]"));
        pause(10);
        grab(mc, "home_gui3_light", 5);
        onPage("dark", p -> p.click("[data-action=theme]"));
        guiScale(mc, 2);
    }

    /**
     * {@code -Pautopilot=reload}: the Java hot reload end to end. Opens the home page and waits (up to 3 minutes) for
     * the reload bar (edit a Java file meanwhile), presses its button, waits for the result and screenshots it.
     */
    private static void hotReload(Minecraft mc) {
        guiScale(mc, 2);
        shoot(mc, "reload_open", () -> OrielClientCommands.preset("gallery"));
        until("the reload bar showed", t -> OrielAutomation.screen().map(p -> p.exists("#o-devbar")).orElse(false), 3600,
                "no reload bar", true);
        grab(mc, "reload_bar", 5);
        onPage("press 热重载", p -> p.click("#o-devbar .ore-btn"));
        until("the reload finished", t -> OrielAutomation.screen().flatMap(p -> p.text("#o-devbar .t"))
                .map(s -> s.startsWith("已") || s.contains("失败") || s.contains("重启")).orElse(false), 7200, "no result", true);
        pause(50); // past the resource reload's fading overlay
        grab(mc, "reload_done", 5);
        onPage("the result", p -> Constants.LOG.info("Oriel autopilot: reload bar says {}", p.text("#o-devbar .t").orElse("(gone)")));
    }

    /**
     * The style showcase ({@code -Pautopilot=styles}): its index and every look at GUI scale 2, two of them at 3,
     * then Escape: from a look back to the index, from the index back to the gallery.
     */
    private static void styles(Minecraft mc) {
        guiScale(mc, 2);
        // One-click save (below): the template's copy lands in config/oriel/my-pages/, and the saved view lists it. A copy
        // from an earlier run is cleared first, before the centre reads the library.
        java.nio.file.Path copy = dev.oriel.mod.internal.platform.Services.PLATFORM.configDir().resolve("oriel/my-pages/rpg.html");
        steps.add(() -> {
            try {
                java.nio.file.Files.deleteIfExists(copy);
            } catch (java.io.IOException e) {
                fail("could not clear {}", copy);
            }
        });
        shoot(mc, "styles_index", () -> OrielScreens.open(dev.oriel.kit.OrielKit.STYLES));
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.click("#grid .card")).orElse(false),
                "opened a template's details", "could not open a template's details"));
        settle("the template details", 20);
        grab(mc, "styles_index_detail", 5);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.click("#d-save")).orElse(false), "clicked save", "could not click save"));
        settle("the saved template", 10);
        steps.add(() -> check(java.nio.file.Files.exists(copy), "saving a template wrote its copy", "saving a template wrote no {}", copy));
        steps.add(() -> OrielAutomation.screen().ifPresent(p -> {
            p.key("Escape");
            p.click("#side [data-view=saved]");
        }));
        settle("the saved view", 15);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.exists("#grid .card")).orElse(false),
                "the saved view lists the copy", "the saved view is empty"));
        grab(mc, "styles_index_saved", 5);
        for (String look : dev.oriel.kit.preset.StylesPreset.PAGES) {
            shoot(mc, "styles_" + look, () -> OrielScreens.open(dev.oriel.kit.OrielKit.page("styles/" + look)));
        }
        guiScale(mc, 3);
        shoot(mc, "styles_xianxia_gui3", () -> OrielScreens.open(dev.oriel.kit.OrielKit.page("styles/xianxia")));
        shoot(mc, "styles_terraria_gui3", () -> OrielScreens.open(dev.oriel.kit.OrielKit.page("styles/terraria")));
        guiScale(mc, 2);
        open("a look", () -> OrielScreens.open(dev.oriel.kit.OrielKit.page("styles/dungeon")));
        onPage("a look", page -> page.key("Escape"));
        until("Escape went back to the style index", ticks -> OrielAutomation.screen().map(p -> p.exists("#grid .card")).orElse(false),
                100, "Escape on a look did not open the style index", true);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.exists("#grid .card")).orElse(false),
                "Escape on a look went back to the style index", "Escape on a look did not open the style index"));
        onPage("the style index", page -> page.key("Escape"));
        until("Escape went back to the gallery", ticks -> OrielAutomation.screen().map(p -> p.exists("#cards")).orElse(false),
                100, "Escape on the style index did not open the gallery", true);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.exists("#cards")).orElse(false),
                "Escape on the style index went back to the gallery", "Escape on the style index did not open the gallery"));
    }

    /**
     * The repair forge as a real container ({@code -Pautopilot=forge}): /oriel-forge opens the server's menu on the kit's
     * forge station page; a damaged sword and its ingot go in by clicking the page's slots (vanilla moves the items),
     * the forge works, and the repaired sword waits in the output.
     */
    private static void forgeStation(Minecraft mc) {
        steps.add(() -> {
            command(mc, "clear @s");
            command(mc, "gamemode survival");
            command(mc, "give @s minecraft:iron_sword[damage=200]");
            command(mc, "give @s minecraft:iron_ingot 3");
        });
        pause(10);
        steps.add(() -> command(mc, "oriel-forge"));
        settle("the forge station", 20);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.exists("#work")).orElse(false),
                "/oriel-forge opened the forge station", "/oriel-forge opened no forge station"));
        grab(mc, "forge_open", 5);
        // The hotbar is menu slots 30-38: the sword went to the first, the ingots to the second.
        for (String[] move : new String[][] {{"30", "0"}, {"31", "1"}}) {
            steps.add(() -> OrielAutomation.screen().ifPresent(p -> p.click("slot[index='" + move[0] + "']")));
            pause(3);
            steps.add(() -> OrielAutomation.screen().ifPresent(p -> p.click("slot[index='" + move[1] + "']")));
            pause(3);
        }
        pause(10);
        grab(mc, "forge_working", 3);
        pause(60);
        steps.add(() -> check(OrielAutomation.screen().map(p -> p.exists("#work.done")).orElse(false),
                "the forge repaired the sword", "the forge has no repaired sword to take"));
        grab(mc, "forge_done", 5);
        steps.add(() -> McClient.setScreen(null));
    }

    /**
     * {@code -Pautopilot=effects}: the effects page (filters, clip-path, masks, blend modes, rounded overflow clips,
     * background-clip: text), which McCanvas draws through offscreen layers, at GUI scale 2 and 3.
     */
    private static void effects(Minecraft mc) {
        shoot(mc, "effects", () -> OrielScreens.open(devUrl("effects")));
        steps.add(() -> Constants.LOG.info("Oriel autopilot: effects layers {}", dev.oriel.mod.internal.client.render.Layers.describe()));
        guiScale(mc, 3);
        shoot(mc, "effects_gui3", () -> OrielScreens.open(devUrl("effects")));
        guiScale(mc, 2);
    }

    /**
     * {@code -Pautopilot=text}: the catalog's text section in every bundled font, the formatted text field after
     * colouring a selection, and the notices.
     */
    private static void text(Minecraft mc) {
        guiScale(mc, 2);
        shoot(mc, "text_open", () -> OrielClientCommands.preset("catalog"));
        onPage("the text section", p -> p.click("#list .item[data-sec=type]"));
        pause(30);
        grab(mc, "text_fonts_mc", 5);
        for (String font : new String[] {"pixel", "pixel_12", "cubic", "sans", "kai", "smiley", "kuaile"}) {
            onPage("font " + font, p -> p.click(".font-card[data-font-pick=" + font + "]"));
            pause(10);
            grab(mc, "text_font_" + font, 5);
        }
        onPage("back to the game's font", p -> p.click(".font-card[data-font-pick=mc]"));
        onPage("the font select in the status bar", p -> p.click("[data-font-select] .value"));
        pause(8);
        grab(mc, "text_globals_open", 5);
        onPage("pick a font there", p -> p.click("[data-font-select] .option[data-font-pick=sans]"));
        pause(8);
        grab(mc, "text_globals_sans", 5);
        onPage("and back", p -> p.click(".font-card[data-font-pick=mc]"));
        onPage("the inputs section", p -> p.click("#list .item[data-sec=inputs]"));
        pause(30);
        onPage("type into the formatted field", p -> {
            p.click(".o-mcfield > input");
            p.key("End");
            p.type(" 新增");
        });
        pause(4);
        onPage("colour it", p -> p.click(".o-mcfield .sw[data-mc=e]"));
        pause(6);
        grab(mc, "text_mcfield", 5);
        onPage("the custom colour", p -> p.click(".o-mcfield .sw.custom"));
        pause(6);
        grab(mc, "text_mcfield_hex", 5);
        onPage("the notices section", p -> p.click("#list .item[data-sec=notices]"));
        pause(30);
        onPage("a notice and toasts", p -> {
            p.click(".sec[data-sec=notices] [data-kind=warn][data-notice]");
            p.click(".sec[data-sec=notices] [data-kind=success][data-toast]");
            p.click(".sec[data-sec=notices] [data-kind=error][data-toast]");
        });
        pause(12);
        grab(mc, "text_notices", 5);
        guiScale(mc, 3);
        shoot(mc, "text_gui3", () -> OrielClientCommands.preset("catalog"));
        onPage("the text section", p -> p.click("#list .item[data-sec=type]"));
        pause(30);
        onPage("font kai", p -> p.click(".font-card[data-font-pick=kai]"));
        pause(10);
        grab(mc, "text_gui3_kai", 5);
        onPage("font pixel 12", p -> p.click(".font-card[data-font-pick=pixel_12]"));
        pause(10);
        grab(mc, "text_gui3_pixel12", 5);
        onPage("back to the game's font", p -> p.click(".font-card[data-font-pick=mc]"));
        shoot(mc, "text_home", () -> OrielClientCommands.preset("gallery"));
        onPage("the theme button", p -> p.click("[data-action=theme]"));
        pause(10);
        grab(mc, "text_home_light", 5);
        onPage("dark again", p -> p.click("[data-action=theme]"));
        guiScale(mc, 2);
    }

    /** Play mode: things to try, the home page open, and a chat note on what to look at. */
    private static void play(Minecraft mc) {
        command(mc, "clear @s");
        command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5,\"minecraft:looting\":3},minecraft:custom_name=\"龙牙\"]");
        command(mc, "give @s minecraft:bow[minecraft:enchantments={\"minecraft:power\":4}]");
        command(mc, "give @s minecraft:netherite_pickaxe");
        command(mc, "give @s minecraft:golden_apple 8");
        command(mc, "give @s minecraft:enchanted_book[minecraft:stored_enchantments={\"minecraft:mending\":1}]");
        command(mc, "give @s minecraft:elytra");
        command(mc, "give @s minecraft:diamond 32");
        command(mc, "effect give @s minecraft:night_vision 600 0 true");
        pause(20);
        steps.add(() -> {
            String[] lines = {"oriel.dev.acceptance.ready", "oriel.dev.acceptance.home", "oriel.dev.acceptance.catalog",
                    "oriel.dev.acceptance.hud", "oriel.dev.acceptance.ime", "oriel.dev.acceptance.reload"};
            for (String key : lines) McClient.systemMessage(Component.literal(ClientText.t(key)));
            OrielClientCommands.preset("gallery");
        });
        if (Boolean.getBoolean("oriel.play.test")) {
            pause(60);
            grab(mc, "play_ready", 5);
        }
    }

    /** Waits {@code ticks} ticks before the next step. */
    private static void pause(int ticks) {
        steps.add(() -> wait = ticks);
    }

    /**
     * Runs {@code open}, waits for the open page to settle (at least {@link #SETTLE} ticks), and saves a screenshot
     * named {@code oriel_<name>.png}.
     */
    private static void mcpPage(Minecraft mc) {
        final JsonObject task;
        try {
            Path file = Path.of(System.getProperty("oriel.mcp.task", ""));
            if (!Files.isRegularFile(file) || Files.size(file) > 131072) throw new IOException("Invalid MCP task file");
            task = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            fail("cannot read MCP task", error);
            return;
        }
        String url = task.get("pageUrl").getAsString();
        int initialFailures = failures;
        steps.add(() -> OrielScreens.open(url, task.get("data")));
        settle("MCP initial page", SETTLE);
        for (JsonElement value : task.getAsJsonArray("actions")) {
            String action = value.getAsString();
            int space = action.indexOf(' ');
            String verb = space < 0 ? action : action.substring(0, space);
            String argument = space < 0 ? "" : action.substring(space + 1);
            if (verb.equals("wait")) {
                int frames = Integer.parseInt(argument);
                if (frames < 0 || frames > 600) throw new IllegalArgumentException("Invalid wait");
                steps.add(() -> wait = Math.max(1, (int) Math.ceil(frames * 16.0 / 50.0)));
                continue;
            }
            onPage("MCP " + verb, page -> {
                boolean ok;
                switch (verb) {
                    case "click" -> ok = page.click(argument);
                    case "move" -> ok = page.hover(argument);
                    case "key" -> ok = page.key(argument);
                    case "type" -> { page.type(argument); ok = true; }
                    case "wheel" -> {
                        int split = argument.lastIndexOf(' ');
                        ok = split > 0 && page.wheel(argument.substring(0, split), Double.parseDouble(argument.substring(split + 1)) / 24.0);
                    }
                    default -> throw new IllegalArgumentException("Unsupported MCP action: " + verb);
                }
                check(ok, "MCP action {} succeeded", "MCP action {} failed", action);
            });
        }
        settle("MCP final page", SETTLE);
        leave();
        grab(mc, "mcp_page", 5);
        Path screenshot = mc.gameDirectory.toPath().resolve("screenshots/oriel_mcp_page.png");
        until("MCP screenshot saved", ticks -> Files.isRegularFile(screenshot));
        steps.add(() -> {
            DocumentDriver driver = McClient.screen() == null ? null : DocumentDriver.of(McClient.screen());
            Document doc = driver == null ? null : driver.document();
            int errors = driver == null ? 1 : driver.pageErrorCount();
            int diagnostics = doc == null ? 0 : doc.diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.WARNING);
            JsonObject receipt = new JsonObject();
            receipt.add("jobId", task.get("jobId"));
            receipt.add("hash", task.get("hash"));
            receipt.addProperty("pageUrl", doc == null ? "" : doc.url());
            receipt.addProperty("minecraft", net.minecraft.SharedConstants.getCurrentVersion().name());
            receipt.addProperty("screenshot", screenshot.toAbsolutePath().toString());
            receipt.addProperty("errors", errors);
            receipt.addProperty("diagnostics", diagnostics);
            receipt.addProperty("actionFailures", failures - initialFailures);
            receipt.addProperty("passed", doc != null && doc.url().equals(url) && errors == 0 && diagnostics == 0
                    && failures == initialFailures && Files.isRegularFile(screenshot));
            if (doc != null) receipt.add("diagnosticDetails", new com.google.gson.Gson().toJsonTree(doc.diagnostics().all()));
            try {
                Path output = Path.of(task.get("receiptFile").getAsString());
                Files.createDirectories(output.toAbsolutePath().getParent());
                Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(receipt));
            } catch (IOException error) { fail("cannot write MCP receipt", error); }
        });
    }

    private static void shoot(Minecraft mc, String name, Runnable open) {
        steps.add(open);
        settle(name, SETTLE);
        grab(mc, name, 5);
    }

    /** Runs {@code action} on the open Oriel screen's page; a failed check when none is open. */
    private static void onPage(String what, Consumer<OrielAutomation> action) {
        steps.add(() -> OrielAutomation.screen().ifPresentOrElse(action, () -> fail("no page open for {}", what)));
    }

    /** {@link #settle(String, Supplier, int)} for the open screen's page. */
    private static void settle(String what, int minTicks) {
        settle(what, OrielAutomation::screen, minTicks);
    }

    /**
     * Waits, asking once a tick, until {@code page} has settled ({@link OrielAutomation#settled}) and at least
     * {@code minTicks} have passed. With no such page after {@link #SETTLE} ticks (a screen that is not a Oriel
     * page) it goes on, and so it does after {@link #MAX_SETTLE} ticks of a page that keeps changing.
     */
    private static void settle(String what, Supplier<Optional<OrielAutomation>> page, int minTicks) {
        until(what + " settled", ticks -> ticks >= minTicks && page.get().map(OrielAutomation::settled).orElse(ticks >= SETTLE),
                Math.max(minTicks, MAX_SETTLE), what + " is still changing, going on", false);
    }

    /**
     * Waits until {@code done} holds, asking once a tick with the number of ticks asked so far, and logs {@code what}
     * (which says what holding means) with the ticks it took; goes on with a warning after {@link #MAX_POLL} ticks.
     */
    private static void until(String what, IntPredicate done) {
        until(what, done, MAX_POLL, "gave up waiting until " + what, true);
    }

    /** A step that runs again the next tick, ahead of the others, until {@code done} holds or {@code maxTicks} pass. */
    private static void until(String what, IntPredicate done, int maxTicks, String gaveUp, boolean warn) {
        steps.add(new Runnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (done.test(ticks)) Constants.LOG.info("Oriel autopilot: {} ({} ticks)", what, ticks);
                else if (ticks < maxTicks) steps.addFirst(this);
                else if (warn) Constants.LOG.warn("Oriel autopilot: {} after {} ticks", gaveUp, ticks);
                else Constants.LOG.info("Oriel autopilot: {} after {} ticks", gaveUp, ticks);
            }
        });
    }

    /** Hovers the first element of the open Oriel screen matching {@code selector} and lets hover effects settle. */
    private static void hover(String selector) {
        moveOnto(selector);
        settle("the hover over " + selector, 0);
    }

    /** Moves the pointer off the open page, so the next pages and screenshots have nothing hovered. */
    private static void leave() {
        steps.add(() -> OrielAutomation.screen().ifPresent(OrielAutomation::leave));
    }

    /** Saves a screenshot of the last frame as {@code oriel_<name>.png}, then waits {@code ticks}. */
    private static void grab(Minecraft mc, String name, int ticks) {
        steps.add(() -> {
            McClient.chat().clearMessages(false);
            Constants.LOG.info("Oriel autopilot: {} at {} fps", name, mc.getFps());
            //? if >=26.2 {
            Screenshot.grab(mc.gameDirectory, "oriel_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            //?} else
            //Screenshot.grab(mc.gameDirectory, "oriel_" + name + ".png", mc.getMainRenderTarget(), 1,
                    msg -> Constants.LOG.info("Oriel autopilot: {}", msg.getString()));
            wait = ticks;
        });
    }

    private static void guiScale(Minecraft mc, int scale) {
        steps.add(() -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
            wait = 5;
        });
    }

    // ---- Checks ----

    /**
     * Logs {@code pass} when {@code ok}, else {@code fail} as an error, each with {@code args} for its placeholders.
     * Failures count toward the total logged when the autopilot finishes.
     */
    private static void check(boolean ok, String pass, String fail, Object... args) {
        if (!ok) {
            fail(fail, args);
            return;
        }
        checks++;
        Constants.LOG.info("Oriel autopilot: " + pass, args);
    }

    /**
     * A check this Minecraft version can't make yet (the feature isn't ported to it): logged as skipped, with why, and
     * counted apart from the checks.
     */
    private static void skip(String what, String why) {
        skipped++;
        Constants.LOG.warn("Oriel autopilot: SKIPPED {}: {}", what, why);
    }

    /** A failed check: logs {@code message} as an error (a throwable last in {@code args} with its stack trace). */
    private static void fail(String message, Object... args) {
        checks++;
        failures++;
        Constants.LOG.error("Oriel autopilot: " + message, args);
    }

    // ---- Server pages (ServerPages, DocumentDriver's close keys) ----

    /** The integrated server's opens and closes of the autopilot's sessions, and the keys its pages saw. */
    private static final List<String> serverLog = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Whether the server reopens its page whenever the player closes it, as a hostile server would. */
    private static volatile boolean reopen;

    /** A page that keeps Escape, reports the keys it sees, and pushes nothing. */
    private static final String STUBBORN_PAGE = """
            <body style="background:#203040"><p>Stubborn page</p><input id="name">
            <script>addEventListener('keydown', e => { oriel.send('key', e.key); if (e.key === 'Escape') e.preventDefault() })</script>
            </body>""";

    /**
     * What a hostile server can and can't do with pages: a page that keeps Escape still closes on Shift+Escape and
     * on Escape pressed three times; a page opened while chat is open waits for chat to close; a server that reopens
     * its page as the player closes it is stopped; a script can't open a web link without a click; data nested
     * deeper than {@code client.maxDataDepth} is dropped; a page's messages are capped per screen, across reloads.
     */
    private static void serverPages(Minecraft mc) {
        Supplier<Integer> session = () -> McClient.screen() instanceof OrielScreen s ? s.driver().session() : -1;
        // Shift+Escape: closes at once, and the page never sees it.
        steps.add(() -> serverOpen(mc, STUBBORN_PAGE));
        until("the server's page is shown", ticks -> session.get() >= 0);
        steps.add(() -> pressEscape(mc, true));
        until("Shift+Escape closed the server's page", ticks -> McClient.screen() == null && serverLog.contains("closed"));
        steps.add(() -> check(serverLog.stream().noneMatch(e -> e.startsWith("key")), "the page never saw Shift+Escape",
                "the page saw Shift+Escape: {}", serverLog));
        // Escape three times: the page keeps the first two. (Waits between closing and opening, so the reopen
        // guard doesn't count these.)
        steps.add(() -> wait = 30);
        steps.add(serverLog::clear);
        steps.add(() -> serverOpen(mc, STUBBORN_PAGE));
        until("the server's page is shown again", ticks -> session.get() >= 0);
        steps.add(() -> pressEscape(mc, false));
        steps.add(() -> pressEscape(mc, false));
        steps.add(() -> check(session.get() >= 0, "the page kept two Escapes", "two Escapes closed the page"));
        steps.add(() -> pressEscape(mc, false));
        until("three Escapes closed the page", ticks -> McClient.screen() == null);
        steps.add(() -> wait = 30);
        // A page the server opens while chat is open waits until chat closes.
        steps.add(() -> McClient.setScreen(chatScreen()));
        steps.add(() -> serverOpen(mc, "<p>After chat</p>"));
        steps.add(() -> wait = 10);
        steps.add(() -> check(McClient.screen() instanceof ChatScreen, "a server page waits while chat is open",
                "a server page replaced chat"));
        steps.add(() -> McClient.setScreen(null));
        until("the waiting page is shown once chat closed", ticks -> session.get() >= 0);
        // A script can't open a web link by itself.
        steps.add(() -> serverOpen(mc, "<script>setTimeout(() => location.href = 'https://example.com/', 50)</script><p>Link</p>"));
        steps.add(() -> wait = 20);
        steps.add(() -> check(session.get() >= 0, "a script's web link without a click was ignored",
                "a script opened {} without a click", McClient.screen()));
        // Data nested past client.maxDataDepth is dropped.
        steps.add(() -> serverRun(mc, player -> {
            OrielSession s = OrielServer.openInline(player, "<p>Data</p>", JsonParser.parseString("{\"ok\": 1}"));
            s.push(JsonParser.parseString("[".repeat(100) + "]".repeat(100)));
        }));
        until("the data page is shown", ticks -> McClient.screen() instanceof OrielScreen v && v.driver().data() instanceof com.google.gson.JsonObject);
        steps.add(() -> wait = 5);
        steps.add(() -> check(McClient.screen() instanceof OrielScreen v && v.driver().data() instanceof com.google.gson.JsonObject o && o.has("ok"),
                "deeply nested data was dropped", "deeply nested data reached the page"));
        // Patches from the server set one field, and count toward client.maxDataChars with the rest of the data.
        steps.add(() -> serverRun(mc, player -> {
            JsonPrimitive half = new JsonPrimitive("z".repeat(OrielConfig.CLIENT_MAX_DATA.get() / 2));
            OrielServer.openInline(player, "<p>{{ hp }}</p>", JsonParser.parseString("{\"hp\": 20}"))
                    .patch("hp", new JsonPrimitive(7)).patch("a", half).patch("b", half);
        }));
        until("the patched server page is shown", ticks -> serverData().has("a"));
        steps.add(() -> wait = 5);
        steps.add(() -> {
            JsonObject data = serverData();
            check(data.has("hp") && data.get("hp").getAsInt() == 7 && !data.has("b"), "a server's patches applied, up to client.maxDataChars",
                    "a server's patches gave {}", data.keySet());
            if (McClient.screen() instanceof OrielScreen v) v.driver().reload();
        });
        settle("the reloaded server page", 0);
        onPage("the reloaded server page", p -> {
            String hp = p.eval("oriel.data.hp + ' ' + ('b' in oriel.data)").map(JsonElement::getAsString).orElse("");
            check(hp.equals("7 false"), "a server's patch was kept across a reload", "after a reload the server page's data is '{}'", hp);
        });
        // oriel.send is capped per screen (client.messageBurst), and reloading the page doesn't reset the count.
        steps.add(() -> {
            serverLog.clear();
            serverOpen(mc, "<p>Spam</p><script>for (let i = 0; i < 100; i++) oriel.send('spam', i)</script>");
        });
        until("the spamming page is shown", ticks -> session.get() >= 0);
        steps.add(() -> wait = 5);
        steps.add(() -> {
            if (McClient.screen() instanceof OrielScreen v) v.driver().reload();
        });
        steps.add(() -> wait = 5);
        steps.add(() -> {
            long sent = serverLog.stream().filter("spam"::equals).count();
            int burst = OrielConfig.CLIENT_MESSAGE_BURST.get();
            check(sent >= burst && sent < 2L * burst, "a page got {} of 200 messages through, across a reload (burst {})",
                    "a page got {} of 200 messages through across a reload; expected one burst of {} and a little", sent, burst);
        });
        // Typing into a server's page shows Oriel's notice over it.
        steps.add(() -> serverOpen(mc, "<body style='background:#2a2a40;padding:20px'><p>Sign in</p><input autofocus></body>"));
        until("the sign-in page is shown", ticks -> session.get() >= 0 && McClient.screen() instanceof OrielScreen v && v.driver().typing());
        onPage("the sign-in page", page -> page.type("hunter2"));
        grab(mc, "server_page_typing", 5);
        // A server that reopens its page whenever it is closed is stopped after client.reopenStrikes reopens.
        steps.add(() -> {
            reopen = true;
            serverLog.clear();
            serverOpen(mc, "<p>Again</p>");
        });
        for (int i = 0; i <= OrielConfig.CLIENT_REOPEN_STRIKES.get(); i++) {
            until("the reopened page is shown, or the server is stopped", ticks -> session.get() >= 0 || ServerPages.blocked());
            steps.add(() -> {
                if (session.get() >= 0) pressEscape(mc, false);
            });
            steps.add(() -> wait = 2);
        }
        steps.add(() -> {
            reopen = false;
            check(McClient.screen() == null && ServerPages.blocked(), "the reopen loop was stopped after {} opens",
                    "the reopen loop went on: {} opens", serverLog.stream().filter("opened"::equals).count());
        });
    }

    /** The open server page's data, or an empty object. */
    private static JsonObject serverData() {
        return McClient.screen() instanceof OrielScreen v && v.driver().session() >= 0 && v.driver().data() instanceof JsonObject o
                ? o : new JsonObject();
    }

    private static void serverOpen(Minecraft mc, String html) {
        serverRun(mc, player -> open(player, html));
    }

    private static void open(ServerPlayer player, String html) {
        serverLog.add("opened");
        OrielServer.openInline(player, html, null)
                .onMessage("key", (p, key) -> serverLog.add("key " + key))
                .onMessage("spam", (p, value) -> serverLog.add("spam"))
                .onClose(() -> {
                    serverLog.add("closed");
                    if (reopen) player.level().getServer().execute(() -> open(player, html));
                });
    }

    /** Runs {@code action} on the integrated server with the local player's server player, if there are both. */
    public static void serverRun(Minecraft mc, Consumer<ServerPlayer> action) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    /** Escape through Minecraft's keyboard handler, as the player presses it. */
    private static void pressEscape(Minecraft mc, boolean shift) {
        KeyEvent event = KeyNames.escape(shift);
        McClient.key(event, true);
        McClient.key(event, false);
    }

    // ---- World ----

    /** Kill statistics for the Mobdex: some mobs caught (defeated), some only seen (defeated by). */
    private static void awardKills(Minecraft mc) {
        serverRun(mc, player -> {
            for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.SPIDER,
                    EntityTypes.PIG, EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.CHICKEN, EntityTypes.SLIME, EntityTypes.SQUID)) {
                player.awardStat(Stats.ENTITY_KILLED.get(type), 3);
            }
            for (EntityType<?> type : List.of(EntityTypes.ENDERMAN, EntityTypes.WITCH, EntityTypes.GHAST, EntityTypes.BLAZE)) {
                player.awardStat(Stats.ENTITY_KILLED_BY.get(type), 1);
            }
        });
    }

    /** An empty chat screen, as T opens it. */
    private static ChatScreen chatScreen() {
        return new ChatScreen("", false);
    }

    private static void command(Minecraft mc, String cmd) {
        if (mc.player != null) mc.player.connection.sendCommand(cmd);
    }
}
