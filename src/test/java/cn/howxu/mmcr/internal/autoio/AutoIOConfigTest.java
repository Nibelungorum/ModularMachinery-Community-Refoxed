package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class AutoIOConfigTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void new_config_defaults_to_enabled_on_all_sides() {
        AutoIOConfig config = new AutoIOConfig();

        assertThat(config.enabled()).isFalse();
        assertThat(config.enabledSides()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void direction_bitmask_round_trips_all_sides() {
        EnumSet<Direction> sides = EnumSet.of(Direction.NORTH, Direction.EAST, Direction.DOWN);

        int mask = AutoIOConfig.toMask(sides);

        assertThat(AutoIOConfig.fromMask(mask)).containsExactlyInAnyOrder(Direction.NORTH, Direction.EAST, Direction.DOWN);
    }

    @Test
    void config_save_load_preserves_enabled_and_sides() {
        AutoIOConfig config = new AutoIOConfig();
        config.setEnabled(true);
        config.setAllSides(false);
        config.setSide(Direction.NORTH, true);
        config.setSide(Direction.SOUTH, true);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));

        config.save(output);
        AutoIOConfig loaded = AutoIOConfig.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(loaded.enabled()).isTrue();
        assertThat(loaded.enabledSides()).containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH);
    }

    @Test
    void missing_saved_state_loads_as_enabled_on_all_sides() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));

        AutoIOConfig loaded = AutoIOConfig.load(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(loaded.enabled()).isFalse();
        assertThat(loaded.enabledSides()).containsExactlyInAnyOrder(Direction.values());
    }

    @Test
    void can_disable_all_sides_at_once() {
        AutoIOConfig config = new AutoIOConfig();

        config.setAllSides(false);

        assertThat(config.enabledSides()).isEmpty();
    }

    @Test
    void toggling_side_is_symmetric() {
        AutoIOConfig config = new AutoIOConfig();
        config.setAllSides(false);

        config.toggleSide(Direction.WEST);
        config.toggleSide(Direction.WEST);

        assertThat(config.enabledSides()).isEmpty();
    }

    @Test
    void itemAndFluidProfilesHaveIndependentEnabledSides() {
        ProfileHost host = new ProfileHost();
        CapabilityType item = new CapabilityType(MMCR.id("item"));
        CapabilityType fluid = new CapabilityType(MMCR.id("fluid"));

        host.autoIOConfig(item).setSide(Direction.NORTH, false);

        assertThat(host.isAutoIOSideExposed(item, Direction.NORTH)).isFalse();
        assertThat(host.isAutoIOSideExposed(fluid, Direction.NORTH)).isTrue();
    }

    @Test
    void keyed_profiles_round_trip_for_each_exposed_capability() {
        ProfileHost host = new ProfileHost();
        CapabilityType item = new CapabilityType(MMCR.id("item"));
        CapabilityType fluid = new CapabilityType(MMCR.id("fluid"));
        host.autoIOConfig(item).setEnabled(true);
        host.autoIOConfig(item).setSide(Direction.NORTH, false);
        host.autoIOConfig(fluid).setSide(Direction.SOUTH, false);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        host.saveTo(output);
        ProfileHost restored = new ProfileHost();
        restored.loadFrom(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.autoIOConfig(item).enabled()).isTrue();
        assertThat(restored.isAutoIOSideExposed(item, Direction.NORTH)).isFalse();
        assertThat(restored.isAutoIOSideExposed(fluid, Direction.NORTH)).isTrue();
        assertThat(restored.isAutoIOSideExposed(fluid, Direction.SOUTH)).isFalse();
    }

    private static final class ProfileHost extends IOPortBlockEntity {
        private final IOPortKind kind = PortKinds.ITEM_INPUT;

        private ProfileHost() {
            super(ModBlockEntities.BES.get(PortKinds.ITEM_INPUT.id()).get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of(
                    new ItemBusCapability(this, new LongResourceStorage<>(
                            net.neoforged.neoforge.transfer.item.ItemResource.class, 1, 64L,
                            net.neoforged.neoforge.transfer.item.ItemResource::isEmpty, () -> {}), IOType.INPUT),
                    new FluidHatchCapability(this, new LongFluidStorage(1, 64L, () -> {}), IOType.INPUT)));
        }

        private void saveTo(TagValueOutput output) {
            saveAdditional(output);
        }

        private void loadFrom(ValueInput input) {
            loadAdditional(input);
        }
    }
}
