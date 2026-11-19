package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityFactory;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 一种 IO 端口类型的协议。新增一种 IO 端口(气体、魔源等)
 * 只需实现本接口并通过 {@link cn.howxu.mmcr.registry.PortKinds#register} 注册。
 */
public interface IOPortKind {

    /** 该 kind 的字符串 id,出现在 block 注册名里,如 "item"/"fluid"/"energy"/"gas"/"mana"。 */
    String id();

    IOType ioType();

    /** 该 kind 对应的 BlockEntity 工厂。Block 注册时由这里创建对应实体。 */
    BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory();

    default Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return new IOPortBlock(this, type, properties);
    }

    /** Returns the immutable capability binding declaration for this port kind. */
    PortDefinition definition();

    default List<CapabilityBinding> bindings() {
        return definition().bindings();
    }

    static CapabilityBinding binding(CapabilityType type, IOType ioType, List<PortFamilyDescriptor> families) {
        return new CapabilityBinding(type, CapabilityDirections.of(ioType), capabilityFactory(type), (binding, tier) -> families.stream()
                .filter(family -> family.matches(binding))
                .findFirst()
                .map(family -> tier >= family.detectionTier())
                .orElse(true));
    }

    private static CapabilityFactory capabilityFactory(CapabilityType type) {
        return context -> {
            CapabilityDefinition definition = CapabilityRegistry.get(type);
            if (definition == null) {
                throw new IllegalStateException("Capability is not registered: " + type.id());
            }
            return definition.factory().create(context);
        };
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

    default List<Identifier> overlayTextures() { return List.of(); }

    default List<Component> tooltipLines() { return List.of(); }

    default @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Level level, BlockPos pos) { return null; }
    default void writeOpenData(FriendlyByteBuf buffer, BlockPos pos) { buffer.writeBlockPos(pos); }
    default void writeOpenData(FriendlyByteBuf buffer, BlockPos pos, AbstractContainerMenu menu) {
        writeOpenData(buffer, pos);
    }
    default InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) { return InteractionResult.PASS; }
    default int interactWithContainer(ItemAccess access, BlockEntity owner, Player player, TransactionContext tx) {
        return owner instanceof IOPortBlockEntity port ? interactWithContainer(access, port, tx) : 0;
    }
    default boolean handlesContainerInteraction() { return false; }
    default int interactWithContainer(ItemAccess access, IOPortBlockEntity port, TransactionContext tx) { return 0; }

    default boolean availableForStructure() { return true; }

    default int outputPriority() {
        return families().stream().mapToInt(PortFamilyDescriptor::detectionTier).max().orElse(0);
    }

    /** 该 kind 的服务端 tick 钩子,用于 MEK 气体管道分发等。默认无。 */
    default void tick(IOPortBlockEntity be) {}

    /** 该 kind 所依赖的外部 mod 列表。空表示无外部依赖。用于数据生成阶段为 LootTable 等数据添加 neoforge:conditions。 */
    default List<String> modDependencies() { return List.of(); }
}
