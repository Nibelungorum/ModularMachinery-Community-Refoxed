package dev.latvian.mods.kubejs.registry;

import net.minecraft.resources.ResourceLocation;

public abstract class BuilderBase<T> {
    protected final ResourceLocation id;

    protected BuilderBase(ResourceLocation id) {
        this.id = id;
    }

    public abstract T createObject();
}
