package mindustrytool.features.rangedisplay.ui;

import static solim.UI.*;

import arc.Core;
import arc.func.Cons;
import arc.func.Func;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import java.util.Locale;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustrytool.components.SettingCard;
import mindustrytool.components.SettingKeybindCard;
import mindustrytool.components.SettingScaffold;
import mindustrytool.components.SettingSlider;
import mindustrytool.components.WebStyles;
import mindustrytool.features.rangedisplay.RangeDisplayFeature;
import solim.core.Component;
import solim.reactive.Signal;

public class RangeDisplaySettingsView extends Component {

    private static final Color DISABLED_CHIP_COLOR = new Color(1f, 1f, 1f, 0.35f);

    private final RangeDisplayFeature feature;
    private final Signal<Boolean> turretActiveOnMapOnly = Signal.of(false);
    private final Signal<Boolean> supportActiveOnMapOnly = Signal.of(false);
    private final Signal<Boolean> unitActiveOnMapOnly = Signal.of(false);

    public RangeDisplaySettingsView(RangeDisplayFeature feature) {
        this.feature = feature;
    }

    @Override
    protected Element build() {
        return SettingScaffold.build(() -> {
            headerCard();
            visualStylingCard();
            proximityAndFiltersCard();
            rangeTypesCard();
            turretsFilterCard();
            supportBlocksFilterCard();
            unitsFilterCard();
            keybindsCard();
            actionsCard();
        });
    }

    private void keybindsCard() {
        SettingKeybindCard.forFeature(feature);
    }

    private Component headerCard() {
        return SettingCard.header(feature, "RANGE");
    }

    private Component visualStylingCard() {
        return SettingCard.section(Icon.image,
                Core.bundle.get("feature.range-display.settings.section.visual", "Visual Styling"), () -> {

                    SettingSlider.of(
                            Core.bundle.get("feature.range-display.settings.opacity"),
                            feature.opacityConfig.signal(),
                            0.1f,
                            1.0f,
                            0.05f,
                            feature.opacityConfig.signal().map(v -> Math.round((v != null ? v : 1f) * 100) + "%"));

                    SettingSlider.of(
                            Core.bundle.get("feature.range-display.settings.stroke-width"),
                            feature.strokeWidthConfig.signal(),
                            1.0f,
                            3.0f,
                            0.5f,
                            feature.strokeWidthConfig.signal()
                                    .map(v -> String.format(Locale.US, "%.1fx", v != null ? v : 1f)));

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    SettingSlider.of(
                            Core.bundle.get("feature.range-display.settings.min-zoom"),
                            feature.zoomThresholdConfig.signal(),
                            0.0f,
                            5.0f,
                            0.1f,
                            feature.zoomThresholdConfig.signal().map(v -> {
                                float zoom = v != null ? v : 0.5f;
                                return zoom <= 0.01f
                                        ? Core.bundle.get("feature.range-display.settings.off", "Off")
                                        : String.format(Locale.US, "%.1fx", zoom);
                            }));

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.hover-only"),
                            feature.hoverOnlyConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.dashed"),
                            feature.dashedConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.merged-contour"),
                            feature.mergedContourConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.pulse-wave"),
                            feature.pulseWaveConfig.signal()).props(growX());

                    SettingSlider.of(
                            Core.bundle.get("feature.range-display.settings.fill-alpha"),
                            feature.fillAlphaConfig.signal(),
                            0.02f,
                            0.30f,
                            0.01f,
                            feature.fillAlphaConfig.signal()
                                    .map(v -> Math.round((v != null ? v : 0.10f) * 100) + "%"));

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-attack-cone"),
                            feature.drawAttackConeConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.attack-cone-always"),
                            feature.attackConeAlwaysConfig.signal()).props(growX());
                });
    }

    private Component proximityAndFiltersCard() {
        return SettingCard.section(Icon.filter,
                Core.bundle.get("feature.range-display.settings.section.filters", "Filters"), () -> {
                    checkbox(Core.bundle.get("feature.range-display.settings.proximity-filter"),
                            feature.proximityFilterConfig.signal()).props(growX());

                    SettingSlider.of(
                            Core.bundle.get("feature.range-display.settings.proximity-radius"),
                            feature.proximityRadiusConfig.signal(),
                            10f,
                            60f,
                            5f,
                            feature.proximityRadiusConfig.signal()
                                    .map(v -> Math.round(v != null ? v : 30f) + " tiles"));

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.filter-target-air"),
                            feature.filterTargetAirConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.filter-target-ground"),
                            feature.filterTargetGroundConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.only-with-ammo"),
                            feature.onlyWithAmmoConfig.signal()).props(growX());
                });
    }

    private Component rangeTypesCard() {
        return SettingCard.section(Icon.layers,
                Core.bundle.get("feature.range-display.settings.section.types", "Range Types"), () -> {
                    checkbox(Core.bundle.get("feature.range-display.settings.draw-player-unit"),
                            feature.drawUnitRangePlayerConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-ally-turrets"),
                            feature.drawTurretRangeAllyConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-enemy-turrets"),
                            feature.drawTurretRangeEnemyConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-ally-units"),
                            feature.drawUnitRangeAllyConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-enemy-units"),
                            feature.drawUnitRangeEnemyConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-unit-ability-range"),
                            feature.drawUnitAbilityRangeConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-unit-build-range"),
                            feature.drawUnitBuildRangeConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-unit-mine-range"),
                            feature.drawUnitMineRangeConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-ally-blocks"),
                            feature.drawBlockRangeAllyConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-enemy-blocks"),
                            feature.drawBlockRangeEnemyConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-spawners"),
                            feature.drawSpawnerRangeConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-overdrive-range"),
                            feature.drawOverdriveRangeConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-mender-range"),
                            feature.drawMenderRangeConfig.signal()).props(growX());

                    checkbox(Core.bundle.get("feature.range-display.settings.draw-point-defense-range"),
                            feature.drawPointDefenseRangeConfig.signal()).props(growX());

                    divider().props(color(WebStyles.Colors.SECTION_BORDER));

                    checkbox(Core.bundle.get("feature.range-display.settings.use-team-color-for-special"),
                            feature.useTeamColorForSpecialConfig.signal()).props(growX());
                });
    }

    private Component turretsFilterCard() {
        return filterCard(
                Icon.turret,
                Core.bundle.get("feature.range-display.settings.section.turrets", "Turrets"),
                () -> feature.setCategoryEnabled(true, true),
                () -> feature.setCategoryEnabled(true, false),
                turretActiveOnMapOnly,
                activeOnly -> getFilteredBlocks(true, activeOnly),
                Core.bundle.get("feature.range-display.settings.filter.empty-map", "No blocks on current map"),
                feature::getBlockSignal,
                block -> feature.setBlockEnabled(block, !feature.isBlockEnabled(block)));
    }

    private Component supportBlocksFilterCard() {
        return filterCard(
                Icon.defense,
                Core.bundle.get("feature.range-display.settings.section.support-blocks", "Support Blocks"),
                () -> feature.setCategoryEnabled(false, true),
                () -> feature.setCategoryEnabled(false, false),
                supportActiveOnMapOnly,
                activeOnly -> getFilteredBlocks(false, activeOnly),
                Core.bundle.get("feature.range-display.settings.filter.empty-map", "No blocks on current map"),
                feature::getBlockSignal,
                block -> feature.setBlockEnabled(block, !feature.isBlockEnabled(block)));
    }

    private Component unitsFilterCard() {
        return filterCard(
                Icon.units,
                Core.bundle.get("feature.range-display.settings.section.units", "Units"),
                () -> feature.setAllUnitsEnabled(true),
                () -> feature.setAllUnitsEnabled(false),
                unitActiveOnMapOnly,
                this::getFilteredUnits,
                Core.bundle.get("feature.range-display.settings.filter.empty-units", "No units on current map"),
                feature::getUnitSignal,
                unitType -> feature.setUnitEnabled(unitType, !feature.isUnitEnabled(unitType)));
    }

    private <T extends UnlockableContent> Component filterCard(
            Drawable headerIcon,
            String title,
            Runnable onSelectAll,
            Runnable onSelectNone,
            Signal<Boolean> activeOnMapOnly,
            Func<Boolean, Seq<T>> itemSupplier,
            String emptyMessage,
            Func<T, Signal<Boolean>> signalSupplier,
            Cons<T> onToggle) {
        return SettingCard.card().children(() -> {
            row().props(growX(), gap(unit(2)), center()).children(() -> {
                icon(headerIcon).props(size(unit(5)), prefWidth(unit(5)), prefHeight(unit(5)), color(Pal.accent));
                text(title).props(color(Pal.accent), growX(), left());

                row().props(gap(unit(1))).children(() -> {
                    button(Core.bundle.get("feature.range-display.settings.all", "All"), onSelectAll)
                            .props(style(WebStyles.outlineText()), height(unit(8)), prefHeight(unit(8)), padding(unit(1), unit(2.5f), unit(1), unit(2.5f)));

                    button(Core.bundle.get("feature.range-display.settings.none", "None"), onSelectNone)
                            .props(style(WebStyles.ghostText()), height(unit(8)), prefHeight(unit(8)), padding(unit(1), unit(2.5f), unit(1), unit(2.5f)));
                });
            });

            if (Vars.state != null && Vars.state.isGame()) {
                row().props(growX(), gap(unit(1.5f)), left()).children(() -> {
                    button(Core.bundle.get("feature.range-display.settings.filter.all", "All"),
                            () -> activeOnMapOnly.set(false))
                            .props(style(WebStyles.filterChipText()), checked(activeOnMapOnly.map(active -> !Boolean.TRUE.equals(active))));

                    button(Core.bundle.get("feature.range-display.settings.filter.active-on-map", "Active on Map"),
                            () -> activeOnMapOnly.set(true))
                            .props(style(WebStyles.filterChipText()), checked(activeOnMapOnly.map(active -> Boolean.TRUE.equals(active))));
                });
            }

            dynamic(activeOnMapOnly, activeOnly -> {
                Seq<T> items = itemSupplier.get(Boolean.TRUE.equals(activeOnly));
                if (items.isEmpty()) {
                    text(emptyMessage)
                            .props(color(WebStyles.Colors.GHOST_FG), margin(unit(2)));
                } else {
                    wrap().props(growX(), left(), gap(unit(1.5f))).children(() -> {
                        for (T item : items) {
                            contentChip(item, signalSupplier.get(item), () -> onToggle.get(item));
                        }
                    });
                }
            }).props(growX());
        });
    }

    private Seq<UnitType> getFilteredUnits(boolean activeOnly) {
        Seq<UnitType> result = new Seq<>();
        if (Vars.content == null || Vars.content.units() == null) {
            return result;
        }

        if (activeOnly) {
            ObjectSet<UnitType> activeUnits = new ObjectSet<>();
            if (Vars.state != null && Vars.state.isGame() && Groups.unit != null) {
                Groups.unit.each(u -> {
                    if (u != null && u.type != null) {
                        activeUnits.add(u.type);
                    }
                });
            }
            for (UnitType unitType : Vars.content.units()) {
                if (feature.isRangeUnit(unitType) && activeUnits.contains(unitType)) {
                    result.add(unitType);
                }
            }
            return result;
        }

        for (UnitType unitType : Vars.content.units()) {
            if (feature.isRangeUnit(unitType)) {
                result.add(unitType);
            }
        }
        return result;
    }

    private Seq<Block> getFilteredBlocks(boolean isTurret, boolean activeOnly) {
        Seq<Block> result = new Seq<>();
        if (Vars.content == null || Vars.content.blocks() == null) {
            return result;
        }

        if (activeOnly) {
            ObjectSet<Block> activeBlocks = new ObjectSet<>();
            if (Vars.state != null && Vars.state.isGame() && Groups.build != null) {
                Groups.build.each(b -> {
                    if (b != null && b.block != null) {
                        activeBlocks.add(b.block);
                    }
                });
            }
            for (Block block : Vars.content.blocks()) {
                if (block != null) {
                    boolean matches = isTurret ? feature.isTurretBlock(block) : feature.isSupportBlock(block);
                    if (matches && activeBlocks.contains(block)) {
                        result.add(block);
                    }
                }
            }
            return result;
        }

        for (Block block : Vars.content.blocks()) {
            if (block != null) {
                boolean matches = isTurret ? feature.isTurretBlock(block) : feature.isSupportBlock(block);
                if (matches) {
                    result.add(block);
                }
            }
        }
        return result;
    }

    private Component actionsCard() {
        return SettingCard.card().children(() -> {
            button(Core.bundle.get("feature.range-display.settings.reset", "Reset to Defaults"),
                    feature::resetToDefaults)
                    .props(style(WebStyles.outlineText()), height(unit(11)), prefHeight(unit(11)), growX());
        });
    }

    private void contentChip(UnlockableContent content, Signal<Boolean> signal, Runnable onToggle) {
        TextureRegion region = content.uiIcon != null ? content.uiIcon : content.fullIcon;
        Drawable drawable = region != null ? new TextureRegionDrawable(region) : Tex.clear;

        button()
                .props(style(WebStyles.filterChip()), size(unit(11)), prefWidth(unit(11)), prefHeight(unit(11)), padding(unit(1)), tooltip(content.localizedName), checked(signal))
                .onClick(onToggle)
                .children(() -> {
                    icon(drawable)
                            .props(size(unit(6)), prefWidth(unit(6)), prefHeight(unit(6)), color(signal.map(enabled -> Boolean.TRUE.equals(enabled) ? Color.white
                                    : DISABLED_CHIP_COLOR)));
                });
    }
}
