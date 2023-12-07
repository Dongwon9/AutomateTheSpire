package automatethespire.patches;

import automatethespire.AutomateTheSpire;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

@SpirePatch(clz = ShowCardAndObtainEffect.class, method = SpirePatch.CONSTRUCTOR,
    paramtypez = {AbstractCard.class, float.class, float.class, boolean.class})
public class ShowCardAndObtainPatch {
    public static void Postfix(ShowCardAndObtainEffect __instance) {
        //__instance.duration = Math.min(__instance.duration, AutomateTheSpire.settings.getAutoActionCooldown() - 0.1f);
        AutomateTheSpire.cooldownLeft = __instance.duration;
    }
}
