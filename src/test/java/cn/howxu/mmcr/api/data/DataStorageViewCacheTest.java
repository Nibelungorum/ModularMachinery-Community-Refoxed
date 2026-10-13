package cn.howxu.mmcr.api.data;

import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.api.data.DataStorage.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static cn.howxu.mmcr.api.data.view.DataStorage.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies cached KJS-facing data views remain fresh across mutations and transactions.
 * @author howxu <dev@howxu.cn>
 */
class DataStorageViewCacheTest {
    @Test
    void fresh_views_share_immutable_snapshots_and_recursive_values() {
        DataStorage storage = new DataStorage();
        DataValue payload = DataValue.map(Map.of("entries", DataValue.list(List.of(DataValue.of("original")))));
        storage.set("payload", payload);
        var first = view(storage);
        var snapshot = first.values();
        var projected = snapshot.get("payload");
        var entries = projected.asMap().orElseThrow().get("entries").asList().orElseThrow();

        assertThat(view((Object) storage).values()).isSameAs(snapshot);
        assertThat(first.get("payload")).containsSame(projected);
        assertThat(view(storage).get("payload")).containsSame(projected);
        assertThat(projected).isSameAs(cn.howxu.mmcr.api.data.view.DataValue.fromInternal(payload));
        assertThat(entries.getFirst().stringValue()).isEqualTo("original");
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(projected.asMap().orElseThrow()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(entries::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void no_op_writes_and_missing_removals_keep_the_converted_snapshot() {
        DataStorage storage = new DataStorage();
        var view = view(storage);
        view.set("payload", Map.of("state", "original"));
        var snapshot = view.values();

        view.set("payload", Map.of("state", "original"));
        assertThat(view.remove("missing")).isEmpty();

        assertThat(view.values()).isSameAs(snapshot);
        assertThat(view(storage).values()).isSameAs(snapshot);
    }

    @Test
    void changed_and_removed_values_do_not_modify_old_snapshots() {
        DataStorage storage = new DataStorage();
        var view = view(storage);
        view.set("payload", Map.of("state", "original"));
        var original = view.values();

        view.set("payload", Map.of("state", "replacement"));
        var replacement = view(storage).values();
        assertThat(replacement).isNotSameAs(original);
        assertThat(replacement.get("payload").asMap().orElseThrow().get("state").stringValue())
                .isEqualTo("replacement");
        assertThat(original.get("payload").asMap().orElseThrow().get("state").stringValue()).isEqualTo("original");
        assertThat(view.remove("payload")).containsSame(replacement.get("payload"));
        assertThat(view.values()).isEmpty();
        assertThat(view.get("payload")).isEmpty();
        assertThat(replacement).containsKey("payload");
    }

    @Test
    void unrelated_changes_reuse_the_unchanged_recursive_value() {
        DataStorage storage = new DataStorage();
        var view = view(storage);
        view.set("payload", List.of(Map.of("state", "original")));
        var snapshot = view.values();
        var payload = snapshot.get("payload");

        view.set("other", "changed");

        assertThat(view.values()).isNotSameAs(snapshot);
        assertThat(view.values().get("payload")).isSameAs(payload);
        assertThat(view(storage).get("payload")).containsSame(payload);
    }

    @Test
    void concurrent_cold_conversions_share_recursive_projections() throws Exception {
        DataValue shared = DataValue.list(List.of(DataValue.map(Map.of("state", DataValue.of("original")))));
        DataValue payload = DataValue.map(Map.of("left", shared, "right", shared));
        CyclicBarrier start = new CyclicBarrier(4);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 4).mapToObj(ignored -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                var value = cn.howxu.mmcr.api.data.view.DataValue.fromInternal(payload);
                var projected = StorageAdapters.wrap(value).asMap().orElseThrow();
                var left = projected.get("left").asList().orElseThrow();
                assertThat(projected.get("right").asList().orElseThrow()).isSameAs(left);
                assertThat(left.getFirst().asMap().orElseThrow().get("state").stringValue()).isEqualTo("original");
                return projected;
            })).toList();

            var projected = tasks.getFirst().get(10, TimeUnit.SECONDS);
            for (var task : tasks) assertThat(task.get(10, TimeUnit.SECONDS)).isSameAs(projected);
        }
    }

    @Test
    void cached_reads_ignore_staged_writes_and_refresh_after_commit() {
        DataStorage storage = new DataStorage();
        storage.set("state", DataValue.of("original"));
        var view = view(storage);
        var original = view.values();

        Transaction abandoned = Transaction.create();
        storage.set("state", DataValue.of("temporary"), abandoned);
        assertThat(view(storage).values()).isSameAs(original);
        assertThat(view.get("state")).containsSame(original.get("state"));

        Transaction committed = Transaction.create();
        storage.set("state", DataValue.of("committed"), committed);
        assertThat(view.values()).isSameAs(original);
        committed.commit();
        assertThat(view.values()).isNotSameAs(original);
        assertThat(view(storage).values().get("state").stringValue()).isEqualTo("committed");
        assertThat(original.get("state").stringValue()).isEqualTo("original");
    }
}
