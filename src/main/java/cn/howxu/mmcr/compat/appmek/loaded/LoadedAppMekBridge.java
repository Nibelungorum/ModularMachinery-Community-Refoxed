package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeySlotFilter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalHandlerPort;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismPortSizes;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.mixin.compat.appmek.GenericStackInvAccessor;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AppMek-present additions to existing AE2 and ExtendedAE interface profiles.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedAppMekBridge implements AppMekBridge {
    private static final CapabilityType TYPE = new CapabilityType(MekanismRecipeTypes.CHEMICAL);

    @Override public boolean available() { return true; }

    @Override public List<CapabilityBinding> appendBindings(List<CapabilityBinding> base, CapabilityDirections directions, boolean transfer) {
        List<CapabilityBinding> bindings = new ArrayList<>(base);
        bindings.add(new CapabilityBinding(TYPE, directions, context -> {
            IOPortBlockEntity host = (IOPortBlockEntity) context.host();
            CapabilityDirections effective = host instanceof PatternInterfaceBlockEntity ? CapabilityDirections.output() : directions;
            return new MEChemicalCapability(host, MEChemicalHandlers.forHost(host), effective, transfer);
        }, (binding, tier) -> true, transfer));
        return List.copyOf(bindings);
    }

    @Override public List<PortFamilyDescriptor> appendFamilies(List<PortFamilyDescriptor> base) {
        List<PortFamilyDescriptor> families = new ArrayList<>(base);
        int tier = PortTiers.ItemTier.NORMAL.ordinal() + MekanismPortSizes.ChemicalTier.ULTIMATE.ordinal();
        for (IOType direction : IOType.values()) {
            if (base.stream().anyMatch(family -> family.ioType() == direction)) {
                families.add(new PortFamilyDescriptor(MekanismRecipeTypes.CHEMICAL, direction, tier,
                        List.of(direction == IOType.INPUT ? "chemical_input_hatch" : "chemical_output_hatch")));
                families.add(new PortFamilyDescriptor(MekanismPortFamilies.RADIOACTIVE_CHEMICAL, direction, tier,
                        List.of(direction == IOType.INPUT ? "radioactive_chemical_input_hatch" : "radioactive_chemical_output_hatch")));
            }
        }
        return List.copyOf(families);
    }

    @Override public void configureInventory(Object inventory) {
        GenericStackInv target = (GenericStackInv) inventory;
        AEKeySlotFilter original = target.getFilter();
        ((GenericStackInvAccessor) target).mmcr$setFilter((slot, key) ->
                ValidatedGenericInventory.accepted(key) && (original == null || original.isAllowed(slot, key)));
    }

    @Override public Object inventoryView(Object inventory) { return new ValidatedGenericInventory((GenericInternalInventory) inventory); }

    @Override public Object storageView(Object storage) {
        if (storage == null) return null;
        MEStorage delegate = (MEStorage) storage;
        return new MEStorage() {
            @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) { return delegate.isPreferredStorageFor(key, source); }
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return ValidatedGenericInventory.accepted(key) ? delegate.insert(key, amount, mode, source) : 0L;
            }
            @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) { return delegate.extract(key, amount, mode, source); }
            @Override public void getAvailableStacks(KeyCounter out) { delegate.getAvailableStacks(out); }
            @Override public Component getDescription() { return delegate.getDescription(); }
        };
    }

    @Override public boolean supportsPatternInput(Object key) { return key instanceof MekanismKey chemical && ValidatedGenericInventory.accepted(chemical); }
    @Override public Optional<MachineOutput> patternOutput(Object key, long amount) {
        return amount > 0L && supportsPatternInput(key)
                ? Optional.of(new LoadedChemicalOutput(((MekanismKey) key).getId(), amount, 1F)) : Optional.empty();
    }

    @Override public PatternRequest patternRequest(Object holders) {
        GenericStackInv request = AE2NativeAdapters.requestInventory((KeyCounter[]) holders, MekanismKeyType.TYPE);
        MEChemicalCapability capability = new MEChemicalCapability(null, new InventoryChemicalHandler(request), CapabilityDirections.input(), false);
        return new PatternRequest() {
            @Override public List<MachineCapability> capabilities() { return List.of(capability); }
            @Override public boolean returnRemaining(Object inventory, Object source) {
                return AE2NativeAdapters.returnRemaining(request, (GenericStackInv) inventory, MekanismKeyType.TYPE, (IActionSource) source);
            }
        };
    }

    @Override public long flush(IOPortBlockEntity host, long budget) {
        if (!(host instanceof OutputInterfaceBlockEntity output)) return 0L;
        return AE2NativeAdapters.flush(output.getStorage(), output::networkStorage,
                IActionSource.ofMachine(output), MekanismKeyType.TYPE, budget);
    }

    @Override public void registerCapabilities(RegisterCapabilitiesEvent event) {
        PortKinds.all().stream().filter(kind -> kind.modDependencies().contains("ae2")).forEach(kind -> {
            CapabilityBinding binding = kind.bindings().stream().filter(value -> value.type().equals(TYPE))
                    .filter(CapabilityBinding::nativeTransferExposure).findFirst().orElse(null);
            if (binding == null) return;
            event.registerBlockEntity(Capabilities.CHEMICAL.block(), ModBlockEntities.BES.get(kind.id()).get(), (be, side) -> {
                if (!(be instanceof IOPortBlockEntity port) || !(port.capability(TYPE) instanceof ChemicalHandlerPort chemical)) return null;
                IChemicalHandler handler = chemical.chemicalHandler();
                boolean exposed = port.isNativeSideExposed(binding, side);
                boolean input = chemical.directions().supports(IOType.INPUT);
                return new IChemicalHandler() {
                    @Override public int getChemicalTanks() { return handler.getChemicalTanks(); }
                    @Override public ChemicalStack getChemicalInTank(int tank) { return exposed ? handler.getChemicalInTank(tank).copy() : ChemicalStack.EMPTY; }
                    @Override public void setChemicalInTank(int tank, ChemicalStack stack) { throw new UnsupportedOperationException(); }
                    @Override public long getChemicalTankCapacity(int tank) { return exposed ? handler.getChemicalTankCapacity(tank) : 0L; }
                    @Override public boolean isValid(int tank, ChemicalStack stack) { return exposed && input && handler.isValid(tank, stack); }
                    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
                        return exposed && input ? handler.insertChemical(tank, stack, action) : stack.copy();
                    }
                    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) {
                        return exposed && !input ? handler.extractChemical(tank, amount, action) : ChemicalStack.EMPTY;
                    }
                };
            });
        });
    }
}
