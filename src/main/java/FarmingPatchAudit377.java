import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FarmingPatchAudit377 {
    private static final int REVISION = 377;
    private static final String PACKET_MODE = "cdab";
    private static final int CLEAN_STATE = 3;

    private static final int STD_WATERED = 0x40;
    private static final int STD_DISEASED = 0x80;
    private static final int STD_DEAD = 0xC0;
    private static final int HERB_DISEASED = 0x7F;
    private static final int HERB_DEAD = 0xBF;

    public static boolean ENABLE_AUTO_DUMP = true;

    private static int sceneBaseTileX = 0;
    private static int sceneBaseTileZ = 0;
    private static final Map<Integer, List<MapObject>> CAPTURED_MAP_OBJECTS = new HashMap<Integer, List<MapObject>>();

    private static final Range[] FARMING_RANGES = new Range[] {
            new Range(7800, 7905),
            new Range(7906, 8065),
            new Range(8130, 8215),
            new Range(8535, 8687),
            new Range(8770, 8790)
    };

    private static final TreeSet<Integer> KNOWN_CONTROLLERS = new TreeSet<Integer>(Arrays.asList(
            8150, 8151, 8152, 8153,
            7847, 7848, 7849, 7850,
            8550, 8551, 8552, 8553, 8554, 8555, 8556, 8557,
            7962, 7963, 7964, 7965,
            7838
    ));

    private final List<String> warnings = new ArrayList<String>();
    private int lastControllerCount = 0;

    public static void beginMapCapture(int baseX, int baseZ) {
        sceneBaseTileX = baseX;
        sceneBaseTileZ = baseZ;
        CAPTURED_MAP_OBJECTS.clear();
    }

    public static void recordMapLoc(int objectId, int localX, int localZ, int level, int type, int rotation) {
        try {
            LocType loc = LocType.get(objectId);
            if (!isProbablyFarmingLoc(objectId, loc)) {
                return;
            }

            int absX = localX + sceneBaseTileX;
            int absZ = localZ + sceneBaseTileZ;
            int region = ((absX >> 6) << 8) + (absZ >> 6);

            MapObject obj = new MapObject(
                    objectId,
                    absX,
                    absZ,
                    level,
                    region,
                    type,
                    rotation & 3,
                    Math.max(1, loc.sizeX),
                    Math.max(1, loc.sizeZ)
            );

            List<MapObject> list = CAPTURED_MAP_OBJECTS.get(objectId);
            if (list == null) {
                list = new ArrayList<MapObject>();
                CAPTURED_MAP_OBJECTS.put(objectId, list);
            }

            if (!list.contains(obj)) {
                list.add(obj);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void dumpLoadedClient() {
        if (!ENABLE_AUTO_DUMP) {
            return;
        }
        try {
            FarmingPatchAudit377 audit = new FarmingPatchAudit377();
            String json = audit.runAudit(true);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream("farming_patch_audit_377.json"), StandardCharsets.UTF_8));
            try {
                writer.println(json);
            } finally {
                writer.close();
            }
            System.out.println("[FarmingPatchAudit377] Wrote farming_patch_audit_377.json controllers=" + audit.lastControllerCount() + " captured_ids=" + CAPTURED_MAP_OBJECTS.size());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String out = "farming_patch_audit_377.json";
        boolean includeUnknown = true;
        for (int i = 0; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = args[++i];
            } else if ("--no-unknown".equals(args[i])) {
                includeUnknown = false;
            }
        }

        FarmingPatchAudit377 audit = new FarmingPatchAudit377();
        String json = audit.runAudit(includeUnknown);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8));
        try {
            writer.println(json);
        } finally {
            writer.close();
        }
        System.out.println("Wrote " + out);
    }

    private int lastControllerCount() {
        return lastControllerCount;
    }

    public String runAudit(boolean includeUnknown) {
        List<ControllerAudit> controllers = new ArrayList<ControllerAudit>();

        if (LocType.count <= 0 || LocType.cache == null || LocType.offsets == null || LocType.dat == null) {
            warn("LocType is not unpacked yet. Run from Game after LocType.unpack(...).");
            return toJson(controllers);
        }

        if (VarbitType.instances == null || VarbitType.instances.length == 0) {
            warn("VarbitType.instances is not unpacked yet. Run from Game after VarbitType.unpack(...).");
        }

        for (int id = 0; id < LocType.count; id++) {
            LocType loc;
            try {
                loc = LocType.get(id);
            } catch (Throwable t) {
                warn("Failed reading LocType " + id + ": " + t);
                continue;
            }

            if (loc == null || loc.overrideTypeIDs == null || loc.overrideTypeIDs.length == 0) {
                continue;
            }
            if (loc.varbit < 0 && loc.varp < 0) {
                continue;
            }

            boolean likely = isProbablyFarmingLoc(id, loc);
            if (!likely && !includeUnknown) {
                continue;
            }

            controllers.add(buildAudit(id, loc, likely));
        }

        Collections.sort(controllers, new Comparator<ControllerAudit>() {
            public int compare(ControllerAudit a, ControllerAudit b) {
                int t = a.patchType.compareTo(b.patchType);
                if (t != 0) return t;
                return Integer.compare(a.objectId, b.objectId);
            }
        });

        lastControllerCount = controllers.size();
        return toJson(controllers);
    }

    private ControllerAudit buildAudit(int id, LocType loc, boolean likely) {
        ControllerAudit out = new ControllerAudit();
        out.objectId = id;
        out.name = loc.name == null ? "" : loc.name;
        out.actions = loc.options == null ? new String[0] : loc.options;
        out.patchType = guessPatchType(id, loc);
        out.likelyFarming = likely;
        out.objectVarbit = loc.varbit;
        out.objectVarp = loc.varp;

        if (loc.varbit >= 0 && VarbitType.instances != null && loc.varbit < VarbitType.instances.length && VarbitType.instances[loc.varbit] != null) {
            VarbitType vb = VarbitType.instances[loc.varbit];
            out.varp = vb.varp;
            out.lsb = vb.lsb;
            out.msb = vb.msb;
        } else if (loc.varp >= 0) {
            out.varp = loc.varp;
            out.lsb = 0;
            out.msb = 31;
        } else {
            warn("Could not resolve varbit/varp for controller " + id + " loc.varbit=" + loc.varbit + " loc.varp=" + loc.varp);
        }

        if (out.lsb < 0) out.lsb = 0;
        if (out.msb < out.lsb) out.msb = out.lsb + 7;
        out.bitWidth = out.msb - out.lsb + 1;
        out.cleanState = CLEAN_STATE;
        out.cleanValue = CLEAN_STATE << out.lsb;
        out.children = compactChildren(loc.overrideTypeIDs);
        out.childToFirstIndex = childToFirstIndex(out.children);
        out.stateBands = classifyStateBands(out.patchType, out.children);
        out.width = Math.max(1, loc.sizeX);
        out.height = Math.max(1, loc.sizeZ);

        List<MapObject> mapLocations = CAPTURED_MAP_OBJECTS.get(id);
        if (mapLocations == null) {
            out.mapLocations = Collections.emptyList();
        } else {
            out.mapLocations = new ArrayList<MapObject>(mapLocations);
            Collections.sort(out.mapLocations);
        }

        out.suggestedPatchEntries = suggestedPatchEntries(out);
        return out;
    }

    private static boolean isProbablyFarmingLoc(int id, LocType loc) {
        if (loc == null) return false;
        if (KNOWN_CONTROLLERS.contains(id)) {
            return true;
        }
        if (inAnyRange(id)) {
            return true;
        }
        String hay = haystack(loc);
        if (containsAny(hay, new String[] {
                "farming", "patch", "allotment", "herb", "flower", "compost", "hops",
                "bush", "berry", "tree", "diseased", "dead", "weed"
        })) {
            return true;
        }
        if (loc.overrideTypeIDs != null) {
            for (int child : loc.overrideTypeIDs) {
                if (child >= 0 && (KNOWN_CONTROLLERS.contains(child) || inAnyRange(child))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String guessPatchType(int id, LocType loc) {
        String hay = haystack(loc);
        if (containsAny(hay, new String[] {"compost"})) return "compost_bin";
        if (containsAny(hay, new String[] {"allotment", "potato", "onion", "cabbage", "tomato", "sweetcorn", "strawberry", "watermelon"})) return "allotment";
        if (containsAny(hay, new String[] {"herb", "guam", "marrentill", "tarromin", "harralander", "ranarr", "toadflax", "irit", "avantoe", "kwuarm", "cadantine", "lantadyme", "dwarf", "torstol"})) return "herb";
        if (containsAny(hay, new String[] {"flower", "marigold", "rosemary", "nasturtium", "woad", "limpwurt", "lily"})) return "flower";
        if (containsAny(hay, new String[] {"hops", "barley", "jute", "yanillian", "krandorian", "wildblood"})) return "hops";
        if (containsAny(hay, new String[] {"bush", "berry", "berries", "ivy"})) return "bush";
        if (containsAny(hay, new String[] {"fruit"})) return "fruit_tree";
        if (containsAny(hay, new String[] {"tree"})) return "tree";

        if (id >= 8535 && id <= 8687) return "allotment";
        if (id >= 8150 && id <= 8153) return "herb";
        if (id >= 7847 && id <= 7850) return "flower";
        if (id == 7838) return "compost_bin";
        if (id >= 7962 && id <= 7965) return "fruit_tree";
        if (id >= 7906 && id <= 8065) return "tree";

        int min = Integer.MAX_VALUE;
        int max = -1;
        if (loc.overrideTypeIDs != null) {
            for (int child : loc.overrideTypeIDs) {
                if (child >= 0) {
                    min = Math.min(min, child);
                    max = Math.max(max, child);
                }
            }
        }
        if (min >= 8535 && max <= 8687) return "allotment";
        if (min >= 8130 && max <= 8155) return "herb";
        if (min >= 7840 && max <= 7905) return "flower";
        if (min >= 7906 && max <= 8065) return "tree";
        return "unknown";
    }

    private Map<String, List<Integer>> classifyStateBands(String patchType, int[] children) {
        Map<String, List<Integer>> out = new LinkedHashMap<String, List<Integer>>();
        out.put("weeds", new ArrayList<Integer>());
        out.put("clean", new ArrayList<Integer>());
        out.put("growing", new ArrayList<Integer>());
        out.put("watered", new ArrayList<Integer>());
        out.put("diseased", new ArrayList<Integer>());
        out.put("dead", new ArrayList<Integer>());

        for (int i = 0; i < children.length; i++) {
            if (children[i] < 0) continue;
            if (i >= 0 && i <= 2) {
                out.get("weeds").add(i);
            } else if (i == CLEAN_STATE) {
                out.get("clean").add(i);
            } else if ("herb".equals(patchType)) {
                if (i >= HERB_DEAD) out.get("dead").add(i);
                else if (i >= HERB_DISEASED) out.get("diseased").add(i);
                else out.get("growing").add(i);
            } else {
                if (i >= STD_DEAD) out.get("dead").add(i);
                else if (i >= STD_DISEASED) out.get("diseased").add(i);
                else if (i >= STD_WATERED) out.get("watered").add(i);
                else out.get("growing").add(i);
            }
        }
        return out;
    }

    private List<Map<String, Object>> suggestedPatchEntries(ControllerAudit audit) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (audit.mapLocations.isEmpty()) {
            out.add(basePatchEntry(audit, null));
            return out;
        }
        for (MapObject loc : audit.mapLocations) {
            out.add(basePatchEntry(audit, loc));
        }
        return out;
    }

    private Map<String, Object> basePatchEntry(ControllerAudit audit, MapObject loc) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        int x = loc == null ? -1 : loc.x;
        int z = loc == null ? -1 : loc.z;
        int level = loc == null ? 0 : loc.level;
        String location = guessLocationName(x, z, loc == null ? -1 : loc.region);
        String id = x < 0 ? ("unmapped_" + audit.patchType + "_" + audit.objectId) : ("map_" + audit.patchType + "_" + x + "_" + z + "_" + level);

        entry.put("id", id);
        entry.put("name", title(location) + " " + audit.patchType.replace('_', ' ') + " patch");
        entry.put("location", location);
        entry.put("type", audit.patchType);
        entry.put("x", x);
        entry.put("y", z);
        entry.put("z", level);
        entry.put("width", loc == null ? audit.width : loc.width);
        entry.put("height", loc == null ? audit.height : loc.height);
        entry.put("object_type", loc == null ? 10 : loc.type);
        entry.put("face", loc == null ? 0 : loc.rotation);
        entry.put("varbit_controller_object_id", audit.objectId);
        entry.put("varbit_varp_id", audit.varp);
        entry.put("varbit_lsb", audit.lsb);
        entry.put("varbit_msb", audit.msb);
        entry.put("varbit_clean_value", audit.cleanValue);
        return entry;
    }

    private String toJson(List<ControllerAudit> controllers) {
        StringBuilder sb = new StringBuilder(1024 * 1024);
        sb.append("{\n");
        field(sb, 1, "revision", REVISION, true);
        field(sb, 1, "packet_mode", PACKET_MODE, true);
        field(sb, 1, "scene_base_x", sceneBaseTileX, true);
        field(sb, 1, "scene_base_y", sceneBaseTileZ, true);

        indent(sb, 1).append("\"controllers\": [\n");
        for (int i = 0; i < controllers.size(); i++) {
            controllerJson(sb, controllers.get(i), 2);
            if (i + 1 < controllers.size()) sb.append(",");
            sb.append("\n");
        }
        indent(sb, 1).append("],\n");

        indent(sb, 1).append("\"captured_map_object_id_count\": ").append(CAPTURED_MAP_OBJECTS.size()).append(",\n");
        indent(sb, 1).append("\"warnings\": ");
        stringArray(sb, warnings);
        sb.append("\n}");
        return sb.toString();
    }

    private void controllerJson(StringBuilder sb, ControllerAudit c, int level) {
        indent(sb, level).append("{\n");
        field(sb, level + 1, "object_id", c.objectId, true);
        field(sb, level + 1, "name", c.name, true);
        field(sb, level + 1, "patch_type_hint", c.patchType, true);
        field(sb, level + 1, "object_varbit", c.objectVarbit, true);
        field(sb, level + 1, "object_varp", c.objectVarp, true);
        field(sb, level + 1, "varp", c.varp, true);
        field(sb, level + 1, "lsb", c.lsb, true);
        field(sb, level + 1, "msb", c.msb, true);
        field(sb, level + 1, "bit_width", c.bitWidth, true);
        field(sb, level + 1, "clean_state", c.cleanState, true);
        field(sb, level + 1, "clean_value", c.cleanValue, true);
        field(sb, level + 1, "width", c.width, true);
        field(sb, level + 1, "height", c.height, true);

        indent(sb, level + 1).append("\"actions\": ");
        stringArray(sb, Arrays.asList(c.actions));
        sb.append(",\n");

        indent(sb, level + 1).append("\"children\": ");
        intMap(sb, indexToChild(c.children));
        sb.append(",\n");

        indent(sb, level + 1).append("\"child_to_first_index\": ");
        intMap(sb, c.childToFirstIndex);
        sb.append(",\n");

        indent(sb, level + 1).append("\"state_bands\": ");
        bands(sb, c.stateBands);
        sb.append(",\n");

        indent(sb, level + 1).append("\"map_locations\": ");
        mapLocations(sb, c.mapLocations);
        sb.append(",\n");

        indent(sb, level + 1).append("\"suggested_patch_entries\": ");
        suggestedEntries(sb, c.suggestedPatchEntries, level + 1);
        sb.append("\n");
        indent(sb, level).append("}");
    }

    private static String haystack(LocType loc) {
        StringBuilder sb = new StringBuilder();
        if (loc.name != null) sb.append(loc.name).append(' ');
        if (loc.examine != null) sb.append(loc.examine).append(' ');
        if (loc.options != null) {
            for (String option : loc.options) {
                if (option != null) sb.append(option).append(' ');
            }
        }
        return sb.toString().toLowerCase();
    }

    private static boolean containsAny(String hay, String[] terms) {
        for (String term : terms) {
            if (hay.contains(term.toLowerCase())) return true;
        }
        return false;
    }

    private static boolean inAnyRange(int value) {
        for (Range range : FARMING_RANGES) {
            if (range.contains(value)) return true;
        }
        return false;
    }

    private static int[] compactChildren(int[] raw) {
        if (raw == null) return new int[0];
        int last = -1;
        for (int i = 0; i < raw.length; i++) {
            int value = raw[i] == 65535 ? -1 : raw[i];
            if (value >= 0) last = i;
        }
        if (last < 0) return new int[0];

        int[] out = new int[last + 1];
        for (int i = 0; i <= last; i++) {
            out[i] = raw[i] == 65535 ? -1 : raw[i];
        }
        return out;
    }

    private static Map<Integer, Integer> childToFirstIndex(int[] children) {
        Map<Integer, Integer> out = new TreeMap<Integer, Integer>();
        for (int i = 0; i < children.length; i++) {
            if (children[i] >= 0 && !out.containsKey(children[i])) {
                out.put(children[i], i);
            }
        }
        return out;
    }

    private static Map<Integer, Integer> indexToChild(int[] children) {
        Map<Integer, Integer> out = new TreeMap<Integer, Integer>();
        for (int i = 0; i < children.length; i++) {
            if (children[i] >= 0) out.put(i, children[i]);
        }
        return out;
    }

    private void warn(String msg) {
        if (!warnings.contains(msg)) warnings.add(msg);
        System.err.println("[FarmingPatchAudit377] " + msg);
    }

    private static String guessLocationName(int x, int z, int region) {
        if (x >= 2790 && x <= 2875 && z >= 3420 && z <= 3485) return "catherby";
        if (x >= 3040 && x <= 3075 && z >= 3300 && z <= 3325) return "falador";
        if (x >= 2650 && x <= 2695 && z >= 3350 && z <= 3385) return "ardougne";
        if (x >= 3590 && x <= 3625 && z >= 3520 && z <= 3555) return "phasmatys";
        if (region >= 0) return "region_" + region;
        return "unknown";
    }

    private static String title(String raw) {
        if (raw == null || raw.length() == 0) return "Unknown";
        raw = raw.replace('_', ' ');
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private static void field(StringBuilder sb, int level, String key, Object value, boolean comma) {
        indent(sb, level).append("\"").append(escape(key)).append("\": ");
        value(sb, value);
        if (comma) sb.append(",");
        sb.append("\n");
    }

    private static void value(StringBuilder sb, Object value) {
        if (value == null) sb.append("null");
        else if (value instanceof Number || value instanceof Boolean) sb.append(String.valueOf(value));
        else sb.append("\"").append(escape(String.valueOf(value))).append("\"");
    }

    private static void stringArray(StringBuilder sb, List<String> values) {
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escape(values.get(i))).append("\"");
        }
        sb.append("]");
    }

    private static void intMap(StringBuilder sb, Map<Integer, Integer> map) {
        sb.append("{");
        int i = 0;
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ").append(e.getValue());
        }
        sb.append("}");
    }

    private static void bands(StringBuilder sb, Map<String, List<Integer>> map) {
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, List<Integer>> e : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(escape(e.getKey())).append("\": ");
            intArray(sb, e.getValue());
        }
        sb.append("}");
    }

    private static void intArray(StringBuilder sb, List<Integer> values) {
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        sb.append("]");
    }

    private static void mapLocations(StringBuilder sb, List<MapObject> list) {
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            MapObject m = list.get(i);
            if (i > 0) sb.append(", ");
            sb.append("{");
            sb.append("\"x\": ").append(m.x).append(", ");
            sb.append("\"y\": ").append(m.z).append(", ");
            sb.append("\"z\": ").append(m.level).append(", ");
            sb.append("\"region\": ").append(m.region).append(", ");
            sb.append("\"type\": ").append(m.type).append(", ");
            sb.append("\"rotation\": ").append(m.rotation).append(", ");
            sb.append("\"width\": ").append(m.width).append(", ");
            sb.append("\"height\": ").append(m.height);
            sb.append("}");
        }
        sb.append("]");
    }

    private static void suggestedEntries(StringBuilder sb, List<Map<String, Object>> entries, int level) {
        sb.append("[");
        if (!entries.isEmpty()) sb.append("\n");
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            indent(sb, level + 1).append("{\n");
            int j = 0;
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                field(sb, level + 2, e.getKey(), e.getValue(), j + 1 < entry.size());
                j++;
            }
            indent(sb, level + 1).append("}");
            if (i + 1 < entries.size()) sb.append(",");
            sb.append("\n");
        }
        if (!entries.isEmpty()) indent(sb, level);
        sb.append("]");
    }

    private static StringBuilder indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("    ");
        return sb;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.toString();
    }

    public static final class MapObject implements Comparable<MapObject> {
        public final int objectId;
        public final int x;
        public final int z;
        public final int level;
        public final int region;
        public final int type;
        public final int rotation;
        public final int width;
        public final int height;

        public MapObject(int objectId, int x, int z, int level, int region, int type, int rotation, int width, int height) {
            this.objectId = objectId;
            this.x = x;
            this.z = z;
            this.level = level;
            this.region = region;
            this.type = type;
            this.rotation = rotation;
            this.width = width;
            this.height = height;
        }

        public int compareTo(MapObject other) {
            if (level != other.level) return Integer.compare(level, other.level);
            if (region != other.region) return Integer.compare(region, other.region);
            if (x != other.x) return Integer.compare(x, other.x);
            if (z != other.z) return Integer.compare(z, other.z);
            if (objectId != other.objectId) return Integer.compare(objectId, other.objectId);
            if (type != other.type) return Integer.compare(type, other.type);
            return Integer.compare(rotation, other.rotation);
        }

        public boolean equals(Object o) {
            if (!(o instanceof MapObject)) return false;
            MapObject m = (MapObject)o;
            return objectId == m.objectId && x == m.x && z == m.z && level == m.level && type == m.type && rotation == m.rotation;
        }

        public int hashCode() {
            int h = objectId;
            h = h * 31 + x;
            h = h * 31 + z;
            h = h * 31 + level;
            h = h * 31 + type;
            h = h * 31 + rotation;
            return h;
        }
    }

    private static final class ControllerAudit {
        int objectId;
        String name = "";
        String[] actions = new String[0];
        String patchType = "unknown";
        boolean likelyFarming;
        int objectVarbit = -1;
        int objectVarp = -1;
        int varp = -1;
        int lsb = -1;
        int msb = -1;
        int bitWidth = 0;
        int cleanState = CLEAN_STATE;
        int cleanValue = 0;
        int width = 1;
        int height = 1;
        int[] children = new int[0];
        Map<Integer, Integer> childToFirstIndex = new TreeMap<Integer, Integer>();
        Map<String, List<Integer>> stateBands = new LinkedHashMap<String, List<Integer>>();
        List<MapObject> mapLocations = Collections.emptyList();
        List<Map<String, Object>> suggestedPatchEntries = Collections.emptyList();
    }

    private static final class Range {
        final int min;
        final int max;
        Range(int min, int max) {
            this.min = min;
            this.max = max;
        }
        boolean contains(int value) {
            return value >= min && value <= max;
        }
    }
}
