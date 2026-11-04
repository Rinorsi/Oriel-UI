package dev.oriel.engine.css;

import dev.oriel.engine.css.Token.Type;
import dev.oriel.engine.css.ImageProvider;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.paint.Canvas;
import dev.oriel.engine.style.Image;
import java.util.List;

/** Preserves the original sprite parser, intrinsic size and native GUI draw operation. */
public final class MinecraftImageProvider implements ImageProvider {
    public Image fromUrl(String url) { return url.startsWith(Image.SPRITE_SCHEME) ? new Image.Sprite(url.substring(Image.SPRITE_SCHEME.length())) : null; }
    public Image function(String name, List<ComponentValue> arguments) { return name.equals("sprite") ? sprite(arguments) : null; }
    public boolean handles(Image image) { return image instanceof Image.Sprite; }
    public float[] size(Document document, Image image) { return document.host().spriteSize(((Image.Sprite) image).id()); }
    public void paint(Canvas canvas, Image image, float x, float y, float width, float height, int tint) {
        canvas.drawSprite(((Image.Sprite) image).id(), x, y, width, height, tint);
    }
    /** {@code sprite(minecraft:widget/button)} or {@code sprite("minecraft:widget/button")}. */
    private static Image sprite(List<ComponentValue> args) {
        List<ComponentValue> a = CssParser.trim(args);
        if (a.isEmpty()) return null;
        if (a.size() == 1 && a.get(0) instanceof Token s && s.is(Type.STRING)) return new Image.Sprite(s.value);
        for (ComponentValue v : a) if (!(v instanceof Token t) || t.is(Type.WHITESPACE)) return null;
        return new Image.Sprite(ComponentValue.text(a));
    }


}
