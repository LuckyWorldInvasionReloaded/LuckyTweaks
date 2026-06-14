package com.lwi.luckytweaks;

import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The run-integrity "seal": a sticky, per-player record of whether anything that would invalidate a
 * speedrun has happened. Stored in (and synced/saved by) the Lucky Stats per-player compound, so it
 * travels with the world save and shows on the Lucky Stats screen. Once a reason bit is set it is
 * never cleared.
 *
 * <p>This class is pure logic over a {@link CompoundTag} -- no Lucky Stats imports -- so it loads on
 * client and server alike. The keyed {@link #checksum} makes naive save-editing detectable: flip the
 * reasons in NBT without recomputing the checksum and a read flags {@link #TAMPERED}. As with any
 * client-side scheme it is defeatable by extracting the secret from the jar -- it stops casual
 * cheating, not a determined modder (for that, host the contest on a server).
 */
public final class RunSeal {
    public static final int MODIFIED_CONFIG = 1;
    public static final int CREATIVE = 2;
    public static final int CHEATS = 4;
    public static final int NOT_HARDCORE = 8;
    public static final int TAMPERED = 16;

    public static final String KEY_REASONS = "lt_seal";
    public static final String KEY_CHECKSUM = "lt_sealc";

    // Obfuscation secret for the tamper-evidence checksum. Extractable from the jar by a determined
    // cheater (no client-side secret can be otherwise) -- its job is to defeat CASUAL save-editing.
    private static final byte[] SECRET = "lwi:run-seal:2026:v1".getBytes(StandardCharsets.UTF_8);

    private RunSeal() {}

    public static boolean present(CompoundTag stats) {
        return stats.contains(KEY_REASONS);
    }

    public static int reasons(CompoundTag stats) {
        return stats.getInt(KEY_REASONS);
    }

    /** Keyed checksum binding the reasons to this player; a mismatch on read means the NBT was edited. */
    public static long checksum(int reasons, UUID uuid) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SECRET);
            long hi = uuid.getMostSignificantBits();
            long lo = uuid.getLeastSignificantBits();
            byte[] buf = new byte[20];
            for (int i = 0; i < 8; i++) {
                buf[i] = (byte) (hi >>> (56 - i * 8));
                buf[8 + i] = (byte) (lo >>> (56 - i * 8));
            }
            buf[16] = (byte) (reasons >>> 24);
            buf[17] = (byte) (reasons >>> 16);
            buf[18] = (byte) (reasons >>> 8);
            buf[19] = (byte) reasons;
            md.update(buf);
            byte[] d = md.digest();
            long c = 0;
            for (int i = 0; i < 8; i++) {
                c = (c << 8) | (d[i] & 0xFF);
            }
            return c;
        } catch (NoSuchAlgorithmException e) {
            return (reasons * 0x9E3779B97F4A7C15L) ^ uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        }
    }

    public static boolean verify(CompoundTag stats, UUID uuid) {
        return present(stats) && stats.getLong(KEY_CHECKSUM) == checksum(reasons(stats), uuid);
    }

    /** Persist reasons + a fresh checksum (initialises the seal if it didn't exist yet). */
    public static void write(CompoundTag stats, int reasons, UUID uuid) {
        stats.putInt(KEY_REASONS, reasons);
        stats.putLong(KEY_CHECKSUM, checksum(reasons, uuid));
    }

    /** Whether any player-facing Lucky Tweaks option differs from its default (mirrors the defaults). */
    public static boolean isConfigNonDefault() {
        return !TweaksConfig.FIX_LUCKY_WEAPONS.get()
                || !TweaksConfig.ENABLE_LUCK_FUSION.get()
                || TweaksConfig.LUCKY_BLOCK_SPAWN_MULTIPLIER.get() != 1.0
                || !TweaksConfig.DISABLED_LUCKY_BLOCKS.get().isEmpty()
                || !TweaksConfig.SPAWN_RULES.get().isEmpty();
    }

    /** Human-readable reasons for a tainted seal (rendered on the Lucky Stats screen). */
    public static List<String> reasonLabels(int reasons) {
        List<String> out = new ArrayList<>();
        if ((reasons & MODIFIED_CONFIG) != 0) out.add("Modified config");
        if ((reasons & CREATIVE) != 0) out.add("Creative/spectator used");
        if ((reasons & CHEATS) != 0) out.add("Cheats enabled");
        if ((reasons & NOT_HARDCORE) != 0) out.add("Not hardcore");
        if ((reasons & TAMPERED) != 0) out.add("Save edited");
        return out;
    }
}
