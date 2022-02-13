package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.ProxyConfig;
import com.kairntech.multiroleproxy.util.Sequencer;
import com.kairntech.multiroleproxy.util.SimpleHttpRequest;
import com.kairntech.multiroleproxy.util.SimpleHttpServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.X_LOCAL_PROXY_ID_HEADER;
import static com.kairntech.multiroleproxy.local.Multiroles.MULTIROLES_ATTRIBUTE;
import static com.kairntech.multiroleproxy.remote.RouterHandler.REGISTER_CLIENT_URI;

public class AdminServer extends SimpleHttpServer {

    static final Pattern DURATION_REGEX = Pattern.compile("(\\d+)\\W*(second|minute|hour)s?");

    private final Multiroles multiroles;
    private final EventLoopGroup group;
    private final ProxyConfig config;
    private final MultiroleChangeNotifier multiroleChangeNotifier;
    private final SslContext sslCtx;
    private final String id;
    private volatile Channel channel;
    private boolean displayErrorMessage = true;
    private Thread connectionThread = null;
    private Thread disconnectionThread = null;
    private Object startLock = new Object();
    private volatile String connectReply = "";
    private long millisBeforeDisconnection;

    public AdminServer(NioEventLoopGroup bossGroup, EventLoopGroup workerGroup, Multiroles multiroles, ProxyConfig config, MultiroleChangeNotifier multiroleChangeNotifier, SslContext sslCtx, String id) {
        super(bossGroup, workerGroup);
        this.multiroles = multiroles;
        this.group = workerGroup;
        this.config = config;
        this.multiroleChangeNotifier = multiroleChangeNotifier;
        this.sslCtx = sslCtx;
        this.id = id;
    }

    public void start() {

        addHandler(Pattern.compile(Pattern.quote("/status")), HttpMethod.GET, (req) -> {
            StringJoiner joiner = new StringJoiner("\r\n");
            writeListMultiroles(joiner);
            joiner.add("");
            joiner.add(channel == null ? (connectionThread == null ? "disconnected from remote proxy" : "disconnected from remote proxy (reconnection in progress)") : "connected to remote proxy");
            return textResponse(joiner.toString());
        });

        addHandler(Pattern.compile(Pattern.quote("/connect")), HttpMethod.POST, (req) -> {
            JSONObject parse = parseJsonObjectBody(req);
            String duration = (String)parse.get("duration");
            if (duration == null)
                throw new IllegalArgumentException("missing duration argument");
            Matcher matcher = DURATION_REGEX.matcher(duration);
            if (!matcher.matches())
                throw new IllegalArgumentException("illegal duration ("  + duration + "), expecting something like: \"1 minute\" or \"3 hours\"");
            String unit = matcher.group(2);

            millisBeforeDisconnection = Long.parseLong(matcher.group(1));
            if (unit.equals("second"))
                millisBeforeDisconnection *= 1000;
            else if (unit.equals("minute"))
                millisBeforeDisconnection *= 60*1000;
            else if (unit.equals("hour"))
                millisBeforeDisconnection *= 3600*1000;
            else
                throw new IllegalArgumentException("unsupported duration unit: " + matcher.group(1));
            String response;
            synchronized (startLock) {
                if (connectionThread == null) {
                    connectionThread = new Thread(this::connectToRemote);
                    connectionThread.start();
                    try {
                        startLock.wait(2000L);
                        response = connectReply;
                    } catch (InterruptedException e) {
                        response = "failed to connect to remote proxy, will retry later";
                    }
                } else {
                    response = "already connected to remote proxy";
                }
            }
            return textResponse(response);
        });

        addHandler(Pattern.compile(Pattern.quote("/disconnect")), HttpMethod.POST, (req) -> {
            String response;
            synchronized (startLock) {
                if (disconnectionThread != null) {
                    disconnectionThread.interrupt();
                    disconnectionThread = null;
                }
                if (connectionThread == null) {
                    response = "already disconnected from remote proxy";
                } else {
                    try {
                        connectionThread.interrupt();
                        connectionThread = null;
                    } finally {
                        if (channel != null)
                            channel.close();
                    }
                    response = "disconnected from remote proxy";
                }
            }
            return textResponse(response);
        });

        addHandler(Pattern.compile(Pattern.quote("/add-multirole")), HttpMethod.POST, (req) -> {
            JSONObject parse = parseJsonObjectBody(req);
            String host = (String)parse.get("host");
            if (host == null)
                host = "localhost";
            String port = (String) parse.get("port");
            if (port == null)
                throw new IllegalArgumentException("missing port argument");
            String paths = (String) parse.get("paths");
            if (paths == null)
                throw new IllegalArgumentException("missing paths argument");
//            String paths = (String) parse.get("paths");
//            if (paths == null)
//                throw new IllegalArgumentException("missing paths argument");
            multiroles.addServer(host, Integer.parseInt(port), paths);
            return textResponse("server successfully added");
        });

        addHandler(Pattern.compile(Pattern.quote("/remove-multirole")), HttpMethod.POST, (req) -> {
            JSONObject parse = parseJsonObjectBody(req);
            String host = (String)parse.get("host");
            if (host == null)
                host = "localhost";
            String port = (String) parse.get("port");
            multiroles.deleteServer(host, Integer.parseInt(port));
            return textResponse("server successfully deleted");
        });

        addHandler(Pattern.compile(Pattern.quote("/list-multiroles")), HttpMethod.GET, (req) -> {
            StringJoiner joiner = new StringJoiner("\r\n");
            writeListMultiroles(joiner);
            return textResponse(joiner.toString());
        });

        listenLocally().addListener((ChannelFutureListener)channelFuture -> {
            if (channelFuture.isSuccess()) {
                InetSocketAddress socketAddress = (InetSocketAddress)channelFuture.channel().localAddress();
                System.out.println("admin server listening to requests on http://localhost:" + socketAddress.getPort());
            } else {
                System.err.println("admin server not listening to requests");
            }
        });
    }

    private void writeListMultiroles(StringJoiner joiner) {
        HashSet<Multirole> servers = multiroles.getServers();
        if (servers.isEmpty()) {
            joiner.add("no multirole servers");
        } else {
            joiner.add("multirole servers:");
            for (Multirole server : servers) {
                joiner.add("- " + server.getHost() + ":" + server.getPort() + " paths=" + server.getPaths() + " status: " + server.getStatus().name().replace('_', ' '));
            }
        }
    }

    private JSONObject parseJsonObjectBody(SimpleHttpRequest req) {
        return (JSONObject) JSONValue.parse(req.getRequest().content().toString(StandardCharsets.UTF_8));
    }

    private DefaultFullHttpResponse textResponse(String message) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(message + "\r\n", StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN+";charset=utf-8");
        return response;
    }

    private synchronized void disconnectFromRemote() {
        try {
            wait(millisBeforeDisconnection);
            synchronized (startLock) {
                if (connectionThread != null) {
                    try {
                        connectionThread.interrupt();
                        connectionThread = null;
                    } finally {
                        if (channel != null)
                            channel.close();
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized void connectToRemote() {
        try {
            Bootstrap b = new Bootstrap();
            System.out.println("connecting to remote proxy at " + config.getHost() + ":" + config.getPort() + "...");
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new LocalProxyChannelInitializer(group, sslCtx, c -> {
                        System.out.println("local proxy connected to remote proxy!");
                        displayErrorMessage = true;
                        channel = c;
                    }));
            boolean firstConnection = true;
            while (true) {
                try {
                    if (channel == null) {
                        Channel channel = b.connect(this.config.getHost(), this.config.getPort()).sync().channel();
                        if (firstConnection) {
                            firstConnection = false;
                            synchronized (startLock) {
                                disconnectionThread = new Thread(this::disconnectFromRemote);
                                disconnectionThread.start();
                                connectReply = "connected to remote proxy";
                                startLock.notify();
                            }
                        }
                        multiroles.setSequencer(new Sequencer(channel));
                        channel.attr(MULTIROLES_ATTRIBUTE).set(multiroles);
                        HttpRequest request = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1, HttpMethod.GET, REGISTER_CLIENT_URI, Unpooled.EMPTY_BUFFER);
                        request.headers().set(HttpHeaderNames.HOST, this.config.getHost() + ":" + this.config.getPort());
                        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                        request.headers().set(X_LOCAL_PROXY_ID_HEADER, this.id);
                        channel.writeAndFlush(request).addListener((ChannelFutureListener)channelFuture -> {
                            if (channelFuture.isSuccess()) {
                                multiroleChangeNotifier.remoteProxyConnected(true);
                                multiroles.redeclareServers();
                            } else {
                                multiroleChangeNotifier.remoteProxyConnected(false);
                            }
                        });
                        channel.closeFuture().addListener(e -> {
                            this.channel = null;
                            multiroleChangeNotifier.remoteProxyConnected(false);
                        });
                    }
                } catch (Exception e) {
                    if (firstConnection) {
                        firstConnection = false;
                        synchronized (startLock) {
                            connectReply = "failed to connect to remote proxy, will retry every 30 seconds: " + e.getMessage();
                            startLock.notify();
                        }
                    }
                    if (displayErrorMessage) {
                        System.err.println("remote proxy connection failed!");
                        System.err.println("will retry every 30 seconds from now...");
                        displayErrorMessage = false;
                    }
                    if (!(e instanceof ConnectException)) {
                        if (channel != null)
                            channel.close();
                    }
                    channel = null;
                } finally {
                    wait(30000L);
                }
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

    }

}
