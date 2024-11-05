package petsy.inc.search.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.scheduling.FixedRate;
import io.helidon.scheduling.Scheduling;
import jakarta.json.JsonObject;
import org.json.JSONObject;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.api.exceptions.ObjectAlreadyExists;
import org.typesense.model.*;
import petsy.inc.search.clients.TypeSenseClient;
import petsy.inc.search.utils.JsonConverter;
import petsy.inc.search.utils.ResponseContractMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SearchIndexerService {

    Client typeSenseClient = TypeSenseClient.getTypeSenseClient();

    private final DbClient dbClient;

    private final ResponseContractMapper responseContractMapper = new ResponseContractMapper();

    public SearchIndexerService() {
        Config config = Config.global().get("db");
        this.dbClient = Contexts.globalContext()
                .get(DbClient.class)
                .orElseGet(() -> DbClient.create(config));
    }

    public void schedule() {

        Scheduling.fixedRate()
                .initialDelay(1)
                .delay(1)
                .delayType(FixedRate.DelayType.SINCE_PREVIOUS_END)
                .timeUnit(TimeUnit.MINUTES)
                .task(invocation -> indexItems())
                .build();

        System.out.println("Scheduler run successfully...!");

    }

    private void createCollection() {

        List<String> embedFrom = List.of("title", "description");

        CollectionSchema productsCollection = new CollectionSchema().name("products")
                .defaultSortingField("created_on")
                .enableNestedFields(true)
                .addFieldsItem(new Field().name("title").type(FieldTypes.STRING).sort(true))
                .addFieldsItem(new Field().name("description").type(FieldTypes.STRING))
                .addFieldsItem(new Field().name("price").type(FieldTypes.INT32).sort(true).facet(true))
                .addFieldsItem(new Field().name("longDescription.gender").type(FieldTypes.STRING).facet(true))
                .addFieldsItem(new Field().name("longDescription.breed_type").type(FieldTypes.STRING).facet(true))
                .addFieldsItem(new Field().name("longDescription.age").type(FieldTypes.INT32).facet(true))
                .addFieldsItem(new Field().name("longDescription.color").type(FieldTypes.STRING).facet(true))
                .addFieldsItem(new Field().name("categoryPool").type(FieldTypes.OBJECT_ARRAY).facet(true))
                .addFieldsItem(new Field().name("created_on").type(FieldTypes.INT32).sort(true))
                .addFieldsItem(
                        new Field().name("embedding")
                                .type("float[]")
                                .embed(
                                        new FieldEmbed()
                                                .from(embedFrom)
                                                .modelConfig(
                                                        new FieldEmbedModelConfig()
                                                                .modelName("ts/paraphrase-multilingual-mpnet-base-v2")
                                                )
                                )
                );
        try {
            CollectionResponse collectionResponse = typeSenseClient.collections().create(productsCollection);
            System.out.println("Collection creation response: " + collectionResponse);
        }

        catch (ObjectAlreadyExists o) {
            System.out.println("Collection already exists");
        }

        catch (Exception e) {
            System.out.println("Collection creation exception: " + e);
        }
    }

    private List<JSONObject> getProducts() throws URISyntaxException, IOException {

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-d'T'HH:mm:ss");
        ObjectMapper objectMapper = new ObjectMapper();

        List<JsonObject> data = dbClient.execute().namedQuery("fetch-products")
                .map(row -> row.as(JsonObject.class))
                .toList();

        Map<String, List<JsonObject>> groups = data.stream()
                .collect(Collectors.groupingBy(row -> row.getString("pslug")));

        List<JSONObject> result = new ArrayList<>();
        groups.values()
                .forEach(group -> {
                    try {
                        Map<String, Object> product = responseContractMapper.mapDataToResponse("/responsecontracts/products.json", group);
                        product.put("currencySymbol", "₹");
                        product.put("isWishlisted", !product.get("isWishlisted").toString().equals("\"NA\""));
                        product.put("active", true);
                        product.put("hasStock", Integer.parseInt(product.get("count").toString()) > 0);
                        product.remove("count");

                        String createdOn = product.get("created_on").toString();
                        createdOn = createdOn.substring(1, createdOn.length() - 1);
                        product.put("created_on", format.parse(createdOn).getTime());
                        product.put("id", product.get("uuid"));

//                        System.out.println("json of map: " + JsonConverter.convertMapToJson(product) + "\n\n");
                        result.add(JsonConverter.convertMapToJson(product));
                    } catch (URISyntaxException | IOException | ParseException e) {
                        throw new RuntimeException(e);
                    }
                });

        return result;
    }

    private void indexItems() throws Exception {
        createCollection();
        String productsData = Files.readString(Path.of(JsonConverter.jsonToJsonl(getProducts()).getPath()));
        String response = typeSenseClient.collections("products")
                .documents()
                .import_(
                        productsData,
                        new ImportDocumentsParameters()
                                .action(ImportDocumentsParameters.ActionEnum.UPSERT)
                                .batchSize(500)
                );

        System.out.println("\n\nItems indexed: " + response);

        SearchParameters searchParameters = new SearchParameters().q("*")
                .queryBy("title");

        Integer total = typeSenseClient.collections("products")
                .documents()
                .search(searchParameters)
                .getOutOf();

        System.out.println("Total docs indexed: " + total);
    }

}
