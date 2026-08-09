# Dynamic Model Registry Optimization

## Goal

Refactor dynamic block-model registration so a new dynamic block is added through an explicit model definition rather than inferred from its Java type or registry-name prefix. Continue incremental cleanup of the factory recipe scheduling path only where behavior-preserving reductions in duplication or branching are clear.

## Compatibility

- Keep every existing block identifier, item identifier, texture path, runtime-generated blockstate JSON, item-model JSON, and rendered result unchanged.
- Keep the existing `dynamic_controller_overlay`, `dynamic_port_overlay`, and `dynamic_machine_item` model-loader identifiers unchanged.
- Do not change save data, networking, machine definitions, or public recipe APIs.

## Dynamic Model Definitions

`RuntimeMachineModelRegistry` becomes the single source of truth for dynamic block models. It owns an ordered collection of explicit definitions. Each definition declares:

- The target block or a resolver for registered blocks.
- The existing loader kind: controller or port.
- The generated blockstate identifier and variants.
- The item-model definition requirement.
- The texture and overlay resolution needed by block and item rendering.

The current controller, I/O port, parallel controller, and factory controller registrations are represented by equivalent definitions. The registry exposes definitions to both the runtime resource pack and `DynamicOverlayItemModel`; neither consumer determines a model from naming conventions.

## Texture Resolution

Texture selection becomes part of the relevant explicit definition. Existing texture paths remain unchanged. The port-style prefix chain in `DynamicOverlayTextures` is replaced by declared overlay resolvers or constants associated with definitions. Controller textures continue to resolve from machine appearance and controller-spec caches.

The shared baked-model helpers remain responsible only for resolving base and overlay texture pairs and cache keys. They do not need to know how a block was registered.

## Resource Generation And Registration

`RuntimeMachineResourcePack` iterates the registry definitions to generate exactly the same `blockstates/<name>.json` and `items/<name>.json` resources. `Client` retains its current event wiring. Model-loader registration stays centralized and registers the two unchanged loader codecs.

Adding a dynamic block requires adding one definition at the registration boundary, plus any genuinely new textures or loader kind. It must not require editing resource-pack iteration, item description dispatch, or a global name-prefix conditional.

## Factory Scheduling Cleanup

After the model registry work, inspect the `internal.recipe` factory scheduling path and apply only local, behavior-preserving cleanup:

- Extract a helper only where it removes repeated scheduling decisions used in more than one location.
- Prefer guard clauses and direct expressions over nested branches and temporary values when readability improves.
- Retain existing scheduling semantics and test assertions.

## Validation

- Update or add focused client-model tests proving every existing dynamic block still produces the same resource identifiers, loader identifiers, variants, item-model entry, base texture, overlay texture, and overlay faces.
- Update focused scheduler tests only if extracted behavior requires coverage.
- Run `./gradlew compileJava --no-daemon` and the relevant client-model and recipe scheduler test tasks or targeted Gradle test selection supported by the project.
