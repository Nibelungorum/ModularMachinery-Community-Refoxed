// Module couplers belong to the machine structure, not the startup registration.

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
        // Use api.coupler() to quickly declare a module coupler position.
        .set('B' ,api.coupler())
        .set('D' ,api.anyOf(
            api.block('minecraft:smooth_quartz'),
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput()
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
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput()
        ))
        .set('D', api.block('minecraft:glass'))
        .set('E',api.coupler())
        .controller('F')
        .build()

    // See server_scripts/recipe/A_Module_Machine.js for the recipe definitions.
})
