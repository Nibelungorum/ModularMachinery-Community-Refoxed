package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.function.Supplier;

/** Fixed-direction declaration independent of native implementation. @author howxu <dev@howxu.cn> */
public record ManaPortKind(String id, IOType ioType) implements IOPortKind {
    @Override
    public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
        return (pos, state) -> BotaniaBridge.get().createPort(pos, state, this);
    }

    @Override
    public Block createBlock(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        return BotaniaBridge.get().createBlock(this, properties, type);
    }

    @Override
    public List<PortFamilyDescriptor> families() {
        return List.of(new PortFamilyDescriptor(BotaniaManaIds.MANA, ioType, 0,
                List.of(ioType == IOType.INPUT ? "mana_input_pool" : "mana_output_pool")));
    }

    @Override
    public PortDefinition definition() {
        return PortDefinition.of(MMCR.id(id), new CapabilityBinding(BotaniaManaIds.TYPE,
                CapabilityDirections.of(ioType), context -> BotaniaBridge.get().createCapability(context),
                (binding, tier) -> tier >= 0));
    }

    @Override public List<String> modDependencies() { return List.of("botania"); }
    @Override public void tick(IOPortBlockEntity port) { BotaniaBridge.get().tick(port); }
}
