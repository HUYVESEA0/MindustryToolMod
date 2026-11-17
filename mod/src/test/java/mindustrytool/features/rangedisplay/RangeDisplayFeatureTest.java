package mindustrytool.features.rangedisplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import arc.Core;
import solim.reactive.Effect;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.core.ContentLoader;
import mindustry.type.UnitType;
import mindustry.world.blocks.defense.BuildTurret;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.RegenProjector;
import mindustry.world.blocks.defense.ShockwaveTower;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.units.RepairTower;
import mindustry.world.blocks.units.RepairTurret;
import mindustrytool.test.MindustryTestEnv;

class RangeDisplayFeatureTest extends MindustryTestEnv {

    @BeforeEach
    void setUp() {
        Vars.content = new ContentLoader();
    }

    @Test
    void testConfigMutationsAndReset() {
        RangeDisplayFeature feature = new RangeDisplayFeature();

        feature.opacityConfig.set(0.35f);
        feature.strokeWidthConfig.set(2.5f);
        feature.hoverOnlyConfig.set(true);
        feature.filterTargetAirConfig.set(false);
        feature.filterTargetGroundConfig.set(false);
        feature.onlyWithAmmoConfig.set(true);
        feature.proximityFilterConfig.set(true);
        feature.proximityRadiusConfig.set(45.0f);
        feature.drawTurretRangeAllyConfig.set(false);
        feature.drawTurretRangeEnemyConfig.set(false);
        feature.drawUnitRangePlayerConfig.set(false);
        feature.drawUnitRangeAllyConfig.set(false);
        feature.drawUnitRangeEnemyConfig.set(false);
        feature.drawUnitAbilityRangeConfig.set(false);
        feature.drawUnitBuildRangeConfig.set(true);
        feature.drawUnitMineRangeConfig.set(true);
        feature.drawBlockRangeAllyConfig.set(false);
        feature.drawBlockRangeEnemyConfig.set(false);
        feature.drawSpawnerRangeConfig.set(false);
        feature.drawOverdriveRangeConfig.set(false);
        feature.drawMenderRangeConfig.set(false);
        feature.drawPointDefenseRangeConfig.set(false);
        feature.useTeamColorForSpecialConfig.set(true);
        feature.dashedConfig.set(false);
        feature.zoomThresholdConfig.set(1.5f);
        feature.mergedContourConfig.set(false);
        feature.pulseWaveConfig.set(false);
        feature.fillAlphaConfig.set(0.22f);
        feature.drawAttackConeConfig.set(false);
        feature.attackConeAlwaysConfig.set(true);

        assertEquals(0.35f, feature.opacityConfig.get(), 0.001f);
        assertEquals(2.5f, feature.strokeWidthConfig.get(), 0.001f);
        assertTrue(feature.hoverOnlyConfig.get());
        assertFalse(feature.filterTargetAirConfig.get());
        assertFalse(feature.filterTargetGroundConfig.get());
        assertTrue(feature.onlyWithAmmoConfig.get());
        assertTrue(feature.proximityFilterConfig.get());
        assertEquals(45.0f, feature.proximityRadiusConfig.get(), 0.001f);
        assertFalse(feature.drawTurretRangeAllyConfig.get());
        assertFalse(feature.drawTurretRangeEnemyConfig.get());
        assertFalse(feature.drawUnitRangePlayerConfig.get());
        assertFalse(feature.drawUnitRangeAllyConfig.get());
        assertFalse(feature.drawUnitRangeEnemyConfig.get());
        assertFalse(feature.drawUnitAbilityRangeConfig.get());
        assertTrue(feature.drawUnitBuildRangeConfig.get());
        assertTrue(feature.drawUnitMineRangeConfig.get());
        assertFalse(feature.drawBlockRangeAllyConfig.get());
        assertFalse(feature.drawBlockRangeEnemyConfig.get());
        assertFalse(feature.drawSpawnerRangeConfig.get());
        assertFalse(feature.drawOverdriveRangeConfig.get());
        assertFalse(feature.drawMenderRangeConfig.get());
        assertFalse(feature.drawPointDefenseRangeConfig.get());
        assertTrue(feature.useTeamColorForSpecialConfig.get());
        assertFalse(feature.dashedConfig.get());
        assertEquals(1.5f, feature.zoomThresholdConfig.get(), 0.001f);
        assertFalse(feature.mergedContourConfig.get());
        assertFalse(feature.pulseWaveConfig.get());
        assertEquals(0.22f, feature.fillAlphaConfig.get(), 0.001f);
        assertFalse(feature.drawAttackConeConfig.get());
        assertTrue(feature.attackConeAlwaysConfig.get());

        feature.resetToDefaults();

        assertEquals(1.0f, feature.opacityConfig.get(), 0.001f);
        assertEquals(1.0f, feature.strokeWidthConfig.get(), 0.001f);
        assertFalse(feature.hoverOnlyConfig.get());
        assertTrue(feature.filterTargetAirConfig.get());
        assertTrue(feature.filterTargetGroundConfig.get());
        assertFalse(feature.onlyWithAmmoConfig.get());
        assertFalse(feature.proximityFilterConfig.get());
        assertEquals(30.0f, feature.proximityRadiusConfig.get(), 0.001f);
        assertTrue(feature.drawTurretRangeAllyConfig.get());
        assertTrue(feature.drawTurretRangeEnemyConfig.get());
        assertTrue(feature.drawUnitRangePlayerConfig.get());
        assertTrue(feature.drawUnitRangeAllyConfig.get());
        assertTrue(feature.drawUnitRangeEnemyConfig.get());
        assertTrue(feature.drawUnitAbilityRangeConfig.get());
        assertFalse(feature.drawUnitBuildRangeConfig.get());
        assertFalse(feature.drawUnitMineRangeConfig.get());
        assertTrue(feature.drawBlockRangeAllyConfig.get());
        assertTrue(feature.drawBlockRangeEnemyConfig.get());
        assertTrue(feature.drawSpawnerRangeConfig.get());
        assertTrue(feature.drawOverdriveRangeConfig.get());
        assertTrue(feature.drawMenderRangeConfig.get());
        assertTrue(feature.drawPointDefenseRangeConfig.get());
        assertFalse(feature.useTeamColorForSpecialConfig.get());
        assertTrue(feature.dashedConfig.get());
        assertEquals(0.5f, feature.zoomThresholdConfig.get(), 0.001f);
        assertTrue(feature.mergedContourConfig.get());
        assertTrue(feature.pulseWaveConfig.get());
        assertEquals(0.10f, feature.fillAlphaConfig.get(), 0.001f);
        assertTrue(feature.drawAttackConeConfig.get());
        assertFalse(feature.attackConeAlwaysConfig.get());
    }

    @Test
    void testSignalReactivity() {
        RangeDisplayFeature feature = new RangeDisplayFeature();
        boolean[] notified = new boolean[1];

        Effect.of(feature.opacityConfig.signal(), v -> notified[0] = true);
        feature.opacityConfig.set(0.75f);

        assertTrue(notified[0]);
        assertEquals(0.75f, feature.opacityConfig.get(), 0.001f);
    }

    @Test
    void testBlockClassification() {
        RangeDisplayFeature feature = new RangeDisplayFeature();

        ItemTurret turret = new ItemTurret("test-duo");
        BuildTurret buildTurret = new BuildTurret("test-build-tower");
        MendProjector mend = new MendProjector("test-mender");
        OverdriveProjector overdrive = new OverdriveProjector("test-overdrive");
        MassDriver massDriver = new MassDriver("test-driver");
        ForceProjector force = new ForceProjector("test-force");
        RegenProjector regen = new RegenProjector("test-regen");
        RepairTower repairTower = new RepairTower("test-repair-tower");
        RepairTurret repairTurret = new RepairTurret("test-repair-turret");
        ShockwaveTower shockwave = new ShockwaveTower("test-shockwave");
        Wall wall = new Wall("test-wall");

        assertTrue(feature.isTurretBlock(turret));
        assertFalse(feature.isTurretBlock(buildTurret));
        assertFalse(feature.isTurretBlock(mend));
        assertFalse(feature.isTurretBlock(repairTower));
        assertFalse(feature.isTurretBlock(repairTurret));
        assertFalse(feature.isTurretBlock(shockwave));
        assertFalse(feature.isTurretBlock(wall));

        assertTrue(feature.isSupportBlock(buildTurret));
        assertTrue(feature.isSupportBlock(mend));
        assertTrue(feature.isSupportBlock(overdrive));
        assertTrue(feature.isSupportBlock(massDriver));
        assertTrue(feature.isSupportBlock(force));
        assertTrue(feature.isSupportBlock(regen));
        assertTrue(feature.isSupportBlock(repairTower));
        assertTrue(feature.isSupportBlock(repairTurret));
        assertTrue(feature.isSupportBlock(shockwave));
        assertFalse(feature.isSupportBlock(turret));
        assertFalse(feature.isSupportBlock(wall));

        assertTrue(feature.isRangeBlock(turret));
        assertTrue(feature.isRangeBlock(mend));
        assertTrue(feature.isRangeBlock(repairTower));
        assertTrue(feature.isRangeBlock(repairTurret));
        assertTrue(feature.isRangeBlock(shockwave));
        assertFalse(feature.isRangeBlock(wall));
    }

    @Test
    void testSemanticColorBlockClassification() {
        RangeDisplayFeature feature = new RangeDisplayFeature();

        OverdriveProjector overdrive = new OverdriveProjector("test-semantic-overdrive");
        MendProjector mend = new MendProjector("test-semantic-mender");
        RegenProjector regen = new RegenProjector("test-semantic-regen");
        RepairTower repairTower = new RepairTower("test-semantic-repair-tower");
        RepairTurret repairTurret = new RepairTurret("test-semantic-repair-turret");
        PointDefenseTurret segment = new PointDefenseTurret("test-semantic-segment");
        ShockwaveTower shockwave = new ShockwaveTower("test-semantic-shockwave");
        ItemTurret turret = new ItemTurret("test-semantic-turret");
        Wall wall = new Wall("test-semantic-wall");

        assertTrue(feature.isOverdriveBlock(overdrive));
        assertFalse(feature.isOverdriveBlock(mend));
        assertFalse(feature.isOverdriveBlock(segment));
        assertFalse(feature.isOverdriveBlock(turret));

        assertTrue(feature.isMenderBlock(mend));
        assertTrue(feature.isMenderBlock(regen));
        assertTrue(feature.isMenderBlock(repairTower));
        assertTrue(feature.isMenderBlock(repairTurret));
        assertFalse(feature.isMenderBlock(overdrive));
        assertFalse(feature.isMenderBlock(segment));
        assertFalse(feature.isMenderBlock(turret));

        assertTrue(feature.isPointDefenseBlock(segment));
        assertTrue(feature.isPointDefenseBlock(shockwave));
        assertFalse(feature.isPointDefenseBlock(overdrive));
        assertFalse(feature.isPointDefenseBlock(mend));
        assertFalse(feature.isPointDefenseBlock(turret));

        assertTrue(feature.isSpecialColorBlock(overdrive));
        assertTrue(feature.isSpecialColorBlock(mend));
        assertTrue(feature.isSpecialColorBlock(segment));
        assertTrue(feature.isSpecialColorBlock(shockwave));
        assertFalse(feature.isSpecialColorBlock(turret));
        assertFalse(feature.isSpecialColorBlock(wall));
    }

    @Test
    void testPerBlockTogglesAndSignals() {
        RangeDisplayFeature feature = new RangeDisplayFeature();
        ItemTurret turret = new ItemTurret("test-scatter");

        assertTrue(feature.isBlockEnabled(turret));
        assertTrue(feature.getBlockSignal(turret).peek());

        feature.setBlockEnabled(turret, false);
        flushEffects();
        assertFalse(feature.isBlockEnabled(turret));
        assertFalse(feature.getBlockSignal(turret).peek());
        assertFalse(Core.settings.getBool(RangeDisplayFeature.blockSettingKey(turret)));

        feature.setBlockEnabled(turret, true);
        flushEffects();
        assertTrue(feature.isBlockEnabled(turret));
        assertTrue(feature.getBlockSignal(turret).peek());
        assertTrue(Core.settings.getBool(RangeDisplayFeature.blockSettingKey(turret)));
    }

    @Test
    void testCategoryTogglesAndReset() {
        RangeDisplayFeature feature = new RangeDisplayFeature();
        ItemTurret turret1 = new ItemTurret("test-hail");
        ItemTurret turret2 = new ItemTurret("test-salvo");
        MendProjector mender = new MendProjector("test-mend-proj");

        feature.rebuildBitSet();

        feature.setCategoryEnabled(true, false);
        flushEffects();
        assertFalse(feature.isBlockEnabled(turret1));
        assertFalse(feature.isBlockEnabled(turret2));
        assertTrue(feature.isBlockEnabled(mender));

        feature.setCategoryEnabled(true, true);
        flushEffects();
        assertTrue(feature.isBlockEnabled(turret1));
        assertTrue(feature.isBlockEnabled(turret2));

        feature.setBlockEnabled(turret1, false);
        feature.setBlockEnabled(mender, false);
        flushEffects();
        assertFalse(feature.isBlockEnabled(turret1));
        assertFalse(feature.isBlockEnabled(mender));

        feature.resetToDefaults();
        flushEffects();
        assertTrue(feature.isBlockEnabled(turret1));
        assertTrue(feature.isBlockEnabled(mender));
    }

    @Test
    void testAllPrimitiveCachesSynchronizeOnSignalAndConfigChanges() {
        RangeDisplayFeature feature = new RangeDisplayFeature();

        assertEquals(1.0f, feature.cachedOpacity, 0.001f);
        assertEquals(1.0f, feature.cachedStrokeWidth, 0.001f);
        assertFalse(feature.cachedHoverOnly);
        assertTrue(feature.cachedFilterTargetAir);
        assertTrue(feature.cachedFilterTargetGround);
        assertFalse(feature.cachedOnlyWithAmmo);
        assertFalse(feature.cachedProximityFilter);
        assertEquals(30.0f, feature.cachedProximityRadius, 0.001f);
        assertTrue(feature.cachedDrawTurretRangeAlly);
        assertTrue(feature.cachedDrawTurretRangeEnemy);
        assertTrue(feature.cachedDrawUnitRangePlayer);
        assertTrue(feature.cachedDrawUnitRangeAlly);
        assertTrue(feature.cachedDrawUnitRangeEnemy);
        assertTrue(feature.cachedDrawUnitAbilityRange);
        assertFalse(feature.cachedDrawUnitBuildRange);
        assertFalse(feature.cachedDrawUnitMineRange);
        assertTrue(feature.cachedDrawBlockRangeAlly);
        assertTrue(feature.cachedDrawBlockRangeEnemy);
        assertTrue(feature.cachedDrawSpawnerRange);
        assertTrue(feature.cachedDrawOverdriveRange);
        assertTrue(feature.cachedDrawMenderRange);
        assertTrue(feature.cachedDrawPointDefenseRange);
        assertFalse(feature.cachedUseTeamColorForSpecial);
        assertTrue(feature.cachedDashed);
        assertEquals(0.5f, feature.cachedZoomThreshold, 0.001f);
        assertTrue(feature.cachedMergedContour);
        assertTrue(feature.cachedPulseWave);
        assertEquals(0.10f, feature.cachedFillAlpha, 0.001f);
        assertTrue(feature.cachedDrawAttackCone);
        assertFalse(feature.cachedAttackConeAlways);

        feature.opacityConfig.set(0.42f);
        feature.strokeWidthConfig.set(2.8f);
        feature.hoverOnlyConfig.set(true);
        feature.filterTargetAirConfig.set(false);
        feature.filterTargetGroundConfig.set(false);
        feature.onlyWithAmmoConfig.set(true);
        feature.proximityFilterConfig.set(true);
        feature.proximityRadiusConfig.set(55.0f);
        feature.drawTurretRangeAllyConfig.set(false);
        feature.drawTurretRangeEnemyConfig.set(false);
        feature.drawUnitRangePlayerConfig.set(false);
        feature.drawUnitRangeAllyConfig.set(false);
        feature.drawUnitRangeEnemyConfig.set(false);
        feature.drawUnitAbilityRangeConfig.set(false);
        feature.drawUnitBuildRangeConfig.set(true);
        feature.drawUnitMineRangeConfig.set(true);
        feature.drawBlockRangeAllyConfig.set(false);
        feature.drawBlockRangeEnemyConfig.set(false);
        feature.drawSpawnerRangeConfig.set(false);
        feature.drawOverdriveRangeConfig.set(false);
        feature.drawMenderRangeConfig.set(false);
        feature.drawPointDefenseRangeConfig.set(false);
        feature.useTeamColorForSpecialConfig.set(true);
        feature.dashedConfig.set(false);
        feature.zoomThresholdConfig.set(1.5f);
        feature.mergedContourConfig.set(false);
        feature.pulseWaveConfig.set(false);
        feature.fillAlphaConfig.set(0.25f);
        feature.drawAttackConeConfig.set(false);
        feature.attackConeAlwaysConfig.set(true);
        flushEffects();

        assertEquals(0.42f, feature.cachedOpacity, 0.001f);
        assertEquals(2.8f, feature.cachedStrokeWidth, 0.001f);
        assertTrue(feature.cachedHoverOnly);
        assertFalse(feature.cachedFilterTargetAir);
        assertFalse(feature.cachedFilterTargetGround);
        assertTrue(feature.cachedOnlyWithAmmo);
        assertTrue(feature.cachedProximityFilter);
        assertEquals(55.0f, feature.cachedProximityRadius, 0.001f);
        assertFalse(feature.cachedDrawTurretRangeAlly);
        assertFalse(feature.cachedDrawTurretRangeEnemy);
        assertFalse(feature.cachedDrawUnitRangePlayer);
        assertFalse(feature.cachedDrawUnitRangeAlly);
        assertFalse(feature.cachedDrawUnitRangeEnemy);
        assertFalse(feature.cachedDrawUnitAbilityRange);
        assertTrue(feature.cachedDrawUnitBuildRange);
        assertTrue(feature.cachedDrawUnitMineRange);
        assertFalse(feature.cachedDrawBlockRangeAlly);
        assertFalse(feature.cachedDrawBlockRangeEnemy);
        assertFalse(feature.cachedDrawSpawnerRange);
        assertFalse(feature.cachedDrawOverdriveRange);
        assertFalse(feature.cachedDrawMenderRange);
        assertFalse(feature.cachedDrawPointDefenseRange);
        assertTrue(feature.cachedUseTeamColorForSpecial);
        assertFalse(feature.cachedDashed);
        assertEquals(1.5f, feature.cachedZoomThreshold, 0.001f);
        assertFalse(feature.cachedMergedContour);
        assertFalse(feature.cachedPulseWave);
        assertEquals(0.25f, feature.cachedFillAlpha, 0.001f);
        assertFalse(feature.cachedDrawAttackCone);
        assertTrue(feature.cachedAttackConeAlways);

        feature.opacityConfig.signal().set(0.88f);
        feature.strokeWidthConfig.signal().set(3.5f);
        feature.hoverOnlyConfig.signal().set(false);
        feature.filterTargetAirConfig.signal().set(true);
        feature.filterTargetGroundConfig.signal().set(true);
        feature.onlyWithAmmoConfig.signal().set(false);
        feature.proximityFilterConfig.signal().set(false);
        feature.proximityRadiusConfig.signal().set(12.0f);
        feature.drawTurretRangeAllyConfig.signal().set(true);
        feature.drawTurretRangeEnemyConfig.signal().set(true);
        feature.drawUnitRangePlayerConfig.signal().set(true);
        feature.drawUnitRangeAllyConfig.signal().set(true);
        feature.drawUnitRangeEnemyConfig.signal().set(true);
        feature.drawUnitAbilityRangeConfig.signal().set(true);
        feature.drawUnitBuildRangeConfig.signal().set(false);
        feature.drawUnitMineRangeConfig.signal().set(false);
        feature.drawBlockRangeAllyConfig.signal().set(true);
        feature.drawBlockRangeEnemyConfig.signal().set(true);
        feature.drawSpawnerRangeConfig.signal().set(true);
        feature.drawOverdriveRangeConfig.signal().set(true);
        feature.drawMenderRangeConfig.signal().set(true);
        feature.drawPointDefenseRangeConfig.signal().set(true);
        feature.useTeamColorForSpecialConfig.signal().set(false);
        feature.dashedConfig.signal().set(true);
        feature.zoomThresholdConfig.signal().set(2.5f);
        feature.mergedContourConfig.signal().set(true);
        feature.pulseWaveConfig.signal().set(true);
        feature.fillAlphaConfig.signal().set(0.15f);
        feature.drawAttackConeConfig.signal().set(true);
        feature.attackConeAlwaysConfig.signal().set(false);
        flushEffects();

        assertEquals(0.88f, feature.cachedOpacity, 0.001f);
        assertEquals(3.5f, feature.cachedStrokeWidth, 0.001f);
        assertFalse(feature.cachedHoverOnly);
        assertTrue(feature.cachedFilterTargetAir);
        assertTrue(feature.cachedFilterTargetGround);
        assertFalse(feature.cachedOnlyWithAmmo);
        assertFalse(feature.cachedProximityFilter);
        assertEquals(12.0f, feature.cachedProximityRadius, 0.001f);
        assertTrue(feature.cachedDrawTurretRangeAlly);
        assertTrue(feature.cachedDrawTurretRangeEnemy);
        assertTrue(feature.cachedDrawUnitRangePlayer);
        assertTrue(feature.cachedDrawUnitRangeAlly);
        assertTrue(feature.cachedDrawUnitRangeEnemy);
        assertTrue(feature.cachedDrawUnitAbilityRange);
        assertFalse(feature.cachedDrawUnitBuildRange);
        assertFalse(feature.cachedDrawUnitMineRange);
        assertTrue(feature.cachedDrawBlockRangeAlly);
        assertTrue(feature.cachedDrawBlockRangeEnemy);
        assertTrue(feature.cachedDrawSpawnerRange);
        assertTrue(feature.cachedDrawOverdriveRange);
        assertTrue(feature.cachedDrawMenderRange);
        assertTrue(feature.cachedDrawPointDefenseRange);
        assertFalse(feature.cachedUseTeamColorForSpecial);
        assertTrue(feature.cachedDashed);
        assertEquals(2.5f, feature.cachedZoomThreshold, 0.001f);
        assertTrue(feature.cachedMergedContour);
        assertTrue(feature.cachedPulseWave);
        assertEquals(0.15f, feature.cachedFillAlpha, 0.001f);
        assertTrue(feature.cachedDrawAttackCone);
        assertFalse(feature.cachedAttackConeAlways);

        feature.opacityConfig.signal().set(null);
        feature.hoverOnlyConfig.signal().set(null);
        feature.filterTargetAirConfig.signal().set(null);
        feature.useTeamColorForSpecialConfig.signal().set(null);
        feature.zoomThresholdConfig.signal().set(null);
        feature.mergedContourConfig.signal().set(null);
        feature.pulseWaveConfig.signal().set(null);
        feature.fillAlphaConfig.signal().set(null);
        feature.drawAttackConeConfig.signal().set(null);
        feature.attackConeAlwaysConfig.signal().set(null);
        feature.drawUnitAbilityRangeConfig.signal().set(null);
        feature.drawUnitBuildRangeConfig.signal().set(null);
        feature.drawUnitMineRangeConfig.signal().set(null);
        flushEffects();
        assertEquals(1.0f, feature.cachedOpacity, 0.001f);
        assertFalse(feature.cachedHoverOnly);
        assertTrue(feature.cachedFilterTargetAir);
        assertFalse(feature.cachedUseTeamColorForSpecial);
        assertEquals(0.5f, feature.cachedZoomThreshold, 0.001f);
        assertTrue(feature.cachedMergedContour);
        assertTrue(feature.cachedPulseWave);
        assertEquals(0.10f, feature.cachedFillAlpha, 0.001f);
        assertTrue(feature.cachedDrawAttackCone);
        assertFalse(feature.cachedAttackConeAlways);
        assertTrue(feature.cachedDrawUnitAbilityRange);
        assertFalse(feature.cachedDrawUnitBuildRange);
        assertFalse(feature.cachedDrawUnitMineRange);

        feature.opacityConfig.set(0.1f);
        feature.zoomThresholdConfig.set(3.0f);
        feature.drawUnitBuildRangeConfig.set(true);
        flushEffects();
        feature.resetToDefaults();
        flushEffects();
        assertEquals(1.0f, feature.cachedOpacity, 0.001f);
        assertEquals(1.0f, feature.cachedStrokeWidth, 0.001f);
        assertFalse(feature.cachedHoverOnly);
        assertEquals(0.5f, feature.cachedZoomThreshold, 0.001f);
        assertFalse(feature.cachedDrawUnitBuildRange);
    }

    @Test
    void testUnitClassificationAndPerUnitToggles() {
        RangeDisplayFeature feature = new RangeDisplayFeature();
        UnitType dagger = new UnitType("test-dagger");
        UnitType poly = new UnitType("test-poly");
        UnitType hidden = new UnitType("test-hidden-unit");
        hidden.hidden = true;
        UnitType internal = new UnitType("test-internal-unit");
        internal.internal = true;

        UnitType prevBlock = UnitTypes.block;
        UnitType blockUnit = new UnitType("test-block-unit");
        UnitTypes.block = blockUnit;
        try {
            assertTrue(feature.isRangeUnit(dagger));
            assertTrue(feature.isRangeUnit(poly));
            assertFalse(feature.isRangeUnit(hidden));
            assertFalse(feature.isRangeUnit(internal));
            assertFalse(feature.isRangeUnit(blockUnit));
            assertFalse(feature.isRangeUnit(null));

            feature.rebuildBitSet();

            assertTrue(feature.isUnitEnabled(dagger));
            assertTrue(feature.getUnitSignal(dagger).peek());

            feature.setUnitEnabled(dagger, false);
            flushEffects();
            assertFalse(feature.isUnitEnabled(dagger));
            assertFalse(feature.getUnitSignal(dagger).peek());
            assertFalse(Core.settings.getBool(RangeDisplayFeature.unitSettingKey(dagger)));
            assertTrue(feature.isUnitEnabled(poly));

            feature.setAllUnitsEnabled(false);
            flushEffects();
            assertFalse(feature.isUnitEnabled(dagger));
            assertFalse(feature.isUnitEnabled(poly));

            feature.setAllUnitsEnabled(true);
            flushEffects();
            assertTrue(feature.isUnitEnabled(dagger));
            assertTrue(feature.isUnitEnabled(poly));

            feature.setUnitEnabled(dagger, false);
            flushEffects();
            assertFalse(feature.isUnitEnabled(dagger));

            feature.resetToDefaults();
            flushEffects();
            assertTrue(feature.isUnitEnabled(dagger));
            assertTrue(feature.isUnitEnabled(poly));
        } finally {
            UnitTypes.block = prevBlock;
        }
    }
}
