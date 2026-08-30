MMCREvents.startup(event => {
    const machine = event
        .createMachine("mmcr_kubejs:kubejs_data_storage_machine")
        .displayNameKey("machine.mmcr_kubejs.kubejs_data_storage_machine")
        .recipeFamily("mmcr_kubejs:kubejs_data_storage_machine")
        // .appearance("minecraft:green_terracotta");

    const api = MMCR.getAPI()

    machine
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {

            })
        )

})
