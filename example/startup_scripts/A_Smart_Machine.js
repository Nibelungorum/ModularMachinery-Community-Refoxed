// Use the smart interface system to control machine parameters.

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_purpur_furnace")
        .displayNameKey("machine.mmcr_kubejs.kubejs_purpur_furnace")
        .recipePool("mmcr_kubejs:kubejs_purpur_furnace")
        .allowParallelism()
        .maxParallelAmount(32)
        .appearance('minecraft:end_stone_bricks')
        .runningSound('minecraft:block.furnace.fire_crackle') // Sound played repeatedly while the machine is running.
        .finishSound('minecraft:entity.ender_dragon.growl') // Sound played when a recipe finishes.
        // Register a smart interface. Each interface can be used by only one machine.
        // register_name generates the mmcr.smart_interface.type.register_name translation key and a description.
        // The arguments define the minimum, maximum, priority, and value type; end() finishes the interface definition.
        .smartInterface('kubejs_mode', 1, 3).priority(1).valueType('integer').end()
        .smartInterface('kubejs_conversation', 0, 1).priority(0).valueType('float').end()
        // Functions receive the actual interface value and return a modifier number.
        // MULTIPLY is the default operation. No endpoint mapping or automatic clamping is applied.
        // Mode 1/2/3 uses energy multipliers 1/2/4.
        .energyByInterface('kubejs_mode', value => Math.pow(2, value - 1))
        // Piecewise duration multipliers: 0 -> 1, 0.5 -> 1.5, 1 -> 2.5.
        .durationByInterface('kubejs_conversation', value => {
            if (value <= 0.5) return 1 + value
            return 0.5 + 2 * value
        })
        // Functions must return a finite number and depend only on the input value.
        // They run when modifiers refresh, not necessarily every tick.

    builder.register()

    // See server_scripts/structure/A_Smart_Machine.js for the structure definition.
})
