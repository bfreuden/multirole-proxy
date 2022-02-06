package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.remote.RouterHandler.X_REQUEST_UUID_HEADER;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

public class ResponseTweakerHandler extends ChannelInboundHandlerAdapter {

    private boolean keepAlive;
    private String requestUUID;
    private static final Logger log = Logger.getLogger( ResponseTweakerHandler.class.getSimpleName().replace("Handler", "") );
    private static final Logger log2 = Logger.getLogger( RequestTweakerHandler.class.getSimpleName().replace("Handler", "") );

    public class RequestTweakerHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                keepAlive = HttpUtil.isKeepAlive(request);
                requestUUID = request.headers().get(X_REQUEST_UUID_HEADER);
                if (requestUUID == null)
                    requestUUID = "";
                maybeLogFinest(log2, () -> "get request UUID: " + ctx.channel() + " " + requestUUID);
                if (keepAlive) {
                    maybeLogFinest(log2, () -> "keep-alive request detected, changing it to close: " + ctx.channel() + " " + msg);
                    request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                } else {
                    maybeLogFinest(log2, () -> "non keep-alive request detected, nothing to do: " + ctx.channel() + " " + msg);
                }
            }
            ctx.write(msg, promise);
        }
    }

    public final RequestTweakerHandler requestConnectionTweaker = new RequestTweakerHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse) {
            maybeLogFinest(log, () -> "analyzing http response from multirole: " + ctx.channel() + " " + msg);
            HttpResponse response = (HttpResponse) msg;
            response.headers().add(X_REQUEST_UUID_HEADER, requestUUID);
            if (keepAlive) {
                maybeLogFinest(log, () -> "keep-alive request, changing response to keep-alive: " + ctx.channel());
                response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                maybeLogFinest(log, () -> "non keep-alive request detected, nothing to do");
            }
            String connectionHeader = response.headers().get(HttpHeaderNames.CONNECTION);
            if (!HttpHeaderValues.CLOSE.toString().equals(connectionHeader)) {
                log.log(Level.SEVERE, "expecting a connection close response header, got: " + connectionHeader);
            }
        } else if ((msg instanceof HttpObject)){
            maybeLogFinest(log, () -> "receiving content from multirole: " + ctx.channel() + " " + msg);
        } else  {
            log.log(Level.WARNING, "unsupported message type: " + ctx.channel() + " " + msg);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}
