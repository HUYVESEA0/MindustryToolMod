package mindustrytool.features.ui.modmanager;

import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.bundleFormat;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeCheckBoxStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeImageButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeScrollPaneStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextButtonStyle;
import static mindustrytool.features.ui.modmanager.ModStatusBadges.safeTextFieldStyle;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.HandCursorListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Button.ButtonStyle;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Nullable;
import arc.util.Scaling;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import mindustry.Vars;
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
import mindustrytool.features.ui.modmanager.PackModel.ModEntry;
import solim.graphics.BlurHelper;

public final class ModPackerDialog extends BaseDialog {

    private String search = "";
    private boolean showAll = false;

    private PackModel model = new PackModel();
    private final Map<String, LoadedMod> installedByName = new HashMap<>();

    private @Nullable Table selectedTab;
    private @Nullable Table unselectedTab;
    private @Nullable Table optionsTab;
    private @Nullable Table fileEntriesTab;
    private Runnable rebuildFiles = () -> {};

    private boolean optionShow = false;
    private boolean filesShow = false;
    private @Nullable String hoveringMode = null;
    private @Nullable Texture customIconTexture;
    private @Nullable String loadedIconPath;
    private @Nullable Fi activeTempExtractDir;
    private final Vec2 tmpMouse = new Vec2();

    public ModPackerDialog() {
        super(bundle("feature.mod-manager.packer.title", "Modpack Studio"));
        name = "modPackerDialog";
        closeOnBack();
        BlurHelper.applyBackdropBlur(this, 10f);

        shown(() -> {
            disposeCustomIconTexture();
            cleanupTempExtractDir();
            model = new PackModel();
            model.setVersion("1.0");
            model.setMode(PackModel.MODE_DISTRIBUTE);
            model.setSkipRepeat(true);
            optionShow = false;
            filesShow = false;
            rebuild();
        });

        hidden(() -> {
            disposeCustomIconTexture();
            cleanupTempExtractDir();
        });
        resized(this::rebuild);
    }

    private void cleanupTempExtractDir() {
        if (activeTempExtractDir != null) {
            try {
                activeTempExtractDir.deleteDirectory();
            } catch (Exception ignored) {
            }
            activeTempExtractDir = null;
        }
    }

    private void disposeCustomIconTexture() {
        if (customIconTexture != null) {
            customIconTexture.dispose();
            customIconTexture = null;
        }
        loadedIconPath = null;
    }

    public PackModel getModel() {
        return model;
    }

    public void rebuild() {
        safeTextFieldStyle(Styles.defaultField);
        safeScrollPaneStyle(Styles.smallPane);
        cont.clearChildren();
        cacheInstalledMods();

        float screenW = Core.graphics != null ? Core.graphics.getWidth() : 1280f;
        float screenH = Core.graphics != null ? Core.graphics.getHeight() : 720f;
        boolean portrait = screenW < screenH;
        float colWidth = Math.max(280f, screenW / 2.8f / Scl.scl());

        if (portrait) {
            cont.table(main -> {
                main.top();
                main.table(top -> {
                    top.button(Icon.list, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                        optionShow = !optionShow;
                        if (optionShow) {
                            filesShow = false;
                        }
                    }).margin(6f);

                    Image zoomImg = new Image(Icon.zoom);
                    zoomImg.setScaling(Scaling.fit);
                    top.add(zoomImg).size(64f);

                    TextField searchField = new TextField(search, safeTextFieldStyle(Styles.defaultField));
                    searchField.setMessageText(bundle("search", "Search:"));
                    searchField.changed(() -> {
                        search = searchField.getText().trim().toLowerCase(Locale.ROOT);
                        rebuildList();
                    });
                    top.add(searchField).growX();

                    top.button(Icon.download, safeImageButtonStyle(Styles.clearNonei), 32f, this::openModpack).margin(6f);

                    top.button(Icon.file, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                        filesShow = !filesShow;
                        if (filesShow) {
                            optionShow = false;
                        }
                    }).margin(6f);
                }).growX().padLeft(20f).padRight(20f).row();

                main.table(ModStatusBadges.grayUIAlpha(), cent -> {
                    Table mods = new Table();
                    mods.top();

                    mods.table(left -> {
                        left.table(list -> {
                            list.add(bundle("mod.disabled", "Available")).color(Pal.accent).row();
                            ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                            list.row();
                            unselectedTab = list.top().table().growX().fillY().top().get();
                        }).growX().fillY();
                    }).growX().fillY().row();

                    mods.table(right -> {
                        right.table(list -> {
                            list.add(bundle("mod.enabled", "Selected")).color(Pal.accent).row();
                            ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                            list.row();
                            selectedTab = list.top().table().growX().fillY().top().get();
                        }).growX().fillY();
                    }).growX().fillY();

                    ScrollPane pane = new ScrollPane(mods, safeScrollPaneStyle(Styles.smallPane));
                    pane.setForceScroll(false, true);
                    cent.top().add(pane).growX().fillY();
                }).grow().margin(6f).row();

                main.table(buttons -> {
                    buttons.defaults().width(180f).height(58f).pad(4f);
                    TextButtonStyle grayStyle = safeTextButtonStyle(Styles.grayt);
                    buttons.button(bundle("back", "Back"), Icon.leftOpen, grayStyle, 36f, this::hide);
                    buttons.button(bundle("feature.mod-manager.packer.import", "Install Modpack"), Icon.download, grayStyle, 36f, this::promptInstallModpackFile);
                    buttons.button(bundle("feature.mod-manager.packer.export", "Export Modpack"), Icon.export, grayStyle, 36f, this::exportModpack);
                }).fill();
            }).grow();
        } else {
            cont.table(main -> {
                main.top();
                main.table(top -> {
                    ImageButton optBtn = top.button(Icon.list, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                        optionShow = !optionShow;
                        if (optionShow) {
                            filesShow = false;
                        }
                    }).margin(6f).get();
                    ModStatusBadges.attachTooltip(optBtn, bundle("info.title", "Info"));

                    Image zoomImg = new Image(Icon.zoom);
                    zoomImg.setScaling(Scaling.fit);
                    top.add(zoomImg).size(64f);

                    TextField searchField = new TextField(search, safeTextFieldStyle(Styles.defaultField));
                    searchField.setMessageText(bundle("search", "Search:"));
                    searchField.changed(() -> {
                        search = searchField.getText().trim().toLowerCase(Locale.ROOT);
                        rebuildList();
                    });
                    top.add(searchField).growX();

                    CheckBox showAllBox = new CheckBox(bundle("feature.mod-manager.hide-incompatible", "Hide incompatible"), safeCheckBoxStyle(Styles.defaultCheck));
                    showAllBox.setChecked(!showAll);
                    showAllBox.changed(() -> {
                        showAll = !showAllBox.isChecked();
                        rebuildList();
                    });
                    top.add(showAllBox).padLeft(8f).padRight(8f);

                    ImageButton filesBtn = top.button(Icon.file, safeImageButtonStyle(Styles.clearNonei), 32f, () -> {
                        filesShow = !filesShow;
                        if (filesShow) {
                            optionShow = false;
                        }
                    }).margin(6f).get();
                    ModStatusBadges.attachTooltip(filesBtn, bundle("feature.mod-manager.packer.files", "Attached Files"));
                }).growX().padLeft(40f).padRight(40f).row();

                main.table(mods -> {
                    mods.table(left -> {
                        left.table(ModStatusBadges.grayUIAlpha(), list -> {
                            list.add(bundle("mod.disabled", "Unselected")).color(Pal.accent).row();
                            ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                            list.row();
                            Table unselect = new Table();
                            unselect.top();
                            unselectedTab = unselect;
                            ScrollPane pane = new ScrollPane(unselect, safeScrollPaneStyle(Styles.smallPane));
                            pane.setForceScroll(false, true);
                            list.top().add(pane).width(colWidth).fillY().top();
                        }).fillX().growY();
                    }).fillX().growY();

                    ModStatusBadges.line(mods, Color.gray, false, 4f).padLeft(6f).padRight(6f);

                    mods.table(right -> {
                        right.table(ModStatusBadges.grayUIAlpha(), list -> {
                            list.add(bundle("mod.enabled", "Selected")).color(Pal.accent).row();
                            ModStatusBadges.line(list, Pal.accent, true, 4f).padTop(6f).padBottom(6f);
                            list.row();
                            Table select = new Table();
                            select.top();
                            selectedTab = select;
                            ScrollPane pane = new ScrollPane(select, safeScrollPaneStyle(Styles.smallPane));
                            pane.setForceScroll(false, true);
                            list.top().add(pane).width(colWidth).fillY().top();
                        }).fillX().growY();
                    }).fillX().growY();
                }).grow().row();

                main.table(buttons -> {
                    buttons.defaults().width(200f).height(62f).pad(4f);
                    TextButtonStyle grayStyle = safeTextButtonStyle(Styles.grayt);
                    buttons.button(bundle("back", "Back"), Icon.leftOpen, grayStyle, 36f, this::hide);
                    buttons.button(bundle("mod.import.file", "Import File"), Icon.file, grayStyle, 36f, this::openModpack);
                    buttons.button(bundle("feature.mod-manager.packer.import", "Install Modpack"), Icon.download, grayStyle, 36f, this::promptInstallModpackFile);
                    buttons.button(bundle("feature.mod-manager.packer.backups", "Backups"), Icon.save, grayStyle, 36f, this::showBackupsDialog);
                    buttons.button(bundle("feature.mod-manager.packer.export", "Export Modpack"), Icon.export, grayStyle, 36f, this::exportModpack);
                }).fill();
            }).fillX().growY();
        }

        cont.fill(over -> {
            Drawable tex = portrait ? ModStatusBadges.grayUI() : ModStatusBadges.grayUIAlpha();

            Table options = new Table(tex);
            options.touchable = Touchable.enabled;
            options.top().left().defaults().growX().pad(6f);
            optionsTab = options;
            buildOptions(options);
            over.addChild(options);

            Table fileEntries = new Table(tex);
            fileEntries.touchable = Touchable.enabled;
            fileEntries.top().left().defaults().growX().pad(6f);
            fileEntriesTab = fileEntries;
            buildFileEntries(fileEntries);
            over.addChild(fileEntries);

            if (Core.app != null) {
                Core.app.post(() -> {
                    float scenW = cont.getWidth();
                    float scenH = cont.getHeight();

                    options.pack();
                    if (portrait) {
                        options.setWidth(Math.max(scenW, options.getWidth()));
                        options.setHeight(scenH);
                        options.x = -options.getWidth();
                        options.update(() -> {
                            float toX = optionShow ? 0f : -options.getWidth();
                            options.x = Mathf.lerpDelta(options.x, toX, 0.2f);
                        });

                        fileEntries.pack();
                        fileEntries.setWidth(scenW);
                        fileEntries.setHeight(scenH);
                        fileEntries.x = scenW;
                        fileEntries.update(() -> {
                            float toX = filesShow ? over.getWidth() - fileEntries.getWidth() : over.getWidth();
                            fileEntries.x = Mathf.lerpDelta(fileEntries.x, toX, 0.2f);
                        });
                    } else {
                        options.setWidth(Math.max(scenW / 3.4f, options.getWidth()));
                        options.setHeight(scenH);
                        options.x = -options.getWidth();
                        options.update(() -> {
                            float toX = optionShow ? 0f : -options.getWidth();
                            options.x = Mathf.lerpDelta(options.x, toX, 0.2f);
                        });

                        fileEntries.pack();
                        fileEntries.setWidth(Math.max(scenW / 3.4f, 340f));
                        fileEntries.setHeight(scenH);
                        fileEntries.x = scenW;
                        fileEntries.update(() -> {
                            float toX = filesShow ? over.getWidth() - fileEntries.getWidth() : over.getWidth();
                            fileEntries.x = Mathf.lerpDelta(fileEntries.x, toX, 0.2f);
                        });

                        if (!Core.app.isMobile()) {
                            over.update(() -> {
                                if (Core.input == null || Core.scene == null) {
                                    return;
                                }
                                tmpMouse.set(Core.input.mouseX(), Core.input.mouseY());
                                Core.scene.screenToStageCoordinates(tmpMouse);
                                over.stageToLocalCoordinates(tmpMouse);
                                float x = tmpMouse.x;
                                float y = tmpMouse.y;

                                if (y < 0f || y > over.getHeight()) {
                                    return;
                                }
                                if (!optionShow && x >= 0f && x < 48f) {
                                    optionShow = true;
                                } else if (optionShow && x > options.getWidth() + 20f) {
                                    optionShow = false;
                                }

                                if (!filesShow && x <= over.getWidth() && x > over.getWidth() - 48f) {
                                    filesShow = true;
                                } else if (filesShow && x < over.getWidth() - fileEntries.getWidth() - 20f) {
                                    filesShow = false;
                                }
                            });
                        }
                    }
                });
            }
        });

        rebuildList();
    }

    private void cacheInstalledMods() {
        installedByName.clear();
        if (Vars.mods == null || Vars.mods.list() == null) {
            return;
        }
        for (LoadedMod mod : Vars.mods.list()) {
            if (mod != null && mod != Main.self && mod.name != null) {
                installedByName.put(mod.name.toLowerCase(Locale.ROOT), mod);
            }
        }
    }

    private boolean isModSelected(String modName) {
        if (modName == null || model.getMods() == null) {
            return false;
        }
        for (ModEntry entry : model.getMods()) {
            if (entry != null && entry.getName() != null && entry.getName().equalsIgnoreCase(modName)) {
                return true;
            }
        }
        return false;
    }

    private void toggleModSelection(LoadedMod mod, int stat) {
        if (mod == null || mod.name == null) {
            return;
        }
        if (isModSelected(mod.name)) {
            Iterator<ModEntry> it = model.getMods().iterator();
            while (it.hasNext()) {
                ModEntry e = it.next();
                if (e != null && e.getName() != null && e.getName().equalsIgnoreCase(mod.name)) {
                    it.remove();
                }
            }
        } else {
            List<String> deps = new ArrayList<>();
            if (mod.meta != null && mod.meta.dependencies != null) {
                for (String dep : mod.meta.dependencies) {
                    if (dep != null && !dep.trim().isEmpty()) {
                        deps.add(dep.trim());
                    }
                }
            }
            String displayName = mod.meta != null && mod.meta.displayName != null ? mod.meta.displayName : mod.name;
            String version = mod.meta != null && mod.meta.version != null ? mod.meta.version : "";
            String author = mod.meta != null && mod.meta.author != null ? mod.meta.author : "";
            String shortDesc = mod.meta != null && mod.meta.shortDescription() != null ? mod.meta.shortDescription() : "";
            String fileName = mod.file != null ? mod.file.name() : (mod.name + ".zip");
            String sourcePath = mod.file != null ? mod.file.file().getAbsolutePath() : "";
            model.getMods().add(new ModEntry(
                    mod.name,
                    displayName,
                    version,
                    author,
                    fileName,
                    sourcePath,
                    shortDesc,
                    deps,
                    stat));
        }
        rebuildList();
    }

    private void buildOptions(Table options) {
        Table header = options.table(Tex.whiteui, it -> {
            it.left();
            if (Styles.outlineLabel != null) {
                it.add(bundle("info.title", "Info"), Styles.outlineLabel).pad(12f).left();
            } else {
                it.add(bundle("info.title", "Info")).pad(12f).left();
            }
            it.add().growX();
            it.button(Icon.cancel, safeImageButtonStyle(Styles.clearNonei), 32f, () -> optionShow = false).margin(6f);
        }).pad(0f).get();
        header.setColor(Pal.gray);
        options.row();

        ModStatusBadges.line(options, Pal.darkerGray, true, 4f).pad(0f).padBottom(4f);
        options.row();

        Table list = new Table();
        list.left().defaults().pad(6f).left().growX();

        list.add(bundle("info.title", "Info")).color(Pal.accent).row();
        ModStatusBadges.line(list, Pal.accent, true, 3f).padTop(0f).row();

        list.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
            tab.left().defaults().pad(6f).left();
            tab.add(bundle("feature.mod-manager.packer.icon", "Modpack Icon")).row();

            loadedIconPath = null;
            Table iconBox = tab.left().table(Tex.whiteui, it -> {
                Image iconImage = new Image(Tex.nomap);
                iconImage.setScaling(Scaling.fit);
                iconImage.update(() -> {
                    String currentPath = model.getIconPath();
                    if ((currentPath == null || currentPath.isEmpty()) && loadedIconPath != null) {
                        disposeCustomIconTexture();
                        iconImage.setDrawable(Tex.nomap);
                    } else if (currentPath != null && !currentPath.isEmpty() && !currentPath.equals(loadedIconPath)) {
                        loadedIconPath = currentPath;
                        Fi fi = new Fi(new File(currentPath));
                        if (fi.exists() && !fi.isDirectory()) {
                            try {
                                if (customIconTexture != null) {
                                    customIconTexture.dispose();
                                    customIconTexture = null;
                                }
                                customIconTexture = new Texture(fi);
                                iconImage.setDrawable(new TextureRegionDrawable(new TextureRegion(customIconTexture)));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                it.add(iconImage).size(160f);
            }).margin(4f).left().get();
            iconBox.setColor(Pal.darkerGray);
            iconBox.addListener(new HandCursorListener());
            iconBox.clicked(() -> FileChooser.open("png", "jpg").submit(file -> {
                if (file == null || !file.exists()) {
                    return;
                }
                model.setIconPath(file.file().getAbsolutePath());
            }));
            tab.row();
        }).row();

        list.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
            tab.left().defaults().pad(6f).left();
            tab.add(bundle("editor.name", "Name:")).padBottom(0f).row();
            TextField displayField = new TextField(model.getDisplayName());
            displayField.changed(() -> {
                model.setDisplayName(displayField.getText());
                if (model.getName().trim().isEmpty()) {
                    model.setName(sanitizeInternalName(displayField.getText()));
                }
            });
            displayField.update(() -> displayField.setColor(displayField.getText().trim().isEmpty() ? Color.red : Color.white));
            tab.add(displayField).padTop(0f).growX().row();

            tab.add(bundle("name", "Name:")).padBottom(0f).row();
            TextField nameField = new TextField(model.getName());
            nameField.changed(() -> model.setName(nameField.getText()));
            nameField.update(() -> {
                if (!nameField.hasKeyboard() && !nameField.getText().equals(model.getName())) {
                    nameField.setText(model.getName());
                }
            });
            tab.add(nameField).padTop(0f).growX();
        }).row();

        list.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
            tab.left().defaults().pad(6f).left();
            tab.add(bundle("editor.description", "Description:")).padBottom(0f).row();
            TextArea descArea = new TextArea(model.getDescription());
            descArea.changed(() -> model.setDescription(descArea.getText()));
            tab.add(descArea).padTop(0f).growX().height(110f);
        }).row();

        list.table(it -> {
            it.defaults().growX().pad(6f);
            it.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
                tab.left().defaults().pad(6f).left();
                tab.add(bundle("editor.author", "Author:")).padBottom(0f).row();
                TextField authorField = new TextField(model.getAuthor());
                authorField.changed(() -> model.setAuthor(authorField.getText()));
                authorField.update(() -> authorField.setColor(authorField.getText().trim().isEmpty() ? Color.red : Color.white));
                tab.add(authorField).padTop(0f).growX();
            });

            it.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
                tab.left().defaults().pad(6f).left();
                tab.add(bundle("mod.version", "Version:")).padBottom(0f).row();
                TextField verField = new TextField(model.getVersion());
                verField.changed(() -> model.setVersion(verField.getText()));
                verField.update(() -> verField.setColor(verField.getText().trim().isEmpty() ? Color.red : Color.white));
                tab.add(verField).padTop(0f).growX();
            });
        }).pad(0f).growX().fillY().row();

        list.add(bundle("level.mode", "Mode:")).color(Pal.accent).row();
        ModStatusBadges.line(list, Pal.accent, true, 3f).padTop(0f).row();

        list.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
            tab.left().defaults().pad(6f).left();
            tab.add(bundle("level.mode", "Mode:")).padBottom(0f).row();
            tab.table(types -> {
                types.defaults().pad(4f).growX().fillY();
                addModeToggle(types, PackModel.MODE_DISTRIBUTE, bundle("feature.mod-manager.packer.mode.distribute", "Distribute"), Icon.addSmall);
                addModeToggle(types, PackModel.MODE_SHADOW, bundle("feature.mod-manager.packer.mode.shadow", "Shadow"), Icon.layersSmall != null ? Icon.layersSmall : Icon.bookSmall);
                addModeToggle(types, PackModel.MODE_OVERRIDE, bundle("feature.mod-manager.packer.mode.override", "Override"), Icon.warningSmall);
            }).growX().fillY().row();

            Label modeDesc = new Label("");
            modeDesc.setWrap(true);
            modeDesc.setColor(Color.lightGray);
            String[] lastMode = { null };
            modeDesc.update(() -> {
                String active = hoveringMode != null ? hoveringMode : model.getMode();
                if (!Objects.equals(active, lastMode[0])) {
                    lastMode[0] = active;
                    modeDesc.setText(resolveModeDescription(active));
                }
            });
            tab.add(modeDesc).pad(10f).growX();
        }).row();

        list.table(ModStatusBadges.darkGrayUIAlpha(), tab -> {
            tab.left().defaults().pad(6f).left().growX();
            CheckBox skipRepeat = new CheckBox(bundle("feature.mod-manager.packer.option.skip-repeat", "Skip already installed mods"), safeCheckBoxStyle(Styles.defaultCheck));
            skipRepeat.setChecked(model.isSkipRepeat());
            skipRepeat.changed(() -> model.setSkipRepeat(skipRepeat.isChecked()));
            tab.add(skipRepeat).left().row();

            CheckBox rawBackup = new CheckBox(bundle("feature.mod-manager.packer.option.raw-backup", "Create backup before installing"), safeCheckBoxStyle(Styles.defaultCheck));
            rawBackup.setChecked(model.isRawBackup());
            rawBackup.changed(() -> model.setRawBackup(rawBackup.isChecked()));
            tab.add(rawBackup).left().row();

            CheckBox force = new CheckBox(bundle("feature.mod-manager.packer.option.force-mode", "Lock installation mode"), safeCheckBoxStyle(Styles.defaultCheck));
            force.setChecked(model.isForce());
            force.changed(() -> model.setForce(force.isChecked()));
            tab.add(force).left().row();

            CheckBox uncheck = new CheckBox(bundle("feature.mod-manager.packer.option.uncheck", "Ignore validation warnings on export"), safeCheckBoxStyle(Styles.defaultCheck));
            uncheck.setChecked(model.isUncheck());
            uncheck.changed(() -> model.setUncheck(uncheck.isChecked()));
            tab.add(uncheck).left().row();
        }).row();

        ScrollPane pane = new ScrollPane(list, safeScrollPaneStyle(Styles.smallPane));
        pane.setScrollingDisabled(true, false);
        options.add(pane).growX().fillY();
    }

    private void addModeToggle(Table parent, String modeKey, String label, @Nullable Drawable icon) {
        TextButton btn = parent.button(
                label,
                icon,
                safeTextButtonStyle(Styles.flatTogglet),
                24f,
                () -> model.setMode(modeKey)).margin(10f).get();
        btn.update(() -> btn.setChecked(modeKey.equalsIgnoreCase(model.getMode())));
        btn.hovered(() -> hoveringMode = modeKey);
        btn.exited(() -> {
            if (modeKey.equals(hoveringMode)) {
                hoveringMode = null;
            }
        });
    }

    private String resolveModeLabel(@Nullable String mode) {
        if (PackModel.MODE_SHADOW.equalsIgnoreCase(mode)) {
            return bundle("feature.mod-manager.packer.mode.shadow", "Shadow");
        }
        if (PackModel.MODE_OVERRIDE.equalsIgnoreCase(mode)) {
            return bundle("feature.mod-manager.packer.mode.override", "Override");
        }
        return bundle("feature.mod-manager.packer.mode.distribute", "Distribute");
    }

    private String resolveModeDescription(@Nullable String mode) {
        if (PackModel.MODE_SHADOW.equalsIgnoreCase(mode)) {
            return bundle("feature.mod-manager.packer.mode.shadow.desc", "Backs up current state, disables other mods, and enables only modpack mods.");
        }
        if (PackModel.MODE_OVERRIDE.equalsIgnoreCase(mode)) {
            return bundle("feature.mod-manager.packer.mode.override.desc", "Backs up current state and removes non-modpack mods from the mods folder.");
        }
        return bundle("feature.mod-manager.packer.mode.distribute.desc", "Installs and enables bundled mods alongside existing mods.");
    }

    private void buildFileEntries(Table file) {
        Table header = file.table(Tex.whiteui, it -> {
            it.left();
            if (Styles.outlineLabel != null) {
                it.add(bundle("feature.mod-manager.packer.files", "Attached Files"), Styles.outlineLabel).pad(12f).left();
            } else {
                it.add(bundle("feature.mod-manager.packer.files", "Attached Files")).pad(12f).left();
            }
            it.add().growX();
            it.button(Icon.cancel, safeImageButtonStyle(Styles.clearNonei), 32f, () -> filesShow = false).margin(6f);
        }).pad(0f).get();
        header.setColor(Pal.gray);
        file.row();

        ModStatusBadges.line(file, Pal.darkerGray, true, 3f).pad(0f).padBottom(4f);
        file.row();

        file.table(t -> {
            Table list = new Table();
            list.top().left();
            rebuildFiles = () -> {
                list.clearChildren();
                if (model.getFiles() == null || model.getFiles().isEmpty()) {
                    Label empty = new Label(bundle("none.found", "<none found>"));
                    empty.setColor(Color.lightGray);
                    empty.setWrap(true);
                    list.add(empty).growX().pad(12f).row();
                    return;
                }
                for (FileEntry entry : new ArrayList<>(model.getFiles())) {
                    Table tab = buildFileTab(entry);
                    list.add(tab).growX().padTop(4f).padBottom(4f).row();
                }
            };
            rebuildFiles.run();

            ScrollPane pane = new ScrollPane(list, safeScrollPaneStyle(Styles.smallPane));
            pane.setScrollingDisabled(true, false);
            t.top().add(pane).growX().fillY();
        }).grow().row();

        file.table(attach -> {
            attach.defaults().growX().height(40f).pad(2f);
            TextButtonStyle flatStyle = safeTextButtonStyle(Styles.flatt);
            attach.button(bundle("savegame", "Saves"), Icon.mapSmall, flatStyle, 20f, this::attachSavesDir).row();
            attach.button(bundle("schematics", "Schematics"), Icon.bookSmall, flatStyle, 20f, this::attachSchematicsDir).row();
            attach.button(bundle("settings", "Settings"), Icon.settingsSmall, flatStyle, 20f, this::attachSettingsBin).row();
            attach.button(bundle("custom", "Custom"), Icon.addSmall, flatStyle, 20f, this::attachCustomFile).row();
        }).growX();
    }

    private Table buildFileTab(FileEntry entry) {
        Table tab = new Table(ModStatusBadges.darkGrayUIAlpha());
        tab.margin(6f);

        Fi fi = new Fi(new File(entry.getSourcePath()));
        String ext = fi.extension().toLowerCase(Locale.ROOT);
        Drawable iconDrawable = "msav".equals(ext)
                ? Icon.map
                : ("msch".equals(ext)
                        ? Icon.book
                        : ("bin".equals(ext) ? Icon.settings : Icon.file));

        Image iconImg = new Image(iconDrawable);
        iconImg.setScaling(Scaling.fit);
        tab.add(iconImg).size(52f).pad(6f);

        tab.table(info -> {
            info.left();
            info.table(t -> {
                t.add(fi.name()).pad(4f).left().growX();
                t.button(Icon.cancel, safeImageButtonStyle(Styles.clearNonei), 24f, () -> {
                    model.getFiles().remove(entry);
                    rebuildFiles.run();
                }).margin(4f);
            }).growX().row();

            Table pathBox = info.table(Tex.whiteui, ent -> {
                Label srcLabel = new Label(entry.getSourcePath());
                srcLabel.setWrap(true);
                srcLabel.setColor(Color.lightGray);
                ent.add(srcLabel).growX().row();

                ent.table(t -> {
                    Image arrow = new Image(Icon.rightSmall);
                    arrow.setScaling(Scaling.fit);
                    t.add(arrow).size(24f);
                    t.add("files/");
                    TextField toField = new TextField(entry.getTargetPath() != null ? entry.getTargetPath() : "");
                    toField.changed(() -> entry.setTargetPath(toField.getText()));
                    toField.update(() -> toField.setColor(
                            entry.getTargetPath() == null || entry.getTargetPath().trim().isEmpty()
                                    ? Color.red
                                    : Color.white));
                    t.add(toField).growX();
                }).growX();
            }).margin(6f).growX().fillY().get();
            pathBox.setColor(Color.black);
        }).growX().fillY();

        return tab;
    }

    private void attachSavesDir() {
        if (Vars.saveDirectory != null && Vars.saveDirectory.exists()) {
            addFileEntryIfAbsent("saves", Vars.saveDirectory.file().getAbsolutePath(), "saves");
        }
    }

    private void attachSchematicsDir() {
        if (Vars.schematicDirectory != null && Vars.schematicDirectory.exists()) {
            addFileEntryIfAbsent("schematics", Vars.schematicDirectory.file().getAbsolutePath(), "schematics");
        }
    }

    private void attachSettingsBin() {
        if (Core.settings != null && Core.settings.getSettingsFile() != null && Core.settings.getSettingsFile().exists()) {
            addFileEntryIfAbsent("settings.bin", Core.settings.getSettingsFile().file().getAbsolutePath(), "settings");
        }
    }

    private void attachCustomFile() {
        FileChooser.open("*").submit(f -> {
            if (f != null && f.exists()) {
                addFileEntryIfAbsent(f.name(), f.file().getAbsolutePath(), "custom");
            }
        });
    }

    private void addFileEntryIfAbsent(String targetPath, String sourcePath, String category) {
        for (FileEntry existing : model.getFiles()) {
            if (existing != null && sourcePath.equals(existing.getSourcePath())) {
                return;
            }
        }
        model.getFiles().add(new FileEntry(targetPath, sourcePath, category));
        rebuildFiles.run();
    }

    private void rebuildList() {
        if (selectedTab == null || unselectedTab == null) {
            return;
        }
        selectedTab.clearChildren();
        unselectedTab.clearChildren();

        for (ModEntry entry : new ArrayList<>(model.getMods())) {
            if (entry == null) {
                continue;
            }
            if (!matchesSearch(entry.getDisplayName(), entry.getName(), entry.getAuthor(), entry.getShortDescription())) {
                continue;
            }
            LoadedMod loaded = installedByName.get(entry.getName().toLowerCase(Locale.ROOT));
            Table card = buildPackModCard(entry, loaded, true);
            selectedTab.add(card).fillY().growX().pad(4f).minWidth(0f).row();
        }

        if (Vars.mods != null && Vars.mods.list() != null) {
            for (LoadedMod mod : Vars.mods.list()) {
                if (mod == null || mod == Main.self || isModSelected(mod.name)) {
                    continue;
                }
                int stat = ModStat.checkModStat(mod, false);
                if (!showAll && !ModStat.isValid(stat)) {
                    continue;
                }
                String displayName = mod.meta != null && mod.meta.displayName != null ? mod.meta.displayName : mod.name;
                String author = mod.meta != null && mod.meta.author != null ? mod.meta.author : "";
                String shortDesc = mod.meta != null && mod.meta.shortDescription() != null ? mod.meta.shortDescription() : "";
                if (!matchesSearch(displayName, mod.name, author, shortDesc)) {
                    continue;
                }
                ModEntry candidate = new ModEntry(
                        mod.name,
                        displayName,
                        mod.meta != null && mod.meta.version != null ? mod.meta.version : "",
                        author,
                        mod.file != null ? mod.file.name() : (mod.name + ".zip"),
                        mod.file != null ? mod.file.file().getAbsolutePath() : "",
                        shortDesc,
                        new ArrayList<>(),
                        stat);
                Table card = buildPackModCard(candidate, mod, false);
                unselectedTab.add(card).fillY().growX().pad(4f).minWidth(0f).row();
            }
        }
    }

    private boolean matchesSearch(@Nullable String displayName, @Nullable String name, @Nullable String author, @Nullable String desc) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        return (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(search))
                || (name != null && name.toLowerCase(Locale.ROOT).contains(search))
                || (author != null && author.toLowerCase(Locale.ROOT).contains(search))
                || (desc != null && desc.toLowerCase(Locale.ROOT).contains(search));
    }

    private Table buildPackModCard(ModEntry entry, @Nullable LoadedMod loaded, boolean isSelected) {
        Table card = new Table();
        boolean[] expanded = { false };
        int[] detailTab = { 0 };
        Table detailsBody = new Table(Styles.grayPanel);
        Collapser collapser = new Collapser(detailsBody, true);
        collapser.setDuration(0.3f);

        String displayName = entry.getDisplayName() != null && !entry.getDisplayName().isEmpty()
                ? entry.getDisplayName()
                : entry.getName();
        String version = entry.getVersion() != null ? entry.getVersion() : "";
        String author = entry.getAuthor() != null && !entry.getAuthor().isEmpty()
                ? entry.getAuthor()
                : bundle("unknown", "Unknown");
        String shortDesc = RemoteModCard.sanitizeSingleLine(entry.getShortDescription(), 110);
        int stat = entry.getStat();

        Runnable toggleExpand = () -> {
            expanded[0] = !expanded[0];
            if (expanded[0] && detailsBody.getChildren().isEmpty()) {
                buildPackCardDetails(detailsBody, entry, loaded, author, stat, detailTab);
            }
            collapser.setCollapsed(!expanded[0], true);
        };

        card.button(top -> {
            top.table(ModStatusBadges.buttonSelectDrawable(), icon -> {
                Drawable drawable = loaded != null && loaded.iconTexture != null
                        ? new TextureRegionDrawable(new TextureRegion(loaded.iconTexture))
                        : Tex.nomap;
                Image iconImg = new Image(drawable);
                iconImg.setScaling(Scaling.fit);
                icon.add(iconImg).size(80f);
            }).pad(10f).margin(4f).size(88f);

            Table info = new Table();
            info.left().top().margin(12f).marginLeft(6f).defaults().left();

            Label titleLbl = new Label(displayName);
            titleLbl.setColor(Pal.accent);
            titleLbl.setWrap(true);
            titleLbl.setAlignment(Align.left);
            info.add(titleLbl).padRight(160f).grow().minWidth(0f).row();

            Label verLbl = new Label(version);
            verLbl.setFontScale(0.8f);
            verLbl.setColor(Color.lightGray);
            verLbl.setWrap(true);
            verLbl.setAlignment(Align.left);
            info.add(verLbl).padRight(50f).grow().minWidth(0f).row();

            Label descLbl = new Label(shortDesc);
            descLbl.setWrap(true);
            descLbl.setAlignment(Align.left);
            info.add(descLbl).padRight(50f).grow().minWidth(0f).row();

            Table over = new Table();
            over.right();

            Table status = new Table();
            status.top().right().defaults().size(26f).pad(4f);
            ModStatusBadges.buildModAttrIcons(status, stat);
            ModStatusBadges.buildErrorIcons(status, stat);
            over.add(status).fill().pad(4f);

            over.table(side -> {
                ModStatusBadges.line(side, Color.darkGray, false, 3f);
                side.table(buttons -> {
                    buttons.defaults().width(45f).growY();
                    buttons.button(
                            isSelected ? Icon.leftOpen : Icon.rightOpen,
                            safeImageButtonStyle(Styles.clearNonei),
                            48f,
                            () -> {
                                if (loaded != null) {
                                    toggleModSelection(loaded, stat);
                                } else {
                                    model.getMods().remove(entry);
                                    rebuildList();
                                }
                            }).fillX().growY();
                    ModStatusBadges.addEventBlocker(buttons);
                }).fillX().growY();
            }).fillX().growY().marginTop(6f).marginBottom(6f);

            top.stack(info, over).grow().minWidth(0f);
        }, safeButtonStyle(Styles.grayt), toggleExpand).growX().fillY().minWidth(0f).row();

        card.add(collapser).growX().fillY().minWidth(0f).row();
        return card;
    }

    private void buildPackCardDetails(
            Table body,
            ModEntry entry,
            @Nullable LoadedMod loaded,
            String author,
            int stat,
            int[] detailTab) {
        body.clearChildren();
        Table details = new Table();
        details.left().defaults().growX().minWidth(0f).pad(4f).padLeft(12f).padRight(12f);

        Label authorLbl = new Label(bundleFormat("mod.author", author));
        authorLbl.setColor(Pal.accent);
        authorLbl.setWrap(true);
        authorLbl.setAlignment(Align.left);
        details.add(authorLbl).growX().minWidth(0f).padRight(50f).row();

        details.table(status -> {
            status.left().defaults().left();
            ModStatusBadges.buildModBasicStatus(status, stat);
            ModStatusBadges.buildModAttrList(status, stat);
            ModStatusBadges.buildModErrList(status, stat);
        }).minWidth(0f).row();

        List<UnlockableContent> contents = ModCard.collectModContents(loaded);
        String fullDesc = loaded != null && loaded.meta != null && loaded.meta.description != null
                ? loaded.meta.description
                : entry.getShortDescription();

        Table subContent = new Table();
        Runnable rebuildSub = () -> {
            subContent.clearChildren();
            subContent.left().top();
            if (detailTab[0] == 1) {
                Label raw = new Label(fullDesc != null ? fullDesc : "");
                raw.setWrap(true);
                subContent.add(raw).growX().minWidth(0f).left().row();
            } else if (detailTab[0] == 2) {
                int cols = 8;
                int idx = 0;
                for (UnlockableContent uc : contents) {
                    Drawable contentIcon = uc.uiIcon != null ? new TextureRegionDrawable(uc.uiIcon) : Tex.nomap;
                    ImageButton btn = subContent.button(
                            contentIcon,
                            safeImageButtonStyle(Styles.flati),
                            32f,
                            () -> {
                                if (Vars.ui != null && Vars.ui.content != null) {
                                    Vars.ui.content.show(uc);
                                }
                            }).size(42f).pad(2f).get();
                    ModStatusBadges.attachTooltip(btn, uc.localizedName);
                    idx++;
                    if (idx % cols == 0) {
                        subContent.row();
                    }
                }
            } else {
                ModCard.renderFormattedDescription(subContent, fullDesc);
            }
        };

        details.table(ModStatusBadges.darkGrayUI(), desc -> {
            desc.left().top().defaults().pad(6f).left();
            desc.table(ModStatusBadges.grayUI(), switchBar -> {
                switchBar.left().defaults().height(38f);
                ButtonStyle swStyle = ModStatusBadges.switchButStyle();
                Button fmtBtn = switchBar.button(b -> b.add(bundle("feature.mod-manager.tab.formatted", "Formatted"), 0.85f), swStyle, () -> {
                    detailTab[0] = 0;
                    rebuildSub.run();
                }).margin(12f).get();
                fmtBtn.update(() -> fmtBtn.setChecked(detailTab[0] == 0));

                Button rawBtn = switchBar.button(b -> b.add(bundle("editor.description", "Description"), 0.85f), swStyle, () -> {
                    detailTab[0] = 1;
                    rebuildSub.run();
                }).margin(12f).get();
                rawBtn.update(() -> rawBtn.setChecked(detailTab[0] == 1));

                if (!contents.isEmpty()) {
                    Button contBtn = switchBar.button(b -> b.add(bundle("feature.mod-manager.tab.content", "Content") + " (" + contents.size() + ")", 0.85f), swStyle, () -> {
                        detailTab[0] = 2;
                        rebuildSub.run();
                    }).margin(12f).get();
                    contBtn.update(() -> contBtn.setChecked(detailTab[0] == 2));
                }
            }).margin(0f).pad(0f).growX().fillY().minWidth(0f).row();

            desc.add(subContent).growX().minWidth(0f).pad(6f).row();
            rebuildSub.run();
        }).margin(6f).padTop(0f).grow().minWidth(0f).row();

        body.add(details).grow().minWidth(0f);
    }

    private void exportModpack() {
        int stat = ModpackService.evaluateStat(model);
        if (!model.isUncheck() && !PackModel.isStatValid(stat)) {
            if (Vars.ui != null) {
                Vars.ui.showErrorMessage(bundle("feature.mod-manager.packer.invalid", "Modpack configuration is incomplete or has conflicts."));
            }
            optionShow = true;
            return;
        }
        String defaultFileName = (model.getName() != null && !model.getName().trim().isEmpty()
                ? model.getName().trim()
                : "modpack") + ".zip";
        FileChooser.save("zip")
                .name(defaultFileName)
                .submit(dest -> {
                    if (dest == null) {
                        return;
                    }
                    try {
                        Fi target = dest.extension().isEmpty() ? dest.sibling(dest.name() + ".zip") : dest;
                        ModpackService.exportModpack(model, target);
                        if (Vars.ui != null) {
                            Vars.ui.showInfoFade(bundleFormat("feature.mod-manager.packer.exported", target.name()));
                        }
                    } catch (Exception e) {
                        if (Vars.ui != null) {
                            Vars.ui.showException(e);
                        }
                    }
                });
    }

    private void openModpack() {
        FileChooser.open("zip", "jar").submit(file -> {
            if (file == null || !file.exists()) {
                return;
            }
            try {
                disposeCustomIconTexture();
                cleanupTempExtractDir();
                Fi extractDir = (Vars.tmpDirectory != null ? Vars.tmpDirectory : Core.settings.getDataDirectory().child("tmp"))
                        .child("modpack_edit_" + System.currentTimeMillis());
                activeTempExtractDir = extractDir;
                model = ModpackService.readModpack(file, extractDir);
                rebuild();
            } catch (Exception e) {
                cleanupTempExtractDir();
                if (Vars.ui != null) {
                    Vars.ui.showException(e);
                }
            }
        });
    }

    private void promptInstallModpackFile() {
        FileChooser.open("zip", "jar").submit(file -> {
            if (file == null || !file.exists()) {
                return;
            }
            showInstallModpackDialog(file);
        });
    }

    private void showInstallModpackDialog(Fi archiveFile) {
        Fi dataDir = Vars.dataDirectory != null ? Vars.dataDirectory : Core.settings.getDataDirectory();
        Fi previewExtract = dataDir.child("tmp").child("modpack_preview_" + System.currentTimeMillis());
        PackModel preview;
        try {
            preview = ModpackService.readModpack(archiveFile, previewExtract);
        } catch (Exception e) {
            previewExtract.deleteDirectory();
            if (Vars.ui != null) {
                Vars.ui.showException(e);
            }
            return;
        } finally {
            previewExtract.deleteDirectory();
        }

        String[] selectedMode = { preview.getMode() };
        boolean[] createBackup = {
                preview.isRawBackup()
                        || PackModel.MODE_SHADOW.equalsIgnoreCase(preview.getMode())
                        || PackModel.MODE_OVERRIDE.equalsIgnoreCase(preview.getMode())
        };

        BaseDialog dlg = new BaseDialog(bundle("feature.mod-manager.packer.import", "Install Modpack"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        dlg.cont.table(body -> {
            body.top().left().margin(12f).defaults().growX().left().padBottom(8f);
            String title = preview.getDisplayName() != null && !preview.getDisplayName().isEmpty()
                    ? preview.getDisplayName()
                    : preview.getName();
            body.add(title + " [lightgray]v" + preview.getVersion() + "[]").color(Pal.accent).row();
            body.add((preview.getMods() != null ? preview.getMods().size() : 0) + " " + bundle("mods", "Mods")).color(Color.lightGray).row();

            if (!preview.isForce()) {
                body.table(modes -> {
                    modes.defaults().growX().height(40f).pad(2f);
                    TextButtonStyle toggleStyle = safeTextButtonStyle(Styles.flatTogglet);
                    for (String modeOpt : new String[] { PackModel.MODE_DISTRIBUTE, PackModel.MODE_SHADOW, PackModel.MODE_OVERRIDE }) {
                        TextButton b = modes.button(resolveModeLabel(modeOpt), toggleStyle, () -> selectedMode[0] = modeOpt).get();
                        b.update(() -> b.setChecked(modeOpt.equalsIgnoreCase(selectedMode[0])));
                    }
                }).growX().row();
            }

            CheckBox backupCheck = new CheckBox(bundle("feature.mod-manager.packer.option.raw-backup", "Create backup before installing"), safeCheckBoxStyle(Styles.defaultCheck));
            backupCheck.setChecked(createBackup[0]);
            backupCheck.changed(() -> createBackup[0] = backupCheck.isChecked());
            body.add(backupCheck).left().row();

            body.button(bundle("feature.mod-manager.packer.import", "Install Modpack"), Icon.download, safeTextButtonStyle(Styles.flatt), 24f, () -> {
                dlg.hide();
                try {
                    ModpackService.installModpack(archiveFile, dataDir, selectedMode[0], createBackup[0]);
                    ModService.getInstance().notifyInstalledModsChanged();
                    if (Vars.ui != null) {
                        Vars.ui.showInfo(bundle("mod.requiresrestart", "Restart required"));
                    }
                } catch (Exception e) {
                    if (Vars.ui != null) {
                        Vars.ui.showException(e);
                    }
                }
            }).height(46f).padTop(8f).row();
        }).width(440f);

        dlg.show();
    }

    private void showBackupsDialog() {
        Fi dataDir = Vars.dataDirectory != null ? Vars.dataDirectory : Core.settings.getDataDirectory();
        BaseDialog dlg = new BaseDialog(bundle("feature.mod-manager.packer.backups", "Backups"));
        dlg.closeOnBack();
        dlg.addCloseButton();

        Table listTable = new Table();
        Runnable[] rebuildBackups = { null };
        rebuildBackups[0] = () -> {
            listTable.clearChildren();
            listTable.top().left();
            List<Fi> backups = ModpackService.listBackups(dataDir);
            if (backups.isEmpty()) {
                Label empty = new Label(bundle("none.found", "<none found>"));
                empty.setColor(Color.lightGray);
                listTable.add(empty).pad(16f).row();
                return;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            for (Fi backup : backups) {
                listTable.table(ModStatusBadges.darkGrayUIAlpha(), row -> {
                    row.left().margin(8f);
                    row.table(info -> {
                        info.left();
                        info.add(backup.name()).color(Pal.accent).left().row();
                        info.add(fmt.format(new Date(backup.lastModified())), 0.8f).color(Color.lightGray).left().row();
                    }).growX().left();

                    row.button(bundle("feature.mod-manager.packer.backup.restore", "Restore"), Icon.refreshSmall, safeTextButtonStyle(Styles.flatt), 20f, () -> {
                        try {
                            ModpackService.restoreBackupArchive(backup, dataDir);
                            ModService.getInstance().notifyInstalledModsChanged();
                            dlg.hide();
                            if (Vars.ui != null) {
                                Vars.ui.showInfo(bundle("mod.requiresrestart", "Restart required"));
                            }
                        } catch (Exception e) {
                            if (Vars.ui != null) {
                                Vars.ui.showException(e);
                            }
                        }
                    }).height(38f).padRight(4f);

                    row.button(Icon.trash, safeImageButtonStyle(Styles.clearNonei), 24f, () -> {
                        backup.delete();
                        rebuildBackups[0].run();
                    }).size(38f);
                }).growX().padBottom(6f).row();
            }
        };

        dlg.cont.table(body -> {
            body.top().left().margin(10f);
            body.button(bundle("feature.mod-manager.packer.backup.create", "Create Backup"), Icon.save, safeTextButtonStyle(Styles.flatt), 24f, () -> {
                try {
                    Fi created = ModpackService.createBackupArchive(dataDir, "manual");
                    rebuildBackups[0].run();
                    if (Vars.ui != null) {
                        Vars.ui.showInfoFade(bundleFormat("feature.mod-manager.packer.exported", created.name()));
                    }
                } catch (Exception e) {
                    if (Vars.ui != null) {
                        Vars.ui.showException(e);
                    }
                }
            }).growX().height(46f).padBottom(10f).row();

            rebuildBackups[0].run();
            ScrollPane pane = new ScrollPane(listTable, Styles.smallPane);
            pane.setScrollingDisabled(true, false);
            body.add(pane).width(480f).height(340f).row();
        });

        dlg.show();
    }

    private static String sanitizeInternalName(@Nullable String displayName) {
        if (displayName == null) {
            return "";
        }
        return displayName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }
}
