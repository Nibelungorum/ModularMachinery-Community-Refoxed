package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityContext;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Verifies native capability registration consumes binding exposure declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
class ModCapabilitiesTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void resolves_only_the_queried_ports_handler_even_with_a_type_wide_adapter() throws Exception {
        var adapters = new ExternalCapabilityContext();
        var id = MMCR.id("test_sided_item_view");
        var cap = BlockCapability.createSided(id, String.class);
        var event = registrationEvent();
        var item = PortKinds.ITEM_INPUT.bindings().getFirst().type();
        adapters.bind(item, new CapabilityBinding.ExternalExposure<>(id, String.class,
                (_, _, _) -> "adapter"));

        for (var kind : List.of(PortKinds.ITEM_INPUT, PortKinds.ITEM_OUTPUT, PortKinds.EXTENDED_ITEM_INPUT)) {
            var binding = kind.bindings().getFirst();
            var exposure = new CapabilityBinding.ExternalExposure<>(id, String.class,
                    (host, _, _) -> {
                        assertThat(((IOPortBlockEntity) host).kind().id()).isEqualTo(kind.id());
                        return kind.id();
                    });
            var effective = kind == PortKinds.EXTENDED_ITEM_INPUT ? binding : new CapabilityBinding(
                    binding.type(), binding.directions(), binding.factory(), binding.tierPolicy(), exposure);
            ModCapabilities.registerNativePort(event, withBindings(kind, effective), adapters);
        }

        for (var kind : List.of(PortKinds.ITEM_INPUT, PortKinds.ITEM_OUTPUT, PortKinds.EXTENDED_ITEM_INPUT)) {
            var port = (IOPortBlockEntity) kind.entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(kind.id()).get().defaultBlockState());
            var level = LevelStub.createWithBlockEntities(List.of(port));
            var expected = kind == PortKinds.EXTENDED_ITEM_INPUT ? "adapter" : kind.id();
            assertThat(cap.getCapability(level, BlockPos.ZERO, null, port, Direction.NORTH)).isEqualTo(expected);
            port.setAutoIOSide(item, Direction.NORTH, false);
            assertThat(cap.getCapability(level, BlockPos.ZERO, null, port, Direction.NORTH)).isNull();
            assertThat(cap.getCapability(level, BlockPos.ZERO, null, port, Direction.SOUTH)).isEqualTo(expected);
        }
    }

    @Test
    void internal_only_binding_never_registers_a_global_provider() throws Exception {
        var adapters = new ExternalCapabilityContext();
        var kind = PortKinds.ITEM_INPUT;
        var binding = kind.bindings().getFirst();
        var id = MMCR.id("test_internal_view");
        adapters.bind(binding.type(), new CapabilityBinding.ExternalExposure<>(id, String.class, (_, _, _) -> "leak"));
        var event = registrationEvent();
        ModCapabilities.registerNativePort(event, withBindings(kind, CapabilityBinding.internalOnly(
                binding.type(), binding.directions(), binding.factory(), binding.tierPolicy())), adapters);

        assertThat(event.isBlockRegistered(BlockCapability.createSided(id, String.class),
                ModBlocks.BLOCKS.get(kind.id()).get())).isFalse();
    }

    @Test
    void global_adapter_cannot_publish_the_me_interfaces_internal_inventory() throws Exception {
        var adapters = new ExternalCapabilityContext();
        var id = MMCR.id("test_unrestricted_me_items");
        var cap = BlockCapability.createSided(id, String.class);
        adapters.bind(AE2ResourceFamilies.ITEM.capabilityType(),
                new CapabilityBinding.ExternalExposure<>(id, String.class, (_, _, _) -> "unrestricted"));
        var event = registrationEvent();
        for (var bindings : List.of(AE2ResourceFamilies.inputBindings(), AE2ResourceFamilies.stockingInputBindings(),
                AE2ResourceFamilies.asyncOutputBindings(), AE2ResourceFamilies.patternBindings())) {
            var host = bindings.getFirst().directions().supports(IOType.INPUT) ? PortKinds.ITEM_INPUT : PortKinds.ITEM_OUTPUT;
            ModCapabilities.registerNativePort(event, withBindings(host, bindings.toArray(CapabilityBinding[]::new)), adapters);
            assertThat(event.isBlockRegistered(cap, ModBlocks.BLOCKS.get(host.id()).get())).isFalse();
        }
    }

    @Test
    void rejects_ambiguous_adapters_and_incompatible_local_overrides() throws Exception {
        var kind = PortKinds.ITEM_INPUT;
        var binding = kind.bindings().getFirst();
        var id = MMCR.id("conflicting_external_id");
        var exposure = new CapabilityBinding.ExternalExposure<>(id, String.class, (_, _, _) -> "adapter");
        var adapters = new ExternalCapabilityContext();
        adapters.bind(binding.type(), exposure);
        var incompatible = new CapabilityBinding(binding.type(), binding.directions(), binding.factory(),
                binding.tierPolicy(), new CapabilityBinding.ExternalExposure<>(id, Integer.class, (_, _, _) -> 1));
        var event = registrationEvent();
        assertThatIllegalArgumentException().isThrownBy(() ->
                ModCapabilities.registerNativePort(event, withBindings(kind, incompatible), adapters))
                .withMessageContaining(id.toString()).withMessageContaining(kind.id());
        adapters.bind(binding.type(), exposure);
        assertThatIllegalArgumentException().isThrownBy(() -> ModCapabilities.registerNativePort(event, kind, adapters))
                .withMessageContaining(binding.type().id().toString());
    }

    private static RegisterCapabilitiesEvent registrationEvent() throws ReflectiveOperationException {
        var constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static IOPortKind withBindings(IOPortKind kind, CapabilityBinding... bindings) {
        return new IOPortKind() {
            @Override public String id() { return kind.id(); }
            @Override public IOType ioType() { return kind.ioType(); }
            @Override public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() {
                return kind.entityFactory();
            }
            @Override public PortDefinition definition() { return PortDefinition.of(MMCR.id(id()), bindings); }
        };
    }

    @Test
    void native_resource_provider_exposes_resource_facet_without_transfer_facet() {
        ResourceOnlyCapability capability = new ResourceOnlyCapability();
        CapabilityBinding binding = new CapabilityBinding(capability.type(), CapabilityDirections.input(),
                _ -> capability, PortTierPolicy.always());
        ResourceOnlyPort port = new ResourceOnlyPort(binding, capability);
        Level level = LevelStub.createWithBlockEntities(List.of(port));
        port.setLevel(level);
        int lookupsBefore = LevelStub.capabilityLookups(level);

        assertThat(ModCapabilities.resourceStorage(port, List.of(binding), Direction.NORTH, ItemResource.class))
                .isSameAs(capability.storage());

        port.enableAutoIOAndRunCycle();
        assertThat(port.autoIOCandidateCount()).isZero();
        assertThat(LevelStub.capabilityLookups(level)).isEqualTo(lookupsBefore);
    }

    private static final class ResourceOnlyPort extends IOPortBlockEntity {
        private final IOPortKind kind;
        private final CapabilitySnapshot snapshot;

        private ResourceOnlyPort(CapabilityBinding binding, MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            kind = new IOPortKind() {
                @Override public String id() { return "resource_only_test"; }
                @Override public IOType ioType() { return IOType.INPUT; }
                @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() { return null; }
                @Override public PortDefinition definition() { return PortDefinition.of(MMCR.id("resource_only_test"), binding); }
            };
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return kind; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }

        private void enableAutoIOAndRunCycle() {
            setAutoIOEnabled(true);
            runAutoIOCycle();
        }
    }

    private static final class ResourceOnlyCapability implements MachineCapability, ResourceFacet<ItemResource> {
        private static final CapabilityType TYPE = new CapabilityType(MMCR.id("resource_only_test"));
        private final ResourceStorage<ItemResource> storage = new LongResourceStorage<>(ItemResource.class, 1, 64L,
                ItemResource::isEmpty, () -> {});

        @Override public CapabilityType type() { return TYPE; }
        @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
        @Override public Class<ItemResource> resourceType() { return ItemResource.class; }
        @Override public ResourceStorage<ItemResource> storage() { return storage; }
        @Override public CapabilityView view() {
            return new CapabilityView() {
                @Override public CapabilityType type() { return TYPE; }
                @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
                @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(ResourceFacet.class); }
            };
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            return _ -> CapabilityResult.successful();
        }
    }
}
