// Demo only: pending real StartupEvents.registry('mmcr:machine') exposure in MMCR's KubeJS integration.
StartupEvents.registry('mmcr:machine', event => {
    event.create('mmcr:demo_cube')
        .localizedName('Demo Cube')
        .pattern(`
            CCC
            CFC
            CCC
        `, { C: 'mmcr:casing', F: 'mmcr:controller' });
});
