// Here we will register one special type, Host machine and module machine
// 参考 GTL的太空电梯

MMCREvents.startup(event => {
    const space_elevator = event
        .createMachine("mmcr_kubejs:kubejs_space_elevator")
        .displayNameKey("machine.mmcr_kubejs.kubejs_space_elevator")
        .recipeFamily("mmcr_kubejs:kubejs_space_elevator")
        .appearance('minecraft:smooth_quartz')
        .controllerBaseTexture('minecraft:block/quartz_block_bottom') // Smooth Quartz does not have standard texture, re modify it
        .formedPortBaseTexture('minecraft:block/quartz_block_bottom')
        .host("mmcr_kubejs:kubejs_space_reassembler")

    const space_reassembler = event
        .createMachine("mmcr_kubejs:kubejs_space_reassembler")
        .displayNameKey("machine.mmcr_kubejs.kubejs_space_reassembler")
        .recipeFamily("mmcr_kubejs:kubejs_space_reassembler")
        .appearance('minecraft:quartz_pillar')
        .module()


    space_elevator.register()
    space_reassembler.register()
})
