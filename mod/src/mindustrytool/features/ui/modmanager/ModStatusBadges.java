package mindustrytool.features.ui.modmanager;

import arc.Core;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Font.FontData;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button.ButtonStyle;
import arc.scene.ui.CheckBox.CheckBoxStyle;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Label;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.ScrollPane.ScrollPaneStyle;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField.TextFieldStyle;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.Scaling;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;

public final class ModStatusBadges {

    public static final Color LIGHT_BLUE = Color.valueOf("D3FDFF");

    private ModStatusBadges() {
    }

    public static String bundle(String key) {
        return bundle(key, key);
    }

    public static String bundle(String key, String fallback) {
        return Core.bundle != null ? Core.bundle.get(key, fallback) : fallback;
    }

    public static String bundleFormat(String key, Object... args) {
        if (Core.bundle != null && Core.bundle.has(key)) {
            return Core.bundle.format(key, args);
        }
        return args != null && args.length > 0 ? key + " " + args[0] : key;
    }

    public static Drawable grayUI() {
        return tintWhiteUi(Pal.darkerGray, 1f, Styles.grayPanel);
    }

    public static Drawable grayUIAlpha() {
        return tintWhiteUi(Pal.darkerGray, 0.7f, Styles.black6);
    }

    public static Drawable darkGrayUI() {
        return tintWhiteUi(Pal.darkestGray, 1f, Styles.black8);
    }

    public static Drawable darkGrayUIAlpha() {
        return tintWhiteUi(Pal.darkestGray, 0.7f, Styles.black6);
    }

    public static Drawable padGrayUIAlpha() {
        if (Tex.whiteui instanceof TextureRegionDrawable) {
            Color c = new Color(Pal.darkerGray);
            c.a = 0.7f;
            Drawable tinted = ((TextureRegionDrawable) Tex.whiteui).tint(c);
            tinted.setLeftWidth(8f);
            tinted.setRightWidth(8f);
            tinted.setTopHeight(8f);
            tinted.setBottomHeight(8f);
            return tinted;
        }
        return grayUIAlpha();
    }

    public static Drawable buttonSelectDrawable() {
        return Tex.buttonSelect != null ? Tex.buttonSelect : darkGrayUI();
    }

    private static Drawable tintWhiteUi(Color baseColor, float alpha, @Nullable Drawable fallback) {
        if (Tex.whiteui instanceof TextureRegionDrawable && baseColor != null) {
            Color c = new Color(baseColor);
            c.a = alpha;
            return ((TextureRegionDrawable) Tex.whiteui).tint(c);
        }
        return fallback != null ? fallback : Tex.whiteui;
    }

    public static ButtonStyle switchButStyle() {
        ButtonStyle style = new ButtonStyle();
        style.up = Styles.none;
        style.over = grayUIAlpha();
        style.down = grayUI();
        style.checked = grayUI();
        return style;
    }

    public static Cell<Image> line(Table table, Color color, boolean horizontal, float stroke) {
        Cell<Image> cell = table.image().color(color);
        if (horizontal) {
            cell.height(stroke).growX();
        } else {
            cell.width(stroke).growY();
        }
        return cell;
    }

    public static void addEventBlocker(Element element) {
        element.addListener(event -> {
            if (event != null) {
                event.stop();
            }
            return false;
        });
    }

    public static void buildStars(Table stars, int starCount) {
        stars.add(new Element() {
            @Override
            public void draw() {
                validate();
                if (Icon.starSmall == null) {
                    return;
                }
                Draw.color(Color.darkGray);
                Icon.starSmall.draw(
                        x - width * 0.2f,
                        y - height * 0.2f,
                        0f,
                        0f,
                        width,
                        height,
                        1.4f,
                        1.4f,
                        0f);
                Draw.color(Color.white);
                Icon.starSmall.draw(x, y, width, height);
                Draw.reset();
            }
        }).size(60f).pad(-16f);

        Label countLabel = Styles.outlineLabel != null
                ? new Label(String.valueOf(starCount), Styles.outlineLabel)
                : new Label(String.valueOf(starCount));
        countLabel.setFontScale(0.85f);
        stars.add(countLabel).bottom().padBottom(4f).padLeft(-2f);
    }

    private static void ensureDefaultButtonStyle() {
        if (Core.scene == null) {
            return;
        }
        try {
            Core.scene.getStyle(ButtonStyle.class);
        } catch (Throwable ignored) {
            Core.scene.addStyle(
                    ButtonStyle.class,
                    Styles.defaultb != null ? Styles.defaultb : new ButtonStyle());
        }
    }

    private static Font resolveFallbackFont() {
        if (Fonts.def != null) {
            return Fonts.def;
        }
        if (Core.scene != null) {
            try {
                LabelStyle labelStyle = Core.scene.getStyle(LabelStyle.class);
                if (labelStyle != null && labelStyle.font != null) {
                    return labelStyle.font;
                }
            } catch (Throwable ignored) {
            }
        }
        return new Font(new FontData(), new TextureRegion(), true);
    }

    public static ButtonStyle safeButtonStyle(@Nullable ButtonStyle preferred) {
        ensureDefaultButtonStyle();
        if (preferred != null) {
            return preferred;
        }
        if (Styles.defaultb != null) {
            return Styles.defaultb;
        }
        ButtonStyle fallback = new ButtonStyle();
        if (Core.scene != null) {
            try {
                return Core.scene.getStyle(ButtonStyle.class);
            } catch (Throwable ignored) {
                Core.scene.addStyle(ButtonStyle.class, fallback);
            }
        }
        return fallback;
    }

    public static TextButtonStyle safeTextButtonStyle(@Nullable TextButtonStyle preferred) {
        ensureDefaultButtonStyle();
        TextButtonStyle chosen = preferred != null ? preferred : Styles.defaultt;
        if (chosen != null) {
            if (chosen.font == null) {
                chosen.font = resolveFallbackFont();
            }
            return chosen;
        }
        TextButtonStyle fallback = new TextButtonStyle();
        fallback.font = resolveFallbackFont();
        if (Core.scene != null) {
            try {
                return Core.scene.getStyle(TextButtonStyle.class);
            } catch (Throwable ignored) {
                Core.scene.addStyle(TextButtonStyle.class, fallback);
            }
        }
        return fallback;
    }

    public static ImageButtonStyle safeImageButtonStyle(@Nullable ImageButtonStyle preferred) {
        ensureDefaultButtonStyle();
        if (preferred != null) {
            return preferred;
        }
        if (Styles.defaulti != null) {
            return Styles.defaulti;
        }
        return new ImageButtonStyle();
    }

    public static CheckBoxStyle safeCheckBoxStyle(@Nullable CheckBoxStyle preferred) {
        ensureDefaultButtonStyle();
        CheckBoxStyle chosen = preferred != null ? preferred : Styles.defaultCheck;
        if (chosen != null) {
            if (chosen.font == null) {
                chosen.font = resolveFallbackFont();
            }
            return chosen;
        }
        CheckBoxStyle fallback = new CheckBoxStyle();
        fallback.font = resolveFallbackFont();
        fallback.fontColor = Color.white;
        Drawable box = grayUI();
        fallback.checkboxOff = box;
        fallback.checkboxOn = box;
        fallback.checkboxOver = box;
        fallback.checkboxOffDisabled = box;
        fallback.checkboxOnDisabled = box;
        return fallback;
    }

    public static ScrollPaneStyle safeScrollPaneStyle(@Nullable ScrollPaneStyle preferred) {
        if (preferred != null) {
            return preferred;
        }
        if (Styles.smallPane != null) {
            return Styles.smallPane;
        }
        if (Styles.defaultPane != null) {
            return Styles.defaultPane;
        }
        ScrollPaneStyle fallback = new ScrollPaneStyle();
        if (Core.scene != null) {
            try {
                return Core.scene.getStyle(ScrollPaneStyle.class);
            } catch (Throwable ignored) {
                Core.scene.addStyle(ScrollPaneStyle.class, fallback);
            }
        }
        return fallback;
    }

    public static TextFieldStyle safeTextFieldStyle(@Nullable TextFieldStyle preferred) {
        if (preferred != null) {
            return preferred;
        }
        if (Styles.defaultField != null) {
            return Styles.defaultField;
        }
        TextFieldStyle fallback = new TextFieldStyle();
        fallback.font = safeTextButtonStyle(null).font;
        fallback.fontColor = Color.white;
        fallback.messageFont = fallback.font;
        fallback.messageFontColor = Color.gray;
        if (Core.scene != null) {
            try {
                return Core.scene.getStyle(TextFieldStyle.class);
            } catch (Throwable ignored) {
                Core.scene.addStyle(TextFieldStyle.class, fallback);
            }
        }
        return fallback;
    }

    public static void buildCornerIcons(
            Table parent,
            int stat,
            boolean isRemote,
            @Nullable String updateVersion,
            boolean isInstalled) {
        parent.top().right();
        parent.defaults().size(26f).pad(4f);

        if (isRemote) {
            if (isInstalled) {
                if (updateVersion != null && !updateVersion.isEmpty()) {
                    cornerIcon(
                            parent,
                            Icon.starSmall,
                            LIGHT_BLUE,
                            bundleFormat("feature.mod-manager.update.available", updateVersion));
                } else {
                    cornerIcon(
                            parent,
                            Icon.okSmall,
                            Pal.heal,
                            bundle("feature.mod-manager.status.installed", "Installed"));
                }
            }
        } else {
            if (updateVersion != null && !updateVersion.isEmpty()) {
                cornerIcon(
                        parent,
                        Icon.upSmall,
                        LIGHT_BLUE,
                        bundleFormat("feature.mod-manager.update.available", updateVersion));
            } else if (ModStat.isValid(stat) && ModStat.isEnabled(stat)) {
                cornerIcon(
                        parent,
                        Icon.okSmall,
                        Pal.heal,
                        bundle("mod.enabled", "Enabled"));
            }
            if (ModStat.isLocalFile(stat)) {
                cornerIcon(
                        parent,
                        Icon.fileSmall,
                        Color.white,
                        bundle("mod.import.file", "Local File"));
            }
        }

        buildModAttrIcons(parent, stat);

        if (!isRemote && !ModStat.isValid(stat)) {
            buildErrorIcons(parent, stat);
        }
    }

    public static void buildModAttrIcons(Table status, int stat) {
        if (ModStat.isJAR(stat)) {
            cornerIcon(
                    status,
                    Icon.terminal,
                    Pal.reactorPurple,
                    bundle("mod.jarwarn", "JAR Mod"));
        }
        if (ModStat.isJS(stat)) {
            jsBadge(status, bundle("feature.mod-manager.status.scripts", "Scripts"));
        }
        if (!ModStat.isClientOnly(stat)) {
            cornerIcon(
                    status,
                    Icon.hostSmall,
                    Pal.techBlue,
                    bundle("feature.mod-manager.status.multiplayer", "Multiplayer Compatible"));
        }

        if (ModStat.isDeprecated(stat)) {
            int minVer = ModStat.isJAR(stat) ? Vars.minJavaModGameVersion : Vars.minModGameVersion;
            cornerIcon(
                    status,
                    Icon.warningSmall,
                    Color.crimson,
                    bundleFormat("mod.requiresversion", minVer));
        } else if (ModStat.isUnsupported(stat)) {
            cornerIcon(
                    status,
                    Icon.warningSmall,
                    Color.crimson,
                    bundle("mod.incompatiblegame", "Outdated Game"));
        }
    }

    public static void buildErrorIcons(Table status, int stat) {
        if (ModStat.isLibMissing(stat)) {
            cornerIcon(
                    status,
                    Icon.layersSmall != null ? Icon.layersSmall : Icon.bookSmall,
                    Color.crimson,
                    bundle("mod.unmetdependencies", "Unmet Dependencies"));
        } else if (ModStat.isLibIncomplete(stat)) {
            cornerIcon(
                    status,
                    Icon.warningSmall,
                    Color.crimson,
                    bundle("feature.mod-manager.status.incomplete-deps", "Incomplete Dependencies"));
        } else if (ModStat.isLibCircleDepending(stat)) {
            cornerIcon(
                    status,
                    Icon.refresh,
                    Color.crimson,
                    bundle("feature.mod-manager.status.circular-deps", "Circular Dependencies"));
        }

        if (ModStat.isError(stat)) {
            cornerIcon(
                    status,
                    Icon.cancelSmall,
                    Color.crimson,
                    bundle("mod.erroredcontent", "Content Errors"));
        }
        if (ModStat.isBlackListed(stat)) {
            cornerIcon(
                    status,
                    Icon.infoCircle != null ? Icon.infoCircle : Icon.infoCircleSmall,
                    Color.crimson,
                    bundle("feature.mod-manager.status.blacklisted", "Unsupported"));
        }
    }

    public static void buildDetailedStatusRows(
            Table parent,
            int stat,
            @Nullable String updateVersion,
            boolean isRemote,
            boolean isInstalled) {
        parent.left().defaults().left();

        if (isRemote) {
            if (isInstalled) {
                if (updateVersion != null && !updateVersion.isEmpty()) {
                    statusLine(
                            parent,
                            Icon.starSmall,
                            bundleFormat("feature.mod-manager.update.available", updateVersion),
                            LIGHT_BLUE);
                } else {
                    statusLine(
                            parent,
                            Icon.okSmall,
                            bundle("feature.mod-manager.status.installed", "Installed"),
                            Pal.heal);
                }
            }
            buildModAttrList(parent, stat);
            buildModErrList(parent, stat);
        } else {
            if (updateVersion != null && !updateVersion.isEmpty()) {
                statusLine(
                        parent,
                        Icon.upSmall,
                        bundleFormat("feature.mod-manager.update.available", updateVersion),
                        LIGHT_BLUE);
            }
            if (ModStat.isLocalFile(stat)) {
                statusLine(
                        parent,
                        Icon.fileSmall,
                        bundle("mod.import.file", "Local File"),
                        Color.white);
            }
            buildModBasicStatus(parent, stat);
            buildModAttrList(parent, stat);
            buildModErrList(parent, stat);
        }
    }

    public static void buildModBasicStatus(Table status, int stat) {
        if (ModStat.isEnabled(stat) && ModStat.isValid(stat)) {
            statusLine(status, Icon.okSmall, bundle("mod.enabled", "Enabled"), Pal.heal);
        } else if (!ModStat.isEnabled(stat)) {
            statusLine(status, Icon.cancelSmall, bundle("mod.disabled", "Disabled"), Pal.gray);
        }
    }

    public static void buildModAttrList(Table status, int stat) {
        if (ModStat.isJAR(stat)) {
            statusLine(status, Icon.terminal, bundle("mod.jarwarn", "JAR Mod"), Pal.reactorPurple);
        }
        if (ModStat.isJS(stat)) {
            statusLineJs(status, bundle("feature.mod-manager.status.scripts", "Scripts"), Pal.accent);
        }
        if (!ModStat.isClientOnly(stat)) {
            statusLine(status, Icon.hostSmall, bundle("feature.mod-manager.status.multiplayer", "Multiplayer Compatible"), Pal.techBlue);
        }
    }

    public static void buildModErrList(Table status, int stat) {
        if (ModStat.isDeprecated(stat)) {
            int minVer = ModStat.isJAR(stat) ? Vars.minJavaModGameVersion : Vars.minModGameVersion;
            statusLine(
                    status,
                    Icon.warningSmall,
                    bundleFormat("mod.requiresversion", minVer),
                    Color.crimson);
        } else if (ModStat.isUnsupported(stat)) {
            statusLine(status, Icon.warningSmall, bundle("mod.incompatiblegame", "Outdated Game"), Color.crimson);
        }

        if (ModStat.isLibMissing(stat)) {
            statusLine(
                    status,
                    Icon.layersSmall != null ? Icon.layersSmall : Icon.bookSmall,
                    bundle("mod.unmetdependencies", "Unmet Dependencies"),
                    Color.crimson);
        } else if (ModStat.isLibIncomplete(stat)) {
            statusLine(status, Icon.warningSmall, bundle("feature.mod-manager.status.incomplete-deps", "Incomplete Dependencies"), Color.crimson);
        } else if (ModStat.isLibCircleDepending(stat)) {
            statusLine(
                    status,
                    Icon.rotateSmall != null ? Icon.rotateSmall : Icon.refreshSmall,
                    bundle("feature.mod-manager.status.circular-deps", "Circular Dependencies"),
                    Color.crimson);
        }

        if (ModStat.isError(stat)) {
            statusLine(status, Icon.cancelSmall, bundle("mod.erroredcontent", "Content Errors"), Color.crimson);
        }
        if (ModStat.isBlackListed(stat)) {
            statusLine(status, Icon.infoCircleSmall, bundle("feature.mod-manager.status.blacklisted", "Unsupported"), Color.crimson);
        }
    }

    public static void buildStatusChip(
            Table parent,
            String labelText,
            Color color,
            @Nullable String tooltipText) {
        Table chip = parent.table(Tex.whiteui, box -> {
            box.margin(2f, 6f, 2f, 6f);
            box.add(labelText).color(color).fontScale(0.75f);
        }).get();
        chip.setColor(color.r * 0.25f, color.g * 0.25f, color.b * 0.25f, 0.88f);
        attachTooltip(chip, tooltipText);
    }

    public static void attachTooltip(Element element, @Nullable String text) {
        if (text == null || text.isEmpty() || Styles.outlineLabel == null) {
            return;
        }
        element.addListener(new Tooltip(t -> t.table(padGrayUIAlpha(), tip -> tip.add(text, Styles.outlineLabel))));
    }

    public static void attachDynamicTooltip(Element element, Prov<String> textProvider) {
        if (Styles.outlineLabel == null) {
            return;
        }
        element.addListener(new Tooltip(t -> {
            Label lbl = new Label("", Styles.outlineLabel);
            lbl.update(() -> {
                String next = textProvider.get();
                if (next != null && !lbl.textEquals(next)) {
                    lbl.setText(next);
                }
            });
            t.table(padGrayUIAlpha(), tip -> tip.add(lbl));
        }));
    }

    private static void cornerIcon(Table parent, @Nullable Drawable drawable, Color color, String tooltipText) {
        Image img = new Image(drawable);
        img.setScaling(Scaling.fit);
        img.setColor(color);
        attachTooltip(img, tooltipText);
        parent.add(img).size(26f).pad(4f);
    }

    private static void jsBadge(Table parent, @Nullable String tooltipText) {
        Table box = parent.table(Tex.whiteui, t -> {
            t.center();
            t.add("JS").color(Color.black).fontScale(0.68f);
        }).size(26f).pad(4f).get();
        box.setColor(Pal.accent);
        attachTooltip(box, tooltipText);
    }

    private static void statusLine(Table parent, @Nullable Drawable drawable, String description, Color color) {
        parent.table(line -> {
            line.left();
            Image img = new Image(drawable);
            img.setScaling(Scaling.fit);
            img.setColor(color);
            line.add(img).size(26f).pad(4f);
            line.add(description, 0.85f).color(color);
        }).left().row();
    }

    private static void statusLineJs(Table parent, String description, Color color) {
        parent.table(line -> {
            line.left();
            jsBadge(line, null);
            line.add(description, 0.85f).color(color).padLeft(4f);
        }).left().row();
    }
}
