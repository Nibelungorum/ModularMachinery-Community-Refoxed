package cn.howxu.mmcr.publicapi.data;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataStorage.Transaction;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.howxu.mmcr.api.data.view.DataStorage.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies Java API projections are shared without caching mutable inputs or stale transaction data.
 * @author howxu <dev@howxu.cn>
 */
class DataStoreCacheTest {
    @Test
    void fresh_adapters_share_store_snapshots_and_nested_collections() {
        DataStorage storage = new DataStorage();
        DataStore first = StorageAdapters.wrap(view(storage));
        first.set("payload", DataKey.map(Map.of("entries", DataKey.list(List.of(DataKey.of("original"))))));
        var snapshot = first.values();
        var payload = snapshot.get("payload").asMap().orElseThrow();
        var entries = payload.get("entries").asList().orElseThrow();
        DataStore second = StorageAdapters.wrap(view((Object) storage));

        assertThat(second.values()).isSameAs(snapshot);
        assertThat(second.get("payload").orElseThrow().asMap().orElseThrow()).isSameAs(payload);
        assertThat(first.get("payload").orElseThrow().asMap().orElseThrow().get("entries").asList().orElseThrow())
                .isSameAs(entries);
        assertThat(entries.getFirst().stringValue()).isEqualTo("original");
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(payload::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(entries::clear).isInstanceOf(UnsupportedOperationException.class);

        first.set("other", DataKey.of("changed"));
        assertThat(second.values()).isNotSameAs(snapshot);
        assertThat(second.values().get("payload").asMap().orElseThrow()).isSameAs(payload);
    }

    @Test
    void writes_and_removals_refresh_new_adapters_without_changing_old_snapshots() {
        DataStorage storage = new DataStorage();
        DataStore store = StorageAdapters.wrap(view(storage));
        store.set("state", DataKey.of("original"));
        var original = store.values();
        store.set("state", DataKey.of("original"));
        assertThat(store.remove("missing")).isEmpty();
        assertThat(store.values()).isSameAs(original);

        store.set("state", DataKey.of("replacement"));
        var replacement = StorageAdapters.wrap(view(storage)).values();
        assertThat(replacement.get("state").stringValue()).isEqualTo("replacement");
        assertThat(original.get("state").stringValue()).isEqualTo("original");
        assertThat(store.remove("state").orElseThrow().stringValue()).isEqualTo("replacement");
        assertThat(StorageAdapters.wrap(view(storage)).values()).isEmpty();
        assertThat(store.get("state")).isEmpty();
        assertThat(replacement).containsKey("state");
    }

    @Test
    void transaction_reads_ignore_staged_writes_and_refresh_after_commit() {
        DataStorage storage = new DataStorage();
        DataStore store = StorageAdapters.wrap(view(storage));
        store.set("state", DataKey.of("original"));
        var original = store.values();
        Transaction abandoned = Transaction.create();
        store.set("state", DataKey.of("temporary"), StorageAdapters.wrap(abandoned));
        assertThat(StorageAdapters.wrap(view(storage)).values()).isSameAs(original);

        Transaction committed = Transaction.create();
        store.set("state", DataKey.of("committed"), StorageAdapters.wrap(committed));
        assertThat(store.get("state").orElseThrow().stringValue()).isEqualTo("original");
        committed.commit();
        assertThat(store.values()).isNotSameAs(original);
        assertThat(StorageAdapters.wrap(view(storage)).values().get("state").stringValue()).isEqualTo("committed");
        assertThat(original.get("state").stringValue()).isEqualTo("original");
    }

    @Test
    void staged_nested_collections_preserve_projections_until_commit() {
        DataStorage storage = new DataStorage();
        DataStore store = StorageAdapters.wrap(view(storage));
        store.set("payload", DataKey.map(Map.of("entries", DataKey.list(List.of(DataKey.of("original"))))));
        var original = store.values().get("payload").asMap().orElseThrow();
        var originalEntries = original.get("entries").asList().orElseThrow();
        Transaction transaction = Transaction.create();
        store.set("payload", DataKey.map(Map.of("entries", DataKey.list(List.of(DataKey.of("replacement"))))),
                StorageAdapters.wrap(transaction));
        DataStore fresh = StorageAdapters.wrap(view(storage));
        assertThat(fresh.get("payload").orElseThrow().asMap().orElseThrow()).isSameAs(original);
        assertThat(fresh.values().get("payload").asMap().orElseThrow().get("entries").asList().orElseThrow())
                .isSameAs(originalEntries);
        transaction.commit();
        assertThat(store.get("payload").orElseThrow().asMap().orElseThrow().get("entries")
                .asList().orElseThrow().getFirst().stringValue()).isEqualTo("replacement");
        assertThat(originalEntries.getFirst().stringValue()).isEqualTo("original");
    }

    @Test
    void equal_store_contents_remain_isolated() {
        DataStore first = StorageAdapters.wrap(view(new DataStorage()));
        DataStore second = StorageAdapters.wrap(view(new DataStorage()));
        first.set("state", DataKey.of("original"));
        second.set("state", DataKey.of("original"));
        var secondSnapshot = second.values();
        assertThat(first.values()).isEqualTo(secondSnapshot).isNotSameAs(secondSnapshot);

        first.set("state", DataKey.of("changed"));

        assertThat(second.values()).isSameAs(secondSnapshot);
        assertThat(second.get("state").orElseThrow().stringValue()).isEqualTo("original");
    }

    @Test
    void general_map_conversion_does_not_cache_mutable_sources() {
        Map<String, DataValue> source = new LinkedHashMap<>();
        source.put("state", DataValue.of("original"));
        var original = StorageAdapters.values(source);
        source.put("state", DataValue.of("changed"));

        assertThat(StorageAdapters.values(source).get("state").stringValue()).isEqualTo("changed");
        assertThat(original.get("state").stringValue()).isEqualTo("original");
    }
}
