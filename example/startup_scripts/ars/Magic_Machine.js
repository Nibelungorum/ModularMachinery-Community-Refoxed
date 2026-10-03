MMCREvents.startup(event => {
    const builder = event
        .createMachine("mmcr_kubejs:magic")
        .appearance('ars_nouveau:sourcestone')
    builder.register()
})
