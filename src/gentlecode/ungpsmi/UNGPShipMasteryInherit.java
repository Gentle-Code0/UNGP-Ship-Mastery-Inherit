package gentlecode.ungpsmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

import com.fs.starfarer.api.util.Misc;
import gentlecode.ungpsmi.config.Settings;
import gentlecode.ungpsmi.data.SMISpecShipSaveData;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import gentlecode.ungpsmi.helper.SMIPublicHelper;
import shipmastery.data.SaveData;
import shipmastery.deferred.DeferredActionPlugin;
import shipmastery.mastery.MasteryEffect;
import shipmastery.plugin.ModPlugin;
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
    private final Map<String, SMISpecShipSaveData> SMI_SAVE_DATA_MAP = new HashMap<>();
    private String SMI_SEED_SAVE_DATA = null;

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

        // Save the global mastery seed so the new save reproduces identical random rolls
        dataSaver.SMI_SEED_SAVE_DATA = (String) Global.getSector().getPersistentData().get(ModPlugin.GENERATION_SEED_KEY);
        LOGGER.info("UNGP-SMI: captured mastery seed: " + dataSaver.SMI_SEED_SAVE_DATA);

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


                SMISpecShipSaveData smiSpecShipSaveData = dataSaver.SMI_SAVE_DATA_MAP.computeIfAbsent(hullId, k -> new SMISpecShipSaveData());
                LOGGER.info("UNGP-SMI: SNAPSHOT PUT: " + hullId + "'s points: " + masteryPointData + ". level: " + masteryLevelData + " map.size=" + SMI_SAVE_DATA_MAP.size());

                //For points and level saving
                smiSpecShipSaveData.pointAndLevelData = new SaveData(masteryPointData, masteryLevelData);

                //For level effect, level option, level strength saving
                int max = ShipMastery.getMaxMasteryLevel(shipSpec);
                //LOGGER.info("UNGP-SMI: ship " + hullId + "'s max level is: " + max);
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
                    smiSpecShipSaveData.masteryEffectData.put(lv, optionMap);

                    String activeOpt = ShipMastery.getPlayerActiveMasteriesCopy(shipSpec).get(lv);

                    if (activeOpt != null && !activeOpt.isEmpty()) {
                        //LOGGER.info("UNGP-SMI: createSaver: " + hullId + " lv " + lv + "'s activated option is " + activeOpt);
                        smiSpecShipSaveData.activatedOptions.put(lv, activeOpt);
                    } else {
                        //LOGGER.info("UNGP-SMI: createSaver: " + hullId + " lv " + lv + " has no activated option.");
                    }
                }

                for (int lv = 1; lv <= max; lv++)
                {
                    LOGGER.info("UNGP-SMI: createSaver: " + smiSpecShipSaveData.activatedOptions.get(lv));
                }

                // For reroll sequence saving
                @SuppressWarnings("unchecked")
                Map<String, List<Set<Integer>>> globalRerollMap = (Map<String, List<Set<Integer>>>)
                        Global.getSector().getPersistentData().get(ShipMastery.REROLL_SEQUENCE_MAP);
                if (globalRerollMap != null) {
                    List<Set<Integer>> seq = globalRerollMap.get(hullId);
                    if (seq != null && !seq.isEmpty()) {
                        // Deep copy to avoid reference sharing
                        List<Set<Integer>> seqCopy = new ArrayList<>();
                        for (Set<Integer> s : seq) {
                            seqCopy.add(new HashSet<>(s));
                        }
                        smiSpecShipSaveData.rerollMap = seqCopy;
                        LOGGER.info("UNGP-SMI: createSaver: " + hullId + " rerollSequence size=" + seqCopy.size());
                    }
                } else {
                    LOGGER.info("UNGP-SMI: createSaver: no reroll sequence for " + hullId + " (never rerolled, expected)");
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
        this.SMI_SAVE_DATA_MAP.clear();                     // Clear it and make it new

        // Load the global mastery seed
        Object seedObj = jsonObject.opt("SMI_seed");
        if (seedObj != null && seedObj != JSONObject.NULL) {
            this.SMI_SEED_SAVE_DATA = seedObj.toString();
        } else {
            this.SMI_SEED_SAVE_DATA = null;
        }

        JSONObject rootObj = jsonObject.optJSONObject("SMI_playerMasteryData");
        if (rootObj == null) {                         // No snapshot found
            LOGGER.warn("UNGP-SMI: No mastery snapshot found, will keep random effects.");
            return;
        }

        Iterator<?> hullIds = rootObj.keys();
        while (hullIds.hasNext()) {
            SMISpecShipSaveData smiData = new SMISpecShipSaveData();
            String hullId = (String) hullIds.next();

            try {
                ShipHullSpecAPI testSpec = Global.getSettings().getHullSpec(hullId);
                if (testSpec == null) {
                    LOGGER.warn("UNGP-SMI: Skipping non-existent hull in snapshot: " + hullId);
                    continue;
                }


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
                        //LOGGER.info("UNGP-SMI: load:" + hullId + " lv " + lv + "'s activated option is " + activatedOpt);
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

                /* ---------- 3. reroll sequence ---------- */
                JSONArray rerollSeqArr = shipNode.optJSONArray("rerollSequence");
                if (rerollSeqArr != null && rerollSeqArr.length() > 0) {
                    List<Set<Integer>> rerollList = new ArrayList<>();
                    for (int i = 0; i < rerollSeqArr.length(); i++) {
                        JSONArray setArr = rerollSeqArr.getJSONArray(i);
                        Set<Integer> levelSet = new HashSet<>();
                        for (int j = 0; j < setArr.length(); j++) {
                            levelSet.add(setArr.getInt(j));
                        }
                        rerollList.add(levelSet);
                    }
                    smiData.rerollMap = rerollList;
                    LOGGER.info("UNGP-SMI: load: " + hullId + " rerollSequence size=" + rerollList.size());
                }

                /* ---------- 4. assemble back to SMISaveData ---------- */
                smiData.pointAndLevelData = pointLevelData;
                smiData.masteryEffectData = effectMap;
                this.SMI_SAVE_DATA_MAP.put(hullId, smiData);
            } catch (Exception ex) {
                LOGGER.warn("UNGP-SMI: Skipping invalid hull in snapshot: " + hullId, ex);
                continue;
            }
        }

        LOGGER.info("UNGP-SMI: load snapshot completed. ships=" + this.SMI_SAVE_DATA_MAP.size());
    }

    @Override
    public void saveDataToSavepointSlot(JSONObject jsonObject) throws JSONException
    {
        JSONObject rootObj = new JSONObject();

        for (Map.Entry<String, SMISpecShipSaveData> entry : this.SMI_SAVE_DATA_MAP.entrySet()) {
            String hullId = entry.getKey();
            SMISpecShipSaveData smiSpecShipSaveData = entry.getValue();

            /******** 1. points / level ********/
            JSONObject ptLvlJson = new JSONObject();
            ptLvlJson.put("points", smiSpecShipSaveData.pointAndLevelData.points);
            ptLvlJson.put("level",  smiSpecShipSaveData.pointAndLevelData.level);
            //LOGGER.info(("UNGP-SMI: save: Points: " + smiSaveData.pointAndLevelData.points + " Level: " + smiSaveData.pointAndLevelData.level));

            /******** 2. level effect ********/
            JSONObject levelRoot = new JSONObject();
            for (Map.Entry<Integer, Map<String, List<String>>> lvEntry : smiSpecShipSaveData.masteryEffectData.entrySet()) {
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
                String activateOpt = smiSpecShipSaveData.activatedOptions.get(lv);
                //LOGGER.info("UNGP-SMI: save:" + hullId + " lv " + lv + "'s activated option is " + activateOpt);
                levelNode.put("activated", activateOpt == null ? JSONObject.NULL : activateOpt);

                levelRoot.put(String.valueOf(lv), levelNode);
            }

            /******** 3. reroll sequence ********/
            JSONArray rerollSeqJson = new JSONArray();
            if (smiSpecShipSaveData.rerollMap != null) {
                for (Set<Integer> levelSet : smiSpecShipSaveData.rerollMap) {
                    JSONArray setArr = new JSONArray();
                    for (Integer lvNum : levelSet) {
                        setArr.put(lvNum);
                    }
                    rerollSeqJson.put(setArr);
                }
            }

            /******** 4. Combine to one node ********/
            JSONObject shipNode = new JSONObject();
            shipNode.put("pointLevel", ptLvlJson);
            shipNode.put("effects", levelRoot);
            shipNode.put("rerollSequence", rerollSeqJson);

            rootObj.put(hullId, shipNode);
        }

        jsonObject.put("SMI_playerMasteryData", rootObj);
        jsonObject.put("SMI_seed", SMI_SEED_SAVE_DATA == null ? JSONObject.NULL : SMI_SEED_SAVE_DATA);
        LOGGER.info("UNGP-SMI: save JSON snapshot completed. ships=" + this.SMI_SAVE_DATA_MAP.size());

    }

    @Override
    public void startInheritDataFromSaver(TooltipMakerAPI root, Map<String, Object> params)
    {
        inheritCount = 0;

        /* No snapshot found */
        if (!isShipMasteryExist || this.SMI_SAVE_DATA_MAP == null || this.SMI_SAVE_DATA_MAP.isEmpty())
        {
            return;
        }

        /* 1. make sure mastery table is loaded, so it can be replaced. */
        ShipMastery.loadMasteryTable();

        ShipMastery.SaveDataTable table = (ShipMastery.SaveDataTable) Global.getSector().
                getPersistentData().
                get(ShipMastery.MASTERY_KEY);

        if (Settings.INHERIT_LEVEL_EFFECTS)
        {
            // Restore the mastery seed so random rolls are identical to the previous save
            if (SMI_SEED_SAVE_DATA != null) {
                Global.getSector().getPersistentData().put(ModPlugin.GENERATION_SEED_KEY, SMI_SEED_SAVE_DATA);
                LOGGER.info("UNGP-SMI: seed written. key=" + ModPlugin.GENERATION_SEED_KEY
                        + " value=" + Global.getSector().getPersistentData().get(ModPlugin.GENERATION_SEED_KEY));
            }
        }

        for (Map.Entry<String, SMISpecShipSaveData> e : this.SMI_SAVE_DATA_MAP.entrySet())
        {
            String  hullId   = e.getKey();
            SMISpecShipSaveData snap = e.getValue();
            if (snap == null) continue;

            try {
                /* 2.  Check if this saved hull still exists in the game */
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) {
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
                    /* Restore reroll sequence so story-point rerolls remain consistent */
                    if (snap.rerollMap != null && !snap.rerollMap.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, List<Set<Integer>>> globalRerollMap = (Map<String, List<Set<Integer>>>)
                                Global.getSector().getPersistentData().get(ShipMastery.REROLL_SEQUENCE_MAP);
                        if (globalRerollMap == null) {
                            globalRerollMap = new HashMap<>();
                            Global.getSector().getPersistentData().put(ShipMastery.REROLL_SEQUENCE_MAP, globalRerollMap);
                        }
                        // Deep copy to avoid reference sharing
                        List<Set<Integer>> seqCopy = new ArrayList<>();
                        for (Set<Integer> s : snap.rerollMap) {
                            seqCopy.add(new HashSet<>(s));
                        }
                        globalRerollMap.put(hullId, seqCopy);
                        LOGGER.info("UNGP-SMI: restored rerollSequence for " + hullId + " size=" + seqCopy.size());
                    } else {
                        LOGGER.info("UNGP-SMI: no reroll sequence to restore for " + hullId + " (never rerolled, expected)");
                    }

                    /* Re-generate masteries with the restored seed + reroll sequence.
                     * IMPORTANT: calling getMasteryOptionIds first forces ShipMastery to
                     * lazy-initialise the internal mastery data for this hull. Without this,
                     * generateMasteries operates on uninitialised state and produces
                     * different (wrong) results. */
                    try {
                        int maxLv = ShipMastery.getMaxMasteryLevel(spec);
                        for (int lv = 1; lv <= maxLv; lv++) {
                            ShipMastery.getMasteryOptionIds(spec, lv);
                        }
                        ShipMastery.generateMasteries(spec);
                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException f) {
                        LOGGER.warn("UNGP-SMI: Re-generation failed for " + hullId, f);
                    }

                    if (Settings.INHERIT_ACTIVATED_OPTION) {
                        /* Reactive the option if in old save player had activated any of them. */
                        for (int lv = 1; lv <= sd.level; lv++) {
                            String oldActiveOpt = snap.activatedOptions.get(lv);
                            if (oldActiveOpt != null) {
                                LOGGER.info("UNGP-SMI:" + hullId + " lv " + lv + "'s activated option is " + oldActiveOpt + ".");

                                //Reactive here.
                                List<String> validOptions = ShipMastery.getMasteryOptionIds(spec, lv);
                                if (validOptions.contains(oldActiveOpt)) {
                                    sd.activateLevel(lv, oldActiveOpt);
                                    List<MasteryEffect> effects = ShipMastery.getMasteryEffects(spec, lv, oldActiveOpt);
                                    for (MasteryEffect eff : effects)
                                        //Let player see the outcome.
                                        eff.onActivate(Global.getSector().getPlayerPerson());
                                }
                            } else {
                                LOGGER.info("UNGP-SMI:" + hullId + " lv " + lv + " has no activated option.");
                            }
                        }
                    } else {
                        LOGGER.info("UNGP-SMI: INHERIT_ACTIVATED_OPTION is not enabled.");
                    }
                } else {
                    LOGGER.info("UNGP-SMI: INHERIT_LEVEL_EFFECTS is not enabled.");
                }

            } catch (Exception ex) {
                LOGGER.error("UNGP-SMI: Error when inheriting data for hullId: " + hullId, ex);
                continue;
            }

            inheritCount++;
        }

        /* 5. Re-apply all active mastery effects so they take effect immediately without a save/reload. */
        if (Settings.INHERIT_LEVEL_EFFECTS) {
            ShipMastery.activatePlayerMasteries();
        }

        /* 6. Refresh fleet cache, in order to avoid UI bugs or any other bugs. */
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
        root.addPara(UNGP_InheritData.BULLETED_PREFIX + root_i18n.get("addSaverInfo_01"), 5f, Misc.getHighlightColor(), String.valueOf(this.SMI_SAVE_DATA_MAP.size()));
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
