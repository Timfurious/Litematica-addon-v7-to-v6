package dev.v7compat;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Colle entre le hook Mixin et le convertisseur.
 * Appelé à chaque lecture d'un NBT compressé ; ne fait quelque chose que si le compound
 * est une schématique Litematica dont la version dépasse ce que Litematica sait charger.
 */
public final class V7Compat {
    public static final Logger LOGGER = LogManager.getLogger("litematica_v7_compat");

    /** Dernière version de schématique que Litematica (1.17.1) accepte, 6 par défaut. */
    private static final int DEFAULT_MAX_VERSION = 6;
    private static volatile int maxSupportedVersion = -1;

    private V7Compat() {
    }

    public static void apply(NbtCompound root) {
        try {
            if (!SchematicDowngrader.looksLikeSchematic(root)) {
                return;
            }
            int version = root.getInt("Version");
            int max = getMaxSupportedVersion();
            if (version <= max) {
                return; // Litematica sait déjà la lire telle quelle
            }

            int sourceDataVersion = root.getInt("MinecraftDataVersion");
            int gameDataVersion = SharedConstants.getGameVersion().getWorldVersion();
            SchematicDowngrader.convert(root, max, gameDataVersion);

            LOGGER.info("Schématique v{} (DataVersion {}) convertie en mémoire vers v{} (DataVersion {})",
                    version, sourceDataVersion, max, gameDataVersion);
        } catch (Throwable t) {
            // En cas d'échec, la racine garde sa Version d'origine : Litematica affichera son erreur normale.
            LOGGER.error("Conversion v7 -> v6 impossible, la schématique est laissée telle quelle", t);
        }
    }

    /**
     * Lit LitematicaSchematic.SCHEMATIC_VERSION par réflexion : si une version de Litematica
     * gère déjà la v7, le mod devient automatiquement inactif.
     */
    private static int getMaxSupportedVersion() {
        int cached = maxSupportedVersion;
        if (cached > 0) {
            return cached;
        }
        int value = DEFAULT_MAX_VERSION;
        try {
            Class<?> c = Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic");
            value = c.getField("SCHEMATIC_VERSION").getInt(null);
        } catch (Throwable t) {
            LOGGER.warn("SCHEMATIC_VERSION introuvable par réflexion, utilisation de {}", DEFAULT_MAX_VERSION);
        }
        maxSupportedVersion = value;
        return value;
    }
}
