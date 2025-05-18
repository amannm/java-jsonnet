import jakarta.json.Json;
import jakarta.json.JsonValue;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class Jsonnet implements Closeable {

    private final Arena arena;
    private final SymbolLookup symbols;
    private final Linker linker;
    private final MemorySegment engine;
    private final MemorySegment blank;
    private final MethodHandle evaluate;

    public Jsonnet(String libraryName) throws Throwable {
        var url = Thread.currentThread().getContextClassLoader().getResource(libraryName);
        if (url == null) {
            throw new IllegalArgumentException();
        }
        var path = Path.of(url.toURI());
        this.arena = Arena.ofConfined();
        this.symbols = SymbolLookup.libraryLookup(path, arena);
        this.linker = Linker.nativeLinker();
        {
            var methodAddress = symbols.find("jsonnet_make").orElseThrow();
            var methodSignature = FunctionDescriptor.of(ValueLayout.ADDRESS);
            var method = linker.downcallHandle(methodAddress, methodSignature);
            this.engine = (MemorySegment) method.invokeExact();
        }
        {
            var methodAddress = symbols.find("jsonnet_evaluate_snippet").orElseThrow();
            var methodSignature = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
            this.evaluate = linker.downcallHandle(methodAddress, methodSignature);
        }
        this.blank = arena.allocateUtf8String("");
    }

    private byte[] readNullTerminatedBuffer(MemorySegment segment) {
        var resized = segment.reinterpret(Integer.MAX_VALUE);
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            byte curr = resized.get(JAVA_BYTE, i);
            if (curr == 0) {
                var content = new byte[i];
                MemorySegment.copy(resized, JAVA_BYTE, 0, content, 0, i);
                return content;
            }
        }
        throw new UnsupportedOperationException("string exceeds maximum supported length of " + Integer.MAX_VALUE);
    }


    //char *jsonnet_evaluate_snippet(struct JsonnetVm *vm, const char *filename, const char *snippet, int *error);
    public JsonValue evaluate(String snippet) throws Throwable {
        var snippetIn = arena.allocateUtf8String(snippet);
        var errorCodeOut = arena.allocate(ValueLayout.JAVA_LONG);
        var resultOut = (MemorySegment) evaluate.invokeExact(engine, blank, snippetIn, errorCodeOut);
        var errorResult = errorCodeOut.get(ValueLayout.JAVA_LONG, 0);
        if (errorResult != 0) {
            throw new IllegalStateException("error code: " + errorResult);
        }
        var content = readNullTerminatedBuffer(resultOut);
        release(resultOut);
        try (var jr = Json.createReader(new ByteArrayInputStream(content))) {
            return jr.readValue();
        }
    }

    private void release(MemorySegment snippet) throws Throwable {
        var methodAddress = symbols.find("jsonnet_realloc").orElseThrow();
        // char *jsonnet_realloc(struct JsonnetVm *vm, char *buf, size_t sz);
        var methodSignature = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
        var methodHandle = linker.downcallHandle(methodAddress, methodSignature, Linker.Option.isTrivial());
        methodHandle.invoke(engine, snippet, 0L);
    }

    @Override
    public void close() {
        try {
            var methodAddress = symbols.find("jsonnet_destroy").orElseThrow();
            var methodSignature = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
            var call = linker.downcallHandle(methodAddress, methodSignature);
            call.invokeExact(engine);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        arena.close();
    }
}
