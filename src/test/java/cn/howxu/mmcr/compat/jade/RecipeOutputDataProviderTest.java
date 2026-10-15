package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link RecipeOutputDataProvider} routes the controller's recipe outputs through
 * {@link RecipeOutputCodec} when the target is a controller and writes nothing otherwise.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeOutputDataProviderTest {

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void roundTripsControllerOutputsThroughCodec() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        MachineOutput stone = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(stone));

        CompoundTag data = new CompoundTag();
        RecipeOutputDataProvider.INSTANCE.appendServerData(data, accessorFor(controller));

        assertThat(data.contains(RecipeOutputCodec.OUTPUT_KEY)).isTrue();
        List<MachineOutputAmount> decoded = RecipeOutputCodec.read(data);
        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).output()).isInstanceOf(MachineOutput.ItemOutput.class);
        assertThat(((MachineOutput.ItemOutput) decoded.get(0).output()).stack().getCount()).isEqualTo(1);
        assertThat(decoded.get(0).amount()).isEqualTo(4L);
        assertThat(decoded.get(0).output().chance()).isEqualTo(1F);
    }

    @Test
    void emptyOutputsDoNotWriteTheKey() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();

        CompoundTag data = new CompoundTag();
        RecipeOutputDataProvider.INSTANCE.appendServerData(data, accessorFor(controller));

        assertThat(data.contains(RecipeOutputCodec.OUTPUT_KEY)).isFalse();
        assertThat(RecipeOutputCodec.read(data)).isEmpty();
    }

    @Test
    void nonControllerTargetWritesNothing() {
        CompoundTag data = new CompoundTag();
        RecipeOutputDataProvider.INSTANCE.appendServerData(data, accessorFor(new Object()));

        assertThat(data.contains(RecipeOutputCodec.OUTPUT_KEY)).isFalse();
    }

    @Test
    void codecPreservesLongAggregatedAmounts() {
        CompoundTag data = new CompoundTag();
        RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F),
                Long.MAX_VALUE)));

        List<MachineOutputAmount> decoded = RecipeOutputCodec.read(data);
        assertThat(decoded).hasSize(1);
        assertThat(decoded.getFirst()).satisfies(output -> {
            assertThat(output.amount()).isEqualTo(Long.MAX_VALUE);
            assertThat(((MachineOutput.ItemOutput) output.output()).stack().getCount())
                    .isEqualTo(1);
        });
    }

    private static BlockAccessor accessorFor(Object target) {
        return (BlockAccessor) Proxy.newProxyInstance(
                BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> {
                    if ("getTarget".equals(method.getName())) return target;
                    if ("nbtOps".equals(method.getName())) return NbtOps.INSTANCE;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
