import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;

public final class ItemNameDumper {
    private static boolean dumped = false;

    private ItemNameDumper() {
    }

    public static void dump() {
        if (dumped) {
            return;
        }
        dumped = true;

        PrintWriter txt = null;
        PrintWriter jsonl = null;
        PrintWriter csv = null;

        try {
            txt = new PrintWriter(new FileWriter("item_name_dump.txt"));
            jsonl = new PrintWriter(new FileWriter("item_name_dump.jsonl"));
            csv = new PrintWriter(new FileWriter("item_name_dump.csv"));

            txt.println("Item name / description dump");
            txt.println("client=RuneScape-317-client");
            txt.println("obj_count=" + ObjType.count);
            txt.println();

            csv.println("id,name,examine,members,stackable,cost,modelID,linkedID,certificateID,ground_options,inventory_options");

            int dumpedCount = 0;

            for (int id = 0; id < ObjType.count; id++) {
                ObjType obj;

                try {
                    obj = ObjType.get(id);
                } catch (Throwable t) {
                    txt.println("================================================================================");
                    txt.println("ITEM " + id);
                    txt.println("lookup_failed=" + t.getClass().getName() + ": " + safe(t.getMessage()));
                    txt.println();
                    continue;
                }

                if (obj == null) {
                    continue;
                }

                dumpedCount++;
                dumpOne(txt, jsonl, csv, id, obj);
            }

            txt.println();
            txt.println("dumped=" + dumpedCount);

            System.out.println("[ItemNameDumper] Wrote item_name_dump.txt, item_name_dump.jsonl, and item_name_dump.csv. dumped=" + dumpedCount);
        } catch (Throwable t) {
            System.out.println("[ItemNameDumper] Failed:");
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
            try {
                if (csv != null) {
                    csv.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dumpOne(PrintWriter txt, PrintWriter jsonl, PrintWriter csv, int id, ObjType obj) {
        String name = safe(obj.name);
        String examine = safe(obj.examine);

        txt.println("================================================================================");
        txt.println("ITEM " + id);
        txt.println("name=" + name);
        txt.println("examine=" + examine);
        txt.println("members=" + obj.members);
        txt.println("stackable=" + obj.stackable);
        txt.println("cost=" + obj.cost);
        txt.println("modelID=" + obj.modelID);
        txt.println("linkedID=" + obj.linkedID);
        txt.println("certificateID=" + obj.certificateID);
        txt.println("ground_options=" + arrayToString(obj.options));
        txt.println("inventory_options=" + arrayToString(obj.inventoryOptions));
        txt.println("team=" + obj.team);
        txt.println();

        jsonl.println(toJson(id, obj));

        csv.println(
            csvCell(String.valueOf(id)) + "," +
            csvCell(name) + "," +
            csvCell(examine) + "," +
            csvCell(String.valueOf(obj.members)) + "," +
            csvCell(String.valueOf(obj.stackable)) + "," +
            csvCell(String.valueOf(obj.cost)) + "," +
            csvCell(String.valueOf(obj.modelID)) + "," +
            csvCell(String.valueOf(obj.linkedID)) + "," +
            csvCell(String.valueOf(obj.certificateID)) + "," +
            csvCell(arrayToString(obj.options)) + "," +
            csvCell(arrayToString(obj.inventoryOptions))
        );
    }

    private static String toJson(int id, ObjType obj) {
        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("\"id\":").append(id);
        sb.append(",\"name\":\"").append(json(safe(obj.name))).append("\"");
        sb.append(",\"examine\":\"").append(json(safe(obj.examine))).append("\"");
        sb.append(",\"members\":").append(obj.members);
        sb.append(",\"stackable\":").append(obj.stackable);
        sb.append(",\"cost\":").append(obj.cost);
        sb.append(",\"modelID\":").append(obj.modelID);
        sb.append(",\"linkedID\":").append(obj.linkedID);
        sb.append(",\"certificateID\":").append(obj.certificateID);
        sb.append(",\"team\":").append(obj.team);
        sb.append(",\"ground_options\":");
        appendStringArray(sb, obj.options);
        sb.append(",\"inventory_options\":");
        appendStringArray(sb, obj.inventoryOptions);
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

    private static String arrayToString(String[] arr) {
        return arr == null ? "" : Arrays.toString(arr);
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
