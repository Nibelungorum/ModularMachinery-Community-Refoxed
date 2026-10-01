package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.ItemInput;
import cn.howxu.mmcr.api.recipe.ItemOutput;
import cn.howxu.mmcr.api.recipe.FluidInput;
import cn.howxu.mmcr.api.recipe.FluidOutput;
import cn.howxu.mmcr.api.recipe.EnergyInput;
import cn.howxu.mmcr.api.recipe.RequiredHost;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Identity-preserving declaration adapters. @author howxu <dev@howxu.cn> */
public final class RecipeIoAdapters {
    private RecipeIoAdapters() {}
    public static ItemInputSpec wrap(ItemInput v) { return new ItemIn(v); }
    public static ItemOutputSpec wrap(ItemOutput v) { return new ItemOut(v); }
    public static FluidInputSpec wrap(FluidInput v) { return new FluidIn(v); }
    public static FluidOutputSpec wrap(FluidOutput v) { return new FluidOut(v); }
    public static EnergyRateSpec wrap(EnergyInput v) { return new Energy(v); }
    public static HostConstraint wrap(RequiredHost v) { return new Host(v); }
    public static CustomIoSpec wrap(CustomRecipeIo v) { return new Custom(v); }
    public static ItemInputSpec input(ItemRequirement v) { return new CanonicalItemIn(v); }
    public static ItemOutputSpec output(ItemRequirement v) { return new CanonicalItemOut(v); }
    public static FluidInputSpec input(FluidRequirement v) { return new CanonicalFluidIn(v); }
    public static FluidOutputSpec output(FluidRequirement v) { return new CanonicalFluidOut(v); }
    public static EnergyRateSpec wrap(EnergyRequirement v) { return new CanonicalEnergy(v); }
    public static ItemInput unwrap(ItemInputSpec v) { if (v instanceof ItemIn view) return view.delegate; if (v instanceof CanonicalItemIn view) return new ItemInput(view.ingredient(), view.count(), view.delegate.components(), view.consumeChance()); throw foreign(); }
    public static ItemOutput unwrap(ItemOutputSpec v) { if (v instanceof ItemOut view) return view.delegate; if (v instanceof CanonicalItemOut view) return new ItemOutput(view.stack(), view.chance(), view.delegate.components()); throw foreign(); }
    public static FluidInput unwrap(FluidInputSpec v) { if (v instanceof FluidIn view) return view.delegate; if (v instanceof CanonicalFluidIn view) return new FluidInput(view.ingredient(), view.amount(), view.consumeChance()); throw foreign(); }
    public static FluidOutput unwrap(FluidOutputSpec v) { if (v instanceof FluidOut view) return view.delegate; if (v instanceof CanonicalFluidOut view) return new FluidOutput(view.stack(), view.chance()); throw foreign(); }
    public static EnergyInput unwrap(EnergyRateSpec v) { if (v instanceof Energy view) return view.delegate; if (v instanceof CanonicalEnergy view) return new EnergyInput(view.fePerTick()); throw foreign(); }
    public static RequiredHost unwrap(HostConstraint v) { if (v instanceof Host view) return view.delegate; throw foreign(); }
    public static CustomRecipeIo unwrap(CustomIoSpec v) { if (v instanceof Custom view) return view.delegate; throw foreign(); }
    private static IllegalArgumentException foreign() { return new IllegalArgumentException("IO declaration must be library-produced"); }
    public static ItemInputSpec itemInput(Item item, int count) { return wrap(new ItemInput(item, count)); }
    public static ItemInputSpec itemInput(Ingredient item, int count) { return wrap(new ItemInput(item, count)); }
    public static ItemInputSpec itemInput(Ingredient item, int count, ComponentConstraints components, float consumeChance) { return wrap(new ItemInput(item, count, ComponentAdapters.unwrap(components), consumeChance)); }
    public static ItemOutputSpec itemOutput(Item item, int count) { return wrap(new ItemOutput(item, count)); }
    public static ItemOutputSpec itemOutput(ItemStack stack) { return wrap(new ItemOutput(stack)); }
    public static ItemOutputSpec itemOutput(ItemStack stack, float chance) { return wrap(new ItemOutput(stack, chance)); }
    public static ItemOutputSpec itemOutput(ItemStack stack, ComponentConstraints components) { return wrap(new ItemOutput(stack, ComponentAdapters.unwrap(components))); }
    public static ItemOutputSpec itemOutput(ItemStack stack, float chance, ComponentConstraints components) { return wrap(new ItemOutput(stack, chance, ComponentAdapters.unwrap(components))); }
    public static FluidInputSpec fluidInput(Fluid fluid, int amount) { return wrap(new FluidInput(fluid, amount)); }
    public static FluidInputSpec fluidInput(FluidIngredient fluid, int amount) { return wrap(new FluidInput(fluid, amount)); }
    public static FluidInputSpec fluidInput(FluidIngredient fluid, int amount, float consumeChance) { return wrap(new FluidInput(fluid, amount, consumeChance)); }
    public static FluidOutputSpec fluidOutput(Fluid fluid, int amount) { return wrap(new FluidOutput(fluid, amount)); }
    public static FluidOutputSpec fluidOutput(FluidStack stack) { return wrap(new FluidOutput(stack)); }
    public static FluidOutputSpec fluidOutput(FluidStack stack, float chance) { return wrap(new FluidOutput(stack, chance)); }
    public static EnergyRateSpec energyRate(long fePerTick) { return wrap(new EnergyInput(fePerTick)); }
    public static HostConstraint requiredHost(ResourceLocation id) { return wrap(new RequiredHost(id)); }
    public static CustomIoSpec customIo(ResourceLocation typeId, IoDirection io, JsonElement payload) { return wrap(RecipeIoValidation.custom(typeId, ModifierAdapters.io(io), payload)); }
    private record ItemIn(ItemInput delegate) implements ItemInputSpec {
        public Ingredient ingredient() { return delegate.ingredient(); }
        public int count() { return delegate.count(); }
        public ComponentConstraints components() { return ComponentAdapters.wrap(delegate.components()); }
        public float consumeChance() { return delegate.consumeChance(); }
    }
    private record CanonicalItemIn(ItemRequirement delegate) implements ItemInputSpec {
        public Ingredient ingredient() { return delegate.item(); }
        public int count() { return delegate.count(); }
        public ComponentConstraints components() { return ComponentAdapters.wrap(delegate.components()); }
        public float consumeChance() { return delegate.consumeChance(); }
    }
    private record CanonicalItemOut(ItemRequirement delegate) implements ItemOutputSpec {
        public ItemStack stack() { return delegate.stack().copy(); }
        public float chance() { return delegate.chance(); }
        public ComponentConstraints components() { return ComponentAdapters.wrap(delegate.components()); }
    }
    private record CanonicalFluidIn(FluidRequirement delegate) implements FluidInputSpec {
        public FluidIngredient ingredient() { return delegate.fluid(); }
        public int amount() { return delegate.amount(); }
        public float consumeChance() { return delegate.consumeChance(); }
    }
    private record CanonicalFluidOut(FluidRequirement delegate) implements FluidOutputSpec {
        public FluidStack stack() { return delegate.stack().copy(); }
        public float chance() { return delegate.chance(); }
    }
    private record CanonicalEnergy(EnergyRequirement delegate) implements EnergyRateSpec {
        public long fePerTick() { return delegate.fePerTick(); }
    }
    private record ItemOut(ItemOutput delegate) implements ItemOutputSpec {
        public ItemStack stack() { return delegate.stack(); }
        public float chance() { return delegate.chance(); }
        public ComponentConstraints components() { return ComponentAdapters.wrap(delegate.components()); }
    }
    private record FluidIn(FluidInput delegate) implements FluidInputSpec {
        public FluidIngredient ingredient() { return delegate.ingredient(); }
        public int amount() { return delegate.amount(); }
        public float consumeChance() { return delegate.consumeChance(); }
    }
    private record FluidOut(FluidOutput delegate) implements FluidOutputSpec {
        public FluidStack stack() { return delegate.stack(); }
        public float chance() { return delegate.chance(); }
    }
    private record Energy(EnergyInput delegate) implements EnergyRateSpec { public long fePerTick() { return delegate.fePerTick(); } }
    private record Host(RequiredHost delegate) implements HostConstraint { public ResourceLocation id() { return delegate.id(); } }
    private record Custom(CustomRecipeIo delegate) implements CustomIoSpec {
        public ResourceLocation typeId() { return delegate.typeId(); }
        public IoDirection io() { return ModifierAdapters.io(delegate.ioType()); }
        public JsonElement payload() { return delegate.payload(); }
    }
}
