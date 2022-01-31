package com.kairntech.multiroleproxy.local;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ForwardRemoteRequestToMultiroleHandler extends ChannelInboundHandlerAdapter {

    private final EventLoopGroup group;
    private final Bootstrap b;
    private final List<HttpObject> messages = new ArrayList<>();
    private volatile Channel localChannel;
    private volatile Channel multiroleChannel;
    private volatile boolean connecting = false;
    private volatile boolean connected = false;
    private volatile boolean notConnected = false;
//    private AtomicBoolean connecting = new AtomicBoolean();
//    private AtomicBoolean connected = new AtomicBoolean();
//    private AtomicBoolean notConnected = new AtomicBoolean();

    private static final Logger log = Logger.getLogger( ForwardRemoteRequestToMultiroleHandler.class.getSimpleName().replace("Handler", "") );

    public ForwardRemoteRequestToMultiroleHandler(EventLoopGroup group) {
        this.group = group;
        this.b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new MultiroleForwardingClientChannelInitializer(() -> localChannel, null));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        this.localChannel = null;
        for (Object message : messages)
            ReferenceCountUtil.release(message);
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "receiving http content from remote proxy: " + ctx.channel() + " " + msg);
        synchronized (messages) {
            if (!connected && !connecting) {
//            if (!connected.get() && !connecting.get()) {
                if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "connecting to multirole: " + ctx.channel() + " " + msg);
                connecting = true;
//                connecting.set(true);
                localChannel = ctx.channel();
                b.connect("localhost", 12007).addListener((ChannelFutureListener) connectFuture -> {
                    synchronized (messages) {
                        if (connectFuture.isSuccess()) {
                            multiroleChannel = connectFuture.channel();
                            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "connected to multirole with channel: " + multiroleChannel);
                                boolean endOfRequestReached = false;
                                for (HttpObject message : messages) {
                                    endOfRequestReached = writeMessageToMultiroleAndMaybeCloseChannel(message, true);
                                }
                                messages.clear();
                                if (!endOfRequestReached)
//                                    connected.set(true);
                                    connected = true;
                        } else {
                            notConnected = true;
//                            notConnected.set(true);
                            log.log(Level.WARNING, "connection to multirole failed, writing ");
                            for (HttpObject message : messages) {
                                ReferenceCountUtil.release(message);
                            }
                            write503Response(ctx);
                        }
                    }
                });
            }
            if (msg instanceof HttpObject) {
                if (!connected) {
//                if (!connected.get()) {
                    messages.add((HttpObject) msg);
                } else if (!notConnected) {
//                } else if (!notConnected.get()) {
                    ReferenceCountUtil.release(msg);
                } else {
                    writeMessageToMultiroleAndMaybeCloseChannel((HttpObject)msg, false);
                }
            } else {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    private boolean writeMessageToMultiroleAndMaybeCloseChannel(HttpObject message, Boolean accumulated) {
        ChannelFuture writeFuture = multiroleChannel.writeAndFlush(message);
        String qualifier = accumulated ? "" : "accumulated ";
        if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "sending " +qualifier + "http message to multirole: " + multiroleChannel + " " + message);
        if (message instanceof LastHttpContent) {
            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "request send about to complete: " + multiroleChannel + " " + message);
            writeFuture
                    .addListener(new GenericFutureListener<Future<? super Void>>() {
                        @Override
                        public void operationComplete(Future<? super Void> future) {
                            if (log.isLoggable(Level.FINEST)) log.log(Level.FINEST, "request send complete, marking channel as 'not connected': " + multiroleChannel + " " + message);
                            connected = false;
                            connecting = false;
                            notConnected = false;
//                            connected.set(false);
//                            connecting.set(false);
//                            notConnected.set(false);
                            multiroleChannel = null;
                        }
                    });
            return true;
        }
        return false;
    }

    private void write503Response(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(503, "local proxy can't connect to multirole"),
                Unpooled.EMPTY_BUFFER);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "channel exception: " + ctx.channel(), cause);
        ctx.close();
    }

}