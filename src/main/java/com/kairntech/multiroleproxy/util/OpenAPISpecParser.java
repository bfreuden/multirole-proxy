package com.kairntech.multiroleproxy.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class OpenAPISpecParser {

    public static class OpenAPISpec {

        public final JSONObject json;
        public final String md5sum;
        public final String jsonString;

        public OpenAPISpec(JSONObject json, String jsonString, String md5sum) {
            this.json = json;
            this.jsonString = jsonString;
            this.md5sum = md5sum;
        }
    }

    public static OpenAPISpec parse(ByteBuf content, boolean parseOnly) throws NoSuchAlgorithmException, IOException {
        if (parseOnly) {
            String jsonString = content.toString(StandardCharsets.UTF_8);
            JSONObject parse = (JSONObject) JSONValue.parse(jsonString);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(jsonString.getBytes(StandardCharsets.UTF_8));
            String md5sum = Base64.getEncoder().encodeToString(digest);
            return new OpenAPISpec(parse, jsonString, md5sum);
        } else {
            try (InputStreamReader reader = new InputStreamReader(new ByteBufInputStream(content), StandardCharsets.UTF_8)) {
                JSONObject parse = (JSONObject) JSONValue.parse(reader);
                return new OpenAPISpec(parse, null, null);
            }
        }
    }
}
