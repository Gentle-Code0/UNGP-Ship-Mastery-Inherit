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
    private static final Logger LOGGER = Global.getLogger(UNGP_RulesManager.class);

    /** 唯一一次字段反射：从 MasteryGenerator 反跳 levelData */
    public static MasteryLevelData getLevelDataPublic(ShipHullSpecAPI spec, int level) {
        try {
            // 1. 公开 API 逼 ShipMastery 建缓存
            ShipMastery.getMasteryEffects(spec, level, "");
            // 2. 拿任意一个 generator
            MasteryGenerator gen = ShipMastery.getGenerators(spec, level, "").get(0);
            // 3. 反射一次字段（真实名字）
            Field f = gen.getClass().getDeclaredField("parentLevelData");
            f.setAccessible(true);
            return (MasteryLevelData) f.get(gen);
        } catch (Exception e) {
            // 字段不对或 SecurityException：返回 null，外部会跳过覆盖
            return null;
        }
    }

    public static void overrideLevelGenerators(ShipHullSpecAPI spec,
                                               int level,
                                               Map<String, List<String>> optSnap) {
        for (Map.Entry<String, List<String>> optEntry : optSnap.entrySet()) {
            String optId = optEntry.getKey();
            List<MasteryGenerator> gens = ShipMastery.getGenerators(spec, level, optId);
            if (gens == null) continue;          // 这层没 generator 列表

            /* 1. 公开方法清掉旧随机内容 */
            gens.clear();                        // public 方法

            /* 2. 把旧档 generator 填回去 */
            for (String g : optEntry.getValue()) {
                gens.add(ShipMastery.makeGenerator(g)); // public 方法
            }
        }
    }
}