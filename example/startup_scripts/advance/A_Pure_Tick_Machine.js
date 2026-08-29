MMCREvents.startup(event => {
    const machine = event
        .createMachine("mmcr_kubejs:kubejs_pure_tick_machine")
        .displayNameKey("machine.mmcr_kubejs.kubejs_pure_tick_machine")
        .recipeFamily("mmcr_kubejs:kubejs_pure_tick_machine");

    machine
        // Here pure tick machine also allowed to use multi thread block and parallelism controller
        // But they do not have any actual usage, only become one type of numbers you can use in your code
        .allowMultithreading()
        .allowParallelism()
        .maxParallelAmount(2147483647)

    machine.register()
})
