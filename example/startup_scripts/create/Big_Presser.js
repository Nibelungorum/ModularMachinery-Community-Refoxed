MMCREvents.startup(event => {
    const builder = event
        .createMachine("mmcr_kubejs:big_presser")
        .appearance('create:brass_casing')
    builder.register()
})
