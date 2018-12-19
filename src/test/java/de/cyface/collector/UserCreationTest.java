package de.cyface.collector;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.verticle.CollectorApiVerticle;
import de.cyface.collector.verticle.ManagementApiVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

@RunWith(VertxUnitRunner.class)
public final class UserCreationTest {

    private static MongoTest mongoTest;

    private int port;

    private WebClient client;

    private Vertx vertx;
    
    private JsonObject mongoDbConfiguration;

    /**
     * Boots the Mongo database before this test starts.
     * 
     * @throws IOException If no socket was available for the Mongo database.
     */
    @BeforeClass
    public static void setUpMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        ServerSocket socket = new ServerSocket(0);
        int mongoPort = socket.getLocalPort();
        socket.close();
        mongoTest.setUpMongoDatabase(mongoPort);
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterClass
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    @Before
    public void setUp(final TestContext context) throws IOException {
        vertx = Vertx.vertx();

        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        mongoDbConfiguration = new JsonObject()
                .put("connection_string", "mongodb://localhost:" + mongoTest.getMongoPort()).put("db_name", "cyface");

        JsonObject config = new JsonObject().put(Parameter.MANAGEMENT_HTTP_PORT.key(), port)
                .put(Parameter.MONGO_USER_DB.key(), mongoDbConfiguration);
        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        vertx.deployVerticle(ManagementApiVerticle.class.getName(), options, context.asyncAssertSuccess());

        client = WebClient.create(vertx);
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void testCreateUser_HappyPath(final TestContext context) {
        final Async async = context.async();

        client.post(port, "localhost", "/user").sendJsonObject(
                new JsonObject().put("username", "test-user").put("password", "test-password"), result -> {
                    if (result.succeeded()) {
                        async.complete();
                    } else {
                        async.resolve(Future.failedFuture(result.cause()));
                    }
                });

        async.await(3_000L);
        
        final MongoClient mongoClient = Utils.createSharedMongoClient(vertx, mongoDbConfiguration);
        
        final Async mongoQueryCountAsync = context.async();
        mongoClient.count("user", new JsonObject(), result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result(), 1L);
            mongoQueryCountAsync.complete();
        });
        mongoQueryCountAsync.await(3_000L);
        
        final Async mongoQueryAsync = context.async();
        mongoClient.findOne("user", new JsonObject(), null, result -> {
           context.assertTrue(result.succeeded());
           context.assertEquals(result.result().getString("username"), "test-user");
           mongoQueryAsync.complete();
        });
        mongoQueryAsync.await(3_000L);
    }
}
