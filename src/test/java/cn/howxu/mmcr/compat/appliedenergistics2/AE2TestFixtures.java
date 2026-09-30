package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlockEntities;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Shared registry fixtures for AE2 compatibility tests.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2TestFixtures {
    private AE2TestFixtures() {
    }

    public static void ensureAE2KeyTypesInitialized() {
        boolean initialized;
        try {
            initialized = !AEKeyTypes.getAll().isEmpty();
        } catch (IllegalStateException ignored) {
            initialized = false;
        }
        if (initialized) return;

        MappedRegistry<AEKeyType> registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        registry.freeze();
    }

    @SuppressWarnings("unchecked")
    public static void bindAE2InterfaceItem() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ae2", "interface");
        MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze();
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id, new Item(new Item.Properties()));
            }
        } finally {
            registry.freeze();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void bindEntityType(String kind,
                                                               BlockEntityType.BlockEntitySupplier<? extends T> factory) {
        ResourceLocation id = MMCR.id(kind);
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze();
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id,
                        BlockEntityType.Builder.of(factory, Blocks.IRON_BLOCK).build(null));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(kind, DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
    }
}
