package automatethespire;

import automatethespire.patches.DungeonMapPatch;
import automatethespire.patches.MapRoomNodeHoverPatch;
import basemod.BaseMod;
import basemod.ReflectionHacks;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheEnding;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.shop.Merchant;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scannotation.AnnotationDB;

import java.util.*;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.*;

@SpireInitializer
public class AutomateTheSpire implements PostUpdateSubscriber, PostInitializeSubscriber, EditStringsSubscriber {

    private static final String resourcesFolder = "automatethespire";

    public static ModInfo info;
    public static String modID; //Edit your pom.xml to change this
    public static final Logger logger = LogManager.getLogger(modID);
    public static SettingsMenu settings;
    public static float cooldownLeft = 0f;
    public static AbstractRoom currRoom;
    static MapRoomNode prevMapNode = null;

    static {
        loadModInfo();
    }

    private final Localization localization = new Localization();
    private final DebugInfo debugInfo;
    LargeDialogOptionButton prevButton;
    ArrayList<MapRoomNode> choices = null;
    private boolean bossChestOpened = false;
    private boolean mapNodePressed = false;
    private boolean merchantClicked = false;

    public AutomateTheSpire() {
        BaseMod.subscribe(this);
        logger.info(modID + " subscribed to BaseMod.");
        settings = new SettingsMenu();
        debugInfo = new DebugInfo();
    }

    public static void initialize() {
        new AutomateTheSpire();
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
        ArrayList<RewardItem> potions = new ArrayList<>();
        for (RewardItem reward : combatRewardScreen.rewards) {
            if(reward.type == RewardItem.RewardType.RELIC) {
                if(reward.relicLink != null || !settings.isAutoTakeRelics()) {
                    continue;
                }
                if(!settings.isEvenTheBottles() &&
                    (reward.relic.relicId.contains("Bottled") || reward.relic.relicId.equals(WarPaint.ID) ||
                        reward.relic.relicId.equals(Whetstone.ID))) {
                    continue;
                }
            }
            if(reward.type == RewardItem.RewardType.CARD || reward.type == RewardItem.RewardType.SAPPHIRE_KEY) {
                continue;
            }
            if(reward.type == RewardItem.RewardType.POTION) {
                potions.add(reward);
                continue;
            }
            logger.info("Taking Reward : " + reward.type);
            reward.isDone = true;
            rewardTook = true;
            break;
        }
        if(!rewardTook && !potions.isEmpty() && potions.size() <= countPotionSlot()) {
            potions.get(0).isDone = true;
            rewardTook = true;
        }
        if(rewardTook) {
            return FailCode.Success;
        } else {
            return FailCode.Fail;
        }
    }

    @Override
    public void receivePostUpdate() {
        if(!CardCrawlGame.isInARun() || !isPlayerInDungeon()) {
            bossChestOpened = false;
            merchantClicked = false;
            return;
        }
        for (AbstractRelic r : player.relics) {
            if(!r.isDone) {
                return;
            }
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
        if(settings.isAutoClickMerchant()) {
            code = ClickMerchant();
            success = success || code == FailCode.Success;
            cooldownFail = cooldownFail || code == FailCode.CooldownFail;
        }
        if(success || !cooldownFail) {
            cooldownLeft = settings.getAutoActionCooldown();
        } else {
            cooldownLeft -= Gdx.graphics.getDeltaTime();
        }
        //debugInfo.DebugRoomAndPhaseInfo();
    }

    private FailCode ClickEventButton() {
        if(!(getCurrRoom() instanceof EventRoom) && !(getCurrRoom() instanceof NeowRoom) &&
            !(getCurrRoom() instanceof VictoryRoom)) {
            return FailCode.Fail;
        }
        if(screen != CurrentScreen.NONE) {
            return FailCode.Fail;
        }
        ArrayList<LargeDialogOptionButton> activeButtons = EventScreenUtils.getActiveEventButtons();
        if(activeButtons.size() != 1) {
            return FailCode.Fail;
        }
        if(activeButtons.get(0) == prevButton) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }

        prevButton = activeButtons.get(0);
        activeButtons.get(0).pressed = true;
        logger.info("Pressed Event Option: " + activeButtons.get(0).msg);
        return FailCode.Success;
    }

    private FailCode ClickMapNode() {
        if(screen != CurrentScreen.MAP) {
            mapNodePressed = false;
            return FailCode.Fail;
        }
        if(mapNodePressed || (!combatRewardScreen.rewards.isEmpty() && !settings.isEvenIfRewardLeft()) ||
            dungeonMapScreen.clicked) {
            return FailCode.Fail;
        }
        if(currRoom instanceof ShopRoom && !settings.isClickInShop()) {
            return FailCode.Fail;
        }
        if(currMapNode.y == 14 || (id.equals(TheEnding.ID) && currMapNode.y == 2)) {
            DungeonMapPatch.doBossHover = true;
            return FailCode.Success;
        }
        if(prevMapNode != getCurrMapNode()) {
            prevMapNode = getCurrMapNode();
            choices = getMapScreenNodeChoices();
        }
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
        logger.info("Mapnode clicked : " + choices.get(0).getRoomSymbol(true));
        return FailCode.Success;
    }

    private FailCode ClickMerchant() {
        if(!(currRoom instanceof ShopRoom)) {
            merchantClicked = false;
            return FailCode.Fail;
        }
        if(merchantClicked) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        merchantClicked = true;
        shopScreen.open();
        AbstractDungeon.overlayMenu.proceedButton.setLabel(Merchant.NAMES[0]);
        return FailCode.Success;
    }

    private FailCode OpenChest() {
        if(!(currRoom instanceof TreasureRoomBoss)) {
            bossChestOpened = false;
        }
        AbstractChest chest = null;
        if(currRoom instanceof TreasureRoomBoss && !bossChestOpened) {
            chest = ((TreasureRoomBoss) getCurrRoom()).chest;

        }
        if(currRoom instanceof TreasureRoom && !player.hasRelic(CursedKey.ID)) {
            chest = ((TreasureRoom) getCurrRoom()).chest;
        }
        if(chest == null || chest.isOpen) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        if(currRoom instanceof TreasureRoomBoss) {
            bossChestOpened = true;
        }
        chest.isOpen = true;
        chest.open(false);
        logger.info("Opened chest!");
        return FailCode.Success;
    }

    private FailCode ClickProceed() {
        if((boolean) ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "isHidden")) {
            return FailCode.Fail;
        }
        if(!combatRewardScreen.rewards.isEmpty()) {
            return FailCode.Fail;
        }
        if(!(currRoom instanceof RestRoom || currRoom instanceof MonsterRoom || currRoom instanceof TreasureRoom ||
            currRoom instanceof EventRoom || currRoom instanceof TreasureRoomBoss || currRoom instanceof NeowRoom)) {
            return FailCode.Fail;
        }
        if(screen != CurrentScreen.NONE && screen != CurrentScreen.COMBAT_REWARD) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        Hitbox hb = ReflectionHacks.getPrivate(overlayMenu.proceedButton, ProceedButton.class, "hb");
        hb.clicked = true;
        logger.info("pressed proceed!");
        return FailCode.Success;
    }

    public boolean canUseAnyCard() {
        Iterator<AbstractCard> var1 = player.hand.group.iterator();
        AbstractCard c;
        while (var1.hasNext()) {
            c = var1.next();
            if(c.type == AbstractCard.CardType.STATUS && c.costForTurn < -1 &&
                !AbstractDungeon.player.hasRelic(MedicalKit.ID) ||
                c.type == AbstractCard.CardType.CURSE && c.costForTurn < -1 &&
                    !AbstractDungeon.player.hasRelic(BlueCandle.ID)) {
                continue;
            }
            if(c.hasEnoughEnergy()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyPotions() {
        Iterator<AbstractPotion> var1 = player.potions.iterator();
        AbstractPotion p;
        do {
            if(!var1.hasNext()) {
                return false;
            }

            p = var1.next();
        } while (p instanceof PotionSlot || !p.canUse());
        return true;
    }

    public int countPotionSlot() {
        Iterator<AbstractPotion> potions = player.potions.iterator();
        int count = 0;
        AbstractPotion p;
        while (potions.hasNext()) {
            p = potions.next();
            if(p instanceof PotionSlot) {
                count += 1;
            }
        }
        return count;
    }

    private FailCode ClickEndTurn() {
        if(!overlayMenu.endTurnButton.enabled || actionManager.phase != GameActionManager.Phase.WAITING_ON_USER ||
            currRoom.phase != AbstractRoom.RoomPhase.COMBAT || !actionManager.actions.isEmpty() || canUseAnyCard() ||
            hasAnyPotions()) {
            return FailCode.Fail;
        }
        if(cooldownLeft > 0) {
            return FailCode.CooldownFail;
        }
        actionManager.addToBottom(new PressEndTurnButtonAction());
        logger.info("Turn Ended!");
        return FailCode.Success;
    }

    @Override
    public void receivePostInitialize() {
        settings.MakeSettingsUI();
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