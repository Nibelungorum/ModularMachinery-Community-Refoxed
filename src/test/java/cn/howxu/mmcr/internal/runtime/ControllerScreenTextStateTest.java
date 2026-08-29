package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope.CONTROLLER;
import static cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope.OPERATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Controller screen text state behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextStateTest {
    private ControllerScreenTextState state;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

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
    void appendAfter_ignores_missing_anchor_without_mutating_state() {
        state.append(CONTROLLER, id("example:existing"), Component.literal("existing"));
        ControllerScreenTextSnapshot before = state.snapshot();
        long revision = state.revision();
        state.clearDirty();

        state.appendAfter(CONTROLLER, id("example:inserted"), id("example:missing"),
                Component.literal("inserted"));
        state.appendAfter(CONTROLLER, id("example:missing"), id("example:missing"),
                Component.literal("also ignored"));

        assertThat(state.snapshot()).isEqualTo(before);
        assertThat(state.revision()).isEqualTo(revision);
        assertThat(state.dirty()).isFalse();
    }

    @Test
    void appendAfter_inserts_immediately_after_anchor_and_shifts_successor() {
        state.append(CONTROLLER, id("example:anchor"), Component.literal("anchor"));
        state.append(CONTROLLER, id("example:successor"), Component.literal("successor"));

        state.appendAfter(CONTROLLER, id("example:inserted"), id("example:anchor"),
                Component.literal("inserted"));

        assertThat(state.snapshot().lines())
                .extracting(ControllerScreenTextSnapshot.Line::lineId)
                .containsExactly(id("example:anchor"), id("example:inserted"), id("example:successor"));
    }

    @Test
    void appendAfter_moves_existing_line_and_last_insert_is_closest_to_anchor() {
        state.append(CONTROLLER, id("example:anchor"), Component.literal("anchor"));
        state.append(CONTROLLER, id("example:first"), Component.literal("first"));
        state.append(CONTROLLER, id("example:other"), Component.literal("other"));

        state.appendAfter(CONTROLLER, id("example:first"), id("example:anchor"), Component.literal("updated"));
        state.appendAfter(CONTROLLER, id("example:second"), id("example:anchor"), Component.literal("second"));

        assertThat(state.snapshot().lines())
                .extracting(ControllerScreenTextSnapshot.Line::lineId)
                .containsExactly(id("example:anchor"), id("example:second"), id("example:first"), id("example:other"));
        assertThat(state.snapshot().lines().get(2).text()).isEqualTo(Component.literal("updated"));
    }

    @Test
    void appendAfter_rejects_self_insertion_without_mutating_state() {
        state.append(CONTROLLER, id("example:line"), Component.literal("line"));
        ControllerScreenTextSnapshot before = state.snapshot();
        state.clearDirty();

        assertThatThrownBy(() -> state.appendAfter(CONTROLLER, id("example:line"), id("example:line"),
                Component.literal("replacement")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");
        assertThat(state.snapshot()).isEqualTo(before);
        assertThat(state.dirty()).isFalse();
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
    void replace_updates_existing_line_without_changing_order_or_scope() {
        state.append(OPERATION, id("example:operation"), Component.literal("old"));
        state.append(CONTROLLER, id("example:controller"), Component.literal("controller"));

        state.replace(id("example:operation"), Component.literal("new"));
        state.flushReplacements();

        assertThat(state.snapshot().lines())
                .extracting(ControllerScreenTextSnapshot.Line::lineId)
                .containsExactly(id("example:operation"), id("example:controller"));
        assertThat(state.snapshot().lines().getFirst().scope()).isEqualTo(OPERATION);
        assertThat(state.snapshot().lines().getFirst().text()).isEqualTo(Component.literal("new"));
    }

    @Test
    void replace_prefers_controller_scope_and_never_inserts_missing_lines() {
        Identifier duplicate = id("example:progress");
        state.append(OPERATION, duplicate, Component.literal("operation"));
        state.append(CONTROLLER, duplicate, Component.literal("controller"));

        state.replace(duplicate, Component.literal("replacement"));
        state.flushReplacements();

        assertThat(state.snapshot().lines()).extracting(ControllerScreenTextSnapshot.Line::text)
                .containsExactly(Component.literal("operation"), Component.literal("replacement"));
        int lineCount = state.snapshot().lines().size();
        state.replace(id("example:missing"), Component.literal("ignored"));
        state.flushReplacements();
        assertThat(state.snapshot().lines()).hasSize(lineCount);
    }

    @Test
    void replace_uses_the_last_value_for_an_id_in_one_update() {
        state.append(CONTROLLER, id("example:progress"), Component.literal("old"));

        state.replace(id("example:progress"), Component.literal("first"));
        state.replace(id("example:progress"), Component.literal("last"));
        state.flushReplacements();

        assertThat(state.snapshot().lines()).singleElement()
                .extracting(ControllerScreenTextSnapshot.Line::text)
                .isEqualTo(Component.literal("last"));
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

    @Test
    void replacing_an_existing_key_is_allowed_at_the_line_limit_but_new_keys_are_rejected() {
        for (int index = 0; index < 1024; index++) {
            state.append(CONTROLLER, id("test:line_" + index), Component.literal("line"));
        }

        long revisionBeforeReplacement = state.revision();
        state.append(CONTROLLER, id("test:line_0"), Component.literal("replacement"));

        assertThat(state.revision()).isEqualTo(revisionBeforeReplacement + 1);
        ControllerScreenTextSnapshot beforeRejectedAppend = state.snapshot();
        state.clearDirty();

        assertThatThrownBy(() -> state.append(CONTROLLER, id("test:new_line"), Component.literal("new")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many controller screen text lines");
        assertThat(state.snapshot()).isEqualTo(beforeRejectedAppend);
        assertThat(state.revision()).isEqualTo(beforeRejectedAppend.revision());
        assertThat(state.dirty()).isFalse();
    }

    @Test
    void overlong_line_id_is_rejected_without_mutating_state_or_revision() {
        state.append(CONTROLLER, id("test:existing"), Component.literal("existing"));
        ControllerScreenTextSnapshot before = state.snapshot();
        state.clearDirty();

        assertThatThrownBy(() -> state.append(CONTROLLER, id("test:" + "x".repeat(257)), Component.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line ID is too long");
        assertThat(state.snapshot()).isEqualTo(before);
        assertThat(state.revision()).isEqualTo(before.revision());
        assertThat(state.dirty()).isFalse();
    }

    @Test
    void oversized_component_is_rejected_without_mutating_state_or_revision() {
        state.append(CONTROLLER, id("test:existing"), Component.literal("existing"));
        ControllerScreenTextSnapshot before = state.snapshot();
        state.clearDirty();

        assertThatThrownBy(() -> state.append(CONTROLLER, id("test:large"), largeComponent()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encoded controller screen text is too large");
        assertThat(state.snapshot()).isEqualTo(before);
        assertThat(state.revision()).isEqualTo(before.revision());
        assertThat(state.dirty()).isFalse();
    }

    private static Component largeComponent() {
        return Component.literal("x".repeat(40_000)).append(Component.literal("y".repeat(30_000)));
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }
}
