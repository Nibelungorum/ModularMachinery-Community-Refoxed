/**
 * Public startup-registration API inventory for MMCR integrations.
 *
 * <p>This package is the API JAR boundary root. Public machine startup follows
 * three ordered phases: definitions, structures, and recipes. Each event owns
 * its registration window and exposes immutable results after freezing.</p>
 *
 * <h2>Planned ABI allow-list</h2>
 * <ul>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.MachineApi}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.RecipeApi}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.ApiRegistrationException}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.MachineDefinition}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.MachineBuilder}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.PatternBuilder}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.PatternDefinition}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.BlockPredicate}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.ControllerSpec}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.AppearanceSpec}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.PortRequirements}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.PortTiers}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.StructureStage}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.StructureRequirements}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.FactorySpec}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.machine.LevelRequirement}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.ItemInput}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.FluidInput}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.EnergyInput}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.ItemOutput}</li>
 *     <li>{@code cn.howxu.mmcr.api.publicapi.recipe.FluidOutput}</li>
 * </ul>
 *
 * <ul>
 *     <li>Pattern layers and symbol bindings map to {@code PatternBuilder.layer(...)} and
 *     {@code PatternBuilder.where(...)}.</li>
 *     <li>Block, tag, any-of, port-family, controller, and machine-coupler predicates map to
 *     {@code BlockPredicate} factory methods.</li>
 *     <li>Single pattern and multi-stage structures map to
 *     {@code MachineStructureBuilder} with {@code StructureStage} values.</li>
 *     <li>Controller face flags and textures map to {@code MachineBuilder.controller(...)} and
 *     {@code ControllerSpec}.</li>
 *     <li>Basic-block appearance overrides map to {@code MachineBuilder.appearance(...)} and
 *     {@code AppearanceSpec}.</li>
 *     <li>Basic port requirements, tier/category rules, level slots, and single-block modifier
 *     replacements belong to {@code MachineStructureBuilder} and {@code StructureStage}.</li>
 *     <li>Parallelism, factory flags, thread limits, and thread values map to
 *     {@code MachineBuilder.maxParallelism(...)}, {@code MachineBuilder.parallelizable(...)},
 *     and {@code MachineBuilder.factory(...)} with {@code FactorySpec}.</li>
 *     <li>Normal, host, and module machines map to {@code MachineBuilder.role(...)} and
 *     accepted-module methods on {@code MachineBuilder}.</li>
 * </ul>
 *
 * <ul>
 *     <li>Recipe and machine ids map to {@code MachineRecipeBuilder.recipe(...)}.</li>
 *     <li>Tick duration, priority, max threads, cancellation, parallelized execution, and
 *     partial-output behavior map to scalar methods on {@code MachineRecipeBuilder}.</li>
 *     <li>Item inputs, tag inputs, component predicates, and consume chance map to
 *     {@code ItemInput} values and item input methods on {@code MachineRecipeBuilder}.</li>
 *     <li>Fluid inputs and outputs map to {@code FluidInput}, {@code FluidOutput}, and matching
 *     builder methods.</li>
 *     <li>Energy input and output map to {@code EnergyInput} and matching builder methods.</li>
 *     <li>Item outputs, output components, and output chance map to {@code ItemOutput} values
 *     and output methods.</li>
 *     <li>Explicit item, fluid, energy, smart-interface, level, and required-host requirements
 *     map to public requirement/value methods on {@code MachineRecipeBuilder}.</li>
 *     <li>Recipe modifiers map to public modifier values accepted by {@code MachineRecipeBuilder}.</li>
 * </ul>
 *
 * <h2>Public signature rules</h2>
 * <ul>
 *     <li>Public signatures must not expose {@code MachineRegistry}, {@code RecipeRegistry},
 *     {@code CompiledMachinePattern}, {@code BlockArrayCache}, or
 *     {@code DynamicContentReloadService}.</li>
 *     <li>Public signatures must not expose {@code internal.*}, client-only types,
 *     compiled pattern/cache/reload implementation, or mutable implementation collections.</li>
 *     <li>Public signatures must represent startup registration only.</li>
 * </ul>
 *
 * @author howxu &lt;dev@howxu.cn&gt;
 */
package cn.howxu.mmcr.api.publicapi;
