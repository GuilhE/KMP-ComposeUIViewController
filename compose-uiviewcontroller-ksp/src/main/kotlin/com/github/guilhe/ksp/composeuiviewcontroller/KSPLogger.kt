package com.github.guilhe.ksp.composeuiviewcontroller

import com.google.devtools.ksp.processing.KSPLogger

public class KspLogger(private val kspLogger: KSPLogger) : Logger {

    public override fun logging(message: String): Unit = kspLogger.logging(message)

    public override fun info(message: String): Unit = kspLogger.info(message)

    public override fun warn(message: String): Unit = kspLogger.warn(message)

    public override fun error(message: String): Unit = kspLogger.error(message)

    public override fun exception(e: Throwable): Unit = kspLogger.exception(e)
}

public interface Logger {

    public fun logging(message: String)

    public fun info(message: String)

    public fun warn(message: String)

    public fun error(message: String)

    public fun exception(e: Throwable)

    public companion object {
        public lateinit var instance: Logger
    }
}