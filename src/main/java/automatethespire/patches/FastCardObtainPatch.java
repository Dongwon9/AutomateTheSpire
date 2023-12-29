package automatethespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.vfx.FastCardObtainEffect;
import sun.security.provider.ConfigFile;

@SpirePatch(clz = FastCardObtainEffect.class, method = SpirePatch.CONSTRUCTOR)
public class FastCardObtainPatch {
    public static void Postfix(FastCardObtainEffect instance){
        instance.duration = 0f;
    }
}
