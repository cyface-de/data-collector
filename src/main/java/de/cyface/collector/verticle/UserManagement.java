/**
 * 
 */
package de.cyface.collector.verticle;

import de.cyface.collector.handler.UserCreationHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A verticle that starts up an endpoint that may be used to create new user accounts for the Cyface data collector.
 * This is separated from the Upload API as we do not want to expose this unintentionally to the public.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class UserManagement extends AbstractVerticle {
    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        final Router router = setupRouter();
        // TODO: Make the port configurable.
        startHttpServer(startFuture, router, 13371);
    }

    private Router setupRouter() {
        Router router = Router.router(getVertx());

        router.post("/user").handler(BodyHandler.create()).blockingHandler(new UserCreationHandler());

        return router;
    }

    private void startHttpServer(final Future<Void> startFuture, final Router router, final int port) {
        getVertx().createHttpServer().requestHandler(router).listen(port, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(res.cause());
            }
        });
    }
}
