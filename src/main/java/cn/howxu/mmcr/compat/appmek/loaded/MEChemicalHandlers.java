package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.IChemicalHandler;

/**
 * Selects the native chemical projection for an existing ME host.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MEChemicalHandlers {
    private MEChemicalHandlers() {}

    public static IChemicalHandler forHost(IOPortBlockEntity host) {
        IActionSource source = IActionSource.ofMachine((IActionHost) host);
        if (host instanceof StockingInterfaceBlockEntity stocking) {
            stocking.getStorage().setCapacity(MekanismKeyType.TYPE, Long.MAX_VALUE);
            return new NetworkChemicalHandler(stocking::networkStorage, stocking::configuredKeys, source,
                    () -> stocking.getLevel() != null && stocking.getLevel().isClientSide());
        }
        if (host instanceof OutputInterfaceBlockEntity output) {
            return new OutputChemicalHandler(output.getStorage(), output::networkStorage, source, output::onStorageChanged);
        }
        if (host instanceof AsyncOutputInterfaceBlockEntity output) {
            return new OutputChemicalHandler(output.getStorage(), output::networkStorage, source, output::saveChanges);
        }
        if (host instanceof InputInterfaceBlockEntity input) return new InventoryChemicalHandler(input.getStorage());
        if (host instanceof PatternInterfaceBlockEntity pattern) return new InventoryChemicalHandler(pattern.getLogic().getReturnInv());
        throw new IllegalArgumentException("Unsupported ME chemical host");
    }
}
