MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:big_presser")
        .pattern("ABBBA", "X   X", "X   X", "KKKKK")
        .pattern("BAAAB", "     ", "     ", "K   K")
        .pattern("BAAAB", "  D  ", "     ", "KFGFK")
        .pattern("BAAAB", "     ", "     ", "K   K")
        .pattern("ABBBA", "X C X", "X   X", "KKKKK")
        .set('X', api.block('create:brass_casing'))
        .set('A', api.block('create:andesite_casing'))
        .set('B', api.anyOf(
            api.block('create:railway_casing'),
            api.ports()
        ))
        .set('D', api.state('create:depot[waterlogged=false]'))
        .set('F', api.state('create:brass_encased_shaft[axis=x]'))
        .set('G', api.state('create:mechanical_press[facing=east]'))
        .set('K', api.anyOf(
            api.block('create:brass_casing'),
            api.ports()
        ))
        .controller('C')
        .build()
})
