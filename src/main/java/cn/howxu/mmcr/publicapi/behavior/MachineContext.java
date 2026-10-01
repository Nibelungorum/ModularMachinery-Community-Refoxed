package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.presentation.ControllerText;
import cn.howxu.mmcr.publicapi.presentation.JadeText;
import cn.howxu.mmcr.publicapi.runtime.IoSnapshot;
import cn.howxu.mmcr.publicapi.runtime.MachineView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Server-authoritative MMCR callback context; consumers must not implement it.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface MachineContext {
    @Nullable MachineView controller();
    ServerLevel level();
    BlockPos controllerPos();
    @Nullable ResourceLocation machineId();
    long gameTime();
    boolean isDue(long period);
    ControllerText screenText();
    @Nullable DataStore dataStorage();
    IoSnapshot ioView();
    /** Returns copied stacks. */
    List<ItemStack> upgradeItems();
    JadeText jadeText();
    long countStructureBlocks(Block block);
    long countStructureBlocks(String blockId);
}
