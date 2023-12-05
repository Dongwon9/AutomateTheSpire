package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.BaseMod;
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
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
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

import java.util.*;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.*;

@SpireInitializer
public class AutomateTheSpire implements PostUpdateSubscriber, OnPlayerTurnStartPostDrawSubscriber,
    PostBattleSubscriber, PostDeathSubscriber, PostInitializeSubscriber, EditStringsSubscriber, PreStartGameSubscriber {

    private static final String resourcesFolder = "automatethespire";

    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    public static final Logger logger = LogManager.getLogger(modID);

    static {
        loadModInfo();
    }

    private final Localization localization = new Localization();
    public SettingsMenu settings;
    LargeDialogOptionButton prevButton;
    private AbstractRoom currRoom;
    private float cooldownLeft = 0f;
    private boolean turnFullyBegun = false;
    private boolean bossChestOpened = false;
    /*----------Localization----------*/
    private boolean mapNodePressed = false;
    private float eventButtonDelayLeft = 0;
    private AbstractRoom.RoomPhase prevPhase;
    private Class<? extends AbstractRoom> prevRoom;
    private CurrentScreen prevScreen;
    private GameActionManager.Phase prevActionPhase;
    private SpireConfig modConfig;
    private float proceedDelayLeft = 0.1f;

    public AutomateTheSpire() {
        BaseMod.subscribe(this); //This will make BaseMod trigger all the subscribers
        // at their appropriate times.
        logger.info(modID + " subscribed to BaseMod.");
        settings = new SettingsMenu();
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
            if(annotationDB == null) {
                return false;
            }
            Set<String> initializers = annotationDB.getAnnotationIndex()
                .getOrDefault(SpireInitializer.class.getName(), Collections.emptySet());
            return initializers.contains(AutomateTheSpire.class.getName());
        }).findFirst();
        if(infos.isPresent()) {
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
        if(!firstRoomChosen) {
            for (MapRoomNode node : map.get(0)) {
                if(node.hasEdges()) {
                    choices.add(node);
                }
            }
            return choices;
        }
        for (ArrayList<MapRoomNode> rows : map) {
            for (MapRoomNode node : rows) {
                if(!node.hasEdges()) {
                    continue;
                }
                boolean normalConnection = currMapNode.isConnectedTo(node);
                boolean wingedConnection = currMapNode.wingedIsConnectedTo(node);
                if(normalConnection || wingedConnection) {
                    choices.add(node);
                }
            }
        }
        return choices;
    }

    private FailCode TakeCombatReward() {
        if(screen != CurrentScreen.COMBAT_REWARD) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        boolean rewardTook = false;
        for (RewardItem reward : combatRewardScreen.rewards) {
            if(reward.type == RewardItem.RewardType.RELIC) {
                if(!settings.isAutoTakeRelics() || reward.relicLink != null ||
                    !(((reward.relic.relicId.contains("Bottled") || reward.relic.relicId.equals("War Paint") ||
                        reward.relic.relicId.equals("Whetstone")) && settings.isEvenTheBottles()))) {
                    continue;
                }
            }
            if(reward.type == RewardItem.RewardType.CARD || reward.type == RewardItem.RewardType.SAPPHIRE_KEY) {
                continue;
            }
            logger.info("Taking Reward : " + reward.type);
            reward.isDone = true;
            rewardTook = true;
        }
        if(combatRewardScreen.hasTakenAll && !(getCurrRoom() instanceof MonsterRoomBoss)) {
            dungeonMapScreen.open(false);
        }
        if(rewardTook) {
            return FailCode.Success;
        } else {
            return FailCode.Fail;
        }
    }

    //This is used to prefix the IDs of various objects like cards and relics,
    //to avoid conflicts between different mods using the same name for things.
    @Override
    public void receivePostUpdate() {
        if(!CardCrawlGame.isInARun() || !isPlayerInDungeon()) {
            return;
        }
        try {
            currRoom = getCurrRoom();
        } catch (Exception e) {
            return;
        }
        FailCode code;
        boolean success = false, cooldownFail = false;
        if(settings.isAutoClickMap()) {
            code = ClickMapNode();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(settings.isAutoClickEvent()) {
            code = ClickEventButton();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(settings.isAutoEndTurn()) {
            code = ClickEndTurn();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(settings.isAutoClickProceed()) {
            code = ClickProceed();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(settings.isAutoTakeRewards()) {
            code = TakeCombatReward();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(settings.isAutoOpenChest()) {
            code = OpenChest();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(success) {
            cooldownLeft = settings.getAutoActionCooldown();
        } else if(cooldownFail) {
            cooldownLeft -= Gdx.graphics.getDeltaTime();
        } else {
            cooldownLeft = settings.getAutoActionCooldown();
        }
    }

    private void DebugRoomAndPhaseInfo() {
        if(prevPhase != currRoom.phase) {
            logger.info("Phase: " + currRoom.phase);
            prevPhase = currRoom.phase;
        }
        if(prevRoom != currRoom.getClass()) {
            logger.info("Room: " + currRoom.getClass());
            prevRoom = currRoom.getClass();
        }
        if(prevScreen != screen) {
            logger.info("Screen " + screen);
            prevScreen = screen;
        }
        if(prevActionPhase != actionManager.phase) {
            logger.info("ActionPhase : " + actionManager.phase);
            prevActionPhase = actionManager.phase;
        }
    }

    private FailCode ClickEventButton() {
        final float eventButtonDelay = 0.5f;
        if(!(getCurrRoom() instanceof EventRoom) && !(getCurrRoom() instanceof NeowRoom) &&
            !(getCurrRoom() instanceof VictoryRoom)) {
            return FailCode.Fail;
        }
        ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
        if(activeButtons.size() != 1) {
            return FailCode.Fail;
        }
        if(screen == CurrentScreen.GRID) {
            eventButtonDelayLeft = eventButtonDelay;
        }

        eventButtonDelayLeft -= Math.max(Gdx.graphics.getDeltaTime(), settings.getAutoActionCooldown());
        if(eventButtonDelayLeft > 0 || activeButtons.get(0) == prevButton) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        activeButtons.get(0).pressed = true;
        prevButton = activeButtons.get(0);
        eventButtonDelayLeft = 0;
        logger.info("Pressed Event Option");
        return FailCode.Success;
    }

    private FailCode ClickMapNode() {
        if(screen != CurrentScreen.MAP) {
            mapNodePressed = false;
            return FailCode.Fail;
        }
        if(mapNodePressed || (!combatRewardScreen.hasTakenAll && !settings.isEvenIfRewardLeft()) ||
            currRoom instanceof ShopRoom && !settings.isClickInShop() || dungeonMapScreen.clicked || !firstRoomChosen) {
            return FailCode.Fail;
        }
        if(currMapNode.y == 14 || (id.equals("TheEnding") && currMapNode.y == 2)) {
            DungeonMapPatch.doBossHover = true;
            return FailCode.Success;
        }
        ArrayList<MapRoomNode> choices = getMapScreenNodeChoices();
        if(choices.size() != 1) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        MapRoomNodeHoverPatch.hoverNode = choices.get(0);
        MapRoomNodeHoverPatch.doHover = true;
        dungeonMapScreen.clicked = true;
        mapNodePressed = true;
        return FailCode.Success;
    }

    private FailCode OpenChest() {

        if(!(currRoom instanceof TreasureRoomBoss)) {
            bossChestOpened = false;
        }
        AbstractChest chest = null;
        if(currRoom instanceof TreasureRoomBoss && !bossChestOpened) {
            chest = ((TreasureRoomBoss) getCurrRoom()).chest;
            bossChestOpened = true;
        }
        if(currRoom instanceof TreasureRoom && !player.hasRelic("Cursed Key")) {
            chest = ((TreasureRoom) getCurrRoom()).chest;
        }
        if(chest == null || chest.isOpen) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        chest.isOpen = true;
        chest.open(false);
        return FailCode.Success;
    }

    private FailCode ClickProceed() {
        final float proceedDelay = 0.2f;
        if(!(currRoom instanceof RestRoom || currRoom instanceof MonsterRoomBoss ||
            currRoom instanceof TreasureRoomBoss)) {
            return FailCode.Fail;
        }
        if(currRoom instanceof RestRoom && (screen != CurrentScreen.NONE || !CampfireUI.hidden)) {
            return FailCode.Fail;
        }
        if(currRoom instanceof TreasureRoomBoss && !((TreasureRoomBoss) currRoom).chest.isOpen) {
            return FailCode.Fail;
        }
        if(id.equals("TheCity") || id.equals("Exordium")) {
            if(currRoom instanceof MonsterRoomBoss && !combatRewardScreen.hasTakenAll) {
                return FailCode.Fail;
            }
        }
        if((boolean) ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "isHidden")) {
            return FailCode.Fail;
        }
        if((id.equals("TheCity") || id.equals("Exordium")) && currRoom instanceof MonsterRoomBoss &&
            combatRewardScreen.hasTakenAll) {
            proceedDelayLeft -= Math.max(Gdx.graphics.getDeltaTime(), settings.getAutoActionCooldown());
        } else {
            proceedDelayLeft = proceedDelay;
        }
        if((id.equals("TheCity") || id.equals("Exordium")) && currRoom instanceof MonsterRoomBoss &&
            proceedDelayLeft > 0) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        Hitbox hb = ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
        return FailCode.Success;
    }

    public boolean canUseAnyCard() {
        Iterator<AbstractCard> var1 = player.hand.group.iterator();
        AbstractCard c;
        while (var1.hasNext()) {
            c = var1.next();
            if(c.type == AbstractCard.CardType.STATUS && c.costForTurn < -1 &&
                !AbstractDungeon.player.hasRelic("Medical Kit") ||
                c.type == AbstractCard.CardType.CURSE && c.costForTurn < -1 &&
                    !AbstractDungeon.player.hasRelic("Blue Candle")) {
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
            if(!var1.hasNext()) {
                return false;
            }

            p = (AbstractPotion) var1.next();
        } while (p instanceof PotionSlot || p instanceof FairyPotion);
        return true;
    }

    private FailCode ClickEndTurn() {
        if(!turnFullyBegun || actionManager.phase != GameActionManager.Phase.WAITING_ON_USER ||
            currRoom.phase != AbstractRoom.RoomPhase.COMBAT || actionManager.turnHasEnded ||
            !actionManager.actions.isEmpty() || canUseAnyCard() || hasAnyPotions()) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
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
        settings.MakeSettingsUI();

    }

    @Override
    public void receiveEditStrings() {
        localization.receiveEditStrings();
    }

    @Override
    public void receivePreStartGame() {
        turnFullyBegun = false;
    }

    public enum FailCode {
        Fail,
        Success,
        CooldownFail
    }
}
