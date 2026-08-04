package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * 调试扳手。右键 IO 端口在聊天栏打印内部储量,
 * 实际逻辑见 {@link cn.howxu.mmcr.internal.event.WrenchDebugHandler}。
 *
 * @author howxu <dev@howxu.cn>
 */
public class WrenchItem extends Item {

    public WrenchItem() {
        super(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("wrench"))));
    }
}