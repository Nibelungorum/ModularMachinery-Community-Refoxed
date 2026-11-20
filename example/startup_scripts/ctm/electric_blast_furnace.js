StartupEvents.registry('block', event => {
    event.create('heat_proof_machine_casing')
        .displayName('隔热殷钢机械方块')

    event.create('machine_coil_cupronickel')
        .property(BlockProperties.LIT)
        .defaultState(s => s.set(BlockProperties.LIT, false))
        .tagBlock(['mmcr:status_block'])
        .displayName('白铜线圈方块')
})

MMCREvents.startup(event => {

    const builder = event
        .createMachine("mmcr_kubejs:electric_blast_furnace")
        .appearance("kubejs:heat_proof_machine_casing")
        .displayNameKey("工业高炉")

    builder.register()
})
