package cn.howxu.mmcr.internal.assembly;

import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Resolves terminal storage selections to server-side structure item storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureItemStorageResolver {
    private StructureItemStorageResolver() {}

    public static Optional<StructureItemStorage> resolve(ServerPlayer player, TerminalData data) {
        if (data.inventoryMode() == TerminalInventoryMode.INVENTORY) {
            return Optional.of(new PlayerInventoryStructureItemStorage(player));
        }
        GlobalPos container = data.container();
        if (container == null) return Optional.empty();
        ServerLevel level = player.level().getServer().getLevel(container.dimension());
        if (level == null || !level.hasChunkAt(container.pos())) return Optional.empty();
        return BlockStructureItemStorage.at(level, container.pos()).map(storage -> storage);
    }
}
