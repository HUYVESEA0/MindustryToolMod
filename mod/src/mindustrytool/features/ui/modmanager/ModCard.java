package mindustrytool.features.ui.modmanager;

import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundleFormat;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeImageButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextButtonStyle;

import arc.Core;
import arc.files.Fi;
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
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Scaling;
import arc.util.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.mod.Mods.LoadedMod;
import mindustry.ui.FileChooser;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustrytool.Main;
import mindustrytool.features.ui.modmanager.PackModel.FileEntry;

public final class ModCard extends Table {

    private final LoadedMod mod;
    private final Runnable onStateChanged;
    private boolean expanded = false;
    private int detailTab = 0;

    private final Table cornerIconsSlot = new Table();
    private final Table detailedStatusSlot = new Table();
    private final Table updateActionSlot = new Table();
    private final Table subTabContentSlot = new Table();
    private @Nullable Collapser detailsCollapser;
    private @Nullable Table detailsBody;
    private @Nullable ImageButton toggleButton;
    private boolean detailsBuilt = false;

    public ModCard(LoadedMod mod, Runnable onStateChanged) {
        this.mod = mod;
        this.onStateChanged = onStateChanged;
        rebuildCard();
    }

    public LoadedMod getMod() {
        return mod;
    }

    public void rebuildCard() {
        clearChildren();

        String displayName = mod.meta != null && mod.meta.displayName != null
                ? mod.meta.displayName
                : mod.name;
        String version = mod.meta != null && mod.meta.version != null
                ? mod.meta.version
                : "";
        String author = mod.meta != null && mod.meta.author != null && !mod.meta.author.isEmpty()
                ? mod.meta.author
                : bundle("unknown", "Unknown");
        String rawShortDesc = mod.meta != null && mod.meta.shortDescription() != null
                ? mod.meta.shortDescription()
                : "";
        String shortDesc = RemoteModCard.sanitizeSingleLine(rawShortDesc, 110);
        String fullDesc = mod.meta != null && mod.meta.description != null
                ? mod.meta.description
                : rawShortDesc;
        String repo = ModService.resolveModRepo(mod);
        boolean isSelf = mod == Main.self;

        String updateVersion = resolveUpdateVersion(repo);
        int stat = ModStat.checkModStat(mod, updateVersion != null);
        boolean canToggle = !isSelf && (mod.enabled() || ModStat.isValid(stat));

        Runnable toggleExpand = () -> {
            expanded = !expanded;
            if (expanded && !detailsBuilt && detailsBody != null) {
                detailsBuilt = true;
                String curUpdate = resolveUpdateVersion(repo);
                int curStat = ModStat.checkModStat(mod, curUpdate != null);
                buildDetailsBody(
                        detailsBody,
                        author,
                        repo,
                        fullDesc,
                        isSelf,
                        collectModContents(mod),
                        curStat,
                        curUpdate);
            }
            if (detailsCollapser != null) {
                detailsCollapser.setCollapsed(!expanded, true);
            }
        };

        button(top -> {
            top.table(ModStatusBadges.buttonSelectDrawable(), icon -> {
                Image iconImg = new Image(resolveModIcon(mod));
                iconImg.setScaling(Scaling.fit);
                icon.add(iconImg).size(80f);
            }).pad(10f).margin(4f).size(88f);

            Table info = new Table();
            info.left().top().margin(12f).marginLeft(6f).defaults().left();

            Label titleLabel = new Label(displayName);
            titleLabel.setColor(Pal.accent);
            titleLabel.setWrap(true);
            titleLabel.setAlignment(Align.left);
            info.add(titleLabel).padRight(160f).grow().minWidth(0f).row();

            Label verLabel = new Label(version);
            verLabel.setFontScale(0.8f);
            verLabel.setColor(Color.lightGray);
            verLabel.setWrap(true);
            verLabel.setAlignment(Align.left);
            info.add(verLabel).padRight(50f).grow().minWidth(0f).row();

            Label descLabel = new Label(shortDesc);
            descLabel.setWrap(true);
            descLabel.setAlignment(Align.left);
            info.add(descLabel).padRight(50f).grow().minWidth(0f).row();

            Table over = new Table();
            over.right();

            cornerIconsSlot.clearChildren();
            ModStatusBadges.buildCornerIcons(cornerIconsSlot, stat, false, updateVersion, true);
            over.add(cornerIconsSlot).fill().pad(4f);

            over.table(side -> {
                ModStatusBadges.line(side, Color.darkGray, false, 3f);
                side.table(buttons -> {
                    buttons.defaults().size(48f);

                    ImageButton toggleBtn = buttons.button(
                            mod.enabled() ? Icon.rightOpen : Icon.leftOpen,
                            safeImageButtonStyle(Styles.clearNonei),
                            32f,
                            () -> {
                                if (!isSelf && Vars.mods != null) {
                                    Vars.mods.setEnabled(mod, !mod.enabled());
                                    ModService.getInstance().notifyInstalledModsChanged();
                                    onStateChanged.run();
                                }
                            }).disabled(!canToggle).get();
                    this.toggleButton = toggleBtn;
                    ModStatusBadges.attachDynamicTooltip(
                            toggleBtn,
                            () -> bundle(mod.enabled() ? "mod.disable" : "mod.enable"));
                    buttons.row();

                    ImageButton exportBtn = buttons.button(
                            Icon.exportSmall,
                            safeImageButtonStyle(Styles.clearNonei),
                            48f,
                            () -> showShareModDialog(
                                    displayName,
                                    version,
                                    shortDesc,
                                    repo,
                                    ModStat.checkModStat(mod, resolveUpdateVersion(repo) != null))).get();
                    ModStatusBadges.attachTooltip(exportBtn, bundle("export", "Export"));
                    buttons.row();

                    ModStatusBadges.addEventBlocker(buttons);
                }).fill();
            }).fill();

            top.stack(info, over).grow().minWidth(0f);
        }, safeButtonStyle(Styles.grayt), toggleExpand).growX().fillY().minWidth(0f).row();

        Table body = new Table(Styles.grayPanel);
        this.detailsBody = body;
        this.detailsBuilt = expanded;
        if (expanded) {
            buildDetailsBody(body, author, repo, fullDesc, isSelf, collectModContents(mod), stat, updateVersion);
        }

        detailsCollapser = new Collapser(body, !expanded);
        detailsCollapser.setDuration(0.3f);
        add(detailsCollapser).growX().fillY().minWidth(0f).row();
    }

    public void refreshStatusState() {
        String repo = ModService.resolveModRepo(mod);
        String updateVersion = resolveUpdateVersion(repo);
        int stat = ModStat.checkModStat(mod, updateVersion != null);
        boolean isSelf = mod == Main.self;
        boolean canToggle = !isSelf && (mod.enabled() || ModStat.isValid(stat));

        if (toggleButton != null) {
            toggleButton.getStyle().imageUp = mod.enabled() ? Icon.rightOpen : Icon.leftOpen;
            toggleButton.setDisabled(!canToggle);
        }

        cornerIconsSlot.clearChildren();
        ModStatusBadges.buildCornerIcons(cornerIconsSlot, stat, false, updateVersion, true);

        if (detailsBuilt) {
            detailedStatusSlot.clearChildren();
            ModStatusBadges.buildDetailedStatusRows(detailedStatusSlot, stat, updateVersion, false, true);
            rebuildUpdateActionButton(repo, updateVersion);
        }
    }

    private void buildDetailsBody(
            Table detailsBody,
            String author,
            @Nullable String repo,
            @Nullable String fullDesc,
            boolean isSelf,
            List<UnlockableContent> contents,
            int stat,
            @Nullable String updateVersion) {
        detailsBody.clearChildren();

        Table details = new Table();
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
            boolean hasRepo = repo != null && !repo.trim().isEmpty();
            String url = hasRepo ? "https://github.com/" + repo.trim() : bundle("mod.import.file", "Local File");
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
        ModStatusBadges.buildDetailedStatusRows(detailedStatusSlot, stat, updateVersion, false, true);
        details.add(detailedStatusSlot).minWidth(0f).row();

        ModStatusBadges.line(details, Color.gray, true, 4f).pad(6f).padLeft(-6f).padRight(-6f);
        details.row();

        details.table(switchRow -> {
            switchRow.left().defaults().center();
            addSubTabButton(switchRow, bundle("feature.mod-manager.tab.formatted", "Formatted"), 0, fullDesc, contents);
            addSubTabButton(switchRow, bundle("editor.description", "Description"), 1, fullDesc, contents);
            if (!contents.isEmpty()) {
                addSubTabButton(
                        switchRow,
                        bundle("feature.mod-manager.tab.content", "Content") + " (" + contents.size() + ")",
                        2,
                        fullDesc,
                        contents);
            }
        }).grow().minWidth(0f).padBottom(0f).row();

        subTabContentSlot.clearChildren();
        details.table(ModStatusBadges.grayUI(), desc -> {
            desc.defaults().grow().minWidth(0f);
            desc.add(subTabContentSlot).grow().minWidth(0f);
        }).grow().minWidth(0f).margin(12f).padTop(0f).row();

        rebuildSubTabContent(fullDesc, contents);

        Table conf = new Table();
        conf.top().right().table(l -> {
            ModStatusBadges.line(l, Color.darkGray, false, 3f);
            l.table(buttons -> {
                buttons.add(updateActionSlot).fill().row();
                rebuildUpdateActionButton(repo, updateVersion);
                if (!isSelf) {
                    ImageButton deleteBtn = buttons.button(
                            Icon.trashSmall,
                            safeImageButtonStyle(Styles.clearNonei),
                            48f,
                            this::confirmDeleteMod).size(48f).get();
                    ModStatusBadges.attachTooltip(deleteBtn, bundle("delete", "Delete"));
                }
            }).fill();
        }).fill();

        detailsBody.stack(details, conf).grow().minWidth(0f);
    }

    private void rebuildUpdateActionButton(@Nullable String repo, @Nullable String updateVersion) {
        updateActionSlot.clearChildren();
        if (updateVersion != null && repo != null && !repo.trim().isEmpty()) {
            ImageButton updBtn = updateActionSlot.button(
                    Icon.upSmall,
                    safeImageButtonStyle(Styles.clearNonei),
                    48f,
                    () -> ModService.getInstance().installFromRepo(repo, mod.isJava(), onStateChanged))
                    .size(48f)
                    .get();
            updBtn.getStyle().imageUpColor = ModStatusBadges.LIGHT_BLUE;
            ModStatusBadges.attachTooltip(
                    updBtn,
                    bundleFormat("feature.mod-manager.update.available", updateVersion));
        }
    }

    private void addSubTabButton(
            Table parent,
            String label,
            int index,
            @Nullable String fullDesc,
            List<UnlockableContent> contents) {
        parent.button(
                b -> b.add(label, 0.85f),
                ModStatusBadges.switchButStyle(),
                () -> {
                    detailTab = index;
                    rebuildSubTabContent(fullDesc, contents);
                })
                .margin(12f)
                .checked(b -> detailTab == index)
                .disabled(Button::isChecked);
    }

    private void rebuildSubTabContent(@Nullable String fullDesc, List<UnlockableContent> contents) {
        subTabContentSlot.clearChildren();
        subTabContentSlot.top().left();

        if (detailTab == 0) {
            renderFormattedDescription(subTabContentSlot, fullDesc);
        } else if (detailTab == 1) {
            String textToShow = fullDesc != null && !fullDesc.trim().isEmpty()
                    ? fullDesc
                    : bundle("none", "<none>");
            Label rawLabel = new Label(textToShow);
            rawLabel.setWrap(true);
            subTabContentSlot.add(rawLabel).growX().minWidth(0f).left();
        } else {
            subTabContentSlot.table(grid -> {
                grid.left().top();
                int col = 0;
                for (UnlockableContent c : contents) {
                    Drawable cIcon = c.uiIcon != null
                            ? new TextureRegionDrawable(c.uiIcon)
                            : Tex.nomap;
                    ImageButton btn = grid.button(
                            cIcon,
                            safeImageButtonStyle(Styles.flati != null ? Styles.flati : Styles.clearNonei),
                            Vars.iconMed,
                            () -> {
                                if (Vars.ui != null && Vars.ui.content != null) {
                                    Vars.ui.content.show(c);
                                }
                            }).size(50f).get();
                    btn.update(() -> {
                        if (btn.getImage() != null && btn.getClickListener() != null) {
                            btn.getImage().color.lerp(
                                    !btn.getClickListener().isOver() ? Color.lightGray : Color.white,
                                    0.4f * Time.delta);
                        }
                    });
                    ModStatusBadges.attachTooltip(btn, c.localizedName);
                    col++;
                    if (col % 8 == 0) {
                        grid.row();
                    }
                }
            }).growX().minWidth(0f).left();
        }
    }

    static void renderFormattedDescription(Table container, @Nullable String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            Label empty = new Label(bundle("none", "<none>"));
            empty.setColor(Color.lightGray);
            empty.setWrap(true);
            container.add(empty).growX().minWidth(0f).left();
            return;
        }
        String[] lines = markdown.replace("\r\n", "\n").split("\n");
        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                container.add().height(6f).row();
                continue;
            }
            if (trimmed.startsWith("### ")) {
                Label h3 = new Label(trimmed.substring(4).trim());
                h3.setColor(Pal.accent);
                h3.setWrap(true);
                container.add(h3).growX().minWidth(0f).left().padTop(4f).padBottom(2f).row();
            } else if (trimmed.startsWith("## ")) {
                Label h2 = new Label(trimmed.substring(3).trim());
                h2.setColor(Pal.accent);
                h2.setFontScale(1.08f);
                h2.setWrap(true);
                container.add(h2).growX().minWidth(0f).left().padTop(6f).padBottom(2f).row();
            } else if (trimmed.startsWith("# ")) {
                Label h1 = new Label(trimmed.substring(2).trim());
                h1.setColor(Pal.accent);
                h1.setFontScale(1.15f);
                h1.setWrap(true);
                container.add(h1).growX().minWidth(0f).left().padTop(8f).padBottom(3f).row();
                ModStatusBadges.line(container, Pal.accent, true, 2f).padBottom(4f);
                container.row();
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                Label item = new Label("• " + trimmed.substring(2).trim());
                item.setWrap(true);
                container.add(item).growX().minWidth(0f).left().padLeft(8f).padBottom(2f).row();
            } else {
                Label line = new Label(rawLine);
                line.setWrap(true);
                container.add(line).growX().minWidth(0f).left().padBottom(2f).row();
            }
        }
    }

    private static @Nullable String resolveUpdateVersion(@Nullable String repo) {
        if (repo == null || repo.trim().isEmpty()) {
            return null;
        }
        Map<String, String> updates = ModService.getInstance().availableUpdates().peek();
        return updates != null ? updates.get(repo.trim().toLowerCase(Locale.ROOT)) : null;
    }

    private static Drawable resolveModIcon(LoadedMod mod) {
        return mod.iconTexture != null
                ? new TextureRegionDrawable(new TextureRegion(mod.iconTexture))
                : Tex.nomap;
    }

    static List<UnlockableContent> collectModContents(@Nullable LoadedMod mod) {
        List<UnlockableContent> result = new ArrayList<>();
        if (mod == null || !mod.enabled() || Vars.content == null || Vars.content.getContentMap() == null) {
            return result;
        }
        for (Seq<Content> seq : Vars.content.getContentMap()) {
            if (seq == null) {
                continue;
            }
            for (Content item : seq) {
                if (item instanceof UnlockableContent) {
                    UnlockableContent uc = (UnlockableContent) item;
                    if (uc.minfo != null && uc.minfo.mod == mod && !uc.isHidden()) {
                        result.add(uc);
                    }
                }
            }
        }
        return result;
    }

    private void confirmDeleteMod() {
        String displayName = mod.meta != null && mod.meta.displayName != null
                ? mod.meta.displayName
                : mod.name;
        if (Vars.ui != null) {
            Vars.ui.showConfirm(
                    displayName,
                    bundle("mod.remove.confirm", "This mod will be deleted."),
                    () -> {
                        if (Vars.mods != null) {
                            Vars.mods.removeMod(mod);
                            ModService.getInstance().notifyInstalledModsChanged();
                            onStateChanged.run();
                        }
                    });
        }
    }

    private void showShareModDialog(
            String displayName,
            String version,
            String shortDesc,
            @Nullable String repo,
            int stat) {
        BaseDialog dlg = new BaseDialog(bundle("export", "Export"));
        dlg.addCloseButton();
        dlg.closeOnBack();

        dlg.cont.table(t -> {
            t.table(ModStatusBadges.darkGrayUI(), cont -> {
                cont.table(ModStatusBadges.buttonSelectDrawable(), icon -> {
                    Image img = new Image(resolveModIcon(mod));
                    img.setScaling(Scaling.fit);
                    icon.add(img).size(80f);
                }).pad(10f).margin(4f).size(88f);

                Table info = new Table();
                info.left().top().margin(12f).marginLeft(6f).defaults().left();
                Label titleLbl = new Label(displayName);
                titleLbl.setColor(Pal.accent);
                titleLbl.setWrap(true);
                info.add(titleLbl).grow().padRight(160f).row();

                Label verLbl = new Label(version);
                verLbl.setFontScale(0.8f);
                verLbl.setColor(Color.lightGray);
                verLbl.setWrap(true);
                info.add(verLbl).grow().padRight(50f).row();

                Label descLbl = new Label(shortDesc);
                descLbl.setWrap(true);
                info.add(descLbl).grow().padRight(50f).row();

                Table statusOver = new Table();
                statusOver.top().right().defaults().right().top();
                Table statusIcons = new Table();
                ModStatusBadges.buildModAttrIcons(statusIcons, stat);
                statusOver.add(statusIcons).grow();

                cont.stack(info, statusOver).pad(12f).padLeft(4f).growX().fillY().minWidth(420f);
            }).margin(6f).growX().fillY().row();

            if (repo != null && !repo.trim().isEmpty()) {
                String url = "https://github.com/" + repo.trim();
                t.button(bundle("feature.mod-manager.github.open", "Open GitHub Repository"), Icon.githubSmall, safeTextButtonStyle(Styles.flatt), 24f, () -> {
                    Core.app.openURI(url);
                    dlg.hide();
                }).growX().fillY().margin(8f).marginTop(12f).marginBottom(12f).padTop(8f).row();
            }

            if (mod.file != null && mod.file.exists()) {
                t.button(bundle("export", "Export"), Icon.exportSmall, safeTextButtonStyle(Styles.flatt), 24f, () -> {
                    exportModArchive();
                    dlg.hide();
                }).growX().fillY().margin(8f).marginTop(12f).marginBottom(12f).padTop(8f).row();

                t.button(bundle("mods.openfolder", "Open Folder"), Icon.folder, safeTextButtonStyle(Styles.flatt), 24f, () -> {
                    Fi targetFolder = mod.file.isDirectory() ? mod.file : mod.file.parent();
                    if (targetFolder != null) {
                        Core.app.openFolder(targetFolder.file().getAbsolutePath());
                    }
                    dlg.hide();
                }).growX().fillY().margin(8f).marginTop(12f).marginBottom(12f).padTop(8f).row();
            }
        });

        dlg.show();
    }

    private void exportModArchive() {
        if (mod.file == null || !mod.file.exists()) {
            return;
        }
        String ext = mod.file.extension().isEmpty() ? "zip" : mod.file.extension();
        FileChooser.save(ext)
                .name(mod.name + "." + ext)
                .submit(dest -> {
                    if (dest == null) {
                        return;
                    }
                    try {
                        if (mod.file.isDirectory()) {
                            PackModel tempPack = new PackModel();
                            tempPack.setName(mod.name);
                            tempPack.setDisplayName(mod.name);
                            tempPack.setVersion("1.0");
                            tempPack.setAuthor("export");
                            tempPack.getFiles().add(new FileEntry(
                                    mod.name,
                                    mod.file.file().getAbsolutePath(),
                                    "custom"));
                            ModpackService.exportModpack(tempPack, dest);
                        } else {
                            Fi target = dest.extension().isEmpty()
                                    ? dest.sibling(dest.name() + "." + mod.file.extension())
                                    : dest;
                            mod.file.copyTo(target);
                        }
                        if (Vars.ui != null) {
                            Vars.ui.showInfoFade(bundleFormat("feature.mod-manager.packer.exported", dest.name()));
                        }
                    } catch (Exception e) {
                        if (Vars.ui != null) {
                            Vars.ui.showException(e);
                        }
                    }
                });
    }
}
