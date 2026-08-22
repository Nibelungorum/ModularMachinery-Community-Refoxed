// Use the smart interface system to control machine parameters.

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_purpur_furnace")
        .displayNameKey("machine.mmcr_kubejs.kubejs_purpur_furnace")
        .recipeFamily("mmcr_kubejs:kubejs_purpur_furnace")
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
        // After end(), machine parameters can be modified according to the interface value.
        .energyByInterface('kubejs_mode', 1, 2, 1.0, 2.0)
        .energyByInterface('kubejs_mode', 2, 3, 2.0, 4.0)
        .durationByInterface('kubejs_conversation', 0, 0.5, 1.0, 1.5)
        .durationByInterface('kubejs_conversation', 0.5, 1, 1.5, 2.5)

    builder.register()

    // See server_scripts/structure/A_Smart_Machine.js for the structure definition.
})
