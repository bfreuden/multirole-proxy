package com.kairntech.multiroleproxy.util;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

public class SimpleHttpRequest {

    private final FullHttpRequest request;
    private final QueryStringDecoder qqueryStringDecoder;

    public FullHttpRequest getRequest() {
        return request;
    }

    public QueryStringDecoder getQqueryStringDecoder() {
        return qqueryStringDecoder;
    }

    public SimpleHttpRequest(FullHttpRequest request, QueryStringDecoder queryStringDecoder) {
        this.request = request;
        this.qqueryStringDecoder = queryStringDecoder;
    }
}
