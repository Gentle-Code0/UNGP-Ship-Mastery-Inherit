package gentlecode.ungpsmi.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//Stores data that are specific to a ship.
public class SMISpecShipSaveData {
    public shipmastery.data.SaveData pointAndLevelData;
    public Map<Integer, Map<String, List<String>>> masteryEffectData = new HashMap<>();
    public Map<Integer, String> activatedOptions = new HashMap<>();
    public List<Set<Integer>> rerollMap = null;
}
