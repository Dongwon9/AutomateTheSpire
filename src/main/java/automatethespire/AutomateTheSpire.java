package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.*;
import basemod.interfaces.*;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.FairyPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
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

import static com.megacrit.cardcrawl.core.CardCrawlGame.languagePack;
import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.*;

@SpireInitializer
public class AutomateTheSpire implements PostUpdateSubscriber, OnPlayerTurnStartPostDrawSubscriber,
    PostBattleSubscriber, PostDeathSubscriber, PostInitializeSubscriber, EditStringsSubscriber {
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

    private final Localization localization = new Localization();
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
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers
        // at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");

        try {
            Properties defaults = new Properties();
            defaults.put(AutoEndTurn, Boolean.toString(true));
            defaults.put(AutoOpenChest, Boolean.toString(true));
            defaults.put(AutoClickMapNode, Boolean.toString(true));
            defaults.put(AutoTakeRewards, Boolean.toString(true));
            defaults.put(EvenIfRewardLeft, Boolean.toString(true));
            defaults.put("EvenIfInShop", Boolean.toString(true));
            defaults.put(AutoClickEvent, Boolean.toString(true));
            defaults.put(AutoClickProceed, Boolean.toString(true));
            defaults.put("AutoActionCooldown", Float.toString(0f));
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

    private static void loadModInfo() {
        Optional<ModInfo> infos = Arrays.stream(Loader.MODINFOS).filter((modInfo) -> {
            AnnotationDB annotationDB = Patcher.annotationDBMap.get(modInfo.jarURL);
            if (annotationDB == null) {
                return false;
            }
            Set<String> initializers = annotationDB.getAnnotationIndex()
                .getOrDefault(SpireInitializer.class.getName(), Collections.emptySet());
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

    private FailCode TakeCombatReward() {
        if (screen != CurrentScreen.COMBAT_REWARD) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
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
        return FailCode.Success;
    }

    private float getAutoActionCooldown() {
        return modConfig == null ? 0f : modConfig.getFloat("AutoActionCooldown");
    }

    //This is used to prefix the IDs of various objects like cards and relics,
    //to avoid conflicts between different mods using the same name for things.
    @Override
    public void receivePostUpdate() {
        if (!CardCrawlGame.isInARun() || !isPlayerInDungeon()) {
            return;
        }
        try {
            currRoom = getCurrRoom();
        } catch (Exception e) {
            return;
        }
        FailCode code;
        boolean success = false, cooldownFail = false;
        if (isAutoClickMap()) {
            code = ClickMapNode();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (isAutoClickEvent()) {
            code = ClickEventButton();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (isAutoEndTurn()) {
            code = ClickEndTurn();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (isAutoTakeRewards()) {
            code = TakeCombatReward();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (isAutoClickProceed()) {
            code = ClickProceed();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (isAutoOpenChest()) {
            code = OpenChest();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if (success) {
            cooldownLeft = getAutoActionCooldown();
        } else if (cooldownFail) {
            cooldownLeft -= Gdx.graphics.getDeltaTime();
        } else {
            cooldownLeft = getAutoActionCooldown();
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

    private FailCode ClickEventButton() {
        final float eventButtonDelay = 0.5f;
        if (!(getCurrRoom() instanceof EventRoom) && !(getCurrRoom() instanceof NeowRoom) &&
            !(getCurrRoom() instanceof VictoryRoom)) {
            return FailCode.Fail;
        }
        if (screen != CurrentScreen.NONE) {
            eventButtonDelayLeft = eventButtonDelay;
            return FailCode.Fail;
        }

        ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
        if (activeButtons.size() != 1) {
            return FailCode.Fail;
        }
        eventButtonDelayLeft -= Math.max(Gdx.graphics.getDeltaTime(), getAutoActionCooldown());
        if (!(eventButtonDelayLeft <= 0) || activeButtons.get(0) == prevButton) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        activeButtons.get(0).pressed = true;
        prevButton = activeButtons.get(0);
        eventButtonDelayLeft = 0;
        logger.info("Pressed Event Option");
        return FailCode.Success;
    }

    private FailCode ClickMapNode() {
        if (screen != CurrentScreen.MAP) {
            mapNodePressed = false;
            return FailCode.Fail;
        }
        if (mapNodePressed) {
            return FailCode.Fail;
        }
        if (!combatRewardScreen.hasTakenAll && !isEvenIfRewardLeft()) {
            return FailCode.Fail;
        }
        if (currRoom instanceof ShopRoom && !isClickInShop()) {
            return FailCode.Fail;
        }
        if (currMapNode.y == 14 || (id.equals("TheEnding") && currMapNode.y == 2)) {
            DungeonMapPatch.doBossHover = true;
            return FailCode.Success;
        }
        ArrayList<MapRoomNode> choices = getMapScreenNodeChoices();
        if (choices.size() != 1) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        MapRoomNodeHoverPatch.hoverNode = choices.get(0);
        MapRoomNodeHoverPatch.doHover = true;
        dungeonMapScreen.clicked = true;
        mapNodePressed = true;
        return FailCode.Success;
    }

    private FailCode OpenChest() {

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
        if (chest == null || chest.isOpen) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        chest.isOpen = true;
        chest.open(false);
        return FailCode.Success;
    }

    private FailCode ClickProceed() {
        if (!(currRoom instanceof RestRoom) &&
            !((id.equals("TheEnding") || id.equals("TheBeyond")) && (currRoom instanceof MonsterRoomBoss)) &&
            !(currRoom instanceof TreasureRoomBoss) &&
            !((id.equals("TheCity") || id.equals("Exordium")) && (currRoom instanceof MonsterRoomBoss) &&
                combatRewardScreen.hasTakenAll)) {
            return FailCode.Fail;
        }
        if (currRoom instanceof RestRoom && !CampfireUI.hidden) {
            return FailCode.Fail;
        }
        if ((boolean) ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "isHidden")) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        Hitbox hb = ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
        return FailCode.CooldownFail;
    }
    public boolean canUseAnyCard() {
        Iterator<AbstractCard> var1 = player.hand.group.iterator();

        AbstractCard c;
        while(var1.hasNext()) {
            c = var1.next();
            if (c.type == AbstractCard.CardType.STATUS && c.costForTurn < -1 && !AbstractDungeon.player.hasRelic("Medical Kit")) {
                continue;
            } else if (c.type == AbstractCard.CardType.CURSE && c.costForTurn < -1 && !AbstractDungeon.player.hasRelic("Blue Candle")) {
                continue;
            }
            if(c.hasEnoughEnergy()) {
                return true;
            }
        }
        return false;
    }
    public boolean hasAnyPotions() {
        Iterator var1 = player.potions.iterator();

        AbstractPotion p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (AbstractPotion)var1.next();
        } while(p instanceof PotionSlot || p instanceof FairyPotion);

        return true;
    }
    private FailCode ClickEndTurn() {
        if (!turnFullyBegun || actionManager.phase != GameActionManager.Phase.WAITING_ON_USER ||
            currRoom.phase != AbstractRoom.RoomPhase.COMBAT || actionManager.turnHasEnded ||
            !actionManager.actions.isEmpty() || canUseAnyCard() || hasAnyPotions()) {
            return FailCode.Fail;
        }
        if (cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        actionManager.addToBottom(new PressEndTurnButtonAction());
        turnFullyBegun = false;
        return FailCode.Success;
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
        ModLabeledToggleButton endTurn =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[0], 350.0F, 700.0F,
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
        ModLabeledToggleButton openChest =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[1], 350.0F, 650.0F,
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
        ModLabeledToggleButton clickEvent =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[2], 350.0F, 600.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickEvent(), settingsPanel, l -> {
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
        ModLabeledToggleButton takeReward =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[3], 350.0F, 550.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoTakeRewards(), settingsPanel, l -> {
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
        ModLabeledToggleButton clickMap =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[4], 350.0F, 500.0F,
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
        ModLabeledToggleButton rewardLeft =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[5], 375.0F, 450.0F,
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
        ModLabeledToggleButton clickInShop =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[6], 375.0F, 400.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isClickInShop(), settingsPanel, l -> {
            }, button -> {
                if (modConfig != null) {
                    modConfig.setBool("EvenIfInShop", button.enabled);
                    try {
                        modConfig.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        ModLabeledToggleButton clickProceed =
            new ModLabeledToggleButton(languagePack.getUIString("AutoSpire:Settings").TEXT[7], 350.0F, 350.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickProceed(), settingsPanel, l -> {
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
        ModMinMaxSlider slider =
            new ModMinMaxSlider(languagePack.getUIString("AutoSpire:Settings").TEXT[8], 550.0F, 300.0F, 0f, 1f,
                getAutoActionCooldown(), "%.2fs", settingsPanel, s -> {
                if (modConfig != null) {
                    modConfig.setFloat("AutoActionCooldown", s.getValue());
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
        settingsPanel.addUIElement(clickInShop);
        settingsPanel.addUIElement(takeReward);
        settingsPanel.addUIElement(clickProceed);
        settingsPanel.addUIElement(slider);
        BaseMod.registerModBadge(ImageMaster.loadImage("modBadge.png"), "AutomateTheSpire", "Dongwon", "",
            settingsPanel);

    }

    private boolean isClickInShop() {
        return modConfig != null && modConfig.getBool("EvenIfInShop");
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

    @Override
    public void receiveEditStrings() {
        localization.receiveEditStrings();
    }

    public enum FailCode {
        Fail,
        Success,
        CooldownFail
    }
}
