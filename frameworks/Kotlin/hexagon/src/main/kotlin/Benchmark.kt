package com.hexagonkt

import com.hexagonkt.helpers.Environment.systemSetting
import com.hexagonkt.serialization.JsonFormat
import com.hexagonkt.serialization.convertToMap
import com.hexagonkt.serialization.serialize
import com.hexagonkt.server.*
import com.hexagonkt.server.jetty.JettyServletAdapter
import com.hexagonkt.server.servlet.ServletServer
import com.hexagonkt.settings.SettingsManager.settings
import com.hexagonkt.templates.TemplateManager.render
import com.hexagonkt.templates.TemplatePort
import com.hexagonkt.templates.pebble.PebbleAdapter

import java.util.*
import java.util.concurrent.ThreadLocalRandom
import javax.servlet.annotation.WebListener
import java.net.InetAddress.getByName as address

// DATA CLASSES
internal data class Message(val message: String)
internal data class Fortune(val _id: Int, val message: String)
internal data class World(val _id: Int, val id: Int, val randomNumber: Int)

// CONSTANTS
private const val TEXT_MESSAGE: String = "Hello, World!"
private const val QUERIES_PARAM: String = "queries"

private val contentTypeJson: String = JsonFormat.contentType

internal val benchmarkStores: Map<String, BenchmarkStore> by lazy {
    mapOf(
        "mongodb" to BenchmarkMongoDbStore("mongodb"),
        "postgresql" to BenchmarkSqlStore("postgresql")
    )
}

internal val benchmarkTemplateEngines: Map<String, TemplatePort> by lazy {
    mapOf("pebble" to PebbleAdapter)
}

internal val benchmarkServer: Server by lazy {
    val engine = when (systemSetting("WEBENGINE", "jetty")) {
        "jetty" -> JettyServletAdapter()
        else -> error("Unsupported server engine")
    }

    Server(engine, settings, router())
}

// UTILITIES
internal fun randomWorld(): Int = ThreadLocalRandom.current().nextInt(WORLD_ROWS) + 1

private fun Call.returnWorlds(worldsList: List<World>) {
    val worlds = worldsList.map { it.convertToMap() - "_id" }

    ok(worlds.serialize(), contentTypeJson)
}

private fun Call.getWorldsCount() = request[QUERIES_PARAM]?.toIntOrNull().let {
    when {
        it == null -> 1
        it < 1 -> 1
        it > 500 -> 500
        else -> it
    }
}

// HANDLERS
private fun Call.listFortunes(store: BenchmarkStore, templateKind: String, templateEngine: TemplatePort) {
    val fortunes = store.findAllFortunes() + Fortune(0, "Additional fortune added at request time.")
    val sortedFortunes = fortunes.sortedBy { it.message }
    val context = mapOf("fortunes" to sortedFortunes)
    val defaultLocale = Locale.getDefault()

    response.contentType = "text/html;charset=utf-8"
    ok(render(templateEngine, "fortunes.$templateKind.html", defaultLocale, context))
}

private fun Call.dbQuery(store: BenchmarkStore) {
    val world = store.findWorlds(1).first().convertToMap() - "_id"

    ok(world.serialize(), contentTypeJson)
}

private fun Call.getWorlds(store: BenchmarkStore) {
    returnWorlds(store.findWorlds(getWorldsCount()))
}

private fun Call.updateWorlds(store: BenchmarkStore) {
    returnWorlds(store.replaceWorlds(getWorldsCount()))
}

// CONTROLLER
private fun router(): Router = router {

    before {
        response.addHeader("Server", "Servlet/3.1")
        response.addHeader("Transfer-Encoding", "chunked")
    }

    get("/plaintext") { ok(TEXT_MESSAGE, "text/plain") }
    get("/json") { ok(Message(TEXT_MESSAGE).serialize(), contentTypeJson) }

    benchmarkStores.forEach { (storeEngine, store) ->
        benchmarkTemplateEngines.forEach { templateKind ->
            val path = "/$storeEngine/${templateKind.key}/fortunes"

            get(path) { listFortunes(store, templateKind.key, templateKind.value) }
        }

        get("/$storeEngine/db") { dbQuery(store) }
        get("/$storeEngine/query") { getWorlds(store) }
        get("/$storeEngine/update") { updateWorlds(store) }
    }
}

// SERVERS
@WebListener class Web : ServletServer (router())

fun main() {
    benchmarkServer.run()
}
