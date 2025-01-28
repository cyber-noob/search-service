package petsy.inc.search.services;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.scheduling.FixedRate;
import io.helidon.scheduling.Scheduling;
import org.json.JSONObject;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.api.exceptions.ObjectAlreadyExists;
import org.typesense.model.*;
import petsy.inc.search.clients.TypeSenseClient;
import petsy.inc.search.utils.JsonConverter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SearchIndexerService {

    Client typeSenseClient = TypeSenseClient.getTypeSenseClient();

    private final DbClient dbClient;

    public SearchIndexerService() {
        Config config = Config.global().get("db");
        this.dbClient = Contexts.globalContext()
                .get(DbClient.class)
                .orElseGet(() -> DbClient.create(config));
    }

    public void schedule() {

        Scheduling.fixedRate()
                .initialDelay(1)
                .delay(10)
                .delayType(FixedRate.DelayType.SINCE_PREVIOUS_END)
                .timeUnit(TimeUnit.SECONDS)
                .task(invocation -> indexItems())
                .build();

        System.out.println("Scheduler run successfully...!");

    }

    private void createCollection() {

        List<String> embedFrom = List.of("product.general_info.title", "product.general_info.description");

        CollectionSchema productsCollection = new CollectionSchema().name("products")
//                .defaultSortingField("product.general_info.created_on")
                .enableNestedFields(true)
                .addFieldsItem(new Field().name("id").type(FieldTypes.STRING))
                .addFieldsItem(new Field().name("category").type(FieldTypes.STRING))
                .addFieldsItem(new Field().name("family").type(FieldTypes.STRING))
                .addFieldsItem(new Field().name("product.general_info.title").type(FieldTypes.STRING).sort(true))
                .addFieldsItem(new Field().name("product.general_info.description").type(FieldTypes.STRING))
                .addFieldsItem(new Field().name("product.general_info.collection").type(FieldTypes.STRING).facet(true))
                .addFieldsItem(new Field().name("product.general_info.price").type(FieldTypes.INT32).sort(true).facet(true))
                .addFieldsItem(new Field().name("product.sex").type(FieldTypes.STRING).facet(true).optional(true))
                .addFieldsItem(new Field().name("product.breed").type(FieldTypes.STRING).facet(true).optional(true))
                .addFieldsItem(new Field().name("product.age_in_days").type(FieldTypes.INT32).facet(true).optional(true))
                .addFieldsItem(new Field().name("product.color").type(FieldTypes.STRING).facet(true).optional(true))
                .addFieldsItem(new Field().name("product.categoryPool").type(FieldTypes.OBJECT_ARRAY).facet(true))
//                .addFieldsItem(new Field().name("product.general_info.created_on").type(FieldTypes.STRING).sort(true))
                .addFieldsItem(
                        new Field().name("embedding")
                                .type("float[]")
                                .embed(
                                        new FieldEmbed()
                                                .from(embedFrom)
                                                .modelConfig(
                                                        new FieldEmbedModelConfig()
                                                                .modelName("ts/all-MiniLM-L12-v2")
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

        List<DbRow> data = dbClient.execute()
                .namedQuery("fetch-products")
                .toList();

        List<JSONObject> list = new ArrayList<>();
        data.forEach(row -> {
            JSONObject item = new JSONObject();
            item.put("id", row.column("idProduct").getString());
            item.put("category", Collections.valueOf(row.column("category").getString()).getSlug());
            item.put("family", row.column("family").getString());
            item.put("product", new JSONObject(row.column("details").getString()));
            list.add(item);
        });
        return list;
    }

    private void indexItems() throws Exception {
        createCollection();
        List<JSONObject> products = getProducts();
        String productsData = Files.readString(Path.of(JsonConverter.jsonToJsonl(products).getPath()));
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
                .queryBy("product.general_info.title");

        Integer total = typeSenseClient.collections("products")
                .documents()
                .search(searchParameters)
                .getOutOf();

        products.parallelStream()
                .forEach(product -> {
                    try {
                        dbClient.execute()
                                .createUpdate("UPDATE Product SET indexed = 1 WHERE idProduct = ?")
                                .addParam(product.getString("id"))
                                .execute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        List<DbRow> deletedItems = dbClient.execute()
                .namedQuery("fetch-deleted-products")
                .toList();

        AtomicLong deleted = new AtomicLong();
        deletedItems.parallelStream()
                        .forEach(deletedItem ->
                        {
                            try {
                                typeSenseClient.collections("products")
                                        .documents(deletedItem.column("idDeletedProducts").getString())
                                        .delete();
                                deleted.set(dbClient.execute()
                                        .createUpdate("UPDATE DeletedProducts SET deleted = 1 WHERE idDeletedProducts = ?")
                                        .addParam(deletedItem.column("idDeletedProducts").getString())
                                        .execute());
                            } catch (Exception e) {
                                System.out.println("Item not indexed here...");
                            }
                        });

        System.out.println("Total docs indexed: " + total);
        System.out.println("Items deleted: " + deleted);
    }

}
