package mindustrytool.features.ui.modmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Font.FontData;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.EventListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputEvent.InputEventType;
import arc.scene.event.InputListener;
import arc.scene.ui.Dialog;
import arc.scene.ui.Dialog.DialogStyle;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import mindustry.gen.Icon;
import mindustry.mod.Mods.LoadedMod;
import mindustry.mod.Mods.ModMeta;
import mindustry.ui.Styles;
import mindustrytool.features.ui.modmanager.AttachableDialogBridge.AttachedEntry;
import mindustrytool.features.ui.modmanager.PackModel.ModEntry;
import mindustrytool.test.MindustryTestEnv;
import org.junit.jupiter.api.Test;

class ModManagerSolimReactivityTest extends MindustryTestEnv {

    @Test
    void modCardRendersAvailableUpdateBadgeInPureArcScene2D() {
        newScene();
        ModService service = ModService.getInstance();
        service.setAvailableUpdatesForTest(Collections.emptyMap());

        ModMeta meta = new ModMeta();
        meta.name = "sample-mod";
        meta.displayName = "Sample Mod";
        meta.version = "1.0.0";
        meta.author = "Tester";
        meta.description = "Sample description";
        meta.repo = "tester/sample-mod";

        LoadedMod mod = new LoadedMod(null, null, null, null, meta);
        AtomicInteger stateChanges = new AtomicInteger();

        ModCard cardWithoutUpdate = new ModCard(mod, stateChanges::incrementAndGet);
        assertNotNull(cardWithoutUpdate);

        List<String> textsBefore = collectLabelTexts(cardWithoutUpdate);
        assertTrue(containsText(textsBefore, "Sample Mod"));
        assertFalse(hasImageWithColor(cardWithoutUpdate, ModStatusBadges.LIGHT_BLUE));

        Map<String, String> updates = new HashMap<>();
        updates.put("tester/sample-mod", "2.0.0");
        service.setAvailableUpdatesForTest(updates);

        ModCard cardWithUpdate = new ModCard(mod, stateChanges::incrementAndGet);
        assertTrue(hasImageWithColor(cardWithUpdate, ModStatusBadges.LIGHT_BLUE));

        service.setAvailableUpdatesForTest(Collections.emptyMap());
    }

    @Test
    void remoteModCardRendersListingInPureArcScene2D() {
        newScene();
        if (Styles.defaultLabel != null
                && Styles.defaultLabel.font != null
                && Styles.defaultLabel.font.getData() != null
                && Styles.defaultLabel.font.getData().missingGlyph != null) {
            Styles.defaultLabel.font.getData().missingGlyph.xadvance = 10;
            Styles.defaultLabel.font.getData().missingGlyph.width = 10;
        }

        ModListing listing = new ModListing(
                "author/remote-mod-with-very-long-repository-path-name",
                "Remote Mod",
                "remote-mod",
                "Fast remote mod\nLine 2 should be stripped from summary\nLine 3\nLine 4",
                "RemoteAuthor",
                "2026-10-01",
                128,
                "146",
                false,
                true,
                "# Heading\nFull description of remote mod");

        RemoteModCard leftCard = new RemoteModCard(listing, () -> {});
        RemoteModCard rightCard = new RemoteModCard(listing, () -> {});
        assertNotNull(leftCard);

        Table grid = new Table();
        grid.top().left();
        float cardWidth = 500f;
        Table colLeft = new Table();
        colLeft.top().defaults().growX().fillY();
        colLeft.add(leftCard).growX().fillY().pad(4f).minWidth(0f).row();

        ModListing shortListing = new ModListing(
                "author/short",
                "V",
                "v",
                "",
                "A",
                "1.0",
                10,
                "146",
                false,
                false,
                "");
        RemoteModCard shortCard = new RemoteModCard(shortListing, () -> {});
        colLeft.add(shortCard).growX().fillY().pad(4f).minWidth(0f).row();

        ModListing longListing = new ModListing(
                "author/long",
                "Mindustry Tool",
                "mindustry-tool",
                "Mindustry Tool - The Ultimate In-Game Utility & Community Suite with a very long single line description that exceeds 900px unwrapped width",
                "Author",
                "2026-10-17T16:09:45Z",
                248,
                "146",
                false,
                true,
                "# Very Long Markdown\nThis is a very long markdown line that would exceed 1200px if unwrapped in details view.");
        RemoteModCard longCard = new RemoteModCard(longListing, () -> {});
        colLeft.add(longCard).growX().fillY().pad(4f).minWidth(0f).row();

        grid.add(colLeft).width(cardWidth).fillY().pad(6f);
        grid.add(rightCard).width(cardWidth).top().pad(4f).row();
        grid.setSize(1320f, 800f);
        grid.validate();

        assertEquals(cardWidth - 8f, shortCard.getWidth(), 1f);
        assertEquals(cardWidth - 8f, longCard.getWidth(), 1f);

        Element shortHeaderBtn = shortCard.getChildren().first();
        Element longHeaderBtn = longCard.getChildren().first();
        assertEquals(0f, shortHeaderBtn.x, 1f, "Short card inner button must start at x=0");
        assertEquals(shortCard.getWidth(), shortHeaderBtn.getWidth(), 1f, "Short card inner button must fill card width");
        assertEquals(0f, longHeaderBtn.x, 1f, "Long card inner button must not overflow to negative x");
        assertEquals(longCard.getWidth(), longHeaderBtn.getWidth(), 1f, "Long card inner button must not exceed card width");

        longHeaderBtn.fireClick();
        grid.validate();
        assertEquals(0f, longHeaderBtn.x, 1f, "Expanded card inner button must not overflow to negative x");
        assertEquals(longCard.getWidth(), longHeaderBtn.getWidth(), 1f, "Expanded card inner button must not exceed card width");

        List<String> texts = collectLabelTexts(leftCard);
        assertTrue(containsText(texts, "Remote Mod"));
        assertTrue(containsText(texts, "128"));
        assertTrue(containsText(texts, "Fast remote mod"));
        assertFalse(containsText(texts, "Line 2 should be stripped"));
    }

    @Test
    void attachableDialogBridgeRegistersSharedEntriesAndExecutesFallback() {
        FontData fontData = new FontData() {
            @Override
            public boolean hasGlyph(char ch) {
                return true;
            }
        };
        DialogStyle dialogStyle = new DialogStyle();
        dialogStyle.titleFont = new Font(fontData, new TextureRegion(), false);

        AtomicBoolean fallbackSeenOnShow = new AtomicBoolean(false);
        Dialog vanillaMods = new Dialog("Mods", dialogStyle) {
            @Override
            public Dialog show() {
                fallbackSeenOnShow.set(AttachableDialogBridge.isFallbackActive());
                return this;
            }
        };
        Dialog heliumDialog = new Dialog("Helium", dialogStyle) {
            @Override
            public Dialog show() {
                return this;
            }
        };
        Dialog mindustryToolDialog = new Dialog("Mindustry Tool", dialogStyle) {
            @Override
            public Dialog show() {
                return this;
            }
        };

        AtomicBoolean mtoolEnabled = new AtomicBoolean(true);
        AttachableDialogBridge.attach(vanillaMods, heliumDialog, Icon.wrench, "Helium", () -> true);
        AttachableDialogBridge.attach(vanillaMods, mindustryToolDialog, Icon.book, "Mindustry Tool", mtoolEnabled::get);

        List<AttachedEntry> entries = AttachableDialogBridge.getEntries(vanillaMods);
        assertEquals(2, entries.size());
        assertSame(mindustryToolDialog, entries.get(0).dialog);
        assertEquals("Mindustry Tool", entries.get(0).description);
        assertSame(heliumDialog, entries.get(1).dialog);
        assertEquals("Helium", entries.get(1).description);

        assertSame(mindustryToolDialog, AttachableDialogBridge.showFirstAttached(vanillaMods));

        mtoolEnabled.set(false);
        List<AttachedEntry> filteredEntries = AttachableDialogBridge.getEntries(vanillaMods);
        assertEquals(1, filteredEntries.size());
        assertSame(heliumDialog, filteredEntries.get(0).dialog);
        assertSame(heliumDialog, AttachableDialogBridge.showFirstAttached(vanillaMods));

        AttachableDialogBridge.openFallback(vanillaMods);
        assertTrue(fallbackSeenOnShow.get());
        assertFalse(AttachableDialogBridge.isFallbackActive());
    }

    @Test
    void modManagerDialogSwitcherRemainsCompactAndTogglesLabelsOnHover() {
        newScene();
        Table cont = new Table();
        cont.top().left();
        Table switcherTable = ModManagerDialog.buildLandscapeSwitcherTable(
                null,
                r -> {
                    if (r != null) {
                        r.run();
                    }
                },
                () -> {});
        cont.add(switcherTable).top().left();
        cont.setSize(1920f, 1080f);
        cont.validate();

        float collapsedWidth = switcherTable.getPrefWidth();
        assertTrue(collapsedWidth <= 60f, "Expected compact collapsed switcher width <= 60f but got " + collapsedWidth);
        assertTrue(switcherTable.getWidth() <= 60f, "Expected laid-out switcher width <= 60f in 1920px viewport but got " + switcherTable.getWidth());

        fireHoverEvent(switcherTable, true);
        cont.act(0.1f);
        cont.validate();
        float midExpandWidth = switcherTable.getPrefWidth();
        assertTrue(midExpandWidth > collapsedWidth);

        cont.act(0.3f);
        cont.validate();
        float expandedWidth = switcherTable.getPrefWidth();
        assertTrue(expandedWidth > midExpandWidth);
        assertTrue(switcherTable.getWidth() < 400f);
        assertTrue(collectLabelTexts(switcherTable).size() >= 2);

        fireHoverEvent(switcherTable, false);
        cont.act(0.1f);
        cont.validate();
        float midCollapseWidth = switcherTable.getPrefWidth();
        assertTrue(midCollapseWidth < expandedWidth && midCollapseWidth > collapsedWidth);

        cont.act(0.3f);
        cont.validate();
        assertEquals(collapsedWidth, switcherTable.getPrefWidth(), 0.5f);
        assertEquals(collapsedWidth, switcherTable.getWidth(), 0.5f);

        fireHoverEvent(switcherTable, true);
        cont.act(0.4f);
        cont.validate();
        assertEquals(expandedWidth, switcherTable.getPrefWidth(), 0.5f);
    }

    @Test
    void modPackerDialogRebuildsPureArcScene2DColumnsAndDrawers() {
        newScene();
        FontData fontData = new FontData() {
            @Override
            public boolean hasGlyph(char ch) {
                return true;
            }
        };
        DialogStyle dialogStyle = new DialogStyle();
        dialogStyle.titleFont = new Font(fontData, new TextureRegion(), false);
        Core.scene.addStyle(DialogStyle.class, dialogStyle);

        ModPackerDialog packer = new ModPackerDialog();
        packer.getModel().getMods().add(new ModEntry(
                "alpha-mod",
                "Alpha Mod",
                "1.2.0",
                "Alice",
                "alpha-mod.zip",
                "",
                "Alpha description",
                Collections.emptyList(),
                ModStat.ENABLED));
        packer.rebuild();

        List<String> texts = collectLabelTexts(packer.cont);
        assertTrue(containsText(texts, "Alpha Mod"));
    }

    private static boolean containsText(List<String> texts, String substring) {
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t != null && t.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    private static void fireHoverEvent(Element target, boolean enter) {
        InputEvent event = new InputEvent();
        event.listenerActor = target;
        event.targetActor = target;
        event.pointer = -1;
        event.type = enter ? InputEventType.enter : InputEventType.exit;
        for (EventListener listener : target.getListeners()) {
            if (listener instanceof InputListener) {
                InputListener input = (InputListener) listener;
                if (enter) {
                    input.enter(event, 0f, 0f, -1, null);
                } else {
                    input.exit(event, 0f, 0f, -1, null);
                }
            }
        }
    }

    private static List<String> collectLabelTexts(Element element) {
        List<String> result = new ArrayList<>();
        if (element instanceof Label) {
            CharSequence text = ((Label) element).getText();
            if (text != null) {
                result.add(text.toString());
            }
        }
        if (element instanceof Group) {
            for (Element child : ((Group) element).getChildren()) {
                result.addAll(collectLabelTexts(child));
            }
        }
        return result;
    }

    private static boolean hasImageWithColor(Element element, Color target) {
        if (element instanceof Image) {
            Color c = element.color;
            if (c != null
                    && Math.abs(c.r - target.r) < 0.01f
                    && Math.abs(c.g - target.g) < 0.01f
                    && Math.abs(c.b - target.b) < 0.01f) {
                return true;
            }
        }
        if (element instanceof Group) {
            for (Element child : ((Group) element).getChildren()) {
                if (hasImageWithColor(child, target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
