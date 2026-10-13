MMCREvents.server(event => {


    const api = event.getAPI()

    event
        .createStructure("mmcr_kubejs:bench_1")
        .pattern(['XXX', 'XXX', 'XXX'])
        .pattern(['XXX', 'X X', 'XXX'])
        .pattern(['XXX', 'XCX', 'XXX'])
        .set('X', api.anyOf(
            api.block('minecraft:bricks'),
            api.ports()
        ))
        .controller('C')
        .build()
    
    event.createStructure("mmcr_kubejs:bench_2")
        .pattern(['XXX', 'XXX', 'XXX'])
        .pattern(['XXX', 'X X', 'XXX'])
        .pattern(['XXX', 'XCX', 'XXX'])
        .set('X', api.anyOf(
            api.block('minecraft:iron_block'),
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.parallelControllers(),
            api.factoryController(),
        ))
        .controller('C')
        .build()
})
