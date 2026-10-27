MMCREvents.startup(event => {
    const builder = event.createMachine("mmcr_kubejs:water")
    builder.register()
})