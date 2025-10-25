package gentlecode.ungpsmi.plugin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import gentlecode.ungpsmi.config.Settings;
import gentlecode.ungpsmi.config.UNGPSMI_SettingListener;
import org.apache.log4j.Logger;
import ungp.scripts.campaign.specialist.rules.UNGP_RulesManager;

public class UNGPSMI_ModPlugin extends BaseModPlugin {
    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    @Override
    public void onApplicationLoad() throws Exception {
        if(Global.getSettings().getModManager().isModEnabled("lunalib"))
        {
            LOGGER.info("UNGP-SMI: LunaLib detected.");
            UNGPSMI_SettingListener.init();
        } else {
            LOGGER.info("UNGP-SMI: LunaLib not detected.");
            Settings.loadSettingsFromJson();
        }
    }
}
