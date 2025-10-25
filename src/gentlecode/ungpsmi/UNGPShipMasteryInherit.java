package gentlecode.ungpsmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

import com.fs.starfarer.api.util.Misc;
import gentlecode.ungpsmi.config.Settings;
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

    private final Map<String, SMISaveData> tempSnapshot = new HashMap<>();

    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    @Override
    public UNGP_DataSaverAPI createSaverBasedOnCurrentGame(UNGP_InGameData inGameData)
    {
        inheritCount = 0;
        UNGPShipMasteryInherit dataSaver = new UNGPShipMasteryInherit();
        if(!dataSaver.isShipMasteryExist)
        {
            return dataSaver;
        }

//        if (!SMI_SAVE_DATA_MAP.isEmpty()) return this;
//
//        SMI_SAVE_DATA_MAP.clear();

        if (!tempSnapshot.isEmpty()) {
            SMI_SAVE_DATA_MAP.putAll(tempSnapshot);
            tempSnapshot.clear();
            LOGGER.info("UNGP-SMI: temp snapshot merged into static map.");
        }

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
                LOGGER.info("UNGP-SMI: found one valid ship, " + hullId + "'s points: " + masteryPointData + ". level: " + masteryLevelData);


                SMISaveData smiSaveData = SMI_SAVE_DATA_MAP.computeIfAbsent(hullId, k -> new SMISaveData());
                LOGGER.info("UNGP-SMI: SNAPSHOT PUT: " + hullId + "'s points: " + masteryPointData + ". level: " + masteryLevelData + " map.size=" + SMI_SAVE_DATA_MAP.size());

                //For points and level saving
                smiSaveData.pointAndLevelData = new SaveData(masteryPointData, masteryLevelData);

                //For level effect, level option, level strength saving
                int max = ShipMastery.getMaxMasteryLevel(shipSpec);
                LOGGER.info("UNGP-SMI: ship " + hullId + "'s max level is: " + max);
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

                    String activeOpt = ShipMastery.getPlayerActiveMasteriesCopy(shipSpec).get(lv);

                    if (activeOpt != null && !activeOpt.isEmpty()) {
                        LOGGER.info("UNGP-SMI: createSaver: " + hullId + " lv " + lv + "'s activated option is " + activeOpt);
                        smiSaveData.activatedOptions.put(lv, activeOpt);
                    } else {
                        LOGGER.info("UNGP-SMI: createSaver: " + hullId + " lv " + lv + " has no activated option.");
                    }
                }

                for (int lv = 1; lv <= max; lv++)
                {
                    LOGGER.info("UNGP-SMI: createSaver: " + smiSaveData.activatedOptions.get(lv));
                }
            }
        }

        LOGGER.info("UNGP-SMI: save based on current game completed.");
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
        //LOGGER.info("UNGP-SMI: BEFORE SAVE: map.size=" + SMI_SAVE_DATA_MAP.size());
        tempSnapshot.clear();                     // Clear it and make it new

        JSONObject rootObj = jsonObject.optJSONObject("SMI_playerMasteryData");
        if (rootObj == null) {                         // No snapshot found
            LOGGER.warn("UNGP-SMI: No mastery snapshot found, will keep random effects.");
            return;
        }

        Iterator<?> hullIds = rootObj.keys();
        while (hullIds.hasNext()) {
            SMISaveData smiData = new SMISaveData();
            String hullId = (String) hullIds.next();
            JSONObject shipNode = rootObj.getJSONObject(hullId);

            /* ---------- 1. points / level ---------- */
            JSONObject ptLvl = shipNode.getJSONObject("pointLevel");
            float pts = (float) ptLvl.optDouble("points", 0);
            int   lvl = ptLvl.optInt("level", 0);
            SaveData pointLevelData = new SaveData((float) pts, lvl);

            /* ---------- 2. level effect ---------- */
            Map<Integer, Map<String, List<String>>> effectMap = new HashMap<>();
            JSONObject levelRoot = shipNode.optJSONObject("effects");
            if (levelRoot != null) {
                Iterator<?> lvStrIt = levelRoot.keys();
                while (lvStrIt.hasNext()) {
                    String lvStr = (String) lvStrIt.next();
                    int lv = Integer.parseInt(lvStr);
                    JSONObject levelNode = levelRoot.getJSONObject(lvStr);

                    Map<String, List<String>> optMap = new HashMap<>();

                    //Read out each level's activated options in the JSON file
                    String activatedOpt = null;
                    if (levelNode.has("activated")) {
                        Object activatedObj = levelNode.get("activated");
                        if (activatedObj != JSONObject.NULL) {
                            activatedOpt = activatedObj.toString();
                        }
                    }
                    LOGGER.info("UNGP-SMI: load:" + hullId + " lv " + lv + "'s activated option is " + activatedOpt);
                    if(activatedOpt != null && !activatedOpt.isEmpty()) {
                        smiData.activatedOptions.put(lv, activatedOpt);
                    }

                    Iterator<?> optIt = levelNode.keys();
                    while (optIt.hasNext()) {
                        String optId = (String) optIt.next();

                        if(optId.equals("activated")) { //Skip if it reads out "activated"
                            continue;
                        }

                        JSONObject optNode = levelNode.getJSONObject(optId);
                        // Read out generators array
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
            smiData.pointAndLevelData = pointLevelData;
            smiData.masteryEffectData = effectMap;
            tempSnapshot.put(hullId, smiData);
        }

        LOGGER.info("UNGP-SMI: load snapshot completed. ships=" + tempSnapshot.size());
    }

    @Override
    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        JSONObject rootObj = new JSONObject();

        for (Map.Entry<String, SMISaveData> entry : SMI_SAVE_DATA_MAP.entrySet()) {
            String hullId = entry.getKey();
            SMISaveData smiSaveData = entry.getValue();

            /******** 1. points / level ********/
            JSONObject ptLvlJson = new JSONObject();
            ptLvlJson.put("points", smiSaveData.pointAndLevelData.points);
            ptLvlJson.put("level",  smiSaveData.pointAndLevelData.level);
            //LOGGER.info(("UNGP-SMI: save: Points: " + smiSaveData.pointAndLevelData.points + " Level: " + smiSaveData.pointAndLevelData.level));

            /******** 2. level effect ********/
            JSONObject levelRoot = new JSONObject();
            for (Map.Entry<Integer, Map<String, List<String>>> lvEntry : smiSaveData.masteryEffectData.entrySet()) {
                int lv = lvEntry.getKey();
                Map<String, List<String>> optMap = lvEntry.getValue();

                JSONObject levelNode = new JSONObject();
                for (Map.Entry<String, List<String>> optEntry : optMap.entrySet()) {
                    String optId      = optEntry.getKey();
                    List<String> gens = optEntry.getValue();

                    JSONObject optNode = new JSONObject();
                    optNode.put("generators", new JSONArray(gens)); // 直接塞 List<String>
                    levelNode.put(optId, optNode);
                }
                String activateOpt = smiSaveData.activatedOptions.get(lv);
                LOGGER.info("UNGP-SMI: save:" + hullId + " lv " + lv + "'s activated option is " + activateOpt);
                levelNode.put("activated", activateOpt == null ? JSONObject.NULL : activateOpt);

                levelRoot.put(String.valueOf(lv), levelNode);
            }

            /******** 3. Combine to one node ********/
            JSONObject shipNode = new JSONObject();
            shipNode.put("pointLevel", ptLvlJson);
            shipNode.put("effects", levelRoot);

            rootObj.put(hullId, shipNode);
        }

        jsonObject.put("SMI_playerMasteryData", rootObj);
        LOGGER.info("UNGP-SMI: save JSON snapshot completed. ships=" + SMI_SAVE_DATA_MAP.size());

        //SMI_SAVE_DATA_MAP.clear();
    }

    @Override
    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        inheritCount = 0;

        /* No snapshot found */
        if (!isShipMasteryExist || SMI_SAVE_DATA_MAP == null || SMI_SAVE_DATA_MAP.isEmpty())
        {
            return;
        }

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
                LOGGER.warn("UNGP-SMI: hull spec [" + hullId + "] not found, skipped.");
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
            if (Settings.INHERIT_LEVEL_EFFECTS) {
                for (int lv = 1; lv <= sd.level; lv++) {
                    Map<String, List<String>> optSnap = snap.masteryEffectData.get(lv);
                    if (optSnap == null) continue;

                    /* Use old save's data to override new save's level effect generators, in order to eliminate randomness.*/
                    SMIPublicHelper.overrideLevelGenerators(spec, lv, optSnap);

                    /* Reinitialise this level layer (to activate the new generator)*/
                    try {
                        ShipMastery.generateMasteries(spec, Collections.singleton(lv), 0, false);
                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException f) {
                        LOGGER.warn("UNGP-SMI: Re-generation failed for " + hullId + " lv=" + lv, f);
                    }

                    if (Settings.INHERIT_ACTIVATED_OPTION) {
                        /* Reactive the option if in old save player had activated any of them. */
                        String oldActiveOpt = snap.activatedOptions.get(lv);
                        if (oldActiveOpt != null) {
                            LOGGER.info("UNGP-SMI:" + hullId + " lv " + lv + "'s activated option is " + oldActiveOpt + ".");

                            //Reactive here.
                            sd.activateLevel(lv, oldActiveOpt);
                            List<MasteryEffect> effects = ShipMastery.getMasteryEffects(spec, lv, oldActiveOpt);
                            for (MasteryEffect eff : effects)
                                //Let player see the outcome.
                                eff.onActivate(Global.getSector().getPlayerPerson());
                        } else {
                            LOGGER.warn("UNGP-SMI:" + hullId + " lv " + lv + " has no activated option.");
                        }
                    } else {
                        LOGGER.info("UNGP-SMI: INHERIT_ACTIVATED_OPTION is not enabled.");
                    }
                }
            } else {
                LOGGER.info("UNGP-SMI: INHERIT_LEVEL_EFFECTS is not enabled.");
            }

            inheritCount++;
        }

        /* 5. Refresh fleet cache, in order to avoid UI bugs or any other bugs. */
        DeferredActionPlugin.performLater(Utils::fixPlayerFleetInconsistencies, 0f);

        LOGGER.info("UNGP-SMI: inheriting data + overriding random effects completed. ships=" + inheritCount);
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
