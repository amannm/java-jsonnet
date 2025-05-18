import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class BasicTest {
    @Test
    void testJsonnet() {
        var example = """
                /* A C-style comment. */
                # A Python-style comment.
                {
                  cocktails: {
                    // Ingredient quantities are in fl oz.
                    'Tom Collins': {
                      ingredients: [
                        { kind: "Farmer's Gin", qty: 1.5 },
                        { kind: 'Lemon', qty: 1 },
                        { kind: 'Simple Syrup', qty: 0.5 },
                        { kind: 'Soda', qty: 2 },
                        { kind: 'Angostura', qty: 'dash' },
                      ],
                      garnish: 'Maraschino Cherry',
                      served: 'Tall',
                      description: |||
                        The Tom Collins is essentially gin and
                        lemonade.  The bitters add complexity.
                      |||,
                    },
                    Manhattan: {
                      ingredients: [
                        { kind: 'Rye', qty: 2.5 },
                        { kind: 'Sweet Red Vermouth', qty: 1 },
                        { kind: 'Angostura', qty: 'dash' },
                      ],
                      garnish: 'Maraschino Cherry',
                      served: 'Straight Up',
                      description: @'A clear \\ red drink.',
                    },
                  },
                }
                """;
        try (Jsonnet jsonnet = new Jsonnet("libjsonnet.so.0.21.0")) {
            var jsonObject = jsonnet.evaluate(example);
            var writerFactory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
            var writer = writerFactory.createWriter(System.out);
            writer.write(jsonObject);

        } catch (Throwable e) {
            Assertions.fail(e);
        }
    }
}
