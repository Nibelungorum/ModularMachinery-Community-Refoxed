package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the capability host boundary and the built-in port capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityHostTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void host_snapshot_is_immutable_and_keeps_capability_identity() {
        IOPortBlockEntity port = new MixedPort(BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        CapabilityHost host = port;

        CapabilitySnapshot first = host.capabilitySnapshot();
        CapabilitySnapshot second = host.capabilitySnapshot();

        assertThat(first).isSameAs(second);
        assertThat(first.capabilities()).hasSize(2);
        assertThat(first.capabilities().get(0)).isSameAs(second.capabilities().get(0));
        assertThatThrownBy(() -> first.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void port_base_declares_capability_snapshot_as_abstract() throws NoSuchMethodException {
        assertThat(Modifier.isAbstract(IOPortBlockEntity.class.getMethod("capabilitySnapshot").getModifiers())).isTrue();
    }

    @Test
    void item_capability_uses_native_item_handler_and_operation_contract() {
        MachineCapability capability = port("item_input_bus").capabilitySnapshot().capabilities().getFirst();
        ItemBusCapability item = (ItemBusCapability) capability;
        IItemHandler handler = item.itemHandler();

        assertThat(handler.getSlots()).isGreaterThan(1);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertThat(handler.insertItem(0, iron, false)).isEmpty();
        assertThat(item.prepare(request(item)).commit().success()).isTrue();

        ItemStack stored = handler.getStackInSlot(0);
        assertThat(ItemStack.isSameItemSameComponents(stored, iron)).isTrue();
        assertThat(stored.getCount()).isEqualTo(iron.getCount());
    }

    @Test
    void fluid_capability_uses_long_fluid_storage() {
        MachineCapability capability = port("fluid_input_hatch").capabilitySnapshot().capabilities().getFirst();
        FluidHatchCapability fluid = (FluidHatchCapability) capability;
        IFluidHandler handler = fluid.fluidHandler();

        assertThat(handler).isInstanceOf(LongFluidStorage.class);
        FluidStack water = new FluidStack(Fluids.WATER, 750);
        assertThat(handler.fill(water, IFluidHandler.FluidAction.EXECUTE)).isEqualTo(750);

        FluidStack stored = handler.getFluidInTank(0);
        assertThat(FluidStack.isSameFluidSameComponents(stored, water)).isTrue();
        assertThat(stored.getAmount()).isEqualTo(water.getAmount());
    }

    @Test
    void energy_capability_uses_long_value_storage() {
        MachineCapability capability = port("energy_input_hatch_tiny").capabilitySnapshot().capabilities().getFirst();
        EnergyHatchCapability energy = (EnergyHatchCapability) capability;
        LongEnergyStorage storage = (LongEnergyStorage) energy.energyStorage();

        assertThat(storage.insertLong(2_000L, false)).isEqualTo(500L);
        assertThat(storage.getAmountAsLong()).isEqualTo(500L);
        assertThat(energy.prepare(request(energy)).commit().success()).isTrue();
    }

    @Test
    void capability_definitions_use_storage_protocols_from_the_host() {
        StorageHost host = new StorageHost();

        ItemBusCapability item = (ItemBusCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.ITEM_TYPE)
                .factory().create(context(host));
        FluidHatchCapability fluid = (FluidHatchCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.FLUID_TYPE)
                .factory().create(context(host));
        EnergyHatchCapability energy = (EnergyHatchCapability) CapabilityRegistry.get(BuiltinCapabilityDefinitions.ENERGY_TYPE)
                .factory().create(context(host));

        assertThat(item.itemHandler()).isSameAs(host.nativeItemHandler());
        assertThat(fluid.fluidHandler()).isSameAs(host.nativeFluidHandler());
        assertThat(energy.energyStorage()).isSameAs(host.nativeEnergyStorage());
        assertThat(item.directions().supports(IOType.INPUT)).isTrue();
        assertThat(fluid.directions().supports(IOType.INPUT)).isTrue();
        assertThat(energy.directions().supports(IOType.INPUT)).isTrue();
    }

    private static CapabilityRequest request(MachineCapability capability) {
        if (capability.facet(ItemHandlerFacet.class).isPresent()) {
            return new CapabilityRequests.ItemRequest(capability.type(), IOType.INPUT, 1, List.of());
        }
        if (capability.facet(FluidHandlerFacet.class).isPresent()) {
            return new CapabilityRequests.FluidRequest(capability.type(), IOType.INPUT, 1, List.of());
        }
        if (capability.facet(EnergyStorageFacet.class).isPresent()) {
            return new CapabilityRequests.ValueRequest(capability.type(), IOType.INPUT, 1, 1, false);
        }
        return new TestRequest(capability.type(), IOType.INPUT, 1);
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        BlockState state = ModBlocks.BLOCKS.get(id).get().defaultBlockState();
        return kind.entityFactory().create(BlockPos.ZERO, state);
    }

    private record TestRequest(CapabilityType type, IOType ioType, long parallelism) implements CapabilityRequest {}

    private static final class MixedPort extends IOPortBlockEntity {
        private static final IOPortKind KIND = new IOPortKind() {
            @Override public String id() { return "mixed_test"; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return MixedPort::new;
            }
            @Override public PortDefinition definition() {
                return PortDefinition.of(MMCR.id("mixed_test"));
            }
        };

        private MixedPort(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    state);
        }

        private CapabilitySnapshot capabilitySnapshot;

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return KIND; }
        @Override public CapabilitySnapshot capabilitySnapshot() {
            if (capabilitySnapshot == null) {
                capabilitySnapshot = new CapabilitySnapshot(List.of(new TestCapability("first"), new TestCapability("second")));
            }
            return capabilitySnapshot;
        }
    }

    private static CapabilityCreationContext context(CapabilityHost host) {
        return new CapabilityCreationContext() {
            @Override public CapabilityHost host() { return host; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public <T> Optional<T> service(Class<T> serviceType) {
                return serviceType.isInstance(host) ? Optional.of(serviceType.cast(host)) : Optional.empty();
            }
            @Override public Runnable onChanged() { return () -> {}; }
        };
    }

    private static final class StorageHost extends IOPortBlockEntity {
        private final LongItemStorage itemStorage = new LongItemStorage(2, 100L, () -> {});
        private final LongFluidStorage fluidStorage = new LongFluidStorage(2, 100L, () -> {});
        private final LongEnergyStorage energyStorage = new LongEnergyStorage(100L, 20L, () -> {});

        private StorageHost() {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return PortKinds.ITEM_INPUT; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(List.of()); }
        @Override public IItemHandler nativeItemHandler() { return itemStorage; }
        @Override public IFluidHandler nativeFluidHandler() { return fluidStorage; }
        @Override public IEnergyStorage nativeEnergyStorage() { return energyStorage; }
    }

    private record TestCapability(String id) implements MachineCapability {
        @Override public CapabilityType type() { return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", id)); }
        @Override public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }
        @Override public CapabilityView view() { return new CapabilityView() {
            @Override public CapabilityType type() { return TestCapability.this.type(); }
            @Override public CapabilityDirections directions() {
                return TestCapability.this.directions();
            }
        }; }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }
}
