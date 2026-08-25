package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleRecipeHostRequirementTest {
    private static final Identifier MODULE_ID = MMCR.id("module_recipe_host_module");
    private static final Identifier HOST_A = MMCR.id("module_recipe_host_a");
    private static final Identifier HOST_B = MMCR.id("module_recipe_host_b");
    private static final Identifier HOST_C = MMCR.id("module_recipe_host_c");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void emptyRequiredHostSetAcceptsAnyConnectedHost() {
        MachineRecipe recipe = recipe("empty_required_hosts", Set.of());

        assertThat(recipe.requiredHostIds()).isEmpty();
        assertThat(recipe.canRunOnConnectedHost(HOST_A)).isTrue();
    }

    @Test
    void requiredHostIdsRestrictRecipeToListedHosts() {
        MachineRecipe recipe = recipe("required_hosts", Set.of(HOST_A, HOST_B));

        assertThat(recipe.requiredHostIds()).containsExactlyInAnyOrder(HOST_A, HOST_B);
        assertThat(recipe.canRunOnConnectedHost(HOST_A)).isTrue();
        assertThat(recipe.canRunOnConnectedHost(HOST_C)).isFalse();
    }

    @Test
    void connectedModuleStatusAcceptsOnlyMatchingHostRequirements() {
        ModuleConnectionStatus connected = ModuleConnectionStatus.connected(HOST_A);

        assertThat(connected.canRunRecipe(Set.of())).isTrue();
        assertThat(connected.canRunRecipe(Set.of(HOST_A))).isTrue();
        assertThat(connected.canRunRecipe(Set.of(HOST_B))).isFalse();
    }

    @Test
    void requiredHostIdsRoundTripThroughCodec() {
        MachineRecipe recipe = recipe("codec_required_hosts", Set.of(HOST_A, HOST_B));

        MachineRecipe decoded = MachineRecipe.CODEC.codec()
                .parse(JsonOps.INSTANCE, MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow())
                .getOrThrow();

        assertThat(decoded.requiredHostIds()).containsExactlyInAnyOrder(HOST_A, HOST_B);
    }

    private static MachineRecipe recipe(String path, Set<Identifier> requiredHostIds) {
        return new MachineRecipe(MMCR.id(path), MODULE_ID, 20, List.of(), List.of(), List.of(), 0, 1,
                false, List.of(), List.of(), false, List.of(), requiredHostIds);
    }
}
