// 监听物品注册事件
StartupEvents.registry('item', event => {
    event.create('plate_mold')
    event.create('gear_mold')
    event.create('iron_plate')
    event.create('iron_gear')
})
