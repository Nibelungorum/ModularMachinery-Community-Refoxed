package cn.howxu.mmcr.api.recipe.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentPredicateTest {

    @Test
    void listMatchesWhenGreedyFirstCandidateWouldBlockAnotherRequirement() {
        JsonArray values = new JsonArray();
        values.add(1);
        values.add(2);
        ComponentPredicate predicate = ComponentPredicate.list(List.of(
                ComponentPredicate.range(1, 2),
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))
        ));

        assertThat(predicate.matches(new Dynamic<>(JsonOps.INSTANCE, values))).isTrue();
    }
}
