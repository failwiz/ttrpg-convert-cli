package dev.ebullient.convert.tools.dnd5e;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import dev.ebullient.convert.tools.dnd5e.qute.QuteFeat;
import dev.ebullient.convert.tools.dnd5e.qute.Tools5eQuteBase;

public class Json2QuteFeat extends Json2QuteCommon {
    static final Pattern featPattern = Pattern.compile("([^|]+)\\|?.*");

    public Json2QuteFeat(Tools5eIndex index, Tools5eIndexType type, JsonNode jsonNode) {
        super(index, type, jsonNode);
    }

    @Override
    protected Tools5eQuteBase buildQuteResource() {
        String prerequisite = listPrerequisites();
        Set<String> tags = new TreeSet<>(sources.getSourceTags());

        return new QuteFeat(sources,
                decoratedTypeName(sources),
                sources.getSourceText(index.srdOnly()),
                prerequisite,
                null, // Level coming someday..
                getText("##"),
                tags);
    }

    String listPrerequisites() {
        List<String> prereqs = new ArrayList<>();
        Tools5eIndex index = index();
        node.withArray("prerequisite").forEach(entry -> {
            if (entry.has("level")) {
                prereqs.add(levelToText(entry.get("level")));
            }
            entry.withArray("race").forEach(r -> prereqs.add(index.lookupName(Tools5eIndexType.race, raceToText(r))));

            Map<String, List<String>> abilityScores = new HashMap<>();
            entry.withArray("ability").forEach(a -> a.fields().forEachRemaining(score -> abilityScores.computeIfAbsent(
                    score.getValue().asText(),
                    k -> new ArrayList<>()).add(SkillOrAbility.format(score.getKey()))));
            abilityScores.forEach(
                    (k, v) -> prereqs.add(String.format("%s %s or higher", String.join(" or ", v), k)));

            if (entry.has("spellcasting") && entry.get("spellcasting").asBoolean()) {
                prereqs.add("The ability to cast at least one spell");
            }
            if (entry.has("pact")) {
                prereqs.add("Pact of the " + entry.get("pact").asText());
            }
            if (entry.has("patron")) {
                prereqs.add(entry.get("patron").asText() + " Patron");
            }
            entry.withArray("spell").forEach(s -> {
                String text = s.asText().replaceAll("#c", "");
                prereqs.add(index.lookupName(Tools5eIndexType.spell, text));
            });
            entry.withArray("feat").forEach(f -> prereqs
                    .add(featPattern.matcher(f.asText())
                            .replaceAll(m -> index.lookupName(Tools5eIndexType.feat, m.group(1)))));
            entry.withArray("feature").forEach(f -> prereqs.add(featPattern.matcher(f.asText())
                    .replaceAll(m -> index.lookupName(Tools5eIndexType.optionalfeature, m.group(1)))));
            entry.withArray("background")
                    .forEach(f -> prereqs
                            .add(index.lookupName(Tools5eIndexType.background, f.get("name").asText()) + " background"));
            entry.withArray("item").forEach(i -> prereqs.add(index.lookupName(Tools5eIndexType.item, i.asText())));

            if (entry.has("psionics")) {
                prereqs.add("Psionics");
            }

            List<String> profs = new ArrayList<>();
            entry.withArray("proficiency").forEach(f -> f.fields().forEachRemaining(field -> {
                String key = field.getKey();
                if ("weapon".equals(key)) {
                    key += "s";
                }
                profs.add(String.format("%s %s", field.getValue().asText(), key));
            }));
            if (!profs.isEmpty()) {
                prereqs.add(String.format("Proficiency with %s", String.join(" or ", profs)));
            }

            if (entry.has("other")) {
                prereqs.add(entry.get("other").asText());
            }
        });
        return prereqs.isEmpty() ? null : String.join(", ", prereqs);
    }
}
