package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

/**
 * A port kind that supplies its AE2 interface logic profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface InterfaceLogicKind extends AE2PortKind {
    @Override default MenuType<?> menuType() { return AE2MenuTypes.INTERFACE; }

    default InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, Item icon) {
        return new InterfaceLogic(node, host, icon);
    }
}
