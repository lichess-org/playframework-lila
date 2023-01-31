/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import play.Environment;
import play.api.libs.ws.ahc.AsyncHttpClientProvider;
import play.components.AkkaComponents;
import play.components.ConfigurationComponents;
import play.inject.ApplicationLifecycle;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.WSClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;

/**
 * AsyncHttpClient WS implementation components.
 *
 * <p>
 *
 * <p>Usage:
 *
 * <p>
 *
 * <pre>
 * public class MyComponents extends BuiltInComponentsFromContext implements AhcWSComponents {
 *
 *   public MyComponents(ApplicationLoader.Context context) {
 *       super(context);
 *   }
 *
 *   // some service class that depends on WSClient
 *   public SomeService someService() {
 *       // wsClient is provided by AhcWSComponents
 *       return new SomeService(wsClient());
 *   }
 *
 *   // other methods
 * }
 * </pre>
 *
 * @see play.BuiltInComponents
 * @see WSClient
 */
public interface AhcWSComponents
    extends WSClientComponents, ConfigurationComponents, AkkaComponents {

  Environment environment();

  ApplicationLifecycle applicationLifecycle();

  default WSClient wsClient() {
    return new AhcWSClient((StandaloneAhcWSClient) standaloneWSClient(), materializer());
  }

  default StandaloneWSClient standaloneWSClient() {
    return new StandaloneAhcWSClient(asyncHttpClient(), materializer());
  }

  default AsyncHttpClient asyncHttpClient() {
    return new AsyncHttpClientProvider(
            environment().asScala(),
            configuration(),
            applicationLifecycle().asScala(),
            executionContext())
        .get();
  }
}
