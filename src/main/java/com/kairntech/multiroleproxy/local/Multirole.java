package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import com.kairntech.multiroleproxy.util.SimpleHttpClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.local.Multirole.Status.not_known_yet;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLog;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;
import static java.util.logging.Level.FINEST;

public class Multirole {

    private static final Logger log = Logger.getLogger(Multirole.class.getSimpleName());

    private final MultiroleChangeNotifier multiroleChangeNotifier;
    private final EventLoopGroup group;
    private final String id;
    final String host;
    final int port;
    private SimpleHttpClient multiroleClient;
    private volatile Status status = not_known_yet;
    private volatile String md5sum = "";
    private Bootstrap bootstrap;
    private ScheduledFuture schedule;

    public enum Status {
        not_known_yet,
        running,
        stopped,
        not_a_multirole
    }

    public Multirole(EventLoopGroup group, MultiroleChangeNotifier multiroleChangeNotifier, String host, int port) {
        this.multiroleChangeNotifier = multiroleChangeNotifier;
        this.id = host + ":" + port;
        this.host = host;
        this.port = port;
        this.group = group;
    }

    public String getId() {
        return this.id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Status getStatus() {
        return status;
    }

    public void connect() {
        if (status == Status.not_known_yet)
            maybeLogFinest(log, () -> "connecting to the multirole server on " + host + ":" + port);
        if (multiroleClient == null) {
            this.bootstrap  = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new HttpResponseDecoder());
            multiroleClient = new SimpleHttpClient(group, host, port);
        }
        maybeLogFinest(log, () -> "fetching openapi spec from  " + host + ":" + port);
        multiroleClient.get("/openapi.json").send(handler -> {
            if (handler.success()) {
                if (schedule != null) {
                    schedule.cancel(false);
                    schedule = null;
                }
                FullHttpResponse response = handler.result();
                if (response.status().equals(HttpResponseStatus.OK)) {
                    try {
                        OpenAPISpecParser.OpenAPISpec spec = OpenAPISpecParser.parse(response.content(), false);
                        maybeLog(log, FINEST, () -> "fetched openapi spec successfully from  " + host + ":" + port);
                        status = Status.running;
                        String message = "unchanged";
                        if (!md5sum.equals(spec.md5sum)) {
                            message = "changed";
                            md5sum = spec.md5sum;
                            multiroleChangeNotifier.notifySpecChanged(this, spec);
                        }
                        String finalMessage = message;
                        maybeLogFinest(log, () -> "openapi spec " + finalMessage + " for " + host + ":" + port);
                        // setup a connection watch handler
                        setupConnectionLostHandler();
                    } catch (Throwable t) {
                        status = Status.not_a_multirole;
                        log.severe("failed to parse multirole openapi spec of " + host + ":" + port);
                    }
                } else {
                    log.severe("failed to get multirole openapi spec from " + host + ":" + port);
                    status = Status.not_a_multirole;
                }
            } else {
                if (status != Status.stopped)
                    log.warning("failed to connect to multirole: " + host + ":" + port);
                status = Status.stopped;
                // if connection failed, try again later
                if (schedule == null)
                    schedule = group.schedule(this::connect, 30, TimeUnit.SECONDS);
            }
        });
    }

    public void notifyRemoved() {
        multiroleChangeNotifier.notifyMultiroleDeleted(this);
    }


    private void setupConnectionLostHandler() {
        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // if connection succeeded, add a close listener, don't send anything and
                // wait for multirole server to kick us or to shut down
                // in the case, try to reconnect
                future.channel().closeFuture().addListener((ChannelFutureListener) future1 -> {
                    maybeLog(log, FINEST, () -> "got kicked from multirole " + host + ":" + port);
                    connect();
                });
            } else {
                // if connection failed, try again later
                if (schedule == null)
                    schedule = group.schedule(Multirole.this::connect, 30, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Multirole multirole = (Multirole) o;
        return port == multirole.port && host.equals(multirole.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }


    @Override
    public String toString() {
        return host + ":" + port;
    }
}
