// Module couplers belong to the machine structure, not the startup machine registration.

MMCREvents.server(event => {
    const api = event.getAPI()

    const space_elevator = event.createStructure("mmcr_kubejs:kubejs_space_elevator")

    space_elevator
        .pattern(['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['     XXAAAXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['    XXXAAAXXX    ','        B        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['   XXXXAAAXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern([' XXAAAXXXXXAAAXX ','       XXX       ','       DDD       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '])
        .pattern(['XXXAAAXXXXXAAAXXX','    B  X X  B    ','       D D       ','       X X       ','      XX XX      ','     XXX XXX     ','       XXX       ','        X        ','        X        ','        X        ','        X        ','        X        '])
        .pattern([' XXAAAXXXXXAAAXX ','       XXX       ','       DED       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '])
        .pattern(['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['   XXXXXXXXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['    XXXXXXXXX    ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['     XXXXXXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .pattern(['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '])
        .set('X' ,api.block('minecraft:smooth_quartz'))
        .set('A' ,api.block('minecraft:amethyst_block'))
        // you can use api.coupler() quickly set the copler pos
        // 其实应该也在space_elevator构造器里 但是已经是技术债了T_T
        .set('B' ,api.coupler())
        .set('D' ,api.anyOf(
            api.block('minecraft:smooth_quartz'),
            space_elevator.anyOfItemInput(),
            space_elevator.anyOfItemOutput(),
            space_elevator.anyOfEnergyInput()
        ))
        .controller('E')
        .build()

    const space_reassembler = event.createStructure("mmcr_kubejs:kubejs_space_reassembler")

    space_reassembler
        .pattern(['AAA','XBX','XBX','XDX'])
        .pattern(['AAA','BEB','B B','DDD'])
        .pattern(['AAA','XFX','XBX','XDX'])
        .set('X',api.block('minecraft:quartz_pillar'))
        .set('A',api.block('minecraft:amethyst_block'))
        .set('B',api.anyOf(
            api.block('minecraft:smooth_quartz'),
            space_reassembler.anyOfItemInput(),
            space_reassembler.anyOfItemOutput(),
            space_reassembler.anyOfEnergyInput()
        ))
        .set('D', api.block('minecraft:glass'))
        .set('E',api.coupler())
        .controller('F')
        .build()

    // Move to server_scripts/recipe/A_Module_Machine.js see how to create some special recipes
})
