package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.Clients;
import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Peers {

    public static final AttributeKey<String> REQUEST_UUID_ATTRIBUTE = AttributeKey.newInstance("requestUUID");
    public static final AttributeKey<Clients> CLIENTS_ATTRIBUTE = AttributeKey.newInstance("clients");
    public static final AttributeKey<Peers> PEERS_ATTRIBUTE = AttributeKey.newInstance("peers");
    public static final AttributeKey<String> PEERS_ID_ATTRIBUTE = AttributeKey.newInstance("peerId");
    private JSONObject spec;
    private HashMap<String, Peer> id2peers = new HashMap<>();
    private HashMap<String, Peer> uriToPeer = new HashMap<>();

    public synchronized void peerConnected(String peerId, Channel ch) {
        id2peers.put(peerId, new Peer(ch));
    }

    public synchronized void peerDisconnected(Channel ch) {
        uriToPeer.entrySet().removeIf(it -> it.getValue().getChannel() == ch);
        id2peers.entrySet().removeIf(next -> next.getValue().getChannel() == ch);
        computeAggregatedSpec();
    }

    private void computeAggregatedSpec() {
        JSONObject root = new JSONObject();
        root.put("openapi", "3.0.2");

        JSONObject info = new JSONObject();
        info.put("title", "Multirole server API doc");
        info.put("description", "Multirole server API documentation");
        info.put("version", "1.0");
        root.put("info", info);

        JSONObject paths = new JSONObject();
        root.put("paths", paths);

        JSONObject components = new JSONObject();
        root.put("components", components);

        JSONObject schemas = new JSONObject();
        components.put("schemas", schemas);

        id2peers.values().forEach( peer -> peer.enrichAggregatedSpec(paths, schemas));
        this.spec = root;
    }

    public synchronized void declareSpec(String peerId, String multiroleId, String specMd5, OpenAPISpecParser.OpenAPISpec spec) {
        for (String path : spec.getPaths().keySet())
            uriToPeer.put(path, id2peers.get(peerId));
        id2peers.get(peerId).declareSpec(multiroleId, specMd5, spec);
        computeAggregatedSpec();
    }

    public synchronized void undeclareSpec(String peerId, String multiroleId) {
        id2peers.get(peerId).undeclareSpec(multiroleId);
        computeAggregatedSpec();
    }

    public synchronized ByteBuf getSpec(ByteBufAllocator alloc) throws IOException {
        ByteBuf buffer = alloc.buffer(64*1024);
        try (Writer out = new OutputStreamWriter(new ByteBufOutputStream(buffer), StandardCharsets.UTF_8)) {
            spec.writeJSONString(out);
        }
        return buffer;
    }

    public synchronized Pair<Peer, String> getPeerAndMultiroleID(String uri) {
        if (id2peers.isEmpty())
            return null;
        Peer peer = uriToPeer.get(uri);
        if (peer == null)
            if (uri.startsWith("/docs"))
                return new Pair(id2peers.values().iterator().next(), "*"); // static resource, so any peer will do
            else
                return null;
        else
            return new Pair(peer, peer.getMultirole(uri));
    }


}
