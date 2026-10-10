package cn.howxu.mmcr.compat.patchouli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the actual guide resource graph without initializing Minecraft.
 * @author howxu <dev@howxu.cn>
 */
class PatchouliGuideResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path BOOK = RESOURCES.resolve("assets/mmcr/patchouli_books/modular_guide/en_us");
    private static final Path LANG = RESOURCES.resolve("assets/mmcr_patchouli/lang");
    private static final Set<String> TRANSLATED_FIELDS = Set.of(
            "name", "landing_text", "subtitle", "description", "title", "translate");
    private static final Pattern LINKS = Pattern.compile("\\$\\(l:([^)]*)\\)");
    private static final List<String> OPTIONAL_MODS = List.of("ae2", "extendedae", "appflux", "mekanism");

    @Test
    void guide_references_resolve_and_translations_are_complete() throws IOException {
        Map<String, JsonObject> categories = readObjects(BOOK.resolve("categories"));
        Map<String, JsonObject> entries = readObjects(BOOK.resolve("entries"));
        JsonObject english = readJson(LANG.resolve("en_us.json"));
        JsonObject chinese = readJson(LANG.resolve("zh_cn.json"));
        assertThat(categories).isNotEmpty();
        assertThat(entries).containsKey("mmcr:controllers/controller");
        assertThat(chinese.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
        Set<String> usedKeys = new LinkedHashSet<>();
        for (var entry : entries.entrySet()) {
            JsonObject value = entry.getValue();
            String categoryId = value.get("category").getAsString();
            assertThat(categories).as("Category of %s", entry.getKey()).containsKey(categoryId);
            assertThat(flag(value)).as("Entry/category visibility: %s", entry.getKey())
                    .isEqualTo(flag(categories.get(categoryId)));
            assertThat(value.getAsJsonArray("pages")).as("Pages in %s", entry.getKey()).isNotEmpty();
            assertTranslatedFields(value, english, chinese, usedKeys);
        }
        for (JsonObject category : categories.values()) {
            assertTranslatedFields(category, english, chinese, usedKeys);
        }
        JsonObject declaration = readJson(RESOURCES.resolve("data/mmcr/patchouli_books/modular_guide/book.json"));
        assertTranslatedFields(declaration, english, chinese, usedKeys);
        assertThat(english.keySet()).as("No orphaned guide translations")
                .containsExactlyInAnyOrderElementsOf(usedKeys);
    }

    @Test
    void optional_directories_are_safe_and_controllers_need_no_registered_machine() throws IOException {
        Map<String, JsonObject> categories = readObjects(BOOK.resolve("categories"));
        Map<String, JsonObject> entries = readObjects(BOOK.resolve("entries"));
        for (JsonObject value : categories.values()) {
            assertValidFlag(value);
            if (!flag(value).isEmpty()) assertThat(value.get("icon").getAsString()).isEqualTo("mmcr:basic_casing");
        }
        for (var entry : entries.entrySet()) {
            JsonObject value = entry.getValue();
            assertValidFlag(value);
            if (!flag(value).isEmpty()) assertThat(value.get("icon").getAsString()).isEqualTo("mmcr:basic_casing");
            if (value.get("category").getAsString().equals("mmcr:controllers")) {
                assertThat(flag(value)).isEmpty();
                assertThat(value.get("icon").getAsString()).startsWith("mmcr:textures/");
                for (JsonElement page : value.getAsJsonArray("pages")) {
                    assertThat(flag(page.getAsJsonObject())).isEmpty();
                    assertThat(page.getAsJsonObject().get("type").getAsString()).isEqualTo("patchouli:text");
                }
            }
            for (JsonElement page : value.getAsJsonArray("pages")) assertValidFlag(page.getAsJsonObject());
        }
    }

    @Test
    void links_resolve_without_pointing_to_hidden_entries_or_anchors() throws IOException {
        Map<String, JsonObject> entries = readObjects(BOOK.resolve("entries"));
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject translations = readJson(LANG.resolve(language + ".json"));
            for (var source : entries.entrySet()) {
                JsonObject entry = source.getValue();
                assertLinks(entry.get("name").getAsString(), entry, entry, entries, translations);
                for (JsonElement page : entry.getAsJsonArray("pages")) {
                    Set<String> keys = new LinkedHashSet<>();
                    collectTranslationKeys(page, keys);
                    for (String key : keys) assertLinks(key, entry, page.getAsJsonObject(), entries, translations);
                }
            }
        }
    }

    @Test
    void visible_spotlight_item_queries_have_unambiguous_targets() throws IOException {
        Map<String, JsonObject> categories = readObjects(BOOK.resolve("categories"));
        Map<String, JsonObject> entries = readObjects(BOOK.resolve("entries"));
        for (int installed = 0; installed < (1 << OPTIONAL_MODS.size()); installed++) {
            Map<String, String> targets = new LinkedHashMap<>();
            for (var source : entries.entrySet()) {
                JsonObject entry = source.getValue();
                if (!visible(entry, installed)
                        || !visible(categories.get(entry.get("category").getAsString()), installed)) continue;
                var pages = entry.getAsJsonArray("pages");
                for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                    JsonObject page = pages.get(pageIndex).getAsJsonObject();
                    if (!visible(page, installed) || !page.get("type").getAsString().equals("patchouli:spotlight")
                            || !page.has("link_recipe") || !page.get("link_recipe").getAsBoolean()) continue;
                    String item = page.get("item").getAsString();
                    String target = source.getKey() + "#spread" + pageIndex / 2;
                    String previous = targets.putIfAbsent(item, target);
                    if (previous != null) {
                        assertThat(target).as("Item query %s, installed mask %s", item, installed).isEqualTo(previous);
                    }
                }
            }
        }
    }

    private static void assertLinks(String key, JsonObject source, JsonObject sourcePage,
                                    Map<String, JsonObject> entries, JsonObject translations) {
        var matcher = LINKS.matcher(translations.get(key).getAsString());
        while (matcher.find()) {
            String[] target = matcher.group(1).split("#", 2);
            assertThat(entries).as("Link in %s", key).containsKey(target[0]);
            JsonObject destination = entries.get(target[0]);
            JsonObject destinationPage = destination;
            if (target.length == 2) {
                destinationPage = null;
                for (JsonElement page : destination.getAsJsonArray("pages")) {
                    JsonObject value = page.getAsJsonObject();
                    if (value.has("anchor") && value.get("anchor").getAsString().equals(target[1])) destinationPage = value;
                }
                assertThat(destinationPage).as("Anchor %s in %s", target[1], target[0]).isNotNull();
            }
            for (int installed = 0; installed < (1 << OPTIONAL_MODS.size()); installed++) {
                if (visible(source, installed) && visible(sourcePage, installed)) {
                    assertThat(visible(destination, installed) && visible(destinationPage, installed))
                            .as("Visible link in %s to %s, installed mask %s", key, matcher.group(1), installed).isTrue();
                }
            }
        }
    }

    private static void assertValidFlag(JsonObject value) {
        String condition = flag(value);
        if (condition.isEmpty()) return;
        assertThat(condition).matches("&?mod:[a-z0-9_]+(,mod:[a-z0-9_]+)*");
        if (condition.contains(",")) assertThat(condition).startsWith("&");
        for (String mod : condition.replace("&", "").split(",")) {
            assertThat(OPTIONAL_MODS).contains(mod.substring("mod:".length()));
        }
    }

    private static boolean visible(JsonObject value, int installed) {
        String condition = flag(value);
        if (condition.isEmpty()) return true;
        for (String mod : condition.replace("&", "").split(",")) {
            int index = OPTIONAL_MODS.indexOf(mod.substring("mod:".length()));
            assertThat(index).as("Known mod in %s", condition).isNotNegative();
            if ((installed & (1 << index)) == 0) return false;
        }
        return true;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, JsonObject> readObjects(Path directory) throws IOException {
        Map<String, JsonObject> values = new LinkedHashMap<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                String id = "mmcr:" + relative.substring(0, relative.length() - 5);
                assertThat(values).doesNotContainKey(id);
                values.put(id, readJson(file));
            }
        }
        return values;
    }

    private static String flag(JsonObject object) {
        return object.has("flag") ? object.get("flag").getAsString() : "";
    }

    private static void collectTranslationKeys(JsonElement element, Set<String> keys) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> collectTranslationKeys(value, keys));
        } else if (element.isJsonObject()) {
            for (var field : element.getAsJsonObject().entrySet()) {
                if (TRANSLATED_FIELDS.contains(field.getKey())) {
                    keys.add(field.getValue().getAsString());
                }
                if (field.getKey().equals("text")) {
                    assertThat(field.getValue().isJsonObject()).as("Explicit text component").isTrue();
                    assertThat(field.getValue().getAsJsonObject().has("translate")).isTrue();
                }
                collectTranslationKeys(field.getValue(), keys);
            }
        }
    }

    private static void assertTranslatedFields(JsonElement element, JsonObject english, JsonObject chinese,
                                                Set<String> usedKeys) {
        Set<String> keys = new LinkedHashSet<>();
        collectTranslationKeys(element, keys);
        for (String key : keys) {
            assertThat(key).startsWith("guide.mmcr_patchouli.");
            assertThat(english.has(key)).as("English key %s", key).isTrue();
            assertThat(chinese.has(key)).as("Chinese key %s", key).isTrue();
            assertThat(english.get(key).getAsString()).isNotBlank();
            assertThat(chinese.get(key).getAsString()).isNotBlank();
        }
        usedKeys.addAll(keys);
    }
}
