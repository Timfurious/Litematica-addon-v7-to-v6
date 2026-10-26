# Litematica V7 Compat (Fabric 1.17.1)

Addon pour **Litematica 1.17.1** : quand une schématique est de **format v7** (créée sur Minecraft 1.20.5+ / 1.21+ / 26.x),
elle est convertie **en mémoire** au moment du chargement, puis chargée normalement.

- Pas d'interface, pas de bouton, pas de config.
- Aucun fichier créé ni modifié : le `.litematic` d'origine reste intact.
- Les schématiques v6 (et inférieures) ne sont pas touchées.
- Si une version de Litematica gère un jour la v7 elle-même, le mod ne fait plus rien tout seul
  (il lit `LitematicaSchematic.SCHEMATIC_VERSION` par réflexion).

La logique est un port Java de la descente de version de `litematica-converter` (index.js) :

| Élément | Conversion |
|---|---|
| Palette de blocs | `short_grass` → `grass`, `crafter` → `dropper`, `copper_bulb` → `redstone_lamp`, `copper_door/trapdoor` → `iron_*`, `pale_oak_*` → `dark_oak_*`, `creaking_heart` → `dark_oak_log`, `resin_*` → `orange_concrete` |
| Panneaux | `front_text` / `back_text` / `is_waxed` → `Text1..4`, `Color`, `GlowingText` |
| Items (coffres, item frames, shulkers...) | `count` (int) → `Count` (byte), `components` → `tag` (dégâts, enchantements, nom, lore, potion, custom_data, contenu des shulkers) |
| Ticks | `PendingBlockTicks` / `PendingFluidTicks` ajoutés s'ils manquent |
| En-tête | `Version` = 6, `MinecraftDataVersion` = version du jeu (2730) |

## Compiler

Prérequis : **JDK 17** (Gradle 7.3 ne supporte pas JDK 21).

```
gradle build          # avec Gradle 7.3.x installé
```

ou ouvrir le dossier dans IntelliJ IDEA (import Gradle automatique) puis tâche `build`.
Le jar sort dans `build/libs/litematica-v7-compat-1.0.0.jar`.

Sans environnement de dev : pousser ce dossier sur un dépôt GitHub, l'onglet **Actions** compile le jar
(workflow `.github/workflows/build.yml`) et le propose en téléchargement.

## Installer

Mettre le jar dans `.minecraft/mods/` avec Fabric Loader, Litematica et MaLiLib (1.17.1).
Quand une schématique v7 est convertie, une ligne apparaît dans `logs/latest.log` :
`[litematica_v7_compat] Schématique v7 (DataVersion ...) convertie en mémoire vers v6 ...`

## Limites

- Comme le convertisseur d'origine, seuls les blocs de la table ci-dessus sont remplacés ; un bloc inexistant en 1.17.1
  (ex. cerisier, tuff bricks...) est simplement ignoré par Litematica.
- Les données d'entités / block entities propres à 1.20.5+ (autres que panneaux et items) ne sont pas converties.
