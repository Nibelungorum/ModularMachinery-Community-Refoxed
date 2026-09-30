package cn.howxu.mmcr.compat.extendedae.loaded;

import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.AEKeySlotFilter;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import com.glodblock.github.extendedae.config.EAEConfig;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import net.minecraft.world.item.Item;

import java.util.Set;

/** Creates the EAE oversize 36-slot interface inventory profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class OversizeInterfaceLogicFactory {
    private OversizeInterfaceLogicFactory() {}

    public static InterfaceLogic create(IManagedGridNode node, InterfaceLogicHost host, Item icon) {
        InterfaceLogic logic = new InterfaceLogic(node, host, icon, 36);
        Ae2Reflect.setInterfaceConfig(logic, new OversizeConfigInv(AEKeyTypes.getAll(), null,
                GenericStackInv.Mode.CONFIG_STACKS, 36, () -> Ae2Reflect.onInterfaceConfigChange(logic), false));
        Ae2Reflect.setInterfaceStorage(logic, new OversizeConfigInv(AEKeyTypes.getAll(),
                (slot, key) -> Ae2Reflect.isInterfaceSlotAllowed(logic, slot, key),
                GenericStackInv.Mode.STORAGE, 36, () -> Ae2Reflect.onInterfaceStorageChange(logic), false));
        logic.getConfig().useRegisteredCapacities();
        logic.getStorage().useRegisteredCapacities();
        return logic;
    }

    private static final class OversizeConfigInv extends ConfigInventory {
        private OversizeConfigInv(Set<AEKeyType> keyTypes, AEKeySlotFilter filter,
                                  GenericStackInv.Mode mode, int size, Runnable listener, boolean allowOverstacking) {
            super(keyTypes, filter, mode, size, listener, allowOverstacking);
        }

        @Override
        public long getMaxAmount(AEKey key) {
            try {
                return Math.multiplyExact(super.getMaxAmount(key), EAEConfig.getOversizeMultiplier(key));
            } catch (Exception exception) {
                return Long.MAX_VALUE;
            }
        }
    }
}
