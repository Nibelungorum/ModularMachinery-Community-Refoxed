MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_distillation_tower")

    // Define the main structure of the distillation tower.
    structure
        // Use mainStructure to define the main section.
        .mainStructure(stage =>
            stage
                .pattern(['  XXX  ','  AAA  ','       ','       '])
                .pattern([' XXXXX ',' B   B ','  ACA  ','       '])
                .pattern(['XXXXXXX','A     A',' B   B ','  DDD  '])
                .pattern(['XXXXXXX','A     A',' B   B ','  DDD  '])
                .pattern(['XXXXXXX','A     A',' B   B ','  DDD  '])
                .pattern([' XXXXX ',' B   B ','  BBB  ','       '])
                .pattern(['  XXX  ','  BEB  ','       ','       '])
                .set('C',api.anyOf(
                    stage.anyOfItemInput(),
                    stage.anyOfItemOutput(),
                    stage.anyOfEnergyInput(),
                    api.block('minecraft:deepslate_bricks')
                ))
                .set('X', api.block('minecraft:polished_blackstone'))
                .set('A', api.block('minecraft:deepslate_bricks'))
                .set('B', api.block('minecraft:polished_blackstone_bricks'))
                .set('D', api.block('minecraft:gilded_blackstone'))
                .controller('E')
        )
        // Use expandStructure to define an additional section.
        .expandStructure(stage => stage
            .pattern(['  XXX  ','  AAA  ','       ','       ','       '])
            .pattern([' XXXXX ',' B   B ','  ACA  ','  ACA  ','       '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '])
            .pattern([' XXXXX ',' B   B ','  BBB  ','  BBB  ','       '])
            .pattern(['  XXX  ','  BEB  ','       ','       ','       '])
            .set('C',api.anyOf(
                stage.anyOfItemInput(),
                stage.anyOfItemOutput(),
                stage.anyOfEnergyInput(),
                api.block('minecraft:deepslate_bricks')
            ))
            .set('X', api.block('minecraft:polished_blackstone'))
            .set('A', api.block('minecraft:deepslate_bricks'))
            .set('B', api.block('minecraft:polished_blackstone_bricks'))
            .set('D', api.block('minecraft:gilded_blackstone'))
            .controller('E')
        )

        .expandStructure(stage => stage
            .pattern(['  XXX  ','  AAA  ','       ','       ','       ','       '])
            .pattern([' XXXXX ',' B   B ','  ACA  ','  ACA  ','  ACA  ','       '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
            .pattern(['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '])
            .pattern([' XXXXX ',' B   B ','  BBB  ','  BBB  ','  BBB  ','       '])
            .pattern(['  XXX  ','  BEB  ','       ','       ','       ','       '])
            .set('C',api.anyOf(
                stage.anyOfItemInput(),
                stage.anyOfItemOutput(),
                stage.anyOfEnergyInput(),
                api.block('minecraft:deepslate_bricks')
            ))
            .set('X', api.block('minecraft:polished_blackstone'))
            .set('A', api.block('minecraft:deepslate_bricks'))
            .set('B', api.block('minecraft:polished_blackstone_bricks'))
            .set('D', api.block('minecraft:gilded_blackstone'))
            .controller('E')
        )

        // Build the grouped structure.
        structure.build()

    // See server_scripts/recipe/A_Group_Machine.js for the recipe definitions.
})
