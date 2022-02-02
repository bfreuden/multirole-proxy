package com.kairntech.multiroleproxy.local;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ResponseConnectionTweakerHandler extends ChannelInboundHandlerAdapter {

    private boolean keepAlive;
    private static final Logger log = Logger.getLogger( ResponseConnectionTweakerHandler.class.getSimpleName().replace("Handler", "") );

    public class RequestConnectionTweaker extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) msg;
                keepAlive = HttpUtil.isKeepAlive(request);
                if (keepAlive) {
                    log.log(Level.FINEST, "keep-alive request detected, changing it to close: " + ctx.channel() + " " + msg);
                    request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                } else {
                    log.log(Level.FINEST, "non keep-alive request detected, nothing to do: " + ctx.channel() + " " + msg);
                }
            }
            ctx.write(msg, promise);
        }
    }

    public final RequestConnectionTweaker requestConnectionTweaker = new RequestConnectionTweaker();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse) {
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "analyzing http response from multirole: " + ctx.channel() + " " + msg);
            HttpResponse response = (HttpResponse) msg;
            if (keepAlive) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "keep-alive request, changing response to keep-alive: " + ctx.channel());
                response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "non keep-alive request detected, nothing to do");
            }
            String connectionHeader = response.headers().get(HttpHeaderNames.CONNECTION);
            if (!HttpHeaderValues.CLOSE.toString().equals(connectionHeader)) {
                log.log(Level.SEVERE, "expecting a connection close response header, got: " + connectionHeader);
            }
        } else if ((msg instanceof HttpObject)){
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "receiving content from multirole: " + ctx.channel() + " " + msg);
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
