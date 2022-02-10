package com.kairntech.multiroleproxy.local;

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

    public static class RemoteProxyRequest {

        private final List<HttpObject> messages = new ArrayList<>();
        private final Bootstrap b;
        private volatile Channel multiroleChannel;
        private volatile boolean connecting = false;
        private volatile boolean connected = false;
        private volatile boolean notConnected = false;
        private Multirole multirole;
        private String requestId;
        private Runnable doneCallback;

        public RemoteProxyRequest(Bootstrap b, String requestId, Multirole multirole) {
            this.requestId = requestId;
            this.multirole = multirole;
            this.b = b;
        }

        public void releaseMessages() {
            for (Object message : messages)
                ReferenceCountUtil.release(message);
        }

        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            synchronized (messages) {
                if (!connected && !connecting) {
                    maybeLogFinest(log, () -> "connecting to multirole: " + requestId + " " + ctx.channel() + " " + msg);
                    connecting = true;
                    b.connect(multirole.getHost(), multirole.getPort()).addListener((ChannelFutureListener) connectFuture -> {
                        synchronized (messages) {
                            if (connectFuture.isSuccess()) {
                                multiroleChannel = connectFuture.channel();
                                multiroleChannel.attr(MULTIROLE_ATTRIBUTE).set(multirole);
                                multiroleChannel.closeFuture().addListener((ChannelFutureListener) future -> {
                                    maybeLogFinest(log, () -> "multirole channel closed: " + requestId + " " + multiroleChannel);
                                    if (!future.isSuccess()) {
                                        log.log(Level.WARNING, "multirole channel closed error", future.cause());
                                    }
                                });
                                maybeLogFinest(log, () -> "connected to multirole with channel: " +  requestId + " " + multiroleChannel);
                                boolean endOfRequestReached = false;
                                if (!messages.isEmpty())
                                    maybeLogFinest(log, () -> "sending " + messages.size() + " queued message(s) to multirole: " + requestId + " " + ctx.channel());
                                for (HttpObject message : messages) {
                                    endOfRequestReached = writeMessageToMultiroleAndMaybeCloseChannel(message, true);
                                }
                                messages.clear();
                                if (!endOfRequestReached) {
                                    connected = true;
                                    maybeLogFinest(log, () -> "end of messages not reached after un-queuing messages: " + requestId + " " + multiroleChannel);
                                } else {
                                    maybeLogFinest(log, () -> "end of messages reached after un-queuing messages: " + requestId + " " + multiroleChannel);
                                }
                            } else {
                                notConnected = true;
                                log.log(Level.WARNING, "connection to multirole failed, writing 503 response", connectFuture.cause());
                                for (HttpObject message : messages) {
                                    ReferenceCountUtil.release(message);
                                }
                                write503Response(ctx, requestId);
                                doneCallback.run();
                            }
                        }
                    });
                }
                if (multirole != null) {
                    if (msg instanceof HttpObject) {
                        if (!connected) {
                            maybeLogFinest(log, () -> "not connected to multirole yet, enqueuing message: " + requestId + " " + ctx.channel() + " " + msg);
                            messages.add((HttpObject) msg);
                        } else if (notConnected) {
                            maybeLogFinest(log, () -> "connection to multirole failed, discarding the message: " + requestId + " " + ctx.channel() + " " + msg);
                            ReferenceCountUtil.release(msg);
                        } else {
                            maybeLogFinest(log, () -> "connected to multirole, writing message directly: " + requestId + " " + ctx.channel() + " " + msg);
                            writeMessageToMultiroleAndMaybeCloseChannel((HttpObject)msg, false);
                        }
                    } else {
                        ReferenceCountUtil.release(msg);
                    }
                }
            }
        }

        private boolean writeMessageToMultiroleAndMaybeCloseChannel(HttpObject message, boolean accumulated) {
            String qualifier = accumulated ? "" : "accumulated ";
            if (message instanceof LastHttpContent) {
                ChannelFuture writeFuture = multiroleChannel.writeAndFlush(message);
                maybeLogFinest(log, () -> "sending last " + qualifier + "http message to multirole: " + requestId + " " + multiroleChannel + " " + message);
                writeFuture
                        .addListener(future -> maybeLogFinest(log, () -> "request send complete, marking channel as 'not connected': " + requestId + " " + multiroleChannel + " " + message));
                doneCallback.run();
                return true;
            } else {
                maybeLogFinest(log, () -> "sending " + qualifier + "http message to multirole: " + requestId + " " + multiroleChannel + " " + message);
                multiroleChannel.write(message);
                return false;
            }
        }

        public void setDoneCallback(Runnable doneCallback) {
            this.doneCallback = doneCallback;
        }
    }

    private List<RemoteProxyRequest> remoteRequests = new ArrayList<>();
    private RemoteProxyRequest remoteRequest;
    private String requestId1;
    private Multirole multirole;

    private final EventLoopGroup group;
    private final Bootstrap b;

    private static final Logger log = Logger.getLogger( ForwardRemoteRequestToMultiroleHandler.class.getSimpleName().replace("Handler", "") );

    public ForwardRemoteRequestToMultiroleHandler(EventLoopGroup group) {
        this.group = group;
        this.b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new MultiroleForwardingClientChannelInitializer( null));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        for (RemoteProxyRequest request: remoteRequests)
            request.releaseMessages();
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        maybeLogFinest(log, () -> "receiving http content from remote proxy: " + requestId1 + " " + ctx.channel() + " " + msg);
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String multiroleId = request.headers().get(X_MULTIROLE_ID_HEADER);
            if (multiroleId == null)
                log.severe( "no " + X_MULTIROLE_ID_HEADER + " header in http request from remote proxy: " + requestId1 + " " + ctx.channel() + " " + msg);
            requestId1 = request.headers().get(X_REQUEST_UUID_HEADER);
            if (requestId1 == null)
                log.severe( "no " + X_REQUEST_UUID_HEADER + " header in http request from remote proxy: " + " " + ctx.channel() + " " + msg);
            Multiroles multiroles = ctx.channel().attr(MULTIROLES_ATTRIBUTE).get();
            multirole = multiroles.getServer(multiroleId);
            if (multirole == null)
                log.severe( "no multirole server with id " + X_MULTIROLE_ID_HEADER + " multiroleId to serve http request from remote proxy: " + requestId1 + " " + ctx.channel() + " " + msg);
            if (requestId1 != null && multirole != null) {
                remoteRequest = new RemoteProxyRequest(b, requestId1, multirole);
                remoteRequest.setDoneCallback(() -> remoteRequests.remove(remoteRequest));
            }
        }
        if (multirole == null) {
            maybeLogFinest(log, () -> "no multirole, discarding the message: " + requestId1 + " " + ctx.channel() + " " + msg);
            ReferenceCountUtil.release(msg);
            if (msg instanceof LastHttpContent) {
                write503Response(ctx, requestId1);
            }
        } else {
            remoteRequest.channelRead(ctx, msg);
        }

    }

    private static void write503Response(ChannelHandlerContext ctx, String requestId) {
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