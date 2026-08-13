package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class AutoIOPortTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void port_config_toggles_enabled_and_sides() {
        IOPortBlockEntity port = port("item_input_bus_normal");

        port.toggleAutoIOEnabled();
        port.toggleAutoIOSide(Direction.EAST);

        assertThat(port.autoIOConfig().enabled()).isTrue();
        assertThat(port.autoIOConfig().enabledSides()).containsExactly(Direction.EAST);
    }

    @Test
    void port_config_persists() throws Exception {
        IOPortBlockEntity port = port("fluid_output_hatch_tiny");
        port.toggleAutoIOEnabled();
        port.toggleAutoIOSide(Direction.NORTH);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));

        invokeSaveAdditional(port, output);
        IOPortBlockEntity restored = port("fluid_output_hatch_tiny");
        invokeLoadAdditional(restored, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.autoIOConfig().enabled()).isTrue();
        assertThat(restored.autoIOConfig().enabledSides()).containsExactly(Direction.NORTH);
    }

    @Test
    void built_in_ports_report_capability_type() {
        assertThat(port("item_input_bus_normal").autoIOCapabilityType()).isEqualTo(AutoIOCapabilityType.ITEM);
        assertThat(port("fluid_input_hatch_tiny").autoIOCapabilityType()).isEqualTo(AutoIOCapabilityType.FLUID);
        assertThat(port("energy_input_hatch_tiny").autoIOCapabilityType()).isEqualTo(AutoIOCapabilityType.ENERGY);
    }

    @Test
    void toggling_auto_io_syncs_port_update_to_clients() {
        IOPortBlockEntity port = port("item_input_bus_normal");
        var level = LevelStub.createWithBlockEntities(java.util.List.of(port));
        port.setLevel(level);

        port.toggleAutoIOEnabled();

        assertThat(LevelStub.sentBlockUpdates(level)).isEqualTo(1);
    }

    @Test
    void adjacentSideUsesAirWhenPortHasNoLevel() {
        IOPortBlockEntity port = port("item_input_bus_normal");

        IOPortBlockEntity.AdjacentSide side = port.adjacentSide(Direction.EAST);

        assertThat(side.side()).isEqualTo(Direction.EAST);
        assertThat(side.state().isAir()).isTrue();
        assertThat(side.icon().isEmpty()).isTrue();
        assertThat(side.name().getString()).isEqualTo(Blocks.AIR.getName().getString());
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseGet(() -> PortKinds.all().stream()
                        .filter(candidate -> candidate.id().equals(id.replace("_normal", "")))
                        .findFirst()
                        .orElseThrow());
        return (IOPortBlockEntity) kind.entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(kind.id()).get().defaultBlockState());
    }

    private static void invokeSaveAdditional(IOPortBlockEntity port, TagValueOutput output) throws Exception {
        Method method = port.getClass().getSuperclass().getDeclaredMethod("saveAdditional", net.minecraft.world.level.storage.ValueOutput.class);
        method.setAccessible(true);
        method.invoke(port, output);
    }

    private static void invokeLoadAdditional(IOPortBlockEntity port, net.minecraft.world.level.storage.ValueInput input) throws Exception {
        Method method = port.getClass().getSuperclass().getDeclaredMethod("loadAdditional", net.minecraft.world.level.storage.ValueInput.class);
        method.setAccessible(true);
        method.invoke(port, input);
    }
}
