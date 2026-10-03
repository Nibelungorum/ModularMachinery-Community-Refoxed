package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.item.InterfaceBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.List;

/** Optional Flux Networks entry point; signatures contain no native Flux Networks classes.
 * @author howxu <dev@howxu.cn>
 */
public interface FluxNetworksBridge {
    static FluxNetworksBridge get() {
        return FluxNetworksBridgeBootstrap.bridge();
    }

    boolean available();

    List<IOPortKind> portKinds();

    default Item createBlockItem(Block block, Item.Properties properties) {
        return new InterfaceBlockItem(block, properties);
    }

    LootTable.Builder deviceLoot(Block block);
}
