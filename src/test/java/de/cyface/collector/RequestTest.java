/*
 * Copyright 2018 Cyface GmbH
 * This file is part of the Cyface Data Collector.
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

@RunWith(VertxUnitRunner.class)
public class RequestTest {

    private Vertx vertx;
    private int port;
    private final static Logger LOGGER = LoggerFactory.getLogger(RequestTest.class);

    @Before
    public void deployVerticle(TestContext testContext) throws IOException {
        vertx = Vertx.vertx();

        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));

        vertx.deployVerticle(MainVerticle.class.getName(), options, testContext.asyncAssertSuccess());
    }

    @After
    public void stopVertx(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testDefaultHandler(TestContext context) throws Throwable {
        // This test is asynchronous, so get an async handler to inform the test when we
        // are done.
        final Async async = context.async();

        // We create a HTTP client and query our application. When we get the response
        // we check it contains the 'Hello'
        // message. Then, we call the `complete` method on the async handler to declare
        // this async (and here the test) done.
        // Notice that the assertions are made on the 'context' object and are not Junit
        // assert. This ways it manage the
        // async aspect of the test the right way.
        WebClient client = WebClient.create(vertx);
        client.get(port, "localhost", "/").ssl(true).send(response -> {
            if (response.succeeded()) {
                String body = response.result().bodyAsString();
                context.assertTrue(body.contains("Cyface"));
            } else {
                context.fail();
            }
            async.complete();
        });

    }

    // This test will only work with vertx 3.6.0 as multipart file uploads are not
    // supported by the vertx client.
    @Test
    public void testPostFile(TestContext context) throws Exception {
        final Async async = context.async();

        final URL testFileResource = this.getClass().getResource("/test.bin");

        final String deviceIdentifier = UUID.randomUUID().toString();
        final String measurementIdentifier = String.valueOf(1L);

        final MultipartForm form = MultipartForm.create();
        form.attribute("deviceId", deviceIdentifier);
        form.attribute("measurementId", measurementIdentifier);
        form.attribute("deviceType", "HTC Desire");
        form.attribute("osVersion", "4.4.4");
        form.binaryFileUpload("fileToUpload",
                String.format(Locale.US, "%s_%s.cyf", deviceIdentifier, measurementIdentifier),
                testFileResource.getFile(), "application/octet-stream");

        final WebClient client = WebClient.create(vertx);
        final HttpRequest<Buffer> builder = client.post("somepath").ssl(true);
        builder.sendMultipartForm(form, ar -> {
            context.assertTrue(ar.succeeded());
            context.assertNull(ar.cause());
            async.complete();
        });

        async.await(3000L);

        /*
         * Async async = context.async();
         * final MultipartForm parts = MultipartForm.create();
         * final String deviceIdentifier = UUID.randomUUID().toString();
         * final String measurementIdentifier = String.valueOf(1L);
         * parts.attribute("deviceId", deviceIdentifier);
         * parts.attribute("measurementId", measurementIdentifier);
         * parts.attribute("deviceType", "HTC Desire");
         * parts.attribute("osVersion", "4.4.4");
         * parts.binaryFileUpload("fileToUpload",
         * String.format(Locale.US, "%s_%s.cyf", deviceIdentifier, measurementIdentifier),
         * "/home/muthmann/Projekte/Cyface/collector/test.txt", "application/octet-stream");
         * HttpClientRequest request = vertx.createHttpClient().post(port, "localhost", "/measurements")
         * .handler(response -> {
         * context.assertEquals(201, response.statusCode());
         * }).endHandler(end -> {
         * LOGGER.info("Request complete.");
         * async.complete();
         * }).exceptionHandler(t -> {
         * LOGGER.error(t);
         * context.fail();
         * async.complete();
         * });
         * MultipartFormUpload upload = new MultipartFormUpload(vertx.getOrCreateContext(), parts, true);
         * upload.run();
         * upload.endHandler(e -> {
         * LOGGER.info("Finished handling data.");
         * // LOGGER.info("" + request.headers());
         * // request.end();
         * });
         * upload.handler(data -> {
         * LOGGER.info("Handling data: " + data);
         * request.putHeader("Content-Length", String.valueOf(data.length()));
         * request.putHeader("Content-Type", "multipart/form-data");
         * request.write(data).end();
         * });
         * upload.exceptionHandler(t -> {
         * LOGGER.error(t);
         * context.fail();
         * async.complete();
         * });
         * upload.resume();
         * async.await(2000L);
         */
    }
}
