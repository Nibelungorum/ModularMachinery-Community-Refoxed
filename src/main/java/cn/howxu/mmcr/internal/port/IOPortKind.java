package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityFactory;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.Optional;

/**
 * 一种 IO 端口类型的协议。新增一种 IO 端口(气体、魔源等)
 * 只需实现本接口并通过 {@link cn.howxu.mmcr.registry.PortKinds#register} 注册。
 */
public interface IOPortKind {

    /** 该 kind 的字符串 id,出现在 block 注册名里,如 "item"/"fluid"/"energy"/"gas"/"mana"。 */
    String id();

    IOType ioType();

    /** 该 kind 对应的 BlockEntity 工厂。Block 注册时由这里创建对应实体。 */
    BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory();

    /**
     * Legacy capability type view retained for existing port consumers.
     * New kinds may provide only {@link #definition()} and inherit this adapter.
     */
    default List<CapabilityType> capabilityTypes() {
        return bindings().stream().map(CapabilityBinding::type).toList();
    }

    /**
     * Returns the immutable binding view for this legacy port kind.
     *
     * <p>The default adapter keeps existing kind implementations source-compatible while
     * making capability factories and tier policies data-driven.</p>
     */
    default PortDefinition definition() {
        List<CapabilityBinding> bindings = capabilityTypes().stream()
                .map(type -> new CapabilityBinding(type, ioType(), capabilityFactory(type), tierPolicy(type)))
                .toList();
        return PortDefinition.of(MMCR.id(id()), bindings);
    }

    default List<CapabilityBinding> bindings() {
        return definition().bindings();
    }

    private CapabilityFactory capabilityFactory(CapabilityType type) {
        return context -> {
            CapabilityDefinition definition = CapabilityRegistry.get(type);
            if (definition == null) {
                throw new IllegalStateException("Capability is not registered: " + type.id());
            }
            return definition.factory().create(context);
        };
    }

    private PortTierPolicy tierPolicy(CapabilityType type) {
        return (binding, tier) -> families().stream()
                .filter(family -> family.matches(binding) && family.familyId().equals(type.id()))
                .findFirst()
                .map(family -> tier >= family.detectionTier())
                .orElse(true);
    }

    default Optional<ItemBusSize> itemBusSize() { return Optional.empty(); }

    default Optional<FluidHatchSize> fluidHatchSize() { return Optional.empty(); }

    default Optional<EnergyHatchSize> energyHatchSize() { return Optional.empty(); }

    default Optional<ExtendedItemBusSize> extendedItemBusSize() { return Optional.empty(); }

    default Optional<ExtendedFluidHatchSize> extendedFluidHatchSize() { return Optional.empty(); }

    default Optional<ExtendedEnergyHatchSize> extendedEnergyHatchSize() { return Optional.empty(); }

    default Optional<CombinedPortSize> combinedPortSize() { return Optional.empty(); }

    default Optional<ExtendedCombinedPortSize> extendedCombinedPortSize() { return Optional.empty(); }

    default List<PortFamilyDescriptor> families() { return List.of(); }

    /** 该 kind 的服务端 tick 钩子,用于 MEK 气体管道分发等。默认无。 */
    default void tick(IOPortBlockEntity be) {}
}
