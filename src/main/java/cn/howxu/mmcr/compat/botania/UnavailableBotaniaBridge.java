package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.function.Supplier;

/** Inert implementation with no native class linkage. @author howxu <dev@howxu.cn> */
final class UnavailableBotaniaBridge implements BotaniaBridge {
    static final UnavailableBotaniaBridge INSTANCE = new UnavailableBotaniaBridge();

    private UnavailableBotaniaBridge() {}

    @Override public boolean available() { return false; }
    @Override public List<IOPortKind> portKinds() { return List.of(); }

    @Override
    public IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind) {
        throw new IllegalStateException("Botania is unavailable; cannot create a mana port");
    }

    @Override
    public Block createBlock(IOPortKind kind, BlockBehaviour.Properties properties,
                             Supplier<? extends BlockEntityType<?>> type) {
        throw new IllegalStateException("Botania is unavailable; cannot create a mana pool block");
    }

    @Override
    public MachineCapability createCapability(CapabilityCreationContext context) {
        throw new IllegalStateException("Botania is unavailable; cannot create a mana capability");
    }

    @Override public void registerCapabilities(RegisterCapabilitiesEvent event) {}
    @Override public void tick(IOPortBlockEntity port) {}
}
