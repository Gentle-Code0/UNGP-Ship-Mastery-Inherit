package gentlecode.ungpsmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

import com.fs.starfarer.api.util.Misc;
import gentlecode.ungpsmi.data.SMISaveData;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONString;
import shipmastery.data.SaveData;
import shipmastery.mastery.MasteryEffect;
import ungp.api.saves.UNGP_DataSaverAPI;
import ungp.scripts.campaign.UNGP_InGameData;
import ungp.scripts.campaign.inherit.UNGP_InheritData;
import ungp.scripts.campaign.specialist.rules.UNGP_RulesManager;
import shipmastery.ShipMastery;
import shipmastery.util.Utils;

import static gentlecode.ungpsmi.scripts.utils.Constants.root_i18n;

public class UNGPShipMasteryInherit implements UNGP_DataSaverAPI {
    private final boolean isShipMasteryExist = Global.getSettings().getModManager().isModEnabled("shipmasterysystem");
    private int inheritCount = 0;   //For counting numbers, but not used due to not so useful. Keep it for now

    /** A place to save data get from ShipMastery */
    private static final Map<String, SMISaveData> SMI_SAVE_DATA_MAP = new HashMap<>();
    private static final Map<String, SaveData> masteryPointAndLevelSaveData = new HashMap<>();
    private static final Map<String, Map<Integer, Map<String, List<String>>>> masterySaveData = new HashMap<>();

    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    @Override
    public UNGP_DataSaverAPI createSaverBasedOnCurrentGame(UNGP_InGameData inGameData)
    {
        inheritCount = 0;
        UNGPShipMasteryInherit dataSaver = new UNGPShipMasteryInherit();
        if(dataSaver.isShipMasteryExist)
        {
//            if(saveDataHashMap == null)
//            {
//                saveDataHashMap = new ShipMastery.SaveDataTable();
//            }
            for (ShipHullSpecAPI shipSpec : Global.getSettings().getAllShipHullSpecs())
            {
                //For points and level saving
                int masteryLevelData = ShipMastery.getPlayerMasteryLevel(shipSpec);
                float masteryPointData = ShipMastery.getPlayerMasteryPoints(shipSpec);
                if(masteryLevelData != 0 || masteryPointData != 0.0f)
                {
                    inheritCount ++;
                    //LOGGER.info("UNGP Ship Mastery Inherit create save, points: " + masteryPointData + ". level: " + masteryLevelData);

                    String id = Utils.getRestoredHullSpecId(shipSpec);
                    SaveData data = masteryPointAndLevelSaveData.get(id);
                    if (data == null)
                    {
                        //If there is no, then store
                        masteryPointAndLevelSaveData.put(id, new SaveData(masteryPointData, masteryLevelData));
                    } else {
                        //If there is, then update
                        data.points = masteryPointData;
                        data.level = masteryLevelData;
                    }
                }

                //For level effect, level option, level strength saving
                String hullId = Utils.getRestoredHullSpecId(shipSpec);
                int max = ShipMastery.getMaxMasteryLevel(shipSpec);
                if (max == 0) continue; //Check if the ship has any mastery level

                Map<Integer, Map<String, List<String>>> levelMap = new HashMap<>();
                for (int lv = 1; lv <= max; lv++) {
                    List<String> optionIds = ShipMastery.getMasteryOptionIds(shipSpec, lv); //Get the options info
                    Map<String, List<String>> optionMap = new HashMap<>();
                    for (String opt : optionIds) {
                        List<MasteryEffect> eff = ShipMastery.getMasteryEffects(shipSpec, lv, opt); //Get the option's effect info
                        List<String> idPlusArgs = new ArrayList<>();
                        for (MasteryEffect e : eff) {
                            String id   = ShipMastery.getId(e.getClass());   //Get effect id
                            String[] args = e.getArgs();                     //Get effect strength args
                            idPlusArgs.add(id + " " + String.join(" ", args));
                        }
                        optionMap.put(opt, idPlusArgs);
                    }
                    levelMap.put(lv, optionMap);
                }
                masterySaveData.put(hullId, levelMap);
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
        masteryPointAndLevelSaveData.clear();
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
                masteryPointAndLevelSaveData.put(key, saveData);
                //setSaveDataTable(key, loadedLevel, loadedPoints);
            }
        }
        LOGGER.info("UNGP Ship Mastery Inherit load data from save point slot json completed.");
    }

    @Override
    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        //For points and level JSON
        JSONObject masteryPointAndLevelObj = new JSONObject();
        for(Map.Entry<String, SaveData> entry : masteryPointAndLevelSaveData.entrySet())
        {
            SaveData saveData = entry.getValue();
            JSONObject saveDataJson = new JSONObject();
//            LOGGER.info("UNGP Ship Mastery Inherit save to slot, points: " + saveData.points + ". level: " + saveData.level);
            saveDataJson.put("points", saveData.points);
            saveDataJson.put("level", saveData.level);

            String shipId = entry.getKey();
            masteryPointAndLevelObj.put(shipId, saveDataJson);
//            LOGGER.info("UNGP Ship Mastery Inherit save to slot, ship id: " + shipId);
        }
        jsonObject.put("SMI_playerMasteryPointAndLevelData", masteryPointAndLevelObj);

        //For level effect, level option, level strength JSON
        JSONObject masteryInfoObj = new JSONObject();
        for(Map.Entry<String, Map<Integer, Map<String, List<String>>>> entry1 : masterySaveData.entrySet())
        {
            String hullId = entry1.getKey();
            Map<Integer, Map<String, List<String>>> levelMap = entry1.getValue();

            JSONObject shipNode = new JSONObject();

            for(Map.Entry<Integer, Map<String, List<String>>> entry2 : levelMap.entrySet())
            {
                int lv = entry2.getKey();;
                Map<String, List<String>> optionMap = entry2.getValue();

                JSONObject levelNode = new JSONObject();

                for(Map.Entry<String, List<String>> entry3 : optionMap.entrySet())
                {
                    String optId = entry3.getKey();
                    List<String> idPlusArgs = entry3.getValue();

                    JSONObject optNode = new JSONObject();
                    optNode.put("optionId", optId);

                    optNode.put("generators", idPlusArgs);
                    levelNode.put(optId, optNode);
                }
                shipNode.put(String.valueOf(lv), levelNode);
            }
            masteryInfoObj.put(hullId, shipNode);
        }
        jsonObject.put("SMI_playerMasteryEffectData", masteryInfoObj);

        LOGGER.info("UNGP Ship Mastery Inherit save data to save point slot in json format completed.");
    }

    @Override
    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        inheritCount = 0;
//        if(isShipMasteryExist && saveDataHashMap != null)
//        {
//            for(String hullId : saveDataHashMap.keySet())
//            {
//                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
//                SaveData saveData = saveDataHashMap.get(hullId);
//                if(saveData != null)
//                {
//                    ShipMastery.setPlayerMasteryPoints(spec, saveData.points);
//                    for(int i = 0; i < saveData.level; i ++)
//                    {
//                        ShipMastery.advancePlayerMasteryLevel(spec);
//                    }
//                }
//            }
//        }
        if (!isShipMasteryExist || masteryPointAndLevelSaveData == null)
            return;

        for (Map.Entry<String, SaveData> e : masteryPointAndLevelSaveData.entrySet())
        {
            String hullId = e.getKey();
            SaveData saveData = e.getValue();
            if (saveData == null) continue;

            /* 先检查 hullId 是否还存在于当前游戏 */
            boolean isFound = false;
            for(ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs())
            {
                if(hullId.equals(spec.getHullId()))
                {
                    isFound = true;
                    break;
                }
            }

            if(!isFound)
            {
                LOGGER.warn("UNGP Ship Mastery Inherit Mod: hull spec [" + hullId + "] not found, skipped.");
                continue;
            }

            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null)        // 保险起见，再判一次
                continue;

            inheritCount ++;
            /* 正常恢复数据 */
            ShipMastery.addPlayerMasteryPoints(spec, saveData.points, false, false, ShipMastery.MasteryGainSource.OTHER);
            for (int i = 0; i < saveData.level; i++)
                ShipMastery.advancePlayerMasteryLevel(spec);
        }

        LOGGER.info("UNGP Ship Mastery Inherit inheriting data completed.");
    }

    @Override
    public void addSaverInfo(TooltipMakerAPI root, String descKey)
    {
        LOGGER.info("Should be showing SMI's inherit data on screen now.");
        TooltipMakerAPI section = root.beginImageWithText("graphics/icons/reports/fleet24b.png", 24f, 250f, false);
        section.addPara(root_i18n.get("mod_indication"), 3f);
        root.addImageWithText(5f);
        root.addPara(UNGP_InheritData.BULLETED_PREFIX + root_i18n.get("addSaverInfo_01"), 5f, Misc.getHighlightColor(), String.valueOf(masteryPointAndLevelSaveData.size()));
    }

//    private JSONObject safeLoadJSONObject(JSONObject rootJSON, String keyID)
//    {
//        try {
//            return new JSONObject();
//        } catch (Exception ignored) {
//            return null;
//        }
//    }

}
