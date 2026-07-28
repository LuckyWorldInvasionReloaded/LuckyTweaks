package com.lwi.luckytweaks;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * First-launch download of lucky block addons the pack is not allowed to redistribute.
 *
 * <p>Why: Lucky Block Pink's own {@code README.TXT} forbids redistributing its content but allows
 * downloading it from the official link -- the same situation OptiFine had for years, solved the
 * same way. Instead of shipping the addon inside the pack, the pack ships an entry in
 * {@code config/luckytweaks/addon_downloads.txt} and this class fetches the author's official
 * CurseForge file into {@code addons/lucky/} the first time the game starts without it. The
 * Lucky Block mod loads zipped addons natively, and the {@link DropPatches} overlay matches zip
 * names with or without the extension, so a fetched zip behaves exactly like an unpacked folder.
 *
 * <p>Config grammar, one addon per line ({@code #} comments allowed):
 * <pre>target zip name | sha256 | size in bytes | url</pre>
 *
 * <p>Runs from {@code mixin.LuckyAddonFetchMixin} at the HEAD of the Lucky Block loader's addon
 * scan ({@code findAddonsOrMakeDir}) -- the only point that is guaranteed to run before addon
 * discovery whatever the mod-construction order. The download blocks that scan a few seconds on
 * the first launch only; every later launch sees the file (or the pre-existing 1.3.1 folder,
 * which also counts as present) and does nothing.
 *
 * <p>Failure is soft by design: a download or checksum problem logs a clear message telling the
 * player to fetch the file manually from the official page, deletes any partial file, and lets
 * the game boot without the addon rather than crash or hang. The checksum is mandatory -- a file
 * that does not match the pinned sha256/size byte for byte is never installed.
 */
public final class AddonDownloads {

    private AddonDownloads() {}

    /** One config line. */
    private record Entry(String fileName, String sha256, long size, String url) {}

    private static volatile boolean done = false;

    /** Mixin entry point: makes sure every configured addon is present, downloading if needed. */
    public static void ensureAddons() {
        if (done) {
            return;
        }
        done = true;
        List<Entry> entries = load();
        if (entries.isEmpty()) {
            return;
        }
        Path addonsDir = FMLPaths.GAMEDIR.get().resolve("addons").resolve("lucky");
        for (Entry entry : entries) {
            try {
                ensure(addonsDir, entry);
            } catch (Exception e) {
                LuckyTweaksMod.LOGGER.error(
                        "[addonfetch] could not fetch '{}' -- the game will run without it. "
                        + "To install it manually, download the file from the addon's official "
                        + "CurseForge page and place it in addons/lucky/. Cause:",
                        entry.fileName(), e);
            }
        }
    }

    private static void ensure(Path addonsDir, Entry entry) throws Exception {
        Path target = addonsDir.resolve(entry.fileName());
        if (Files.exists(target)) {
            return;
        }
        // A 1.3.1-era install (or a manual unpack) has the addon as a FOLDER with the same base
        // name: that counts as present, never download a duplicate next to it -- the Lucky Block
        // mod would load the addon twice.
        String baseName = entry.fileName().endsWith(".zip")
                ? entry.fileName().substring(0, entry.fileName().length() - 4)
                : entry.fileName();
        if (Files.isDirectory(addonsDir.resolve(baseName))) {
            return;
        }
        Files.createDirectories(addonsDir);
        LuckyTweaksMod.LOGGER.info("[addonfetch] '{}' is missing, downloading from the official "
                + "source ({} KB)...", entry.fileName(), entry.size() / 1024);

        Path part = addonsDir.resolve(entry.fileName() + ".part");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(entry.url()).openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "luckytweaks (Lucky World Invasion Reloaded)");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[65536];
                try (var out = Files.newOutputStream(part)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        digest.update(buf, 0, n);
                        total += n;
                        if (total > entry.size() + 1_000_000) {
                            throw new IOException("downloaded file is much larger than expected");
                        }
                    }
                }
            }
            if (total != entry.size()) {
                throw new IOException("size mismatch: got " + total + ", expected " + entry.size());
            }
            String got = toHex(digest.digest());
            if (!got.equalsIgnoreCase(entry.sha256())) {
                throw new IOException("sha256 mismatch: got " + got);
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            LuckyTweaksMod.LOGGER.info("[addonfetch] '{}' downloaded and verified (sha256 OK)",
                    entry.fileName());
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static List<Entry> load() {
        List<Entry> entries = new ArrayList<>();
        Path file = FMLPaths.CONFIGDIR.get().resolve("luckytweaks").resolve("addon_downloads.txt");
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try {
            for (String line : Files.readString(file, StandardCharsets.UTF_8).lines().toList()) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                String[] parts = t.split("\\|");
                if (parts.length != 4) {
                    LuckyTweaksMod.LOGGER.warn("[addonfetch] malformed line skipped: {}", t);
                    continue;
                }
                entries.add(new Entry(parts[0].trim(), parts[1].trim(),
                        Long.parseLong(parts[2].trim()), parts[3].trim()));
            }
        } catch (IOException | NumberFormatException e) {
            LuckyTweaksMod.LOGGER.error("[addonfetch] cannot read {}", file, e);
        }
        return entries;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
