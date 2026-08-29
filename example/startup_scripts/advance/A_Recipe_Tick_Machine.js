MMCREvents.startup(event => {

    const api = MMCR.getAPI() // I suggest use MMCR.getAPI() in start_up script
    // Some advanced KubeJS usage
    const LivingEntity = Java.loadClass("net.minecraft.world.entity.LivingEntity")
    const MobEffects = Java.loadClass("net.minecraft.world.effect.MobEffects")

    // Some Class
    const ItemRequirement = Java.loadClass("cn.howxu.mmcr.api.recipe.requirement.ItemRequirement")
    const ArrayList = Java.loadClass("java.util.ArrayList")
    const BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries")

    const machine = event
        .createMachine("mmcr_kubejs:kubejs_recipe_ticker")
        .displayNameKey("machine.mmcr_kubejs.kubejs_recipe_ticker")
        .recipeFamily("mmcr_kubejs:kubejs_recipe_ticker")
        .appearance("minecraft:green_terracotta")
        // Here you can set some recipe tick hook
        .recipeBehavior(behavior => behavior
            .idleStart(ctx => {
                // This add two lines to the controller UI
                const screen = ctx.screenText()
                screen.append(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_idle_empty_line"),
                    Text.literal(" ")
                )
                screen.append(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_idle"),
                    Text.translatable(
                        "gui.mmcr_kubejs.display_when_idle",
                    )
                )
            })
            .idleEnd(ctx => {
                // may do something here
            })
            .beforeStart(ctx => {
                // This will clear the lines when it's not idle
                // NOTICE: here ctx is different from idleStart ctx, you should use ctx.machineContext().screenText() to get the text lines
                const screen = ctx.machineContext().screenText()
                screen.remove(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_idle_empty_line")
                )
                screen.remove(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_idle")
                )
                const machineContext = ctx.machineContext()
                const level = machineContext.level()
                const controllerPos = machineContext.controllerPos()
                const minX = controllerPos.getX() - 2
                const minZ = controllerPos.getZ() - 2
                const maxX = controllerPos.getX() + 3
                const maxZ = controllerPos.getZ() + 3
                const area = AABB.of(
                    minX,
                    level.getMinY(),
                    minZ,
                    maxX,
                    level.getMaxY() + 1,
                    maxZ
                )

                level.getEntitiesOfClass(LivingEntity, area).forEach(entity => {
                    entity.potionEffects.add(MobEffects.STRENGTH, 10000, 1)
                })

                // declare if there are 32 gold ingots, if true set the actual input to 1
                // Here is one example you can reproduce with just kubejs
                // I suggest to use Java API if you want more complex tick
                const nextRequirements = new ArrayList()
                let changed = false

                ctx.requirements().forEach(requirement => {
                    // loop the requirements and find ItemInputRequirement
                    if (!(requirement instanceof ItemRequirement)
                        || String(requirement.io().getKey()) !== "input") {
                        nextRequirements.add(requirement)
                        return
                    }

                    // Maybe complex, tag or item or s stack of item
                    const possibleItems = requirement.item().getStackArray()
                    const isExactlyGold =
                        requirement.count() === 32 // count
                        && possibleItems.length === 1 // one time input
                        && BuiltInRegistries.ITEM
                            .getKey(possibleItems[0].getItem())
                            .toString() === "minecraft:gold_ingot" // register key compare

                    if (isExactlyGold) {
                        // change the requirement
                        nextRequirements.add(new ItemRequirement(
                            requirement.io(),
                            requirement.item(),
                            1,
                            requirement.stack(),
                            requirement.chance(),
                            requirement.tags(),
                            requirement.components(),
                            requirement.consumeChance()
                        ))
                        changed = true
                    } else {
                        // or be default cosume
                        nextRequirements.add(requirement)
                    }
                })

                if (changed) {
                    // then change the input requirement
                    // actual cosumation will be changed in the actual process
                    ctx.setRequirements(nextRequirements)
                }
            })
            .recipeTick(ctx => {
                // When recipe is running, append some infomation to the machine controller
                // it's too difficult to implement grid layout, which would be complex to accelete the actual pixels with characters
                const screen = ctx.machineContext().screenText()

                screen.appendAfter(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_start_recipe"),
                    api.id("mmcr_kubejs:in_line"),
                    // Just Text.literal is allowed
                    // For this fully custom usage, I even provide sprintf
                    Text.literal("正在使用雷霆大猪咪暴力执行配方")
                )
            })
            .beforeFinish(ctx => {
                // before finish and output the recipe
                // you can also change the result if you want
                // here we just add one potion effect
                const machineContext = ctx.machineContext()
                const level = machineContext.level()
                const controllerPos = machineContext.controllerPos()
                const minX = controllerPos.getX() - 2
                const minZ = controllerPos.getZ() - 2
                const maxX = controllerPos.getX() + 3
                const maxZ = controllerPos.getZ() + 3
                const area = AABB.of(
                    minX,
                    level.getMinY(),
                    minZ,
                    maxX,
                    level.getMaxY() + 1,
                    maxZ
                )

                level.getEntitiesOfClass(LivingEntity, area).forEach(entity => {
                    entity.potionEffects.add(MobEffects.NIGHT_VISION, 10000, 1)
                })
            })
        )

    // Register Controller UI lines
    // You are allowed to use an event registry to add your custom lines to controller UI
    // MMCR will automatically organize them and display
    event.registerControllerScreenText("mmcr_kubejs:kubejs_recipe_ticker", text => {
        text.appendTranslatable(
            "controller", // means it's a static text line
            "mmcr_kubejs:before_line",
            "gui.mmcr_kubejs.before_line"
        )
        
        text.appendAfterTranslatable(
            "controller",
            "mmcr_kubejs:in_line",       // the new line id
            "mmcr_kubejs:sp_line_1",     // then you can set it must be after which line
            "gui.mmcr_kubejs.in_line"
        )

        text.appendTranslatable(
            "controller",
            "mmcr_kubejs:after_line",
            "gui.mmcr_kubejs.after_line"
        )

    })

    machine.register()
})
