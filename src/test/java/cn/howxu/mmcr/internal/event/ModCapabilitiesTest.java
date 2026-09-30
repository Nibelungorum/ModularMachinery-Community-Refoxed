package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    void selects_external_exposures_from_generic_port_bindings() {
        CapabilityBinding binding = new CapabilityBinding(new CapabilityType(MMCR.id("external_test")),CapabilityDirections.input(),context -> null, PortTierPolicy.always(),
                new CapabilityBinding.ExternalExposure<>(MMCR.id("external_test_native"), String.class,
                        (blockEntity, side, context) -> "exposed"));
        PortDefinition definition = PortDefinition.of(MMCR.id("external_test_port"), binding);
        IOPortKind kind = new IOPortKind() {
            @Override
            public String id() {
                return "external_test_port";
            }

            @Override
            public IOType ioType() {
                return IOType.INPUT;
            }

            @Override
            public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return (position, state) -> null;
            }

            @Override
            public PortDefinition definition() {
                return definition;
            }
        };

        assertThat(ModCapabilities.externalBindings(kind)).containsExactly(binding);
    }

    @Test
    void internal_only_binding_is_not_selected_for_native_transfer_registration() {
        CapabilityBinding binding = CapabilityBinding.internalOnly(
                new CapabilityType(MMCR.id("internal_only_test")), CapabilityDirections.input(), context -> null,
                PortTierPolicy.always());
        PortDefinition definition = PortDefinition.of(MMCR.id("internal_only_test_port"), binding);
        IOPortKind kind = new IOPortKind() {
            @Override public String id() { return "internal_only_test_port"; }
            @Override public IOType ioType() { return IOType.INPUT; }
            @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return (position, state) -> null;
            }
            @Override public PortDefinition definition() { return definition; }
        };

        assertThat(ModCapabilities.nativeTransferBindings(kind, Set.of())).isEmpty();
    }

    @Test
    void native_provider_exposes_typed_handler_without_transfer_facet() {
        ResourceOnlyCapability capability = new ResourceOnlyCapability();
        CapabilityBinding binding = new CapabilityBinding(capability.type(), CapabilityDirections.input(),
                context -> capability, PortTierPolicy.always());
        ResourceOnlyPort port = new ResourceOnlyPort(binding, capability);
        Level level = LevelStub.createWithBlockEntities(List.of(port));
        port.setLevel(level);
        int lookupsBefore = LevelStub.capabilityLookups(level);

        assertThat(ModCapabilities.itemHandler(port, List.of(binding), Direction.NORTH))
                .isSameAs(capability.itemHandler());

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

    private static final class ResourceOnlyCapability implements MachineCapability, ItemHandlerFacet {
        private static final CapabilityType TYPE = new CapabilityType(MMCR.id("resource_only_test"));
        private final LongItemStorage storage = new LongItemStorage(1, 64L, () -> {});

        @Override public CapabilityType type() { return TYPE; }
        @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
        @Override public LongItemStorage itemHandler() { return storage; }
        @Override public CapabilityView view() {
            return new CapabilityView() {
                @Override public CapabilityType type() { return TYPE; }
                @Override public CapabilityDirections directions() { return CapabilityDirections.input(); }
                @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(ItemHandlerFacet.class); }
            };
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }
    }
}
