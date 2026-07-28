package com.lwi.luckytweaks;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Patches lucky block addon config files in memory, so the pack never edits a third-party addon's
 * own files. Patches live in {@code config/luckytweaks/drop_patches/*.txt} and are applied between
 * reading and parsing (see {@code mixin.LuckyLoaderPatchMixin}).
 *
 * <p>Grammar: {@code @addon <folder or zip name>} / {@code @file <name>} / {@code @match} original
 * lines / {@code @replace} ours / {@code @end}. Anchors are matched trimmed, scoped to one addon,
 * and must be a complete drop block -- fragments like {@code group(} would be ambiguous. An anchor
 * matching nowhere (addon updated) warns instead of failing silently; matching several times is
 * applied to all and logged.
 *
 * <p>Runs before Forge configs load, hence the direct disk read. Debug: an empty
 * {@code drop_patches/dump.flag} dumps each patched file's final text to
 * {@code drop_patches_dump/} for diffing. Rationale and proofs: {@code ADDON_PATCHES.md}.
 */
public final class DropPatches {

    private DropPatches() {}

    /** One {@code @match}/{@code @replace} block. */
    private record Patch(List<String> match, List<String> replace, String origin) {}

    /** Patches grouped by addon name, then by file name inside the addon. */
    private static volatile Map<String, Map<String, List<Patch>>> patches = null;
    private static volatile boolean dumpEnabled = false;

    private static Path patchDir() {
        return FMLPaths.CONFIGDIR.get().resolve("luckytweaks").resolve("drop_patches");
    }

    /**
     * Entry point for the mixin. Returns the replacement stream for {@code path} inside the addon at
     * {@code baseDir}, or {@code null} when no patch targets that (addon, file) -- the caller then
     * keeps the loader's original stream, so unpatched files are never re-read or re-encoded.
     */
    public static InputStream maybePatch(File baseDir, String path, InputStream original) {
        if (baseDir == null || path == null || original == null) {
            return null;
        }
        Map<String, List<Patch>> forAddon = forAddon(baseDir.getName());
        if (forAddon == null) {
            return null;
        }
        List<Patch> filePatches = forAddon.get(path.toLowerCase(Locale.ROOT));
        if (filePatches == null || filePatches.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = original.readAllBytes();
            original.close();
            List<String> lines = new ArrayList<>(new String(raw, StandardCharsets.UTF_8).lines().toList());
            int applied = 0;
            for (Patch patch : filePatches) {
                int hits = apply(lines, patch);
                if (hits == 0) {
                    LuckyTweaksMod.LOGGER.warn(
                            "[droppatch] NOT applied in {}/{} (addon updated?): \"{}\"",
                            baseDir.getName(), path, summary(patch));
                } else {
                    applied++;
                    if (hits > 1) {
                        LuckyTweaksMod.LOGGER.info(
                                "[droppatch] applied {} times in {}/{} (duplicated drop): \"{}\"",
                                hits, baseDir.getName(), path, summary(patch));
                    }
                }
            }
            LuckyTweaksMod.LOGGER.info("[droppatch] {}/{}: {}/{} patches applied",
                    baseDir.getName(), path, applied, filePatches.size());
            if (dumpEnabled) {
                dump(baseDir.getName(), path, lines);
            }
            return new java.io.ByteArrayInputStream(
                    String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // If anything goes wrong, let the loader read the file as shipped rather than break the
            // whole addon.
            LuckyTweaksMod.LOGGER.error("[droppatch] failed to patch {}/{} -- addon loads unpatched",
                    baseDir.getName(), path, e);
            return null;
        }
    }

    /**
     * Replaces every occurrence of the patch's anchor block in {@code lines}, comparing trimmed
     * physical lines, and returns how many were replaced. Anchors hold no blank lines (the parser
     * ignores them and they would only make anchors fragile), so the walk CONSUMES blank file lines
     * inside a match region -- an anchor may thus span several drops/comments separated by blank
     * lines, as the Pink {@code natural_gen} patches do. Replacement lines are inserted verbatim,
     * blanks included, so the patched text reproduces the reference exactly.
     */
    private static int apply(List<String> lines, Patch patch) {
        List<String> anchor = patch.match();
        int hits = 0;
        int i = 0;
        while (i < lines.size()) {
            if (lines.get(i).trim().isEmpty()) {
                i++;
                continue;
            }
            int end = matchEnd(lines, i, anchor);
            if (end < 0) {
                i++;
                continue;
            }
            lines.subList(i, end).clear();
            lines.addAll(i, patch.replace());
            i += patch.replace().size();
            hits++;
        }
        return hits;
    }

    /**
     * If the anchor matches at {@code start} (a non-blank line), returns the exclusive end index of
     * the matched region (blank lines inside it included); otherwise {@code -1}.
     */
    private static int matchEnd(List<String> lines, int start, List<String> anchor) {
        int j = 0;
        int k = start;
        while (j < anchor.size()) {
            if (k >= lines.size()) {
                return -1;
            }
            String line = lines.get(k).trim();
            if (line.isEmpty()) {
                k++;
                continue;
            }
            if (!line.equals(anchor.get(j))) {
                return -1;
            }
            j++;
            k++;
        }
        return k;
    }

    /** First anchor line, shortened -- just enough to identify the patch in a log line. */
    private static String summary(Patch patch) {
        String first = patch.match().isEmpty() ? "(empty)" : patch.match().get(0);
        return first.length() > 60 ? first.substring(0, 60) + "..." : first;
    }

    private static Map<String, List<Patch>> forAddon(String addonName) {
        Map<String, Map<String, List<Patch>>> all = patches;
        if (all == null) {
            synchronized (DropPatches.class) {
                all = patches;
                if (all == null) {
                    all = loadAll();
                    patches = all;
                }
            }
        }
        // An addon shipped as a zip carries the ".zip" suffix in its File name; patch files always
        // declare the bare name.
        String name = addonName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        Map<String, List<Patch>> found = all.get(name);
        return (found == null || found.isEmpty()) ? null : found;
    }

    private static Map<String, Map<String, List<Patch>>> loadAll() {
        Map<String, Map<String, List<Patch>>> result = new HashMap<>();
        Path dir = patchDir();
        dumpEnabled = Files.exists(dir.resolve("dump.flag"));
        if (!Files.isDirectory(dir)) {
            return result;
        }
        int count = 0;
        try (var files = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : files) {
                count += parseFile(file, result);
            }
        } catch (IOException e) {
            LuckyTweaksMod.LOGGER.error("[droppatch] cannot list {}", dir, e);
        }
        if (count > 0) {
            LuckyTweaksMod.LOGGER.info("[droppatch] loaded {} patches for {} addon(s){}",
                    count, result.size(), dumpEnabled ? " (dump mode ON)" : "");
        }
        return result;
    }

    /** Parses one patch file into {@code result}; returns the number of patch blocks read. */
    private static int parseFile(Path file, Map<String, Map<String, List<Patch>>> result) {
        List<String> raw;
        try {
            raw = new ArrayList<>(Files.readString(file, StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            LuckyTweaksMod.LOGGER.error("[droppatch] cannot read {}", file.getFileName(), e);
            return 0;
        }
        int count = 0;
        String addon = null;
        String targetFile = null;
        List<String> match = null;
        List<String> replace = null;
        for (int n = 0; n < raw.size(); n++) {
            String line = raw.get(n);
            String trimmed = line.trim();
            // Directives are only ever recognised at the start of a trimmed line; drops lines never
            // begin with '@' (their @luck/@chance modifiers always follow a closing bracket).
            if (trimmed.startsWith("@addon ")) {
                addon = trimmed.substring("@addon ".length()).trim().toLowerCase(Locale.ROOT);
            } else if (trimmed.startsWith("@file ")) {
                targetFile = trimmed.substring("@file ".length()).trim().toLowerCase(Locale.ROOT);
            } else if (trimmed.equals("@match")) {
                match = new ArrayList<>();
                replace = null;
            } else if (trimmed.equals("@replace")) {
                replace = new ArrayList<>();
            } else if (trimmed.equals("@end")) {
                if (addon == null || targetFile == null || match == null || match.isEmpty()
                        || replace == null) {
                    LuckyTweaksMod.LOGGER.warn("[droppatch] malformed block ending at {}:{} -- skipped",
                            file.getFileName(), n + 1);
                } else {
                    result.computeIfAbsent(addon, k -> new HashMap<>())
                            .computeIfAbsent(targetFile, k -> new ArrayList<>())
                            .add(new Patch(List.copyOf(match), List.copyOf(replace),
                                    file.getFileName().toString()));
                    count++;
                }
                match = null;
                replace = null;
            } else if (replace != null) {
                replace.add(line);
            } else if (match != null) {
                // Anchors are matched against trimmed file lines, so store them trimmed. Blank lines
                // are not part of any drop block; keeping them would only make anchors fragile.
                if (!trimmed.isEmpty()) {
                    match.add(trimmed);
                }
            }
            // Outside any block: comments and blank lines, ignored.
        }
        return count;
    }

    private static void dump(String addonName, String path, List<String> lines) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("luckytweaks").resolve("drop_patches_dump");
            Files.createDirectories(dir);
            String safe = (addonName + "__" + path).replaceAll("[^A-Za-z0-9._\\[\\] -]", "_");
            Files.writeString(dir.resolve(safe), String.join("\n", lines), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LuckyTweaksMod.LOGGER.error("[droppatch] dump failed for {}/{}", addonName, path, e);
        }
    }
}
