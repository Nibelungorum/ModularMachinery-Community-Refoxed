MMCREvents.server(event => {
    const api = event.getAPI()
    event
        .createStructure("kubejs:hello_world")
        .pattern('AAA', 'XXX', 'XXX')
        .pattern('AAA', 'X X', 'XBX')
        .pattern('AAA', 'XCX', 'XXX')
        .set('X', api.block('minecraft:bricks'))
        .set('A', api.anyOf(
            api.block('minecraft:bricks'),
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput(),
        ))
        .set('B', api.block('minecraft:blast_furnace'))
        .controller('C')
        .build()
})
