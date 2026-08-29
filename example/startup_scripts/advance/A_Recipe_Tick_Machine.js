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
            .beforeStart(ctx => {
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
                    entity.potionEffects.add(MobEffects.STRENGTH, 100, 1)
                })

                // 判断输入物是否含有32 gold ingot 是则直接把输入消耗改成1
                const nextRequirements = new ArrayList()
                let changed = false

                ctx.requirements().forEach(requirement => {
                    if (!(requirement instanceof ItemRequirement)
                        || String(requirement.io().getKey()) !== "input") {
                        nextRequirements.add(requirement)
                        return
                    }

                    // Ingredient 可能是单个物品、多个候选物品或标签
                    const possibleItems = requirement.item().getStackArray()
                    const isExactlyGold =
                        requirement.count() === 32
                        && possibleItems.length === 1
                        && BuiltInRegistries.ITEM
                            .getKey(possibleItems[0].getItem())
                            .toString() === "minecraft:gold_ingot"

                    if (isExactlyGold) {
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
                        nextRequirements.add(requirement)
                    }
                })

                if (changed) {
                    ctx.setRequirements(nextRequirements)
                }
            })
            .recipeTick(ctx => {
                const screen = ctx.machineContext().screenText()

                screen.appendAfter(
                    api.screenScope().OPERATION,
                    api.id("mmcr_kubejs:display_when_start_recipe"),
                    api.id("mmcr_kubejs:in_line"),
                    Text.literal("正在使用雷霆大猪咪暴力执行配方")
                )
            })
            .beforeFinish(ctx => {
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
                    entity.potionEffects.add(MobEffects.NIGHT_VISION, 100, 1)
                })
            })
        )

    // Register Controller UI lines
    // You are allowed to use an event registry to add your custom lines to controller UI
    // MMCR will automatically organize them and display
    event.registerControllerScreenText("mmcr_kubejs:kubejs_recipe_ticker", text => {
        text.appendTranslatable(
            "controller",
            "mmcr_kubejs:before_line",
            "gui.mmcr_kubejs.before_line"
        )

        text.appendTranslatable(
            "controller",
            "mmcr_kubejs:sp_line_1",
            "gui.mmcr_kubejs.sp_line_1"
        )

        
        text.appendAfterTranslatable(
            "controller",
            "mmcr_kubejs:in_line",       // the new line id
            "mmcr_kubejs:sp_line_1",     // then you can set it must be after which line
            "gui.mmcr_kubejs.in_line"
        )

        text.appendTranslatable(
            "controller",
            "mmcr_kubejs:sp_line_2",
            "gui.mmcr_kubejs.sp_line_2"
        )

        text.appendTranslatable(
            "controller",
            "mmcr_kubejs:after_line",
            "gui.mmcr_kubejs.after_line"
        )

    })


    machine.register()
})
