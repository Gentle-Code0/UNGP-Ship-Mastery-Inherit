package gentlecode.ungpsmi.config;

import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Settings {
    public static boolean INHERIT_LEVEL_EFFECTS;
    public static boolean INHERIT_ACTIVATED_OPTION;

    public static void loadSettingsFromJson() throws JSONException, IOException
    {
        JSONObject json = Global.getSettings().loadJSON("UNGPSMI_settings.json", "ungpshipmasteryinherit");

        try {
            INHERIT_LEVEL_EFFECTS = json.optBoolean("inheritLevelEffects", true);
            INHERIT_ACTIVATED_OPTION = json.optBoolean("inheritActivatedOption", false);
        } catch (Exception e) {
            throw new RuntimeException("Error loading JSON UNGPSMI_OPTIONS...");
        }
    }

}
