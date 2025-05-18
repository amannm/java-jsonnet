import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Throwable {
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
            var result = jsonnet.evaluate(example);
            System.out.println(result);
        }
    }

}