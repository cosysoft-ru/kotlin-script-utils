#!/usr/bin/env kscript

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.function.Consumer

//INCLUDE https://raw.githubusercontent.com/cosysoft-ru/kotlin-script-utils/master/argument-tokenizer.kts

// Содержит общие функции исполнения shell команд из kotlin путем оборачивания строк с командами в отдельные процессы.

operator fun String.invoke(workingDirPath: String = ".",
                           outputFilePath: String = "",
                           errorFilePath: String = "",
                           redirectErrorToOutput: Boolean = false
): ShellCommandResult {
    val processBuilder = prepareCommandProcess(
        this,
        ProcessBuilderArgs(
            workingDirPath = workingDirPath,
            outputFilePath = outputFilePath,
            errorFilePath = errorFilePath,
            redirectErrorToOutput = redirectErrorToOutput
        )
    )
    val result = processBuilder.execute()
    return result
}

data class ShellCommandResult(
    val statusCode: Int,
    val stdOutput: String,
    val stdError: String
)

data class ProcessBuilderArgs(
    val workingDirPath: String = ".",
    val outputFilePath: String = "",
    val errorFilePath: String = "",
    val redirectErrorToOutput: Boolean = false
) {
    init {
        this.validateArgs(
            errorFilePath,
            redirectErrorToOutput
        )
    }

    private fun validateArgs(errorFilePath: String,
                             redirectErrorToOutput: Boolean
    ) {
        val isErrFile = errorFilePath.isNotBlank()
        val errFileWhileOutputRedirect = isErrFile && redirectErrorToOutput
        if (errFileWhileOutputRedirect) {
            throw IllegalArgumentException("Can not redirect sdterr to stdout if file for sdterr provided")
        }
    }
}

fun prepareCommandProcess(
    command: String,
    args: ProcessBuilderArgs
): ProcessBuilder {
    val workingDir = File(args.workingDirPath)
    val commandParts = ArgumentTokenizer.tokenize(command).toTypedArray()
    val processBuilder = ProcessBuilder(*commandParts).directory(workingDir)

    processBuilder.forwardStd(args)

    return processBuilder;
}

fun ProcessBuilder.forwardStd(args: ProcessBuilderArgs) : ProcessBuilder {
    if (args.outputFilePath.isNotBlank()) {
        this.redirectOutput(File(args.outputFilePath))
    } else {
        this.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    }

    if (args.errorFilePath.isNotBlank()) {
        this.redirectError(File(args.errorFilePath))
    } else if (args.redirectErrorToOutput) {
        this.redirectErrorStream(true)
    } else {
        this.redirectError(ProcessBuilder.Redirect.INHERIT)
    }
    return this
}

fun ProcessBuilder.execute() : ShellCommandResult {
    val process = this.start()

    val outputContent = mutableListOf<String>()
    val outputStreamGobbler = StreamGobbler(process.inputStream, Consumer { outputContent.add(it) })
    Executors.newSingleThreadExecutor().submit(outputStreamGobbler)

    val errorContent = mutableListOf<String>()
    val errorStreamGobbler = StreamGobbler(process.errorStream, Consumer { errorContent.add(it) })
    Executors.newSingleThreadExecutor().submit(errorStreamGobbler)

    process.waitFor()

    return ShellCommandResult(process.exitValue(), outputContent.joinToString("\n"), errorContent.joinToString("\n"))
}

class StreamGobbler(
    val inputStream: InputStream,
    val consumer: Consumer<String>
): Runnable {
    override fun run() {
        BufferedReader(InputStreamReader(inputStream))
            .lines()
            .forEach(consumer);
    }
}
