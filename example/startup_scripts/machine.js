// KubeJS recreation of the development defaults. It deliberately does not read mmcr:* definitions.
const NS = 'mmcr_kubejs'
const cloneId = path => `${NS}:kubejs_${path}`
const register = (path, configure) => {
  const builder = new MMCR_MACHINE_BUILDER(cloneId(path))
    .displayNameKey(`machine.mmcr.${path}`)
    .recipeFamily(cloneId(path))
  configure(builder)
  builder.register()
}

const couplerPattern = count => {
  const blocks = new java.util.LinkedHashMap()
  for (let x = 0; x < count; x++) blocks.put(MMCR_API.pos(x, 0, 0), MMCR_API.coupler())
  return MMCR_API.blockArray(blocks)
}

register('blast_furnace', builder => builder
  .allowMultithreading().allowParallelism().maxParallelAmount(2147483647))
register('alloy_furnace', builder => builder
  .appearance('minecraft:bricks').allowModifiers())
register('cracker', builder => builder
  .allowVerticalFacing().fullyRotationallySymmetric())
register('reactor', builder => builder.appearance('minecraft:blue_ice'))
register('thermal_smelting_furnace', builder => builder
  .appearance('minecraft:smooth_basalt').allowMultithreading().allowParallelism().maxParallelAmount(2147483647))
register('purpur_furnace', builder => builder
  .appearance('minecraft:end_stone_bricks').allowMultithreading().allowParallelism().maxParallelAmount(4)
  .runningSound('minecraft:block.furnace.fire_crackle').finishSound('minecraft:entity.ender_dragon.growl')
  .smartInterface('Mode', 1, 3).priority(0).valueType('integer').end()
  .smartInterface('Temperature', 400, 6800).priority(1).valueType('integer').end()
  .smartInterface('ConversionRate', 0, 1).priority(2).valueType('float').end())
register('distillation_tower', builder => builder
  .appearance('minecraft:polished_blackstone').allowMultithreading().allowParallelism().maxParallelAmount(4)
  .expandableStructure(true))
register('eco_matrix', builder => builder.appearance('minecraft:sea_lantern').expandableStructure(true))
register('space_elevator', builder => builder
  .appearance('minecraft:smooth_quartz')
  .controllerBaseTexture('minecraft:block/quartz_block_bottom')
  .formedPortBaseTexture('minecraft:block/quartz_block_bottom')
  .host(cloneId('space_reassembler')).pattern(couplerPattern(3)))
register('space_reassembler', builder => builder
  .appearance('minecraft:quartz_pillar').module().pattern(couplerPattern(1)))
