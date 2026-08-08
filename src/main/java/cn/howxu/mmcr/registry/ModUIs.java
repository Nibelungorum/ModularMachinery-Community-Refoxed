package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 每个 UI 类别注册一个 {@link MenuType};服务端经由 {@link net.minecraft.world.MenuProvider} 注入 BE 引用,客户端通过工厂创建无 BE 实例,槽位数据由数据包同步。 */
public final class ModUIs {

    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, MMCR.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ItemBusMenu>> ITEM_BUS =
            REGISTER.register("item_bus", () -> new MenuType<>((IContainerFactory<ItemBusMenu>) ItemBusMenu::clientOpen, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<FluidHatchMenu>> FLUID_HATCH =
            REGISTER.register("fluid_hatch", () -> new MenuType<>((IContainerFactory<FluidHatchMenu>) FluidHatchMenu::clientOpen, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<EnergyHatchMenu>> ENERGY_HATCH =
            REGISTER.register("energy_hatch", () -> new MenuType<>((IContainerFactory<EnergyHatchMenu>) EnergyHatchMenu::clientOpen, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineControllerMenu>> MACHINE_CONTROLLER =
            REGISTER.register("machine_controller", () -> new MenuType<>((IContainerFactory<MachineControllerMenu>) MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModUIs() {}
}
