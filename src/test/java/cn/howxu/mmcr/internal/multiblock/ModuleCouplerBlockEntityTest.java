package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class ModuleCouplerBlockEntityTest {

    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private static final ResourceKey<Level> MODULE_DIMENSION = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath("mmcr_test", "module"));

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void connection_round_trips_host_and_module_global_positions() throws Exception {
        var coupler = coupler();
        GlobalPos host = GlobalPos.of(Level.OVERWORLD, new BlockPos(4, 5, 6));
        GlobalPos module = GlobalPos.of(MODULE_DIMENSION, new BlockPos(-7, 8, -9));

        coupler.setConnection(host, module);
        var restored = coupler();
        invokeLoadAdditional(restored, inputFrom(coupler));

        assertThat(restored.connectedHost()).contains(host);
        assertThat(restored.connectedModule()).contains(module);
    }

    @Test
    void clear_connection_persists_empty_connection() throws Exception {
        var coupler = coupler();
        coupler.setConnection(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                GlobalPos.of(MODULE_DIMENSION, new BlockPos(1, 2, 3)));
        coupler.clearConnection();

        var restored = coupler();
        invokeLoadAdditional(restored, inputFrom(coupler));

        assertThat(restored.connectedHost()).isEmpty();
        assertThat(restored.connectedModule()).isEmpty();
    }

    @Test
    void coordinate_key_is_stable_across_save_load() throws Exception {
        GlobalPos host = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, -64, 30));
        GlobalPos module = GlobalPos.of(MODULE_DIMENSION, new BlockPos(-200000, 319, 200000));
        var coupler = coupler();

        coupler.setConnection(host, module);
        var first = save(coupler).buildResult();
        var restored = coupler();
        invokeLoadAdditional(restored, TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, first));
        var second = save(restored).buildResult();

        assertThat(second).isEqualTo(first);
    }

    private static ModuleCouplerBlockEntity coupler() {
        return (ModuleCouplerBlockEntity) ModBlockEntities.MODULE_BRIDGE.get().create(
                BlockPos.ZERO, ModBlocks.MODULE_BRIDGE.get().defaultBlockState());
    }

    private static ValueInput inputFrom(ModuleCouplerBlockEntity coupler) throws Exception {
        return TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, save(coupler).buildResult());
    }

    private static TagValueOutput save(ModuleCouplerBlockEntity coupler) throws Exception {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        Method method = coupler.getClass().getDeclaredMethod("saveAdditional", ValueOutput.class);
        method.setAccessible(true);
        method.invoke(coupler, output);
        return output;
    }

    private static void invokeLoadAdditional(ModuleCouplerBlockEntity coupler,
                                             ValueInput input) throws Exception {
        Method method = coupler.getClass().getDeclaredMethod("loadAdditional", ValueInput.class);
        method.setAccessible(true);
        method.invoke(coupler, input);
    }
}
