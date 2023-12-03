package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.BaseMod;
import basemod.ReflectionHacks;
import basemod.interfaces.OnPlayerTurnStartPostDrawSubscriber;
import basemod.interfaces.PostBattleSubscriber;
import basemod.interfaces.PostDeathSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
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
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scannotation.AnnotationDB;

import java.util.*;

@SpireInitializer
public class AutomateTheSpire implements PostUpdateSubscriber, OnPlayerTurnStartPostDrawSubscriber,
                                         PostBattleSubscriber, PostDeathSubscriber {
    public static final float cooldown = 0f;
    private static final String resourcesFolder = "automatethespire";
    private static final float eventButtonDelay = 0.1f;
    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    public static final Logger logger = LogManager.getLogger(modID); //Used to output to the console.

    static {
        loadModInfo();
    }

    LargeDialogOptionButton prevButton;
    private AbstractRoom currRoom;
    private float cooldownLeft = 0f;
    private boolean turnFullyBegun = false;
    private boolean bossChestOpened = false;
    /*----------Localization----------*/
    private boolean mapNodePressed = false;
    private float eventButtonDelayLeft = 0.1f;
    //This will be called by ModTheSpire because of the @SpireInitializer annotation at the top of the class.
    private AbstractRoom.RoomPhase prevPhase;
    //This is used to load the appropriate localization files based on language.
    private Class<? extends AbstractRoom> prevRoom;
    //These methods are used to generate the correct filepaths to various parts of the resources folder.
    private AbstractDungeon.CurrentScreen prevScreen;
    //This determines the mod's ID based on information stored by ModTheSpire.
    private GameActionManager.Phase prevActionPhase;

    public AutomateTheSpire() {
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");
    }

    public static String makeID(String id) {
        return modID + ":" + id;
    }

    public static void initialize() {
        new AutomateTheSpire();
    }

    private static String getLangString() {
        return Settings.language.name().toLowerCase();
    }

    public static String localizationPath(String lang, String file) {
        return resourcesFolder + "/localization/" + lang + "/" + file;
    }

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

    private static void TakeCombatReward() {
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
            return;
        }
        for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            if (reward.type != RewardItem.RewardType.CARD &&
                !(reward.type == RewardItem.RewardType.RELIC && reward.relicLink != null) &&
                reward.type != RewardItem.RewardType.SAPPHIRE_KEY) {
                logger.info("Taking Reward : " + reward.type);
                reward.isDone = true;
            }
        }
        if (AbstractDungeon.combatRewardScreen.hasTakenAll &&
            !(AbstractDungeon.getCurrRoom() instanceof MonsterRoomBoss)) {
            AbstractDungeon.dungeonMapScreen.open(false);
        }
    }

    //This is used to prefix the IDs of various objects like cards and relics,
    //to avoid conflicts between different mods using the same name for things.
    @Override
    public void receivePostUpdate() {
        cooldownLeft -= Gdx.graphics.getDeltaTime();
        if (cooldownLeft > 0 || !CardCrawlGame.isInARun() || !AbstractDungeon.isPlayerInDungeon()) {
            return;
        }
        try {
            currRoom = AbstractDungeon.getCurrRoom();
        } catch (Exception e) {
            return;
        }
        //DebugRoomAndPhaseInfo();
        cooldownLeft = cooldown;
        PressEventButton();
        PressEndTurn();
        PressMapNode();
        TakeCombatReward();
        PressProceed();
        OpenChest();
    }

    private void DebugRoomAndPhaseInfo() {
        if (prevPhase != currRoom.phase) {
            logger.info("Phase: " + currRoom.phase);
            prevPhase = currRoom.phase;
        }
        if (prevRoom != currRoom.getClass()) {
            logger.info("Room: " + currRoom.getClass());
            prevRoom = currRoom.getClass();
        }
        if (prevScreen != AbstractDungeon.screen) {
            logger.info("Screen " + AbstractDungeon.screen);
            prevScreen = AbstractDungeon.screen;
        }
        if (prevActionPhase != AbstractDungeon.actionManager.phase) {
            logger.info("ActionPhase : " + AbstractDungeon.actionManager.phase);
            prevActionPhase = AbstractDungeon.actionManager.phase;
        }
    }

    private void PressEventButton() {
        if (!(AbstractDungeon.getCurrRoom() instanceof EventRoom) &&
             !(AbstractDungeon.getCurrRoom() instanceof NeowRoom) &&
             !(AbstractDungeon.getCurrRoom() instanceof VictoryRoom)){
            return;
        }
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.NONE) {
            eventButtonDelayLeft = eventButtonDelay * 24;
            return;
        }
        ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
        if (activeButtons.size() == 1) {
            eventButtonDelayLeft -= Gdx.graphics.getRawDeltaTime() * 6;
            if (eventButtonDelayLeft <= 0 && activeButtons.get(0) != prevButton) {
                activeButtons.get(0).pressed = true;
                prevButton = activeButtons.get(0);
                eventButtonDelayLeft = 0;
                logger.info("Pressed Event Option");

            }
        }

    }

    private void PressMapNode() {
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) {
            mapNodePressed = false;
            return;
        }
        if (mapNodePressed) {
            return;
        }
        mapNodePressed = true;
        if (AbstractDungeon.currMapNode.y == 14 ||
            (AbstractDungeon.id.equals("TheEnding") && AbstractDungeon.currMapNode.y == 2)) {
            DungeonMapPatch.doBossHover = true;
            return;
        }
        logger.info("click!");
        AbstractDungeon.dungeonMapScreen.clicked = true;
        ArrayList<MapRoomNode> choices = getMapScreenNodeChoices();
        if (choices.size() == 1) {
            MapRoomNodeHoverPatch.hoverNode = choices.get(0);
            MapRoomNodeHoverPatch.doHover = true;
        }
    }

    private void OpenChest() {
        if (!(currRoom instanceof TreasureRoomBoss)) {
            bossChestOpened = false;
        }
        AbstractChest chest = null;
        if (currRoom instanceof TreasureRoomBoss && !bossChestOpened) {
            chest = ((TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest;
            bossChestOpened = true;
        }
        if (currRoom instanceof TreasureRoom && !AbstractDungeon.player.hasRelic("Cursed Key")) {
            chest = ((TreasureRoom) AbstractDungeon.getCurrRoom()).chest;
        }
        if (chest != null && !chest.isOpen) {
            chest.isOpen = true;
            chest.open(false);
        }
    }

    private void PressProceed() {
        if (//!(currRoom instanceof TreasureRoomBoss) &&
                !(currRoom instanceof RestRoom) &&
                ((!AbstractDungeon.id.equals("TheEnding") && !AbstractDungeon.id.equals("TheBeyond")) ||
                 !(currRoom instanceof MonsterRoomBoss))) {
            return;
        }
//        if (currRoom instanceof TreasureRoomBoss &&
//            !((TreasureRoomBoss) currRoom).chest.isOpen) {
//            return;
//        }
        if (currRoom instanceof RestRoom && !CampfireUI.hidden) {
            return;
        }
        if (!(boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class,
                "isHidden")) {
            Hitbox hb = ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.proceedButton, ProceedButton.class,
                    "hb");
            hb.clicked = true;
        }
    }


    private void PressEndTurn() {
        if (turnFullyBegun && AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER &&
            currRoom.phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.actionManager.turnHasEnded &&
            AbstractDungeon.actionManager.actions.isEmpty() && !AbstractDungeon.player.hand.canUseAnyCard() &&
            !AbstractDungeon.player.hasAnyPotions()) {
            AbstractDungeon.actionManager.addToBottom(new PressEndTurnButtonAction());
            logger.info("Ended turn!");
            turnFullyBegun = false;
        }
    }

    @Override
    public void receiveOnPlayerTurnStartPostDraw() {
        turnFullyBegun = true;
    }

    @Override
    public void receivePostBattle(AbstractRoom abstractRoom) {
        turnFullyBegun = false;
    }

    @Override
    public void receivePostDeath() {
        turnFullyBegun = false;
    }


}
