// Use the level system and a shared structure.

MMCREvents.startup(event => {

    // Register the machine that uses levels.
    const builder = event
        .createMachine("mmcr_kubejs:kubejs_thermal_smelting_furnace")
        .displayNameKey("machine.mmcr_kubejs.kubejs_thermal_smelting_furnace")
        .recipeFamily("mmcr_kubejs:kubejs_thermal_smelting_furnace")
        .allowParallelism()
        .allowMultithreading() // Allow use multi threading process
        .maxParallelAmount(32)
        .appearance("minecraft:smooth_basalt")

    builder.register()

    // Register the level type before registering individual levels.
    // Recipes and structures only need to reference the registered level.
    event
        .createLevelType("mmcr_kubejs:thermal_smelting_coil") // Level type identifier.
        .displayNameKey('level.mmcr_kubejs.thermal_smelting_coil') // Translation key for the level type name.
        .register() // Register the level type.

    // Register an individual level.
    event.createLevel("mmcr_kubejs:thermal_smelting_coil_iron") // Level identifier.
        .type("mmcr_kubejs:thermal_smelting_coil") // Parent level type.
        .priority(0) // Priority; iron is usually the lowest tier.
        .state('minecraft:iron_block') // Block identifier representing the level.
        .modifier({ // A level can provide modifiers, just like a single-block modifier.
            durationMultiplier: 0.95,
            energyMultiplier: 0.95,
            parallelismBonus: 4
        })
        .register() // Register the level.

    event.createLevel("mmcr_kubejs:thermal_smelting_coil_gold")
        .type("mmcr_kubejs:thermal_smelting_coil")
        .priority(1)
        .state('minecraft:gold_block')
        .modifier({
            durationMultiplier: 0.85,
            energyMultiplier: 0.85,
            parallelismBonus: 6
        })
        .register()

    event.createLevel("mmcr_kubejs:thermal_smelting_coil_diamond")
        .type("mmcr_kubejs:thermal_smelting_coil")
        .priority(2)
        .state('minecraft:diamond_block')
        .modifier({
            durationMultiplier: 0.75,
            energyMultiplier: 0.75,
            parallelismBonus: 6
        })
        .register()

    // See server_scripts/structure/A_Level_Machine.js for the level structure.
})
