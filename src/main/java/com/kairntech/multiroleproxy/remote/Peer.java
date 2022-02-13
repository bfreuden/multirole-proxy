package com.kairntech.multiroleproxy.remote;

import com.kairntech.multiroleproxy.util.OpenAPISpecParser;
import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.channel.Channel;
import org.json.simple.JSONObject;

import java.util.HashMap;

public class Peer {

    private final Sequencer sequencer;
    private final HashMap<String, OpenAPISpecParser.OpenAPISpec> multiroleSpecs = new HashMap<>();
    private final HashMap<String, String> uriToMultirole = new HashMap<>();

    public Peer(Channel ch) {
        this.sequencer = new Sequencer(ch);
    }

    public Channel getChannel() {
        return sequencer.getUpstreamChannel();
    }

    public Sequencer getSequencer() {
        return this.sequencer;
    }

    public synchronized void declareSpec(String multiroleId, String specMd5, OpenAPISpecParser.OpenAPISpec spec) {
        multiroleSpecs.put(multiroleId, spec);
        for (String path : spec.getPaths().keySet())
            uriToMultirole.put(path, multiroleId);

    }

    public synchronized void undeclareSpec(String multiroleId) {
        multiroleSpecs.remove(multiroleId);
        uriToMultirole.entrySet().removeIf(it -> it.getValue().equals(multiroleId));
    }


    public synchronized Object getMultirole(String uri) {
        return uriToMultirole.get(uri);
    }

    public void enrichAggregatedSpec(JSONObject allPaths, JSONObject allSchemas) {
        for (OpenAPISpecParser.OpenAPISpec spec: multiroleSpecs.values()) {
            spec.getSchemas().forEach(allSchemas::put);
            spec.getPaths().forEach(allPaths::put);
        }
    }
}
