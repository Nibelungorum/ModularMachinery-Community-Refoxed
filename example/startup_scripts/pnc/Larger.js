MMCREvents.startup(event => {
    const builder = event
        .createMachine("mmcr_kubejs:larger")
        .appearance('pneumaticcraft:pressure_chamber_wall')
    builder.register()
})
