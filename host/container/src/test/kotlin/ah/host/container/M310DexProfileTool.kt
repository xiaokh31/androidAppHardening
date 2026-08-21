package ah.host.container

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.builder.BuilderInstruction
import org.jf.dexlib2.builder.MutableMethodImplementation
import org.jf.dexlib2.builder.instruction.BuilderInstruction10x
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.ImmutableMethodParameter
import org.jf.dexlib2.immutable.debug.ImmutableEndLocal
import org.jf.dexlib2.immutable.debug.ImmutableEpilogueBegin
import org.jf.dexlib2.immutable.debug.ImmutableLineNumber
import org.jf.dexlib2.immutable.debug.ImmutablePrologueEnd
import org.jf.dexlib2.immutable.debug.ImmutableRestartLocal
import org.jf.dexlib2.immutable.debug.ImmutableSetSourceFile
import org.jf.dexlib2.immutable.debug.ImmutableStartLocal
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.dexlib2.writer.pool.DexPool

/**
 * M3-10-only post-build DEX transformer. It accepts only the fixed canonical class/method
 * surfaces and adds calls to the test observer at the ADR 0016 points. It is compiled only
 * in host:container test source and cannot enter a Host or Runtime production artifact.
 */
object M310DexProfileTool {
    private const val OBSERVER = "Lah/runtime/profile/M310StartupTimingObserver;"
    private const val APPLICATION = "Lah/fixtures/android/m301/BenchmarkFixtureApplication;"
    private const val PROVIDER = "Lah/fixtures/android/m301/FixtureEventProvider;"
    private const val ACTIVITY = "Lah/fixtures/android/m301/FixtureActivity;"
    private const val SHELL = "Lah/runtime/bootstrap/ShellAppComponentFactory;"
    private const val COORDINATOR = "Lah/runtime/bootstrap/HardeningBootstrap\$Coordinator;"
    private const val GUARD = "Lah/runtime/guard/RuntimeStartupGuard;"

    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 4) { "usage: payload-baseline|payload-protected|shell input.dex observer.dex output.dex" }
        val mode = arguments[0]
        val input = regular(arguments[1], "input")
        val observerDex = regular(arguments[2], "observer")
        val output = Path.of(arguments[3]).toAbsolutePath().normalize()
        require(!Files.exists(output)) { "output DEX already exists" }
        Files.createDirectories(output.parent)

        derive(mode, input, observerDex, output)
    }

    internal fun derive(mode: String, input: Path, observerDex: Path, output: Path) {
        require(Files.isRegularFile(input)) { "input DEX is missing" }
        require(Files.isRegularFile(observerDex)) { "observer DEX is missing" }
        require(!Files.exists(output)) { "output DEX already exists" }
        Files.createDirectories(output.parent)
        val opcodes = Opcodes.forApi(36)
        val source = readDex(input, opcodes)
        val observer = readDex(observerDex, opcodes).classes.singleOrNull { it.type == OBSERVER }
            ?: error("observer DEX must contain exactly the fixed observer class")
        val transformed = when (mode) {
            "payload-baseline" -> transformPayload(source, observer)
            "payload-protected" -> transformPayload(source, null)
            "shell" -> transformShell(source, observer)
            else -> error("unknown M3-10 transform mode")
        }
        writeDex(opcodes, transformed, output)
        println("M3-10 DEX profile PASS mode=$mode classes=${transformed.size}")
    }

    private fun transformPayload(source: DexBackedDexFile, observer: ClassDef?): List<ClassDef> {
        require(source.classes.none { it.type == OBSERVER }) { "payload already contains observer" }
        val requiredClasses = setOf(APPLICATION, PROVIDER, ACTIVITY)
        require(source.classes.map { it.type }.containsAll(requiredClasses)) { "canonical payload classes differ" }
        val transformed = source.classes.map { classDef ->
            when (classDef.type) {
                APPLICATION -> transformApplication(classDef)
                PROVIDER -> transformProvider(classDef)
                ACTIVITY -> transformActivity(classDef)
                else -> classDef
            }
        }.toMutableList()
        observer?.let(transformed::add)
        return transformed.sortedBy { it.type }
    }

    private fun transformApplication(classDef: ClassDef): ClassDef {
        val methods = classDef.methods.map { method ->
            when (method.key()) {
                "<init>()V" -> boundaries(method, "p1", "p2", setOf(Opcode.RETURN_VOID))
                "onCreate()V" -> boundaries(method, "p7", "p8", setOf(Opcode.RETURN_VOID))
                else -> method
            }
        }.toMutableList()
        require(methods.count { it.key() == "<init>()V" } == 1) { "Application constructor differs" }
        require(methods.count { it.key() == "onCreate()V" } == 1) { "Application.onCreate differs" }
        require(methods.none { it.key() == "attachBaseContext(Landroid/content/Context;)V" }) {
            "Application.attachBaseContext unexpectedly exists"
        }
        methods += lifecycleOverride(
            classDef.type,
            classDef.superclass,
            "attachBaseContext",
            "Landroid/content/Context;",
            "p3",
            "p4",
        )
        return copyClass(classDef, methods)
    }

    private fun transformProvider(classDef: ClassDef): ClassDef {
        val methods = classDef.methods.map { method ->
            if (method.key() == "onCreate()Z") boundaries(method, "p5", "p6", setOf(Opcode.RETURN)) else method
        }
        require(methods.count { it.key() == "onCreate()Z" } == 1) { "Provider.onCreate differs" }
        return copyClass(classDef, methods)
    }

    private fun transformActivity(classDef: ClassDef): ClassDef {
        val methods = classDef.methods.map { method ->
            when (method.key()) {
                "<init>()V" -> boundaries(method, "p9", "p10", setOf(Opcode.RETURN_VOID))
                "onCreate(Landroid/os/Bundle;)V" -> boundaries(method, "p11", "p12", setOf(Opcode.RETURN_VOID))
                "onWindowFocusChanged(Z)V" -> focusBoundary(method)
                else -> method
            }
        }.toMutableList()
        require(methods.count { it.key() == "<init>()V" } == 1) { "Activity constructor differs" }
        require(methods.count { it.key() == "onCreate(Landroid/os/Bundle;)V" } == 1) { "Activity.onCreate differs" }
        require(methods.count { it.key() == "onWindowFocusChanged(Z)V" } == 1) { "Activity focus differs" }
        require(methods.none { it.key() == "onResume()V" }) { "Activity.onResume unexpectedly exists" }
        methods += lifecycleOverride(classDef.type, classDef.superclass, "onResume", null, "p13", "p14")
        return copyClass(classDef, methods)
    }

    private fun transformShell(source: DexBackedDexFile, observer: ClassDef): List<ClassDef> {
        require(source.classes.none { it.type == OBSERVER }) { "shell already contains observer" }
        require(source.classes.any { it.type == SHELL } && source.classes.any { it.type == COORDINATOR } &&
            source.classes.any { it.type == GUARD }) { "canonical shell classes differ" }
        val transformed = source.classes.map { classDef ->
            when (classDef.type) {
                SHELL -> transformShellFactory(classDef)
                COORDINATOR -> transformCoordinator(classDef)
                GUARD -> transformGuard(classDef)
                else -> classDef
            }
        }.toMutableList()
        transformed += observer
        return transformed.sortedBy { it.type }
    }

    private fun transformShellFactory(classDef: ClassDef): ClassDef {
        val signature = "instantiateClassLoader(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)Ljava/lang/ClassLoader;"
        val methods = classDef.methods.map { method ->
            if (method.key() == signature) boundaries(method, "h0", "h8", setOf(Opcode.RETURN_OBJECT)) else method
        }
        require(methods.count { it.key() == signature } == 1) { "Shell factory method differs" }
        return copyClass(classDef, methods)
    }

    private fun transformGuard(classDef: ClassDef): ClassDef {
        val prefix = "openVerifiedPayloadInternal(Landroid/content/pm/ApplicationInfo;Ljava/lang/ClassLoader;"
        val methods = classDef.methods.map { method ->
            if (method.key().startsWith(prefix)) guardPoints(method) else method
        }
        require(methods.count { it.key().startsWith(prefix) } == 1) { "Guard method differs" }
        return copyClass(classDef, methods)
    }

    private fun guardPoints(method: Method): Method {
        val original = method.implementation ?: error("Guard implementation is absent")
        val instructions = original.instructions.toList()
        val insertions = mutableListOf<Pair<Int, BuilderInstruction>>()
        fun target(owner: String, name: String, point: String, before: Boolean) {
            val matches = instructions.mapIndexedNotNull { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                if (reference?.definingClass == owner && reference.name == name) index else null
            }
            require(matches.size == 1) { "Guard target $owner->$name differs" }
            var index = matches.single()
            if (!before) {
                index++
                if (index < instructions.size && instructions[index].opcode in MOVE_RESULTS) index++
            }
            insertions += index to mark(point)
        }
        target("Lah/runtime/guard/RuntimeSignerVerifier;", "verify", "h1", before = true)
        target(GUARD, "sha256", "h2", before = false)
        target("Lah/runtime/guard/IntegrityChecks;", "verifyPreReadSigner", "h3", before = false)
        target("Lah/runtime/loader/PayloadRuntime;", "openVerified", "h4", before = false)
        target("Lah/runtime/MemoryControls;", "apply", "h5", before = false)
        val returns = instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
        }
        require(returns.size == 1) { "Guard success return differs" }
        insertions += returns.single() to mark("h6")
        return copyMethod(method, insert(original, insertions))
    }

    private fun transformCoordinator(classDef: ClassDef): ClassDef {
        val signature = "install(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)Lah/runtime/bootstrap/BootstrapResult;"
        val methods = classDef.methods.map { method ->
            if (method.key() != signature) return@map method
            val original = method.implementation ?: error("Coordinator implementation is absent")
            val instructions = original.instructions.toList()
            val matches = (1 until instructions.size).filter { index ->
                val previous = (instructions[index - 1] as? ReferenceInstruction)?.reference as? FieldReference
                val current = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
                previous?.definingClass == "Lah/runtime/bootstrap/HardeningBootstrap\$State;" &&
                    previous.name == "READY" && instructions[index - 1].opcode == Opcode.SGET_OBJECT &&
                    current?.definingClass == COORDINATOR && current.name == "state" &&
                    instructions[index].opcode == Opcode.IPUT_OBJECT
            }
            require(matches.size == 1) { "Coordinator READY commit differs" }
            copyMethod(method, insert(original, listOf((matches.single() + 1) to mark("h7"))))
        }
        require(methods.count { it.key() == signature } == 1) { "Coordinator.install differs" }
        return copyClass(classDef, methods)
    }

    private fun boundaries(method: Method, entry: String, exit: String, returnOpcodes: Set<Opcode>): Method {
        val original = method.implementation ?: error("${method.key()} implementation is absent")
        val returns = original.instructions.toList().mapIndexedNotNull { index, instruction ->
            if (instruction.opcode in returnOpcodes) index else null
        }
        require(returns.isNotEmpty()) { "${method.key()} return topology differs" }
        val insertions = mutableListOf(0 to mark(entry))
        returns.forEach { index -> insertions += index to mark(exit) }
        return copyMethod(method, insert(original, insertions))
    }

    private fun focusBoundary(method: Method): Method {
        val original = method.implementation ?: error("focus implementation is absent")
        val parameterRegister = original.registerCount - 1
        require(parameterRegister in 0..15) { "focus parameter register cannot use invoke-35c" }
        val reference = ImmutableMethodReference(OBSERVER, "p15", listOf("Z"), "V")
        val call = BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, parameterRegister, 0, 0, 0, 0, reference)
        return copyMethod(method, insert(original, listOf(0 to call)))
    }

    private fun lifecycleOverride(
        owner: String,
        superType: String?,
        name: String,
        parameter: String?,
        entry: String,
        exit: String,
    ): Method {
        val actualSuper = requireNotNull(superType) { "lifecycle superclass is absent" }
        val parameters = parameter?.let { listOf(ImmutableMethodParameter(it, emptySet(), null)) } ?: emptyList()
        val parameterTypes = parameter?.let(::listOf) ?: emptyList()
        val registerCount = 1 + if (parameter == null) 0 else 1
        val superCall = BuilderInstruction35c(
            Opcode.INVOKE_SUPER,
            registerCount,
            0,
            if (parameter == null) 0 else 1,
            0,
            0,
            0,
            ImmutableMethodReference(actualSuper, name, parameterTypes, "V"),
        )
        val implementation = MutableMethodImplementation(registerCount).apply {
            addInstruction(mark(entry))
            addInstruction(superCall)
            addInstruction(mark(exit))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        return ImmutableMethod(owner, name, parameters, "V", 0x14, emptySet(), emptySet(), implementation)
    }

    private fun insert(
        original: org.jf.dexlib2.iface.MethodImplementation,
        insertions: List<Pair<Int, BuilderInstruction>>,
    ): org.jf.dexlib2.iface.MethodImplementation {
        require(insertions.groupBy { it.first }.values.all { it.size == 1 }) { "duplicate probe insertion index" }
        val originalInstructions = original.instructions.toList()
        val insertionAddresses = insertions.map { (index, instruction) ->
            require(index in 0..originalInstructions.size) { "probe insertion index escapes method" }
            originalInstructions.take(index).sumOf { it.codeUnits } to instruction.codeUnits
        }
        val mutable = MutableMethodImplementation(original)
        insertions.sortedByDescending { it.first }.forEach { (index, instruction) ->
            mutable.addInstruction(index, instruction)
        }
        val debugItems = original.debugItems.map { item ->
            val shifted = item.codeAddress + insertionAddresses.filter { (address) -> item.codeAddress >= address }.sumOf { it.second }
            when (item) {
                is org.jf.dexlib2.iface.debug.LineNumber -> ImmutableLineNumber(shifted, item.lineNumber)
                is org.jf.dexlib2.iface.debug.StartLocal ->
                    ImmutableStartLocal(shifted, item.register, item.name, item.type, item.signature)
                is org.jf.dexlib2.iface.debug.EndLocal ->
                    ImmutableEndLocal(shifted, item.register, item.name, item.type, item.signature)
                is org.jf.dexlib2.iface.debug.RestartLocal ->
                    ImmutableRestartLocal(shifted, item.register, item.name, item.type, item.signature)
                is org.jf.dexlib2.iface.debug.SetSourceFile -> ImmutableSetSourceFile(shifted, item.sourceFile)
                is org.jf.dexlib2.iface.debug.PrologueEnd -> ImmutablePrologueEnd(shifted)
                is org.jf.dexlib2.iface.debug.EpilogueBegin -> ImmutableEpilogueBegin(shifted)
                else -> error("unsupported DEX debug item type: ${item.javaClass.name}")
            }
        }
        return ImmutableMethodImplementation(mutable.registerCount, mutable.instructions, mutable.tryBlocks, debugItems)
    }

    private fun mark(point: String): BuilderInstruction35c = BuilderInstruction35c(
        Opcode.INVOKE_STATIC,
        0,
        0,
        0,
        0,
        0,
        0,
        ImmutableMethodReference(OBSERVER, point, emptyList(), "V"),
    )

    private fun copyMethod(method: Method, implementation: org.jf.dexlib2.iface.MethodImplementation): Method =
        ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags,
            method.annotations,
            method.hiddenApiRestrictions,
            implementation,
        )

    private fun copyClass(classDef: ClassDef, methods: Iterable<Method>): ClassDef = ImmutableClassDef(
        classDef.type,
        classDef.accessFlags,
        classDef.superclass,
        classDef.interfaces,
        classDef.sourceFile,
        classDef.annotations,
        classDef.fields,
        methods,
    )

    private fun Method.key(): String = buildString {
        append(name)
        append('(')
        parameters.forEach { append(it.type) }
        append(')')
        append(returnType)
    }

    private fun regular(value: String, label: String): Path = Path.of(value).toAbsolutePath().normalize().also {
        require(Files.isRegularFile(it)) { "$label DEX is missing" }
    }

    private fun readDex(path: Path, opcodes: Opcodes): DexBackedDexFile = Files.newInputStream(path).use { stream ->
        DexBackedDexFile.fromInputStream(opcodes, BufferedInputStream(stream))
    }

    private fun writeDex(opcodes: Opcodes, classes: Iterable<ClassDef>, output: Path) {
        val pool = DexPool(opcodes)
        classes.sortedBy { it.type }.forEach(pool::internClass)
        val dataStore = FileDataStore(output.toFile())
        try {
            pool.writeTo(dataStore)
        } finally {
            dataStore.close()
        }
        require(Files.size(output) > 112L) { "output DEX is truncated" }
    }

    private val MOVE_RESULTS = setOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_RESULT_OBJECT)
}
