// Here we will introduce the smart interface system

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_purpur_furnace")
        .displayNameKey("machine.mmcr_kubejs.kubejs_purpur_furnace")
        .recipeFamily("mmcr_kubejs:kubejs_purpur_furnace")
        .allowParallelism()
        .maxParallelAmount(32)
        .appearance('minecraft:end_stone_bricks')
        .runningSound('minecraft:block.furnace.fire_crackle') // this will register a repeat sound during machine running
        .finishSound('minecraft:entity.ender_dragon.growl') // this will register a sound when recipe finish
        // use smartInterface to register a smartInterface usage
        // pay attention, one smart interface only work with one machine
        // register_name: mmcr.smart_interface.type.register_name will automatically be its lang key, and one description will automatically added
        // min value
        // max value
        // priority: when recipe have some conflicts, it will work, bigger value meaning higher priority
        // valueType: the input type
        // end: MUST, end the call
        .smartInterface('kubejs_mode', 1, 3).priority(1).valueType('integer').end()
        .smartInterface('kubejs_conversation', 0, 1).priority(0).valueType('float').end()
        // after end, you can set interface modifier
        // interfaceType, min, max, atMin, atMax, between will have a linear change
        .energyByInterface('kubejs_mode', 1, 2, 1.0, 2.0)
        .energyByInterface('kubejs_mode', 2, 3, 2.0, 4.0)
        .durationByInterface('kubejs_conversation', 0, 0.5, 1.0, 1.5)
        .durationByInterface('kubejs_conversation', 0.5, 1, 1.5, 2.5)

    builder.register()

    // Move to server_scripts/structure/A_Smart_Machine.js see how to use level for your machine
})