package cn.howxu.mmcr.compat.botania.loaded;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.compat.botania.BotaniaBridge;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.ManaPortCapability;
import cn.howxu.mmcr.compat.botania.ManaPortKind;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

import java.util.List;
import java.util.function.Supplier;

/** Native bridge; construction does not touch content registries. @author howxu <dev@howxu.cn> */
public final class LoadedBotaniaBridge implements BotaniaBridge {
    private final List<IOPortKind> kinds = List.of(
            new ManaPortKind(BotaniaManaIds.INPUT, IOType.INPUT),
            new ManaPortKind(BotaniaManaIds.OUTPUT, IOType.OUTPUT));

    @Override public boolean available() { return true; }
    @Override public List<IOPortKind> portKinds() { return kinds; }

    @Override
    public IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind) {
        return new ManaPortBlockEntity(pos, state, kind);
    }

    @Override
    public Block createBlock(IOPortKind kind, BlockBehaviour.Properties properties,
                             Supplier<? extends BlockEntityType<?>> type) {
        return new ManaPortBlock(kind, properties, type);
    }

    @Override
    public MachineCapability createCapability(CapabilityCreationContext context) {
        if (context.host() instanceof ManaPortBlockEntity port) {
            return new ManaPortCapability(port, port.storage(), port.ioType());
        }
        throw new IllegalArgumentException("Botania mana capability requires a mana port");
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (IOPortKind kind : kinds) {
            BlockEntityType<?> type = ModBlockEntities.BES.get(kind.id()).get();
            event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), type,
                    (entity, side) -> ((ManaPortBlockEntity) entity).externalHandler());
            event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP), type,
                    (entity, context) -> (ManaPortBlockEntity) entity);
            event.registerBlockEntity(BotaniaNeoForgeCapabilities.getBlockApiLookupById(Wandable.LOOKUP), type,
                    (entity, side) -> (ManaPortBlockEntity) entity);
        }
    }

    @Override
    public void tick(IOPortBlockEntity port) {
        if (port instanceof ManaPortBlockEntity pool) pool.tickMana();
    }
}
