package com.kairntech.multiroleproxy.util;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static com.kairntech.multiroleproxy.util.MaybeLog.maybeLogFinest;

public class Sequencer {

    private static final Logger log = Logger.getLogger( Sequencer.class.getSimpleName() );

    public Channel getUpstreamChannel() {
        return this.upstream;
    }

    public static class ChannelHandlers {

        private final Channel channel;
        private final Runnable upstreamClosedHandler;
        private final Runnable writeCompleteHandler;

        public ChannelHandlers(Channel channel, Runnable upstreamClosedHandler, Runnable writeCompleteHandler) {
            this.channel = channel;
            this.upstreamClosedHandler = upstreamClosedHandler;
            this.writeCompleteHandler = writeCompleteHandler;
        }

        private void notifyUpstreamUnexpectedlyClosed() {
            if (upstreamClosedHandler != null) {
                try {
                    upstreamClosedHandler.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    //FIXME log
                }
            }
        }

        private void notifyWriteComplete() {
            if (writeCompleteHandler != null) {
                try {
                    writeCompleteHandler.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    //FIXME log
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChannelHandlers that = (ChannelHandlers) o;
            return channel.equals(that.channel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel);
        }


    }

    private Channel upstream;
    private HashMap<ChannelHandlers, LinkedList<HttpObject>> bufferedMessages = new HashMap<>();
    private ChannelHandlers inProgress;
    private boolean upstreamClosed;
    private String expectedContentLength;
    private int writtenContentLength = 0;

    public Sequencer(Channel upstream) {
        this.upstream = upstream;
        upstream.closeFuture().addListener((closeFuture) -> {
            setUpstreamClosed();
        });
    }

    private synchronized void setUpstreamClosed() {
        this.upstreamClosed = true;
        // signal current stream that upstream is closed
        if (inProgress != null)
            inProgress.notifyUpstreamUnexpectedlyClosed();
        // signal pending streams and drop buffered data
        for (Map.Entry<ChannelHandlers, LinkedList<HttpObject>> entry : bufferedMessages.entrySet()) {
            for (HttpObject message : entry.getValue()) {
                // drop buffered data
                ReferenceCountUtil.release(message);
                // signal pending stream that upstream is closed
                if (message instanceof LastHttpContent) {
                    entry.getKey().notifyUpstreamUnexpectedlyClosed();
                }
            }
        }
    }

    public synchronized void write(ChannelHandlers tuple, HttpObject message) {
        if (upstreamClosed) {
            log.warning("upstream channel closed, discarding the message: " + tuple.channel + " " + message);
            // simply drop the message
            ReferenceCountUtil.release(message);
            // if it is the last one, call a channel closed handler
            if (message instanceof LastHttpContent)
                tuple.notifyUpstreamUnexpectedlyClosed();
        } else if (inProgress == null || inProgress == tuple) {
            // first arrived has the right to write into the sequenced channel
            inProgress = tuple;
            boolean lastWritten = writeMessage(message);
            if (lastWritten) {
                maybeLogFinest(log, () -> "handling last write: " + tuple.channel + " " + message);
                maybeLogFinest(log, () -> "expected content length/written content length: " + expectedContentLength + "/" + writtenContentLength + " " + tuple.channel + " " + message);
                writtenContentLength = 0;
                expectedContentLength = "";
            } else {
                maybeLogFinest(log, () -> "handling write: " + tuple.channel + " " + message);
            }
            if (lastWritten) {
                // signal that write is done
                tuple.notifyWriteComplete();
                // the room is open for someone else
                inProgress = null;
                // maybe wake up a pending write
                maybeWakeUpPending();
            }
        } else {
            maybeLogFinest(log, () -> "buffering write for later: " + tuple.channel + " " + message);
            // pause reads
            tuple.channel.config().setAutoRead(false);
            // buffer incoming messages
            LinkedList<HttpObject> HttpObjects = bufferedMessages.computeIfAbsent(tuple, (c) -> new LinkedList<>());
            HttpObjects.add(message);
        }
    }

    private void maybeWakeUpPending() {
        // maybe wake up pending channel
        if (!bufferedMessages.isEmpty()) {
            // take the first one
            // this will be the new in progress
            ChannelHandlers tuple = bufferedMessages.keySet().iterator().next();
            maybeLogFinest(log, () -> "un-buffering messages of pending channel: " + tuple.channel);
            // send its buffered messages
            LinkedList<HttpObject> messages = bufferedMessages.remove(tuple);
            for (HttpObject bufferedMessage : messages)
                write(tuple, bufferedMessage);
            // resume reads
            maybeLogFinest(log, () -> "waking up pending channel: " + tuple.channel);
            tuple.channel.config().setAutoRead(true);
        }
    }

    private boolean writeMessage(HttpObject message) {
        boolean lastWritten = false;
        if (message instanceof HttpContent) {
            HttpContent content = (HttpContent) message;
            writtenContentLength += content.content().readableBytes();
        }
        if (message instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) message;
            expectedContentLength = request.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        } else if (message instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) message;
            expectedContentLength = response.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        }
        if (message instanceof LastHttpContent) {
            upstream.writeAndFlush(message);
            lastWritten = true;
        } else {
            upstream.write(message);
        }
        return lastWritten;
    }

}
