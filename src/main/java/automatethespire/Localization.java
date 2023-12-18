package automatethespire;

import basemod.BaseMod;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.UIStrings;

public class Localization {
    private static final String defaultLanguage = "eng";

    public static String localizationPath(String lang, String file) {
        return "localization/" + lang + "/" + file;
    }

    public String getLangString() {
        return Settings.language.name().toLowerCase();
    }

    private void loadLocalization(String lang) {
        //While this does load every type of localization, most of these files are just outlines so that you can see
        // how they're formatted.
        //Feel free to comment out/delete any that you don't end up using.
        BaseMod.loadCustomStringsFile(UIStrings.class, localizationPath(lang, "UIStrings.json"));
    }

    public void receiveEditStrings() {
        /*
            First, load the default localization.
            Then, if the current language is different, attempt to load localization for that language.
            This results in the default localization being used for anything that might be missing.
            The same process is used to load keywords slightly below.
        */
        loadLocalization(
            defaultLanguage); //no exception catching for default localization; you better have at least one that works.
        if(defaultLanguage.equals(getLangString())) {
            return;
        }
        try {
            loadLocalization(getLangString());
        } catch (GdxRuntimeException e) {
            e.printStackTrace();
        }
    }
}
