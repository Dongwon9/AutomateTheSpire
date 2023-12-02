package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.BaseMod;
import basemod.interfaces.*;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.localization.*;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.TreasureRoom;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scannotation.AnnotationDB;

import java.util.*;

@SpireInitializer
public class AutomateTheSpire implements
                              PostUpdateSubscriber, OnPlayerTurnStartPostDrawSubscriber,
                              PostBattleSubscriber, PostDeathSubscriber {
    public static final float cooldown = 0.2f;
    private static final String resourcesFolder = "automatethespire";
    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    public static final Logger logger = LogManager.getLogger(modID); //Used to output to the console.

    static {
        loadModInfo();
    }

    private float cooldownLeft = 0f;
    private boolean turnFullyBegun = false;

    public AutomateTheSpire() {
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");
    }

    /*----------Localization----------*/

    //This is used to prefix the IDs of various objects like cards and relics,
    //to avoid conflicts between different mods using the same name for things.
    public static String makeID(String id) {
        return modID + ":" + id;
    }

    //This will be called by ModTheSpire because of the @SpireInitializer annotation at the top of the class.
    public static void initialize() {
        new AutomateTheSpire();
    }

    //This is used to load the appropriate localization files based on language.
    private static String getLangString() {
        return Settings.language.name().toLowerCase();
    }

    //These methods are used to generate the correct filepaths to various parts of the resources folder.
    public static String localizationPath(String lang, String file) {
        return resourcesFolder + "/localization/" + lang + "/" + file;
    }

    //This determines the mod's ID based on information stored by ModTheSpire.
    private static void loadModInfo() {
        Optional<ModInfo> infos = Arrays.stream(Loader.MODINFOS).filter((modInfo) -> {
            AnnotationDB annotationDB = Patcher.annotationDBMap.get(modInfo.jarURL);
            if (annotationDB == null) {
                return false;
            }
            Set<String> initializers =
                    annotationDB.getAnnotationIndex().getOrDefault(SpireInitializer.class.getName(),
                            Collections.emptySet());
            return initializers.contains(AutomateTheSpire.class.getName());
        }).findFirst();
        if (infos.isPresent()) {
            info = infos.get();
            modID = info.ID;
        } else {
            throw new RuntimeException("Failed to determine mod info/ID based on initializer.");
        }
    }

    public static ArrayList<MapRoomNode> getMapScreenNodeChoices() {
        ArrayList<MapRoomNode> choices = new ArrayList<>();
        MapRoomNode currMapNode = AbstractDungeon.getCurrMapNode();
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        if (!AbstractDungeon.firstRoomChosen) {
            for (MapRoomNode node : map.get(0)) {
                if (node.hasEdges()) {
                    choices.add(node);
                }
            }
            return choices;
        }
        for (ArrayList<MapRoomNode> rows : map) {
            for (MapRoomNode node : rows) {
                if (!node.hasEdges()) {
                    continue;
                }
                boolean normalConnection = currMapNode.isConnectedTo(node);
                boolean wingedConnection = currMapNode.wingedIsConnectedTo(node);
                if (normalConnection || wingedConnection) {
                    choices.add(node);
                }
            }
        }
        return choices;
    }

    public void setTurnFullyBegun(boolean value) {
        if (turnFullyBegun != value) {
            logger.info("TurnFullyBegun: " + value);
            turnFullyBegun = value;
        }
    }

    private void loadLocalization(String lang) {
        //While this does load every type of localization, most of these files are just outlines so that you can see
        // how they're formatted.
        //Feel free to comment out/delete any that you don't end up using.
        BaseMod.loadCustomStringsFile(CardStrings.class, localizationPath(lang, "CardStrings.json"));
        BaseMod.loadCustomStringsFile(CharacterStrings.class, localizationPath(lang, "CharacterStrings.json"));
        BaseMod.loadCustomStringsFile(EventStrings.class, localizationPath(lang, "EventStrings.json"));
        BaseMod.loadCustomStringsFile(OrbStrings.class, localizationPath(lang, "OrbStrings.json"));
        BaseMod.loadCustomStringsFile(PotionStrings.class, localizationPath(lang, "PotionStrings.json"));
        BaseMod.loadCustomStringsFile(PowerStrings.class, localizationPath(lang, "PowerStrings.json"));
        BaseMod.loadCustomStringsFile(RelicStrings.class, localizationPath(lang, "RelicStrings.json"));
        BaseMod.loadCustomStringsFile(UIStrings.class, localizationPath(lang, "UIStrings.json"));
    }

    @Override
    public void receivePostUpdate() {
        cooldownLeft -= Gdx.graphics.getDeltaTime();
        if (cooldownLeft >0 || !CardCrawlGame.isInARun() || !AbstractDungeon.isPlayerInDungeon()) {
            return;
        }
        cooldownLeft = cooldown;
        PressEventButton();
        PressEndTurn();
        PressMapNode();
        TakeCombatReward();

    }

    private static void TakeCombatReward() {
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
            for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
                if (reward.type != RewardItem.RewardType.CARD &&
                    !(reward.type == RewardItem.RewardType.RELIC &&
                      AbstractDungeon.getCurrRoom().getClass() == TreasureRoom.class &&
                      Settings.isFinalActAvailable && !Settings.hasSapphireKey
                    ) && reward.type != RewardItem.RewardType.SAPPHIRE_KEY) {
                    logger.info("Taking Reward : " + reward.type);
                    reward.isDone = true;
                }
            }
            if (AbstractDungeon.combatRewardScreen.hasTakenAll &&
                !(AbstractDungeon.getCurrRoom() instanceof MonsterRoomBoss)) {
                AbstractDungeon.dungeonMapScreen.open(false);
            }
        }
    }

    private static void PressMapNode() {
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            if (AbstractDungeon.currMapNode.y == 14 ||
                (AbstractDungeon.id.equals("TheEnding") && AbstractDungeon.currMapNode.y == 2)) {
                DungeonMapPatch.doBossHover = true;
            }
            ArrayList<MapRoomNode> choices = getMapScreenNodeChoices();
            if (choices.size() == 1) {
                MapRoomNodeHoverPatch.hoverNode = choices.get(0);
                MapRoomNodeHoverPatch.doHover = true;
                AbstractDungeon.dungeonMapScreen.clicked = true;
            }
        }
    }

    private static void PressEventButton() {
        if (AbstractDungeon.getCurrRoom() instanceof EventRoom || AbstractDungeon.getCurrRoom() instanceof NeowRoom) {
            ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
            if (activeButtons.size() == 1) {
                activeButtons.get(0).pressed = true;
            }
        }
    }

    private void PressEndTurn() {
        if (turnFullyBegun && AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER &&
            AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT &&
            !AbstractDungeon.actionManager.turnHasEnded && AbstractDungeon.actionManager.actions.isEmpty() &&
            !AbstractDungeon.player.hand.canUseAnyCard() && !AbstractDungeon.player.hasAnyPotions()
        ) {
            AbstractDungeon.actionManager.addToBottom(new PressEndTurnButtonAction());
            logger.info("Ended turn!");
            setTurnFullyBegun(false);
        }
    }

    @Override
    public void receiveOnPlayerTurnStartPostDraw() {
        setTurnFullyBegun(true);
    }

    @Override
    public void receivePostBattle(AbstractRoom abstractRoom) {
        setTurnFullyBegun(false);
    }

    @Override
    public void receivePostDeath() {
        setTurnFullyBegun(false);
    }



}
