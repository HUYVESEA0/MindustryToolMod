package mindustrytool.features.ui.modmanager;

import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeImageButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextButtonStyle;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Interp;
import arc.scene.Element;
import arc.scene.actions.Actions;
import arc.scene.actions.TemporalAction;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Scaling;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.mod.Mods.LoadedMod;
import mindustry.ui.FileChooser;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrytool.components.FileIcon;
import mindustrytool.features.ui.modmanager.AttachableDialogBridge.AttachedEntry;
import solim.core.Disposable;
import solim.graphics.BlurHelper;
import solim.reactive.Effect;

public final class ModManagerDialog extends BaseDialog {

    private final @Nullable Runnable customOpenBrowser;
    private final @Nullable Runnable customOpenModpacks;
    private final Runnable onOpenVanillaMods;

    private @Nullable ModBrowserDialog browserDialog;
    private @Nullable ModPackerDialog packerDialog;
    private boolean switchingDialog = false;

    private String searchQuery = "";
    private boolean portraitMenuOpen = false;
    private boolean portraitSwitchExpanded = false;

    private final ObjectMap<LoadedMod, ModCard> cardCache = new ObjectMap<>();

    private @Nullable Table enabledListTable;
    private @Nullable Table disabledListTable;
    private @Nullable Table tipTable;
    private @Nullable Table switcherOverlayRoot;
    private @Nullable Disposable updatesEffect;

    public ModManagerDialog(Runnable onOpenVanillaMods) {
        this(null, null, onOpenVanillaMods);
    }

    public ModManagerDialog(
            @Nullable Runnable onOpenBrowser,
            @Nullable Runnable onOpenModpacks,
            Runnable onOpenVanillaMods) {
        super(bundle("mods", bundle("feature.mod-manager.name", "Mods")));
        this.customOpenBrowser = onOpenBrowser;
        this.customOpenModpacks = onOpenModpacks;
        this.onOpenVanillaMods = onOpenVanillaMods;

        name = "modManagerDialog";
        closeOnBack();
        BlurHelper.applyBackdropBlur(this, 10f);

        titleTable.row();
        ModStatusBadges.line(titleTable, Pal.accent, true, 3f).padTop(4f);

        titleTable.visibility = () -> !isChildDialogShown();
        cont.visibility = () -> !isChildDialogShown();

        fill(over -> {
            this.switcherOverlayRoot = over;
            over.visibility = () -> !isChildDialogShown();
        });

        shown(() -> {
            portraitMenuOpen = false;
            portraitSwitchExpanded = false;
            if (updatesEffect != null) {
                updatesEffect.dispose();
            }
            updatesEffect = Effect.of(ModService.getInstance().availableUpdates(), u -> refreshModLists());
            rebuildLayout();
            if (Core.app != null) {
                Core.app.post(() -> rebuildSwitcherOverlay(Core.graphics != null && Core.graphics.isPortrait()));
            }
            ModService.getInstance().notifyInstalledModsChanged();
            ModService.getInstance().checkInstalledModUpdates();
        });

        resized(this::rebuildLayout);

        hidden(() -> {
            if (updatesEffect != null) {
                updatesEffect.dispose();
                updatesEffect = null;
            }
            cardCache.clear();
            if (!switchingDialog && Vars.mods != null && Vars.mods.requiresReload() && Vars.ui != null) {
                Vars.ui.showConfirm(
                        bundle("mod.reloadrequired", "Reload required"),
                        bundle("mod.requiresrestart", "Relaunch"),
                        () -> Core.app.exit());
            }
        });

        rebuildLayout();
    }

    private boolean isChildDialogShown() {
        return (browserDialog != null && browserDialog.isShown())
                || (packerDialog != null && packerDialog.isShown());
    }

    public void rebuildLayout() {
        Cell<?> buttonsCell = getCell(buttons);
        boolean portrait = Core.graphics != null && Core.graphics.isPortrait();
        if (buttonsCell != null) {
            buttonsCell.padBottom(portrait ? 29f : 3f);
        }

        cont.clearChildren();

        Table main = new Table();
        if (portrait) {
            buildPortraitMain(main);
            cont.add(main).grow();
        } else {
            buildLandscapeMain(main);
            cont.add(main).fillX().growY();
        }

        main.update(() -> {
            if (tipTable != null && !tipTable.visible && Vars.mods != null && Vars.mods.requiresReload()) {
                tipTable.visible = true;
                tipTable.color.a = 0f;
                tipTable.clearChildren();
                tipTable.add(bundle("mod.reloadrequired", "Reload required")).color(Color.crimson);
                tipTable.actions(Actions.alpha(1f, 0.3f, Interp.pow3Out));
            }
        });

        rebuildSwitcherOverlay(portrait);
        refreshModLists();
    }

    private void rebuildSwitcherOverlay(boolean portrait) {
        if (switcherOverlayRoot == null) {
            return;
        }
        switcherOverlayRoot.clearChildren();
        if (portrait) {
            switcherOverlayRoot.bottom().add(buildPortraitSwitcher()).growX();
        } else {
            switcherOverlayRoot.top().left().table(t -> {
                t.top().left();
                t.add(buildLandscapeSwitcherTable(
                        this,
                        this::switchToDialog,
                        this.onOpenVanillaMods)).top().left();
            }).padTop(titleTable.getPrefHeight()).grow();
        }
    }

    private void buildLandscapeMain(Table main) {
        main.table(search -> {
            if (Icon.zoom != null) {
                search.image(Icon.zoom).size(64f).scaling(Scaling.fit);
            }
            TextField field = new TextField(searchQuery);
            field.setMessageText(bundle("search", "Search:"));
            field.changed(() -> {
                searchQuery = field.getText() != null ? field.getText().toLowerCase(Locale.ROOT) : "";
                refreshModLists();
            });
            search.add(field).growX();
        }).growX().fillY().padLeft(16f).padRight(16f);
        main.row();

        float paneWidth = resolveColumnPaneWidth();

        Table modsSplit = new Table(mods -> {
            mods.table(left -> {
                left.table(ModStatusBadges.grayUIAlpha(), list -> {
                    list.add(bundle("mod.enabled", "Enabled")).color(Pal.accent);
                    list.row();
                    ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                    list.row();
                    ScrollPane pane = list.top().pane(
                            Styles.smallPane != null ? Styles.smallPane : Styles.defaultPane,
                            it -> {
                                it.top();
                                this.enabledListTable = it;
                            })
                            .width(paneWidth)
                            .fillY()
                            .top()
                            .get();
                    pane.setForceScroll(false, true);
                    pane.setScrollingDisabled(true, false);
                }).fillX().growY();
            }).fillX().growY();

            ModStatusBadges.line(mods, Color.gray, false, 4f).padLeft(6f).padRight(6f);

            mods.table(right -> {
                right.table(ModStatusBadges.grayUIAlpha(), list -> {
                    list.add(bundle("mod.disabled", "Disabled")).color(Pal.accent);
                    list.row();
                    ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                    list.row();
                    ScrollPane pane = list.top().pane(
                            Styles.smallPane != null ? Styles.smallPane : Styles.defaultPane,
                            it -> {
                                it.top();
                                this.disabledListTable = it;
                            })
                            .width(paneWidth)
                            .fillY()
                            .top()
                            .get();
                    pane.setForceScroll(false, true);
                    pane.setScrollingDisabled(true, false);
                }).fillX().growY();
            }).fillX().growY();
        });

        Table tipOverlay = new Table(tip -> {
            tip.bottom().table(ModStatusBadges.grayUIAlpha(), t -> {
                this.tipTable = t;
                t.visible = false;
            }).fillY().growX().margin(8f);
        });

        main.stack(modsSplit, tipOverlay).grow();
        main.row();

        ModStatusBadges.line(main, Pal.gray, true, 4f).pad(-8f).padTop(6f).padBottom(6f);
        main.row();

        main.table(btns -> {
            btns.table(top -> {
                top.defaults().growX().height(54f).pad(4f);
                TextButtonStyle topStyle = safeTextButtonStyle(
                        Styles.flatBordert != null ? Styles.flatBordert : Styles.grayt);
                top.button(bundle("mods.browser", "Mod Browser"), Icon.planet, topStyle, 46f, this::openModBrowser).margin(8f);
                top.button(bundle("mod.import.file", "Import File"), Icon.file, topStyle, 46f, this::importModFile).margin(8f);
                top.button(bundle("mod.import.github", "Import From GitHub"), Icon.download, topStyle, 46f, this::showImportGithubDialog).margin(8f);
            }).growX().fillY().padBottom(6f);
            btns.row();
            btns.table(bot -> {
                bot.defaults().growX().height(62f).pad(4f);
                TextButtonStyle botStyle = safeTextButtonStyle(Styles.grayt);
                bot.button(bundle("back", "Back"), Icon.leftOpen, botStyle, 46f, this::hide);
                bot.button(bundle("hosts.refresh", "Refresh"), Icon.refresh, botStyle, 46f, this::refreshMods);
                bot.button(bundle("feature.mod-manager.packer.title", "Modpack Studio"), Icon.list, botStyle, 46f, this::openModPacker);
                bot.button(bundle("mods.openfolder", "Open Folder"), Icon.save, botStyle, 46f, this::openModFolder);
                bot.button(bundle("mods.guide", "Modding Guide"), Icon.link, botStyle, 46f, () -> Core.app.openURI(ModService.MOD_GUIDE_URL));
            }).growX().fillY();
        }).growX().fillY();
    }

    private void buildPortraitMain(Table main) {
        Table listContainer = new Table(ModStatusBadges.grayUIAlpha());
        listContainer.top().margin(6f);

        listContainer.table(search -> {
            if (Icon.zoom != null) {
                search.image(Icon.zoom).size(42f).scaling(Scaling.fit);
            }
            TextField field = new TextField(searchQuery);
            field.setMessageText(bundle("search", "Search:"));
            field.changed(() -> {
                searchQuery = field.getText() != null ? field.getText().toLowerCase(Locale.ROOT) : "";
                refreshModLists();
            });
            search.add(field).growX();
        }).growX().fillY().pad(4f).row();

        Table menuPaneTable = new Table();
        menuPaneTable.pane(Styles.smallPane != null ? Styles.smallPane : Styles.defaultPane, pane -> {
            pane.defaults().growX().fillY().pad(4f);
            TextButtonStyle grayStyle = safeTextButtonStyle(Styles.grayt);
            pane.button(bundle("mods.browser", "Mod Browser"), Icon.planet, grayStyle, 46f, this::openModBrowser).margin(4f).row();
            pane.button(bundle("mod.import.file", "Import File"), Icon.file, grayStyle, 46f, this::importModFile).margin(4f).row();
            pane.button(bundle("mod.import.github", "Import From GitHub"), Icon.download, grayStyle, 46f, this::showImportGithubDialog).margin(4f).row();
            ModStatusBadges.line(pane, Color.gray, true, 4f).padTop(6f).padBottom(6f);
            pane.row();
            pane.button(bundle("feature.mod-manager.packer.title", "Modpack Studio"), Icon.list, grayStyle, 46f, this::openModPacker).margin(4f).row();
            pane.button(bundle("mods.openfolder", "Open Folder"), Icon.save, grayStyle, 46f, this::openModFolder).margin(4f).row();
            pane.button(bundle("mods.guide", "Modding Guide"), Icon.link, grayStyle, 46f, () -> Core.app.openURI(ModService.MOD_GUIDE_URL)).margin(4f).row();
        }).growX().fillY().maxHeight(400f);

        Collapser menuCollapser = new Collapser(menuPaneTable, !portraitMenuOpen);
        menuCollapser.setDuration(0.3f);

        TextButtonStyle flatStyle = safeTextButtonStyle(Styles.flatt);
        listContainer.button(bundle("feature.mod-manager.name", "Menu"), Icon.menuSmall, flatStyle, () -> {
            portraitMenuOpen = !portraitMenuOpen;
            menuCollapser.setCollapsed(!portraitMenuOpen, true);
        }).growX().height(38f).margin(8f).row();

        listContainer.add(menuCollapser).growX().fillY().row();
        ModStatusBadges.line(listContainer, Pal.darkerGray, true, 4f);
        listContainer.row();

        ScrollPane mainScroll = listContainer.pane(
                Styles.smallPane != null ? Styles.smallPane : Styles.defaultPane,
                pane -> {
                    pane.table(en -> {
                        en.add(bundle("mod.enabled", "Enabled"))
                                .color(Pal.accent).left().growX().labelAlign(Align.left).row();
                        ModStatusBadges.line(en, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                        en.row();
                        en.top().table(enabled -> {
                            enabled.top();
                            this.enabledListTable = enabled;
                        }).growX().fillY().top();
                    }).margin(6f).growX().fillY().row();

                    pane.table(di -> {
                        di.add(bundle("mod.disabled", "Disabled"))
                                .color(Pal.accent).left().growX().labelAlign(Align.left).row();
                        ModStatusBadges.line(di, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                        di.row();
                        di.top().table(disabled -> {
                            disabled.top();
                            this.disabledListTable = disabled;
                        }).growX().fillY().top();
                    }).margin(6f).growX().fillY();
                }).growX().fillY().scrollX(false).scrollY(true).get();
        mainScroll.setForceScroll(false, true);

        Table tipOverlay = new Table(tip -> {
            tip.bottom().table(ModStatusBadges.grayUIAlpha(), t -> {
                this.tipTable = t;
                t.visible = false;
            }).fillY().growX().margin(8f);
        });

        main.stack(listContainer, tipOverlay).grow();
        main.row();
        ModStatusBadges.line(main, Pal.gray, true, 4f).pad(-8f).padTop(6f).padBottom(6f);
        main.row();
        main.table(but -> {
            TextButtonStyle grayStyle = safeTextButtonStyle(Styles.grayt);
            but.button(bundle("back", "Back"), Icon.leftOpen, grayStyle, 46f, this::hide)
                    .height(58f).pad(6f).growX();
            but.button(bundle("hosts.refresh", "Refresh"), Icon.refresh, grayStyle, 46f, this::refreshMods)
                    .height(58f).pad(6f).growX();
        }).growX().fillY();
    }

    public void refreshModLists() {
        if (enabledListTable != null) {
            enabledListTable.clearChildren();
        }
        if (disabledListTable != null) {
            disabledListTable.clearChildren();
        }

        if (Vars.mods == null) {
            return;
        }
        Seq<LoadedMod> raw = Vars.mods.list();
        if (raw == null) {
            return;
        }

        Map<String, String> updates = ModService.getInstance().availableUpdates().peek();
        String query = searchQuery != null ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";

        for (LoadedMod mod : raw) {
            if (mod == null || !matchesQuery(mod, query)) {
                continue;
            }
            String repo = ModService.resolveModRepo(mod);
            String updateVer = (repo != null && !repo.trim().isEmpty() && updates != null)
                    ? updates.get(repo.trim().toLowerCase(Locale.ROOT))
                    : null;
            int stat = ModStat.checkModStat(mod, updateVer != null);
            Table target = (mod.enabled() && ModStat.isValid(stat)) ? enabledListTable : disabledListTable;
            if (target != null) {
                ModCard card = obtainModCard(mod);
                card.refreshStatusState();
                target.add(card).growX().fillY().pad(4f).minWidth(0f);
                target.row();
            }
        }
    }

    private ModCard obtainModCard(LoadedMod mod) {
        ModCard cached = cardCache.get(mod);
        if (cached != null) {
            return cached;
        }
        ModCard created = new ModCard(mod, this::refreshModLists);
        cardCache.put(mod, created);
        return created;
    }

    private void openModBrowser() {
        if (customOpenBrowser != null) {
            customOpenBrowser.run();
            return;
        }
        if (browserDialog == null) {
            browserDialog = new ModBrowserDialog(() -> {
                cardCache.clear();
                refreshModLists();
            });
        }
        browserDialog.show();
    }

    private void openModPacker() {
        if (customOpenModpacks != null) {
            customOpenModpacks.run();
            return;
        }
        if (packerDialog == null) {
            packerDialog = new ModPackerDialog();
        }
        packerDialog.show();
    }

    private void refreshMods() {
        cardCache.clear();
        ModService.getInstance().fetchModListings(true, this::refreshModLists);
        ModService.getInstance().checkInstalledModUpdates();
        refreshModLists();
    }

    private void openModFolder() {
        if (Vars.modDirectory == null) {
            return;
        }
        Core.app.openFolder(Vars.modDirectory.file().getAbsolutePath());
    }

    private void importModFile() {
        FileChooser.open("zip", "jar")
                .submit(file -> {
                    if (file == null || !file.exists()) {
                        return;
                    }
                    try {
                        if (Vars.mods != null) {
                            Vars.mods.importMod(file);
                            cardCache.clear();
                            ModService.getInstance().notifyInstalledModsChanged();
                            refreshModLists();
                        }
                    } catch (Exception e) {
                        if (Vars.ui != null) {
                            Vars.ui.showException(e);
                        }
                    }
                });
    }

    private void showImportGithubDialog() {
        BaseDialog dlg = new BaseDialog(bundle("mod.import.github", "Import From GitHub"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            Label prompt = new Label(bundle("feature.mod-manager.github.prompt", "GitHub repository (owner/repo):"));
            prompt.setColor(Color.lightGray);
            body.add(prompt).growX().left().padBottom(6f).row();

            TextField repoField = new TextField("");
            repoField.setMessageText(bundle("feature.mod-manager.github.repo-placeholder", "owner/repo"));

            body.table(inp -> {
                inp.add(repoField).growX().fillY();
                inp.button(Icon.paste, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                    String clip = Core.app.getClipboardText();
                    if (clip != null) {
                        repoField.setText(clip.trim());
                    }
                }).size(48f);
            }).width(420f).padBottom(10f).row();

            body.button(
                    bundle("mod.import", "Import Mod"),
                    Icon.downloadSmall,
                    safeTextButtonStyle(Styles.flatt),
                    24f,
                    () -> {
                        String repo = ModService.parseGithubRepo(repoField.getText());
                        if (repo == null) {
                            if (Vars.ui != null) {
                                Vars.ui.showErrorMessage(bundle("invalid", "Invalid"));
                            }
                            return;
                        }
                        dlg.hide();
                        ModService.getInstance().installFromRepo(repo, false, () -> {
                            cardCache.clear();
                            refreshModLists();
                        });
                    }).width(420f).height(46f).row();
        });

        dlg.show();
    }

    private Table buildPortraitSwitcher() {
        Table wrapper = new Table();
        List<SwitcherItem> items = buildSwitcherItems(this, this::switchToDialog, this.onOpenVanillaMods, () -> {});

        Table listPanel = new Table(ModStatusBadges.grayUIAlpha());
        listPanel.top().left().margin(6f);
        BlurHelper.applyBackdropBlur(listPanel, 10f);

        Collapser listCollapser = new Collapser(listPanel, !portraitSwitchExpanded);
        listCollapser.setDuration(0.3f);

        Image arrowImage = new Image(Icon.upOpen);
        arrowImage.setOrigin(Align.center);
        arrowImage.setRotation(portraitSwitchExpanded ? 180f : 0f);

        for (SwitcherItem item : items) {
            Button btn = new Button(safeButtonStyle(Styles.cleart != null ? Styles.cleart : Styles.flatt));
            btn.left().margin(4f).marginRight(12f);
            if (item.icon != null) {
                btn.image(item.icon).size(46f).padRight(8f);
            }
            Label label = new Label(item.label);
            label.setColor(item.active ? Pal.accent : Color.white);
            btn.add(label).left().growX();
            btn.clicked(() -> {
                portraitSwitchExpanded = false;
                listCollapser.setCollapsed(true, true);
                item.onSelect.run();
            });
            listPanel.add(btn).growX().left().row();
        }

        Button toggleBtn = new Button(safeButtonStyle(Styles.flati != null ? Styles.flati : Styles.flatt));
        toggleBtn.add(arrowImage).size(20f);
        toggleBtn.clicked(() -> {
            portraitSwitchExpanded = !portraitSwitchExpanded;
            listCollapser.setCollapsed(!portraitSwitchExpanded, true);
            arrowImage.setOrigin(Align.center);
            arrowImage.clearActions();
            arrowImage.addAction(Actions.rotateTo(portraitSwitchExpanded ? 180f : 0f, 0.3f, Interp.pow3Out));
        });

        wrapper.add(toggleBtn).growX().height(32f).row();
        wrapper.add(listCollapser).growX().row();
        return wrapper;
    }

    static Table buildLandscapeSwitcherTable(
            @Nullable Dialog selfDialog,
            Cons<Runnable> onSwitchDialog,
            Runnable onOpenVanillaMods) {
        Table switcherTable = new Table();
        switcherTable.name = "mod-manager-switcher";
        switcherTable.top().left();

        List<HorizontalCollapser> collapsers = new ArrayList<>();
        Runnable collapseAll = () -> {
            for (HorizontalCollapser c : collapsers) {
                c.setCollapsed(true, true);
            }
        };
        Runnable expandAll = () -> {
            for (HorizontalCollapser c : collapsers) {
                c.setCollapsed(false, true);
            }
        };

        List<SwitcherItem> items = buildSwitcherItems(selfDialog, onSwitchDialog, onOpenVanillaMods, collapseAll);
        for (SwitcherItem item : items) {
            Button btn = new Button(safeButtonStyle(Styles.cleart != null ? Styles.cleart : Styles.flatt));
            btn.left().margin(0f);
            btn.table(iconBox -> {
                iconBox.left().margin(4f);
                if (item.icon != null) {
                    iconBox.image(item.icon).size(46f);
                }
            }).left();

            Table labelTable = new Table();
            labelTable.left().margin(4f).marginRight(8f);
            Label label = new Label(item.label);
            label.setColor(item.active ? Pal.accent : Color.white);
            labelTable.add(label).left();

            HorizontalCollapser collapser = new HorizontalCollapser(labelTable, true);
            collapser.setDuration(0.3f, Interp.pow3Out);
            collapsers.add(collapser);

            btn.add(collapser).left();
            btn.clicked(item.onSelect);

            btn.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    if (pointer == -1 && (fromActor == null || !fromActor.isDescendantOf(btn))) {
                        expandAll.run();
                    }
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    if (pointer == -1 && (toActor == null || !toActor.isDescendantOf(switcherTable))) {
                        collapseAll.run();
                    }
                }
            });

            switcherTable.add(btn).growX().left().row();
        }

        switcherTable.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                if (pointer == -1 && (fromActor == null || !fromActor.isDescendantOf(switcherTable))) {
                    expandAll.run();
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                if (pointer == -1 && (toActor == null || !toActor.isDescendantOf(switcherTable))) {
                    collapseAll.run();
                }
            }
        });

        return switcherTable;
    }

    private static List<SwitcherItem> buildSwitcherItems(
            @Nullable Dialog selfDialog,
            Cons<Runnable> onSwitchDialog,
            Runnable onOpenVanillaMods,
            Runnable onCollapseSwitcher) {
        List<SwitcherItem> items = new ArrayList<>();
        List<AttachedEntry> attached = Vars.ui != null
                ? AttachableDialogBridge.getEntries(Vars.ui.mods)
                : Collections.emptyList();

        if (!attached.isEmpty()) {
            for (AttachedEntry entry : attached) {
                boolean isSelf = entry.dialog == selfDialog;
                Runnable action = isSelf
                        ? onCollapseSwitcher
                        : () -> {
                            onCollapseSwitcher.run();
                            onSwitchDialog.get(() -> {
                                if (Core.scene != null) {
                                    entry.dialog.show(Core.scene);
                                } else {
                                    entry.dialog.show();
                                }
                            });
                        };
                items.add(new SwitcherItem(
                        "dialog:" + System.identityHashCode(entry.dialog),
                        entry.icon,
                        entry.description,
                        isSelf,
                        action));
            }
        } else {
            items.add(new SwitcherItem(
                    "self",
                    resolveSelfIcon(),
                    bundle("feature.mod-manager.name", "Mindustry Tool"),
                    true,
                    onCollapseSwitcher));
        }

        items.add(new SwitcherItem(
                "vanilla",
                Icon.left,
                bundle("back", "Back"),
                false,
                () -> {
                    onCollapseSwitcher.run();
                    onSwitchDialog.get(onOpenVanillaMods);
                }));
        return items;
    }

    private static Drawable resolveSelfIcon() {
        try {
            return FileIcon.of("mindustrytool.png");
        } catch (Throwable ignored) {
            return Icon.book;
        }
    }

    private void switchToDialog(Runnable action) {
        switchingDialog = true;
        try {
            hide(null);
            if (action != null) {
                action.run();
            }
        } finally {
            if (Core.app != null) {
                Core.app.post(() -> switchingDialog = false);
            } else {
                switchingDialog = false;
            }
        }
    }

    private static float resolveColumnPaneWidth() {
        return Core.graphics != null && Core.graphics.getWidth() > 0
                ? Core.graphics.getWidth() / 2.8f / Math.max(0.01f, Scl.scl())
                : 520f;
    }

    private static boolean matchesQuery(LoadedMod mod, String lowerQuery) {
        if (lowerQuery.isEmpty()) {
            return true;
        }
        String internalName = mod.name != null ? mod.name.toLowerCase(Locale.ROOT) : "";
        String dispName = mod.meta != null && mod.meta.displayName != null
                ? mod.meta.displayName.toLowerCase(Locale.ROOT)
                : "";
        return internalName.contains(lowerQuery) || dispName.contains(lowerQuery);
    }

    private static final class SwitcherItem {
        final String key;
        final @Nullable Drawable icon;
        final String label;
        final boolean active;
        final Runnable onSelect;

        SwitcherItem(
                String key,
                @Nullable Drawable icon,
                String label,
                boolean active,
                Runnable onSelect) {
            this.key = key;
            this.icon = icon;
            this.label = label;
            this.active = active;
            this.onSelect = onSelect;
        }
    }

    static final class HorizontalCollapser extends WidgetGroup {

        private final Table table;
        private boolean collapsed;
        private boolean actionRunning = false;
        private float currentWidth = 0f;
        private final CollapseWidthAction collapseAction = new CollapseWidthAction();

        HorizontalCollapser(Table table, boolean collapsed) {
            this.table = table;
            this.collapsed = collapsed;
            setTransform(true);
            updateTouchable();
            addChild(table);
        }

        void setDuration(float seconds, Interp interpolation) {
            collapseAction.setDuration(seconds);
            collapseAction.setInterpolation(interpolation);
        }

        void setCollapsed(boolean collapse, boolean animate) {
            if (this.collapsed == collapse && !actionRunning) {
                return;
            }
            this.collapsed = collapse;
            updateTouchable();
            if (animate) {
                actionRunning = true;
                removeAction(collapseAction);
                addAction(collapseAction);
                collapseAction.restart();
            } else {
                actionRunning = false;
                clearActions();
                currentWidth = collapse ? 0f : table.getPrefWidth();
                invalidateHierarchy();
            }
        }

        private void updateTouchable() {
            this.touchable = collapsed ? Touchable.disabled : Touchable.enabled;
        }

        @Override
        public void draw() {
            if (currentWidth > 1f && getHeight() > 1f) {
                Draw.flush();
                if (clipBegin(x, y, currentWidth, getHeight())) {
                    super.draw();
                    Draw.flush();
                    clipEnd();
                }
            }
        }

        @Override
        protected void drawChildren() {
            if (collapsed && !actionRunning) {
                return;
            }
            super.drawChildren();
        }

        @Override
        public void layout() {
            float prefW = table.getPrefWidth();
            table.setBounds(0f, 0f, prefW, getHeight());
            if (!actionRunning) {
                currentWidth = collapsed ? 0f : prefW;
            }
        }

        @Override
        public float getPrefWidth() {
            return actionRunning ? currentWidth : (collapsed ? 0f : table.getPrefWidth());
        }

        @Override
        public float getPrefHeight() {
            return table.getPrefHeight();
        }

        @Override
        public float getMinWidth() {
            return 0f;
        }

        @Override
        public float getMinHeight() {
            return 0f;
        }

        private final class CollapseWidthAction extends TemporalAction {
            @Override
            protected void begin() {
                actionRunning = true;
            }

            @Override
            protected void update(float percent) {
                float target = table.getPrefWidth();
                currentWidth = collapsed ? (1f - percent) * target : percent * target;
                invalidateHierarchy();
            }

            @Override
            protected void end() {
                actionRunning = false;
                currentWidth = collapsed ? 0f : table.getPrefWidth();
                invalidateHierarchy();
            }
        }
    }
}
