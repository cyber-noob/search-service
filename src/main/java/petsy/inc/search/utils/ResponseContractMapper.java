package petsy.inc.search.utils;

import jakarta.json.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ResponseContractMapper {

    private Map<String, Object> mapper(JSONObject contract, List<JsonObject> data) {

        JSONObject result = new JSONObject();

        for (String key : contract.keySet()) {

//            System.out.println("key: " + key);

            if (contract.get(key) instanceof String) {
                result.put(
                        key, data.getFirst()
                                .get(contract.getString(key))
                );
            }

            if (contract.get(key) instanceof JSONObject) {

                if (!contract.getJSONObject(key).has("type")) {

                    JSONObject intermittent = new JSONObject();

                    for (String innerKey : contract.getJSONObject(key).keySet()) {

                        if (contract.getJSONObject(key).get(innerKey) instanceof String) {
                            intermittent.put(
                                    innerKey, data.getFirst()
                                            .get(contract.getJSONObject(key).get(innerKey))
                            );

                            result.put(key, intermittent);
                        }

                        else if (contract.getJSONObject(key).get(innerKey) instanceof JSONObject)
                            result.put(key, mapper((JSONObject) contract.getJSONObject(key).get(innerKey), data));
                    }
                }

                else {

                    String type = contract.getJSONObject(key)
                            .getString("type");

                    if (type.equals("object")) {
                        JSONObject intermittentObject = new JSONObject();

                        JSONObject object = contract.getJSONObject(key)
                                .getJSONObject("contract");

                        object.keySet()
                                .forEach(key1 -> intermittentObject.put(key1, data.getFirst().get(object.get(key1))));
                        result.put(key, intermittentObject);
                    }

                    if (type.equals("array")) {
                        String mode = contract.getJSONObject(key)
                                .getString("mode");

                        JSONArray intermittentArray = new JSONArray();

                        if (mode.equals("unique")) {
                            JSONArray array = contract.getJSONObject(key)
                                    .getJSONArray("contract");

                            for (int i = 0; i < array.length(); i++) {
                                JSONObject intermittentObject = new JSONObject();
                                JSONObject object = array.getJSONObject(i);

                                object.keySet()
                                        .forEach(key1 -> intermittentObject.put(key1, data.getFirst().get(object.get(key1))));

                                intermittentArray.put(intermittentObject);
                            }

                            result.put(key, intermittentArray);
                        }

                        if (mode.equals("repeat")) {
                            JSONArray array = contract.getJSONObject(key)
                                    .getJSONArray("contract");

                            data.forEach(datum -> {
                                JSONObject intermittentObject = new JSONObject();
                                array.getJSONObject(0)
                                        .keySet()
                                        .forEach(key1 -> intermittentObject.put(key1, datum.get(array.getJSONObject(0).get(key1))));

                                intermittentArray.put(intermittentObject);
                            });

                            result.put(key, intermittentArray);
                        }
                    }
                }
            }
        }

        return result.toMap();
    }

    public Map<String, Object> mapDataToResponse(String contractPath, List<JsonObject> data) throws URISyntaxException, IOException {

        String json = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                ResponseContractMapper.class.getResourceAsStream(contractPath)
                        )
                )
        ).lines()
                .collect(Collectors.joining("\n"));

        JSONObject contract = new JSONObject(json);

//        System.out.println("contract: " + contract.toString(4));

        return mapper(contract, data);
    }
}
