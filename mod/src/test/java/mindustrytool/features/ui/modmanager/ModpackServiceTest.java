package mindustrytool.features.ui.modmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import arc.Core;
import arc.Settings;
import arc.files.Fi;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import mindustry.Vars;
import mindustrytool.features.ui.modmanager.PackModel.FileEntry;
import mindustrytool.features.ui.modmanager.PackModel.ModEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackServiceTest {

    @TempDir
    File tempDir;

    private Fi dataDir;

    @BeforeEach
    void setUp() {
        dataDir = new Fi(tempDir);
        Core.settings = new Settings();
        Core.settings.setDataDirectory(dataDir);
        Vars.dataDirectory = dataDir;
        Vars.modDirectory = dataDir.child("mods");
        Vars.modDirectory.mkdirs();
    }

    @Test
    void evaluatesPackModelValidationBitmask() {
        PackModel emptyModel = new PackModel();
        int emptyStat = ModpackService.evaluateStat(emptyModel);
        assertTrue(PackModel.hasNonModSelect(emptyStat));
        assertTrue(PackModel.hasMissingInfo(emptyStat));
        assertFalse(PackModel.isStatValid(emptyStat));

        PackModel validModel = new PackModel();
        validModel.setDisplayName("Industrial Pack");
        validModel.setName("industrial-pack");
        validModel.setVersion("1.2.0");
        validModel.setAuthor("Tester");

        Fi dummyModJar = dataDir.child("sample-mod.jar");
        dummyModJar.writeString("dummy-jar-bytes");
        ModEntry modEntry = new ModEntry(
                "sample-mod",
                "Sample Mod",
                "1.0",
                "Tester",
                dummyModJar.name(),
                dummyModJar.file().getAbsolutePath(),
                "Description",
                Arrays.asList("required-dep"),
                ModStat.ENABLED | ModStat.JAR_MOD);
        validModel.getMods().add(modEntry);

        int missingDepStat = ModpackService.evaluateStat(validModel);
        assertTrue(PackModel.hasDependMissing(missingDepStat));
        assertTrue(PackModel.isStatValid(missingDepStat));

        ModEntry depEntry = new ModEntry(
                "required-dep",
                "Required Dep",
                "1.0",
                "Tester",
                dummyModJar.name(),
                dummyModJar.file().getAbsolutePath(),
                "Dep",
                Collections.emptyList(),
                ModStat.ENABLED);
        validModel.getMods().add(depEntry);

        int cleanStat = ModpackService.evaluateStat(validModel);
        assertEquals(0, cleanStat);
        assertTrue(PackModel.isStatValid(cleanStat));

        Fi extraFile = dataDir.child("extra.txt");
        extraFile.writeString("hello");
        validModel.getFiles().add(new FileEntry("saves/slot1.msav", extraFile.file().getAbsolutePath(), "saves"));
        validModel.getFiles().add(new FileEntry("saves\\slot1.msav", extraFile.file().getAbsolutePath(), "saves"));
        int conflictStat = ModpackService.evaluateStat(validModel);
        assertTrue(PackModel.hasFileConflict(conflictStat));
        assertFalse(PackModel.isStatValid(conflictStat));
    }

    @Test
    void exportsAndReadsModpackArchiveRoundTrip() throws IOException {
        PackModel model = new PackModel();
        model.setDisplayName("Test Pack");
        model.setName("test-pack");
        model.setVersion("2.0");
        model.setAuthor("MindustryTool");
        model.setDescription("Round-trip modpack test");
        model.setMode(PackModel.MODE_SHADOW);
        model.setSkipRepeat(false);
        model.setRawBackup(true);
        model.setForce(true);

        Fi modArchive = dataDir.child("test-mod.zip");
        modArchive.writeString("fake-mod-zip-content");
        model.getMods().add(new ModEntry(
                "test-mod",
                "Test Mod",
                "1.1",
                "Author",
                modArchive.name(),
                modArchive.file().getAbsolutePath(),
                "Desc",
                Collections.emptyList(),
                ModStat.ENABLED));

        Fi dirMod = dataDir.child("dir-mod");
        dirMod.child("mod.json").writeString("{\"name\":\"dir-mod\"}");
        model.getMods().add(new ModEntry(
                "dir-mod",
                "Dir Mod",
                "1.0",
                "Author",
                dirMod.name(),
                dirMod.file().getAbsolutePath(),
                "Directory mod",
                Collections.emptyList(),
                ModStat.ENABLED));

        Fi extraSave = dataDir.child("test.msav");
        extraSave.writeString("save-data-payload");
        model.getFiles().add(new FileEntry("saves/test.msav", extraSave.file().getAbsolutePath(), "saves"));

        Fi exportedZip = dataDir.child("exported-pack.zip");
        ModpackService.exportModpack(model, exportedZip);
        assertTrue(exportedZip.exists());

        Fi extractDir = dataDir.child("extracted");
        PackModel loaded = ModpackService.readModpack(exportedZip, extractDir);
        assertEquals("Test Pack", loaded.getDisplayName());
        assertEquals("test-pack", loaded.getName());
        assertEquals("2.0", loaded.getVersion());
        assertEquals("MindustryTool", loaded.getAuthor());
        assertEquals(PackModel.MODE_SHADOW, loaded.getMode());
        assertFalse(loaded.isSkipRepeat());
        assertTrue(loaded.isRawBackup());
        assertTrue(loaded.isForce());
        assertEquals(2, loaded.getMods().size());
        assertEquals("test-mod", loaded.getMods().get(0).getName());
        Fi extractedModFile = new Fi(new File(loaded.getMods().get(0).getSourcePath()));
        assertTrue(extractedModFile.exists());
        assertEquals("fake-mod-zip-content", extractedModFile.readString());
        assertEquals("dir-mod", loaded.getMods().get(1).getName());
        Fi extractedDirModFile = new Fi(new File(loaded.getMods().get(1).getSourcePath()));
        assertTrue(extractedDirModFile.exists());
        assertEquals("dir-mod.zip", extractedDirModFile.name());
        try (ZipFile nestedZip = new ZipFile(extractedDirModFile.file())) {
            assertTrue(nestedZip.getEntry("mod.json") != null);
        }
        assertEquals(1, loaded.getFiles().size());
    }

    @Test
    void rejectsZipSlipPathTraversalWhenReadingModpack() throws IOException {
        Fi maliciousZip = dataDir.child("malicious.zip");
        try (ZipOutputStream zos = new ZipOutputStream(maliciousZip.write(false))) {
            zos.putNextEntry(new ZipEntry("pack.json"));
            zos.write("{\"name\":\"bad\",\"displayName\":\"Bad\"}".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("../../escape.txt"));
            zos.write("pwned".getBytes());
            zos.closeEntry();
        }
        Fi extractDir = dataDir.child("extract_malicious");
        assertThrows(IOException.class, () -> ModpackService.readModpack(maliciousZip, extractDir));
    }

    @Test
    void createsListsAndRestoresBackups() throws IOException {
        Fi installedMod = Vars.modDirectory.child("alpha-mod.jar");
        installedMod.writeString("alpha-v1");

        Fi backupZip = ModpackService.createBackupArchive(dataDir, "before-override");
        assertTrue(backupZip.exists());

        List<Fi> backups = ModpackService.listBackups(dataDir);
        assertEquals(1, backups.size());

        installedMod.delete();
        Fi extraMod = Vars.modDirectory.child("extra-mod.jar");
        extraMod.writeString("extra-v1");
        assertFalse(installedMod.exists());
        assertTrue(extraMod.exists());

        ModpackService.restoreBackupArchive(backupZip, dataDir);
        assertTrue(installedMod.exists());
        assertFalse(extraMod.exists());
        assertEquals("alpha-v1", installedMod.readString());
    }
}
