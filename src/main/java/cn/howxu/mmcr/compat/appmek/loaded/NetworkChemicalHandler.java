package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Configured, read-only chemical projection of a live ME network and its client mirror.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkChemicalHandler implements IChemicalHandler, NativeReservationAccess {
    private final Supplier<@Nullable MEStorage> storage;
    private final Supplier<List<AEKey>> configuredKeys;
    private final IActionSource source;
    private final BooleanSupplier client;
    private final Map<MekanismKey, Long> mirror = new HashMap<>();

    public NetworkChemicalHandler(Supplier<@Nullable MEStorage> storage, Supplier<List<AEKey>> configuredKeys,
                                  IActionSource source) {
        this(storage, configuredKeys, source, () -> false);
    }

    public NetworkChemicalHandler(Supplier<@Nullable MEStorage> storage, Supplier<List<AEKey>> configuredKeys,
                                  IActionSource source, BooleanSupplier client) {
        this.storage = storage;
        this.configuredKeys = configuredKeys;
        this.source = source;
        this.client = client;
    }

    private List<MekanismKey> keys() {
        return configuredKeys.get().stream().filter(MekanismKey.class::isInstance).map(MekanismKey.class::cast)
                .filter(key -> ChemicalAttributeValidator.DEFAULT.process(key.getStack())).distinct().toList();
    }

    @Override public int getChemicalTanks() { return keys().size(); }

    @Override public ChemicalStack getChemicalInTank(int tank) {
        List<MekanismKey> keys = keys();
        if (tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
        if (client.getAsBoolean()) return keys.get(tank).withAmount(mirror.getOrDefault(keys.get(tank), 0L));
        MEStorage network = storage.get();
        return network == null ? ChemicalStack.EMPTY
                : keys.get(tank).withAmount(network.extract(keys.get(tank), Long.MAX_VALUE, Actionable.SIMULATE, source));
    }

    @Override public long getChemicalTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, ChemicalStack stack) { return false; }
    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) { return stack.copy(); }

    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (client.getAsBoolean() || amount <= 0L) return ChemicalStack.EMPTY;
        MEStorage network = storage.get();
        if (network == null) return ChemicalStack.EMPTY;
        List<MekanismKey> keys = keys();
        if (tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
        MekanismKey key = keys.get(tank);
        return key.withAmount(network.extract(key, amount,
                action == Action.SIMULATE ? Actionable.SIMULATE : Actionable.MODULATE, source));
    }

    public void prepareSync(int slots) {
        if (!client.getAsBoolean()) throw new IllegalStateException("Network mirror synchronization is client-only");
        if (slots != keys().size()) throw new IllegalArgumentException("Chemical sync configuration mismatch");
        mirror.clear();
    }

    @Override public void setChemicalInTank(int tank, ChemicalStack stack) {
        if (!client.getAsBoolean()) throw new IllegalStateException("Cannot replace live network contents");
        MekanismKey key = keys().get(tank);
        if (!stack.isEmpty() && !key.equals(MekanismKey.of(stack))) {
            throw new IllegalArgumentException("Chemical sync key does not match configuration");
        }
        if (stack.isEmpty()) mirror.remove(key);
        else mirror.put(key, stack.getAmount());
    }

    @Override public Object reservationIdentity() {
        if (client.getAsBoolean()) return mirror;
        MEStorage network = storage.get();
        return network == null ? this : network;
    }
    @Override public Object reservationSlot(int slot) { return keys().get(slot); }
    @Override public Object resourceKey(Object resource) { return MekanismKey.of((ChemicalStack) resource); }
    @Override public Object resource(Object key) { return key instanceof MekanismKey chemical ? chemical.withAmount(1L) : ChemicalStack.EMPTY; }
    @Override public Object storedKey(int slot) { return keys().get(slot); }
    @Override public long storedAmount(int slot) { return getChemicalInTank(slot).getAmount(); }
}
