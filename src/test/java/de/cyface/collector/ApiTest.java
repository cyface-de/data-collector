package de.cyface.collector;

import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.jaxrs.CheckingWebTarget;
import guru.nidi.ramltester.junit.RamlMatchers;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ApiTest {

    private static final RamlDefinition api = RamlLoaders.fromClasspath().load("/webroot/api/collector.raml")
            .assumingBaseUri("http://localhost:8080");

    private ResteasyClient client = new ResteasyClientBuilder().build();
    private CheckingWebTarget checking;
    private Vertx vertx;
    private int port;

    @Before
    public void bootApp(final TestContext ctx) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();
        
        JsonObject config = new JsonObject().put(Parameter.HTTP_PORT.key(), port);
        DeploymentOptions options = new DeploymentOptions().setConfig(config);

        vertx = Vertx.vertx();
        vertx.deployVerticle(MainVerticle.class, options, ctx.asyncAssertSuccess());
    }

    @After
    public void shutdown(final TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void testMeasurementsEndpoint() throws URISyntaxException {
        checking = api.createWebTarget(client.target("https://localhost:"+port));

        File file = new File(this.getClass().getResource("/iphone-neu.ccyf").toURI());

        MultipartFormDataOutput upload = new MultipartFormDataOutput();
        upload.addFormData("fileToUpload", file, MediaType.APPLICATION_OCTET_STREAM_TYPE);
        Entity<MultipartFormDataOutput> multiPartEntity = Entity.entity(upload, MediaType.MULTIPART_FORM_DATA_TYPE);

        checking.path("/measurements").request().post(multiPartEntity);
        assertThat(checking.getLastReport(), RamlMatchers.hasNoViolations());
    }

}
