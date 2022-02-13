package com.kairntech.multiroleproxy;

import com.kairntech.multiroleproxy.command.Command;
import com.kairntech.multiroleproxy.local.LocalProxy;
import com.kairntech.multiroleproxy.remote.RemoteProxy;
import org.apache.commons.cli.*;

public class Main {

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("port", true, "remote proxy port");
        options.addOption("host", true, "remote proxy host");
        ProxyConfig config = null;
        try {
            CommandLine cmd = parser.parse(options, args);
            String[] remainingArgs = cmd.getArgs();
//            if (remainingArgs.length == 0) {
//                System.err.println("no target provided on command-line, expected one of 'remote', 'local'");
//                System.exit(1);
//            }
            String target = remainingArgs.length == 1 ? remainingArgs[0] : null;
            if ("remote".equals(target)) {
                RemoteProxy remoteProxy = new RemoteProxy(
                        new ProxyConfig()
                                .setPort(Integer.parseInt(cmd.getOptionValue("port", "9080")))
                );
                remoteProxy.start();
            } else if ("local".equals(target)) {
                LocalProxy localProxy = new LocalProxy(
                        new ProxyConfig()
                                .setHost(cmd.getOptionValue("host", "localhost"))
                                .setPort(Integer.parseInt(cmd.getOptionValue("port", "9080")))
                );
                localProxy.start();
            } else {
                Command.main(remainingArgs);
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
