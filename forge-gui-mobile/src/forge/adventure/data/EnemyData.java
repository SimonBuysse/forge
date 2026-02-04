// EnemyData.java (key changes only)
// Goal: if randomizeDeck == true -> 100% pick from decks2 folders matching ANY of enemy's colorSort entries.
// colorSort is now a LIST in JSON. We combine all matching folders into one pool and pick uniformly.

package forge.adventure.data;

import forge.adventure.util.CardUtil;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameFormat;
import forge.model.FModel;
import forge.util.MyRandom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public class EnemyData implements Serializable {
    private static final long serialVersionUID = -3317270785183936320L;

    public String name;
    public String nameOverride;
    public String sprite;
    public String[] deck;
    public boolean copyPlayerDeck = false;
    public String ai;
    public boolean boss = false;
    public boolean flying = false;
    public boolean randomizeDeck = false;
    public float spawnRate;
    public float difficulty;
    public float speed;
    public float scale = 1.0f;
    public int life;
    public RewardData[] rewards;
    public String[] equipment;
    public String colors = "";
    public EnemyData nextEnemy;
    public int teamNumber = -1;

    // CHANGED: was String colorSort = "";
    // JSON now provides an array, so store it as a list.
    public ArrayList<String> colorSort = new ArrayList<>();

    public String[] questTags = new String[0];
    public float lifetime;
    public int gamesPerMatch = 1;
    public boolean usingColorSortDeck = false;

    public static String LAST_ENEMY_FOUGHT = null;
    public static String LAST_CHOSEN_DECK_PATH = null;
    public static boolean LAST_CHOSEN_DECK_WAS_COLORSORT = false;

    // Cache of scanned decks2 folders: folderName -> list of .dck paths
    private static final HashMap<String, ArrayList<String>> DECKS2_FOLDER_CACHE = new HashMap<>();

    public EnemyData() { }

    public EnemyData(EnemyData enemyData) {
        name            = enemyData.name;
        sprite          = enemyData.sprite;
        deck            = enemyData.deck;
        ai              = enemyData.ai;
        boss            = enemyData.boss;
        flying          = enemyData.flying;
        randomizeDeck   = enemyData.randomizeDeck;
        spawnRate       = enemyData.spawnRate;
        copyPlayerDeck  = enemyData.copyPlayerDeck;
        difficulty      = enemyData.difficulty;
        speed           = enemyData.speed;
        scale           = enemyData.scale;
        life            = enemyData.life;
        equipment       = enemyData.equipment;
        colors          = enemyData.colors;
        teamNumber      = enemyData.teamNumber;
        nextEnemy       = enemyData.nextEnemy == null ? null : new EnemyData(enemyData.nextEnemy);
        nameOverride    = enemyData.nameOverride == null ? "" : enemyData.nameOverride;
        questTags       = enemyData.questTags.clone();
        lifetime        = enemyData.lifetime;
        gamesPerMatch   = enemyData.gamesPerMatch;

        // CHANGED: deep copy list
        colorSort = enemyData.colorSort == null ? new ArrayList<>() : new ArrayList<>(enemyData.colorSort);

        if (enemyData.scale == 0.0f) {
            scale = 1.0f;
        }
        if (enemyData.rewards == null) {
            rewards = null;
        } else {
            rewards = new RewardData[enemyData.rewards.length];
            for (int i = 0; i < rewards.length; i++) {
                rewards[i] = new RewardData(enemyData.rewards[i]);
            }
        }
    }

    public Deck generateDeck(boolean isFantasyMode, boolean useGeneticAI) {
        boolean canUseGeneticAI = useGeneticAI && life > 16;

        if (canUseGeneticAI && Config.instance().getSettingData().generateLDADecks) {
            GameFormat fmt = FModel.getFormats().getStandard();
            int rand = MyRandom.getRandom().nextInt(100);
            if (rand > 90) {
                fmt = FModel.getFormats().getLegacy();
            } else if (rand > 50) {
                fmt = FModel.getFormats().getModern();
            }
            return DeckgenUtil.buildLDACArchetypeDeck(fmt, true);
        }

    if (randomizeDeck) {
        String enemyKey = this.getName();

        // Immediate rematch: reuse same deck
        if (enemyKey != null && enemyKey.equals(LAST_ENEMY_FOUGHT) && LAST_CHOSEN_DECK_PATH != null) {
            usingColorSortDeck = LAST_CHOSEN_DECK_WAS_COLORSORT;
            return CardUtil.getDeck(LAST_CHOSEN_DECK_PATH, true, isFantasyMode, colors, life > 13, canUseGeneticAI);
        }

        // Choose % chance to use decks2 (colorSort pools)
        final int COLOR_SORT_PERCENT = 70; // <-- set 0..100 how you want
        boolean useColorSort = MyRandom.getRandom().nextInt(100) < COLOR_SORT_PERCENT;

        String chosen;
        if (useColorSort) {
            chosen = pickOneDeckFromColorSortFolders(colorSort);
            usingColorSortDeck = true;
        } else {
            chosen = deck[MyRandom.getRandom().nextInt(deck.length)];
            usingColorSortDeck = false;
        }

        // Safety fallback (in case folders missing / empty)
        if (chosen == null || chosen.isEmpty()) {
            chosen = deck[MyRandom.getRandom().nextInt(deck.length)];
            usingColorSortDeck = false;
        }

        LAST_ENEMY_FOUGHT = enemyKey;
        LAST_CHOSEN_DECK_PATH = chosen;
        LAST_CHOSEN_DECK_WAS_COLORSORT = usingColorSortDeck;

        return CardUtil.getDeck(chosen, true, isFantasyMode, colors, life > 13, canUseGeneticAI);
    }

        // Non-randomized: original behavior
        return CardUtil.getDeck(
                deck[Current.player().getEnemyDeckNumber(this.getName(), deck.length)],
                true, isFantasyMode, colors, life > 13, canUseGeneticAI
        );
    }

    public String getName() {
        if (nameOverride != null && !nameOverride.isEmpty()) return nameOverride;
        if (name != null && !name.isEmpty()) return name;
        return "(Unnamed Enemy)";
    }

    public boolean match(EnemyData other) {
        if (this.equals(other)) return true;
        if (!this.name.equals(other.name)) return false;
        if (questTags.length != other.questTags.length) return false;
        ArrayList<String> myQuestTags = new ArrayList<>(Arrays.asList(questTags));
        ArrayList<String> otherQuestTags = new ArrayList<>(Arrays.asList(other.questTags));
        myQuestTags.removeAll(otherQuestTags);
        return myQuestTags.isEmpty();
    }

    // -----------------------------
    // decks2 scanning + caching
    // -----------------------------

    private static String pickOneDeckFromColorSortFolders(ArrayList<String> colorSorts) {
        ArrayList<String> pool = getCombinedDeckPool(colorSorts);
        if (pool.isEmpty()) return null;
        return pool.get(MyRandom.getRandom().nextInt(pool.size()));
    }

    private static ArrayList<String> getCombinedDeckPool(ArrayList<String> colorSorts) {
        // Combine + de-dup across folders
        HashSet<String> seen = new HashSet<>();
        ArrayList<String> combined = new ArrayList<>();

        if (colorSorts == null || colorSorts.isEmpty()) return combined;

        for (String raw : colorSorts) {
            String folderName = normalizeFolderName(raw);
            if (folderName.isEmpty()) continue;

            ArrayList<String> decks = getOrScanFolder(folderName);
            if (decks == null || decks.isEmpty()) continue;

            for (String p : decks) {
                if (p == null) continue;
                if (seen.add(p)) combined.add(p);
            }
        }

        return combined;
    }

    private static String normalizeFolderName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if ("colorless".equalsIgnoreCase(s)) return "Colorless";
        // your folders are uppercase (WU, WUB, etc.)
        return s.toUpperCase();
    }

    private static ArrayList<String> getOrScanFolder(String folderName) {
        ArrayList<String> cached = DECKS2_FOLDER_CACHE.get(folderName);
        if (cached != null) return cached;

        String targetPath = "res/adventure/common/decks2/" + folderName;
        FileHandle dir = Gdx.files.internal(targetPath);
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            DECKS2_FOLDER_CACHE.put(folderName, new ArrayList<>());
            return DECKS2_FOLDER_CACHE.get(folderName);
        }

        ArrayList<String> decks = new ArrayList<>();
        scan(dir, decks);

        DECKS2_FOLDER_CACHE.put(folderName, decks);
        return decks;
    }

    private static void scan(FileHandle dir, ArrayList<String> out) {
        if (dir == null || !dir.exists()) return;

        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) {
                scan(f, out);
            } else if ("dck".equalsIgnoreCase(f.extension())) {
                String p = f.path().replace('\\', '/');
                if (p.startsWith("res/")) {
                    p = p.substring("res/".length());
                }
                out.add(p);
            }
        }
    }
}
