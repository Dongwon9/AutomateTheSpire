package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.ReflectionHacks;
import basemod.interfaces.*;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
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

import java.io.IOException;
import java.util.*;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.*;

@SpireInitializer
public class AutomateTheSpire implements PostUpdateSubscriber, OnPlayerTurnStartPostDrawSubscriber,
                                         PostBattleSubscriber, PostDeathSubscriber, PostInitializeSubscriber {
    public static final float cooldown = 0f;
    public static final String AutoEndTurn = "AutoEndTurn";
    public static final String AutoOpenChest = "AutoOpenChest";
    public static final String AutoClickMapNode = "AutoClickMapNode";
    public static final String AutoTakeRewards = "AutoTakeRewards";
    public static final String EvenIfRewardLeft = "EvenIfRewardLeft";
    public static final String AutoClickEvent = "AutoClickEvent";
    public static final String AutoClickProceed = "AutoClickProceed";
    private static final String resourcesFolder = "automatethespire";

    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    public static final Logger logger = LogManager.getLogger(modID);

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
    private AbstractRoom.RoomPhase prevPhase;
    private Class<? extends AbstractRoom> prevRoom;
    private CurrentScreen prevScreen;
    private GameActionManager.Phase prevActionPhase;
    private SpireConfig modConfig;

    public AutomateTheSpire() {
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");
        try {
            Properties defaults = new Properties();
            defaults.put(AutoEndTurn, Boolean.toString(true));
            defaults.put(AutoOpenChest, Boolean.toString(true));
            defaults.put(AutoClickMapNode, Boolean.toString(true));
            defaults.put(AutoTakeRewards, Boolean.toString(true));
            defaults.put(EvenIfRewardLeft, Boolean.toString(true));
            defaults.put(AutoClickEvent, Boolean.toString(true));
            defaults.put(AutoClickProceed, Boolean.toString(true));
            modConfig = new SpireConfig("AutomateTheSpire", "Config", defaults);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            Set<String> initializers = annotationDB.getAnnotationIndex().getOrDefault(SpireInitializer.class.getName(),
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
        MapRoomNode currMapNode = getCurrMapNode();
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        if (!firstRoomChosen) {
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
        if (screen != CurrentScreen.COMBAT_REWARD) {
            return;
        }
        for (RewardItem reward : combatRewardScreen.rewards) {
            if (reward.type != RewardItem.RewardType.CARD &&
                !(reward.type == RewardItem.RewardType.RELIC && reward.relicLink != null) &&
                reward.type != RewardItem.RewardType.SAPPHIRE_KEY) {
                logger.info("Taking Reward : " + reward.type);
                reward.isDone = true;
            }
        }
        if (combatRewardScreen.hasTakenAll && !(getCurrRoom() instanceof MonsterRoomBoss)) {
            dungeonMapScreen.open(false);
        }
    }

    //This is used to prefix the IDs of various objects like cards and relics,
    //to avoid conflicts between different mods using the same name for things.
    @Override
    public void receivePostUpdate() {
        cooldownLeft -= Gdx.graphics.getDeltaTime();
        if (cooldownLeft > 0 || !CardCrawlGame.isInARun() || !isPlayerInDungeon()) {
            return;
        }
        try {
            currRoom = getCurrRoom();
        } catch (Exception e) {
            return;
        }
        //DebugRoomAndPhaseInfo();
        cooldownLeft = cooldown;
        if (isAutoClickEvent()) {
            ClickEventButton();
        }
        if (isAutoEndTurn()) {
            ClickEndTurn();
        }
        if (isAutoClickMap()) {
            ClickMapNode();
        }
        if (isAutoTakeRewards()) {
            TakeCombatReward();
        }
        if (isAutoClickProceed()) {
            ClickProceed();
        }
        if (isAutoOpenChest()) {
            OpenChest();
        }
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
        if (prevScreen != screen) {
            logger.info("Screen " + screen);
            prevScreen = screen;
        }
        if (prevActionPhase != actionManager.phase) {
            logger.info("ActionPhase : " + actionManager.phase);
            prevActionPhase = actionManager.phase;
        }
    }

    private void ClickEventButton() {
        final float eventButtonDelay = 0.5f;
        if (!(getCurrRoom() instanceof EventRoom) && !(getCurrRoom() instanceof NeowRoom) &&
            !(getCurrRoom() instanceof VictoryRoom)) {
            return;
        }
        if (screen != CurrentScreen.NONE) {
            eventButtonDelayLeft = eventButtonDelay;
            return;
        }
        ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
        if (activeButtons.size() == 1) {
            eventButtonDelayLeft -= Gdx.graphics.getDeltaTime();
            if (eventButtonDelayLeft <= 0 && activeButtons.get(0) != prevButton) {
                activeButtons.get(0).pressed = true;
                prevButton = activeButtons.get(0);
                eventButtonDelayLeft = 0;
                logger.info("Pressed Event Option");

            }
        }

    }

    private void ClickMapNode() {
        if (screen != CurrentScreen.MAP) {
            mapNodePressed = false;
            return;
        }
        if (mapNodePressed) {
            return;
        }
        if (!combatRewardScreen.hasTakenAll && !isEvenIfRewardLeft()) {
            return;
        }
        mapNodePressed = true;
        if (currMapNode.y == 14 || (id.equals("TheEnding") && currMapNode.y == 2)) {
            DungeonMapPatch.doBossHover = true;
            return;
        }
        ArrayList<MapRoomNode> choices = getMapScreenNodeChoices();
        if (choices.size() == 1) {
            MapRoomNodeHoverPatch.hoverNode = choices.get(0);
            MapRoomNodeHoverPatch.doHover = true;
            dungeonMapScreen.clicked = true;
        }
    }

    private void OpenChest() {
        if (!(currRoom instanceof TreasureRoomBoss)) {
            bossChestOpened = false;
        }
        AbstractChest chest = null;
        if (currRoom instanceof TreasureRoomBoss && !bossChestOpened) {
            chest = ((TreasureRoomBoss) getCurrRoom()).chest;
            bossChestOpened = true;
        }
        if (currRoom instanceof TreasureRoom && !player.hasRelic("Cursed Key")) {
            chest = ((TreasureRoom) getCurrRoom()).chest;
        }
        if (chest != null && !chest.isOpen) {
            chest.isOpen = true;
            chest.open(false);
        }
    }

    private void ClickProceed() {
        if (!(currRoom instanceof RestRoom) &&
            !((id.equals("TheEnding") || id.equals("TheBeyond")) &&
                 (currRoom instanceof MonsterRoomBoss)) &&
            !(currRoom instanceof TreasureRoomBoss) && !(
                    (id.equals("TheCity") || id.equals("Exordium")) &&
                                                         (currRoom instanceof MonsterRoomBoss) &&
                                                         combatRewardScreen.hasTakenAll)) {
            return;
        }
        if (currRoom instanceof RestRoom && !CampfireUI.hidden) {
            return;
        }
        if ((boolean) ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "isHidden")) {
            return;
        }
        Hitbox hb = ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
    }

    private void ClickEndTurn() {
        if (turnFullyBegun && actionManager.phase == GameActionManager.Phase.WAITING_ON_USER &&
            currRoom.phase == AbstractRoom.RoomPhase.COMBAT && !actionManager.turnHasEnded &&
            actionManager.actions.isEmpty() && !player.hand.canUseAnyCard() && !player.hasAnyPotions()) {
            actionManager.addToBottom(new PressEndTurnButtonAction());
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

    public void receivePostInitialize() {
        ModPanel settingsPanel = new ModPanel();
        ModLabeledToggleButton endTurn = new ModLabeledToggleButton("Automatically end turn", 350.0F, 700.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoEndTurn(), settingsPanel, l -> {

        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoEndTurn, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton openChest = new ModLabeledToggleButton("Automatically open all chests", 350.0F, 650.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoOpenChest(), settingsPanel, l -> {
        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoOpenChest, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton clickEvent = new ModLabeledToggleButton("Automatically click event option", 350.0F,
                600.0F, Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickEvent(), settingsPanel, l -> {
        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoClickEvent, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton takeReward = new ModLabeledToggleButton("Automatically take golds,relics,potions",
                350.0F, 550.0F, Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoTakeRewards(), settingsPanel,
                l -> {
                }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoTakeRewards, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton clickMap = new ModLabeledToggleButton("Automatically click map nodes", 350.0F, 500.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickMap(), settingsPanel, l -> {
        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoClickMapNode, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton rewardLeft = new ModLabeledToggleButton("Even if there are rewards left", 375.0F, 450.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isEvenIfRewardLeft(), settingsPanel, l -> {
        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(EvenIfRewardLeft, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ModLabeledToggleButton clickProceed = new ModLabeledToggleButton("Automatically click proceed button", 350.0F,
                400.0F, Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickProceed(), settingsPanel, l -> {
        }, button -> {
            if (modConfig != null) {
                modConfig.setBool(AutoClickProceed, button.enabled);
                try {
                    modConfig.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        settingsPanel.addUIElement(endTurn);
        settingsPanel.addUIElement(openChest);
        settingsPanel.addUIElement(clickEvent);
        settingsPanel.addUIElement(clickMap);
        settingsPanel.addUIElement(rewardLeft);
        settingsPanel.addUIElement(takeReward);
        settingsPanel.addUIElement(clickProceed);
        BaseMod.registerModBadge(ImageMaster.loadImage("modBadge.png"), "AutomateTheSpire", "Dongwon", "",
                settingsPanel);
    }

    private boolean isAutoEndTurn() {
        return modConfig != null && modConfig.getBool(AutoEndTurn);
    }

    private boolean isAutoOpenChest() {
        return modConfig != null && modConfig.getBool(AutoOpenChest);
    }

    private boolean isAutoTakeRewards() {
        return modConfig != null && modConfig.getBool(AutoTakeRewards);
    }

    private boolean isEvenIfRewardLeft() {
        return modConfig != null && modConfig.getBool(EvenIfRewardLeft);
    }

    private boolean isAutoClickMap() {
        return modConfig != null && modConfig.getBool(AutoClickMapNode);
    }

    private boolean isAutoClickEvent() {
        return modConfig != null && modConfig.getBool(AutoClickEvent);
    }

    private boolean isAutoClickProceed() {
        return modConfig != null && modConfig.getBool(AutoClickProceed);
    }
}
