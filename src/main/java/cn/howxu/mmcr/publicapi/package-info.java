/**
 * Java-facing MMCR declarations, registration events and consumption views.
 *
 * <p>Factories provide Public drafts, specs and views. Internal typed adapters delegate
 * validation and execution to the authoritative implementation in {@code cn.howxu.mmcr.api}.
 * Consumers compose declarations entirely through Public contracts.</p>
 *
 * <p>Machine registration uses the definitions, structures and recipes events in
 * that order. Structure events delegate to the same core registration collector used
 * directly by KubeJS. The core and KubeJS do not depend on this package.</p>
 *
 * <p>Use {@link cn.howxu.mmcr.publicapi.Machines#machine},
 * {@link cn.howxu.mmcr.publicapi.Structures#structure} and
 * {@link cn.howxu.mmcr.publicapi.Recipes#recipe} for declarations. Requirement, IO and
 * component factories live in {@code recipe.requirement.Requirements},
 * {@code recipe.IoValues} and {@code recipe.component.ComponentConditions}.
 * Registration uses {@code event.RegisterMachineDefinitionsEvent},
 * {@code event.RegisterMachineStructuresEvent} and {@code event.RegisterMachineRecipesEvent}
 * with their Public registrars. Providers implement
 * {@code registration.MachineDefinitionProvider}.</p>
 *
 * @author howxu &lt;dev@howxu.cn&gt;
 */
package cn.howxu.mmcr.publicapi;
