package mindustrytool.features.ui.modmanager;

import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundleFormat;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeImageButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextButtonStyle;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Scaling;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.mod.Mods.LoadedMod;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrytool.features.ui.modmanager.LocalFavoritesStore.FavoriteFolder;

public final class RemoteModCard extends Table {

    private final ModListing listing;
    private final Runnable onStateChanged;
    private final @Nullable String favoriteFolderOwner;

    private final Table cornerIconsSlot = new Table();
    private final Table detailedStatusSlot = new Table();
    private final Table actionButtonsSlot = new Table();
    private final Table subTabContentSlot = new Table();

    private @Nullable Table detailsBody;
    private @Nullable Collapser detailsCollapser;
    private boolean detailsBuilt = false;
    private boolean expanded = false;
    private int detailTab = 0;

    public RemoteModCard(ModListing listing, Runnable onStateChanged) {
        this(listing, null, onStateChanged);
    }

    public RemoteModCard(
            ModListing listing,
            @Nullable String favoriteFolderOwner,
            Runnable onStateChanged) {
        this.listing = listing;
        this.favoriteFolderOwner = favoriteFolderOwner;
        this.onStateChanged = onStateChanged;
        rebuildCard();
    }

    public ModListing getListing() {
        return listing;
    }

    public void rebuildCard() {
        clearChildren();

        String displayName = listing.getName() != null && !listing.getName().trim().isEmpty()
                ? listing.getName().trim()
                : (listing.getInternalName() != null ? listing.getInternalName().trim() : "");
        String author = listing.getAuthor() != null && !listing.getAuthor().trim().isEmpty()
                ? listing.getAuthor().trim()
                : bundle("unknown", "Unknown");
        String version = listing.getVersion();
        String shortDesc = sanitizeSingleLine(listing.getShortDescription(), 110);
        String fullDesc = listing.getDescription() != null && !listing.getDescription().trim().isEmpty()
                ? listing.getDescription()
                : listing.getShortDescription();
        String repo = listing.getRepo() != null ? listing.getRepo().trim() : "";

        LoadedMod installedMod = ModService.getInstance().findInstalledMod(listing);
        boolean isInstalled = installedMod != null;
        String updateVersion = ModService.getInstance().getAvailableUpdate(installedMod);
        int stat = ModStat.checkRemoteModStat(listing);

        Runnable toggleExpand = () -> {
            expanded = !expanded;
            if (expanded && !detailsBuilt && detailsBody != null) {
                detailsBuilt = true;
                LoadedMod currentInstalled = ModService.getInstance().findInstalledMod(listing);
                boolean curInstalled = currentInstalled != null;
                String curUpdate = ModService.getInstance().getAvailableUpdate(currentInstalled);
                int curStat = ModStat.checkRemoteModStat(listing);
                buildDetailsBody(detailsBody, author, repo, fullDesc, curStat, curInstalled, curUpdate);
            }
            if (detailsCollapser != null) {
                detailsCollapser.setCollapsed(!expanded, true);
            }
        };

        button(top -> {
            top.table(ModStatusBadges.buttonSelectDrawable(), icon -> {
                Image iconImage = new Image(resolveInitialIcon(installedMod));
                iconImage.setScaling(Scaling.fit);
                ModService.getInstance().loadModIcon(listing.getIconUrl(), loaded -> {
                    if (loaded != null) {
                        iconImage.setDrawable(loaded);
                    }
                });
                Table stars = new Table();
                stars.bottom().left();
                ModStatusBadges.buildStars(stars, listing.getStars());
                icon.stack(iconImage, stars).size(80f);
            }).pad(10f).margin(4f).size(88f);

            Table info = new Table();
            info.left().top().margin(12f).marginLeft(6f).defaults().left();

            Label nameLabel = new Label(displayName);
            nameLabel.setColor(Pal.accent);
            nameLabel.setWrap(true);
            nameLabel.setAlignment(Align.left);
            info.add(nameLabel).padRight(160f).growX().minWidth(0f).row();

            Label verLabel = new Label(version);
            verLabel.setFontScale(0.8f);
            verLabel.setColor(Color.lightGray);
            verLabel.setWrap(true);
            verLabel.setAlignment(Align.left);
            info.add(verLabel).padRight(50f).growX().minWidth(0f).row();

            Label descLabel = new Label(shortDesc);
            descLabel.setWrap(true);
            descLabel.setAlignment(Align.left);
            info.add(descLabel).padRight(50f).growY().growX().minWidth(0f).row();

            Table over = new Table();
            over.right();

            cornerIconsSlot.clearChildren();
            ModStatusBadges.buildCornerIcons(cornerIconsSlot, stat, true, updateVersion, isInstalled);
            over.add(cornerIconsSlot).fill().pad(4f);

            over.table(side -> {
                ModStatusBadges.line(side, Color.darkGray, false, 3f);
                rebuildActionButtons(repo, isInstalled, updateVersion);
                side.add(actionButtonsSlot).fill();
            }).fill();

            top.stack(info, over).grow().minWidth(0f);
        }, safeButtonStyle(Styles.grayt), toggleExpand).growX().fillY().minWidth(0f).row();

        Table body = new Table(Styles.grayPanel);
        this.detailsBody = body;
        this.detailsBuilt = expanded;
        if (expanded) {
            buildDetailsBody(body, author, repo, fullDesc, stat, isInstalled, updateVersion);
        }

        detailsCollapser = new Collapser(body, !expanded);
        detailsCollapser.setDuration(0.3f);
        add(detailsCollapser).growX().fillY().minWidth(0f).row();
    }

    public void refreshState() {
        LoadedMod installedMod = ModService.getInstance().findInstalledMod(listing);
        boolean isInstalled = installedMod != null;
        String updateVersion = ModService.getInstance().getAvailableUpdate(installedMod);
        int stat = ModStat.checkRemoteModStat(listing);
        String repo = listing.getRepo() != null ? listing.getRepo().trim() : "";

        cornerIconsSlot.clearChildren();
        ModStatusBadges.buildCornerIcons(cornerIconsSlot, stat, true, updateVersion, isInstalled);

        if (detailsBuilt) {
            detailedStatusSlot.clearChildren();
            ModStatusBadges.buildDetailedStatusRows(detailedStatusSlot, stat, updateVersion, true, isInstalled);
        }

        rebuildActionButtons(repo, isInstalled, updateVersion);
    }

    static String sanitizeSingleLine(@Nullable String text, int maxChars) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String firstNonEmpty = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                firstNonEmpty = trimmed;
                break;
            }
        }
        if (firstNonEmpty.length() > maxChars) {
            return firstNonEmpty.substring(0, maxChars - 3).trim() + "...";
        }
        return firstNonEmpty;
    }

    private void rebuildActionButtons(String repo, boolean isInstalled, @Nullable String updateVersion) {
        actionButtonsSlot.clearChildren();
        actionButtonsSlot.defaults().size(48f);

        ModName modName = ModName.fromListing(listing);
        ImageButton favBtn = actionButtonsSlot.button(
                Icon.star,
                safeImageButtonStyle(Styles.clearNonei),
                24f,
                () -> {
                    if (favoriteFolderOwner != null) {
                        LocalFavoritesStore.getInstance().toggleModInFolder(favoriteFolderOwner, modName);
                        onStateChanged.run();
                    } else {
                        showFavoritePickerDialog();
                    }
                }).get();
        if (favBtn.getImage() != null) {
            favBtn.getImage().setScale(0.9f);
        }
        boolean isFav = favoriteFolderOwner != null
                || LocalFavoritesStore.getInstance().isFavorited(modName);
        favBtn.getStyle().imageUpColor = isFav ? Pal.accent : Color.white;
        ModStatusBadges.attachTooltip(favBtn, bundle("feature.button.favorite.add", "Favorites"));
        actionButtonsSlot.row();

        Set<String> installing = ModService.getInstance().installingRepos().peek();
        boolean isInstalling = !repo.isEmpty()
                && installing != null
                && installing.contains(repo.toLowerCase(Locale.ROOT));

        Drawable installIcon;
        Color installColor;
        if (isInstalling) {
            installIcon = Icon.refreshSmall;
            installColor = Pal.accent;
        } else if (isInstalled && updateVersion != null && !updateVersion.isEmpty()) {
            installIcon = Icon.upSmall;
            installColor = ModStatusBadges.LIGHT_BLUE;
        } else {
            installIcon = Icon.downloadSmall;
            installColor = isInstalled ? Pal.heal : Color.white;
        }

        ImageButton installBtn = actionButtonsSlot.button(
                installIcon,
                safeImageButtonStyle(Styles.clearNonei),
                48f,
                () -> {
                    if (!repo.isEmpty() && !isInstalling) {
                        ModService.getInstance().installFromRepo(repo, listing.isHasJava(), () -> {
                            refreshState();
                            onStateChanged.run();
                        });
                        refreshState();
                    }
                }).disabled(isInstalling || repo.isEmpty()).get();
        if (installBtn.getImage() != null) {
            installBtn.getImage().setColor(installColor);
        }
        ModStatusBadges.attachTooltip(
                installBtn,
                bundle(isInstalled ? "feature.mod-manager.browser.reinstall" : "feature.mod-manager.browser.add",
                        isInstalled ? "Reinstall / Update" : "Install Mod"));
        actionButtonsSlot.row();

        ModStatusBadges.addEventBlocker(actionButtonsSlot);
    }

    private void buildDetailsBody(
            Table detailsBody,
            String author,
            String repo,
            @Nullable String fullDesc,
            int stat,
            boolean isInstalled,
            @Nullable String updateVersion) {
        detailsBody.clearChildren();
        detailsBody.table(details -> {
            details.left().defaults().growX().minWidth(0f).pad(4f).padLeft(12f).padRight(12f);

            Label authorLabel = new Label(bundleFormat("mod.author", author));
            authorLabel.setColor(Pal.accent);
            authorLabel.setWrap(true);
            authorLabel.setAlignment(Align.left);
            details.add(authorLabel).growX().minWidth(0f).padRight(50f).row();

            details.table(link -> {
                link.left();
                if (Icon.githubSmall != null) {
                    link.image(Icon.githubSmall).scaling(Scaling.fit).size(24f).color(Color.lightGray);
                }
                boolean hasRepo = !repo.isEmpty();
                String url = hasRepo ? "https://github.com/" + repo : "";
                TextButtonStyle linkStyle = safeTextButtonStyle(Styles.nonet != null ? Styles.nonet : Styles.flatt);
                TextButton linkButton = link.button(url, linkStyle, () -> {
                    if (hasRepo) {
                        Core.app.openURI(url);
                    }
                }).padLeft(4f).padRight(50f).wrap().growX().minWidth(0f).left().align(Align.left).height(30f).disabled(!hasRepo).get();
                if (linkButton.getLabel() != null) {
                    linkButton.getLabel().setAlignment(Align.left);
                    linkButton.getLabel().setWrap(true);
                    linkButton.getLabel().setEllipsis(true);
                    linkButton.getLabel().setFontScale(0.9f);
                }
            }).minWidth(0f).row();

            detailedStatusSlot.clearChildren();
            ModStatusBadges.buildDetailedStatusRows(detailedStatusSlot, stat, updateVersion, true, isInstalled);
            details.add(detailedStatusSlot).minWidth(0f).row();

            ModStatusBadges.line(details, Color.gray, true, 4f).pad(6f).padLeft(-6f).padRight(-6f);
            details.row();

            details.table(switchRow -> {
                switchRow.left().defaults().center();
                addSubTabButton(switchRow, bundle("feature.mod-manager.tab.formatted", "Formatted"), 0, fullDesc);
                addSubTabButton(switchRow, bundle("editor.description", "Description"), 1, fullDesc);
            }).grow().minWidth(0f).padBottom(0f).row();

            subTabContentSlot.clearChildren();
            details.table(ModStatusBadges.grayUI(), desc -> {
                desc.defaults().grow().minWidth(0f);
                desc.add(subTabContentSlot).grow().minWidth(0f);
            }).grow().minWidth(0f).margin(12f).padTop(0f).row();
        }).grow().minWidth(0f);

        rebuildSubTabContent(fullDesc);
    }

    private void addSubTabButton(Table parent, String label, int index, @Nullable String fullDesc) {
        parent.button(
                b -> b.add(label, 0.85f),
                ModStatusBadges.switchButStyle(),
                () -> {
                    detailTab = index;
                    rebuildSubTabContent(fullDesc);
                })
                .margin(12f)
                .checked(b -> detailTab == index)
                .disabled(Button::isChecked);
    }

    private void rebuildSubTabContent(@Nullable String fullDesc) {
        subTabContentSlot.clearChildren();
        subTabContentSlot.top().left();

        if (detailTab == 0) {
            ModCard.renderFormattedDescription(subTabContentSlot, fullDesc);
        } else {
            String textToShow = fullDesc != null && !fullDesc.trim().isEmpty()
                    ? fullDesc
                    : bundle("none", "<none>");
            Label rawLabel = new Label(textToShow);
            rawLabel.setWrap(true);
            subTabContentSlot.add(rawLabel).growX().minWidth(0f).left();
        }
    }

    private void showFavoritePickerDialog() {
        ModName modName = ModName.fromListing(listing);
        BaseDialog dlg = new BaseDialog(bundle("feature.section.favorites", "Favorites"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        Table foldersTable = new Table();
        Runnable[] rebuildFolders = new Runnable[1];
        rebuildFolders[0] = () -> {
            foldersTable.clearChildren();
            foldersTable.top().left().defaults().left().growX();
            List<FavoriteFolder> folders = LocalFavoritesStore.getInstance().folders().peek();
            if (folders == null || folders.isEmpty()) {
                Label empty = new Label(bundle("none.found", "<none found>"));
                empty.setColor(Color.lightGray);
                empty.setWrap(true);
                foldersTable.add(empty).width(380f).pad(6f).left().row();
                return;
            }
            for (FavoriteFolder folder : folders) {
                boolean inside = folder.contains(modName);
                Button rowBtn = new Button(safeButtonStyle(Styles.underlineb != null ? Styles.underlineb : Styles.flatTogglet));
                rowBtn.left().margin(10f).marginLeft(14f);
                rowBtn.setChecked(inside);
                Label lbl = new Label(folder.getName() + " (" + folder.getMods().size() + ")");
                lbl.setColor(inside ? Pal.accent : Color.white);
                rowBtn.add(lbl).growX().left();
                rowBtn.clicked(() -> {
                    LocalFavoritesStore.getInstance().toggleModInFolder(folder.getName(), modName);
                    rebuildFolders[0].run();
                    refreshState();
                    onStateChanged.run();
                });
                foldersTable.add(rowBtn).width(380f).pad(6f).row();
            }
        };
        rebuildFolders[0].run();

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            body.add(foldersTable).growX().padBottom(10f).row();

            body.table(createRow -> {
                TextField nameField = new TextField("");
                nameField.setMessageText(bundle("name", "Name:"));
                createRow.add(nameField).growX().height(40f).padRight(6f);
                createRow.button(
                        bundle("add", "Add..."),
                        Icon.add,
                        safeTextButtonStyle(Styles.flatt),
                        24f,
                        () -> {
                            String rawName = nameField.getText() != null ? nameField.getText().trim() : "";
                            FavoriteFolder created = LocalFavoritesStore.getInstance().createFolder(
                                    rawName.isEmpty() ? "Favorites" : rawName);
                            LocalFavoritesStore.getInstance().toggleModInFolder(created.getName(), modName);
                            nameField.setText("");
                            rebuildFolders[0].run();
                            refreshState();
                            onStateChanged.run();
                        }).height(40f).margin(6f);
            }).width(380f).row();
        });

        dlg.show();
    }

    private static Drawable resolveInitialIcon(@Nullable LoadedMod installedMod) {
        if (installedMod != null && installedMod.iconTexture != null) {
            return new TextureRegionDrawable(new TextureRegion(installedMod.iconTexture));
        }
        return Tex.nomap;
    }
}
