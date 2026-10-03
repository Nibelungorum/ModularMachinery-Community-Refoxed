package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import sonar.fluxnetworks.api.FluxDataComponents;

import java.util.List;

/** @author howxu <dev@howxu.cn> */
public final class LoadedFluxNetworksBridge implements FluxNetworksBridge {
    @Override public boolean available() { return true; }

    @Override
    public List<IOPortKind> portKinds() {
        return List.of(FluxNetworkInterfaceKind.INPUT, FluxNetworkInterfaceKind.OUTPUT);
    }

    @Override
    public Item createBlockItem(Block block, Item.Properties properties) {
        return new FluxNetworkInterfaceItem(block, properties);
    }

    @Override
    public LootTable.Builder deviceLoot(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .when(ExplosionCondition.survivesExplosion())
                .add(LootItem.lootTableItem(block).apply(CopyComponentsFunction.copyComponents(
                         CopyComponentsFunction.Source.BLOCK_ENTITY)
                        .include(FluxDataComponents.FLUX_CONFIG).include(FluxDataComponents.STORED_ENERGY)
                        .include(DataComponents.CUSTOM_DATA))));
    }
}
