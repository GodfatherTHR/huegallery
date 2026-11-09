package com.gallery.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Ollama tool schemas exposed to the local model. */
public final class AiToolDefinitions {

    private static final JsonArray TOOLS = build();

    private AiToolDefinitions() {
    }

    public static JsonArray all() {
        return TOOLS;
    }

    private static JsonArray build() {
        JsonArray tools = new JsonArray();

        tools.add(tool("list_tags",
                "List the most-used tags in the gallery, with image counts.",
                new JsonObject()));

        JsonObject tagProperties = new JsonObject();
        tagProperties.add("tags", arrayProperty("Tag names to search for, e.g. [\"sunset\",\"sea\"]."));
        tagProperties.add("match", enumProperty("Whether an image must match any tag or all tags.",
                "any", "all"));
        tagProperties.add("limit", integerProperty("Maximum number of images to return (1-24)."));
        tools.add(tool("search_by_tags",
                "Find gallery images by their tags. Use this for scenes, objects, places and themes.",
                tagProperties, "tags"));

        JsonObject textProperties = new JsonObject();
        textProperties.add("query", stringProperty("Words to find in image titles or descriptions."));
        textProperties.add("limit", integerProperty("Maximum number of images to return (1-24)."));
        tools.add(tool("search_by_text",
                "Find gallery images whose title or description contains the supplied words.",
                textProperties, "query"));

        JsonObject colourProperties = new JsonObject();
        colourProperties.add("colours", arrayProperty(
                "Colour names such as red, blue, warm orange, forest green, ocean, sunset, or #rrggbb."));
        colourProperties.add("limit", integerProperty("Maximum number of images to return (1-24)."));
        tools.add(tool("search_by_colour",
                "Rank gallery images by how closely their colour distribution matches the requested colours.",
                colourProperties, "colours"));

        JsonObject similarProperties = new JsonObject();
        similarProperties.add("image_id", integerProperty("The id of an existing gallery image."));
        similarProperties.add("limit", integerProperty("Maximum number of images to return (1-24)."));
        tools.add(tool("find_similar",
                "Find images that are visually similar to an existing gallery image id.",
                similarProperties, "image_id"));

        return tools;
    }

    private static JsonObject tool(String name, String description, JsonObject properties,
            String... required) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        if (required.length > 0) {
            JsonArray requiredArray = new JsonArray();
            for (String field : required) {
                requiredArray.add(field);
            }
            parameters.add("required", requiredArray);
        }

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static JsonObject stringProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject arrayProperty(String description) {
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        JsonObject property = new JsonObject();
        property.addProperty("type", "array");
        property.addProperty("description", description);
        property.add("items", items);
        return property;
    }

    private static JsonObject integerProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("description", description);
        return property;
    }

    private static JsonObject enumProperty(String description, String... values) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        JsonArray allowed = new JsonArray();
        for (String value : values) {
            allowed.add(value);
        }
        property.add("enum", allowed);
        return property;
    }
}
