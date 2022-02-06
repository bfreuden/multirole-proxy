package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class Peers {

    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    public static final AttributeKey<String> PEERS_ID_ATTRIBUTE = AttributeKey.newInstance("peerId");
    private Channel channel;
    private OpenAPISpecParser.OpenAPISpec spec;

    public Channel getPeerChannel() {
        return this.channel;
    }

    public void peerConnected(String peerId, Channel ch) {
        this.channel = ch;
    }

    public void peerDisconnected(Channel ch) {
        this.channel = null;
    }

    public void declareSpec(String peerId, String multiroleId, String specMd5, OpenAPISpecParser.OpenAPISpec spec) {
        this.spec = spec;
    }

    public ByteBuf getSpec(ByteBufAllocator alloc) throws IOException {
        ByteBuf buffer = alloc.buffer(1);
        try (Writer out = new OutputStreamWriter(new ByteBufOutputStream(buffer), StandardCharsets.UTF_8)) {
            spec.json.writeJSONString(out);
        }
        return buffer;
    }
}
