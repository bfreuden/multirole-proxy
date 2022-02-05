package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.util.SimpleHttpRequest;
import com.kairntech.multiroleproxy.util.SimpleHttpServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class AdminServer extends SimpleHttpServer {

    private final Multiroles multiroles;

    public AdminServer(NioEventLoopGroup bossGroup, EventLoopGroup workerGroup, Multiroles multiroles) {
        super(bossGroup, workerGroup);
        this.multiroles = multiroles;
    }

    public void start() {

        addHandler(Pattern.compile(Pattern.quote("/status")), HttpMethod.GET, (req) -> textResponse("running"));

        addHandler(Pattern.compile(Pattern.quote("/add-multirole")), HttpMethod.POST, (req) -> {
            JSONObject parse = parseJsonObjectBody(req);
            String host = (String)parse.get("host");
            Integer port = (Integer) parse.get("port");
            multiroles.addServer(host, port);
            return textResponse("server successfully added");
        });

        addHandler(Pattern.compile(Pattern.quote("/delete-multirole")), HttpMethod.POST, (req) -> {
            JSONObject parse = parseJsonObjectBody(req);
            String host = (String)parse.get("host");
            Integer port = (Integer) parse.get("port");
            multiroles.deleteServer(host, port);
            return textResponse("server successfully deleted");
        });

        addHandler(Pattern.compile(Pattern.quote("/list-multiroles")), HttpMethod.POST, (req) -> {
            HashSet<Multirole> servers = multiroles.getServers();
            StringJoiner joiner = new StringJoiner("\r\n");
            joiner.add("multirole servers:");
            for (Multirole server : servers) {
                joiner.add("- " + server.getHost() + ":" + server.getPort() + " status: " + server.getStatus().name().replace('_', ' '));
            }
            joiner.add("");
            return textResponse(joiner.toString());
        });

        listenLocally().addListener((channelFuture) -> {
            if (channelFuture.isSuccess()) {
                System.out.println("admin server listening to requests");
            } else {
                System.err.println("admin server not listening to requests");
            }
        });
    }

    private JSONObject parseJsonObjectBody(SimpleHttpRequest req) {
        return (JSONObject) JSONValue.parse(req.getRequest().content().toString(StandardCharsets.UTF_8));
    }

    private DefaultFullHttpResponse textResponse(String message) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN+";charset=utf-8");
        return response;
    }

}
