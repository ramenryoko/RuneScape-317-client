import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;

public final class CompostBinVarpDumper {
    private static boolean dumped = false;

    private CompostBinVarpDumper() {
    }

    public static void dump(Game game) {
        if (dumped) {
            return;
        }
        dumped = true;

        PrintWriter txt = null;
        PrintWriter jsonl = null;

        try {
            if (game != null) {
                LocType.game = game;
            }

            txt = new PrintWriter(new FileWriter("compost_bin_varp_dump.txt"));
            jsonl = new PrintWriter(new FileWriter("compost_bin_varp_dump.jsonl"));

            txt.println("Compost bin varp/varbit dump");
            txt.println("client=RuneScape-317-client");
            txt.println("loc_count=" + LocType.count);
            txt.println("varbit_count=" + (VarbitType.instances == null ? -1 : VarbitType.instances.length));
            txt.println();

            int matches = 0;

            for (int id = 0; id < LocType.count; id++) {
                LocType loc;
                try {
                    loc = LocType.get(id);
                } catch (Throwable t) {
                    continue;
                }

                try {
                    if (!matchesCompostRelated(loc)) {
                        continue;
                    }

                    matches++;
                    dumpOne(txt, jsonl, id, loc);
                } catch (Throwable t) {
                    txt.println("================================================================================");
                    txt.println("OBJECT " + id + " dump_failed=" + t.getClass().getName() + ": " + t.getMessage());
                    txt.println();
                }
            }

            txt.println();
            txt.println("matches=" + matches);

            System.out.println("[CompostBinVarpDumper] Wrote compost_bin_varp_dump.txt and compost_bin_varp_dump.jsonl. matches=" + matches);
        } catch (Throwable t) {
            System.out.println("[CompostBinVarpDumper] Failed:");
            t.printStackTrace();
        } finally {
            try {
                if (txt != null) {
                    txt.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (jsonl != null) {
                    jsonl.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean matchesCompostRelated(LocType loc) {
        if (loc == null) {
            return false;
        }

        if (containsCompost(loc.name) || containsCompost(loc.examine)) {
            return true;
        }

        String[] options = loc.options;
        if (options != null) {
            for (int i = 0; i < options.length; i++) {
                if (containsCompost(options[i])) {
                    return true;
                }
            }
        }

        int[] children = loc.overrideTypeIDs;
        if (children == null || children.length == 0) {
            return false;
        }

        for (int i = 0; i < children.length; i++) {
            int childId = children[i];
            if (childId < 0 || childId >= LocType.count) {
                continue;
            }

            try {
                LocType child = LocType.get(childId);
                if (child == null) {
                    continue;
                }

                if (containsCompost(child.name) || containsCompost(child.examine)) {
                    return true;
                }

                String[] childOptions = child.options;
                if (childOptions != null) {
                    for (int j = 0; j < childOptions.length; j++) {
                        if (containsCompost(childOptions[j])) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static boolean containsCompost(String value) {
        return value != null && value.toLowerCase().contains("compost");
    }

    private static void dumpOne(PrintWriter txt, PrintWriter jsonl, int id, LocType loc) {
        txt.println("================================================================================");
        txt.println("OBJECT " + id);
        txt.println("name=" + safe(loc.name));
        txt.println("examine=" + safe(loc.examine));
        txt.println("sizeX=" + loc.sizeX);
        txt.println("sizeZ=" + loc.sizeZ);
        txt.println("options=" + arrayToString(loc.options));
        txt.println("varbit=" + loc.varbit);
        txt.println("varp=" + loc.varp);

        if (loc.varbit >= 0 && VarbitType.instances != null && loc.varbit < VarbitType.instances.length) {
            VarbitType vb = VarbitType.instances[loc.varbit];
            if (vb != null) {
                txt.println("varbit.varp=" + vb.varp);
                txt.println("varbit.lsb=" + vb.lsb);
                txt.println("varbit.msb=" + vb.msb);
                txt.println("varbit.width=" + Math.max(0, vb.msb - vb.lsb));
            } else {
                txt.println("varbit.definition=null");
            }
        }

        int[] children = loc.overrideTypeIDs;
        txt.println("overrideTypeIDs=" + Arrays.toString(children));

        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                int childId = children[i];

                if (childId < 0 || childId >= LocType.count) {
                    txt.println("child[" + i + "]=" + childId);
                    continue;
                }

                try {
                    LocType child = LocType.get(childId);
                    if (child == null) {
                        txt.println("child[" + i + "]=" + childId + " null");
                        continue;
                    }

                    txt.println("child[" + i + "]=" + childId
                            + " name=" + safe(child.name)
                            + " examine=" + safe(child.examine)
                            + " options=" + arrayToString(child.options)
                            + " varbit=" + child.varbit
                            + " varp=" + child.varp
                            + " overrideTypeIDs=" + Arrays.toString(child.overrideTypeIDs));
                } catch (Throwable t) {
                    txt.println("child[" + i + "]=" + childId + " lookup_failed=" + t.getClass().getSimpleName());
                }
            }
        }

        txt.println();
        jsonl.println(toJson(id, loc));
    }

    private static String toJson(int id, LocType loc) {
        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("\"object_id\":").append(id);
        sb.append(",\"name\":\"").append(json(safe(loc.name))).append("\"");
        sb.append(",\"examine\":\"").append(json(safe(loc.examine))).append("\"");
        sb.append(",\"sizeX\":").append(loc.sizeX);
        sb.append(",\"sizeZ\":").append(loc.sizeZ);
        sb.append(",\"varbit\":").append(loc.varbit);
        sb.append(",\"varp\":").append(loc.varp);

        if (loc.varbit >= 0 && VarbitType.instances != null && loc.varbit < VarbitType.instances.length) {
            VarbitType vb = VarbitType.instances[loc.varbit];
            if (vb != null) {
                sb.append(",\"varbit_varp\":").append(vb.varp);
                sb.append(",\"lsb\":").append(vb.lsb);
                sb.append(",\"msb\":").append(vb.msb);
            }
        }

        sb.append(",\"options\":");
        appendStringArray(sb, loc.options);

        sb.append(",\"overrideTypeIDs\":");
        appendIntArray(sb, loc.overrideTypeIDs);

        sb.append(",\"children\":[");
        int[] children = loc.overrideTypeIDs;
        if (children != null) {
            boolean first = true;

            for (int i = 0; i < children.length; i++) {
                int childId = children[i];

                if (!first) {
                    sb.append(",");
                }
                first = false;

                sb.append("{\"index\":").append(i).append(",\"object_id\":").append(childId);

                if (childId >= 0 && childId < LocType.count) {
                    try {
                        LocType child = LocType.get(childId);
                        if (child != null) {
                            sb.append(",\"name\":\"").append(json(safe(child.name))).append("\"");
                            sb.append(",\"examine\":\"").append(json(safe(child.examine))).append("\"");
                            sb.append(",\"options\":");
                            appendStringArray(sb, child.options);
                            sb.append(",\"varbit\":").append(child.varbit);
                            sb.append(",\"varp\":").append(child.varp);
                            sb.append(",\"overrideTypeIDs\":");
                            appendIntArray(sb, child.overrideTypeIDs);
                        }
                    } catch (Throwable ignored) {
                        sb.append(",\"lookup_failed\":true");
                    }
                }

                sb.append("}");
            }
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
                sb.append("\"").append(json(safe(arr[i]))).append("\"");
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

    private static String arrayToString(String[] arr) {
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
}
