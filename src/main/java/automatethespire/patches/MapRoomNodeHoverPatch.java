package automatethespire.patches;
import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.map.MapRoomNode;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;

@SpirePatch(clz = MapRoomNode.class, method = "update")
public class MapRoomNodeHoverPatch {
    public static MapRoomNode hoverNode;

    public static boolean doHover = false;

    @SpireInsertPatch(locator = Locator.class)
    public static void Insert(MapRoomNode _instance) {
        if (doHover)
            if (hoverNode == _instance) {
                _instance.hb.hovered = true;
                doHover = false;
            } else {
                _instance.hb.hovered = false;
            }
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher.MethodCallMatcher methodCallMatcher = new Matcher.MethodCallMatcher(Hitbox.class, "update");
            int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList(), (Matcher)methodCallMatcher);
            results[0] = results[0] + 1;
            return results;
        }
    }
}
