package automatethespire;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

import java.util.ArrayList;

public class EventScreenUtils {
    public static EventDialogType getEventDialogType() {
        boolean genericShown = ReflectionHacks.getPrivateStatic(GenericEventDialog.class, "show");
        if(genericShown) {
            return EventDialogType.IMAGE;
        }
        boolean roomShown =
            ReflectionHacks.getPrivate((AbstractDungeon.getCurrRoom()).event.roomEventText, RoomEventDialog.class,
                "show");
        if(roomShown) {
            return EventDialogType.ROOM;
        }
        return EventDialogType.NONE;
    }

    public static ArrayList<LargeDialogOptionButton> getEventButtons() {
        EventDialogType eventType = getEventDialogType();
        switch (eventType) {
            case IMAGE:
                return (AbstractDungeon.getCurrRoom()).event.imageEventText.optionList;
            case ROOM:
                return RoomEventDialog.optionList;
        }
        return new ArrayList<>();
    }

    public static ArrayList<LargeDialogOptionButton> getActiveEventButtons() {
        ArrayList<LargeDialogOptionButton> buttons = getEventButtons();
        ArrayList<LargeDialogOptionButton> activeButtons = new ArrayList<>();
        for (LargeDialogOptionButton button : buttons) {
            if(!button.isDisabled) {
                activeButtons.add(button);
            }
        }
        return activeButtons;
    }

    public enum EventDialogType {
        IMAGE,
        ROOM,
        NONE
    }
}
