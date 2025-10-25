package gentlecode.ungpsmi.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.apache.log4j.Logger;
import shipmastery.ShipMastery;
import shipmastery.data.MasteryGenerator;
import shipmastery.data.MasteryLevelData;
import shipmastery.mastery.MasteryEffect;
import ungp.scripts.campaign.specialist.rules.UNGP_RulesManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class SMIPublicHelper {
    public static void overrideLevelGenerators(ShipHullSpecAPI spec,
                                               int level,
                                               Map<String, List<String>> optSnap) {
        for (Map.Entry<String, List<String>> optEntry : optSnap.entrySet()) {
            String optId = optEntry.getKey();
            List<MasteryGenerator> gens = ShipMastery.getGenerators(spec, level, optId);
            if (gens == null) continue;          // If this level layer has no generator.

            /* 1. Clear old random data */
            gens.clear();                        // public method

            /* 2. Put old save's generators back. */
            for (String g : optEntry.getValue()) {
                gens.add(ShipMastery.makeGenerator(g)); // public method
            }
        }
    }
}