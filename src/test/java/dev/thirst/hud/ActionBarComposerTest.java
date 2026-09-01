package dev.thirst.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActionBarComposerTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private final Component bar = Component.text("[|||-------]");
    private final Component external = Component.text("22C");
    private final Component separator = Component.text(" | ");

    @Test
    void nullExternalReturnsBarUnchanged() {
        assertSame(bar, ActionBarComposer.compose(bar, null, separator, true));
    }

    @Test
    void externalFirstOrder() {
        Component out = ActionBarComposer.compose(bar, external, separator, true);
        assertEquals("22C | [|||-------]", plain(out));
    }

    @Test
    void externalLastOrder() {
        Component out = ActionBarComposer.compose(bar, external, separator, false);
        assertEquals("[|||-------] | 22C", plain(out));
    }
}
