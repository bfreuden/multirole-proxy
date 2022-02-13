package com.kairntech.multiroleproxy;

import com.kairntech.multiroleproxy.local.LocalProxy;
import com.kairntech.multiroleproxy.remote.RemoteProxy;
import org.apache.commons.cli.*;

public class Main {

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("remote-port", true, "remote proxy port");
        options.addOption("remote-host", true, "remote proxy host");
        ProxyConfig config = null;
        try {
            CommandLine cmd = parser.parse(options, args);
            String[] remainingArgs = cmd.getArgs();
            if (remainingArgs.length == 0) {
                System.err.println("no target provided on command-line, expected one of 'remote' or 'local'");
                System.exit(1);
            }
            String target = remainingArgs[0];
            if (target.equals("remote")) {
                RemoteProxy remoteProxy = new RemoteProxy(
                        new ProxyConfig()
                                .setPort(Integer.parseInt(cmd.getOptionValue("remote-port", "9080")))
                );
                remoteProxy.start();
            } else if (target.equals("local")) {
                LocalProxy localProxy = new LocalProxy(
                        new ProxyConfig()
                                .setHost(cmd.getOptionValue("remote-host", "localhost"))
                                .setPort(Integer.parseInt(cmd.getOptionValue("remote-port", "9080")))
                );
                localProxy.start();
            } else {
                System.err.println("unknown target: " + target + ", expected one of 'remote' or 'local'");
                System.exit(1);
            }
        } catch (ParseException e) {
            System.err.println("unable to parse command-line: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("multirole-proxy", options);
            System.exit(1);
        } catch (Throwable e) {
            System.err.println("error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
