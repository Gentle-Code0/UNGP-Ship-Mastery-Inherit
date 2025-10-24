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
import gentlecode.ungpsmi.helper.SMIPublicHelper;
import shipmastery.data.MasteryGenerator;
import shipmastery.data.MasteryLevelData;
import shipmastery.data.SaveData;
import shipmastery.deferred.DeferredActionPlugin;
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

                int masteryLevelData = ShipMastery.getPlayerMasteryLevel(shipSpec);
                float masteryPointData = ShipMastery.getPlayerMasteryPoints(shipSpec);
                if(masteryLevelData != 0 || masteryPointData != 0.0f)
                {
                    inheritCount ++;
                    //LOGGER.info("UNGP Ship Mastery Inherit create save, points: " + masteryPointData + ". level: " + masteryLevelData);
                    String hullId = Utils.getRestoredHullSpecId(shipSpec);
                    LOGGER.info("UNGP Ship Mastery Inherit found one valid ship, " + hullId + "'s points: " + masteryPointData + ". level: " + masteryLevelData);


                    SMISaveData smiSaveData = SMI_SAVE_DATA_MAP.computeIfAbsent(hullId, k -> new SMISaveData());
                    LOGGER.info("UNGP Ship Mastery Inherit SNAPSHOT PUT: " + hullId + "'s points: " + masteryPointData + ". level: " + masteryLevelData + " map.size=" + SMI_SAVE_DATA_MAP.size());

                    //For points and level saving
                    smiSaveData.pointAndLevelData = new SaveData(masteryPointData, masteryLevelData);

                    //For level effect, level option, level strength saving
                    int max = ShipMastery.getMaxMasteryLevel(shipSpec);
                    LOGGER.info("UNGP Ship Mastery Inherit, ship " + hullId + "'s max level is: " + max);
                    if (max == 0) continue; //Check if the ship has any mastery level

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
                        smiSaveData.masteryEffectData.put(lv, optionMap);
                    }
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
        LOGGER.info("UNGP Ship Mastery Inherit BEFORE SAVE: map.size=" + SMI_SAVE_DATA_MAP.size());
        //SMI_SAVE_DATA_MAP.clear();                     // Clear it and make it new

        JSONObject rootObj = jsonObject.optJSONObject("SMI_playerMasteryData");
        if (rootObj == null) {                         // No snapshot found
            LOGGER.warn("No mastery snapshot found, will keep random effects.");
            return;
        }

        Iterator<?> hullIds = rootObj.keys();
        while (hullIds.hasNext()) {
            String hullId = (String) hullIds.next();
            JSONObject shipNode = rootObj.getJSONObject(hullId);

            /* ---------- 1. points / level ---------- */
            JSONObject ptLvl = shipNode.getJSONObject("pointLevel");
            float pts = (float) ptLvl.optDouble("points", 0);
            int   lvl = ptLvl.optInt("level", 0);
            SaveData pointLevelData = new SaveData((float) pts, lvl);

            /* ---------- 2. level effect ---------- */
            Map<Integer, Map<String, List<String>>> effectMap = new HashMap<>();
            JSONObject effRoot = shipNode.optJSONObject("effects");
            if (effRoot != null) {
                Iterator<?> lvStrIt = effRoot.keys();
                while (lvStrIt.hasNext()) {
                    String lvStr = (String) lvStrIt.next();
                    int lv = Integer.parseInt(lvStr);
                    JSONObject levelNode = effRoot.getJSONObject(lvStr);

                    Map<String, List<String>> optMap = new HashMap<>();
                    Iterator<?> optIt = levelNode.keys();
                    while (optIt.hasNext()) {
                        String optId = (String) optIt.next();
                        JSONObject optNode = levelNode.getJSONObject(optId);

                        // 读出 generators 数组
                        JSONArray gensArr = optNode.getJSONArray("generators");
                        List<String> gens = new ArrayList<>();
                        for (int i = 0; i < gensArr.length(); i++) {
                            gens.add(gensArr.getString(i));
                        }
                        optMap.put(optId, gens);
                    }
                    effectMap.put(lv, optMap);
                }
            }

            /* ---------- 3. assemble back to SMISaveData ---------- */
            SMISaveData smiData = new SMISaveData();
            smiData.pointAndLevelData = pointLevelData;
            smiData.masteryEffectData = effectMap;
            SMI_SAVE_DATA_MAP.put(hullId, smiData);
        }

        LOGGER.info("UNGP Ship Mastery Inherit load snapshot completed. ships=" + SMI_SAVE_DATA_MAP.size());

//        masteryPointAndLevelSaveData.clear();
//        JSONObject obj = jsonObject.optJSONObject("SMI_playerMasteryData");
//        Iterator<?> keys = obj.keys();
//        while(keys.hasNext())
//        {
//            String key = (String) keys.next();
////            LOGGER.info("UNGP Ship Mastery Inherit find key: " + key);
//            if(!key.isEmpty())
//            {
////                LOGGER.info("UNGP Ship Mastery Inherit," + key + " is not empty");
//                JSONObject saveDataJson = obj.optJSONObject(key);
//                float loadedPoints = 0.0f;
//                int loadedLevel = 0;
//                if(!saveDataJson.isNull("points")) {
//                    loadedPoints = (float) saveDataJson.getDouble("points");
////                    LOGGER.info("UNGP Ship Mastery Inherit, points: " + loadedPoints);
//                }
//                if(!saveDataJson.isNull("level")){
//                    loadedLevel = saveDataJson.getInt("level");
////                    LOGGER.info("UNGP Ship Mastery Inherit, level: " + loadedLevel);
//                }
//                //LOGGER.info("UNGP Ship Mastery Inherit, points: " + loadedPoints + ". level: " + loadedLevel);
//                SaveData saveData = new SaveData(loadedPoints, loadedLevel);
//
//                // Restore data into Save Data Table
//                masteryPointAndLevelSaveData.put(key, saveData);
//                //setSaveDataTable(key, loadedLevel, loadedPoints);
//            }
//        }
//        LOGGER.info("UNGP Ship Mastery Inherit load data from save point slot json completed.");
    }

    @Override
    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        JSONObject rootObj = new JSONObject();

        for (Map.Entry<String, SMISaveData> entry : SMI_SAVE_DATA_MAP.entrySet()) {
            String hullId = entry.getKey();
            SMISaveData data = entry.getValue();

            /******** 1. points / level ********/
            JSONObject ptLvlJson = new JSONObject();
            ptLvlJson.put("points", data.pointAndLevelData.points);
            ptLvlJson.put("level",  data.pointAndLevelData.level);

            /******** 2. level effect ********/
            JSONObject levelRoot = new JSONObject();
            for (Map.Entry<Integer, Map<String, List<String>>> lvEntry : data.masteryEffectData.entrySet()) {
                int lv = lvEntry.getKey();
                Map<String, List<String>> optMap = lvEntry.getValue();

                JSONObject levelNode = new JSONObject();
                for (Map.Entry<String, List<String>> optEntry : optMap.entrySet()) {
                    String optId      = optEntry.getKey();
                    List<String> gens = optEntry.getValue();

                    JSONObject optNode = new JSONObject();
                    optNode.put("optionId", optId);
                    optNode.put("generators", new JSONArray(gens)); // 直接塞 List<String>
                    levelNode.put(optId, optNode);
                }
                levelRoot.put(String.valueOf(lv), levelNode);
            }

            /******** 3. Combine to one node ********/
            JSONObject shipNode = new JSONObject();
            shipNode.put("pointLevel", ptLvlJson);
            shipNode.put("effects", levelRoot);

            rootObj.put(hullId, shipNode);
        }

        jsonObject.put("SMI_playerMasteryData", rootObj);
        LOGGER.info("UNGP Ship Mastery Inherit save JSON snapshot completed. ships=" + SMI_SAVE_DATA_MAP.size());

        SMI_SAVE_DATA_MAP.clear();
    }

    @Override
    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        inheritCount = 0;

        /* No snapshot found */
        if (!isShipMasteryExist || SMI_SAVE_DATA_MAP == null || SMI_SAVE_DATA_MAP.isEmpty())
            return;

        /* 1. make sure mastery table is loaded, so it can be replaced. */
        ShipMastery.loadMasteryTable();

        ShipMastery.SaveDataTable table = (ShipMastery.SaveDataTable) Global.getSector().
                getPersistentData().
                get(ShipMastery.MASTERY_KEY);

        for (Map.Entry<String, SMISaveData> e : SMI_SAVE_DATA_MAP.entrySet())
        {
            String  hullId   = e.getKey();
            SMISaveData snap = e.getValue();
            if (snap == null) continue;

            /* 2.  Check if this saved hull still exists in the game */
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null)
            {
                LOGGER.warn("UNGP Ship Mastery Inherit Mod: hull spec [" + hullId + "] not found, skipped.");
                continue;
            }

            /* 3. Inherit mastery points */
            ShipMastery.addPlayerMasteryPoints(spec,
                    snap.pointAndLevelData.points,
                    false, false,
                    ShipMastery.MasteryGainSource.OTHER);

            /* 4. Inherit and cover the level first, avoid using advancePlayerMasteryLevel causing randomization */
            int maxLevel = ShipMastery.getMaxMasteryLevel(spec);
            int targetLevel = snap.pointAndLevelData.level;
            if (targetLevel <= 0 || maxLevel == 0) continue;
            SaveData sd = table.computeIfAbsent(hullId, k -> new SaveData(0, 0));
            sd.level = Math.min(targetLevel, maxLevel);

            /* 5. Cover the random generated level effect */

            /* 6. Cover generators layer by layer */
            for (int lv = 1; lv <= sd.level; lv++)
            {
                Map<String, List<String>> optSnap = snap.masteryEffectData.get(lv);
                if (optSnap == null) continue;
                SMIPublicHelper.overrideLevelGenerators(spec, lv, optSnap);   // 全部公开 API

                /* 5-2 重新初始化该层（让新 generator 生效）*/
                try {
                    ShipMastery.generateMasteries(spec, Collections.singleton(lv), 0, false);
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException f) {
                    LOGGER.warn("Re-generation failed for " + hullId + " lv=" + lv, f);
                }

                /* 5-3 如果玩家已激活该层，重新触发 onActivate */
                String activeOpt = sd.activeLevels.get(lv);
                if (activeOpt != null) {
                    List<MasteryEffect> effects = ShipMastery.getMasteryEffects(spec, lv, activeOpt);
                    for (MasteryEffect eff : effects)
                        eff.onActivate(Global.getSector().getPlayerPerson());
                }
//                Map<String, List<String>> optSnap = snap.masteryEffectData.get(lv);
//                if (optSnap == null)
//                {
//                    LOGGER.warn("No Option snapshot for " + hullId + " lv=" + lv + " - skipped");
//                    continue;  //Leave it random if no snapshot found in any layer
//                }
//
//                ShipMastery.getMasteryEffects(spec, lv, "");
//                MasteryLevelData levelData = SMIPublicHelper.getLevelDataPublic(spec, lv);
//                if (levelData == null)
//                {
//                    LOGGER.warn("No MasteryLevelData for " + hullId + " lv=" + lv + " - skipped");
//                    continue;
//                }
//
//                /* Clear old random data */
//                levelData.clear();
//
//                /* Put generator in snapshot back into it */
//                for (Map.Entry<String, List<String>> optEntry : optSnap.entrySet())
//                {
//                    String optId = optEntry.getKey();
//                    for (String g : optEntry.getValue())
//                    {
//                        MasteryGenerator gen = ShipMastery.makeGenerator(g);
//                        levelData.addGeneratorToList(optId, gen);
//                    }
//                }
//
//                /* Restore activated effect options */
//                String activeOpt = sd.activeLevels.get(lv);
//                if (activeOpt != null)
//                {
//                    List<MasteryEffect> effects = ShipMastery.getMasteryEffects(spec, lv, activeOpt);
//                    for (MasteryEffect eff : effects)
//                        eff.onActivate(Global.getSector().getPlayerPerson());
//                }
            }

            inheritCount++;
        }

        /* 5. 修复舰队不一致（ Deferred 一帧后执行） */
        DeferredActionPlugin.performLater(Utils::fixPlayerFleetInconsistencies, 0f);

        LOGGER.info("UNGP Ship Mastery Inherit  inheriting data + overriding random effects completed. ships=" + inheritCount);


//        inheritCount = 0;
//
//        if (!isShipMasteryExist || masteryPointAndLevelSaveData == null)
//            return;
//
//        for (Map.Entry<String, SaveData> e : masteryPointAndLevelSaveData.entrySet())
//        {
//            String hullId = e.getKey();
//            SaveData saveData = e.getValue();
//            if (saveData == null) continue;
//
//            /* 先检查 hullId 是否还存在于当前游戏 */
//            boolean isFound = false;
//            for(ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs())
//            {
//                if(hullId.equals(spec.getHullId()))
//                {
//                    isFound = true;
//                    break;
//                }
//            }
//
//            if(!isFound)
//            {
//                LOGGER.warn("UNGP Ship Mastery Inherit Mod: hull spec [" + hullId + "] not found, skipped.");
//                continue;
//            }
//
//            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
//            if (spec == null)        // 保险起见，再判一次
//                continue;
//
//            inheritCount ++;
//            /* 正常恢复数据 */
//            ShipMastery.addPlayerMasteryPoints(spec, saveData.points, false, false, ShipMastery.MasteryGainSource.OTHER);
//            for (int i = 0; i < saveData.level; i++)
//                ShipMastery.advancePlayerMasteryLevel(spec);
//        }
//
//        LOGGER.info("UNGP Ship Mastery Inherit inheriting data completed.");
    }

    @Override
    public void addSaverInfo(TooltipMakerAPI root, String descKey)
    {
        LOGGER.info("Should be showing SMI's inherit data on screen now.");
        TooltipMakerAPI section = root.beginImageWithText("graphics/icons/reports/fleet24b.png", 24f, 250f, false);
        section.addPara(root_i18n.get("mod_indication"), 3f);
        root.addImageWithText(5f);
        root.addPara(UNGP_InheritData.BULLETED_PREFIX + root_i18n.get("addSaverInfo_01"), 5f, Misc.getHighlightColor(), String.valueOf(SMI_SAVE_DATA_MAP.size()));
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
