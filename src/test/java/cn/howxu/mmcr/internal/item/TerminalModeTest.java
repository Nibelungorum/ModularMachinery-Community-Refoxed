package cn.howxu.mmcr.internal.item;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalModeTest {

    @Test
    void defaultModeIsBuildAndModesToggle() {
        assertEquals(TerminalMode.BUILD, TerminalMode.defaultMode());
        assertEquals(TerminalMode.DEMOLISH, TerminalMode.BUILD.next());
        assertEquals(TerminalMode.BUILD, TerminalMode.DEMOLISH.next());
    }

    @Test
    void modeFormattingMatchesDesign() {
        assertEquals(ChatFormatting.GREEN, TerminalMode.BUILD.color());
        assertEquals(ChatFormatting.RED, TerminalMode.DEMOLISH.color());
    }
}
