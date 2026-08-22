// Here we will use some advanced features
// and build one bigger machine
MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_reactor") // First, create your machine definition
        .displayNameKey("machine.mmcr_kubejs.kubejs_reactor") //
        .recipeFamily("mmcr_kubejs:kubejs_reactor") //
        .appearance("minecraft:blue_ice") //

    builder.register()
    // Move to server_scripts/structure/A_BlockState_Machine.js see how to define a block state structure for your machine
})