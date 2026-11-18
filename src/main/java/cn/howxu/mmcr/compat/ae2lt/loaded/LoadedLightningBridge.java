package cn.howxu.mmcr.compat.ae2lt.loaded;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.Set;

/** @author howxu <dev@howxu.cn> */
public final class LoadedLightningBridge implements LightningBridge {
    public static final AE2ResourceFamily<LightningTier> FAMILY = new AE2ResourceFamily<>(
            new CapabilityType(LightningRecipeTypes.LIGHTNING), new AE2KeyAdapter<>() {
                @Override
                public AEKeyType keyType() {
                    return LightningKeyType.INSTANCE;
                }

                @Override public MachineOutput patternOutput(LightningTier tier, long amount) {
                    return amount > maxPatternTotal() ? null : new LightningOutput(tier, amount, 1F);
                }
                @Override public LightningTier patternResource(MachineOutput output) {
                    return output instanceof LightningOutput lightning ? lightning.tier() : null;
                }
                @Override public Predicate<LightningTier> patternInput(MachineRequirement requirement) {
                    return requirement instanceof LightningRequirement r && r.io().isInput()
                            ? tier -> r.tier() == tier : null;
                }
                @Override public long maxPatternTotal() { return Integer.MAX_VALUE; }
                @Override
                public Class<LightningTier> resourceType() {
                    return LightningTier.class;
                }

                @Override
                public AEKey toKey(LightningTier resource) {
                    return switch (resource) {
                        case HIGH_VOLTAGE -> LightningKey.HIGH_VOLTAGE;
                        case EXTREME_HIGH_VOLTAGE -> LightningKey.EXTREME_HIGH_VOLTAGE;
                    };
                }

                @Override
                public Optional<LightningTier> toResource(AEKey key) {
                    if (!(key instanceof LightningKey)) return Optional.empty();
                    if (LightningKey.HIGH_VOLTAGE.equals(key)) return Optional.of(LightningTier.HIGH_VOLTAGE);
                    if (LightningKey.EXTREME_HIGH_VOLTAGE.equals(key)) return Optional.of(LightningTier.EXTREME_HIGH_VOLTAGE);
                    return Optional.empty();
                }
            },
            new PortFamilyDescriptor(LightningRecipeTypes.LIGHTNING, IOType.INPUT, 0, List.of()),
            new PortFamilyDescriptor(LightningRecipeTypes.LIGHTNING, IOType.OUTPUT, 0, List.of()),
            LightningResourceStorage::new, LightningNetworkResourceStorage::new,
            (host, storage, direction, _) -> new LightningCapability(host, storage, direction));

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<AE2ResourceFamily<?>> resourceFamilies() {
        return List.of(FAMILY);
    }

    @Override
    public void registerCapabilityDefinitions() {
        CapabilityRegistry.register(new CapabilityDefinition(FAMILY.capabilityType(),
                Set.of(ResourceFacet.class, OperationFacet.class), context -> {
                    if (!(context.host() instanceof IOPortBlockEntity host)) {
                        throw new IllegalArgumentException("Lightning capability requires an ME interface host");
                    }
                    var storage = switch (host) {
                        case InputInterfaceBlockEntity input -> input.resourceStorage(FAMILY);
                        case StockingInterfaceBlockEntity stocking -> stocking.resourceStorage(FAMILY);
                        case OutputInterfaceBlockEntity output -> output.resourceStorage(FAMILY);
                        case AsyncOutputInterfaceBlockEntity async -> async.resourceStorage(FAMILY);
                        case PatternInterfaceBlockEntity pattern -> pattern.resourceStorage(FAMILY);
                        default -> throw new IllegalArgumentException("Lightning capability requires an ME interface host");
                    };
                    return new LightningCapability(host, storage, context.ioType());
                }));
    }
}
