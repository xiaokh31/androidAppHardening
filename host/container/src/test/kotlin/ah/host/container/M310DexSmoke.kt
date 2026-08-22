package ah.host.container

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.dexlib2.writer.pool.DexPool

/** Test-only feasibility probe for deterministic post-build DEX rewriting. */
object M310DexSmoke {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 2) { "expected input and output DEX paths" }
        val input = Path.of(arguments[0]).toAbsolutePath().normalize()
        val output = Path.of(arguments[1]).toAbsolutePath().normalize()
        require(Files.isRegularFile(input)) { "input DEX is missing" }
        require(!Files.exists(output)) { "output DEX already exists" }
        val repeatedOutput = output.resolveSibling("${output.fileName}.repeat")
        require(!Files.exists(repeatedOutput)) { "repeated output DEX already exists" }
        Files.createDirectories(output.parent)

        val opcodes = Opcodes.forApi(36)
        val dex = Files.newInputStream(input).use { stream ->
            DexBackedDexFile.fromInputStream(opcodes, BufferedInputStream(stream))
        }
        require(dex.classes.isNotEmpty()) { "input DEX contains no classes" }
        writeDex(opcodes, dex, output)
        writeDex(opcodes, dex, repeatedOutput)
        require(Files.size(output) > 112L) { "rewritten DEX is truncated" }
        require(Files.readAllBytes(output).contentEquals(Files.readAllBytes(repeatedOutput))) {
            "independent DEX rewrites differ"
        }
        println("M3-10 DEX smoke PASS classes=${dex.classes.size}")
    }

    private fun writeDex(opcodes: Opcodes, dex: DexBackedDexFile, output: Path) {
        val pool = DexPool(opcodes)
        dex.classes.sortedBy { it.type }.forEach(pool::internClass)
        val dataStore = FileDataStore(output.toFile())
        try {
            pool.writeTo(dataStore)
        } finally {
            dataStore.close()
        }
    }
}
