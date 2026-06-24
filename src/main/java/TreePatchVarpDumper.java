import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;

public final class TreePatchVarpDumper {
    private static boolean dumped = false;
    private static final int[] EMPTY = new int[0];

    private TreePatchVarpDumper() {
    }

    private static int[] overrides(LocType loc) {
        if (loc == null) {
            return EMPTY;
        }
        int[] arr = loc.overrideTypeIDs;
        return arr == null ? EMPTY : arr;
    }

    public static void dump(Game game) {
        if (dumped) {
            return;
        }
        dumped = true;

        try {
            if (game != null) {
                LocType.game = game;
            }

            try (PrintWriter txt = new PrintWriter(new FileWriter("tree_patch_varp_dump.txt"));
                 PrintWriter jsonl = new PrintWriter(new FileWriter("tree_patch_varp_dump.jsonl"));
                 PrintWriter csv = new PrintWriter(new FileWriter("tree_patch_varp_dump.csv"))) {

                txt.println("Tree / fruit tree / calquat / spirit patch varp dump");
                txt.println("loc_count=" + LocType.count);
                txt.println("varbit_count=" + (VarbitType.instances == null ? -1 : VarbitType.instances.length));
                txt.println();
                csv.println("object_id,name,options,varbit,varp,varbit_varp,lsb,msb,width,children");

                int matches = 0;
                for (int id = 0; id < LocType.count; id++) {
                    LocType loc;
                    try {
                        loc = LocType.get(id);
                    } catch (Throwable t) {
                        continue;
                    }

                    if (!matchesTreePatchRelated(id, loc)) {
                        continue;
                    }

                    matches++;
                    dumpOne(txt, jsonl, csv, id, loc);
                }

                txt.println();
                txt.println("matches=" + matches);
                System.out.println("[TreePatchVarpDumper] Wrote tree_patch_varp_dump.txt/jsonl/csv. matches=" + matches);
            }
        } catch (Throwable t) {
            System.out.println("[TreePatchVarpDumper] Failed:");
            t.printStackTrace();
        }
    }

    private static boolean matchesTreePatchRelated(int id, LocType loc) {
        if (loc == null) {
            return false;
        }

        if (inKnownTreePatchRange(id)) {
            return true;
        }

        if (matchesTreeText(loc.name) || matchesTreeText(loc.examine)) {
            return true;
        }

        if (loc.options != null) {
            for (String option : loc.options) {
                if (matchesTreeText(option)) {
                    return true;
                }
            }
        }

        int[] arr = overrides(loc);
        for (int i = 0; i < arr.length; i++) {
            int childId = arr[i];
            if (childId < 0) {
                continue;
            }
            if (inKnownTreePatchRange(childId)) {
                return true;
            }
            if (childId < LocType.count) {
                try {
                    LocType child = LocType.get(childId);
                    if (child != null && (matchesTreeText(child.name) || matchesTreeText(child.examine))) {
                        return true;
                    }
                    if (child != null && child.options != null) {
                        for (String option : child.options) {
                            if (matchesTreeText(option)) {
                                return true;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return false;
    }

    private static boolean inKnownTreePatchRange(int id) {
        return (id >= 8392 && id <= 8534)
            || (id >= 7935 && id <= 8131)
            || (id >= 7772 && id <= 7806)
            || (id >= 8339 && id <= 8381);
    }

    private static boolean matchesTreeText(String value) {
        if (value == null) {
            return false;
        }

        String s = value.toLowerCase();

        return s.contains("tree patch")
            || s.contains("fruit tree patch")
            || s.contains("calquat patch")
            || s.contains("spirit tree patch")
            || s.equals("oak")
            || s.equals("willow")
            || s.equals("maple")
            || s.equals("yew")
            || s.equals("magic")
            || s.equals("spirit tree")
            || s.equals("calquat")
            || s.equals("apple tree")
            || s.equals("banana tree")
            || s.equals("orange tree")
            || s.equals("curry tree")
            || s.equals("pineapple plant")
            || s.equals("papaya tree")
            || s.equals("palm tree")
            || s.contains("tree stump")
            || s.contains("oak tree")
            || s.contains("willow tree")
            || s.contains("maple tree")
            || s.contains("yew tree")
            || s.contains("magic tree");
    }

    private static void dumpOne(PrintWriter txt, PrintWriter jsonl, PrintWriter csv, int id, LocType loc) {
        int[] arr = overrides(loc);
        VarbitType vb = getVarbit(loc.varbit);

        txt.println("================================================================================");
        txt.println("OBJECT " + id);
        txt.println("name=" + safe(loc.name));
        txt.println("examine=" + safe(loc.examine));
        txt.println("sizeX=" + loc.sizeX);
        txt.println("sizeZ=" + loc.sizeZ);
        txt.println("options=" + stringArray(loc.options));
        txt.println("varbit=" + loc.varbit);
        txt.println("varp=" + loc.varp);

        if (vb != null) {
            txt.println("varbit.varp=" + vb.varp);
            txt.println("varbit.lsb=" + vb.lsb);
            txt.println("varbit.msb=" + vb.msb);
            txt.println("varbit.width=" + Math.max(0, vb.msb - vb.lsb));
        } else {
            txt.println("varbit.definition=null");
        }

        txt.println("overrideTypeIDs=" + Arrays.toString(arr));

        for (int i = 0; i < arr.length; i++) {
            int childId = arr[i];
            if (childId < 0 || childId >= LocType.count) {
                txt.println("child[" + i + "]=" + childId);
                continue;
            }

            try {
                LocType child = LocType.get(childId);
                txt.println("child[" + i + "]=" + childId
                        + " name=" + safe(child.name)
                        + " examine=" + safe(child.examine)
                        + " options=" + stringArray(child.options)
                        + " varbit=" + child.varbit
                        + " varp=" + child.varp
                        + " overrideTypeIDs=" + Arrays.toString(overrides(child)));
            } catch (Throwable t) {
                txt.println("child[" + i + "]=" + childId + " lookup_failed=" + t.getClass().getSimpleName());
            }
        }

        txt.println();

        jsonl.println(toJson(id, loc));
        csv.println(
            csvCell(String.valueOf(id)) + "," +
            csvCell(safe(loc.name)) + "," +
            csvCell(stringArray(loc.options)) + "," +
            csvCell(String.valueOf(loc.varbit)) + "," +
            csvCell(String.valueOf(loc.varp)) + "," +
            csvCell(vb == null ? "" : String.valueOf(vb.varp)) + "," +
            csvCell(vb == null ? "" : String.valueOf(vb.lsb)) + "," +
            csvCell(vb == null ? "" : String.valueOf(vb.msb)) + "," +
            csvCell(vb == null ? "" : String.valueOf(Math.max(0, vb.msb - vb.lsb))) + "," +
            csvCell(childrenSummary(loc))
        );
    }

    private static VarbitType getVarbit(int id) {
        try {
            if (id >= 0 && VarbitType.instances != null && id < VarbitType.instances.length) {
                return VarbitType.instances[id];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String childrenSummary(LocType loc) {
        int[] arr = overrides(loc);
        if (arr.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            int childId = arr[i];
            sb.append(i).append(":").append(childId);
            if (childId >= 0 && childId < LocType.count) {
                try {
                    LocType child = LocType.get(childId);
                    sb.append(" ").append(safe(child.name));
                } catch (Throwable ignored) {
                }
            }
        }
        return sb.toString();
    }

    private static String toJson(int id, LocType loc) {
        int[] arr = overrides(loc);
        VarbitType vb = getVarbit(loc.varbit);
        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("\"object_id\":").append(id);
        sb.append(",\"name\":\"").append(json(safe(loc.name))).append("\"");
        sb.append(",\"examine\":\"").append(json(safe(loc.examine))).append("\"");
        sb.append(",\"sizeX\":").append(loc.sizeX);
        sb.append(",\"sizeZ\":").append(loc.sizeZ);
        sb.append(",\"varbit\":").append(loc.varbit);
        sb.append(",\"varp\":").append(loc.varp);

        if (vb != null) {
            sb.append(",\"varbit_varp\":").append(vb.varp);
            sb.append(",\"lsb\":").append(vb.lsb);
            sb.append(",\"msb\":").append(vb.msb);
            sb.append(",\"width\":").append(Math.max(0, vb.msb - vb.lsb));
        }

        sb.append(",\"options\":");
        appendStringArray(sb, loc.options);

        sb.append(",\"overrideTypeIDs\":");
        appendIntArray(sb, arr);

        sb.append(",\"children\":[");
        for (int i = 0; i < arr.length; i++) {
            int childId = arr[i];
            if (i > 0) {
                sb.append(",");
            }

            sb.append("{\"index\":").append(i).append(",\"object_id\":").append(childId);
            if (childId >= 0 && childId < LocType.count) {
                try {
                    LocType child = LocType.get(childId);
                    sb.append(",\"name\":\"").append(json(safe(child.name))).append("\"");
                    sb.append(",\"examine\":\"").append(json(safe(child.examine))).append("\"");
                    sb.append(",\"options\":");
                    appendStringArray(sb, child.options);
                    sb.append(",\"varbit\":").append(child.varbit);
                    sb.append(",\"varp\":").append(child.varp);
                    sb.append(",\"overrideTypeIDs\":");
                    appendIntArray(sb, overrides(child));
                } catch (Throwable ignored) {
                    sb.append(",\"lookup_failed\":true");
                }
            }
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static void appendStringArray(StringBuilder sb, String[] arr) {
        sb.append("[");
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                if (arr[i] == null) {
                    sb.append("null");
                } else {
                    sb.append("\"").append(json(arr[i])).append("\"");
                }
            }
        }
        sb.append("]");
    }

    private static void appendIntArray(StringBuilder sb, int[] arr) {
        sb.append("[");
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(arr[i]);
            }
        }
        sb.append("]");
    }

    private static String stringArray(String[] arr) {
        return arr == null ? "null" : Arrays.toString(arr);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String json(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String csvCell(String value) {
        value = safe(value);
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        value = value.replace("\"", "\"\"");
        if (quote) {
            return "\"" + value + "\"";
        }
        return value;
    }
}
