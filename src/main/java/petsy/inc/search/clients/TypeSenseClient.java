package petsy.inc.search.clients;

import io.helidon.config.Config;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TypeSenseClient {

    private static Client typeSenseClient = null;

    public TypeSenseClient() {

        List<Node> nodes = new ArrayList<>();
        Config config = Config.global();
        nodes.add(
                new Node(
                        config.get("typesense").get("protocol").asString().get(),       // For Typesense Cloud use https
                        config.get("typesense").get("host").asString().get(),  // For Typesense Cloud use xxx.a1.typesense.net
                        config.get("typesense").get("port").asString().get()        // For Typesense Cloud use 443
                )
        );

        System.out.println("typesense host: " + config.get("typesense")
                .get("host")
                .asString()
                .get()
        );

        System.out.println("typesense key: " + config.get("typesense")
                .get("apiKey")
                .asString()
                .get()
        );
        Configuration configuration = new Configuration(
                nodes,
                Duration.ofMinutes(3),
                config.get("typesense")
                        .get("apiKey")
                        .asString()
                        .get()
        );

        typeSenseClient =  new Client(configuration);
    }

    public static Client getTypeSenseClient() {

        if (typeSenseClient == null)
            new TypeSenseClient();

        return typeSenseClient;
    }
}
