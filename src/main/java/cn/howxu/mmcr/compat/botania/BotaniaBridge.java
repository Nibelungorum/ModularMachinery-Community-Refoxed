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

/** Neutral entry point for optional native mana pools. @author howxu <dev@howxu.cn> */
public interface BotaniaBridge {
    static BotaniaBridge get() { return BotaniaBridgeBootstrap.get(); }

    boolean available();
    List<IOPortKind> portKinds();
    IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind);
    Block createBlock(IOPortKind kind, BlockBehaviour.Properties properties,
                      Supplier<? extends BlockEntityType<?>> type);
    MachineCapability createCapability(CapabilityCreationContext context);
    void registerCapabilities(RegisterCapabilitiesEvent event);
    void tick(IOPortBlockEntity port);
}
