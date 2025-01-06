package petsy.inc.search.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import org.typesense.api.Client;
import org.typesense.model.CollectionResponse;
import org.typesense.model.SearchParameters;
import org.typesense.model.SearchResult;
import petsy.inc.search.clients.TypeSenseClient;

import java.util.List;
import java.util.Map;

public class SearchService implements HttpService {

    Client typeSenseClient = TypeSenseClient.getTypeSenseClient();
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void routing(HttpRules httpRules) {
        httpRules
                .get("/products", this::Search)
                .post("/delete", this::deleteCollection);
    }

    public void Search(ServerRequest request, ServerResponse response) throws Exception {

        Map<String, List<String>> query = request.query().toMap();
        String type = query.getOrDefault("type", List.of("")).getFirst();

        List<String> tempQuery = query.getOrDefault("query", List.of(""));
        int queryTokens = tempQuery.size();
        String q = String.join(" ", tempQuery);

        String filter = query.getOrDefault("filter", List.of("")).getFirst();
        String groups = query.getOrDefault("group", List.of("")).getFirst();
        String sort = query.getOrDefault("sort", List.of("")).getFirst();

        switch (type) {
            case null -> response.status(400);

            case "simple" -> {
                SearchParameters searchParameters = new SearchParameters()
                        .q(q)
                        .queryBy("product.general_info.title")
                        .filterBy(filter)
                        .filterBy(groups)
                        .sortBy("product.general_info.created_on:desc")
                        .excludeFields("embedding");

                SearchResult searchResult = typeSenseClient.collections("products")
                        .documents()
                        .search(searchParameters);
                Map<String, Object> props = objectMapper.convertValue(searchResult, new TypeReference<>() {});
                response.send(props);
            }

            case "semantic" -> {
                String s = (!sort.isEmpty()) ? sort : "_text_match:desc";
                SearchParameters searchParameters = new SearchParameters()
                        .q(q)
                        .queryBy("embedding,product.general_info.title")
//                        .vectorQuery("embedding:([], alpha: 0.2)")
                        .filterBy(filter)
                        .facetBy(groups)
                        .sortBy(s)
                        .excludeFields("embedding");

                SearchResult searchResult = typeSenseClient.collections("products")
                        .documents()
                        .search(searchParameters);

                int matchedTokens = 0;
                Map<String, Object> props = objectMapper.convertValue(searchResult, new TypeReference<>() {});
//                JSONObject json = JsonConverter.convertMapToJson(props);
//                JSONArray hits = json.getJSONArray("hits");
//                for (Object object : hits) {
//                    JSONObject hit = (JSONObject) object;
//                    if (!hit.getJSONObject("highlight").isEmpty())
//                        matchedTokens = hit.getJSONObject("highlight")
//                                .getJSONObject("title")
//                                .getJSONArray("matched_tokens")
//                                .length();
//
//                    float vector_distance = 0;
//                    if (hit.get("vector_distance") != null)
//                        vector_distance = hit.getFloat("vector_distance");
//
//                    hit.put("score", ((double) queryTokens / matchedTokens * 0.6) + (vector_distance * 0.4));
//                }
//                json.put("hits", hits);

                response.send(props);
            }

            default -> response.send();
        }
    }

    public void deleteCollection(ServerRequest request, ServerResponse response) throws Exception {

        CollectionResponse collectionResponse = typeSenseClient.collections("products").delete();
        response.send(collectionResponse.toString());
    }
}
