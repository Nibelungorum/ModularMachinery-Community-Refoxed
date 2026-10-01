package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.publicapi.recipe.component.*;
import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Typed component adapters retaining core identity. @author howxu <dev@howxu.cn> */
public final class ComponentAdapters {
    private ComponentAdapters() {}
    public static ComponentCondition wrap(ComponentPredicate value) {
        return switch (value) {
            case ComponentPredicate.Exact v -> new Exact(v);
            case ComponentPredicate.MapValue v -> new MapView(v);
            case ComponentPredicate.ListValue v -> new ListView(v);
            case ComponentPredicate.Range v -> new Range(v);
            case ComponentPredicate.TextValue v -> new Text(v);
        };
    }
    public static ComponentPredicate unwrap(ComponentCondition value) {
        if (value instanceof Condition view) return view.delegate;
        throw new IllegalArgumentException("Condition must be library-produced");
    }
    public static ComponentConstraints wrap(DataComponentPredicateSet value) { return new Constraints(value); }
    public static DataComponentPredicateSet unwrap(ComponentConstraints value) {
        if (value == null) return DataComponentPredicateSet.EMPTY;
        if (value instanceof Constraints view) return view.delegate;
        throw new IllegalArgumentException("Constraints must be library-produced");
    }
    public static ComponentConstraints empty() { return wrap(DataComponentPredicateSet.EMPTY); }
    public static ComponentConstraints ofTypes(Map<DataComponentType<?>, ComponentCondition> values) {
        return wrap(new DataComponentPredicateSet(values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> unwrap(e.getValue())))));
    }
    public static ComponentConstraints ofIds(Map<ResourceLocation, ComponentCondition> values) {
        return wrap(DataComponentPredicateSet.ofIds(values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> unwrap(e.getValue())))));
    }
    public static ExactCondition exact(JsonElement value) { return (ExactCondition) wrap(ComponentPredicate.exact(value)); }
    public static ExactCondition exact(Dynamic<?> value) { return (ExactCondition) wrap(ComponentPredicate.exact(value)); }
    public static MapCondition map(Map<String, ComponentCondition> values) {
        return (MapCondition) wrap(ComponentPredicate.map(values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> unwrap(e.getValue())))));
    }
    public static ListCondition list(List<ComponentCondition> values) { return (ListCondition) wrap(ComponentPredicate.list(values.stream().map(ComponentAdapters::unwrap).toList())); }
    public static RangeCondition range(double min, double max) { return (RangeCondition) wrap(ComponentPredicate.range(min, max)); }
    public static TextCondition text(String value, TextMatchMode mode) { return (TextCondition) wrap(ComponentPredicate.text(value, mode(mode))); }
    public static TextCondition text(Component value, TextMatchMode mode) { return (TextCondition) wrap(ComponentPredicate.text(value, mode(mode))); }
    private static ComponentPredicate.TextMode mode(TextMatchMode mode) { return switch (mode) { case PLAIN -> ComponentPredicate.TextMode.PLAIN; case FULL -> ComponentPredicate.TextMode.FULL; }; }
    private abstract static class Condition implements ComponentCondition {
        final ComponentPredicate delegate;
        Condition(ComponentPredicate delegate) { this.delegate = delegate; }
        public boolean isExact() { return delegate.isExact(); }
        public boolean matches(Dynamic<?> candidate) { return delegate.matches(candidate); }
    }
    private static final class Exact extends Condition implements ExactCondition {
        Exact(ComponentPredicate.Exact value) { super(value); }
        public Dynamic<?> value() { return ((ComponentPredicate.Exact) delegate).value(); }
    }
    private static final class MapView extends Condition implements MapCondition {
        MapView(ComponentPredicate.MapValue value) { super(value); }
        public Map<String, ComponentCondition> values() { return ((ComponentPredicate.MapValue) delegate).values().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> wrap(e.getValue()))); }
    }
    private static final class ListView extends Condition implements ListCondition {
        ListView(ComponentPredicate.ListValue value) { super(value); }
        public List<ComponentCondition> values() { return ((ComponentPredicate.ListValue) delegate).values().stream().map(ComponentAdapters::wrap).toList(); }
    }
    private static final class Range extends Condition implements RangeCondition {
        Range(ComponentPredicate.Range value) { super(value); }
        public double min() { return ((ComponentPredicate.Range) delegate).min(); }
        public double max() { return ((ComponentPredicate.Range) delegate).max(); }
    }
    private static final class Text extends Condition implements TextCondition {
        Text(ComponentPredicate.TextValue value) { super(value); }
        public Component value() { return ((ComponentPredicate.TextValue) delegate).value(); }
        public TextMatchMode mode() { return switch (((ComponentPredicate.TextValue) delegate).mode()) { case PLAIN -> TextMatchMode.PLAIN; case FULL -> TextMatchMode.FULL; }; }
    }
    private record Constraints(DataComponentPredicateSet delegate) implements ComponentConstraints {
        public Map<DataComponentType<?>, ComponentCondition> values() { return delegate.values().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> wrap(e.getValue()))); }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public boolean hasNonExactValues() { return delegate.hasNonExactValues(); }
        public boolean matches(ItemStack stack) { return delegate.matches(stack); }
        public boolean matches(ItemStack stack, DynamicOps<?> ops) { return delegate.matches(stack, ops); }
        public ItemStack displayStack(Item item, int count) { return delegate.displayStack(item, count); }
        public ItemStack displayStack(Item item, int count, DynamicOps<?> ops) { return delegate.displayStack(item, count, ops); }
        public void applyTo(ItemStack stack) { delegate.applyTo(stack); }
        public void applyTo(ItemStack stack, DynamicOps<?> ops) { delegate.applyTo(stack, ops); }
        public Optional<DataComponentPatch> exactPatch() { return delegate.exactPatch(); }
    }
}
