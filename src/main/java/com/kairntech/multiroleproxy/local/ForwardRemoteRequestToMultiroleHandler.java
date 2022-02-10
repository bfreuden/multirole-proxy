package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.local.MultiroleChangeNotifier.X_MULTIROLE_ID_HEADER;
import static com.kairntech.multiroleproxy.local.Multiroles.MULTIROLES_ATTRIBUTE;
import static com.kairntech.multiroleproxy.local.Multiroles.MULTIROLE_ATTRIBUTE;
import static com.kairntech.multiroleproxy.remote.RouterHandler.X_REQUEST_UUID_HEADER;
import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ForwardRemoteRequestToMultiroleHandler extends ChannelInboundHandlerAdapter {

    private final EventLoopGroup group;
    private final Bootstrap b;
    private final List<HttpObject> messages = new ArrayList<>();
    private volatile Channel multiroleChannel;
    private volatile boolean connecting = false;
    private volatile boolean connected = false;
    private volatile boolean notConnected = false;

    private static final Logger log = Logger.getLogger( ForwardRemoteRequestToMultiroleHandler.class.getSimpleName().replace("Handler", "") );
    private Multirole multirole;
    private String requestId;

    public ForwardRemoteRequestToMultiroleHandler(EventLoopGroup group) {
        this.group = group;
        this.b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new MultiroleForwardingClientChannelInitializer( null));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        for (Object message : messages)
            ReferenceCountUtil.release(message);
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        maybeLogFinest(log, () -> "receiving http content from remote proxy: " + ctx.channel() + " " + msg);
        synchronized (messages) {
            if (!connected && !connecting) {
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    String multiroleId = request.headers().get(X_MULTIROLE_ID_HEADER);
                    if (multiroleId == null)
                        log.severe( "no " + X_MULTIROLE_ID_HEADER + " header in http request from remote proxy: " + ctx.channel() + " " + msg);
                    requestId = request.headers().get(X_REQUEST_UUID_HEADER);
                    if (requestId == null)
                        log.severe( "no " + X_REQUEST_UUID_HEADER + " header in http request from remote proxy: " + ctx.channel() + " " + msg);
                    Multiroles multiroles = ctx.channel().attr(MULTIROLES_ATTRIBUTE).get();
                    multirole = multiroles.getServer(multiroleId);
                    if (multirole == null)
                        log.severe( "no multirole server with id " + X_MULTIROLE_ID_HEADER + " multiroleId to serve http request from remote proxy: " + ctx.channel() + " " + msg);
                }
                if (multirole == null) {
                    maybeLogFinest(log, () -> "no multirole, discarding the message: " + ctx.channel() + " " + msg);
                    ReferenceCountUtil.release(msg);
                    if (msg instanceof LastHttpContent) {
                        write503Response(ctx);
                        resetState();
                    }
                } else {
                    maybeLogFinest(log, () -> "connecting to multirole: " + ctx.channel() + " " + msg);
                    connecting = true;
                    b.connect(multirole.getHost(), multirole.getPort()).addListener((ChannelFutureListener) connectFuture -> {
                        synchronized (messages) {
                            if (connectFuture.isSuccess()) {
                                multiroleChannel = connectFuture.channel();
                                multiroleChannel.attr(MULTIROLE_ATTRIBUTE).set(multirole);
                                multiroleChannel.closeFuture().addListener((ChannelFutureListener) future -> {
                                    maybeLogFinest(log, () -> "multirole channel closed: " + multiroleChannel);
                                    if (!future.isSuccess()) {
                                        log.log(Level.WARNING, "multirole channel closed error", future.cause());
                                    }
                                });
                                maybeLogFinest(log, () -> "connected to multirole with channel: " + multiroleChannel);
                                boolean endOfRequestReached = false;
                                if (!messages.isEmpty())
                                    maybeLogFinest(log, () -> "sending " + messages.size() + " queued message(s) to multirole: " + ctx.channel());
                                for (HttpObject message : messages) {
                                    endOfRequestReached = writeMessageToMultiroleAndMaybeCloseChannel(message, true);
                                }
                                messages.clear();
                                if (!endOfRequestReached) {
                                    connected = true;
                                    maybeLogFinest(log, () -> "end of messages not reached after un-queuing messages: " + multiroleChannel);
                                } else {
                                    maybeLogFinest(log, () -> "end of messages reached after un-queuing messages: " + multiroleChannel);
                                }
                            } else {
                                notConnected = true;
                                log.log(Level.WARNING, "connection to multirole failed, writing 503 response", connectFuture.cause());
                                for (HttpObject message : messages) {
                                    ReferenceCountUtil.release(message);
                                }
                                write503Response(ctx);
                            }
                        }
                    });
                }
            }
            if (multirole != null) {
                if (msg instanceof HttpObject) {
                    if (!connected) {
                        maybeLogFinest(log, () -> "not connected to multirole yet, enqueuing message: " + ctx.channel() + " " + msg);
                        messages.add((HttpObject) msg);
                    } else if (notConnected) {
                        maybeLogFinest(log, () -> "connection to multirole failed, discarding the message: " + ctx.channel() + " " + msg);
                        ReferenceCountUtil.release(msg);
                    } else {
                        maybeLogFinest(log, () -> "connected to multirole, writing message directly: " + ctx.channel() + " " + msg);
                        writeMessageToMultiroleAndMaybeCloseChannel((HttpObject)msg, false);
                    }
                } else {
                    ReferenceCountUtil.release(msg);
                }
            }
        }
    }

    private boolean writeMessageToMultiroleAndMaybeCloseChannel(HttpObject message, Boolean accumulated) {
        ChannelFuture writeFuture = multiroleChannel.writeAndFlush(message);
        String qualifier = accumulated ? "" : "accumulated ";
        maybeLogFinest(log, () -> "sending " +qualifier + "http message to multirole: " + multiroleChannel + " " + message);
        if (message instanceof LastHttpContent) {
            maybeLogFinest(log, () -> "request send about to complete: " + multiroleChannel + " " + message);
            resetState();
            writeFuture
                    .addListener(future -> maybeLogFinest(log, () -> "request send complete, marking channel as 'not connected': " + multiroleChannel + " " + message));
            return true;
        }
        return false;
    }

    private void resetState() {
        connecting = false;
        connected = false;
        notConnected = false;
        multiroleChannel = null;
        multirole = null;
        requestId = null;
    }

    private void write503Response(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(503, "local proxy can't connect to multirole"),
                Unpooled.EMPTY_BUFFER);
        response.headers().add(X_REQUEST_UUID_HEADER, requestId);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}