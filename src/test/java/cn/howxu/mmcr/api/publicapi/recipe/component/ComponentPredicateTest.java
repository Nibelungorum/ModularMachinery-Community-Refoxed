package cn.howxu.mmcr.api.publicapi.recipe.component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentPredicateTest {

    @Test
    void valueDeclarationsCompareByTheirJsonAndStructure() {
        assertThat(ComponentPredicate.exact(new JsonPrimitive(3)))
                .isEqualTo(ComponentPredicate.exact(new JsonPrimitive(3)));
        assertThat(ComponentPredicate.map(Map.of("nested", ComponentPredicate.range(1, 2))))
                .isEqualTo(ComponentPredicate.map(Map.of("nested", ComponentPredicate.range(1, 2))));
        assertThat(ComponentPredicate.list(List.of(ComponentPredicate.text("Required", ComponentPredicate.TextMode.PLAIN))))
                .isEqualTo(ComponentPredicate.list(List.of(ComponentPredicate.text("Required", ComponentPredicate.TextMode.PLAIN))));
        assertThat(ComponentPredicate.range(1, 2)).isEqualTo(ComponentPredicate.range(1, 2));
        assertThat(ComponentPredicate.text("Required", ComponentPredicate.TextMode.FULL))
                .isEqualTo(ComponentPredicate.text("Required", ComponentPredicate.TextMode.FULL));
    }

    @Test
    void declarationsCopyMutableJsonMapsAndLists() {
        JsonObject exactValue = new JsonObject();
        exactValue.addProperty("value", 1);
        Map<String, ComponentPredicate> predicateValues = new LinkedHashMap<>();
        predicateValues.put("nested", ComponentPredicate.exact(exactValue));
        ComponentPredicate predicate = ComponentPredicate.map(predicateValues);
        List<ComponentPredicate> predicateListValues = new ArrayList<>();
        predicateListValues.add(predicate);
        ComponentPredicate list = ComponentPredicate.list(predicateListValues);
        Map<Identifier, ComponentPredicate> componentValues = new LinkedHashMap<>();
        componentValues.put(Identifier.parse("minecraft:repair_cost"), list);
        DataComponentPredicateSet declarations = new DataComponentPredicateSet(componentValues);

        exactValue.addProperty("changed", true);
        predicateValues.clear();
        predicateListValues.clear();
        componentValues.clear();

        ComponentPredicate.Exact nested = (ComponentPredicate.Exact) ((ComponentPredicate.MapValue)
                ((ComponentPredicate.ListValue) declarations.values().get(Identifier.parse("minecraft:repair_cost")))
                        .values().getFirst()).values().get("nested");
        assertThat(nested.value())
                .isEqualTo(JsonParser.parseString("{\"value\":1}"));
        nested.value().getAsJsonObject().addProperty("changed", true);
        assertThat(nested.value()).isEqualTo(JsonParser.parseString("{\"value\":1}"));
        assertThatThrownBy(() -> declarations.values().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((ComponentPredicate.MapValue) predicate).values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((ComponentPredicate.ListValue) list).values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void detectsExactForms() {
        DataComponentPredicateSet exact = new DataComponentPredicateSet(Map.of(
                Identifier.parse("minecraft:repair_cost"), ComponentPredicate.exact(new JsonPrimitive(1))));
        DataComponentPredicateSet ranged = new DataComponentPredicateSet(Map.of(
                Identifier.parse("minecraft:repair_cost"), ComponentPredicate.range(1, 4)));

        assertThat(ComponentPredicate.exact(new JsonPrimitive(1)).isExact()).isTrue();
        assertThat(ComponentPredicate.range(1, 4).isExact()).isFalse();
        assertThat(exact.hasNonExactValues()).isFalse();
        assertThat(ranged.hasNonExactValues()).isTrue();
    }
}
