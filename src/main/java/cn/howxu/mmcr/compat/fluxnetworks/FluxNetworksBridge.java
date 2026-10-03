package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.internal.port.IOPortKind;
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

    LootTable.Builder deviceLoot(Block block);
}
