package cn.howxu.mmcr.api.publicapi.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.JadeTextSnapshot;
import cn.howxu.mmcr.internal.runtime.JadeTextState;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the independent Jade text API and runtime state.
 *
 * @author howxu <dev@howxu.cn>
 */
class JadeTextStateTest {

    @Test
    void appendUpdatesAnExistingLineWithoutDuplicatingIt() {
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("first"), Component.literal("one"));
        state.append(MMCR.id("second"), Component.literal("two"));
        state.append(MMCR.id("first"), Component.literal("updated"));

        assertThat(state.snapshot().lines()).extracting(JadeTextSnapshot.Line::lineId)
                .containsExactly(MMCR.id("first"), MMCR.id("second"));
        assertThat(state.snapshot().lines().getFirst().text().getString()).isEqualTo("updated");
    }

    @Test
    void appendAfterMovesOrInsertsOnlyAfterAnExistingTarget() {
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("first"), Component.literal("one"));
        state.append(MMCR.id("third"), Component.literal("three"));
        state.appendAfter(MMCR.id("second"), MMCR.id("first"), Component.literal("two"));
        state.appendAfter(MMCR.id("missing"), MMCR.id("unknown"), Component.literal("ignored"));

        assertThat(state.snapshot().lines()).extracting(JadeTextSnapshot.Line::lineId)
                .containsExactly(MMCR.id("first"), MMCR.id("second"), MMCR.id("third"));
    }

    @Test
    void removeAndClearDiscardLines() {
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("first"), Component.literal("one"));
        state.append(MMCR.id("second"), Component.literal("two"));

        state.remove(MMCR.id("first"));
        assertThat(state.snapshot().lines()).extracting(JadeTextSnapshot.Line::lineId)
                .containsExactly(MMCR.id("second"));

        state.clear();
        assertThat(state.snapshot().lines()).isEmpty();
    }

    @Test
    void noopAcceptsAllMutationsWithoutRetainingLines() {
        JadeText noop = JadeText.noop();
        noop.append(MMCR.id("line"), Component.literal("ignored"));
        noop.appendAfter(MMCR.id("other"), MMCR.id("line"), Component.literal("ignored"));
        noop.replace(MMCR.id("line"), Component.literal("ignored"));
        noop.remove(MMCR.id("line"));
        noop.clear();

        assertThat(noop).isSameAs(JadeText.noop());
    }

    @Test
    void stateRejectsNullIdentifiersAndText() {
        JadeTextState state = new JadeTextState();

        assertThatThrownBy(() -> state.append(null, Component.literal("text")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> state.append(MMCR.id("line"), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> state.remove(null))
                .isInstanceOf(NullPointerException.class);
    }
}
