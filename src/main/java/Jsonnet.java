import java.io.Closeable;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

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

    //char *jsonnet_evaluate_snippet(struct JsonnetVm *vm, const char *filename, const char *snippet, int *error);
    public String evaluate(String snippet) throws Throwable {
        var snippetPointer = arena.allocateUtf8String(snippet);
        var errorCodePointer = arena.allocate(ValueLayout.JAVA_LONG);
        var result = (MemorySegment) evaluate.invokeExact(engine, blank, snippetPointer, errorCodePointer);
        var errorResult = errorCodePointer.get(ValueLayout.JAVA_LONG, 0);
        if (errorResult != 0) {
            throw new IllegalStateException("error code: " + errorResult);
        }
        var copied = result.reinterpret(Integer.MAX_VALUE).getUtf8String(0);
        release(result);
        return copied;
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
