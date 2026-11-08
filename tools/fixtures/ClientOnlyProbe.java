import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.Identifier;
import dev.oriel.mod.client.*;
import dev.oriel.mod.internal.client.McClient;
import dev.oriel.mod.internal.client.OrielClient;
import dev.oriel.mod.internal.net.MessagePayload;

/** External attach-driver fixture: no integrated server, server probe or Oriel server dependency. */
public final class ClientOnlyProbe {
    private static final Identifier HUD = Identifier.parse("oriel_acceptance:local");
    private static final String URL = "oriel_mine:s-client/index.html";
    private static String page(String generation) {
        return "<html><head><link rel='stylesheet' href='oriel:ui/ore.css'/></head>"
            + "<body style='background:#203020;color:white;font-size:12px'><div id='generation'>LOCAL " + generation
            + "</div><button id='send' class='ore-btn'>Local send</button><div id='reply'>waiting</div>"
            + "<script>document.querySelector('#send').addEventListener('click',function(){oriel.send('local-probe',{value:42});});</script></body></html>";
    }
    public static void run(String text) throws Exception {
        var mc = Minecraft.getInstance();
        var parts = text.strip().split("\\n", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        System.out.println("S_PROBE pid="+ProcessHandle.current().pid()+" user.dir="+System.getProperty("user.dir")+" gameDir="+mc.gameDirectory+" operation="+parts[0]);
        Path file = mc.gameDirectory.toPath().resolve("config/oriel/my-pages/s-client/index.html");
        switch(parts[0]) {
            case "connect" -> {
                mc.options.onboardingAccessibilityFinished();
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2); mc.resizeGui();
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(arg),
                    new ServerData("Oriel-free acceptance", arg, ServerData.Type.OTHER), false, null);
            }
            case "joined" -> {
                require(mc.player != null && mc.level != null && mc.getConnection() != null && !mc.hasSingleplayerServer(), "joined dedicated server");
                System.out.println("S_JOINED server="+mc.getCurrentServer().ip+" player="+mc.player.getName().getString());
                require(!OrielClient.sendToServer(new MessagePayload(123, "probe", "{}")), "unavailable upstream returns false");
            }
            case "prepare" -> { Files.createDirectories(file.getParent()); Files.writeString(file, page("A")); }
            case "open" -> {
                var screen = OrielScreens.open(URL);
                screen.driver().onMessage("local-probe", value -> {
                    require(value.getAsJsonObject().get("value").getAsInt() == 42, "local oriel.send listener");
                    screen.driver().document().querySelector("#reply").setTextContent("LOCAL SEND OK");
                });
            }
            case "click" -> require(OrielAutomation.screen().orElseThrow().click("#send"), "click local send");
            case "page" -> {
                var driver = DocumentDriver.of(McClient.screen());
                require(driver != null && driver.document() != null, "local page loaded");
                require(!driver.serverSession(), "local page has no server session");
                require(driver.document().diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.ERROR) == 0, "local page errors zero");
                System.out.println("S_BODY "+OrielAutomation.screen().orElseThrow().text("body"));
            }
            case "reload" -> Files.writeString(file, page("B"));
            case "reloaded" -> require(DocumentDriver.of(McClient.screen()).document().querySelector("#generation").textContent().contains("LOCAL B"), "hot reload generation B");
            case "hud" -> { McClient.setScreen(null); OrielHud.register(HUD, URL); OrielHud.show(HUD); }
            case "hudcheck" -> {
                require(OrielHud.isShown(HUD) && OrielHud.driver(HUD).document() != null, "local HUD shown");
                require(OrielHud.driver(HUD).document().diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.ERROR)==0, "HUD errors zero");
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory,arg+".png",mc.gameRenderer.mainRenderTarget(),1,c -> System.out.println("S_SHOT "+c.getString()));
            case "inspect" -> System.out.println("S_SCREEN "+(McClient.screen()==null?"null":McClient.screen().getClass().getName()));
            case "stop" -> mc.stop();
            default -> throw new IllegalArgumentException("Unknown operation: "+parts[0]);
        }
    }
    private static void require(boolean okay, String name) {
        if (!okay) throw new AssertionError("S_FAIL "+name);
        System.out.println("S_PASS "+name);
    }
}
