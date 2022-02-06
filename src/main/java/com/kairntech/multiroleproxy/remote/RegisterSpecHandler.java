package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

// can't be a SimpleChannelInboundHandler because it will forward a message if not the target
public class RegisterSpecHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(RegisterSpecHandler.class.getSimpleName().replace("Handler", "") );

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Peers peers = ctx.channel().attr(Peers.PEERS_ATTRIBUTE).get();
        RouterHandler.RouteType routeType = ctx.channel().attr(RouterHandler.ROUTE_TYPE_ATTRIBUTE).get();
        if (routeType == RouterHandler.RouteType.REGISTER_SPEC) {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                if (!request.decoderResult().isSuccess()) {
                    writeResponse(ctx, BAD_REQUEST, "http decoder error");
                } else {
                    String proxyId = request.headers().get(X_LOCAL_PROXY_ID_HEADER);
                    String multiroleId = request.headers().get(X_MULTIROLE_ID_HEADER);
                    String specMd5 = request.headers().get(X_MULTIROLE_SPEC_MD5_HEADER);
                    if (proxyId == null || multiroleId == null || specMd5 == null) {
                        writeResponse(ctx, BAD_REQUEST, "missing http headers: " +
                                X_LOCAL_PROXY_ID_HEADER + "=" + proxyId + " " +
                                X_MULTIROLE_ID_HEADER + "=" + multiroleId + " " +
                                X_MULTIROLE_SPEC_MD5_HEADER + "=" + specMd5 + " ");
                    } else {
                        try {
                            OpenAPISpecParser.OpenAPISpec spec = OpenAPISpecParser.parse(request.content(), true);
                            peers.declareSpec(proxyId, multiroleId, specMd5, spec);
                            writeResponse(ctx, BAD_REQUEST, "done");
                        } catch (Exception ex) {
                            writeResponse(ctx, INTERNAL_SERVER_ERROR, ex.getClass().getName() + " " + ex.getMessage());
                        }

                    }
                }
            } else {
                log.log(Level.WARNING, "unsupported message: " + msg);
            }
            ReferenceCountUtil.release(msg);
        } else {
            // forward message to the next
            ctx.fireChannelRead(msg);
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN + ";charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }
}