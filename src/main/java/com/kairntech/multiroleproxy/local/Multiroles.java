package com.kairntech.multiroleproxy.local;

import com.kairntech.multiroleproxy.util.Sequencer;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;

import java.util.*;
import java.util.logging.Logger;

public class Multiroles {

    public static final AttributeKey<Multiroles> MULTIROLES_ATTRIBUTE = AttributeKey.newInstance("multiroles");
    public static final AttributeKey<Multirole> MULTIROLE_ATTRIBUTE = AttributeKey.newInstance("multirole");

    private static final Logger log = Logger.getLogger(Multiroles.class.getSimpleName());

    private final EventLoopGroup group;
    private final HashMap<String, Multirole> servers = new HashMap<>();
    private final MultiroleChangeNotifier multiroleChangeNotifier;
    private Sequencer sequencer;

    public Multiroles(EventLoopGroup group, MultiroleChangeNotifier multiroleChangeNotifier) {
        this.group = group;
        this.multiroleChangeNotifier = multiroleChangeNotifier;
    }

    public synchronized void addServer(String host, Integer port) {
        log.info("adding multirole server: " + host+ ":" + port);
        String id = Multirole.getId(host, port);
        if (servers.containsKey(id))
            throw new IllegalArgumentException("multirole already exists");
        Multirole multirole = new Multirole(group, multiroleChangeNotifier, host, port);
        multirole.setSequencer(sequencer);
        servers.put(id, multirole);
        log.info("multirole server added: " + host+ ":" + port);
        multirole.connect();
    }

    public synchronized Multirole getServer(String id) {
        if (id.equals("*") && !servers.isEmpty())
            return servers.values().iterator().next();
        return this.servers.get(id);
    }

    public synchronized void deleteServer(String host, Integer port) {
        log.info("deleting multirole server: " + host+ ":" + port);
        String id = Multirole.getId(host, port);
        Multirole multirole = servers.get(id);
        if (multirole == null)
            throw new NoSuchElementException("multirole does not exists");
        log.info("multirole server deleted: " + host+ ":" + port);
        multirole.notifyRemoved();
        servers.remove(id);
    }

    public synchronized HashSet<Multirole> getServers() {
        return new HashSet<>(this.servers.values());
    }

    public synchronized void setSequencer(Sequencer sequencer) {
        this.sequencer = sequencer;
        for (Multirole multirole: servers.values())
            multirole.setSequencer(sequencer);
    }

    public Sequencer getSequencer() {
        return this.sequencer;
    }
}
