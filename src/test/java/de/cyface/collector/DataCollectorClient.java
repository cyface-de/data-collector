package de.cyface.collector;

import java.io.IOException;
import java.net.ServerSocket;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * A client providing capabilities for tests to communicate with a Cyface Data Collector server.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class DataCollectorClient {

    /**
     * The port the server is reachable at.
     */
    private int port;

    /**
     * @return The port the server is reachable at.
     */
    int getPort() {
        return port;
    }

    /**
     * Starts a test Cyface Data Collector and creates a Vert.x <code>WebClient</code> usable to access a Cyface Data
     * Collector.
     * 
     * @param vertx The <code>Vertx</code> instance to start and access the server.
     * @param ctx The <code>TestContext</code> to create a new Server and <code>WebClient</code>.
     * @param mongoPort The port to run the test Mongo database under.
     * @return A completely configured <code>WebClient</code> capable of accessing the started Cyface Data Collector.
     * @throws IOException If the server port could not be opened.
     */
    WebClient createWebClient(final Vertx vertx, final TestContext ctx, final int mongoPort) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        JsonObject mongoDbConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:" + mongoPort).put("db_name", "cyface");

        JsonObject config = new JsonObject().put(Parameter.MONGO_DATA_DB.key(), mongoDbConfig)
                .put(Parameter.MONGO_USER_DB.key(), mongoDbConfig).put(Parameter.HTTP_PORT.key(), port)
                .put(Parameter.JWT_PRIVATE_KEY_FILE_PATH.key(), this.getClass().getResource("/private_key.pem").getFile())
                .put(Parameter.JWT_PUBLIC_KEY_FILE_PATH.key(), this.getClass().getResource("/public.pem").getFile());
        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        vertx.deployVerticle(MainVerticle.class.getName(), options, ctx.asyncAssertSuccess());

        String truststorePath = TestUtils.class.getResource("/localhost.jks").getFile();

        return WebClient.create(vertx, new WebClientOptions().setSsl(true)
                .setTrustStoreOptions(new JksOptions().setPath(truststorePath).setPassword("secret")));
    }
}
