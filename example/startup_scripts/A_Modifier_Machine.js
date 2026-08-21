// We will use some advanced features of MMCR in this Machine

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:kubejs_alloy_furnace") // First, create your machine definition
        .displayNameKey("machine.mmcr_kubejs.kubejs_alloy_furnace") //
        .recipeFamily("mmcr_kubejs:kubejs_alloy_furnace") //
        .appearance('minecraft:bricks') // This will change the identifier as the basic texture of your controller block
        .allowModifiers() // This will allow you to use modifiers

    builder.register()


    // Move to server_scripts/structure/A_Modifier_Machine.js see how to define a structure for your machine
})