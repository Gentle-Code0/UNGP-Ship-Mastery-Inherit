package gentlecode.ungpsmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

import org.apache.log4j.Logger;
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
    public Map<String, SaveData> saveDataHashMap = new HashMap<>();

    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    @Override
    public UNGP_DataSaverAPI createSaverBasedOnCurrentGame(UNGP_InGameData inGameData)
    {
        UNGPShipMasteryInherit dataSaver = new UNGPShipMasteryInherit();
        if(dataSaver.isShipMasteryExist)
        {
//            if(saveDataHashMap == null)
//            {
//                saveDataHashMap = new ShipMastery.SaveDataTable();
//            }
            for (ShipHullSpecAPI shipSpec : Global.getSettings().getAllShipHullSpecs())
            {
                int masteryLevelData = ShipMastery.getPlayerMasteryLevel(shipSpec);
                float masteryPointData = ShipMastery.getPlayerMasteryPoints(shipSpec);
                if(masteryLevelData != 0 || masteryPointData != 0.0f)
                {
                    //LOGGER.info("UNGP Ship Mastery Inherit create save, points: " + masteryPointData + ". level: " + masteryLevelData);
                    dataSaver.setSaveDataTable(shipSpec, masteryLevelData, masteryPointData);
                }
            }
        }
        LOGGER.info("UNGP Ship Mastery Inherit save based on current game completed.");
        return dataSaver;
    }

    @Override
    public UNGP_DataSaverAPI createEmptySaver()
    {
        return new UNGPShipMasteryInherit();
    }

    @Override
    public void loadDataFromSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        saveDataHashMap.clear();
        JSONObject obj = jsonObject.optJSONObject("SMI_playerMasteryData");
        Iterator<?> keys = obj.keys();
        while(keys.hasNext())
        {
            String key = (String) keys.next();
//            LOGGER.info("UNGP Ship Mastery Inherit find key: " + key);
            if(!key.isEmpty())
            {
//                LOGGER.info("UNGP Ship Mastery Inherit," + key + " is not empty");
                JSONObject saveDataJson = obj.optJSONObject(key);
                float loadedPoints = 0.0f;
                int loadedLevel = 0;
                if(!saveDataJson.isNull("points")) {
                    loadedPoints = (float) saveDataJson.getDouble("points");
//                    LOGGER.info("UNGP Ship Mastery Inherit, points: " + loadedPoints);
                }
                if(!saveDataJson.isNull("level")){
                    loadedLevel = saveDataJson.getInt("level");
//                    LOGGER.info("UNGP Ship Mastery Inherit, level: " + loadedLevel);
                }
                //LOGGER.info("UNGP Ship Mastery Inherit, points: " + loadedPoints + ". level: " + loadedLevel);
                SaveData saveData = new SaveData(loadedPoints, loadedLevel);

                // Restore data into Save Data Table
                saveDataHashMap.put(key, saveData);
                //setSaveDataTable(key, loadedLevel, loadedPoints);
            }
        }
        LOGGER.info("UNGP Ship Mastery Inherit load data from save point slot json completed.");
    }

    @Override
    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        JSONObject masteryObj = new JSONObject();
        for(Map.Entry<String, SaveData> entry : saveDataHashMap.entrySet())
        {
            SaveData saveData = entry.getValue();
            JSONObject saveDataJson = new JSONObject();
//            LOGGER.info("UNGP Ship Mastery Inherit save to slot, points: " + saveData.points + ". level: " + saveData.level);
            saveDataJson.put("points", saveData.points);
            saveDataJson.put("level", saveData.level);

            String shipId = entry.getKey();
            masteryObj.put(shipId, saveDataJson);
//            LOGGER.info("UNGP Ship Mastery Inherit save to slot, ship id: " + shipId);
        }
        jsonObject.put("SMI_playerMasteryData", masteryObj);
        LOGGER.info("UNGP Ship Mastery Inherit save data to save point slot in json format completed.");
    }

    @Override
    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        if(isShipMasteryExist && saveDataHashMap != null)
        {
            for(String hullId : saveDataHashMap.keySet())
            {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                SaveData saveData = saveDataHashMap.get(hullId);
                if(saveData != null)
                {
                    ShipMastery.setPlayerMasteryPoints(spec, saveData.points);
                    for(int i = 0; i < saveData.level; i ++)
                    {
                        ShipMastery.advancePlayerMasteryLevel(spec);
                    }
                }
            }
        }

        LOGGER.info("UNGP Ship Mastery Inherit inheriting data completed.");
    }

    @Override
    public void addSaverInfo(TooltipMakerAPI root, String descKey)
    {
    }

//    private JSONObject safeLoadJSONObject(JSONObject rootJSON, String keyID)
//    {
//        try {
//            return new JSONObject();
//        } catch (Exception ignored) {
//            return null;
//        }
//    }

    /** Store data into ShipMastery.SaveDataTable format */
    private void setSaveDataTable(ShipHullSpecAPI shipSpec, int masteryLevel, float masteryPoint)
    {
        String id = Utils.getRestoredHullSpecId(shipSpec);
        SaveData data = saveDataHashMap.get(id);
        if (data == null)
        {
            saveDataHashMap.put(id, new SaveData(masteryPoint, masteryLevel));
        } else {
            data.points = masteryPoint;
            data.level = masteryLevel;
        }
    }

    /** Store data into ShipMastery.SaveDataTable format, use only String format ship id*/
    private void setSaveDataTable(String id, int masteryLevel, float masteryPoint)
    {
        SaveData data = saveDataHashMap.get(id);
        if (data == null)
        {
            saveDataHashMap.put(id, new SaveData(masteryPoint, masteryLevel));
        } else {
            data.points = masteryPoint;
            data.level = masteryLevel;
        }
    }
}
