MMCREvents.startup(event => {
    const machine = event
        .createMachine("mmcr_kubejs:kubejs_data_storage_machine")
        .displayNameKey("machine.mmcr_kubejs.kubejs_data_storage_machine")
        .recipeFamily("mmcr_kubejs:kubejs_data_storage_machine")
        .appearance("minecraft:crying_obsidian");

    // Some lib we will use
    // see what, big integer
    const BigInteger = Java.loadClass("java.math.BigInteger")
    const DataValue = Java.loadClass("cn.howxu.mmcr.api.data.DataValue")
    const EnergyRequirement = Java.loadClass(
        "cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement"
    )
    const api = MMCR.getAPI()
    const RecipeIO = api.recipeIO()
    const OutputPolicy = api.outputPolicy()

    const INT_MAX = MMCR.getValues().INT_MAX

    machine
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                const storage = ctx.dataStorage()
                if (storage == null) return

                // get big integer data from data storage
                let stored = BigInteger.ZERO
                const saved = storage.get("energy")

                if (saved.isPresent()) {
                    stored = saved.get()
                        .asBigInteger()
                        .orElse(BigInteger.ZERO)
                }

                // every 5 ticks do one input check
                if (ctx.isDue(5)) {
                    // because of the neoforge limit
                    // 2.1G is the biggest input and output value
                    const available = ctx.ioView().energyInput()
                    const maxRequest = Math.min(available, INT_MAX)

                    let low = 0
                    let high = maxRequest

                    // a binary search for every input hatch for transferLimit
                    while (low < high) {
                        const candidate = low + Math.ceil((high - low) / 2)

                        const probe = ctx.ioPlan()
                        probe.addInput(new EnergyRequirement(candidate))

                        if (probe.simulate().energySatisfied()) {
                            low = candidate
                        } else {
                            high = candidate - 1
                        }
                    }

                    if (low > 0) {
                        const inputPlan = ctx.ioPlan()
                        inputPlan.addInput(new EnergyRequirement(low))

                        const next = stored.add(BigInteger.valueOf(low))

                        if (inputPlan.commit(transaction => {
                            // update the data storage value
                            storage.set("energy", DataValue.of(next), transaction)
                        }).successful()) {
                            stored = next
                        }
                    }
                }

                // every 5 tick do one output
                if (ctx.isDue(5)) {

                    // get output capability
                    const outputCapacity = ctx.ioView().energyOutputCapacity()

                    if (outputCapacity > 0 && stored.signum() > 0) {
                        // limit 2.1G
                        const requestedBig = stored.min(
                            BigInteger.valueOf(Math.min(outputCapacity, INT_MAX))
                        )
                        const requested = requestedBig.intValue()

                        if (requested > 0) {
                            const outputPlan = ctx.ioPlan()

                            // some output hatches have transformer limit, so OutputPolicy.ALLOW_PARTIAL
                            outputPlan.addOutput(
                                new EnergyRequirement(RecipeIO.OUTPUT, requested),
                                OutputPolicy.ALLOW_PARTIAL
                            )

                            const simulation = outputPlan.simulate()
                            const outputs = simulation.outputs()

                            if (!outputs.isEmpty()) {
                                const accepted = outputs.get(0).accepted()

                                if (accepted > 0) {
                                    const next = stored.subtract(
                                        BigInteger.valueOf(accepted)
                                    )

                                    // use js promise to update storage
                                    if (outputPlan.commit(transaction => {
                                        storage.set("energy", DataValue.of(next), transaction)
                                    }).successful()) {
                                        stored = next
                                    }
                                }
                            }
                        }
                    }
                }

                ctx.screenText().replace(
                    api.id("mmcr_kubejs:fe_storage_status"),
                    Text.literal("FE stored: " + stored.toString())
                )

            })
        )

    machine.register()
    
    // add one text to display the energy storage
    event.registerControllerScreenText(
        "mmcr_kubejs:kubejs_data_storage_machine",
        text => {
            text.append(
                "controller",
                "mmcr_kubejs:fe_storage_status",
                Text.literal("No FE storaged!")
            )
        }
    )



})
