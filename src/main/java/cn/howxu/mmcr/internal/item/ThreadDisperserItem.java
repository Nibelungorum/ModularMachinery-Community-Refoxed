package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Thread capacity item for factory schedulers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ThreadDisperserItem extends Item {

    public ThreadDisperserItem() {
        super(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, MMCR.id("thread_disperser"))));
    }
}
