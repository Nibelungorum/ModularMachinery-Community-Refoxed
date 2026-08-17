package dev.latvian.mods.kubejs.registry;

import net.minecraft.resources.Identifier;

public abstract class BuilderBase<T> {
    protected final Identifier id;

    protected BuilderBase(Identifier id) {
        this.id = id;
    }

    public abstract T createObject();
}
