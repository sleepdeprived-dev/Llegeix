package com.david.llegeix.translate

import com.david.llegeix.platform.desktopDataDirectory
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Bergamot on the Mac: the translator binary and Mozilla's models for it.
 *
 * The models cover Catalan and Romanian to and from English, so Catalan and
 * Romanian reach each other through English, as ML Kit's do on the phone. They
 * download on first use, like ML Kit's, into Application Support, from the
 * Remote Settings feed Firefox reads. The versions are the ones checked when
 * the port began (tools/macos).
 */
internal object Bergamot {

    /** Each model the app uses, at the version it was checked against. */
    private val VERSIONS = mapOf("caen" to "2.0", "enca" to "2.0", "enro" to "1.0", "roen" to "1.0")

    private const val FEED = "https://firefox.settings.services.mozilla.com/v1"

    private val modelsDirectory get() = File(desktopDataDirectory, "translation")

    /**
     * The translator itself. Bundled with the app; `llegeix.bergamot` points at
     * a development build instead (see tools/macos/build-bergamot.sh).
     */
    private val binary: File? by lazy {
        listOfNotNull(
            System.getProperty("llegeix.bergamot")?.let(::File),
            System.getProperty("compose.application.resources.dir")?.let { File(it, "bergamot") },
        ).firstOrNull { it.canExecute() }
    }

    /** The models between two languages, through English where there is no direct one. */
    fun route(from: String, to: String): List<String> = when {
        from == to -> emptyList()
        from == "en" || to == "en" -> listOf(from + to)
        else -> listOf(from + "en", "en" + to)
    }.onEach { require(it in VERSIONS) { "No model for $it" } }

    fun isInstalled(pair: String): Boolean = File(modelsDirectory, "$pair/config.yml").exists()

    /** Fetch any of [pairs] not already here. Blocking; call off the main thread. */
    fun install(pairs: List<String>) {
        val missing = pairs.filterNot(::isInstalled)
        if (missing.isEmpty()) return
        val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        fun get(url: String): HttpResponse<ByteArray> =
            http.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofByteArray())
                .also { if (it.statusCode() != 200) throw IOException("HTTP ${it.statusCode()} for $url") }

        val base = JSONObject(get("$FEED/").body().decodeToString())
            .getJSONObject("capabilities").getJSONObject("attachments").getString("base_url")
        val records = JSONObject(
            get("$FEED/buckets/main/collections/translations-models/records").body().decodeToString(),
        ).getJSONArray("data")

        for (pair in missing) {
            val from = pair.take(2)
            val to = pair.drop(2)
            val files = (0 until records.length()).map(records::getJSONObject).filter {
                it.optString("fromLang") == from && it.optString("toLang") == to &&
                    it.optString("version") == VERSIONS.getValue(pair)
            }
            if (files.isEmpty()) throw IOException("No $pair model in the feed")

            // Into a scratch folder first, so an interrupted download never
            // leaves a pair that looks installed and is not.
            val staging = File(modelsDirectory, "$pair.partial").apply { deleteRecursively(); mkdirs() }
            for (record in files) {
                val url = base + record.getJSONObject("attachment").getString("location")
                File(staging, record.getString("name")).writeBytes(get(url).body())
            }
            val dir = File(modelsDirectory, pair)
            File(staging, "config.yml").writeText(config(dir, pair))
            dir.deleteRecursively()
            Files.move(staging.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /** Translate [text] through one model. Blocking; call off the main thread. */
    fun run(pair: String, text: String): String {
        val program = binary ?: throw IOException("The translator is not installed")
        val process = ProcessBuilder(
            program.path,
            "--model-config-paths",
            File(modelsDirectory, "$pair/config.yml").path,
        ).start()
        process.outputStream.bufferedWriter().use { it.write(text.replace('\n', ' ')); it.newLine() }
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("The translator did not answer")
        }
        if (process.exitValue() != 0) throw IOException("The translator failed")
        return out.trim()
    }

    private fun config(dir: File, pair: String) = """
        models: [${dir.path}/model.$pair.intgemm.alphas.bin]
        vocabs: [${dir.path}/vocab.$pair.spm, ${dir.path}/vocab.$pair.spm]
        shortlist: [${dir.path}/lex.50.50.$pair.s2t.bin, false]
        beam-size: 1
        normalize: 1.0
        word-penalty: 0
        max-length-break: 128
        mini-batch-words: 1024
        workspace: 128
        max-length-factor: 2.0
        skip-cost: true
        cpu-threads: 0
        quiet: true
        quiet-translation: true
        gemm-precision: int8shiftAlphaAll
        alignment: soft
    """.trimIndent() + "\n"
}
