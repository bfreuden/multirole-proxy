package com.kairntech.multiroleproxy.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

public class OpenAPISpecParser {

    public static class OpenAPISpec {

        public final JSONObject json;
        public final String md5sum;
        public final String jsonString;


        public OpenAPISpec(JSONObject json) {
            this.json = json;
            this.md5sum = null;
            this.jsonString = null;
        }

        public OpenAPISpec(JSONObject json, String jsonString, String md5sum) {
            this.json = json;
            this.jsonString = jsonString;
            this.md5sum = md5sum;
        }

        public Set<String> getPaths() {
            return OpenAPISpecParser.getPaths(json);
        }


//        public Set<String, String> getPathsWithVerbs() {
//            return OpenAPISpecParser.getPaths(json);
//        }

    }

    private static Set<String> getPaths(JSONObject obj) {
        JSONObject paths = (JSONObject) obj.get("paths");
        if (paths == null)
            return Collections.emptySet();
        Set<String> set = (Set<String>) paths.keySet();
        return set == null ? Collections.emptySet() : set;
    }

    public static OpenAPISpec parse(ByteBuf content) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new ByteBufInputStream(content), StandardCharsets.UTF_8)) {
            JSONObject parse = (JSONObject) JSONValue.parse(reader);
            return new OpenAPISpec(parse);
        }
    }

    public static OpenAPISpec parse(ByteBuf content, boolean parseOnly) throws NoSuchAlgorithmException, IOException {
        if (parseOnly) {
            return  parse(content);
        } else {
            String jsonString = content.toString(StandardCharsets.UTF_8);
            JSONObject parse = (JSONObject) JSONValue.parse(jsonString);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(jsonString.getBytes(StandardCharsets.UTF_8));
            String md5sum = Base64.getEncoder().encodeToString(digest);
            return new OpenAPISpec(parse, jsonString, md5sum);
        }
    }

}
