package mindustrytool.features.rangedisplay;

import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.ObjectMap.Entry;
import arc.struct.Seq;
import arc.util.Nullable;
import java.util.BitSet;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.ShieldRegenFieldAbility;
import mindustry.entities.abilities.StatusFieldAbility;
import mindustry.entities.abilities.SuppressionFieldAbility;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.BuildTurret;
import mindustry.world.blocks.defense.ForceProjector;
import mindustry.world.blocks.defense.ForceProjector.ForceBuild;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.OverdriveProjector.OverdriveBuild;
import mindustry.world.blocks.defense.RegenProjector;
import mindustry.world.blocks.defense.ShockwaveTower;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.power.LightBlock;
import mindustry.world.blocks.units.RepairTower;
import mindustry.world.blocks.units.RepairTurret;
import mindustrytool.components.FileIcon;
import mindustrytool.features.Feature;
import mindustrytool.features.FeatureMetadata;
import mindustrytool.features.rangedisplay.ui.RangeDisplaySettingsDialog;
import solim.config.ConfigGroup;
import solim.config.ConfigValue;
import solim.overlay.SolimDialog;
import solim.reactive.Effect;
import solim.reactive.Signal;

public class RangeDisplayFeature extends Feature {

    private static final Color ORANGE = Color.valueOf("#FFA500");

    public final ConfigGroup config;
    public final ConfigValue<Float> opacityConfig;
    public final ConfigValue<Float> strokeWidthConfig;
    public final ConfigValue<Boolean> hoverOnlyConfig;
    public final ConfigValue<Boolean> filterTargetAirConfig;
    public final ConfigValue<Boolean> filterTargetGroundConfig;
    public final ConfigValue<Boolean> onlyWithAmmoConfig;
    public final ConfigValue<Boolean> proximityFilterConfig;
    public final ConfigValue<Float> proximityRadiusConfig;
    public final ConfigValue<Boolean> drawTurretRangeAllyConfig;
    public final ConfigValue<Boolean> drawTurretRangeEnemyConfig;
    public final ConfigValue<Boolean> drawUnitRangePlayerConfig;
    public final ConfigValue<Boolean> drawUnitRangeAllyConfig;
    public final ConfigValue<Boolean> drawUnitRangeEnemyConfig;
    public final ConfigValue<Boolean> drawUnitAbilityRangeConfig;
    public final ConfigValue<Boolean> drawUnitBuildRangeConfig;
    public final ConfigValue<Boolean> drawUnitMineRangeConfig;
    public final ConfigValue<Boolean> drawBlockRangeAllyConfig;
    public final ConfigValue<Boolean> drawBlockRangeEnemyConfig;
    public final ConfigValue<Boolean> drawSpawnerRangeConfig;
    public final ConfigValue<Boolean> drawOverdriveRangeConfig;
    public final ConfigValue<Boolean> drawMenderRangeConfig;
    public final ConfigValue<Boolean> drawPointDefenseRangeConfig;
    public final ConfigValue<Boolean> useTeamColorForSpecialConfig;
    public final ConfigValue<Boolean> dashedConfig;
    public final ConfigValue<Float> zoomThresholdConfig;
    public final ConfigValue<Boolean> mergedContourConfig;
    public final ConfigValue<Boolean> pulseWaveConfig;
    public final ConfigValue<Float> fillAlphaConfig;
    public final ConfigValue<Boolean> drawAttackConeConfig;
    public final ConfigValue<Boolean> attackConeAlwaysConfig;

    private final Cons<Unit> unitDrawer = this::drawUnitRange;
    private final Cons<Building> buildingDrawer = this::drawBuildingRange;

    private static final Boolf<Building> RANGE_BUILDING_PREDICATE = b -> {
        if (b == null || !b.isValid() || b.team == Team.derelict || b.block == null) {
            return false;
        }
        return b.block instanceof BaseTurret
                || b.block instanceof OverdriveProjector
                || b.block instanceof MassDriver
                || b.block instanceof BuildTurret
                || b.block instanceof MendProjector
                || b.block instanceof RegenProjector
                || b.block instanceof ForceProjector
                || b.block instanceof RepairTower
                || b.block instanceof RepairTurret
                || b.block instanceof ShockwaveTower;
    };

    private final Rect viewBounds = new Rect();
    private final Color colorScratch = new Color();
    private final Vec2 coneOffset1 = new Vec2();
    private final Vec2 coneOffset2 = new Vec2();
    private final RangeContourRenderer contourRenderer = new RangeContourRenderer();
    private @Nullable RangeDisplaySettingsDialog settingsDialog;

    private static final Boolf<Unit> ANY_UNIT = u -> true;

    private @Nullable BitSet blockEnabled;
    private @Nullable BitSet unitEnabled;
    private final Seq<Block> rangeBlocks = new Seq<>();
    private final ObjectMap<String, Signal<Boolean>> blockSignals = new ObjectMap<>();
    private final ObjectMap<String, Signal<Boolean>> unitSignals = new ObjectMap<>();

    float cachedOpacity = 1.0f;
    float cachedStrokeWidth = 1.0f;
    boolean cachedHoverOnly = false;
    boolean cachedFilterTargetAir = true;
    boolean cachedFilterTargetGround = true;
    boolean cachedOnlyWithAmmo = false;
    boolean cachedProximityFilter = false;
    float cachedProximityRadius = 30f;
    boolean cachedDrawTurretRangeAlly = true;
    boolean cachedDrawTurretRangeEnemy = true;
    boolean cachedDrawUnitRangePlayer = true;
    boolean cachedDrawUnitRangeAlly = true;
    boolean cachedDrawUnitRangeEnemy = true;
    boolean cachedDrawUnitAbilityRange = true;
    boolean cachedDrawUnitBuildRange = false;
    boolean cachedDrawUnitMineRange = false;
    boolean cachedDrawBlockRangeAlly = true;
    boolean cachedDrawBlockRangeEnemy = true;
    boolean cachedDrawSpawnerRange = true;
    boolean cachedDrawOverdriveRange = true;
    boolean cachedDrawMenderRange = true;
    boolean cachedDrawPointDefenseRange = true;
    boolean cachedUseTeamColorForSpecial = false;
    boolean cachedDashed = true;
    float cachedZoomThreshold = 0.5f;
    boolean cachedMergedContour = true;
    boolean cachedPulseWave = true;
    float cachedFillAlpha = 0.10f;
    boolean cachedDrawAttackCone = true;
    boolean cachedAttackConeAlways = false;

    private float frameOpacity = 1.0f;
    private float frameStrokeWidth = 1.0f;
    private boolean frameHoverOnly = false;
    private boolean frameFilterTargetAir = true;
    private boolean frameFilterTargetGround = true;
    private boolean frameOnlyWithAmmo = false;
    private boolean frameProximityFilter = false;
    private float frameProximityDistSq = 0f;
    private float frameProximityX = 0f;
    private float frameProximityY = 0f;
    private boolean frameDrawTurretRangeAlly = true;
    private boolean frameDrawTurretRangeEnemy = true;
    private boolean frameDrawUnitRangePlayer = true;
    private boolean frameDrawUnitRangeAlly = true;
    private boolean frameDrawUnitRangeEnemy = true;
    private boolean frameDrawUnitAbilityRange = true;
    private boolean frameDrawUnitBuildRange = false;
    private boolean frameDrawUnitMineRange = false;
    private boolean frameDrawBlockRangeAlly = true;
    private boolean frameDrawBlockRangeEnemy = true;
    private boolean frameDrawSpawnerRange = true;
    private boolean frameDrawOverdriveRange = true;
    private boolean frameDrawMenderRange = true;
    private boolean frameDrawPointDefenseRange = true;
    private boolean frameUseTeamColorForSpecial = false;
    private boolean frameDashed = true;
    private boolean frameMergedContour = true;
    private boolean framePulseWave = true;
    private float frameFillAlpha = 0.10f;
    private boolean frameDrawAttackCone = true;
    private boolean frameAttackConeAlways = false;
    private float frameRendererScale = 1.0f;
    private @Nullable Team framePlayerTeam;

    public RangeDisplayFeature() {
        super(FeatureMetadata.builder()
                .id("range-display")
                .icon(FileIcon.of("range-display.png"))
                .order(5)
                .quickAccess(false)
                .enabledByDefault(true)
                .development(false)
                .displayGroup(true)
                .build());

        config = configGroup();
        opacityConfig = config.floatValue("opacity", 1.0f);
        strokeWidthConfig = config.floatValue("stroke-width", 1.0f);
        hoverOnlyConfig = config.boolValue("hover-only", false);
        filterTargetAirConfig = config.boolValue("filter-target-air", true);
        filterTargetGroundConfig = config.boolValue("filter-target-ground", true);
        onlyWithAmmoConfig = config.boolValue("only-with-ammo", false);
        proximityFilterConfig = config.boolValue("proximity-filter", false);
        proximityRadiusConfig = config.floatValue("proximity-radius", 30f);
        drawTurretRangeAllyConfig = config.boolValue("draw-turret-range-ally", true);
        drawTurretRangeEnemyConfig = config.boolValue("draw-turret-range-enemy", true);
        drawUnitRangePlayerConfig = config.boolValue("draw-unit-range-player", true);
        drawUnitRangeAllyConfig = config.boolValue("draw-unit-range-ally", true);
        drawUnitRangeEnemyConfig = config.boolValue("draw-unit-range-enemy", true);
        drawUnitAbilityRangeConfig = config.boolValue("draw-unit-ability-range", true);
        drawUnitBuildRangeConfig = config.boolValue("draw-unit-build-range", false);
        drawUnitMineRangeConfig = config.boolValue("draw-unit-mine-range", false);
        drawBlockRangeAllyConfig = config.boolValue("draw-block-range-ally", true);
        drawBlockRangeEnemyConfig = config.boolValue("draw-block-range-enemy", true);
        drawSpawnerRangeConfig = config.boolValue("draw-spawner-range", true);
        drawOverdriveRangeConfig = config.boolValue("draw-overdrive-range", true);
        drawMenderRangeConfig = config.boolValue("draw-mender-range", true);
        drawPointDefenseRangeConfig = config.boolValue("draw-point-defense-range", true);
        useTeamColorForSpecialConfig = config.boolValue("use-team-color-for-special", false);
        dashedConfig = config.boolValue("dashed", true);
        zoomThresholdConfig = config.floatValue("zoom-threshold", 0.5f);
        mergedContourConfig = config.boolValue("merged-contour", true);
        pulseWaveConfig = config.boolValue("pulse-wave", true);
        fillAlphaConfig = config.floatValue("fill-alpha", 0.10f);
        drawAttackConeConfig = config.boolValue("draw-attack-cone", true);
        attackConeAlwaysConfig = config.boolValue("attack-cone-always", false);

        Effect.of(opacityConfig.signal(), v -> cachedOpacity = v != null ? v : 1.0f);
        Effect.of(strokeWidthConfig.signal(), v -> cachedStrokeWidth = v != null ? v : 1.0f);
        Effect.of(hoverOnlyConfig.signal(), v -> cachedHoverOnly = v != null ? v : false);
        Effect.of(filterTargetAirConfig.signal(), v -> cachedFilterTargetAir = v != null ? v : true);
        Effect.of(filterTargetGroundConfig.signal(), v -> cachedFilterTargetGround = v != null ? v : true);
        Effect.of(onlyWithAmmoConfig.signal(), v -> cachedOnlyWithAmmo = v != null ? v : false);
        Effect.of(proximityFilterConfig.signal(), v -> cachedProximityFilter = v != null ? v : false);
        Effect.of(proximityRadiusConfig.signal(), v -> cachedProximityRadius = v != null ? v : 30f);
        Effect.of(drawTurretRangeAllyConfig.signal(), v -> cachedDrawTurretRangeAlly = v != null ? v : true);
        Effect.of(drawTurretRangeEnemyConfig.signal(), v -> cachedDrawTurretRangeEnemy = v != null ? v : true);
        Effect.of(drawUnitRangePlayerConfig.signal(), v -> cachedDrawUnitRangePlayer = v != null ? v : true);
        Effect.of(drawUnitRangeAllyConfig.signal(), v -> cachedDrawUnitRangeAlly = v != null ? v : true);
        Effect.of(drawUnitRangeEnemyConfig.signal(), v -> cachedDrawUnitRangeEnemy = v != null ? v : true);
        Effect.of(drawUnitAbilityRangeConfig.signal(), v -> cachedDrawUnitAbilityRange = v != null ? v : true);
        Effect.of(drawUnitBuildRangeConfig.signal(), v -> cachedDrawUnitBuildRange = v != null ? v : false);
        Effect.of(drawUnitMineRangeConfig.signal(), v -> cachedDrawUnitMineRange = v != null ? v : false);
        Effect.of(drawBlockRangeAllyConfig.signal(), v -> cachedDrawBlockRangeAlly = v != null ? v : true);
        Effect.of(drawBlockRangeEnemyConfig.signal(), v -> cachedDrawBlockRangeEnemy = v != null ? v : true);
        Effect.of(drawSpawnerRangeConfig.signal(), v -> cachedDrawSpawnerRange = v != null ? v : true);
        Effect.of(drawOverdriveRangeConfig.signal(), v -> cachedDrawOverdriveRange = v != null ? v : true);
        Effect.of(drawMenderRangeConfig.signal(), v -> cachedDrawMenderRange = v != null ? v : true);
        Effect.of(drawPointDefenseRangeConfig.signal(), v -> cachedDrawPointDefenseRange = v != null ? v : true);
        Effect.of(useTeamColorForSpecialConfig.signal(), v -> cachedUseTeamColorForSpecial = v != null ? v : false);
        Effect.of(dashedConfig.signal(), v -> cachedDashed = v != null ? v : true);
        Effect.of(zoomThresholdConfig.signal(), v -> cachedZoomThreshold = v != null ? v : 0.5f);
        Effect.of(mergedContourConfig.signal(), v -> cachedMergedContour = v != null ? v : true);
        Effect.of(pulseWaveConfig.signal(), v -> cachedPulseWave = v != null ? v : true);
        Effect.of(fillAlphaConfig.signal(), v -> cachedFillAlpha = v != null ? v : 0.10f);
        Effect.of(drawAttackConeConfig.signal(), v -> cachedDrawAttackCone = v != null ? v : true);
        Effect.of(attackConeAlwaysConfig.signal(), v -> cachedAttackConeAlways = v != null ? v : false);

        syncConfigCache();

        bindToggle("rangedisplay", KeyCode.unset);
        bindDialog("rangedisplaysettings", KeyCode.unset, getSettingDialog(), false);

        Events.on(ClientLoadEvent.class, e -> rebuildBitSet());
        Events.run(Trigger.draw, this::draw);
    }

    private void syncConfigCache() {
        Float op = opacityConfig.get();
        cachedOpacity = op != null ? op : 1.0f;
        Float sw = strokeWidthConfig.get();
        cachedStrokeWidth = sw != null ? sw : 1.0f;
        Boolean ho = hoverOnlyConfig.get();
        cachedHoverOnly = ho != null ? ho : false;
        Boolean fta = filterTargetAirConfig.get();
        cachedFilterTargetAir = fta != null ? fta : true;
        Boolean ftg = filterTargetGroundConfig.get();
        cachedFilterTargetGround = ftg != null ? ftg : true;
        Boolean owa = onlyWithAmmoConfig.get();
        cachedOnlyWithAmmo = owa != null ? owa : false;
        Boolean pf = proximityFilterConfig.get();
        cachedProximityFilter = pf != null ? pf : false;
        Float pr = proximityRadiusConfig.get();
        cachedProximityRadius = pr != null ? pr : 30f;
        Boolean dta = drawTurretRangeAllyConfig.get();
        cachedDrawTurretRangeAlly = dta != null ? dta : true;
        Boolean dte = drawTurretRangeEnemyConfig.get();
        cachedDrawTurretRangeEnemy = dte != null ? dte : true;
        Boolean dup = drawUnitRangePlayerConfig.get();
        cachedDrawUnitRangePlayer = dup != null ? dup : true;
        Boolean dua = drawUnitRangeAllyConfig.get();
        cachedDrawUnitRangeAlly = dua != null ? dua : true;
        Boolean due = drawUnitRangeEnemyConfig.get();
        cachedDrawUnitRangeEnemy = due != null ? due : true;
        Boolean duar = drawUnitAbilityRangeConfig.get();
        cachedDrawUnitAbilityRange = duar != null ? duar : true;
        Boolean dubr = drawUnitBuildRangeConfig.get();
        cachedDrawUnitBuildRange = dubr != null ? dubr : false;
        Boolean dumr = drawUnitMineRangeConfig.get();
        cachedDrawUnitMineRange = dumr != null ? dumr : false;
        Boolean dba = drawBlockRangeAllyConfig.get();
        cachedDrawBlockRangeAlly = dba != null ? dba : true;
        Boolean dbe = drawBlockRangeEnemyConfig.get();
        cachedDrawBlockRangeEnemy = dbe != null ? dbe : true;
        Boolean dsp = drawSpawnerRangeConfig.get();
        cachedDrawSpawnerRange = dsp != null ? dsp : true;
        Boolean dor = drawOverdriveRangeConfig.get();
        cachedDrawOverdriveRange = dor != null ? dor : true;
        Boolean dmr = drawMenderRangeConfig.get();
        cachedDrawMenderRange = dmr != null ? dmr : true;
        Boolean dpr = drawPointDefenseRangeConfig.get();
        cachedDrawPointDefenseRange = dpr != null ? dpr : true;
        Boolean utc = useTeamColorForSpecialConfig.get();
        cachedUseTeamColorForSpecial = utc != null ? utc : false;
        Boolean dsh = dashedConfig.get();
        cachedDashed = dsh != null ? dsh : true;
        Float zt = zoomThresholdConfig.get();
        cachedZoomThreshold = zt != null ? zt : 0.5f;
        Boolean mc = mergedContourConfig.get();
        cachedMergedContour = mc != null ? mc : true;
        Boolean pw = pulseWaveConfig.get();
        cachedPulseWave = pw != null ? pw : true;
        Float fa = fillAlphaConfig.get();
        cachedFillAlpha = fa != null ? fa : 0.10f;
        Boolean dac = drawAttackConeConfig.get();
        cachedDrawAttackCone = dac != null ? dac : true;
        Boolean aca = attackConeAlwaysConfig.get();
        cachedAttackConeAlways = aca != null ? aca : false;
    }

    public static String blockSettingKey(Block block) {
        return "mindustrytool.features.range-display.block." + block.name;
    }

    public static String unitSettingKey(UnitType unitType) {
        return "mindustrytool.features.range-display.unit." + unitType.name;
    }

    public boolean isRangeUnit(@Nullable UnitType unitType) {
        return unitType != null && !unitType.internal && !unitType.isHidden() && unitType != UnitTypes.block;
    }

    public boolean isTurretBlock(Block block) {
        return block instanceof BaseTurret && !(block instanceof BuildTurret);
    }

    public boolean isSupportBlock(Block block) {
        return block instanceof OverdriveProjector
                || block instanceof MassDriver
                || block instanceof BuildTurret
                || block instanceof MendProjector
                || block instanceof RegenProjector
                || block instanceof ForceProjector
                || block instanceof RepairTower
                || block instanceof RepairTurret
                || block instanceof ShockwaveTower;
    }

    public boolean isRangeBlock(Block block) {
        return isTurretBlock(block) || isSupportBlock(block);
    }

    public boolean isOverdriveBlock(Block block) {
        return block instanceof OverdriveProjector;
    }

    public boolean isMenderBlock(Block block) {
        return block instanceof MendProjector
                || block instanceof RegenProjector
                || block instanceof RepairTower
                || block instanceof RepairTurret;
    }

    public boolean isPointDefenseBlock(Block block) {
        return block instanceof PointDefenseTurret
                || block instanceof ShockwaveTower;
    }

    public boolean isSpecialColorBlock(Block block) {
        return isOverdriveBlock(block) || isMenderBlock(block) || isPointDefenseBlock(block);
    }

    public void rebuildBitSet() {
        if (Vars.content == null) {
            return;
        }

        if (Vars.content.blocks() != null) {
            int max = Vars.content.blocks().size;
            BitSet bitSet = new BitSet(Math.max(max, 256));
            rangeBlocks.clear();

            for (Block block : Vars.content.blocks()) {
                if (block != null && isRangeBlock(block)) {
                    boolean enabled = Core.settings.getBool(blockSettingKey(block), true);
                    bitSet.set(block.id, enabled);
                    rangeBlocks.add(block);
                }
            }
            this.blockEnabled = bitSet;
        }

        if (Vars.content.units() != null) {
            int maxUnits = Vars.content.units().size;
            BitSet unitBitSet = new BitSet(Math.max(maxUnits, 256));

            for (UnitType unitType : Vars.content.units()) {
                if (unitType != null && isRangeUnit(unitType) && unitType.id >= 0) {
                    boolean enabled = Core.settings.getBool(unitSettingKey(unitType), true);
                    unitBitSet.set(unitType.id, enabled);
                }
            }
            this.unitEnabled = unitBitSet;
        }
    }

    public Signal<Boolean> getBlockSignal(Block block) {
        Signal<Boolean> signal = blockSignals.get(block.name);
        if (signal == null) {
            boolean initial = Core.settings.getBool(blockSettingKey(block), true);
            signal = Signal.of(initial);
            Effect.of(signal, enabled -> {
                boolean value = Boolean.TRUE.equals(enabled);
                Core.settings.put(blockSettingKey(block), value);
                if (blockEnabled == null && Vars.content != null && Vars.content.blocks() != null) {
                    rebuildBitSet();
                } else if (blockEnabled != null && block.id >= 0) {
                    blockEnabled.set(block.id, value);
                }
            });
            blockSignals.put(block.name, signal);
        }
        return signal;
    }

    public boolean isBlockEnabled(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        return blockEnabled != null && block.id >= 0
                ? blockEnabled.get(block.id)
                : Core.settings.getBool(blockSettingKey(block), true);
    }

    public void setBlockEnabled(@Nullable Block block, boolean enabled) {
        if (block == null) {
            return;
        }
        getBlockSignal(block).set(enabled);
    }

    public void setCategoryEnabled(boolean isTurret, boolean enabled) {
        if (Vars.content == null || Vars.content.blocks() == null) {
            return;
        }
        for (Block block : Vars.content.blocks()) {
            if (block == null) {
                continue;
            }
            if (isTurret ? isTurretBlock(block) : isSupportBlock(block)) {
                getBlockSignal(block).set(enabled);
            }
        }
    }

    public Signal<Boolean> getUnitSignal(UnitType unitType) {
        Signal<Boolean> signal = unitSignals.get(unitType.name);
        if (signal == null) {
            boolean initial = Core.settings.getBool(unitSettingKey(unitType), true);
            signal = Signal.of(initial);
            Effect.of(signal, enabled -> {
                boolean value = Boolean.TRUE.equals(enabled);
                Core.settings.put(unitSettingKey(unitType), value);
                if (unitEnabled == null && Vars.content != null && Vars.content.units() != null) {
                    rebuildBitSet();
                } else if (unitEnabled != null && unitType.id >= 0) {
                    unitEnabled.set(unitType.id, value);
                }
            });
            unitSignals.put(unitType.name, signal);
        }
        return signal;
    }

    public boolean isUnitEnabled(@Nullable UnitType unitType) {
        if (unitType == null) {
            return false;
        }
        return unitEnabled != null && unitType.id >= 0
                ? unitEnabled.get(unitType.id)
                : Core.settings.getBool(unitSettingKey(unitType), true);
    }

    public void setUnitEnabled(@Nullable UnitType unitType, boolean enabled) {
        if (unitType == null) {
            return;
        }
        getUnitSignal(unitType).set(enabled);
    }

    public void setAllUnitsEnabled(boolean enabled) {
        if (Vars.content == null || Vars.content.units() == null) {
            return;
        }
        for (UnitType unitType : Vars.content.units()) {
            if (isRangeUnit(unitType)) {
                getUnitSignal(unitType).set(enabled);
            }
        }
    }

    public void resetToDefaults() {
        opacityConfig.reset();
        strokeWidthConfig.reset();
        hoverOnlyConfig.reset();
        filterTargetAirConfig.reset();
        filterTargetGroundConfig.reset();
        onlyWithAmmoConfig.reset();
        proximityFilterConfig.reset();
        proximityRadiusConfig.reset();
        drawTurretRangeAllyConfig.reset();
        drawTurretRangeEnemyConfig.reset();
        drawUnitRangePlayerConfig.reset();
        drawUnitRangeAllyConfig.reset();
        drawUnitRangeEnemyConfig.reset();
        drawUnitAbilityRangeConfig.reset();
        drawUnitBuildRangeConfig.reset();
        drawUnitMineRangeConfig.reset();
        drawBlockRangeAllyConfig.reset();
        drawBlockRangeEnemyConfig.reset();
        drawSpawnerRangeConfig.reset();
        drawOverdriveRangeConfig.reset();
        drawMenderRangeConfig.reset();
        drawPointDefenseRangeConfig.reset();
        useTeamColorForSpecialConfig.reset();
        dashedConfig.reset();
        zoomThresholdConfig.reset();
        mergedContourConfig.reset();
        pulseWaveConfig.reset();
        fillAlphaConfig.reset();
        drawAttackConeConfig.reset();
        attackConeAlwaysConfig.reset();

        for (Entry<String, Signal<Boolean>> entry : blockSignals.entries()) {
            entry.value.set(true);
        }
        for (Entry<String, Signal<Boolean>> entry : unitSignals.entries()) {
            entry.value.set(true);
        }

        if (Vars.content != null && Vars.content.blocks() != null) {
            for (Block b : Vars.content.blocks()) {
                if (b != null && isRangeBlock(b)) {
                    Core.settings.remove(blockSettingKey(b));
                }
            }
        }
        if (Vars.content != null && Vars.content.units() != null) {
            for (UnitType u : Vars.content.units()) {
                if (u != null && isRangeUnit(u)) {
                    Core.settings.remove(unitSettingKey(u));
                }
            }
        }
        rebuildBitSet();
        syncConfigCache();
    }

    @Override
    public void onDisable() {
        contourRenderer.dispose();
    }

    @Override
    public @Nullable Prov<SolimDialog> getSettingDialog() {
        return () -> settingsDialog != null ? settingsDialog : (settingsDialog = new RangeDisplaySettingsDialog(this));
    }

    private void draw() {
        if (!isEnabled() || Vars.state == null || !Vars.state.isGame() || Vars.ui == null
                || Vars.ui.hudfrag == null || !Vars.ui.hudfrag.shown || Core.camera == null) {
            return;
        }

        if ((blockEnabled == null && Vars.content != null && Vars.content.blocks() != null)
                || (unitEnabled == null && Vars.content != null && Vars.content.units() != null)) {
            rebuildBitSet();
        }

        if (cachedOpacity <= 0.001f) {
            return;
        }

        if (cachedZoomThreshold > 0.01f && Vars.renderer != null && Vars.renderer.getScale() < cachedZoomThreshold) {
            return;
        }

        frameOpacity = cachedOpacity;
        frameStrokeWidth = cachedStrokeWidth;
        frameHoverOnly = cachedHoverOnly;
        frameFilterTargetAir = cachedFilterTargetAir;
        frameFilterTargetGround = cachedFilterTargetGround;
        frameOnlyWithAmmo = cachedOnlyWithAmmo;
        frameProximityFilter = cachedProximityFilter;
        float radTiles = cachedProximityRadius;
        frameProximityDistSq = (radTiles * Vars.tilesize) * (radTiles * Vars.tilesize);

        float mouseX = Core.input != null ? Core.input.mouseWorldX() : 0f;
        float mouseY = Core.input != null ? Core.input.mouseWorldY() : 0f;

        boolean hasValidPlayerUnit = Vars.player != null && Vars.player.unit() != null && Vars.player.unit().isValid();
        frameProximityX = hasValidPlayerUnit ? Vars.player.x : mouseX;
        frameProximityY = hasValidPlayerUnit ? Vars.player.y : mouseY;

        frameDrawTurretRangeAlly = cachedDrawTurretRangeAlly;
        frameDrawTurretRangeEnemy = cachedDrawTurretRangeEnemy;
        frameDrawUnitRangePlayer = cachedDrawUnitRangePlayer;
        frameDrawUnitRangeAlly = cachedDrawUnitRangeAlly;
        frameDrawUnitRangeEnemy = cachedDrawUnitRangeEnemy;
        frameDrawUnitAbilityRange = cachedDrawUnitAbilityRange;
        frameDrawUnitBuildRange = cachedDrawUnitBuildRange;
        frameDrawUnitMineRange = cachedDrawUnitMineRange;
        frameDrawBlockRangeAlly = cachedDrawBlockRangeAlly;
        frameDrawBlockRangeEnemy = cachedDrawBlockRangeEnemy;
        frameDrawSpawnerRange = cachedDrawSpawnerRange;
        frameDrawOverdriveRange = cachedDrawOverdriveRange;
        frameDrawMenderRange = cachedDrawMenderRange;
        frameDrawPointDefenseRange = cachedDrawPointDefenseRange;
        frameUseTeamColorForSpecial = cachedUseTeamColorForSpecial;
        frameDashed = cachedDashed;
        frameMergedContour = cachedMergedContour;
        framePulseWave = cachedPulseWave;
        frameFillAlpha = cachedFillAlpha;
        frameDrawAttackCone = cachedDrawAttackCone;
        frameAttackConeAlways = cachedAttackConeAlways;
        frameRendererScale = Vars.renderer != null ? Vars.renderer.getScale() : 1.0f;
        framePlayerTeam = Vars.player != null ? Vars.player.team() : null;

        contourRenderer.resetFrame();
        Core.camera.bounds(viewBounds);

        float z = Draw.z();
        Draw.z(Layer.overlayUI);

        if (frameDrawSpawnerRange && Vars.spawner != null && Vars.spawner.getSpawns() != null
                && Vars.state.rules != null) {
            float dropRadius = Vars.state.rules.dropZoneRadius;
            Color spawnerColor = Vars.state.rules.waveTeam != null ? Vars.state.rules.waveTeam.color : Color.scarlet;
            Seq<Tile> spawns = Vars.spawner.getSpawns();
            for (int i = 0; i < spawns.size; i++) {
                Tile tile = spawns.get(i);
                if (tile == null) {
                    continue;
                }
                float x = tile.worldx();
                float y = tile.worldy();
                if (isInViewBounds(x, y, dropRadius)) {
                    drawRangeShape(
                            RangeContourRenderer.LAYER_SPAWNER,
                            tile.pos(),
                            x,
                            y,
                            dropRadius,
                            -1,
                            spawnerColor);
                }
            }
        }

        boolean needHoverLookup = frameHoverOnly || (frameDrawAttackCone && !frameAttackConeAlways);
        Building hoveredBuilding = needHoverLookup && Vars.world != null
                ? Vars.world.buildWorld(mouseX, mouseY)
                : null;
        if (hoveredBuilding != null && isHiddenByFog(hoveredBuilding)) {
            hoveredBuilding = null;
        }
        Unit hoveredUnit = needHoverLookup && Groups.unit != null
                ? Units.closest(null, mouseX, mouseY, 32f, ANY_UNIT)
                : null;
        if (hoveredUnit != null && isHiddenByFog(hoveredUnit)) {
            hoveredUnit = null;
        }

        if (frameHoverOnly) {
            if (hoveredBuilding != null && RANGE_BUILDING_PREDICATE.get(hoveredBuilding)) {
                drawBuildingRange(hoveredBuilding);
            }

            if (frameDrawUnitRangePlayer && Vars.player != null && Vars.player.unit() != null) {
                drawUnitRange(Vars.player.unit());
            }

            if (hoveredUnit != null && (Vars.player == null || hoveredUnit != Vars.player.unit())) {
                drawUnitRange(hoveredUnit);
            }
        } else {
            if (frameDrawUnitRangeAlly || frameDrawUnitRangeEnemy || frameDrawUnitRangePlayer) {
                float margin = 1000f;
                float cx = Core.camera.position.x;
                float cy = Core.camera.position.y;
                float cw = Core.camera.width;
                float ch = Core.camera.height;
                Groups.unit.intersect(cx - cw / 2f - margin, cy - ch / 2f - margin, cw + margin * 2f, ch + margin * 2f,
                        unitDrawer);
            }

            if (frameDrawTurretRangeAlly || frameDrawTurretRangeEnemy
                    || frameDrawBlockRangeAlly || frameDrawBlockRangeEnemy) {
                float cx = Core.camera.position.x;
                float cy = Core.camera.position.y;
                float cw = Core.camera.width;
                float ch = Core.camera.height;
                float margin = 1400f;
                queryVisibleBuildings(cx - cw / 2f - margin, cy - ch / 2f - margin, cw + margin * 2f, ch + margin * 2f);
            }
        }

        if (frameDrawAttackCone && !frameAttackConeAlways) {
            if (hoveredBuilding instanceof TurretBuild
                    && isTurretAttackConeVisible((TurretBuild) hoveredBuilding)) {
                drawTurretAttackCone((TurretBuild) hoveredBuilding, hoveredBuilding.team.color);
            }
            if (hoveredUnit != null && hoveredUnit.isValid() && isUnitCategoryEnabled(hoveredUnit)
                    && isUnitEnabled(hoveredUnit.type) && canUnitAttackTarget(hoveredUnit)) {
                drawUnitAttackCone(hoveredUnit);
            }
            boolean altHeld = Core.input != null
                    && (Core.input.keyDown(KeyCode.altLeft) || Core.input.keyDown(KeyCode.altRight));
            if (altHeld && Vars.player != null && Vars.player.unit() != null
                    && Vars.player.unit().isValid() && Vars.player.unit() != hoveredUnit) {
                Unit playerUnit = Vars.player.unit();
                if (isUnitCategoryEnabled(playerUnit) && isUnitEnabled(playerUnit.type)
                        && canUnitAttackTarget(playerUnit)) {
                    drawUnitAttackCone(playerUnit);
                }
            }
        }

        Draw.z(z);
        Draw.reset();
    }

    private boolean isHiddenByFog(Building build) {
        return framePlayerTeam != null
                && build.team != framePlayerTeam
                && Vars.state != null
                && Vars.state.rules != null
                && Vars.state.rules.fog
                && build.inFogTo(framePlayerTeam);
    }

    private boolean isHiddenByFog(Unit unit) {
        return framePlayerTeam != null
                && unit.team != framePlayerTeam
                && Vars.state != null
                && Vars.state.rules != null
                && Vars.state.rules.fog
                && unit.inFogTo(framePlayerTeam);
    }

    private void queryVisibleBuildings(float rx, float ry, float rw, float rh) {
        if (Vars.state != null && Vars.state.teams != null && Vars.state.teams.present != null) {
            boolean wantAlly = frameDrawTurretRangeAlly || frameDrawBlockRangeAlly;
            boolean wantEnemy = frameDrawTurretRangeEnemy || frameDrawBlockRangeEnemy;
            Seq<TeamData> present = Vars.state.teams.present;

            if (!rangeBlocks.isEmpty()) {
                for (int i = 0; i < present.size; i++) {
                    TeamData data = present.get(i);
                    if (data == null || data.team == Team.derelict) {
                        continue;
                    }
                    boolean isAlly = framePlayerTeam != null && data.team == framePlayerTeam;
                    if (isAlly ? !wantAlly : !wantEnemy) {
                        continue;
                    }
                    for (int b = 0; b < rangeBlocks.size; b++) {
                        Block block = rangeBlocks.get(b);
                        if (blockEnabled != null && block.id >= 0 && !blockEnabled.get(block.id)) {
                            continue;
                        }
                        boolean isTurret = isTurretBlock(block);
                        if (isTurret
                                ? (isAlly ? !frameDrawTurretRangeAlly : !frameDrawTurretRangeEnemy)
                                : (isAlly ? !frameDrawBlockRangeAlly : !frameDrawBlockRangeEnemy)) {
                            continue;
                        }
                        if (isOverdriveBlock(block) && !frameDrawOverdriveRange
                                || isMenderBlock(block) && !frameDrawMenderRange
                                || isPointDefenseBlock(block) && !frameDrawPointDefenseRange) {
                            continue;
                        }
                        Seq<Building> builds = data.getBuildings(block);
                        for (int j = 0; j < builds.size; j++) {
                            drawBuildingRange(builds.get(j));
                        }
                    }
                }
                return;
            }

            for (int i = 0; i < present.size; i++) {
                TeamData data = present.get(i);
                if (data == null || data.team == Team.derelict || data.buildingTree == null) {
                    continue;
                }
                boolean isAlly = framePlayerTeam != null && data.team == framePlayerTeam;
                if (isAlly ? !wantAlly : !wantEnemy) {
                    continue;
                }
                data.buildingTree.intersect(rx, ry, rw, rh, buildingDrawer);
            }
            return;
        }
        if (Vars.indexer != null && Core.camera != null) {
            float radius = Math.max(rw, rh) * 0.5f;
            Vars.indexer.eachBlock(null, Core.camera.position.x, Core.camera.position.y, radius,
                    RANGE_BUILDING_PREDICATE, buildingDrawer);
        }
    }

    private static int resolveTeamLayerId(@Nullable Team team) {
        if (team == null) {
            return 0;
        }
        return Mathf.clamp(team.id, 0, 255);
    }

    private boolean isInViewBounds(float x, float y, float range) {
        return x + range >= viewBounds.x && x - range <= viewBounds.x + viewBounds.width
                && y + range >= viewBounds.y && y - range <= viewBounds.y + viewBounds.height;
    }

    private boolean isUnitCategoryEnabled(Unit unit) {
        boolean isPlayer = Vars.player != null && unit == Vars.player.unit();
        if (isPlayer) {
            return frameDrawUnitRangePlayer;
        }
        boolean isAlly = framePlayerTeam != null && unit.team == framePlayerTeam;
        return isAlly ? frameDrawUnitRangeAlly : frameDrawUnitRangeEnemy;
    }

    private boolean isTurretAttackConeVisible(TurretBuild turretBuild) {
        if (!turretBuild.isValid() || turretBuild.team == Team.derelict || turretBuild.block == null
                || !isBlockEnabled(turretBuild.block)) {
            return false;
        }
        boolean isAlly = framePlayerTeam != null && turretBuild.team == framePlayerTeam;
        if (isAlly ? !frameDrawTurretRangeAlly : !frameDrawTurretRangeEnemy) {
            return false;
        }
        if (turretBuild.block instanceof Turret) {
            Turret turret = (Turret) turretBuild.block;
            boolean canHitAir = turret.targetAir && frameFilterTargetAir;
            boolean canHitGround = turret.targetGround && frameFilterTargetGround;
            return canHitAir || canHitGround;
        }
        return true;
    }

    private boolean canUnitAttackTarget(Unit unit) {
        if (unit.type == null) {
            return true;
        }
        boolean canHitAir = unit.type.targetAir && frameFilterTargetAir;
        boolean canHitGround = unit.type.targetGround && frameFilterTargetGround;
        return canHitAir || canHitGround;
    }

    private static boolean hasWeaponAttackRange(Unit unit) {
        float range = unit.range();
        if (range <= 0f) {
            return false;
        }
        if (unit.hasWeapons()) {
            return true;
        }
        return unit.type == null || range != unit.type.mineRange;
    }

    private float resolveUnitAbilityRange(Unit unit, @Nullable Ability ability) {
        if (ability instanceof RepairFieldAbility) {
            return frameDrawMenderRange ? ((RepairFieldAbility) ability).range : 0f;
        }
        if (ability instanceof ShieldRegenFieldAbility) {
            return frameDrawMenderRange ? ((ShieldRegenFieldAbility) ability).range : 0f;
        }
        if (ability instanceof EnergyFieldAbility) {
            return frameDrawMenderRange ? ((EnergyFieldAbility) ability).range : 0f;
        }
        if (ability instanceof StatusFieldAbility) {
            return frameDrawOverdriveRange ? ((StatusFieldAbility) ability).range : 0f;
        }
        if (ability instanceof ForceFieldAbility) {
            return unit.shield > 0f ? ((ForceFieldAbility) ability).radius : 0f;
        }
        if (ability instanceof SuppressionFieldAbility) {
            return ((SuppressionFieldAbility) ability).range;
        }
        return 0f;
    }

    private float estimateMaxUnitRange(Unit unit) {
        float max = 0f;
        if (hasWeaponAttackRange(unit) && canUnitAttackTarget(unit)) {
            max = Math.max(max, unit.range());
        }
        if (frameDrawUnitBuildRange && unit.type != null && unit.canBuild()) {
            max = Math.max(max, unit.type.buildRange);
        }
        if (frameDrawUnitMineRange && unit.type != null && unit.canMine()) {
            max = Math.max(max, unit.type.mineRange);
        }
        if (frameDrawUnitAbilityRange && unit.abilities != null) {
            Ability[] abilities = unit.abilities;
            for (int i = 0; i < abilities.length; i++) {
                float abilityRange = resolveUnitAbilityRange(unit, abilities[i]);
                if (abilityRange > max) {
                    max = abilityRange;
                }
            }
        }
        return max;
    }

    private void drawUnitAbilityRanges(Unit unit) {
        Ability[] abilities = unit.abilities;
        if (abilities == null) {
            return;
        }
        Color teamColor = unit.team.color;
        int teamLayerId = resolveTeamLayerId(unit.team);

        for (int i = 0; i < abilities.length; i++) {
            Ability ability = abilities[i];
            float abilityRange = resolveUnitAbilityRange(unit, ability);
            if (abilityRange <= 0f || !isInViewBounds(unit.x, unit.y, abilityRange)) {
                continue;
            }
            int edges = ability instanceof ForceFieldAbility
                    ? (((ForceFieldAbility) ability).sides > 0 ? ((ForceFieldAbility) ability).sides : 6)
                    : -1;
            Color color = teamColor;
            int layerId = teamLayerId;
            if (!frameUseTeamColorForSpecial) {
                if (ability instanceof RepairFieldAbility
                        || ability instanceof ShieldRegenFieldAbility
                        || ability instanceof EnergyFieldAbility) {
                    color = Pal.heal;
                    layerId = RangeContourRenderer.LAYER_MENDER;
                } else if (ability instanceof StatusFieldAbility) {
                    color = ORANGE;
                    layerId = RangeContourRenderer.LAYER_OVERDRIVE;
                }
            }
            drawRangeShape(layerId, unit.id * 31 + i, unit.x, unit.y, abilityRange, edges, color);
        }
    }

    private void drawUnitRange(Unit unit) {
        if (!unit.isValid()) {
            return;
        }
        if (unit.type != null && unitEnabled != null && unit.type.id >= 0 && !unitEnabled.get(unit.type.id)) {
            return;
        }

        boolean isPlayer = Vars.player != null && unit == Vars.player.unit();
        if (isPlayer) {
            if (!frameDrawUnitRangePlayer) {
                return;
            }
        } else {
            if (frameProximityFilter) {
                float dx = unit.x - frameProximityX;
                float dy = unit.y - frameProximityY;
                if (dx * dx + dy * dy > frameProximityDistSq) {
                    return;
                }
            }

            boolean isAlly = framePlayerTeam != null && unit.team == framePlayerTeam;
            if (isAlly ? !frameDrawUnitRangeAlly : !frameDrawUnitRangeEnemy) {
                return;
            }
        }

        float maxRange = estimateMaxUnitRange(unit);
        if (maxRange <= 0f || !isInViewBounds(unit.x, unit.y, maxRange)) {
            return;
        }

        if (isHiddenByFog(unit)) {
            return;
        }

        if (hasWeaponAttackRange(unit) && canUnitAttackTarget(unit)) {
            float weaponRange = unit.range();
            if (weaponRange > 0f && isInViewBounds(unit.x, unit.y, weaponRange)) {
                drawRangeShape(
                        resolveTeamLayerId(unit.team),
                        unit.id,
                        unit.x,
                        unit.y,
                        weaponRange,
                        -1,
                        unit.team.color);

                if (frameDrawAttackCone && frameAttackConeAlways) {
                    drawUnitAttackCone(unit);
                }
            }
        }

        if (frameDrawUnitAbilityRange && unit.abilities != null) {
            drawUnitAbilityRanges(unit);
        }

        if (frameDrawUnitBuildRange && unit.type != null && unit.canBuild() && unit.type.buildRange > 0f) {
            float buildRange = unit.type.buildRange;
            if (isInViewBounds(unit.x, unit.y, buildRange)) {
                Color color = frameUseTeamColorForSpecial ? unit.team.color : Pal.accent;
                int layerId = frameUseTeamColorForSpecial
                        ? resolveTeamLayerId(unit.team)
                        : RangeContourRenderer.LAYER_UNIT_BUILD;
                drawRangeShape(layerId, unit.id * 31 + 17, unit.x, unit.y, buildRange, -1, color);
            }
        }

        if (frameDrawUnitMineRange && unit.type != null && unit.canMine() && unit.type.mineRange > 0f) {
            float mineRange = unit.type.mineRange;
            if (isInViewBounds(unit.x, unit.y, mineRange)) {
                Color color = frameUseTeamColorForSpecial ? unit.team.color : Color.lightGray;
                int layerId = frameUseTeamColorForSpecial
                        ? resolveTeamLayerId(unit.team)
                        : RangeContourRenderer.LAYER_UNIT_MINE;
                drawRangeShape(layerId, unit.id * 31 + 23, unit.x, unit.y, mineRange, -1, color);
            }
        }
    }

    private static float estimateMaxRange(Building build) {
        if (build.block instanceof BaseTurret) {
            return ((BaseTurret) build.block).range;
        }
        if (build.block instanceof MassDriver) {
            return ((MassDriver) build.block).range;
        }
        if (build.block instanceof OverdriveProjector) {
            OverdriveProjector op = (OverdriveProjector) build.block;
            return op.range + op.phaseRangeBoost;
        }
        if (build.block instanceof ForceProjector) {
            return ((ForceProjector) build.block).radius + 64f;
        }
        if (build.block instanceof MendProjector) {
            return ((MendProjector) build.block).range;
        }
        if (build.block instanceof RegenProjector) {
            return ((RegenProjector) build.block).range * Vars.tilesize;
        }
        if (build.block instanceof RepairTower) {
            return ((RepairTower) build.block).range;
        }
        if (build.block instanceof RepairTurret) {
            return ((RepairTurret) build.block).repairRadius;
        }
        if (build.block instanceof ShockwaveTower) {
            return ((ShockwaveTower) build.block).range;
        }
        if (build.block instanceof BuildTurret) {
            return ((BuildTurret) build.block).range;
        }
        return 1000f;
    }

    private void drawBuildingRange(Building build) {
        if (!RANGE_BUILDING_PREDICATE.get(build)) {
            return;
        }

        if (blockEnabled != null && build.block.id >= 0 && !blockEnabled.get(build.block.id)) {
            return;
        }

        if (build.block instanceof LightBlock || build.block instanceof LogicBlock) {
            return;
        }

        Block block = build.block;
        if (isOverdriveBlock(block) && !frameDrawOverdriveRange
                || isMenderBlock(block) && !frameDrawMenderRange
                || isPointDefenseBlock(block) && !frameDrawPointDefenseRange) {
            return;
        }

        float maxRange = estimateMaxRange(build);
        if (!isInViewBounds(build.x, build.y, maxRange)) {
            return;
        }

        if (isHiddenByFog(build)) {
            return;
        }

        if (frameProximityFilter) {
            float dx = build.x - frameProximityX;
            float dy = build.y - frameProximityY;
            if (dx * dx + dy * dy > frameProximityDistSq) {
                return;
            }
        }

        boolean isTurret = isTurretBlock(build.block);
        boolean isAlly = framePlayerTeam != null && build.team == framePlayerTeam;

        if (isTurret) {
            if (isAlly ? !frameDrawTurretRangeAlly : !frameDrawTurretRangeEnemy) {
                return;
            }

            if (build.block instanceof Turret) {
                Turret t = (Turret) build.block;
                boolean canHitAir = t.targetAir && frameFilterTargetAir;
                boolean canHitGround = t.targetGround && frameFilterTargetGround;
                if (!canHitAir && !canHitGround) {
                    return;
                }
            }

            boolean hasAmmo = !(build instanceof TurretBuild) || ((TurretBuild) build).hasAmmo();
            if (frameOnlyWithAmmo && !hasAmmo) {
                return;
            }
        } else {
            if (isAlly ? !frameDrawBlockRangeAlly : !frameDrawBlockRangeEnemy) {
                return;
            }
        }

        float range = 0f;
        int shapeEdges = -1;

        if (build instanceof TurretBuild) {
            range = ((TurretBuild) build).range();
        } else if (build.block instanceof BaseTurret) {
            range = ((BaseTurret) build.block).range;
        } else if (build instanceof OverdriveBuild && build.block instanceof OverdriveProjector) {
            OverdriveBuild ob = (OverdriveBuild) build;
            OverdriveProjector op = (OverdriveProjector) build.block;
            range = op.range + ob.phaseHeat * op.phaseRangeBoost;
        } else if (build.block instanceof MassDriver) {
            range = ((MassDriver) build.block).range;
        } else if (build.block instanceof BuildTurret) {
            range = ((BuildTurret) build.block).range;
        } else if (build.block instanceof MendProjector) {
            range = ((MendProjector) build.block).range;
        } else if (build.block instanceof RegenProjector) {
            range = ((RegenProjector) build.block).range * Vars.tilesize;
            shapeEdges = 4;
        } else if (build instanceof ForceBuild && build.block instanceof ForceProjector) {
            ForceBuild fb = (ForceBuild) build;
            range = fb.broken ? 0f : fb.realRadius();
            shapeEdges = 6;
        } else if (build.block instanceof RepairTower) {
            range = ((RepairTower) build.block).range;
        } else if (build.block instanceof RepairTurret) {
            range = ((RepairTurret) build.block).repairRadius;
        } else if (build.block instanceof ShockwaveTower) {
            range = ((ShockwaveTower) build.block).range;
        }

        if (range <= 0f) {
            return;
        }

        float x = build.x;
        float y = build.y;
        if (!isInViewBounds(x, y, range)) {
            return;
        }

        Color color;
        int layerId;
        if (isTurret) {
            boolean canShoot = (!(build instanceof TurretBuild) || ((TurretBuild) build).hasAmmo())
                    && build.efficiency > 0f;
            if (!canShoot) {
                color = Color.gray;
                layerId = RangeContourRenderer.LAYER_NO_AMMO;
            } else {
                color = resolveSpecialColor(block, build.team.color);
                layerId = resolveBlockLayerId(block, build.team);
            }
        } else if (build.efficiency <= 0f && block.hasPower) {
            color = Color.gray;
            layerId = RangeContourRenderer.LAYER_NO_AMMO;
        } else {
            color = resolveSpecialColor(block, build.team.color);
            layerId = resolveBlockLayerId(block, build.team);
        }

        drawRangeShape(layerId, build.id, x, y, range, shapeEdges, color);

        float minRange = isTurret && build.block instanceof Turret ? ((Turret) build.block).minRange : 0f;
        if (minRange > 0f) {
            Draw.z(Layer.overlayUI);
            colorScratch.set(Color.scarlet).a(frameOpacity * 0.7f);
            Lines.stroke(frameStrokeWidth, colorScratch);
            int minLevel = RangeDrawUtils.resolveLevel(minRange, frameRendererScale);
            RangeDrawUtils.dashCircle(x, y, minRange, minLevel);
        }

        if (frameDrawAttackCone && frameAttackConeAlways && build instanceof TurretBuild) {
            drawTurretAttackCone((TurretBuild) build, color);
        }
    }

    private int resolveBlockLayerId(Block block, Team team) {
        if (!frameUseTeamColorForSpecial) {
            if (isOverdriveBlock(block)) {
                return RangeContourRenderer.LAYER_OVERDRIVE;
            }
            if (isMenderBlock(block)) {
                return RangeContourRenderer.LAYER_MENDER;
            }
            if (isPointDefenseBlock(block)) {
                return RangeContourRenderer.LAYER_POINT_DEFENSE;
            }
        }
        return resolveTeamLayerId(team);
    }

    private Color resolveSpecialColor(Block block, Color teamColor) {
        if (frameUseTeamColorForSpecial) {
            return teamColor;
        }

        if (isOverdriveBlock(block)) {
            return ORANGE;
        }

        if (isMenderBlock(block)) {
            return Pal.heal;
        }

        if (isPointDefenseBlock(block)) {
            return Color.white;
        }

        return teamColor;
    }

    private void drawRangeShape(
            int layerId,
            int entityId,
            float x,
            float y,
            float range,
            int shapeEdges,
            Color color) {
        if (frameMergedContour) {
            float polyRadius = shapeEdges == 4 ? range / Mathf.sqrt2 : range;
            float polyRot = shapeEdges == 4 ? 45f : 0f;
            float stroke = Math.max(1.5f, frameStrokeWidth * 2f);
            if (contourRenderer.drawMergedShape(
                    layerId,
                    entityId,
                    x,
                    y,
                    polyRadius,
                    shapeEdges,
                    polyRot,
                    color,
                    frameFillAlpha,
                    stroke,
                    frameOpacity,
                    framePulseWave,
                    frameRendererScale)) {
                return;
            }
        }

        Draw.z(Layer.overlayUI);
        if (shapeEdges <= 0) {
            drawCircle(x, y, range, color);
        } else if (shapeEdges == 4) {
            drawSquare(x, y, range, color);
        } else {
            drawPoly(x, y, shapeEdges, range, color);
        }
    }

    private void drawTurretAttackCone(TurretBuild turretBuild, Color color) {
        if (!(turretBuild.block instanceof Turret)) {
            return;
        }
        Turret turret = (Turret) turretBuild.block;
        float range = turretBuild.range();
        if (range <= 0f) {
            return;
        }
        float coneAngle = turret.shootCone;
        float dir = turretBuild.rotation;
        coneOffset1.set(turret.shootX, turret.shootY).rotate(dir - 90f);

        float pulseAlpha = (0.1f + Mathf.absin(8f, 0.15f)) * frameOpacity;
        Draw.z(Layer.light + 5f);
        colorScratch.set(color).a(pulseAlpha);
        Draw.color(colorScratch);
        RangeDrawUtils.circleFan(
                turretBuild.x + coneOffset1.x,
                turretBuild.y + coneOffset1.y,
                range,
                coneAngle * 2f,
                dir - coneAngle,
                36);
    }

    private void drawUnitAttackCone(Unit unit) {
        WeaponMount[] mounts = unit.mounts;
        if (mounts == null || mounts.length == 0) {
            return;
        }
        float pulseAlpha = (0.1f + Mathf.absin(8f, 0.15f)) * frameOpacity;
        Draw.z(Layer.light + 5f);
        colorScratch.set(unit.team.color).a(pulseAlpha);
        Draw.color(colorScratch);

        for (int i = 0; i < mounts.length; i++) {
            WeaponMount mount = mounts[i];
            if (mount == null || mount.weapon == null) {
                continue;
            }
            Weapon weapon = mount.weapon;
            BulletType bullet = weapon.bullet;
            if (bullet != null && !(bullet.collidesAir && frameFilterTargetAir)
                    && !(bullet.collidesGround && frameFilterTargetGround)) {
                continue;
            }
            float weaponRange = weapon.range();
            if (weaponRange <= 0f) {
                continue;
            }
            float coneAngle = weapon.shootCone;
            float weaponRot = mount.rotate ? mount.rotation : weapon.baseRotation;
            float dir = weaponRot + unit.rotation;
            coneOffset1.set(weapon.x, weapon.y).rotate(unit.rotation - 90f);
            coneOffset2.set(weapon.shootX, weapon.shootY).rotate(unit.rotation - 90f + weaponRot);
            coneOffset1.add(coneOffset2);

            RangeDrawUtils.circleFan(
                    unit.x + coneOffset1.x,
                    unit.y + coneOffset1.y,
                    weaponRange,
                    coneAngle * 2f,
                    dir - coneAngle,
                    36);
        }
    }

    private void drawCircle(float x, float y, float range, Color color) {
        int level = RangeDrawUtils.resolveLevel(range, frameRendererScale);
        colorScratch.set(Pal.gray).a(frameOpacity * 0.65f);
        Lines.stroke(frameStrokeWidth, colorScratch);
        if (frameDashed) {
            RangeDrawUtils.dashCircle(x, y - 1f, range, level);
        } else {
            RangeDrawUtils.lineCircle(x, y - 1f, range, level);
        }
        colorScratch.set(color).a(frameOpacity);
        Lines.stroke(frameStrokeWidth, colorScratch);
        if (frameDashed) {
            RangeDrawUtils.dashCircle(x, y, range, level);
        } else {
            RangeDrawUtils.lineCircle(x, y, range, level);
        }
    }

    private void drawPoly(float x, float y, int sides, float range, Color color) {
        colorScratch.set(Pal.gray).a(frameOpacity * 0.65f);
        Lines.stroke(frameStrokeWidth, colorScratch);
        Lines.poly(x, y - 1f, sides, range);
        colorScratch.set(color).a(frameOpacity);
        Lines.stroke(frameStrokeWidth, colorScratch);
        Lines.poly(x, y, sides, range);
    }

    private void drawSquare(float x, float y, float range, Color color) {
        colorScratch.set(color).a(frameOpacity);
        Lines.stroke(frameStrokeWidth, colorScratch);
        if (frameDashed) {
            Drawf.dashSquareBasic(x, y, range);
        } else {
            Lines.rect(x - range / 2f, y - range / 2f, range, range);
        }
    }
}
