package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.List;

/** Absent-mod bridge that contributes no optional ports.
 * @author howxu <dev@howxu.cn>
 */
final class UnavailableFluxNetworksBridge implements FluxNetworksBridge {
    static final UnavailableFluxNetworksBridge INSTANCE = new UnavailableFluxNetworksBridge();

    private UnavailableFluxNetworksBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<IOPortKind> portKinds() {
        return List.of();
    }

    @Override
    public LootTable.Builder deviceLoot(Block block) {
        throw new IllegalStateException("Flux Networks is not loaded");
    }
}
