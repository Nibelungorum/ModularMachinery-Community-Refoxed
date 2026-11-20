MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:inner_ctm")
        .pattern(" XXXXX ", " XXCXX ", "XABDEAX", "BXEEEXA", "BXAAAXA", " XBBBX ", " XAAAX ")
        .set('X', api.anyOf(
            api.block('mmcr:basic_casing'),
            api.ports()
        ))
        .set('A', api.state('mmcr:gearbox'))
        .set('B', api.state('mmcr:air_intake'))
        .set('D', api.state('mmcr:combustion_chamber'))
        .set('E', api.block('mmcr:vent'))
        .controller('C')
        .build()
})
