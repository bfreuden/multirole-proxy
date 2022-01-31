package com.kairntech.multiroleproxy.remote;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.kairntech.multiroleproxy.remote.RouterHandler.REGISTER_CLIENT_URI;

public class RemoteProxyTest {

    @Test
    public void test() {

        Peers peers = new Peers();

        EmbeddedChannel peerChannel = new EmbeddedChannel(RemoteProxyChannelInitializer.handlers());
        RemoteProxyChannelInitializer.addAttributes(peerChannel, peers);

        List<ByteBuf> registerClientReqBuffers =
                encodeRequest(
                    new DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1,
                        HttpMethod.GET,
                        REGISTER_CLIENT_URI
                    )
                );
        registerClientReqBuffers.forEach(peerChannel::writeInbound);

        Object obj = peerChannel.readOutbound();
        DefaultHttpResponse defaultHttpResponse = decodeResponse((ByteBuf) obj);
        Assert.assertEquals(HttpResponseStatus.OK, defaultHttpResponse.status());

        // direction switcher applied

        Assert.assertTrue(
                peerChannel.writeOutbound(
                        new DefaultFullHttpRequest(
                            HttpVersion.HTTP_1_1,
                            HttpMethod.POST,
                            "/convert/toto", Unpooled.wrappedBuffer("hello world!".getBytes(StandardCharsets.UTF_8))
                        )
                )
        );
        System.out.println();
    }

    private List<ByteBuf> encodeRequest(HttpRequest req) {
        EmbeddedChannel httpClientChannel = new EmbeddedChannel(new HttpRequestEncoder());
        httpClientChannel.writeOutbound(
                req);
        ByteBuf byteBuf;
        ArrayList<ByteBuf> byteBuffers = new ArrayList<>();
        while ((byteBuf=httpClientChannel.readOutbound()) != null) {
            byteBuffers.add(byteBuf);
        }
        return byteBuffers;
    }

    private DefaultHttpResponse decodeResponse(ByteBuf response) {
        EmbeddedChannel httpClientChannel = new EmbeddedChannel(new HttpResponseDecoder());
        httpClientChannel.writeInbound(
                response);
        return httpClientChannel.readInbound();
    }
}
