package petsy.inc.search.utils;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonConverter {
    
    public static File jsonToJsonl(List<JSONObject> input) throws IOException {

        File fout = new File("output.jsonl");
        FileOutputStream fos = new FileOutputStream(fout);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

        for (JSONObject object : input) {
            bw.write(object.toString());
            bw.newLine();
        }

        bw.close();

        return fout;
    }

    public static JSONObject convertMapToJson(Map<String, Object> map) {

        JSONObject result = new JSONObject();

        if (map != null) {
            for (String key : map.keySet()) {

//                System.out.println("key: " + key);

                if (
                        map.get(key) instanceof JsonString ||
                                map.get(key) instanceof JsonNumber ||
                                map.get(key) instanceof JsonArray ||
                                map.get(key) instanceof Boolean ||
                                map.get(key) instanceof String ||
                                map.get(key) instanceof Number
                )
                    result.put(key, parseValue(map.get(key)));

                else if (map.get(key) instanceof List<?>) {
                    JSONArray array = new JSONArray();

                    ((List<Map<String, Object>>) map.get(key))
                            .forEach(item -> {
                                JSONObject object = new JSONObject();
                                item.keySet()
                                        .forEach(innerKey -> {
                                            if (
                                                    item.get(innerKey) instanceof JsonString ||
                                                            item.get(innerKey) instanceof JsonNumber ||
                                                            item.get(innerKey) instanceof JsonArray ||
                                                            item.get(innerKey) instanceof Boolean ||
                                                            item.get(innerKey) instanceof String ||
                                                            item.get(innerKey) instanceof Number
                                            )
                                                object.put(innerKey, parseValue(item.get(innerKey)));

                                            else
                                                object.put(innerKey, convertMapToJson((Map<String, Object>) item.get(innerKey)));

                                            array.put(object);
                                        });
                            });

                    result.put(key, array);
                } else
                    result.put(key, convertMapToJson((Map<String, Object>) map.get(key)));
            }
        }

        return result;
    }

    private static Object parseValue(Object object) {
        if (object instanceof Number || object instanceof JsonNumber) {
            try {
                return Integer.parseInt(object.toString());
            } catch (Exception e) {
                return Long.valueOf(object.toString());
            }
        }

        else if (object instanceof Boolean)
            return object;

        else {
            return String.valueOf(object).replaceAll("^\"|\"$", "");
        }
    }
}
