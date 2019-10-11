#!/usr/bin/env kscript

//DEPS com.beust:klaxon:5.0.1

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.net.URL
import kotlin.system.exitProcess


/**
 * @param issueURL URL задачи из локального трекера
 * @return номер задачи repo-mos.ru указанной в локальной задаче
 */
fun getRepoMosIssueByLocalIssue(issueURL : String) : String? {
    val issueInfoJson = StringBuilder(URL(issueURL).readText())
        val jsonParser = Parser.default()
    val issueInfoJsonPojo = jsonParser.parse(issueInfoJson) as JsonObject
    val repoMosIssue =
        try {
            issueInfoJsonPojo
                .array<JsonObject>("issues")
                ?.get(0)
                ?.array<JsonObject>("custom_fields")
                ?.first { it.string("name") == "RepoMos" }
                ?.string("value")
                ?.split("/")
                ?.last()
        } catch (e : IndexOutOfBoundsException) {
            println("Entered local issue #$issueNum is not valid!")
            exitProcess(1)
        }
    return repoMosIssue
}