MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:inner_ctm")
        .appearance("mmcr:basic_casing")

    builder.register()
})