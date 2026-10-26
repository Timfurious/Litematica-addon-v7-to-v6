package dev.v7compat;

import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Port Java de la partie "descente de version" du convertisseur litematica-converter (index.js).
 *
 * Travaille uniquement sur l'arbre NBT en mémoire, ne touche à aucun fichier.
 * Le contenu est ramené au format "pré-1.20.5" que comprend Litematica sur 1.17.1 :
 *  - palette : short_grass -> grass, fallbacks (crafter, copper_bulb, copper_door, pale_oak, resin...)
 *  - panneaux : front_text/back_text -> Text1..Text4 / Color / GlowingText
 *  - items : count (int) -> Count (byte), components -> tag (NBT classique)
 *  - listes PendingBlockTicks / PendingFluidTicks garanties présentes
 */
public final class SchematicDowngrader {

    private static final String EMPTY_COMPONENT = "{\"text\":\"\"}";
    private static final Set<String> JSON_BOOLEAN_KEYS = new HashSet<>(
            Arrays.asList("bold", "italic", "underlined", "strikethrough", "obfuscated"));

    private SchematicDowngrader() {
    }

    /** Vrai si le compound ressemble à la racine d'un fichier .litematic. */
    public static boolean looksLikeSchematic(NbtCompound root) {
        return root != null
                && root.contains("Version", NbtElement.NUMBER_TYPE)
                && root.contains("Regions", NbtElement.COMPOUND_TYPE)
                && root.contains("Metadata", NbtElement.COMPOUND_TYPE);
    }

    /**
     * Convertit en place. Version / MinecraftDataVersion sont écrits en dernier :
     * si une exception survient, le fichier garde sa version d'origine et Litematica
     * affichera simplement son erreur habituelle.
     */
    public static void convert(NbtCompound root, int targetSchematicVersion, int targetDataVersion) {
        NbtCompound regions = root.getCompound("Regions");

        for (String name : new ArrayList<>(regions.getKeys())) {
            NbtCompound region = regions.getCompound(name);
            convertPalette(region);
            ensureTickLists(region);
        }

        walkSigns(regions);
        walkItems(regions);

        root.putInt("MinecraftDataVersion", targetDataVersion);
        root.putInt("Version", targetSchematicVersion);
    }

    // ------------------------------------------------------------------ palette

    private static void convertPalette(NbtCompound region) {
        NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < palette.size(); i++) {
            convertBlock(palette.getCompound(i));
        }
    }

    static void convertBlock(NbtCompound block) {
        String name = block.getString("Name");
        if (!name.startsWith("minecraft:")) {
            return;
        }
        String bare = name.substring("minecraft:".length());

        // 1.20.3+ : grass -> short_grass (on revient à grass)
        if (bare.equals("short_grass")) {
            setBlock(block, "minecraft:grass", null);
            return;
        }

        // Fallbacks pour les blocs introduits en 1.20.5 / 1.21.x
        if (bare.equals("crafter")) {
            NbtCompound props = block.getCompound("Properties");
            NbtCompound out = new NbtCompound();
            String orientation = props.getString("orientation"); // ex: "north_up", "up_north"
            int us = orientation.indexOf('_');
            out.putString("facing", us > 0 ? orientation.substring(0, us) : "north");
            if (props.contains("triggered")) {
                out.putString("triggered", props.getString("triggered"));
            }
            setBlock(block, "minecraft:dropper", out);
            return;
        }
        if (bare.contains("copper_bulb")) {
            NbtCompound props = block.getCompound("Properties");
            NbtCompound out = new NbtCompound();
            if (props.contains("lit")) {
                out.putString("lit", props.getString("lit"));
            }
            setBlock(block, "minecraft:redstone_lamp", out);
            return;
        }
        if (bare.contains("copper_door")) {
            block.putString("Name", "minecraft:iron_door");
            return;
        }
        if (bare.contains("copper_trapdoor")) {
            block.putString("Name", "minecraft:iron_trapdoor");
            return;
        }

        // Fallbacks pour les blocs de 1.21.4
        if (bare.startsWith("pale_oak_")) {
            block.putString("Name", "minecraft:dark_oak_" + bare.substring("pale_oak_".length()));
            return;
        }
        if (bare.equals("creaking_heart")) {
            block.putString("Name", "minecraft:dark_oak_log");
            return;
        }
        if (bare.startsWith("resin_")) {
            setBlock(block, "minecraft:orange_concrete", null);
        }
    }

    private static void setBlock(NbtCompound block, String name, NbtCompound props) {
        block.putString("Name", name);
        if (props == null || props.getKeys().isEmpty()) {
            block.remove("Properties");
        } else {
            block.put("Properties", props);
        }
    }

    private static void ensureTickLists(NbtCompound region) {
        if (!region.contains("PendingBlockTicks", NbtElement.LIST_TYPE)) {
            region.put("PendingBlockTicks", new NbtList());
        }
        if (!region.contains("PendingFluidTicks", NbtElement.LIST_TYPE)) {
            region.put("PendingFluidTicks", new NbtList());
        }
    }

    // ------------------------------------------------------------------ panneaux

    private static void walkSigns(NbtElement e) {
        if (e instanceof NbtCompound) {
            NbtCompound c = (NbtCompound) e;
            if (c.contains("front_text", NbtElement.COMPOUND_TYPE)) {
                convertSign(c);
            }
            for (String key : new ArrayList<>(c.getKeys())) {
                NbtElement child = c.get(key);
                if (child instanceof NbtCompound || child instanceof NbtList) {
                    walkSigns(child);
                }
            }
        } else if (e instanceof NbtList) {
            for (NbtElement child : (NbtList) e) {
                if (child instanceof NbtCompound || child instanceof NbtList) {
                    walkSigns(child);
                }
            }
        }
    }

    private static void convertSign(NbtCompound sign) {
        NbtCompound front = sign.getCompound("front_text");
        NbtElement messages = front.get("messages");

        for (int i = 0; i < 4; i++) {
            String json = EMPTY_COMPONENT;
            if (messages instanceof NbtList && i < ((NbtList) messages).size()) {
                json = toJsonString(((NbtList) messages).get(i));
            }
            sign.putString("Text" + (i + 1), json);
        }
        sign.putString("Color", front.contains("color", NbtElement.STRING_TYPE) ? front.getString("color") : "black");
        sign.putByte("GlowingText", (byte) (front.getByte("has_glowing_text") != 0 ? 1 : 0));

        sign.remove("front_text");
        sign.remove("back_text");
        sign.remove("is_waxed");
    }

    // ------------------------------------------------------------------ items

    private static void walkItems(NbtElement e) {
        if (e instanceof NbtCompound) {
            NbtCompound c = (NbtCompound) e;
            if (looksLikeItem(c)) {
                convertItem(c);
            }
            // les clés sont relues APRÈS la conversion : on descend aussi dans le "tag" fraîchement créé
            for (String key : new ArrayList<>(c.getKeys())) {
                NbtElement child = c.get(key);
                if (child instanceof NbtCompound || child instanceof NbtList) {
                    walkItems(child);
                }
            }
        } else if (e instanceof NbtList) {
            for (NbtElement child : (NbtList) e) {
                if (child instanceof NbtCompound || child instanceof NbtList) {
                    walkItems(child);
                }
            }
        }
    }

    private static boolean looksLikeItem(NbtCompound c) {
        if (!c.contains("id", NbtElement.STRING_TYPE)) {
            return false;
        }
        // un block entity a toujours x/y/z : ce n'est pas un item
        if (c.contains("x") && c.contains("y") && c.contains("z")) {
            return false;
        }
        return c.contains("Count", NbtElement.NUMBER_TYPE)
                || c.contains("count", NbtElement.NUMBER_TYPE)
                || c.contains("components", NbtElement.COMPOUND_TYPE)
                || c.contains("Slot", NbtElement.NUMBER_TYPE);
    }

    static void convertItem(NbtCompound item) {
        // 1. count (int, >= 1.20.5) -> Count (byte)
        if (item.contains("count", NbtElement.NUMBER_TYPE)) {
            int v = item.getInt("count");
            v = Math.max(-128, Math.min(127, v));
            item.remove("count");
            item.putByte("Count", (byte) v);
        }

        // 2. components -> tag
        if (!item.contains("components", NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound comps = item.getCompound("components");
        NbtCompound tag = item.contains("tag", NbtElement.COMPOUND_TYPE) ? item.getCompound("tag") : new NbtCompound();

        if (comps.contains("minecraft:custom_data", NbtElement.COMPOUND_TYPE)) {
            NbtCompound data = comps.getCompound("minecraft:custom_data");
            for (String k : new ArrayList<>(data.getKeys())) {
                tag.put(k, data.get(k).copy());
            }
        }
        if (comps.contains("minecraft:damage", NbtElement.NUMBER_TYPE)) {
            tag.putInt("Damage", comps.getInt("minecraft:damage"));
        }
        if (comps.contains("minecraft:repair_cost", NbtElement.NUMBER_TYPE)) {
            tag.putInt("RepairCost", comps.getInt("minecraft:repair_cost"));
        }
        if (comps.contains("minecraft:unbreakable")) {
            tag.putByte("Unbreakable", (byte) 1);
        }
        if (comps.contains("minecraft:custom_model_data", NbtElement.NUMBER_TYPE)) {
            tag.putInt("CustomModelData", comps.getInt("minecraft:custom_model_data"));
        }
        if (comps.contains("minecraft:custom_name")) {
            sub(tag, "display").putString("Name", toJsonString(comps.get("minecraft:custom_name")));
        }
        if (comps.get("minecraft:lore") instanceof NbtList) {
            NbtList lore = new NbtList();
            for (NbtElement line : (NbtList) comps.get("minecraft:lore")) {
                lore.add(NbtString.of(toJsonString(line)));
            }
            sub(tag, "display").put("Lore", lore);
        }
        if (comps.contains("minecraft:enchantments", NbtElement.COMPOUND_TYPE)) {
            NbtList list = enchantmentList(comps.getCompound("minecraft:enchantments"));
            if (!list.isEmpty()) {
                tag.put("Enchantments", list);
            }
        }
        if (comps.contains("minecraft:stored_enchantments", NbtElement.COMPOUND_TYPE)) {
            NbtList list = enchantmentList(comps.getCompound("minecraft:stored_enchantments"));
            if (!list.isEmpty()) {
                tag.put("StoredEnchantments", list);
            }
        }
        NbtElement potion = comps.get("minecraft:potion_contents");
        if (potion instanceof NbtCompound && ((NbtCompound) potion).contains("potion", NbtElement.STRING_TYPE)) {
            tag.putString("Potion", ((NbtCompound) potion).getString("potion"));
        } else if (potion instanceof NbtString) {
            tag.putString("Potion", potion.asString());
        }

        // Contenu d'un shulker box / coffre en item : components -> BlockEntityTag.Items
        if (comps.contains("minecraft:block_entity_data", NbtElement.COMPOUND_TYPE)) {
            NbtCompound bed = comps.getCompound("minecraft:block_entity_data");
            NbtCompound bet = sub(tag, "BlockEntityTag");
            for (String k : new ArrayList<>(bed.getKeys())) {
                if (!k.equals("id")) {
                    bet.put(k, bed.get(k).copy());
                }
            }
        }
        if (comps.get("minecraft:container") instanceof NbtList) {
            NbtList items = new NbtList();
            for (NbtElement el : (NbtList) comps.get("minecraft:container")) {
                if (!(el instanceof NbtCompound) || !((NbtCompound) el).contains("item", NbtElement.COMPOUND_TYPE)) {
                    continue;
                }
                NbtCompound entry = (NbtCompound) el;
                NbtCompound inner = entry.getCompound("item").copy();
                inner.putByte("Slot", (byte) entry.getInt("slot"));
                items.add(inner);
            }
            if (!items.isEmpty()) {
                sub(tag, "BlockEntityTag").put("Items", items);
            }
        }

        item.remove("components");
        if (!tag.getKeys().isEmpty()) {
            item.put("tag", tag);
        }
    }

    /** 1.21.4- : {levels:{"minecraft:x":1}} ; 1.21.5+ : {"minecraft:x":1} directement. */
    private static NbtList enchantmentList(NbtCompound ench) {
        NbtCompound levels = ench.contains("levels", NbtElement.COMPOUND_TYPE) ? ench.getCompound("levels") : ench;
        NbtList out = new NbtList();
        for (String id : new ArrayList<>(levels.getKeys())) {
            if (id.equals("show_in_tooltip")) {
                continue;
            }
            NbtElement v = levels.get(id);
            if (!(v instanceof AbstractNbtNumber)) {
                continue;
            }
            NbtCompound e = new NbtCompound();
            e.putString("id", id);
            e.putShort("lvl", (short) ((AbstractNbtNumber) v).intValue());
            out.add(e);
        }
        return out;
    }

    private static NbtCompound sub(NbtCompound parent, String key) {
        if (!parent.contains(key, NbtElement.COMPOUND_TYPE)) {
            parent.put(key, new NbtCompound());
        }
        return parent.getCompound(key);
    }

    // ------------------------------------------------------------------ composants de texte -> JSON

    /** Texte (String JSON, String brute ou compound NBT 1.20.3+) -> chaîne JSON attendue par 1.17.1. */
    static String toJsonString(NbtElement e) {
        if (e == null) {
            return EMPTY_COMPONENT;
        }
        if (e instanceof NbtString) {
            String s = e.asString();
            if (s.isEmpty()) {
                return EMPTY_COMPONENT;
            }
            char c = s.charAt(0);
            if (c == '{' || c == '[' || c == '"') {
                return s; // déjà du JSON
            }
            return "{\"text\":" + quote(s) + "}";
        }
        StringBuilder sb = new StringBuilder();
        writeJson(sb, e, null);
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, NbtElement e, String key) {
        if (e instanceof NbtCompound) {
            NbtCompound c = (NbtCompound) e;
            sb.append('{');
            boolean first = true;
            for (String k : c.getKeys()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(k)).append(':');
                writeJson(sb, c.get(k), k);
            }
            sb.append('}');
        } else if (e instanceof NbtList) {
            sb.append('[');
            boolean first = true;
            for (NbtElement child : (NbtList) e) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(sb, child, null);
            }
            sb.append(']');
        } else if (e instanceof NbtString) {
            sb.append(quote(e.asString()));
        } else if (e instanceof AbstractNbtNumber) {
            AbstractNbtNumber n = (AbstractNbtNumber) e;
            if (e.getType() == NbtElement.BYTE_TYPE && key != null && JSON_BOOLEAN_KEYS.contains(key)) {
                sb.append(n.byteValue() != 0);
            } else {
                sb.append(n.numberValue());
            }
        } else {
            sb.append(quote(e.asString()));
        }
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
