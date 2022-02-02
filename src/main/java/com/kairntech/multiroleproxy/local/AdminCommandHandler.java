package com.kairntech.multiroleproxy.local;


import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class AdminCommandHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = Logger.getLogger( AdminCommandHandler.class.getSimpleName().replace("Handler", "") );

    private final AdminServer multiroles;

    public AdminCommandHandler(AdminServer multiroles) {
        this.multiroles = multiroles;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> params = queryStringDecoder.parameters();
            String command = null;
            List<String> arguments = null;
            if (!params.isEmpty()) {
                for (Entry<String, List<String>> p: params.entrySet()) {
                    String key = p.getKey();
                    List<String> vals = p.getValue();
                    if (key.equals("command") && vals.size() != 0) {
                        command = vals.get(0);
                    } else if (key.equals("arguments")) {
                        arguments = vals;
                    }

                }
            }
            if (command == null) {
                writeResponse(request, ctx, "missing 'command' query param" + System.lineSeparator(), true);
            } else {
                if (command.equals("list-multiroles")) {
                    List<String> names = multiroles.getMultiroleAsStrings();
                    if (names.isEmpty()) {
                        writeResponse(request, ctx, "no multirole server registered" + System.lineSeparator(), false);
                    } else {
                        StringJoiner stringJoiner = new StringJoiner(System.lineSeparator());
                        stringJoiner.add("registered multirole servers:");
                        for (String name : names) {
                            stringJoiner.add("- " + name);
                        }
                        writeResponse(request, ctx, stringJoiner + System.lineSeparator(), false);
                    }
                } else if (command.equals("add-multirole") || command.equals("add-multiroles")) {
                    if (arguments.isEmpty())
                        writeResponse(request, ctx, "missing 'arguments' query param" + System.lineSeparator(), true);
                    else {
                        try {
                            multiroles.addMultiroleServers(arguments);
                            writeResponse(request, ctx, "done" + System.lineSeparator(), true);
                        } catch (Exception ex) {
                            writeResponse(request, ctx, "error adding multirole server(s): " + ex.getMessage() + System.lineSeparator(), true);
                        }
                    }
                } else if (command.equals("delete-multirole") || command.equals("delete-multiroles")) {
                    if (arguments.isEmpty())
                        writeResponse(request, ctx, "missing 'arguments' query param" + System.lineSeparator(), true);
                    else {
                        try {
                            multiroles.deleteMultiroleServers(arguments);
                            writeResponse(request, ctx, "done" + System.lineSeparator(), true);
                        } catch (Exception ex) {
                            writeResponse(request, ctx, "error deleting multirole server(s): " + ex.getMessage() + System.lineSeparator(), true);
                        }
                    }
                } else {
                    writeResponse(request, ctx, "unknown command: " + command + System.lineSeparator(), true);
                }
            }
        } else {
            log.log(Level.WARNING, "unexpected message: " + msg);
        }

    }

    private void writeResponse(HttpObject currentObj, ChannelHandlerContext ctx, String body, boolean error) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, !error && currentObj.decoderResult().isSuccess() ? OK : BAD_REQUEST,
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);

    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "", cause);
        ctx.close();
    }
}