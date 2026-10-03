package cn.howxu.mmcr.compat.extendedae_plus.loaded;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.ConfigManager;
import appeng.util.inv.AppEngInternalInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity.MasterLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** @author howxu <dev@howxu.cn> */
public final class MirrorPatternInterfaceLogic extends PatternProviderLogic {
    private static final String MASTER_TAG = "mmcr_mirror_master";
    private static final int FAST_SYNC_INTERVAL = 2;
    private static final int STABLE_SYNC_INTERVAL = 20;
    private static final int UNAVAILABLE_RETRY_INTERVAL = 40;
    private static final long UNKNOWN_PATTERN_VERSION = Long.MIN_VALUE;
    private static final InternalInventory DISABLED_PATTERN_INVENTORY = new AppEngInternalInventory(0);

    private final PatternProviderLogicHost host;
    private final AppEngInternalInventory actualPatternInventory;
    private final Map<Setting<?>, Enum<?>> defaultSettings = new LinkedHashMap<>();
    private final int defaultPriority;
    private @Nullable MasterLocation master;
    private @Nullable PatternInterfaceBlockEntity cachedSource;
    private long cachedVersion = UNKNOWN_PATTERN_VERSION;
    private int cachedSize = -1;
    private int cachedUnlockedSlots = -1;
    private long nextSyncTick = Long.MIN_VALUE;
    private boolean updatingPatterns;

    public MirrorPatternInterfaceLogic(IManagedGridNode node, PatternProviderLogicHost host) {
        super(node, host, 36);
        this.host = host;
        actualPatternInventory = (AppEngInternalInventory) super.getPatternInv();
        IConfigManager manager = getConfigManager();
        if (manager instanceof ConfigManager config && !manager.hasSetting(EAPSettings.ADVANCED_BLOCKING)) {
            config.registerSetting(EAPSettings.ADVANCED_BLOCKING, YesNo.NO);
        }
        for (Setting<?> setting : manager.getSettings()) defaultSettings.put(setting, manager.getSetting(setting));
        defaultPriority = getPriority();
    }

    public boolean bindToMaster(MasterLocation target) {
        BlockEntity mirror = host.getBlockEntity();
        if (!(mirror.getLevel() instanceof ServerLevel level) || target.side() != null
                || target.dimension().equals(level.dimension()) && target.pos().equals(mirror.getBlockPos())) {
            return false;
        }
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        PatternInterfaceBlockEntity source = null;
        if (targetLevel != null && targetLevel.hasChunkAt(target.pos())) {
            source = validSource(targetLevel.getBlockEntity(target.pos()));
            if (source == null) return false;
        }

        boolean changed = !Objects.equals(master, target);
        master = target;
        resetSyncState();
        if (source != null) changed |= syncFromMaster(source);
        if (changed) saveChanges();
        nextSyncTick = level.getGameTime() + (source == null ? UNAVAILABLE_RETRY_INTERVAL : FAST_SYNC_INTERVAL);
        return true;
    }

    public boolean unbindFromMaster() {
        if (!(host.getBlockEntity().getLevel() instanceof ServerLevel) || !hasMasterBinding()) return false;
        master = null;
        resetSyncState();
        clearMirroredPatterns();
        resetMirroredSettings();
        saveChanges();
        return true;
    }

    public boolean hasMasterBinding() {
        return master != null;
    }

    public Component createBoundMessage() {
        return masterMessage("bound");
    }

    public Component createUnboundMessage() {
        return Component.translatable("extendedae_plus.message.mirror_pattern_provider.unbound");
    }

    public Component getStatusMessage() {
        return masterMessage("following");
    }

    private Component masterMessage(String status) {
        if (master == null) {
            return Component.translatable("extendedae_plus.message.mirror_pattern_provider.missing_master");
        }
        BlockPos pos = master.pos();
        return Component.translatable("extendedae_plus.message.mirror_pattern_provider." + status,
                pos.getX(), pos.getY(), pos.getZ()).append(Component.translatable(
                "message.mmcr.mirror_pattern_interface.master_dimension", master.dimension().location().toString()));
    }

    public void serverTick(ServerLevel level) {
        if (master == null || level.getGameTime() < nextSyncTick) return;
        ServerLevel targetLevel = level.getServer().getLevel(master.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(master.pos())) {
            resetSyncState();
            nextSyncTick = level.getGameTime() + UNAVAILABLE_RETRY_INTERVAL;
            return;
        }
        PatternInterfaceBlockEntity source = validSource(targetLevel.getBlockEntity(master.pos()));
        if (source == null) {
            unbindFromMaster();
            return;
        }
        boolean changed = syncFromMaster(source);
        if (changed) saveChanges();
        nextSyncTick = level.getGameTime() + (changed ? FAST_SYNC_INTERVAL : STABLE_SYNC_INTERVAL);
    }

    private @Nullable PatternInterfaceBlockEntity validSource(@Nullable BlockEntity candidate) {
        if (candidate instanceof PatternInterfaceBlockEntity source && source != host.getBlockEntity()
                && !source.isRemoved() && (source.kind() == PatternInterfaceKind.INSTANCE
                || source.kind() == ExtendedPatternInterfaceKind.INSTANCE)
                && !(source.getLogic() instanceof MirrorPatternInterfaceLogic)) {
            return source;
        }
        return null;
    }

    private boolean syncFromMaster(PatternInterfaceBlockEntity source) {
        PatternProviderLogic sourceLogic = source.getLogic();
        boolean changed = syncSharedSettings(sourceLogic.getConfigManager());
        if (getPriority() != sourceLogic.getPriority()) {
            setPriority(sourceLogic.getPriority());
            changed = true;
        }

        InternalInventory inventory = sourceLogic.getPatternInv();
        long version = sourceLogic instanceof PatternProviderLogicSyncBridge bridge
                ? bridge.eap$getPatternSyncVersion() : UNKNOWN_PATTERN_VERSION;
        int size = inventory.size();
        int unlockedSlots = sourceLogic instanceof PatternProviderPageUnlockBridge bridge
                ? bridge.eap$getUnlockedPatternSlots() : size;
        if (cachedSource == source && version != UNKNOWN_PATTERN_VERSION && cachedVersion == version
                && cachedSize == size && cachedUnlockedSlots == unlockedSlots) return changed;

        boolean patternsChanged = false;
        updatingPatterns = true;
        try {
            for (int slot = 0; slot < actualPatternInventory.size(); slot++) {
                ItemStack desired = slot < size && slot < unlockedSlots ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
                if (!ItemStack.matches(actualPatternInventory.getStackInSlot(slot), desired)) {
                    actualPatternInventory.setItemDirect(slot, desired.copy());
                    patternsChanged = true;
                }
            }
        } finally {
            updatingPatterns = false;
        }
        if (patternsChanged || cachedSource != source) updatePatterns();
        cachedSource = source;
        cachedVersion = version;
        cachedSize = size;
        cachedUnlockedSlots = unlockedSlots;
        return changed || patternsChanged;
    }

    private boolean syncSharedSettings(IConfigManager source) {
        IConfigManager target = getConfigManager();
        boolean changed = syncSetting(source, target, EAPSettings.ADVANCED_BLOCKING);
        for (Setting<?> setting : source.getSettings()) {
            if (setting != EAPSettings.ADVANCED_BLOCKING) changed |= syncSetting(source, target, setting);
        }
        return changed;
    }

    private static <T extends Enum<T>> boolean syncSetting(IConfigManager source, IConfigManager target, Setting<T> setting) {
        if (!source.hasSetting(setting) || !target.hasSetting(setting)) return false;
        T value = source.getSetting(setting);
        if (value == target.getSetting(setting)) return false;
        target.putSetting(setting, value);
        return true;
    }

    private void resetMirroredSettings() {
        IConfigManager manager = getConfigManager();
        for (var entry : defaultSettings.entrySet()) {
            if (manager.getSetting(entry.getKey()) != entry.getValue()) {
                putDefaultSetting(manager, entry.getKey(), entry.getValue());
            }
        }
        if (getPriority() != defaultPriority) setPriority(defaultPriority);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putDefaultSetting(IConfigManager manager, Setting setting, Enum value) {
        manager.putSetting(setting, value);
    }

    private void clearMirroredPatterns() {
        actualPatternInventory.fromItemContainerContents(ItemContainerContents.EMPTY);
        updatePatterns();
    }

    private void resetSyncState() {
        cachedSource = null;
        cachedVersion = UNKNOWN_PATTERN_VERSION;
        cachedSize = -1;
        cachedUnlockedSlots = -1;
        nextSyncTick = Long.MIN_VALUE;
    }

    @Override
    public InternalInventory getPatternInv() {
        return DISABLED_PATTERN_INVENTORY;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (!updatingPatterns) super.onChangeInventory(inventory, slot);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        if (!updatingPatterns) super.saveChangedInventory(inventory);
    }

    @Override
    public void importSettings(DataComponentMap input, @Nullable Player player) {
    }

    @Override
    public void exportSettings(DataComponentMap.Builder builder) {
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        if (master == null) {
            tag.remove(MASTER_TAG);
        } else {
            CompoundTag binding = new CompoundTag();
            binding.putString("dimension", master.dimension().location().toString());
            binding.putLong("pos", master.pos().asLong());
            tag.put(MASTER_TAG, binding);
        }
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        actualPatternInventory.fromItemContainerContents(ItemContainerContents.EMPTY);
        super.readFromNBT(tag, registries);
        master = null;
        resetSyncState();
        if (tag.contains(MASTER_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag binding = tag.getCompound(MASTER_TAG);
            if (binding.contains("dimension", Tag.TAG_STRING) && binding.contains("pos", Tag.TAG_LONG)) {
                master = new MasterLocation(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(binding.getString("dimension"))), BlockPos.of(binding.getLong("pos")), null);
            }
        }
        if (master == null) {
            clearMirroredPatterns();
            resetMirroredSettings();
        }
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        ItemContainerContents patterns = actualPatternInventory.toItemContainerContents();
        actualPatternInventory.fromItemContainerContents(ItemContainerContents.EMPTY);
        try {
            super.addDrops(drops);
        } finally {
            actualPatternInventory.fromItemContainerContents(patterns);
        }
    }
}
