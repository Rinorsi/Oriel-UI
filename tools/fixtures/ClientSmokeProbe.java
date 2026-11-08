import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import dev.oriel.mod.client.*;
import dev.oriel.mod.internal.client.McClient;

/** Six core scenarios for an actual slim JAR; three assertions per scenario, matching the full autopilot gate. */
public final class ClientSmokeProbe {
    private static final Identifier HUD = Identifier.parse("oriel_acceptance:smoke");
    public static void run(String text) throws Exception {
        var parts = text.strip().split("\\n",2);
        String name = parts[1];
        var mc = Minecraft.getInstance();
        String html = "<html><body style='color:white;background:#203020;font-size:12px'>SLIM CORE " + name + "</body></html>";
        if (parts[0].equals("open")) {
            switch(name) {
                case "settings" -> OrielScreens.open("oriel:ui/modsettings.html");
                case "about" -> OrielScreens.open("oriel:ui/about.html");
                case "inline" -> OrielScreens.openInline(html,null);
                case "local" -> OrielScreens.open("oriel_mine:s-client/index.html");
                case "reload" -> { OrielScreens.open("oriel_mine:s-client/index.html").driver().reload(); }
                case "hud" -> { McClient.setScreen(null); OrielHud.register(HUD,"oriel_mine:s-client/index.html"); OrielHud.show(HUD); }
                default -> throw new IllegalArgumentException(name);
            }
        } else if (parts[0].equals("shot")) {
            Screenshot.grab(mc.gameDirectory,"s_smoke_"+name+".png",mc.gameRenderer.mainRenderTarget(),1,c -> System.out.println("S_SMOKE_SHOT "+c.getString()));
        } else if (parts[0].equals("check")) {
            DocumentDriver driver = name.equals("hud") ? OrielHud.driver(HUD) : DocumentDriver.of(McClient.screen());
            check(driver != null && driver.document()!=null,name+" document opened");
            check(driver.document().diagnostics().count(dev.oriel.engine.diag.Diagnostic.Severity.ERROR)==0,name+" errors zero");
            check(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots/s_smoke_"+name+".png")),name+" screenshot saved");
        } else throw new IllegalArgumentException(parts[0]);
    }
    private static void check(boolean okay,String name) {
        if(!okay) throw new AssertionError("S_SMOKE_FAIL "+name);
        System.out.println("S_SMOKE_PASS "+name);
    }
}
