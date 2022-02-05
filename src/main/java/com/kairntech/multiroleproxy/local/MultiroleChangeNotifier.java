package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.ProxyConfig;
import com.kairntech.multiroleproxy.remote.RouterHandler;
import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import com.kairntech.multiroleproxy.util.SimpleHttpClient;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class MultiroleChangeNotifier {

    private static final Logger log = Logger.getLogger(MultiroleChangeNotifier.class.getSimpleName());

    public static String X_LOCAL_PROXY_ID_HEADER = "X-Local-Proxy-ID";
    public static String X_MULTIROLE_ID_HEADER = "X-Multirole-ID";
    public static String X_MULTIROLE_SPEC_MD5_HEADER = "X-Multirole-Spec-MD5";

    private final SimpleHttpClient remoteProxyClient;
    private final String localProxyId;
    private final HashMap<Multirole, OpenAPISpecParser.OpenAPISpec> specsToPublish = new HashMap<>();
    private boolean connectedToRemote = false;

    public MultiroleChangeNotifier(EventLoopGroup group, ProxyConfig config, String localProxyId) {
        this.remoteProxyClient = new SimpleHttpClient(group, config.getHost(), config.getPort());
        this.localProxyId = localProxyId;
    }

    public synchronized void notifySpecChanged(Multirole multirole, OpenAPISpecParser.OpenAPISpec spec) {
        log.info("multirole spec changed " + multirole);
        if (connectedToRemote) {
            publishSpec(multirole, spec);
        } else {
            log.info("queuing multirole spec for later publishing " + multirole);
            specsToPublish.put(multirole, spec);
        }
    }

    public synchronized void remoteProxyConnected(boolean connected) {
        connectedToRemote = connected;
        if (connected)
            log.info("remote proxy available");
        else
            log.info("remote proxy no longer available");
        if (connected) {
            ArrayList<Multirole> multiroles = new ArrayList<>(specsToPublish.keySet());
            for (Multirole multirole : multiroles) {
                publishSpec(multirole, specsToPublish.get(multirole));
            }
        }
    }

    private void publishSpec(Multirole multirole, OpenAPISpecParser.OpenAPISpec spec) {
        log.info("publishing multirole spec " + multirole);
        SimpleHttpClient.SimpleHttpClientRequest request = remoteProxyClient.request(HttpMethod.POST, RouterHandler.REGISTER_SPEC_URI, Unpooled.copiedBuffer((CharSequence) spec, StandardCharsets.UTF_8));
        request.request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        request.request.headers().add(X_LOCAL_PROXY_ID_HEADER, localProxyId);
        request.request.headers().add(X_MULTIROLE_ID_HEADER, multirole.getId());
        request.request.headers().add(X_MULTIROLE_SPEC_MD5_HEADER, spec.md5sum);
        request.send(future -> {
            if (future.success()) {
                log.info("multirole spec successfully published " + multirole);
                synchronized (MultiroleChangeNotifier.this) {
                    specsToPublish.remove(multirole);
                }
            } else {
                log.warning("failed to publish spec of multirole " + multirole);
            }
        });
    }
}
