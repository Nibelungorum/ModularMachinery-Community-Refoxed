package cn.howxu.mmcr.internal.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope.CONTROLLER;
import static cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope.OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Controller screen text state behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextStateTest {
    private ControllerScreenTextState state;

    @BeforeEach
    void setUp() {
        state = new ControllerScreenTextState();
    }

    @Test
    void sameIdUpdatesInPlaceWithoutDuplicate() {
        state.append(CONTROLLER, id("example:first"), Component.literal("one"));
        state.append(CONTROLLER, id("example:first"), Component.literal("two"));

        assertEquals(List.of(Component.literal("two")), state.snapshot().lines().stream()
                .map(ControllerScreenTextSnapshot.Line::text).toList());
    }

    @Test
    void removeAndScopeClearOnlyAffectRequestedEntries() {
        state.append(CONTROLLER, id("example:controller"), Component.literal("controller"));
        state.append(OPERATION, id("example:operation"), Component.literal("operation"));

        state.clear(OPERATION);

        assertEquals(List.of(id("example:controller")), state.snapshot().lines().stream()
                .map(ControllerScreenTextSnapshot.Line::lineId).toList());
    }

    @Test
    void unchangedUpdateDoesNotAdvanceRevision() {
        state.append(CONTROLLER, id("example:status"), Component.literal("same"));
        long revision = state.revision();
        state.append(CONTROLLER, id("example:status"), Component.literal("same"));

        assertEquals(revision, state.revision());
    }

    @Test
    void mutatingAppendedComponentDoesNotChangeStoredSnapshotsOrRevision() {
        MutableComponent text = Component.literal("one");
        state.append(CONTROLLER, id("example:mutable"), text);
        ControllerScreenTextSnapshot beforeMutation = state.snapshot();
        long revision = state.revision();
        state.clearDirty();

        text.append(" changed");

        assertEquals("one", beforeMutation.lines().getFirst().text().getString());
        assertEquals("one", state.snapshot().lines().getFirst().text().getString());
        assertEquals(revision, state.revision());
        assertFalse(state.dirty());
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }
}
