package mindustrytool.features.ui.modmanager;

import arc.Core;
import arc.files.Fi;
import arc.func.Cons;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;
import mindustrytool.services.Request;
import mindustrytool.services.Request.BodyHandlers;
import mindustrytool.utils.JsonUtils;
import solim.reactive.Readable;
import solim.reactive.Signal;

public final class ModService {

    public static final String MODS_LIST_PRIMARY_URL =
            "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";
    public static final String MODS_LIST_FALLBACK_URL =
            "https://raw.githubusercontent.com/EB-wilson/HeMindustryMods/master/mods.json";
    public static final String GITHUB_API_URL = "https://api.github.com";
    public static final String MOD_GUIDE_URL = "https://mindustrygame.github.io/wiki/modding/1-modding/";

    private static final int MAX_ICON_CACHE_SIZE = 256;
    private static final int MAX_FAILED_ICON_CACHE_SIZE = 512;
    private static final int MAX_UPDATE_CHECK_CACHE_SIZE = 256;
    private static final long UPDATE_CHECK_COOLDOWN_MS = 5 * 60 * 1000L;

    private static final ModService INSTANCE = new ModService();

    private final Request http = Request.builder()
            .timeout(Duration.ofSeconds(25))
            .build();

    private final Map<String, Drawable> iconCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Drawable>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, Drawable> eldest) {
                    return size() > MAX_ICON_CACHE_SIZE;
                }
            });
    private final Map<String, List<Cons<Drawable>>> pendingIconCallbacks = new ConcurrentHashMap<>();
    private final Set<String> failedIcons = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, Boolean> eldest) {
                    return size() > MAX_FAILED_ICON_CACHE_SIZE;
                }
            }));
    private final List<Runnable> pendingListingCallbacks = new ArrayList<>();
    private final Map<String, Long> lastUpdateCheckMillis = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, Long> eldest) {
                    return size() > MAX_UPDATE_CHECK_CACHE_SIZE;
                }
            });

    private final Signal<List<ModListing>> listingsSignal = Signal.of(Collections.emptyList());
    private final Signal<Boolean> loadingSignal = Signal.of(false);
    private final Signal<String> errorSignal = Signal.of(null);
    private final Signal<Map<String, String>> availableUpdatesSignal = Signal.of(Collections.emptyMap());
    private final Signal<Set<String>> installingReposSignal = Signal.of(Collections.emptySet());
    private final Signal<Integer> installedRevisionSignal = Signal.of(0);

    private ModService() {
    }

    public static ModService getInstance() {
        return INSTANCE;
    }

    public Readable<List<ModListing>> listings() {
        return listingsSignal;
    }

    public Readable<Boolean> loading() {
        return loadingSignal;
    }

    public Readable<String> error() {
        return errorSignal;
    }

    public Readable<Map<String, String>> availableUpdates() {
        return availableUpdatesSignal;
    }

    public Readable<Set<String>> installingRepos() {
        return installingReposSignal;
    }

    public Readable<Integer> installedRevision() {
        return installedRevisionSignal;
    }

    void setAvailableUpdatesForTest(@Nullable Map<String, String> updates) {
        availableUpdatesSignal.set(updates != null
                ? Collections.unmodifiableMap(new HashMap<>(updates))
                : Collections.emptyMap());
    }

    public void notifyInstalledModsChanged() {
        Runnable updateTask = () -> {
            Integer current = installedRevisionSignal.peek();
            installedRevisionSignal.set((current != null ? current : 0) + 1);
        };
        if (Core.app != null) {
            Core.app.post(updateTask);
        } else {
            updateTask.run();
        }
    }

    public void fetchModListings(boolean forceRefresh, @Nullable Runnable onComplete) {
        if (!forceRefresh && !listingsSignal.peek().isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        synchronized (pendingListingCallbacks) {
            if (onComplete != null) {
                pendingListingCallbacks.add(onComplete);
            }
            if (Boolean.TRUE.equals(loadingSignal.peek())) {
                return;
            }
            loadingSignal.set(true);
        }
        postOnMain(() -> errorSignal.set(null));

        http.get(MODS_LIST_PRIMARY_URL)
                .sendAsync()
                .thenApply(resp -> resp != null && resp.statusCode() >= 200 && resp.statusCode() < 300
                        ? resp.body()
                        : null)
                .exceptionally(err -> null)
                .thenCompose(primaryText -> {
                    if (primaryText != null && !primaryText.trim().isEmpty()) {
                        return CompletableFuture.completedFuture(primaryText);
                    }
                    return http.get(MODS_LIST_FALLBACK_URL)
                            .sendAsync()
                            .thenApply(resp -> {
                                if (resp == null
                                        || resp.statusCode() < 200
                                        || resp.statusCode() >= 300
                                        || resp.body() == null
                                        || resp.body().trim().isEmpty()) {
                                    throw new RuntimeException("Fallback mod listing request failed with HTTP status "
                                            + (resp != null ? resp.statusCode() : -1));
                                }
                                return resp.body();
                            });
                })
                .thenAccept(jsonText -> {
                    List<ModListing> parsed = parseListingsJson(jsonText);
                    postOnMain(() -> {
                        listingsSignal.set(Collections.unmodifiableList(parsed));
                        loadingSignal.set(false);
                        errorSignal.set(null);
                        checkInstalledModUpdates();
                        drainPendingListingCallbacks();
                    });
                })
                .exceptionally(err -> {
                    Log.warn("Failed to fetch mod listings: @", err.getMessage());
                    postOnMain(() -> {
                        loadingSignal.set(false);
                        errorSignal.set(err.getMessage() != null ? err.getMessage() : "Request failed");
                        drainPendingListingCallbacks();
                    });
                    return null;
                });
    }

    private void drainPendingListingCallbacks() {
        List<Runnable> callbacks;
        synchronized (pendingListingCallbacks) {
            callbacks = new ArrayList<>(pendingListingCallbacks);
            pendingListingCallbacks.clear();
        }
        for (Runnable callback : callbacks) {
            callback.run();
        }
    }

    public static List<ModListing> parseListingsJson(@Nullable String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<ModListing> list = JsonUtils.fromJsonArray(ModListing.class, jsonText);
        return list != null ? list : Collections.emptyList();
    }

    public void checkInstalledModUpdates() {
        if (Vars.mods == null) {
            return;
        }
        Seq<LoadedMod> mods = Vars.mods.list();
        if (mods == null || mods.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (LoadedMod mod : mods) {
            String repo = resolveModRepo(mod);
            if (repo == null || repo.trim().isEmpty()) {
                continue;
            }
            final String cleanRepo = repo.trim();
            final String repoKey = cleanRepo.toLowerCase(Locale.ROOT);
            Long lastChecked = lastUpdateCheckMillis.get(repoKey);
            if (lastChecked != null && now - lastChecked < UPDATE_CHECK_COOLDOWN_MS) {
                continue;
            }
            lastUpdateCheckMillis.put(repoKey, now);

            final String localVersion = mod.meta != null && mod.meta.version != null ? mod.meta.version : "";
            String releaseApi = GITHUB_API_URL + "/repos/" + cleanRepo + "/releases/latest";

            http.get(releaseApi)
                    .sendAsync()
                    .thenAccept(resp -> {
                        if (resp == null || resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null) {
                            return;
                        }
                        try {
                            JsonNode node = JsonUtils.readTree(resp.body());
                            String remoteTag = extractReleaseVersion(node);
                            if (!remoteTag.isEmpty()
                                    && VersionCompareHelper.tryCompareVersion(localVersion, remoteTag) < 0) {
                                postOnMain(() -> {
                                    Map<String, String> next = new HashMap<>(availableUpdatesSignal.peek());
                                    next.put(repoKey, remoteTag);
                                    availableUpdatesSignal.set(Collections.unmodifiableMap(next));
                                });
                            }
                        } catch (Exception ignored) {
                        }
                    })
                    .exceptionally(err -> null);
        }
    }

    public static @Nullable String resolveModRepo(@Nullable LoadedMod mod) {
        if (mod == null) {
            return null;
        }
        String repo = Core.settings != null ? mod.getRepo() : null;
        if ((repo == null || repo.trim().isEmpty()) && mod.meta != null) {
            repo = mod.meta.repo;
        }
        return repo;
    }

    public @Nullable String getAvailableUpdate(@Nullable LoadedMod mod) {
        String repo = resolveModRepo(mod);
        if (repo == null || repo.trim().isEmpty()) {
            return null;
        }
        return availableUpdatesSignal.peek().get(repo.trim().toLowerCase(Locale.ROOT));
    }

    public @Nullable LoadedMod findInstalledMod(@Nullable ModListing listing) {
        if (listing == null || Vars.mods == null) {
            return null;
        }
        Seq<LoadedMod> installed = Vars.mods.list();
        if (installed == null) {
            return null;
        }
        String listingRepo = listing.getRepo() != null ? listing.getRepo().trim() : "";
        String listingInternal = listing.getInternalName() != null ? listing.getInternalName().trim() : "";
        for (LoadedMod mod : installed) {
            if (mod == null) {
                continue;
            }
            String modRepo = resolveModRepo(mod);
            if (!listingRepo.isEmpty() && modRepo != null && listingRepo.equalsIgnoreCase(modRepo.trim())) {
                return mod;
            }
            if (!listingInternal.isEmpty() && mod.name != null && listingInternal.equalsIgnoreCase(mod.name.trim())) {
                return mod;
            }
        }
        return null;
    }

    public static @Nullable String parseGithubRepo(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.startsWith("https://github.com/")) {
            cleaned = cleaned.substring("https://github.com/".length());
        } else if (cleaned.startsWith("http://github.com/")) {
            cleaned = cleaned.substring("http://github.com/".length());
        } else if (cleaned.startsWith("github.com/")) {
            cleaned = cleaned.substring("github.com/".length());
        }
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        String[] parts = cleaned.split("/");
        if (parts.length < 2) {
            return null;
        }
        String owner = parts[0].trim();
        String repo = parts[1].trim();
        if (owner.isEmpty() || repo.isEmpty()) {
            return null;
        }
        return owner + "/" + repo;
    }

    public static String extractReleaseVersion(@Nullable JsonNode releaseNode) {
        if (releaseNode == null) {
            return "";
        }
        JsonNode tagNode = releaseNode.get("tag_name");
        if (tagNode != null && !tagNode.isNull() && !tagNode.asText().trim().isEmpty()) {
            return tagNode.asText().trim();
        }
        JsonNode nameNode = releaseNode.get("name");
        if (nameNode != null && !nameNode.isNull() && !nameNode.asText().trim().isEmpty()) {
            return nameNode.asText().trim();
        }
        return "";
    }

    public static @Nullable String resolveReleaseAssetUrl(@Nullable JsonNode releaseNode, boolean preferJar) {
        if (releaseNode == null) {
            return null;
        }
        JsonNode assets = releaseNode.get("assets");
        String dexedJarUrl = null;
        String jarUrl = null;
        String zipUrl = null;

        if (assets != null && assets.isArray()) {
            for (JsonNode asset : assets) {
                JsonNode nameNode = asset.get("name");
                JsonNode urlNode = asset.get("browser_download_url");
                if (nameNode == null || urlNode == null) {
                    continue;
                }
                String name = nameNode.asText("").toLowerCase(Locale.ROOT);
                String downloadUrl = urlNode.asText("");
                if (downloadUrl.isEmpty()) {
                    continue;
                }
                if (name.startsWith("dexed") && name.endsWith(".jar")) {
                    dexedJarUrl = downloadUrl;
                } else if (name.endsWith(".jar") && jarUrl == null) {
                    jarUrl = downloadUrl;
                } else if (name.endsWith(".zip") && zipUrl == null) {
                    zipUrl = downloadUrl;
                }
            }
        }

        if (dexedJarUrl != null) {
            return dexedJarUrl;
        }
        if (jarUrl != null) {
            return jarUrl;
        }
        if (zipUrl != null) {
            return zipUrl;
        }
        JsonNode zipball = releaseNode.get("zipball_url");
        if (zipball != null && !zipball.isNull() && !zipball.asText("").trim().isEmpty()) {
            return zipball.asText().trim();
        }
        return null;
    }

    public void installFromRepo(@Nullable String rawRepo, boolean isJavaHint, @Nullable Runnable onSuccess) {
        String parsedRepo = parseGithubRepo(rawRepo);
        if (parsedRepo == null) {
            if (Vars.ui != null) {
                Vars.ui.showErrorMessage(Core.bundle != null ? Core.bundle.get("invalid", "Invalid") : "Invalid");
            }
            return;
        }
        final String repoKey = parsedRepo.toLowerCase(Locale.ROOT);
        if (installingReposSignal.peek().contains(repoKey)) {
            return;
        }
        markInstalling(repoKey, true);

        String releaseApi = GITHUB_API_URL + "/repos/" + parsedRepo + "/releases/latest";
        http.get(releaseApi)
                .sendAsync()
                .thenApply(resp -> {
                    if (resp == null || resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null) {
                        return null;
                    }
                    try {
                        return JsonUtils.readTree(resp.body());
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .exceptionally(err -> null)
                .thenCompose(releaseNode -> {
                    String assetUrl = resolveReleaseAssetUrl(releaseNode, isJavaHint);
                    if (assetUrl == null || assetUrl.isEmpty()) {
                        assetUrl = "https://github.com/" + parsedRepo + "/archive/HEAD.zip";
                    }
                    final String finalUrl = assetUrl;
                    String ext = finalUrl.toLowerCase(Locale.ROOT).endsWith(".jar") ? ".jar" : ".zip";
                    Fi baseTmp = Vars.tmpDirectory != null
                            ? Vars.tmpDirectory
                            : (Core.settings != null ? Core.settings.getDataDirectory().child("tmp") : new Fi("tmp"));
                    Fi tmpDir = baseTmp.child("mindustrytool-mods");
                    tmpDir.mkdirs();
                    Fi dest = tmpDir.child(parsedRepo.replace('/', '_') + "_" + System.currentTimeMillis() + ext);
                    return http.get(finalUrl)
                            .sendAsync(BodyHandlers.ofByteArray())
                            .thenApply(resp -> {
                                if (resp == null
                                        || resp.statusCode() < 200
                                        || resp.statusCode() >= 300
                                        || resp.body() == null) {
                                    throw new RuntimeException("Download failed with HTTP status "
                                            + (resp != null ? resp.statusCode() : -1));
                                }
                                dest.writeBytes(resp.body(), false);
                                return dest;
                            });
                })
                .thenAccept(downloadedFile -> {
                    postOnMain(() -> {
                        try {
                            installDownloadedArchive(downloadedFile, parsedRepo);
                            Map<String, String> nextUpdates = new HashMap<>(availableUpdatesSignal.peek());
                            nextUpdates.remove(repoKey);
                            availableUpdatesSignal.set(Collections.unmodifiableMap(nextUpdates));
                            notifyInstalledModsChanged();
                        } catch (Exception e) {
                            Log.err("Failed to import downloaded mod", e);
                            if (Vars.ui != null) {
                                Vars.ui.showException(e);
                            }
                        } finally {
                            downloadedFile.delete();
                            markInstalling(repoKey, false);
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        }
                    });
                })
                .exceptionally(err -> {
                    Log.err("Failed to download mod from repo " + parsedRepo, err);
                    postOnMain(() -> {
                        markInstalling(repoKey, false);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        if (Vars.ui != null) {
                            Vars.ui.showException(err);
                        }
                    });
                    return null;
                });
    }

    private void installDownloadedArchive(Fi archiveFile, String repo) throws Exception {
        if (Vars.mods == null) {
            return;
        }
        Fi rollbackFile = null;
        Seq<LoadedMod> list = Vars.mods.list();
        if (list != null) {
            for (LoadedMod existing : new Seq<>(list)) {
                if (existing != null && existing.getRepo() != null
                        && existing.getRepo().trim().equalsIgnoreCase(repo)) {
                    if (rollbackFile == null
                            && existing.file != null
                            && existing.file.exists()
                            && !existing.file.isDirectory()) {
                        rollbackFile = archiveFile.sibling("rollback_" + existing.file.name());
                        existing.file.copyTo(rollbackFile);
                    }
                    Vars.mods.removeMod(existing);
                }
            }
        }
        try {
            LoadedMod imported = Vars.mods.importMod(archiveFile);
            if (imported != null) {
                imported.setRepo(repo);
            }
        } catch (Exception e) {
            if (rollbackFile != null && rollbackFile.exists()) {
                try {
                    LoadedMod restored = Vars.mods.importMod(rollbackFile);
                    if (restored != null) {
                        restored.setRepo(repo);
                    }
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (rollbackFile != null && rollbackFile.exists()) {
                rollbackFile.delete();
            }
        }
    }

    public void importLocalModFile(@Nullable Fi file, @Nullable Runnable onSuccess) {
        if (file == null || !file.exists() || Vars.mods == null) {
            return;
        }
        try {
            Vars.mods.importMod(file);
            notifyInstalledModsChanged();
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception e) {
            Log.err("Failed to import local mod file", e);
            if (Vars.ui != null) {
                Vars.ui.showException(e);
            }
        }
    }

    public void loadModIcon(@Nullable String iconUrl, @Nullable Cons<Drawable> callback) {
        if (iconUrl == null || iconUrl.trim().isEmpty() || callback == null) {
            return;
        }
        String cleanUrl = iconUrl.trim();
        Drawable cached = iconCache.get(cleanUrl);
        if (cached != null) {
            callback.get(cached);
            return;
        }
        if (failedIcons.contains(cleanUrl)) {
            return;
        }
        synchronized (pendingIconCallbacks) {
            List<Cons<Drawable>> waiters = pendingIconCallbacks.get(cleanUrl);
            if (waiters != null) {
                waiters.add(callback);
                return;
            }
            List<Cons<Drawable>> initial = new ArrayList<>();
            initial.add(callback);
            pendingIconCallbacks.put(cleanUrl, initial);
        }

        http.get(cleanUrl)
                .sendAsync(BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    if (resp == null || resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().length == 0) {
                        failedIcons.add(cleanUrl);
                        synchronized (pendingIconCallbacks) {
                            pendingIconCallbacks.remove(cleanUrl);
                        }
                        return;
                    }
                    final byte[] bytes = resp.body();
                    postOnMain(() -> {
                        List<Cons<Drawable>> waiters;
                        synchronized (pendingIconCallbacks) {
                            waiters = pendingIconCallbacks.remove(cleanUrl);
                        }
                        try {
                            Pixmap pixmap = new Pixmap(bytes);
                            Texture tex;
                            try {
                                tex = new Texture(pixmap);
                                tex.setFilter(TextureFilter.linear);
                            } finally {
                                pixmap.dispose();
                            }
                            Drawable drawable = new TextureRegionDrawable(new TextureRegion(tex));
                            iconCache.put(cleanUrl, drawable);
                            if (waiters != null) {
                                for (Cons<Drawable> waiter : waiters) {
                                    waiter.get(drawable);
                                }
                            }
                        } catch (Throwable ignored) {
                            failedIcons.add(cleanUrl);
                        }
                    });
                })
                .exceptionally(err -> {
                    failedIcons.add(cleanUrl);
                    synchronized (pendingIconCallbacks) {
                        pendingIconCallbacks.remove(cleanUrl);
                    }
                    return null;
                });
    }

    private void markInstalling(String repoKey, boolean installing) {
        Set<String> next = new HashSet<>(installingReposSignal.peek());
        if (installing) {
            next.add(repoKey);
        } else {
            next.remove(repoKey);
        }
        installingReposSignal.set(Collections.unmodifiableSet(next));
    }

    private static void postOnMain(Runnable runnable) {
        if (Core.app != null) {
            Core.app.post(runnable);
        } else {
            runnable.run();
        }
    }
}
