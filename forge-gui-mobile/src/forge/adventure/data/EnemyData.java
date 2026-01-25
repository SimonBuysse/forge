package forge.adventure.data;

import forge.adventure.util.CardUtil;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameFormat;
import forge.model.FModel;
import forge.util.Aggregates;
import forge.util.MyRandom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Data class that will be used to read Json configuration files
 * BiomeData
 * contains the information of enemies
 */
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
    private static List<String> ALL_DCK_PATHS = null;
    public EnemyData nextEnemy;
    public int teamNumber = -1;
    public String colorSort = "";
    private static Map<String, List<String>> DCK_PATHS_BY_COLOR_SORT = null;
    public String[] questTags = new String[0];
    public float lifetime;
    public int gamesPerMatch = 1;
    // Accepts: BR, UBRG, UBRG+URG, etc. (only WUBRG groups separated by '+')
    private static final Pattern COLOR_SORT_FOLDER =
            Pattern.compile("^[WUBRG]{1,5}(\\+[WUBRG]{1,5})*$", Pattern.CASE_INSENSITIVE);
    private static String LAST_ENEMY_FOUGHT = null;
    private static String LAST_CHOSEN_DECK_PATH = null;

    public EnemyData() {
    }

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
        colorSort       = enemyData.colorSort;
        if (enemyData.scale == 0.0f) {
            scale = 1.0f;
        }
        if (enemyData.rewards == null) {
            rewards = null;
        } else {
            rewards = new RewardData[enemyData.rewards.length];
            for (int i = 0; i < rewards.length; i++)
                rewards[i] = new RewardData(enemyData.rewards[i]);
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
        // Randomized: prefer the folder that matches colorSort
        if (randomizeDeck) {
            String chosen;
            String enemyKey = this.getName();
            // If this is an immediate rematch, reuse the same chosen deck
            if (enemyKey != null && enemyKey.equals(LAST_ENEMY_FOUGHT) && LAST_CHOSEN_DECK_PATH != null) {
                return CardUtil.getDeck(LAST_CHOSEN_DECK_PATH, true, isFantasyMode, colors, life > 13, canUseGeneticAI);
            }
            if (MyRandom.percentTrue(20)) {
                // 20%: decks2 by colorSort
                chosen = pickOneDeckFromColorSortFolder(colorSort);
                if (chosen == null || chosen.isEmpty()) {
                    chosen = deck[MyRandom.getRandom().nextInt(deck.length)];
                }
            } else {
                // 80%: enemy's own deck[]
                chosen = deck[MyRandom.getRandom().nextInt(deck.length)];
            }
            // Remember for a possible retry
            LAST_ENEMY_FOUGHT = enemyKey;
            LAST_CHOSEN_DECK_PATH = chosen;
            return CardUtil.getDeck(chosen, true, isFantasyMode, colors, life > 13, canUseGeneticAI);
        }
        // Non-randomized: original behavior (enemy-defined list with per-enemy cycling)
        return CardUtil.getDeck(
                deck[Current.player().getEnemyDeckNumber(this.getName(), deck.length)],
                true, isFantasyMode, colors, life > 13, canUseGeneticAI
        );
    }

    public String getName(){
        //todo: make this the default accessor for anything seen in UI
        if (nameOverride != null && !nameOverride.isEmpty())
            return nameOverride;
        if (name != null && !name.isEmpty())
            return name;
        return "(Unnamed Enemy)";
    }

    public boolean match(EnemyData other) {
        //equals() does not cover cases where data is updated to override speed, displayname, etc
        if (this.equals(other))
            return true;
        if (!this.name.equals(other.name))
            return false;
        if (questTags.length != other.questTags.length)
            return false;
        ArrayList<String> myQuestTags = new ArrayList<>(Arrays.asList(questTags));
        ArrayList<String> otherQuestTags = new ArrayList<>(Arrays.asList(other.questTags));
        myQuestTags.removeAll(otherQuestTags);
        return myQuestTags.isEmpty();
    }

    // -----------------------------
    // decks2 scanning + caching
    // -----------------------------

    private static String pickOneDeckFromColorSortFolder(String colorSort) {
        String folder = (colorSort == null) ? "" : colorSort.trim();
        String folderName = "colorless".equalsIgnoreCase(folder) ? "Colorless" : folder.toUpperCase();
        String targetPath = "res/adventure/common/decks2/" + folderName;

        Gdx.app.log("EnemyData", "---- pickOneDeckFromColorSortFolder DEBUG ----");
        Gdx.app.log("EnemyData", "input colorSort='" + colorSort + "' -> folderName='" + folderName + "'");
        Gdx.app.log("EnemyData", "targetPath='" + targetPath + "'");

        FileHandle dir = Gdx.files.internal(targetPath);
        Gdx.app.log("EnemyData", "dir.path()='" + (dir == null ? "null" : dir.path()) + "'");
        Gdx.app.log("EnemyData", "exists=" + (dir != null && dir.exists()) + " isDirectory=" + (dir != null && dir.isDirectory()));

        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Gdx.app.log("EnemyData", "ABORT: folder missing or not a directory: " + targetPath);
            return null;
        }

        // Print first-level contents so we know what LibGDX thinks is inside UR
        FileHandle[] kids = dir.list();
        Gdx.app.log("EnemyData", "first-level entries in " + dir.path() + " = " + (kids == null ? 0 : kids.length));
        if (kids != null) {
            int shown = 0;
            for (FileHandle k : kids) {
                if (shown++ >= 20) break;
                Gdx.app.log("EnemyData", "  - " + k.name() + (k.isDirectory() ? " [dir]" : " [file]"));
            }
        }

        ArrayList<String> decks = new ArrayList<>();
        scan(dir, decks);

        Gdx.app.log("EnemyData", "recursive .dck count=" + decks.size());
        for (int i = 0; i < Math.min(5, decks.size()); i++) {
            Gdx.app.log("EnemyData", "sample[" + i + "]=" + decks.get(i));
        }

        // HARD GUARANTEE: all must be under /decks2/<FOLDERNAME>/
        String needle = ("/decks2/" + folderName + "/").toLowerCase();
        int bad = 0;
        for (String p : decks) {
            String norm = (p == null ? "" : p.replace('\\', '/').toLowerCase());
            if (!norm.contains(needle)) {
                bad++;
                if (bad <= 10) {
                    Gdx.app.log("EnemyData", "BAD PATH (not in " + needle + "): " + p);
                }
            }
        }
        Gdx.app.log("EnemyData", "badPaths=" + bad + " (should be 0)");

        if (decks.isEmpty()) return null;

        String chosen = decks.get(MyRandom.getRandom().nextInt(decks.size()));
        Gdx.app.log("EnemyData", "CHOSEN=" + chosen);
        Gdx.app.log("EnemyData", "---- END DEBUG ----");
        return chosen;
    }

    private static void scan(FileHandle dir, List<String> out) {
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

    private Deck tryLoadDeckWithPathVariants(String chosen, boolean isFantasyMode, boolean canUseGeneticAI) {
        if (chosen == null || chosen.isEmpty()) return null;

        String p = chosen.replace('\\', '/');
        String pNoRes = p.startsWith("res/") ? p.substring("res/".length()) : p;

        String[] attempts = pNoRes.equals(p) ? new String[]{ p } : new String[]{ p, pNoRes };

        for (String attempt : attempts) {
            try {
                Gdx.app.log("EnemyData", "Trying deck path: " + attempt);
                Deck d = CardUtil.getDeck(attempt, true, isFantasyMode, colors, life > 13, canUseGeneticAI);
                if (d != null) return d;
            } catch (Throwable t) {
                Gdx.app.log("EnemyData", "Load failed for: " + attempt + " -> " + t.getClass().getName() + ": " + t.getMessage());
            }
        }
        return null;
    }
}

