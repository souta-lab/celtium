package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.Callback;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;

import static org.lwjgl.system.APIUtil.apiCreateCIF;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_double;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_float;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint64;

/**
 * The stub's upcall machinery, extracted from {@code CeltiumGlStub} unchanged in behaviour
 * (refactor/giant-split-3): everything that turns a function name into a callable native address - the FFM
 * upcalls, the libffi trampolines, the descriptors and CIFs built from a row's declared argument types, and
 * the caches that keep one trampoline per name - lives here, while the stub keeps the function table, the
 * name-to-id lookup and the dispatch the upcalls land in.
 * <p>
 * The class is public, unlike the earlier extracted records, because the FFM binder binds the SAM interfaces
 * below reflectively: they were public nested interfaces of the public stub, and they keep exactly that shape
 * here. Nothing else in the tree names this class except the stub and its test.
 * <p>
 * The dispatch itself does not move: the trampoline entries call the two functional interfaces the stub
 * supplies at construction (its own {@code dispatch} / {@code legacyDispatch} as method references), so the
 * stub's dispatch keeps the visibility it had and this class never calls back into the stub's internals. The
 * one reference back is the table's argument-type codes ({@code CeltiumGlStub.A_*}), which stay the stub's -
 * they are the encoding every row of its function table is written in.
 */
public final class GlTrampolines {

    /** The stub's uniform dispatch: one function id and the call's twelve argument slots. */
    interface Dispatch {
        long invoke(int functionId, long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                long a8, long a9, long a10, long a11);
    }

    /** The stub's legacy (libffi) entry: the result slot and the pointer array the closure was handed. */
    interface LegacyDispatch {
        void invoke(int functionId, long result, long args);
    }

    private final Dispatch dispatch;
    private final LegacyDispatch legacyDispatch;

    /** Function-name -> trampoline for the libffi path; the FFM addresses live in {@link #ffmAddresses}. */
    private final Map<String, Callback> trampolines = new HashMap<>();
    private final List<Callback> ownedTrampolines = new ArrayList<>();

    GlTrampolines(Dispatch dispatch, LegacyDispatch legacyDispatch) {
        this.dispatch = dispatch;
        this.legacyDispatch = legacyDispatch;
    }

    /**
     * Resolves one function's callable address: the FFM upcall when the signature can ride it, otherwise the
     * libffi trampoline for the name. The stub's {@code addressFor} keeps the table lookup, the
     * unknown-symbol record and the trace print; everything from the signature onward lives here.
     */
    long addressFor(String name, int functionId, byte[] args) {
        if (celtium$ffmCompatible(args)) {
            // Explicit get/put: computeIfAbsent with a lambda that captures id/args allocates that lambda on every
            // call, and this is the per-name resolution path of the GL surface.
            Long stub = this.ffmAddresses.get(name);
            if (stub == null) {
                long address = celtium$ffmStub(functionId, args);
                if (address != 0L) {
                    stub = Long.valueOf(address);
                    this.ffmAddresses.put(name, stub);
                }
            }
            if (stub != null) {
                return stub.longValue();
            }
        }
        return libffiTrampoline(name, functionId, args).address();
    }

    /**
     * The libffi trampoline for one name, created and cached on first use: the double shape when the row's
     * first argument is a double, the float shape when it is a float, the uniform shape otherwise - the same
     * three branches {@code addressFor} used to spell out against the stub's own map. Package-private rather
     * than folded into {@link #addressFor} so a test can take the trampoline itself and drive its entries
     * against a recording dispatch.
     */
    Callback libffiTrampoline(String name, int functionId, byte[] args) {
        Callback trampoline = this.trampolines.get(name);
        if (trampoline == null) {
            Callback created;
            if (args.length > 0 && args[0] == CeltiumGlStub.A_DOUBLE) {
                created = new CeltiumGlDoubleTrampoline(functionId, this.dispatch, this.legacyDispatch);
            } else if (args.length > 0 && args[0] == CeltiumGlStub.A_FLOAT) {
                created = new CeltiumGlFloatTrampoline(functionId, this.dispatch, this.legacyDispatch);
            } else {
                created = new CeltiumGlTrampoline(functionId, this.dispatch, this.legacyDispatch, args);
            }
            this.ownedTrampolines.add(created);
            this.trampolines.put(name, created);
            trampoline = created;
        }
        return trampoline;
    }

    // ---- FFM upcalls -----------------------------------------------------------------

    /**
     * {@code -Dceltium.glstub.ffm=false} forces every trampoline onto the libffi path. The default is FFM:
     * LWJGL's runtime binder turns the same SAM + CIF into a {@code java.lang.foreign} upcall, which the steady
     * JFR showed paying a fraction of the legacy path's libffi closure plus per-argument pointer dereferences.
     */
    private static final boolean FFM_UPCALLS = !"false".equals(System.getProperty("celtium.glstub.ffm", "true"));

    /**
     * {@return whether the signature can ride the FFM upcall}: the FFM path shares the uniform long-argument SAM, so
     * it can only carry calls whose every argument the ABI puts in a general-purpose register. Float and double
     * arguments live in the SSE registers - a long-typed parameter reads the wrong slot (the glClearColor colour that
     * arrived as -1.6e29 before the CIF declared the floats) - and those stay on the libffi trampolines whose own
     * CIFs marshal them. NOOP rows are exempt anyway: they discard every argument, so their declared
     * all-pointer signature already reads general-purpose registers correctly.
     */
    static boolean celtium$ffmCompatible(byte[] args) {
        if (!FFM_UPCALLS) {
            return false;
        }
        for (byte arg : args) {
            if (arg == CeltiumGlStub.A_FLOAT || arg == CeltiumGlStub.A_DOUBLE) {
                return false;
            }
        }
        return true;
    }

    /** Reflective handles into {@code org.lwjgl.system.ffm}: present on JDK 25+, absent on older runtimes. */
    private static final class FfmRuntime {
        private java.lang.reflect.Method ffmUpcall;
        private java.lang.reflect.Method binderAllocate;
        private java.lang.reflect.Method segmentAddress;
        private Object arena;
        private Throwable failure;
    }

    private static volatile FfmRuntime ffmRuntime;

    private static FfmRuntime celtium$ffmRuntime() {
        FfmRuntime runtime = ffmRuntime;
        if (runtime == null) {
            synchronized (GlTrampolines.class) {
                runtime = ffmRuntime;
                if (runtime == null) {
                    runtime = new FfmRuntime();
                    try {
                        Class<?> ffmClass = Class.forName("org.lwjgl.system.ffm.FFM");
                        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
                        Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
                        runtime.arena = arenaClass.getMethod("ofAuto").invoke(null);
                        runtime.segmentAddress = segmentClass.getMethod("address");
                        runtime.ffmUpcall = ffmClass.getMethod("ffmUpcall",
                                Class.class, org.lwjgl.system.libffi.FFICIF.class);
                        runtime.binderAllocate = Class.forName("org.lwjgl.system.ffm.UpcallBinder")
                                .getMethod("allocate", arenaClass, Object.class);
                        // The binder generates its upcall class under a per-package configuration: nothing is
                        // registered for our package until this runs, and the unconfigured call fails with
                        // "No FFMConfig registered". One default config is enough - the stub needs no symbol
                        // lookup, no tracing and no nullable mapping.
                        Object builder = ffmClass.getMethod("ffmConfigBuilder",
                                java.lang.invoke.MethodHandles.Lookup.class)
                                .invoke(null, java.lang.invoke.MethodHandles.lookup());
                        Object config = builder.getClass().getMethod("build").invoke(builder);
                        ffmClass.getMethod("ffmConfig", Package.class,
                                Class.forName("org.lwjgl.system.ffm.FFMConfig"))
                                .invoke(null, GlTrampolines.class.getPackage(), config);
                    } catch (Throwable error) {
                        runtime.failure = error;
                        System.out.println("[Celtium] GLSTUB ffm runtime unavailable: " + error);
                    }
                    ffmRuntime = runtime;
                }
            }
        }
        return runtime;
    }

    /**
     * Creates one FFM upcall for {@code functionId} with the signature's own CIF and returns its callable address,
     * or 0 when the runtime or the binder refused - the caller then takes the libffi trampoline for the same name.
     * The SAM instance, the CIF and the returned segment all stay referenced for the stub's lifetime.
     */
    private long celtium$ffmStub(int functionId, byte[] args) {
        FfmRuntime runtime = celtium$ffmRuntime();
        if (runtime == null || runtime.failure != null) {
            return 0L;
        }
        try {
            int id = functionId;
            // dispatch() fills the shared argument scratch - allocating a fresh array per call here was exactly the
            // ~4 MB/frame of garbage the scratch exists to avoid.
            GlTrampolineI callback = (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                    GlTrampolines.this.dispatch.invoke(id, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11);
            Object binder = runtime.ffmUpcall.invoke(null, GlTrampolineI.class, cifFor(args));
            Object segment = runtime.binderAllocate.invoke(binder, runtime.arena, callback);
            long address = ((Long) runtime.segmentAddress.invoke(segment)).longValue();
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the java.lang.foreign upcall)");
            }
            this.ffmOwned.add(segment);
            this.ffmOwned.add(callback);
            return address;
        } catch (Throwable error) {
            if (runtime.failure == null) {
                runtime.failure = error;
            }
            this.ffmFailures++;
            if (this.ffmFailures <= 3) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                System.out.println("[Celtium] GLSTUB ffmStub failed for functionId=" + functionId + ": "
                        + cause.getClass().getName() + " " + cause.getMessage());
            }
            return 0L;
        }
    }

    /** Function-name -> callable address for the FFM stubs; misses fall through to the libffi trampolines. */
    private final Map<String, Long> ffmAddresses = new HashMap<>();
    /** Objects whose lifetime pins the FFM stubs: binder segments and their SAM instances. */
    private final List<Object> ffmOwned = new ArrayList<>();
    private int ffmFailures;

    // ---- trampoline plumbing ---------------------------------------------------------

    /**
     * Argument slots in the plain (integer/pointer) upcall shape.
     * <p>
     * This is set by the widest row in the table, {@code glTexImage3D} at ten arguments - and it has to be that wide
     * for the texture path to work at all: {@code glTexImage2D} takes nine arguments, so its {@code pixels} pointer is
     * slot 8 and an upcall narrower than that never receives it. Slots past a call's declared arguments carry whatever
     * the caller left in the registers and stack; no handler reads past its own row's declaration, so they are inert.
     */
    static final int SLOTS = 12;

    /**
     * One uniform call interface covers every function in the table: the FFM upcall invokes the SAM with the C
     * arguments marshalled per the CIF, so a CIF of twelve argument slots means {@code invoke} receives up to twelve
     * argument slots. Callers of the real functions supply fewer - the extra slots carry garbage that the dispatcher
     * ignores; callers expecting narrower results (int or nothing) read only the low bits or discard the value
     * entirely. The widest function in the table is {@code glTexImage3D} at ten arguments.
     */
    private static final Callback.Descriptor UNIFORM_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    /**
     * One descriptor per declared signature, so the trampoline reads the arguments where the caller actually put
     * them.
     * <p>
     * The uniform pointer interface above is only correct for functions that take integers and pointers: an x86-64
     * caller passes floating-point arguments in the SSE registers, so a closure that declares pointers reads the
     * general-purpose registers and sees whatever was left in them. That is not theoretical - it is what turned the
     * game's {@code glClearColor} into an arbitrary colour. The table already declares every argument's type, so the
     * CIF is built from it and float arguments arrive as floats.
     */
    private static final java.util.Map<String, Callback.Descriptor> SIGNATURE_DESCRIPTORS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static Callback.Descriptor descriptorFor(byte[] args) {
        StringBuilder key = new StringBuilder(args.length + 1);
        for (byte arg : args) {
            key.append((char) ('a' + arg));
        }
        return SIGNATURE_DESCRIPTORS.computeIfAbsent(key.toString(), ignored ->
                new Callback.Descriptor(trampolineType(args), java.lang.invoke.MethodHandles.lookup(), cifFor(args)));
    }

    /**
     * {@return the trampoline interface a signature's arguments are read by}.
     * <p>
     * LWJGL 3.4.3's {@code Callback.Descriptor} carries the callback type as well as the CIF, because the trampoline is
     * built from the descriptor rather than from the interface alone. Which interface it is follows from where the
     * arguments actually land: an x86-64 caller passes floats and doubles in the SSE registers, so those signatures
     * need the upcalls whose abstract method declares them - the same reason {@link #ffiTypeFor} distinguishes them.
     */
    static Class<? extends org.lwjgl.system.CallbackI> trampolineType(byte[] args) {
        boolean floats = false;
        boolean doubles = false;
        for (byte arg : args) {
            if (arg == CeltiumGlStub.A_FLOAT) {
                floats = true;
            } else if (arg == CeltiumGlStub.A_DOUBLE) {
                doubles = true;
            }
        }
        if (doubles) {
            return GlTrampolineDoubleI.class;
        }
        return floats ? GlTrampolineFloatI.class : GlTrampolineI.class;
    }

    /** The signature's call interface itself, so the FFM binder can see the CIF the Callback descriptor wraps. */
    private static FFICIF cifFor(byte[] args) {
        java.util.List<FFIType> types = new java.util.ArrayList<FFIType>(SLOTS);
        for (byte arg : args) {
            if (types.size() == SLOTS) {
                break;
            }
            types.add(ffiTypeFor(arg));
        }
        // Padded to the uniform arity: the dispatcher ignores the slots past the declared arguments, and the
        // native side reads only what the caller passed.
        while (types.size() < SLOTS) {
            types.add(ffi_type_pointer);
        }
        return apiCreateCIF(ffi_type_pointer, types.toArray(new FFIType[0]));
    }

    private static FFIType ffiTypeFor(byte arg) {
        switch (arg) {
            case CeltiumGlStub.A_FLOAT:
                return ffi_type_float;
            case CeltiumGlStub.A_DOUBLE:
                return ffi_type_double;
            case CeltiumGlStub.A_LONG:
                return ffi_type_sint64;
            case CeltiumGlStub.A_PTR:
                return ffi_type_pointer;
            default:
                return ffi_type_sint32;
        }
    }

    /**
     * The upcall interface the FFM backend binds: one abstract method receiving the call's argument values. The
     * legacy {@code callback} entry is not used by the FFM path and stays a stub.
     */
    @FunctionalInterface
    public interface GlTrampolineI extends org.lwjgl.system.CallbackI {
        long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                long a8, long a9, long a10, long a11);

        /**
         * Required by {@link org.lwjgl.system.CallbackI}; the trampoline class overrides it with the legacy-backend entry. A default
         * must exist so this stays a functional interface.
         */
        @Override
        default void callback(long result, long args) {
        }

        /** Supplied by the trampoline instance. */
        @Override
        default Callback.Descriptor getDescriptor() {
            return UNIFORM_DESCRIPTOR;
        }
    }

    /**
     * The upcall shape for functions that take floating-point arguments.
     * <p>
     * The long-argument shape cannot deliver them: an x86-64 caller passes floats and doubles in the SSE registers,
     * and an upcall whose parameters are all {@code long} reads the general-purpose ones instead, so the values
     * arrive as whatever was left in them (measured: {@code glClearColor(0.25, 0.5, 0.75, 1)} arriving as
     * {@code -1.6e29}). The CIF declares the floats, so the runtime marshals them where they belong. The shape carries
     * eight slots, which is every float-argument row's width; the dispatcher's wider slots stay zero for these.
     */
    private static final Callback.Descriptor FLOAT4_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineFloatI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_float, ffi_type_float, ffi_type_float, ffi_type_float,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    private static final Callback.Descriptor DOUBLE1_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineDoubleI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_double, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    /** The four-float upcall: {@code glClearColor} and the single-float setters share it. */
    @FunctionalInterface
    public interface GlTrampolineFloatI extends org.lwjgl.system.CallbackI {
        long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7);

        @Override
        default void callback(long result, long args) {
        }

        @Override
        default Callback.Descriptor getDescriptor() {
            return FLOAT4_DESCRIPTOR;
        }
    }

    /** The double upcall: {@code glClearDepth} is the one in the table. */
    @FunctionalInterface
    public interface GlTrampolineDoubleI extends org.lwjgl.system.CallbackI {
        long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7);

        @Override
        default void callback(long result, long args) {
        }

        @Override
        default Callback.Descriptor getDescriptor() {
            return DOUBLE1_DESCRIPTOR;
        }
    }

    private static final class CeltiumGlFloatTrampoline extends Callback implements GlTrampolineFloatI {
        private final int functionId;
        private final Dispatch dispatch;
        private final LegacyDispatch legacyDispatch;

        CeltiumGlFloatTrampoline(int functionId, Dispatch dispatch, LegacyDispatch legacyDispatch) {
            super(FLOAT4_DESCRIPTOR);
            this.functionId = functionId;
            this.dispatch = dispatch;
            this.legacyDispatch = legacyDispatch;
        }

        @Override
        public long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7) {
            // Every float-argument row declares at most four arguments, so this narrow upcall stays as measured and
            // the wide slots the dispatcher carries are zero rather than junk.
            return this.dispatch.invoke(this.functionId,
                    Float.floatToRawIntBits(a0) & 0xFFFFFFFFL, Float.floatToRawIntBits(a1) & 0xFFFFFFFFL,
                    Float.floatToRawIntBits(a2) & 0xFFFFFFFFL, Float.floatToRawIntBits(a3) & 0xFFFFFFFFL,
                    a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        @Override
        public void callback(long result, long args) {
            this.legacyDispatch.invoke(this.functionId, result, args);
        }
    }

    private static final class CeltiumGlDoubleTrampoline extends Callback implements GlTrampolineDoubleI {
        private final int functionId;
        private final Dispatch dispatch;
        private final LegacyDispatch legacyDispatch;

        CeltiumGlDoubleTrampoline(int functionId, Dispatch dispatch, LegacyDispatch legacyDispatch) {
            super(DOUBLE1_DESCRIPTOR);
            this.functionId = functionId;
            this.dispatch = dispatch;
            this.legacyDispatch = legacyDispatch;
        }

        @Override
        public long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
            // One double argument is the widest row this shape serves; the wide slots stay zero (see the float entry).
            return this.dispatch.invoke(this.functionId, Double.doubleToRawLongBits(a0), a1, a2, a3, a4, a5, a6, a7,
                    0L, 0L, 0L, 0L);
        }

        @Override
        public void callback(long result, long args) {
            this.legacyDispatch.invoke(this.functionId, result, args);
        }
    }

    /**
     * The upcall object, implementing every trampoline interface.
     * <p>
     * LWJGL 3.4.3's {@code Callback} casts the instance to the type its descriptor names, so one object has to satisfy
     * whichever interface the signature's descriptor was built with - the uniform long upcall for pointers and
     * integers, and the float and double upcalls for the signatures whose arguments a caller passes in the SSE
     * registers. The float and double entries convert to raw bits and hand them to the same dispatch, which is what
     * the uniform upcall received before the descriptor carried a type at all.
     */
    private static final class CeltiumGlTrampoline extends Callback
            implements GlTrampolineI, GlTrampolineFloatI, GlTrampolineDoubleI {
        private final int functionId;
        private final Dispatch dispatch;
        private final LegacyDispatch legacyDispatch;
        private final Callback.Descriptor descriptor;

        /**
         * One native trampoline per function name. Two call entries exist because which backend LWJGL uses is a
         * runtime property, not a compile-time one: {@code invoke} serves the FFM backend, {@code callback} the
         * legacy libffi one.
         */
        CeltiumGlTrampoline(int functionId, Dispatch dispatch, LegacyDispatch legacyDispatch, byte[] args) {
            super(descriptorFor(args));
            this.functionId = functionId;
            this.dispatch = dispatch;
            this.legacyDispatch = legacyDispatch;
            this.descriptor = descriptorFor(args);
        }

        @Override
        public Callback.Descriptor getDescriptor() {
            return this.descriptor;
        }

        /** The FFM backend's entry: the C call's arguments arrive as this SAM's parameters, in order. */
        @Override
        public long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                long a8, long a9, long a10, long a11) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the uniform long-argument upcall)");
            }
            return this.dispatch.invoke(this.functionId, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11);
        }

        /** The float-argument entry: four floats, converted to raw bits for the uniform dispatch. */
        @Override
        public long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the float-argument upcall)");
            }
            return this.dispatch.invoke(this.functionId,
                    Float.floatToRawIntBits(a0) & 0xFFFFFFFFL, Float.floatToRawIntBits(a1) & 0xFFFFFFFFL,
                    Float.floatToRawIntBits(a2) & 0xFFFFFFFFL, Float.floatToRawIntBits(a3) & 0xFFFFFFFFL,
                    a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        /** The double-argument entry: one double, converted to raw bits for the uniform dispatch. */
        @Override
        public long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the double-argument upcall)");
            }
            return this.dispatch.invoke(this.functionId, Double.doubleToRawLongBits(a0),
                    a1, a2, a3, a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        /**
         * The legacy (libffi) backend's entry: {@code args} is an array of pointers, one per C argument, each
         * pointing at that argument's storage; the result goes to the closure-owned slot {@code result}.
         */
        @Override
        public void callback(long result, long args) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=legacy (the libffi pointer-array callback)");
            }
            this.legacyDispatch.invoke(this.functionId, result, args);
        }
    }

    /** One line saying which upcall entry the runtime actually uses; the two need different argument handling. */
    private static final java.util.concurrent.atomic.AtomicBoolean ENTRY_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();
}
