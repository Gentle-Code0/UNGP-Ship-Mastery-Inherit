package gentlecode.ungpsmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

import com.thoughtworks.xstream.mapper.Mapper;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import shipmastery.data.SaveData;
import ungp.api.saves.UNGP_DataSaverAPI;
import ungp.scripts.campaign.UNGP_InGameData;
import ungp.scripts.campaign.specialist.rules.UNGP_RulesManager;
import shipmastery.ShipMastery;
import shipmastery.util.Utils;

public class UNGPShipMasteryInherit implements UNGP_DataSaverAPI {
    private final boolean isShipMasteryExist = Global.getSettings().getModManager().isModEnabled("shipmasterysystem");

    /** A place to save data get from ShipMastery */
    private static ShipMastery.SaveDataTable SAVE_DATA_TABLE;

    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    public UNGP_DataSaverAPI createSaverBasedOnCurrentGame(UNGP_InGameData inGameData)
    {
        if(this.isShipMasteryExist)
        {
            for (ShipHullSpecAPI shipSpec : Global.getSettings().getAllShipHullSpecs())
            {
                int masteryLevelData = ShipMastery.getPlayerMasteryLevel(shipSpec);
                float masteryPointData = ShipMastery.getPlayerMasteryPoints(shipSpec);
                if(masteryLevelData != 0 || masteryPointData != 0.0f)
                {
                    setSaveDataTable(shipSpec, masteryLevelData, masteryPointData);
                }
            }
        }
        LOGGER.info("UNGP Ship Mastery Inherit save based on current game completed.");
        return this;
    }

    public UNGP_DataSaverAPI createEmptySaver()
    {
        return new UNGPShipMasteryInherit();
    }

    public void loadDataFromSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        Iterator<String> keys = jsonObject.keys();
        while(keys.hasNext())
        {
            String key = keys.next();
            JSONObject saveDataJson = jsonObject.getJSONObject(key);
            float loadedPoints = (float)jsonObject.getDouble(key);
            int loadedLevel = jsonObject.getInt(key);

            // Restore data into Save Data Table
            setSaveDataTable(key, loadedLevel, loadedPoints);
        }
        LOGGER.info("UNGP Ship Mastery Inherit load data from save point slot json completed.");
    }

    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        for(Map.Entry<String, SaveData> entry : SAVE_DATA_TABLE.entrySet())
        {
            SaveData saveData = entry.getValue();
            JSONObject saveDataJson = new JSONObject();
            saveDataJson.put("points", saveData.points);
            saveDataJson.put("level", saveData.level);

            jsonObject.put(entry.getKey(), saveDataJson);
        }
        LOGGER.info("UNGP Ship Mastery Inherit save data to save point slot in json format completed.");
    }

    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        if(isShipMasteryExist && SAVE_DATA_TABLE != null)
        {
            for(ShipHullSpecAPI shipSpec : Global.getSettings().getAllShipHullSpecs())
            {
                SaveData saveData =SAVE_DATA_TABLE.get(shipSpec.getHullId());
                if(saveData != null)
                {
                    ShipMastery.setPlayerMasteryPoints(shipSpec, saveData.points);
                    for(int i = 0; i < saveData.level; i ++)
                    {
                        ShipMastery.advancePlayerMasteryLevel(shipSpec);
                    }
                }
            }
        }

        LOGGER.info("UNGP Ship Mastery Inherit inheriting data completed.");
    }

    public void addSaverInfo(TooltipMakerAPI root, String descKey) {}

//    private JSONObject safeLoadJSONObject(JSONObject rootJSON, String keyID)
//    {
//        try {
//            return new JSONObject();
//        } catch (Exception ignored) {
//            return null;
//        }
//    }

    /** Store data into ShipMastery.SaveDataTable format */
    private static void setSaveDataTable(ShipHullSpecAPI shipSpec, int masteryLevel, float masteryPoint)
    {
        String id = Utils.getRestoredHullSpecId(shipSpec);
        SaveData data = SAVE_DATA_TABLE.get(id);
        if (data == null)
        {
            SAVE_DATA_TABLE.put(id, new SaveData(masteryPoint, masteryLevel));
        } else {
            data.points = masteryPoint;
            data.level = masteryLevel;
        }
    }

    /** Store data into ShipMastery.SaveDataTable format, use only String format ship id*/
    private static void setSaveDataTable(String id, int masteryLevel, float masteryPoint)
    {
        SaveData data = SAVE_DATA_TABLE.get(id);
        if (data == null)
        {
            SAVE_DATA_TABLE.put(id, new SaveData(masteryPoint, masteryLevel));
        } else {
            data.points = masteryPoint;
            data.level = masteryLevel;
        }
    }
}
