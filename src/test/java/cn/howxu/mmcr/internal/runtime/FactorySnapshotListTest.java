package cn.howxu.mmcr.internal.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class FactorySnapshotListTest {
    @Test
    void updates_and_removals_preserve_previous_snapshots_and_lane_order() {
        FactorySnapshotList<String> empty = new FactorySnapshotList<>();
        FactorySnapshotList<String> first = empty.with(1023, "last").with(5, "middle").with(0, "first");
        FactorySnapshotList<String> changed = first.with(5, "replacement").with(0, null);
        assertThat(empty).isEmpty();
        assertThat(first).containsExactly("first", "middle", "last");
        assertThat(changed).containsExactly("replacement", "last");
        assertThat(changed.with(5, null).with(1023, null)).isEmpty();
        assertThat(first).isUnmodifiable();
        assertThat(first.subList(0, 2)).isUnmodifiable();
        assertThatThrownBy(() -> first.subList(0, 0).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.set(0, "mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.add("mutated")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void arbitrary_external_lists_are_defensively_copied_but_owned_snapshots_are_shared() {
        List<String> source = new ArrayList<>(List.of("before"));
        List<String> captured = FactorySnapshotList.immutableCopy(source);
        source.set(0, "after");
        assertThat(captured).containsExactly("before");
        FactorySnapshotList<String> owned = new FactorySnapshotList<String>().with(64, "owned");
        assertThat(FactorySnapshotList.immutableCopy(owned)).isSameAs(owned);
        assertThatThrownBy(() -> owned.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> owned.get(1)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
