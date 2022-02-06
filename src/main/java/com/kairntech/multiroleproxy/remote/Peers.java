package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Peers {

    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    public static final AttributeKey<Peer> PEER_ATTRIBUTE = AttributeKey.newInstance("peer");
    public static final AttributeKey<String> PEERS_ID_ATTRIBUTE = AttributeKey.newInstance("peerId");
    private OpenAPISpecParser.OpenAPISpec spec;
    private HashMap<String, Peer> id2peers = new HashMap<>();

    public synchronized Sequencer getPeerSequencer(String peerId) {
        return  peerId != null ? id2peers.get(peerId).getSequencer() : null;
    }

    public synchronized void peerConnected(String peerId, Channel ch) {
        id2peers.put(peerId, new Peer(ch));
    }

    public synchronized void peerDisconnected(Channel ch) {
        id2peers.entrySet().removeIf(next -> next.getValue().getChannel() == ch);
    }

    public synchronized void declareSpec(String peerId, String multiroleId, String specMd5, OpenAPISpecParser.OpenAPISpec spec) {
        this.spec = spec;
    }

    public synchronized ByteBuf getSpec(ByteBufAllocator alloc) throws IOException {
        ByteBuf buffer = alloc.buffer(64*1024);
        try (Writer out = new OutputStreamWriter(new ByteBufOutputStream(buffer), StandardCharsets.UTF_8)) {
            spec.json.writeJSONString(out);
        }
        return buffer;
    }

    public synchronized Peer getPeer(String uri) {
        if (id2peers.isEmpty()) {
            return null;
        } else {
            return id2peers.values().iterator().next();
        }
    }
}
