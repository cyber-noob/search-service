
package petsy.inc.search;


import io.helidon.common.context.Contexts;
import io.helidon.dbclient.DbClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import petsy.inc.search.services.SearchIndexerService;
import petsy.inc.search.services.SearchService;


/**
 * The application main class.
 */
public class Main {


    /**
     * Cannot be instantiated.
     */
    private Main() {
    }


    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(String[] args) throws Exception {
        
        // load logging configuration
        LogConfig.configureRuntime();

        // initialize global config from default configuration
        Config config = Config.create();
        Config.global(config);

        DbClient dbClient = DbClient.create(config.get("db"));
        Contexts.globalContext().register(dbClient);

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(Main::routing)
                .build()
                .start();

        SearchIndexerService searchIndexerService = new SearchIndexerService();
        searchIndexerService.schedule();
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/simple-greet");

        searchIndexerService.indexItems();
    }


    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        routing
                .register("/search", new SearchService())
                .get("/simple-greet", (req, res) -> res.send("Hello World!"));
    }
}