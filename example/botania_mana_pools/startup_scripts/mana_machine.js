// Install only this example's startup_scripts/ and server_scripts/ in kubejs/.
MMCREvents.startup(event => {
    const api = event.getAPI()
    const Component = Java.loadClass('net.minecraft.network.chat.Component')
    const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    const lineId = ResourceLocation.parse('mmcr_example:mana_available')

    event.createMachine('mmcr_example:mana_machine')
        .recipePool('mmcr_example:mana_machine')
        .displayNameKey('machine.mmcr_example.mana_machine')
        .appearance('minecraft:stone_bricks')
        .preServerTick(ctx => {
            // This callback's real API is ioView(), not io.view().
            // Snapshot reads do not transfer mana or replace recipe planning.
            const view = ctx.ioView()
            ctx.screenText().append(api.screenScope().OPERATION, lineId,
                Component.translatable('gui.mmcr.example.mana_available',
                    api.readableNumberExact(view.manaInput()),
                    api.readableNumberExact(view.manaOutputCapacity())))
        })
        .register()
})
