package de.cyface.collector.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public final class MainVerticle extends AbstractVerticle {
	@Override
	public void start(final Future<Void> startFuture) throws Exception {
		// Create a few futures to synchronize the start up process
		Future<Void> collectorApiFuture = Future.future();
		Future<Void> managementApiFuture = Future.future();
		CompositeFuture startUpProcessFuture = CompositeFuture.all(collectorApiFuture, managementApiFuture);
		
		// Start the collector API as first verticle.
		vertx.deployVerticle(CollectorApiVerticle.class, new DeploymentOptions().setConfig(config()), result -> {
			if (result.succeeded()) {
				collectorApiFuture.complete();
			} else {
				collectorApiFuture.fail(result.cause());
			}
		});
		
		// Start the management API as second verticle.
		vertx.deployVerticle(ManagementApiVerticle.class, new DeploymentOptions().setConfig(config()), result -> {
			if(result.succeeded()) {
				managementApiFuture.complete();
			} else {
				managementApiFuture.fail(result.cause());
			}
		});
		
		// As soon as both futures have a succeeded or one failed, finish the start up process.
		startUpProcessFuture.setHandler(result -> {
			if(result.succeeded()) {
				startFuture.complete();
			} else {
				startFuture.fail(result.cause());
			}
		});

	}

}
