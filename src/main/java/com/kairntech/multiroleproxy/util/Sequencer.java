package com.kairntech.multiroleproxy.util;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public class Sequencer {

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
    private HashMap<ChannelHandlers, LinkedList<HttpMessage>> bufferedMessages = new HashMap<>();
    private ChannelHandlers inProgress;
    private boolean closed;

    public Sequencer(Channel upstream) {
        this.upstream = upstream;
        upstream.closeFuture().addListener((closeFuture) -> {
            setClosed();
        });
    }

    private synchronized void setClosed() {
        this.closed = true;
        // signal current stream that upstream is closed
        if (inProgress != null)
            inProgress.notifyUpstreamUnexpectedlyClosed();
        // signal pending streams and drop buffered data
        for (Map.Entry<ChannelHandlers, LinkedList<HttpMessage>> entry : bufferedMessages.entrySet()) {
            for (HttpMessage message : entry.getValue()) {
                // drop buffered data
                ReferenceCountUtil.release(message);
                // signal pending stream that upstream is closed
                if (message instanceof LastHttpContent) {
                    entry.getKey().notifyUpstreamUnexpectedlyClosed();
                }
            }
        }
    }

    public synchronized void write(ChannelHandlers tuple, HttpMessage message) {
        if (closed) {
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
                // signal that write is done
                tuple.notifyWriteComplete();
                // the room is open for someone else
                inProgress = null;
                // maybe wake up a pending write
                maybeWakeUpPending();
            }
        } else {
            // pause reads
            tuple.channel.config().setAutoRead(false);
            // buffer incoming messages
            LinkedList<HttpMessage> httpMessages = bufferedMessages.computeIfAbsent(tuple, (c) -> new LinkedList<>());
            httpMessages.add(message);
        }
    }

    private void maybeWakeUpPending() {
        // maybe wake up pending channel
        if (!bufferedMessages.isEmpty()) {
            // take the first one
            // this will be the new in progress
            ChannelHandlers tuple = bufferedMessages.keySet().iterator().next();
            // send its buffered messages
            LinkedList<HttpMessage> messages = bufferedMessages.remove(tuple);
            for (HttpMessage bufferedMessage : messages)
                write(tuple, bufferedMessage);
            // resume reads
            tuple.channel.config().setAutoRead(true);
        }
    }

    private boolean writeMessage(HttpMessage message) {
        boolean lastWritten = false;
        if (message instanceof HttpRequest) {
            if (message instanceof LastHttpContent) {
                upstream.writeAndFlush(message);
                lastWritten = true;
            } else {
                upstream.write(message);
            }
        } else if (message instanceof HttpResponse) {
            if (message instanceof LastHttpContent) {
                upstream.writeAndFlush(message);
                lastWritten = true;
            } else {
                upstream.write(message);
            }
        }
        if (!lastWritten && message instanceof LastHttpContent) {
            upstream.writeAndFlush(message);
            lastWritten = true;
        }
        return lastWritten;
    }

}
