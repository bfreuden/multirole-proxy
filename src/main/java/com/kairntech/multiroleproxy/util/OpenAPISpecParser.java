package com.kairntech.multiroleproxy.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class OpenAPISpecParser {

    public static class OpenAPISpec {

        private static Pattern MULTIROLE_PATH = Pattern.compile("^/(?:convert|process|annotate|format|segment)/[^/]+$");

        public final JSONObject json;
        public final String md5sum;
        public final String jsonString;
        private HashMap<String, JSONObject> paths;
        private JSONArray tags;
        private HashMap<String, JSONObject> schemas;

        public OpenAPISpec(JSONObject json) {
            this.json = json;
            this.jsonString = analyze(null, null);
            this.md5sum = null;
        }

        public OpenAPISpec(JSONObject json, String jsonString, String targetPathsRegex) throws NoSuchAlgorithmException {
            this.json = json;
            this.jsonString = analyze(jsonString, targetPathsRegex);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(this.jsonString.getBytes(StandardCharsets.UTF_8));
            this.md5sum = Base64.getEncoder().encodeToString(digest);
        }

        private String analyze(String jsonString, String targetPathsRegex) {
            Pattern targetPathsPattern = targetPathsRegex == null ? null : Pattern.compile(targetPathsRegex);
            this.paths = new HashMap<>();
            this.schemas = new HashMap<>();
            this.tags = (JSONArray)json.get("tags");
            JSONObject pathsJson = (JSONObject) json.get("paths");
            if (pathsJson == null)
                return jsonString;
            JSONObject schemasJson = (JSONObject) json.get("components");
            if (schemasJson != null)
                schemasJson = (JSONObject) schemasJson.get("schemas");
            Set<String> uris = pathsJson.keySet();
            Set<String> tags = new HashSet<>();
            for (String uri: uris) {
                if (MULTIROLE_PATH.matcher(uri).matches() && (targetPathsPattern == null || targetPathsPattern.matcher(uri).matches())) {
                    JSONObject path = (JSONObject) pathsJson.get(uri);
                    paths.put(uri, path);
                    if (schemasJson != null)
                        findUsedSchemas(schemasJson, path, tags);
                }
            }

            // transitively find all schemas
            int nbSchemas = 0;
            int newNbSchemas = schemas.size();
            do {
                nbSchemas = newNbSchemas;
                HashSet<String> schemaNames = new HashSet<>(schemas.keySet());
                for (String schemaName: schemaNames) {
                    findUsedSchemas(schemasJson, schemas.get(schemaName), null);
                }
                newNbSchemas = schemas.size();
            } while (nbSchemas != newNbSchemas);

            // restrict the spec (paths and schemas) to target paths
            if (targetPathsRegex != null) {
                if (this.tags != null)
                    this.tags.removeIf(tag -> !tags.contains(((JSONObject)tag).get("name")));
                pathsJson.entrySet().removeIf(entry -> !paths.containsKey(((Map.Entry)entry).getKey()));
//                Iterator<Map.Entry> iterator = pathsJson.entrySet().iterator();
//                while (iterator.hasNext()) {
//                    Map.Entry next = iterator.next();
//                    String path = (String) next.getKey();
//                    if (!paths.containsKey(path))
//                        iterator.remove();
//                }
                if (schemasJson != null) {
                    schemasJson.entrySet().removeIf(entry -> !schemas.containsKey(((Map.Entry)entry).getKey()));
//                    iterator = schemasJson.entrySet().iterator();
//                    while (iterator.hasNext()) {
//                        Map.Entry next = iterator.next();
//                        String schemaName = (String) next.getKey();
//                        if (!schemas.containsKey(schemaName))
//                            iterator.remove();
//                    }
                }
                StringWriter stringWriter = new StringWriter();
                try {
                    json.writeJSONString(stringWriter);
                    stringWriter.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return stringWriter.toString();
            } else {
                return jsonString;
            }
        }

        private void findUsedSchemas(JSONObject schemasJson, Object jsonThing, Set<String> tags) {
            if (jsonThing == null)
                return;
            if (jsonThing instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) jsonThing;
                Set<String> keys = jsonObject.keySet();
                for (String key: keys) {
                    Object o = jsonObject.get(key);
                    if (key.equals("tags") && o instanceof JSONArray) {
                        if (tags != null) {
                            JSONArray array = (JSONArray) o;
                            for (Object tag : array)
                                tags.add((String)tag);
                        }
                    } else {
                        findUsedSchemas(schemasJson, o, tags);
                    }
                }
            } else  if (jsonThing instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) jsonThing;
                for (Object o : jsonArray) {
                    findUsedSchemas(schemasJson, o, tags);
                }
            } else if (jsonThing instanceof String) {
                String string = (String) jsonThing;
                if (string.startsWith("#/components/schemas/")) {
                    String schemaName = string.substring("#/components/schemas/".length());
                    JSONObject schema = (JSONObject) schemasJson.get(schemaName);
                    if (schema != null) {
                        this.schemas.put(schemaName, schema);
                    }
                }
            }
        }

        public HashMap<String, JSONObject> getPaths() {
            return paths;
        }

        public Map<String, JSONObject> getSchemas() {
            return this.schemas;
        }

        public JSONArray getTags() {
            return tags;
        }
    }

    public static OpenAPISpec parse(ByteBuf content) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new ByteBufInputStream(content), StandardCharsets.UTF_8)) {
            JSONObject parse = (JSONObject) JSONValue.parse(reader);
            return new OpenAPISpec(parse);
        }
    }

    public static OpenAPISpec parse(ByteBuf content, String targetPathsRegex) throws NoSuchAlgorithmException {
        String jsonString = content.toString(StandardCharsets.UTF_8);
        JSONObject parse = (JSONObject) JSONValue.parse(jsonString);
        return new OpenAPISpec(parse, jsonString, targetPathsRegex);
    }


    public static void main(String[] args) throws Exception {
        ByteBuf byteBuf = readFile(new File("jopenapi.json"));
//        ByteBuf byteBuf = readFile(new File("pyopenapi.json"));
//        OpenAPISpec spec = parse(byteBuf, "/convert/inscriptis");
        OpenAPISpec spec = parse(byteBuf, "/convert/tika");
//        OpenAPISpec spec = parse(byteBuf);
        System.out.println(spec.getPaths());
        System.out.println(spec.getSchemas().keySet());
        System.out.println(spec.getSchemas().size());
        System.out.println(spec.getTags());

    }

    private static ByteBuf readFile(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream is = new FileInputStream(file);
        int read = is.read(bytes);
        if (read != bytes.length)
            throw new RuntimeException();
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        return byteBuf;
    }
}
