MMCREvents.startup(event => {
    const builder = event
        .createMachine("mmcr_kubejs:gateway")
        .appearance('botania:elven_gateway_core')
    builder.register()
})
