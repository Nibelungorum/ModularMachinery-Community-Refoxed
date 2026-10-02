package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauBridge;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortKind;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.common.items.DominionWand;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Ars-present implementation; construction does not access MMCR content registries.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedArsNouveauBridge implements ArsNouveauBridge {
    private final List<IOPortKind> kinds = List.of(
            new SourcePortKind(ArsSourceIds.INPUT, IOType.INPUT),
            new SourcePortKind(ArsSourceIds.OUTPUT, IOType.OUTPUT));

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<IOPortKind> portKinds() {
        return kinds;
    }

    @Override
    public IOPortBlockEntity createPort(BlockPos pos, BlockState state, IOPortKind kind) {
        return new SourcePortBlockEntity(pos, state, kind);
    }

    @Override
    public MachineCapability createCapability(CapabilityCreationContext context) {
        if (context.host() instanceof SourcePortBlockEntity port) {
            return new SourcePortCapability(port, port.storage(), port.ioType());
        }
        throw new IllegalArgumentException("Ars source capability requires a source port");
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (IOPortKind kind : kinds) {
            event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY, ModBlockEntities.BES.get(kind.id()).get(),
                    (entity, side) -> ((SourcePortBlockEntity) entity).externalHandler());
        }
    }

    @Override
    public void registerMenus(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
        registrar.accept(ArsSourceIds.MENU, () -> IMenuTypeExtension.create(SourcePortMenu::clientOpen));
    }

    @Override
    public AbstractContainerMenu createMenu(String id, int containerId, Inventory inventory, Level level, BlockPos pos) {
        if (!isSourcePort(id)) return null;
        return new SourcePortMenu(containerId, inventory,
                level.getBlockEntity(pos) instanceof SourcePortBlockEntity port ? port : null);
    }

    @Override
    public boolean isSourcePort(String id) {
        return ArsSourceIds.INPUT.equals(id) || ArsSourceIds.OUTPUT.equals(id);
    }

    @Override
    public boolean isDominionWand(ItemStack stack) {
        return stack.getItem() instanceof DominionWand;
    }

    @Override
    public ItemStack sourceIcon() {
        return new ItemStack(ItemsRegistry.SOURCE_GEM.get());
    }
}
