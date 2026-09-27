// Register a host machine and a module machine.
// This example is based on GTL's space elevator.

MMCREvents.startup(event => {
    const space_elevator = event
        .createMachine("mmcr_kubejs:kubejs_space_elevator")
        .displayNameKey("machine.mmcr_kubejs.kubejs_space_elevator")
        .recipePool("mmcr_kubejs:kubejs_space_elevator")
        .appearance('minecraft:smooth_quartz')
        .controllerBaseTexture('minecraft:block/quartz_block_bottom') // Smooth quartz has no standard texture, so set the base texture manually.
        .formedPortBaseTexture('minecraft:block/quartz_block_bottom')
        .host("mmcr_kubejs:kubejs_space_reassembler")

    // It can host two kinds of recipe pool if you like
    const space_reassembler = event
        .createMachine("mmcr_kubejs:kubejs_space_reassembler")
        .displayNameKey("machine.mmcr_kubejs.kubejs_space_reassembler")
        .recipePool("mmcr_kubejs:kubejs_space_reassembler", "mmcr_kubejs:kubejs_space_miner", "mmcr_kubejs:kubejs_space_1", "mmcr_kubejs:kubejs_space_2", "mmcr_kubejs:kubejs_space_3")
        .appearance('minecraft:quartz_pillar')
        .allowMultithreading()
        .module()


    space_elevator.register()
    space_reassembler.register()
})
