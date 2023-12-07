package automatethespire;

import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModMinMaxSlider;
import basemod.ModPanel;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;

import java.io.IOException;
import java.util.Properties;

import static automatethespire.AutomateTheSpire.modID;
import static com.megacrit.cardcrawl.core.CardCrawlGame.languagePack;

public class SettingsMenu {
    public static final String AutoEndTurn = "AutoEndTurn";
    public static final String AutoOpenChest = "AutoOpenChest";
    public static final String AutoClickMapNode = "AutoClickMapNode";
    public static final String AutoTakeRewards = "AutoTakeRewards";
    public static final String EvenIfRewardLeft = "EvenIfRewardLeft";
    public static final String AutoClickEvent = "AutoClickEvent";
    public static final String AutoClickProceed = "AutoClickProceed";
    private final String EvenTheBottles = "EvenTheBottles";
    private final String EvenIfInShop = "EvenIfInShop";
    private final String AutoActionCooldown = "AutoActionCooldown";
    public SpireConfig config;

    public SettingsMenu() {
        try {
            Properties defaults = new Properties();
            defaults.put(AutoEndTurn, Boolean.toString(true));
            defaults.put(AutoOpenChest, Boolean.toString(true));
            defaults.put(AutoClickMapNode, Boolean.toString(true));
            defaults.put(AutoTakeRewards, Boolean.toString(true));
            defaults.put(EvenTheBottles, Boolean.toString(false));
            defaults.put(EvenIfRewardLeft, Boolean.toString(false));
            defaults.put(EvenIfInShop, Boolean.toString(false));
            defaults.put(AutoClickEvent, Boolean.toString(true));
            defaults.put(AutoClickProceed, Boolean.toString(true));
            defaults.put(AutoActionCooldown, Float.toString(0f));
            defaults.put("AutoTakeRelics", Boolean.toString(true));
            config = new SpireConfig("AutomateTheSpire", "Config", defaults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void MakeSettingsUI() {
        ModPanel settingsPanel = new ModPanel();
        ModLabeledToggleButton[] buttons =
            {new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[0], 350.0F, 700.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoEndTurn(), settingsPanel, l -> {}, button -> {
                if(config != null) {
                    config.setBool(AutoEndTurn, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[1], 350.0F, 650.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoOpenChest(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(AutoOpenChest, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[2], 350.0F, 600.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickEvent(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(AutoClickEvent, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[3], 350.0F, 550.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoTakeRewards(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(AutoTakeRewards, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[10], 375.0F, 500.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoTakeRelics(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool("AutoTakeRelics", button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[9], 375.0F, 450.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isEvenTheBottles(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(EvenTheBottles, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[4], 350.0F, 400.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickMap(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(AutoClickMapNode, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[5], 375.0F, 350.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isEvenIfRewardLeft(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(EvenIfRewardLeft, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[6], 375.0F, 300.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isClickInShop(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(EvenIfInShop, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }), new ModLabeledToggleButton(languagePack.getUIString(modID + ":Settings").TEXT[7], 350.0F, 250.0F,
                Settings.CREAM_COLOR, FontHelper.charDescFont, isAutoClickProceed(), settingsPanel, l -> {
            }, button -> {
                if(config != null) {
                    config.setBool(AutoClickProceed, button.enabled);
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            })};
        ModMinMaxSlider slider =
            new ModMinMaxSlider(languagePack.getUIString(modID + ":Settings").TEXT[8], 800.0F, 700.0F, 0.1f, 1f,
                getAutoActionCooldown(), "%.2fs", settingsPanel, s -> {
                if(config != null) {
                    config.setFloat(AutoActionCooldown, s.getValue());
                    try {
                        config.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        for (ModLabeledToggleButton b : buttons) {
            settingsPanel.addUIElement(b);
        }
        settingsPanel.addUIElement(slider);

        BaseMod.registerModBadge(ImageMaster.loadImage("modBadge.png"), "AutomateTheSpire", "Dongwon", "",
            settingsPanel);
    }

    public float getAutoActionCooldown() {
        return config == null ? 0.1f : Math.max(config.getFloat(AutoActionCooldown), 0.1f);
    }

    public boolean isAutoTakeRelics() {
        return config != null && config.getBool("AutoTakeRelics");
    }

    public boolean isClickInShop() {
        return config != null && config.getBool(EvenIfInShop);
    }

    public boolean isAutoEndTurn() {
        return config != null && config.getBool(AutoEndTurn);
    }

    public boolean isAutoOpenChest() {
        return config != null && config.getBool(AutoOpenChest);
    }

    public boolean isAutoTakeRewards() {
        return config != null && config.getBool(AutoTakeRewards);
    }

    public boolean isEvenTheBottles() {
        return config != null && config.getBool(EvenTheBottles);
    }

    public boolean isEvenIfRewardLeft() {
        return config != null && config.getBool(EvenIfRewardLeft);
    }

    public boolean isAutoClickMap() {
        return config != null && config.getBool(AutoClickMapNode);
    }

    public boolean isAutoClickEvent() {
        return config != null && config.getBool(AutoClickEvent);
    }

    public boolean isAutoClickProceed() {
        return config != null && config.getBool(AutoClickProceed);
    }
}
