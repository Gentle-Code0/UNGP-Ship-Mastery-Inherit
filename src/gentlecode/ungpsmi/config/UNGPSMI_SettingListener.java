package gentlecode.ungpsmi.config;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import gentlecode.ungpsmi.scripts.utils.Constants;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import shipmastery.config.LunaLibSettingsListener;
import ungp.scripts.campaign.specialist.rules.UNGP_RulesManager;

public class UNGPSMI_SettingListener implements LunaSettingsListener{
    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    public static void init() {
        UNGPSMI_SettingListener settingListener = new UNGPSMI_SettingListener();
        LunaSettings.addSettingsListener(settingListener);
        settingListener.settingsChanged(Constants.MOD_ID);

        LOGGER.info("UNGP-SMI: LunaLib config should be loaded.");
    }

    @Override
    public void settingsChanged(@NotNull String modId)
    {
        if(!Constants.MOD_ID.equals(modId))
        {
            LOGGER.warn("UNGP-SMI: MOD ID is not matched in settingsChanged.");
            return;
        }

        Settings.INHERIT_LEVEL_EFFECTS = LunaSettings.getBoolean(Constants.MOD_ID, "ungpsmi_InheritLevelEffects");
        Settings.INHERIT_ACTIVATED_OPTION = LunaSettings.getBoolean(Constants.MOD_ID, "ungpsmi_InheritActivatedOption");

        LOGGER.info("UNGP-SMI: Settings are changed.");
    }

}
