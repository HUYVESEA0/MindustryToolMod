package mindustrytool.features.ui.modmanager;

import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundleFormat;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeCheckBoxStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeImageButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextButtonStyle;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectMap.Entry;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Scaling;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.FileChooser;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrytool.features.ui.modmanager.LocalFavoritesStore.ConflictResolution;
import mindustrytool.features.ui.modmanager.LocalFavoritesStore.FavoriteFolder;
import solim.graphics.BlurHelper;

public final class ModBrowserDialog extends BaseDialog {

    private static final int PAGE_SIZE = 40;

    private final @Nullable Runnable onCloseCallback;

    private String searchQuery = "";
    private boolean orderDate = false;
    private boolean reverse = false;
    private boolean hideInvalid = true;
    private int visibleLimit = PAGE_SIZE;

    private final List<ModListing> filteredListings = new ArrayList<>();
    private final ObjectMap<ModListing, RemoteModCard> cardCache = new ObjectMap<>();

    private @Nullable Table scrollListRoot;

    public ModBrowserDialog() {
        this(null);
    }

    public ModBrowserDialog(@Nullable Runnable onCloseCallback) {
        super(bundle("mods.browser", "Mod Browser"));
        this.onCloseCallback = onCloseCallback;

        name = "modBrowserDialog";
        closeOnBack();
        BlurHelper.applyBackdropBlur(this, 10f);

        titleTable.row();
        ModStatusBadges.line(titleTable, Pal.accent, true, 3f).padTop(4f);

        shown(() -> {
            LocalFavoritesStore.getInstance().ensureLoaded();
            rebuildLayout();
            ModService.getInstance().fetchModListings(false, this::refreshBrowserContent);
        });

        resized(this::rebuildLayout);

        hidden(() -> {
            cardCache.clear();
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });

        rebuildLayout();
    }

    public void rebuildLayout() {
        LocalFavoritesStore.getInstance().ensureLoaded();
        cont.clearChildren();

        cont.table(main -> {
            main.top();
            main.table(top -> {
                if (Icon.zoom != null) {
                    top.image(Icon.zoom).size(64f).scaling(Scaling.fit);
                }
                TextField searchField = new TextField(searchQuery);
                searchField.setMessageText(bundle("search", "Search:"));
                searchField.changed(() -> {
                    searchQuery = searchField.getText() != null
                            ? searchField.getText().toLowerCase(Locale.ROOT)
                            : "";
                    visibleLimit = PAGE_SIZE;
                    refreshBrowserContent();
                });
                top.add(searchField).growX();

                ImageButton[] sortFieldBtnRef = new ImageButton[1];
                ImageButton sortFieldBtn = top.button(
                        orderDate ? Icon.list : Icon.star,
                        safeImageButtonStyle(Styles.emptyi),
                        32f,
                        () -> {
                            orderDate = !orderDate;
                            if (sortFieldBtnRef[0] != null) {
                                sortFieldBtnRef[0].getStyle().imageUp = orderDate ? Icon.list : Icon.star;
                            }
                            visibleLimit = PAGE_SIZE;
                            refreshBrowserContent();
                        }).size(48f).get();
                sortFieldBtnRef[0] = sortFieldBtn;
                ModStatusBadges.attachDynamicTooltip(
                        sortFieldBtn,
                        () -> bundle(orderDate ? "mods.browser.sortdate" : "mods.browser.sortstars"));

                ImageButton[] sortOrderBtnRef = new ImageButton[1];
                ImageButton sortOrderBtn = top.button(
                        reverse ? Icon.upOpen : Icon.downOpen,
                        safeImageButtonStyle(Styles.emptyi),
                        32f,
                        () -> {
                            reverse = !reverse;
                            if (sortOrderBtnRef[0] != null) {
                                sortOrderBtnRef[0].getStyle().imageUp = reverse ? Icon.upOpen : Icon.downOpen;
                            }
                            visibleLimit = PAGE_SIZE;
                            refreshBrowserContent();
                        }).size(48f).get();
                sortOrderBtnRef[0] = sortOrderBtn;
                ModStatusBadges.attachDynamicTooltip(
                        sortOrderBtn,
                        () -> bundle(reverse ? "feature.mod-manager.browser.sort-asc" : "feature.mod-manager.browser.sort-desc",
                                reverse ? "Sort ascending" : "Sort descending"));

                CheckBox hideInvalidCheck = new CheckBox(
                        bundle("feature.mod-manager.hide-incompatible", "Hide incompatible"),
                        safeCheckBoxStyle(Styles.defaultCheck));
                hideInvalidCheck.setChecked(hideInvalid);
                hideInvalidCheck.changed(() -> {
                    hideInvalid = hideInvalidCheck.isChecked();
                    visibleLimit = PAGE_SIZE;
                    refreshBrowserContent();
                });
                top.add(hideInvalidCheck).padLeft(6f);
            }).growX().padLeft(40f).padRight(40f);

            main.row();
            ModStatusBadges.line(main, Pal.accent, true, 4f).padTop(4f);
            main.row();

            Table list = new Table();
            list.top().defaults().fill();
            this.scrollListRoot = list;

            ScrollPane pane = Styles.smallPane != null
                    ? new ScrollPane(list, Styles.smallPane)
                    : new ScrollPane(list);
            pane.setScrollingDisabled(true, false);
            main.add(pane).growY().fillX();

            main.row();
            ModStatusBadges.line(main, Color.gray, true, 4f).padTop(6f).padBottom(6f);
            main.row();

            main.table(bot -> {
                bot.defaults().width(242f).height(62f).pad(6f);
                TextButtonStyle grayStyle = safeTextButtonStyle(Styles.grayt);
                bot.button(bundle("back", "Back"), Icon.leftOpen, grayStyle, 46f, this::hide);
                bot.button(
                        bundle("hosts.refresh", "Refresh"),
                        Icon.refresh,
                        grayStyle,
                        46f,
                        () -> refreshListingsFromNetwork(true));
                if (Core.graphics != null && Core.graphics.isPortrait()) {
                    bot.row();
                }
                bot.button(
                        bundle("import", "Import"),
                        Icon.download,
                        grayStyle,
                        46f,
                        this::showImportFolderDialog);
            }).growX().fillY();
        }).grow();

        refreshBrowserContent();
    }

    public void refreshBrowserContent() {
        if (scrollListRoot == null) {
            return;
        }
        scrollListRoot.clearChildren();

        int n = resolveColumnCount();
        float colWidth = resolveColumnWidth();

        recomputeFilteredListings();

        ObjectMap<FavoriteFolder, Table[]> folderColumns = new ObjectMap<>();
        buildFavoritesSection(scrollListRoot, n, colWidth, folderColumns);

        scrollListRoot.row();
        scrollListRoot.add(bundle("mods", "Mods")).color(Pal.accent).left().padLeft(26f);
        scrollListRoot.row();
        ModStatusBadges.line(scrollListRoot, Pal.accent, true, 4f).pad(6f).padLeft(20f).padRight(20f);
        scrollListRoot.row();

        List<ModListing> rawAll = ModService.getInstance().listings().peek();
        boolean isLoading = Boolean.TRUE.equals(ModService.getInstance().loading().peek());
        String err = ModService.getInstance().error().peek();

        if (isLoading && (rawAll == null || rawAll.isEmpty())) {
            Label loadingLbl = new Label(bundle("mods.browser.fetching", "Fetching Releases..."));
            loadingLbl.setColor(Pal.accent);
            scrollListRoot.add(loadingLbl).pad(24f).center().row();
            return;
        }

        if (err != null && !err.isEmpty() && (rawAll == null || rawAll.isEmpty())) {
            scrollListRoot.table(errBox -> {
                if (Icon.warning != null) {
                    errBox.image(Icon.warning).size(48f).pad(6f).color(Color.red);
                }
                Label errLbl = Styles.outlineLabel != null
                        ? new Label(bundleFormat("browser.error.download", err), Styles.outlineLabel)
                        : new Label(bundleFormat("browser.error.download", err));
                errBox.add(errLbl).padRight(12f);
                errBox.button(
                        bundle("button.retry", "Retry"),
                        safeTextButtonStyle(Styles.cleart != null ? Styles.cleart : Styles.flatt),
                        () -> refreshListingsFromNetwork(true)).margin(6f);
            }).center().pad(16f).row();
            return;
        }

        Table[] normCols = new Table[n];
        scrollListRoot.table(norm -> {
            for (int i = 0; i < n; i++) {
                Table col = new Table(ModStatusBadges.grayUIAlpha());
                col.top().defaults().growX().fillY();
                norm.add(col).width(colWidth).fillY().pad(6f);
                normCols[i] = col;
            }
        }).row();

        int count = Math.min(visibleLimit, filteredListings.size());
        for (int i = 0; i < count; i++) {
            ModListing listing = filteredListings.get(i);
            RemoteModCard card = obtainBrowserCard(listing);
            card.refreshState();
            normCols[i % n].add(card).growX().fillY().pad(4f).minWidth(0f).row();
        }

        for (Entry<FavoriteFolder, Table[]> entry : folderColumns.entries()) {
            FavoriteFolder folder = entry.key;
            Table[] cols = entry.value;
            int idx = 0;
            for (ModListing listing : filteredListings) {
                if (folder.contains(ModName.fromListing(listing))) {
                    RemoteModCard favCard = new RemoteModCard(
                            listing,
                            folder.getName(),
                            this::refreshBrowserContent);
                    cols[idx++ % n].add(favCard).growX().fillY().pad(4f).minWidth(0f).row();
                }
            }
        }

        if (filteredListings.size() > count) {
            int remaining = filteredListings.size() - count;
            scrollListRoot.button(
                    "+" + remaining + " (" + filteredListings.size() + " " + bundle("mods", "Mods") + ")",
                    Icon.downOpen,
                    safeTextButtonStyle(Styles.grayt),
                    32f,
                    () -> {
                        visibleLimit += PAGE_SIZE;
                        refreshBrowserContent();
                    }).growX().height(50f).padLeft(20f).padRight(20f).padTop(6f).row();
        }
    }

    public void refreshFavoritesPanel() {
        refreshBrowserContent();
    }

    public void refreshListingsOnly() {
        refreshBrowserContent();
    }

    private void buildFavoritesSection(
            Table list,
            int n,
            float colWidth,
            ObjectMap<FavoriteFolder, Table[]> folderColumns) {
        list.table(hdr -> {
            hdr.left();
            hdr.add(bundle("feature.section.favorites", "Favorites")).color(Pal.accent).left().growX();
            hdr.button(
                    bundle("add", "Add..."),
                    Icon.addSmall,
                    safeTextButtonStyle(Styles.flatt),
                    20f,
                    this::showCreateFolderDialog).height(36f).margin(6f);
        }).growX().padLeft(26f).padRight(20f);
        list.row();
        ModStatusBadges.line(list, Pal.accent, true, 4f).pad(6f).padLeft(20f).padRight(20f);
        list.row();

        List<FavoriteFolder> folders = LocalFavoritesStore.getInstance().folders().peek();
        if (folders == null || folders.isEmpty()) {
            list.table(tab -> {
                if (Icon.box != null) {
                    tab.image(Icon.box).size(46f).color(Pal.accent);
                }
                tab.add(bundle("none.found", "<none found>")).pad(36f).padLeft(12f);
            }).fill().row();
            return;
        }

        for (FavoriteFolder folder : folders) {
            folderColumns.put(folder, addFavoritesRow(list, folder, n, colWidth));
        }
    }

    private Table[] addFavoritesRow(Table list, FavoriteFolder folder, int n, float colWidth) {
        boolean expanded = !folder.isCollapsed();
        Table[] columns = new Table[n];

        Table colContent = new Table();
        colContent.top();
        for (int i = 0; i < n; i++) {
            Table c = new Table(ModStatusBadges.grayUIAlpha());
            c.top().defaults().growX().fillY();
            colContent.add(c).width(colWidth).fillY().pad(6f);
            columns[i] = c;
        }

        Collapser collapser = new Collapser(colContent, !expanded);
        collapser.setDuration(0.3f);

        Image[] arrowRef = new Image[1];

        list.button(t -> {
            Image arrow = new Image(expanded ? Icon.downOpen : Icon.rightOpen);
            arrowRef[0] = arrow;
            t.add(arrow).size(28f).pad(4f);

            Label nameLbl = new Label(folder.getName());
            nameLbl.setColor(Pal.accent);
            nameLbl.setWrap(true);
            nameLbl.setEllipsis(true);
            nameLbl.setAlignment(Align.left);
            t.add(nameLbl).pad(4f).growX().minWidth(0f);

            t.table(actions -> {
                actions.defaults().size(44f).pad(2f);

                ImageButton editBtn = actions.button(
                        Icon.edit,
                        safeImageButtonStyle(Styles.clearNonei),
                        32f,
                        () -> showRenameFolderDialog(folder)).get();
                ModStatusBadges.attachTooltip(editBtn, bundle("save.rename", "Rename"));

                ImageButton trashBtn = actions.button(
                        Icon.trash,
                        safeImageButtonStyle(Styles.clearNonei),
                        32f,
                        () -> confirmDeleteFolder(folder)).get();
                ModStatusBadges.attachTooltip(trashBtn, bundle("delete", "Delete"));

                ImageButton installAllBtn = actions.button(
                        Icon.download,
                        safeImageButtonStyle(Styles.clearNonei),
                        32f,
                        () -> batchInstallFolder(folder)).get();
                ModStatusBadges.attachTooltip(installAllBtn, bundle("mod.dependencies.downloadall", "Import All"));

                ImageButton exportBtn = actions.button(
                        Icon.export,
                        safeImageButtonStyle(Styles.clearNonei),
                        32f,
                        () -> showExportFolderDialog(folder)).get();
                ModStatusBadges.attachTooltip(exportBtn, bundle("export", "Export"));

                ModStatusBadges.addEventBlocker(actions);
            }).pad(4f).right();
        }, safeButtonStyle(Styles.grayt), () -> {
            LocalFavoritesStore.getInstance().toggleCollapsed(folder.getName());
            collapser.toggle(true);
            if (arrowRef[0] != null) {
                arrowRef[0].setDrawable(collapser.isCollapsed() ? Icon.rightOpen : Icon.downOpen);
            }
        }).growX().fillY().minWidth(0f).padTop(4f).padLeft(20f).padRight(20f);

        list.row();
        list.add(collapser).growX().fillY().minWidth(0f).padLeft(20f).padRight(20f);
        list.row();

        return columns;
    }

    private RemoteModCard obtainBrowserCard(ModListing listing) {
        RemoteModCard cached = cardCache.get(listing);
        if (cached != null) {
            return cached;
        }
        RemoteModCard created = new RemoteModCard(listing, this::refreshBrowserContent);
        cardCache.put(listing, created);
        return created;
    }

    private void refreshListingsFromNetwork(boolean force) {
        cardCache.clear();
        ModService.getInstance().fetchModListings(force, this::refreshBrowserContent);
        refreshBrowserContent();
    }

    private void recomputeFilteredListings() {
        filteredListings.clear();
        List<ModListing> raw = ModService.getInstance().listings().peek();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String query = searchQuery != null ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";
        for (ModListing listing : raw) {
            if (listing == null) {
                continue;
            }
            if (hideInvalid) {
                int stat = ModStat.checkRemoteModStat(listing);
                if (!ModStat.isValid(stat)) {
                    continue;
                }
            }
            if (!matchesListing(listing, query)) {
                continue;
            }
            filteredListings.add(listing);
        }

        Collections.sort(filteredListings, (a, b) -> {
            int cmp;
            if (orderDate) {
                String dateA = a.getLastUpdated() != null ? a.getLastUpdated() : "";
                String dateB = b.getLastUpdated() != null ? b.getLastUpdated() : "";
                cmp = dateA.compareTo(dateB);
                if (cmp == 0) {
                    cmp = Integer.compare(a.getStars(), b.getStars());
                }
            } else {
                cmp = Integer.compare(a.getStars(), b.getStars());
                if (cmp == 0) {
                    String nameA = a.getName() != null ? a.getName() : "";
                    String nameB = b.getName() != null ? b.getName() : "";
                    cmp = String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
                }
            }
            return reverse ? cmp : -cmp;
        });
    }

    private static boolean matchesListing(ModListing listing, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(listing.getName(), query)
                || containsIgnoreCase(listing.getInternalName(), query)
                || containsIgnoreCase(listing.getAuthor(), query)
                || containsIgnoreCase(listing.getRepo(), query);
    }

    private static boolean containsIgnoreCase(@Nullable String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static float resolveAvailableUnscaledWidth() {
        float w = Core.graphics != null && Core.graphics.getWidth() > 0
                ? Core.graphics.getWidth()
                : 1280f;
        float scl = Math.max(0.01f, Scl.scl());
        return Math.max(320f, (w / scl) - 80f);
    }

    private static int resolveColumnCount() {
        float avail = resolveAvailableUnscaledWidth();
        return Math.max((int) (avail / 552f), 1);
    }

    private static float resolveColumnWidth() {
        float avail = resolveAvailableUnscaledWidth();
        int n = resolveColumnCount();
        return Math.min(540f, (avail / n) - 12f);
    }

    private void batchInstallFolder(FavoriteFolder folder) {
        List<String> repos = new ArrayList<>();
        List<ModListing> knownListings = ModService.getInstance().listings().peek();
        for (ModName mod : folder.getMods()) {
            if (mod == null) {
                continue;
            }
            String repo = mod.getRepo() != null ? mod.getRepo().trim() : "";
            if (repo.isEmpty() && knownListings != null) {
                for (ModListing listing : knownListings) {
                    if (listing != null && mod.matches(listing)
                            && listing.getRepo() != null && !listing.getRepo().trim().isEmpty()) {
                        repo = listing.getRepo().trim();
                        break;
                    }
                }
            }
            if (!repo.isEmpty()) {
                repos.add(repo);
            }
        }
        if (repos.isEmpty()) {
            return;
        }
        if (Vars.ui != null) {
            Vars.ui.showInfoFade(bundleFormat("feature.mod-manager.downloading", repos.size()));
        }
        for (String repo : repos) {
            ModService.getInstance().installFromRepo(repo, false, () -> {
                cardCache.clear();
                refreshBrowserContent();
            });
        }
        refreshBrowserContent();
    }

    private void showCreateFolderDialog() {
        BaseDialog dlg = new BaseDialog(bundle("add", "Add..."));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            TextField nameField = new TextField("");
            nameField.setMessageText(bundle("name", "Name:"));

            body.table(row -> {
                row.add(nameField).growX().fillY();
                row.button(Icon.paste, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                    String clip = Core.app.getClipboardText();
                    if (clip != null) {
                        nameField.setText(clip.trim());
                    }
                }).size(48f);
            }).width(420f).padBottom(10f).row();

            body.button(
                    bundle("ok", "OK"),
                    Icon.ok,
                    safeTextButtonStyle(Styles.flatt),
                    24f,
                    () -> {
                        String name = nameField.getText() != null ? nameField.getText().trim() : "";
                        LocalFavoritesStore.getInstance().createFolder(
                                name.isEmpty() ? bundle("feature.section.favorites", "Favorites") : name);
                        dlg.hide();
                        refreshBrowserContent();
                    }).width(420f).height(46f).row();
        });

        dlg.show();
    }

    private void showRenameFolderDialog(FavoriteFolder folder) {
        BaseDialog dlg = new BaseDialog(bundle("save.rename", "Rename"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            TextField nameField = new TextField(folder.getName());
            nameField.setMessageText(bundle("name", "Name:"));

            body.table(row -> {
                row.add(nameField).growX().fillY();
                row.button(Icon.paste, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                    String clip = Core.app.getClipboardText();
                    if (clip != null) {
                        nameField.setText(clip.trim());
                    }
                }).size(48f);
            }).width(420f).padBottom(10f).row();

            body.button(
                    bundle("save", "Save"),
                    Icon.ok,
                    safeTextButtonStyle(Styles.flatt),
                    24f,
                    () -> {
                        String nextName = nameField.getText() != null ? nameField.getText().trim() : "";
                        if (!nextName.isEmpty()) {
                            LocalFavoritesStore.getInstance().renameFolder(folder.getName(), nextName);
                            dlg.hide();
                            refreshBrowserContent();
                        }
                    }).width(420f).height(46f).row();
        });

        dlg.show();
    }

    private void confirmDeleteFolder(FavoriteFolder folder) {
        if (Vars.ui != null) {
            Vars.ui.showConfirm(
                    bundle("delete", "Delete"),
                    bundle("confirm", "Confirm") + ": " + folder.getName(),
                    () -> {
                        LocalFavoritesStore.getInstance().deleteFolder(folder.getName());
                        refreshBrowserContent();
                    });
        } else {
            LocalFavoritesStore.getInstance().deleteFolder(folder.getName());
            refreshBrowserContent();
        }
    }

    private void showExportFolderDialog(FavoriteFolder folder) {
        String json = LocalFavoritesStore.exportFolderJson(folder);
        BaseDialog dlg = new BaseDialog(bundle("export", "Export"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            Label nameLbl = new Label(folder.getName());
            nameLbl.setColor(Pal.accent);
            body.add(nameLbl).growX().pad(6f).left().row();

            body.table(ModStatusBadges.darkGrayUIAlpha(), codeBox -> {
                Label jsonLbl = new Label(json);
                jsonLbl.setWrap(true);
                codeBox.left().top().add(jsonLbl).pad(6f).growX();
            }).margin(12f).minWidth(420f).growX().padBottom(10f).row();

            body.table(but -> {
                but.defaults().growX().height(46f).pad(4f);
                TextButtonStyle flatStyle = safeTextButtonStyle(Styles.flatt);
                but.button(
                        bundle("feature.mod-manager.folder.copy-clipboard", "Copy to Clipboard"),
                        Icon.copy,
                        flatStyle,
                        24f,
                        () -> {
                            Core.app.setClipboardText(json);
                            if (Vars.ui != null) {
                                Vars.ui.showInfoFade(bundle("copied", "Copied."));
                            }
                        });
                but.button(
                        bundle("feature.mod-manager.folder.export-file", "Export to File"),
                        Icon.file,
                        flatStyle,
                        24f,
                        () -> FileChooser.save("json")
                                .name(folder.getName() + ".json")
                                .submit(dest -> {
                                    if (dest != null) {
                                        try {
                                            Fi target = dest.extension().isEmpty()
                                                    ? dest.sibling(dest.name() + ".json")
                                                    : dest;
                                            target.writeString(json, false);
                                        } catch (Exception e) {
                                            if (Vars.ui != null) {
                                                Vars.ui.showException(e);
                                            }
                                        }
                                    }
                                }));
            }).growX().row();
        });

        dlg.show();
    }

    private void showImportFolderDialog() {
        BaseDialog dlg = new BaseDialog(bundle("import", "Import"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(t -> {
            t.top().left().margin(10f);

            TextField nameField = new TextField("");
            nameField.setMessageText(bundle("name", "Name:"));

            t.table(row -> {
                row.add(nameField).growX().fillY();
                row.button(Icon.paste, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                    String clip = Core.app.getClipboardText();
                    if (clip != null) {
                        nameField.setText(clip.trim());
                    }
                }).size(48f);
            }).growX().minWidth(420f).padBottom(8f).row();

            TextArea jsonArea = new TextArea("");
            t.table(row -> {
                row.add(jsonArea).grow().minHeight(140f);
                row.button(Icon.paste, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                    String clip = Core.app.getClipboardText();
                    if (clip != null) {
                        jsonArea.setText(clip);
                    }
                }).size(48f);
            }).grow().minWidth(420f).maxHeight(320f).padBottom(10f).row();

            t.table(actions -> {
                actions.defaults().growX().height(46f).pad(4f);
                TextButtonStyle flatStyle = safeTextButtonStyle(Styles.flatt);
                actions.button(
                        bundle("feature.mod-manager.folder.import-file", "Import from File"),
                        Icon.file,
                        flatStyle,
                        24f,
                        () -> FileChooser.open("json")
                                .submit(file -> {
                                    if (file != null && file.exists()) {
                                        dlg.hide();
                                        try {
                                            handleImportedFolderJson(file.readString(), nameField.getText());
                                        } catch (Exception e) {
                                            if (Vars.ui != null) {
                                                Vars.ui.showException(e);
                                            }
                                        }
                                    }
                                }));
                actions.button(
                        bundle("import", "Import"),
                        Icon.ok,
                        flatStyle,
                        24f,
                        () -> {
                            String rawJson = jsonArea.getText();
                            dlg.hide();
                            handleImportedFolderJson(rawJson, nameField.getText());
                        });
            }).growX().row();
        });

        dlg.show();
    }

    private void handleImportedFolderJson(@Nullable String json, @Nullable String overrideName) {
        if (json == null || json.trim().isEmpty()) {
            if (Vars.ui != null) {
                Vars.ui.showErrorMessage(bundle("invalid", "Invalid"));
            }
            return;
        }
        try {
            FavoriteFolder parsed = LocalFavoritesStore.parseFolderJson(json);
            String finalName = overrideName != null && !overrideName.trim().isEmpty()
                    ? overrideName.trim()
                    : parsed.getName();
            FavoriteFolder folderToImport = new FavoriteFolder(
                    finalName,
                    false,
                    new ArrayList<>(parsed.getMods()));

            if (!LocalFavoritesStore.getInstance().hasFolder(folderToImport.getName())) {
                LocalFavoritesStore.getInstance().importFolder(folderToImport, ConflictResolution.KEEP_BOTH);
                refreshBrowserContent();
                return;
            }
            showImportConflictDialog(folderToImport);
        } catch (Exception e) {
            if (Vars.ui != null) {
                Vars.ui.showErrorMessage(bundle("invalid", "Invalid"));
            }
        }
    }

    private void showImportConflictDialog(FavoriteFolder parsed) {
        BaseDialog dlg = new BaseDialog(bundle("confirm", "Confirm"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            Label msg = new Label(bundleFormat("feature.mod-manager.folder.conflict.exists", parsed.getName()));
            msg.setWrap(true);
            body.add(msg).width(380f).padBottom(12f).left().row();

            body.defaults().width(380f).height(44f).padBottom(6f);

            body.button(
                    bundle("feature.mod-manager.folder.conflict.keep-both", "Keep Both"),
                    Icon.copySmall,
                    safeTextButtonStyle(Styles.flatt),
                    18f,
                    () -> {
                        LocalFavoritesStore.getInstance().importFolder(parsed, ConflictResolution.KEEP_BOTH);
                        dlg.hide();
                        refreshBrowserContent();
                    }).row();

            body.button(
                    bundle("feature.mod-manager.folder.conflict.merge", "Merge"),
                    Icon.addSmall,
                    safeTextButtonStyle(Styles.flatt),
                    18f,
                    () -> {
                        LocalFavoritesStore.getInstance().importFolder(parsed, ConflictResolution.MERGE);
                        dlg.hide();
                        refreshBrowserContent();
                    }).row();

            body.button(
                    bundle("feature.mod-manager.folder.conflict.replace", "Replace"),
                    Icon.refreshSmall,
                    safeTextButtonStyle(Styles.flatt),
                    18f,
                    () -> {
                        LocalFavoritesStore.getInstance().importFolder(parsed, ConflictResolution.REPLACE);
                        dlg.hide();
                        refreshBrowserContent();
                    }).row();
        });

        dlg.show();
    }
}
