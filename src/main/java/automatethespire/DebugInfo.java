package automatethespire;

import automatethespire.AutomateTheSpire;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import org.apache.logging.log4j.Logger;

import static automatethespire.AutomateTheSpire.currRoom;
import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.*;

public class DebugInfo {

    public static final Logger logger = AutomateTheSpire.logger;
    private boolean prevhasTakenAll;
    private CurrentScreen prevScreen;
    private GameActionManager.Phase prevActionPhase;
    private AbstractRoom.RoomPhase prevPhase;
    private Class<? extends AbstractRoom> prevRoom;

    public void DebugRoomAndPhaseInfo() {
        if(prevPhase != currRoom.phase) {
            logger.info("Phase: " + currRoom.phase);
            prevPhase = currRoom.phase;
        }
        if(prevRoom != currRoom.getClass()) {
            logger.info("Room: " + currRoom.getClass());
            prevRoom = currRoom.getClass();
        }
        if(prevScreen != screen) {
            logger.info("Screen : " + screen);
            prevScreen = screen;
        }
        if(prevActionPhase != actionManager.phase) {
            logger.info("ActionPhase : " + actionManager.phase);
            prevActionPhase = actionManager.phase;
        }
        if(prevhasTakenAll != combatRewardScreen.hasTakenAll) {
            logger.info("TakenAll : " + combatRewardScreen.hasTakenAll);
            prevhasTakenAll = combatRewardScreen.hasTakenAll;
        }
    }
}
