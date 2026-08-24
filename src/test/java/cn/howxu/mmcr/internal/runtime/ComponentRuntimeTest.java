package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies component replacement and capability publication semantics.
 *
 * @author howxu <dev@howxu.cn>
 */
class ComponentRuntimeTest {

    @Test
    void replacing_with_the_same_effective_components_does_not_increment_capability_version() {
        ComponentRuntime runtime = new ComponentRuntime();
        List<ProcessingComponent> components = List.of(new ProcessingComponent(null, "input", BlockPos.ZERO));

        runtime.replaceComponents(components);
        long version = runtime.capabilityVersion();
        runtime.replaceComponents(List.copyOf(components));

        assertThat(runtime.components()).containsExactlyElementsOf(components);
        assertThat(runtime.capabilities()).isEmpty();
        assertThat(runtime.capabilityVersion()).isEqualTo(version);
    }

    @Test
    void component_and_capability_views_are_immutable() {
        ComponentRuntime runtime = new ComponentRuntime();
        runtime.replaceComponents(List.of(new ProcessingComponent(null, "input", BlockPos.ZERO)));

        assertThatThrownBy(() -> runtime.components().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> runtime.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void effective_component_replacement_increments_capability_version_once() {
        ComponentRuntime runtime = new ComponentRuntime();
        ProcessingComponent first = new ProcessingComponent(null, "first", BlockPos.ZERO);
        ProcessingComponent second = new ProcessingComponent(null, "second", BlockPos.ZERO);

        runtime.replaceComponents(List.of(first));
        long firstVersion = runtime.capabilityVersion();
        runtime.replaceComponents(List.of(second));

        assertThat(runtime.capabilityVersion()).isEqualTo(firstVersion + 1);
        runtime.replaceComponents(List.of(second));
        assertThat(runtime.capabilityVersion()).isEqualTo(firstVersion + 1);
    }
}
