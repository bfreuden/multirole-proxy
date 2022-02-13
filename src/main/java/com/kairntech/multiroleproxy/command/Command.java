package com.kairntech.multiroleproxy.command;

import com.kairntech.multiroleproxy.util.SimpleHttpClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;

public class Command {

    public static void main(String[] args) {
        Object monitor = new Object();
        boolean[] responseAvailable = new boolean[1];
        try {
            if (args.length == 0)
                throw new IllegalArgumentException("");
            if (args[0].equals("status") || args[0].equals("list-servers")) {
                NioEventLoopGroup group = new NioEventLoopGroup(1);
                SimpleHttpClient client = new SimpleHttpClient(group, "localhost", 30000);
                client.get("/" + args[0]).send(res -> {
                    if (res.success()) {
                        System.out.println(res.result().content().toString(StandardCharsets.UTF_8));
                    } else {
                        System.err.println("error: " + res.cause().getMessage());
                    }
                    synchronized (monitor) {
                        responseAvailable[0] = true;
                        monitor.notify();
                    }
                });
                synchronized (monitor) {
                    if (!responseAvailable[0])
                        monitor.wait();
                }
            } else if (args[0].equals("add") || args[0].equals("remove") || args[0].equals("connect") || args[0].equals("disconnect")) {
                JSONObject body = new JSONObject();
                String uri = "/";
                if (args[0].equals("add")) {
                    uri = "/add-multirole";
                    if (args.length != 3)
                        throw new IllegalArgumentException("incorrect number of arguments for add command");
                    if (!args[1].contains(":"))
                        throw new IllegalArgumentException("expecting host:port after add");
                    String[] split = args[1].trim().split(":");
                    if (!split[0].isEmpty())
                        body.put("host", split[0]);
                    body.put("port", split[1]);
                    body.put("paths", args[2]);
                } else if (args[0].equals("remove")) {
                    uri = "/remove-multirole";
                    if (args.length < 2)
                        throw new IllegalArgumentException("incorrect number of arguments for remove command");
                    if (!args[1].contains(":"))
                        throw new IllegalArgumentException("expecting host:port after remove");
                    String[] split = args[1].trim().split(":");
                    if (!split[0].isEmpty())
                        body.put("host", split[0]);
                    body.put("port", split[1]);
                } else if (args[0].equals("connect")) {
                    uri = "/connect";
                    if (args.length != 3)
                        throw new IllegalArgumentException("incorrect number of arguments for connect command");
                    body.put("duration", args[1] + " " + args[2]);
                } else if (args[0].equals("disconnect")) {
                    uri = "/disconnect";
                }
                NioEventLoopGroup group = new NioEventLoopGroup(1);
                SimpleHttpClient client = new SimpleHttpClient(group, "localhost", 30000);
                ByteBuf byteBuf = Unpooled.copiedBuffer(body.toJSONString(), StandardCharsets.UTF_8);
                client.post(uri, byteBuf).send(res -> {
                    if (res.success()) {
                        System.out.println(res.result().content().toString(StandardCharsets.UTF_8));
                    } else {
                        System.err.println("error: " + res.cause().getMessage());
                    }
                    synchronized (monitor) {
                        responseAvailable[0] = true;
                        monitor.notify();
                    }
                });
                synchronized (monitor) {
                    if (!responseAvailable[0])
                        monitor.wait();
                }
            } else {
                throw new IllegalArgumentException("invalid command: " + args[0]);
            }
        } catch (InterruptedException e) {
            System.err.println("interrupted while waiting for local proxy response");
        } catch (IllegalArgumentException ex) {
            if (!ex.getMessage().isEmpty()) {
                System.err.println(ex.getMessage());
                System.out.println();
                System.out.println("run without command-line argument to show usage");
            } else {
                System.err.println("usage:");
                System.out.println();
                System.out.println("start the remote proxy:");
                System.out.println("  multirole-proxy remote --port <remote-port>");
                System.out.println();
                System.out.println("start the local proxy:");
                System.out.println("  multirole-proxy local --host <remote-host> --port <remote-port>");
                System.out.println();
                System.out.println("status of the local proxy:");
                System.out.println("  multirole-proxy status");
                System.out.println();
                System.out.println("connect local proxy to remote proxy:");
                System.out.println("  multirole-proxy connect 3 hours");
                System.out.println();
                System.out.println("disconnect local proxy from remote proxy:");
                System.out.println("  multirole-proxy disconnect");
                System.out.println();
                System.out.println("add multirole server to local proxy (with a regex of paths to expose):");
                System.out.println("  multirole-proxy add localhost:12008 /convert/tika");
                System.out.println();
                System.out.println("remove multirole server from local proxy:");
                System.out.println("  multirole-proxy remove localhost:12008");
                System.out.println();
                System.out.println("list multirole servers added to local proxy:");
                System.out.println("  multirole-proxy list");
            }
            System.exit(1);
        } finally {
            System.exit(0);
//            group.shutdownGracefully();
        }

    }
}
