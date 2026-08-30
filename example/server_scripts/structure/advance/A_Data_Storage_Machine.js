MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_data_storage_machine")

    structure
        .pattern("         ", "         ", "         ", "   AAA   ", "   ABA   ", "   AAA   ", "         ", "         ", "         ")
        .pattern("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
        .pattern("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
        .pattern("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
        .pattern("   ABA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "BXXXDXXXB", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   ABA   ")
        .pattern("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
        .pattern("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
        .pattern("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
        .pattern("         ", "         ", "         ", "   AAA   ", "   ACA   ", "   AAA   ", "         ", "         ", "         ")
        .set('X', api.block('minecraft:redstone_block'))
        .set('A', api.block('minecraft:crying_obsidian'))
        .set('B', api.anyOf(
            api.anyOfEnergyInput(),
            api.anyOfEnergyOutput()
        ))
        .set('D', api.dataStorage())
        .controller('C')
        .build()
})
