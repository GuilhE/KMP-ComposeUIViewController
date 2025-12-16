# Como Usar FlowProviders.getBuildWorkResult() - Alternativa ao buildFinished

## 📋 Problema

O método `buildFinished()` está **deprecado** e não funciona com **Configuration Caching**.

```kotlin
// ❌ DEPRECADO - não funciona com configuration caching
gradle.buildFinished { result ->
    // cleanup code
}
```

## ✅ Solução Moderna: FlowProviders

### Implementação Completa

```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import java.io.File

class KmpComposeUIViewControllerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val tempFolder = File(project.rootProject.layout.buildDirectory.asFile.get().path, "composeuiviewcontroller")
        
        // ✅ Usar FlowProviders para cleanup moderno
        project.gradle.lifecycle.beforeProject {
            configureCleanupWithFlowProviders(project, tempFolder)
        }
    }

    private fun configureCleanupWithFlowProviders(project: Project, tempFolder: File) {
        // Obter FlowScope e FlowProviders
        val flowScope = project.gradle.services.get(FlowScope::class.java)
        val flowProviders = project.gradle.services.get(FlowProviders::class.java)
        
        // Registrar ação que executa após o build
        flowScope.always {
            // Obter resultado do build
            val buildResult = flowProviders.buildWorkResult.get()
            
            // Se build falhou, fazer cleanup
            if (buildResult.failure.isPresent) {
                if (tempFolder.exists()) {
                    val deleted = tempFolder.deleteRecursively()
                    project.logger.lifecycle(
                        "\n> KmpComposeUIViewControllerPlugin: Build failed - Temp folder deleted: $deleted"
                    )
                }
            }
        }
    }
}
```

## 🔧 Explicação Detalhada

### 1. FlowScope
`FlowScope` permite registrar ações que executam em diferentes pontos do ciclo de vida:

```kotlin
flowScope.always {
    // Executa SEMPRE após o build (sucesso ou falha)
}
```

### 2. FlowProviders
`FlowProviders` fornece acesso aos resultados do build de forma compatível com configuration caching:

```kotlin
val buildResult = flowProviders.buildWorkResult.get()

// Verificar se houve falha
if (buildResult.failure.isPresent) {
    val exception = buildResult.failure.get()
    // Fazer algo
}
```

## 📝 Implementação para nosso Plugin

### Opção 1: Usando FlowProviders (Moderno)

```kotlin
private fun Project.configureCleanTempFilesLogic(tempFolder: File) {
    // Task para cleanup manual
    tasks.register(TASK_CLEAN_TEMP_FILES_FOLDER) { task ->
        task.doLast { 
            if (tempFolder.exists()) {
                tempFolder.deleteRecursively()
            }
        }
    }

    tasks.named("clean").configure { it.finalizedBy(TASK_CLEAN_TEMP_FILES_FOLDER) }
    
    // ✅ Cleanup usando FlowProviders (moderno)
    try {
        val flowScope = gradle.services.get(FlowScope::class.java)
        val flowProviders = gradle.services.get(FlowProviders::class.java)
        
        flowScope.always {
            val buildResult = flowProviders.buildWorkResult.get()
            if (buildResult.failure.isPresent && tempFolder.exists()) {
                val deleted = tempFolder.deleteRecursively()
                logger.lifecycle("\n> $LOG_TAG:\n\t> Build failed - Temp folder deleted: $deleted")
            }
        }
    } catch (e: Exception) {
        // Fallback para buildFinished se FlowProviders não estiver disponível
        @Suppress("DEPRECATION")
        gradle.buildFinished { result ->
            if (result.failure != null && tempFolder.exists()) {
                val deleted = tempFolder.deleteRecursively()
                logger.info("\n> $LOG_TAG:\n\t> Build failed - Temp folder deleted: $deleted")
            }
        }
    }
}
```

### Opção 2: Abordagem Simplificada (Para Testes)

Para os nossos testes, a solução mais simples é usar **task finalization**:

```kotlin
private fun Project.configureCleanTempFilesLogic(tempFolder: File) {
    // Criar uma task que sempre limpa em caso de falha
    val cleanupTask = tasks.register("cleanupTempOnFailure") { task ->
        task.doLast {
            if (gradle.startParameter.isBuildFailed && tempFolder.exists()) {
                tempFolder.deleteRecursively()
            }
        }
    }
    
    // Garantir que sempre executa
    gradle.taskGraph.whenReady {
        allTasks.lastOrNull()?.finalizedBy(cleanupTask)
    }
}
```

## 🎯 Recomendação Final

### Para produção (compatível com configuration caching):
```kotlin
private fun Project.configureCleanTempFilesLogic(tempFolder: File) {
    tasks.register(TASK_CLEAN_TEMP_FILES_FOLDER) { task ->
        task.doLast { 
            if (tempFolder.exists()) {
                tempFolder.deleteRecursively()
            }
        }
    }

    tasks.named("clean").configure { it.finalizedBy(TASK_CLEAN_TEMP_FILES_FOLDER) }
    
    // Usar FlowProviders com fallback
    try {
        val flowScope = gradle.services.get(FlowScope::class.java)
        val flowProviders = gradle.services.get(FlowProviders::class.java)
        
        flowScope.always {
            val buildResult = flowProviders.buildWorkResult.get()
            if (buildResult.failure.isPresent && tempFolder.exists()) {
                tempFolder.deleteRecursively()
            }
        }
    } catch (e: Exception) {
        // Fallback para gradle < 8.1
        @Suppress("DEPRECATION")
        gradle.buildFinished { result ->
            if (result.failure != null && tempFolder.exists()) {
                tempFolder.deleteRecursively()
            }
        }
    }
}
```

## ⚠️ Importante

### Compatibilidade
- **FlowProviders:** Gradle 8.1+
- **buildFinished:** Todas as versões (mas deprecado)

### Configuration Caching
- **FlowProviders:** ✅ Compatível
- **buildFinished:** ❌ Não funciona

### Para nosso caso
Como nossos **testes precisam** que o cleanup aconteça imediatamente e **não usamos configuration caching nos testes**, a solução atual com `buildFinished` é **adequada** até migrarmos para FlowProviders.

## 📚 Referências

- [Gradle FlowProviders Documentation](https://docs.gradle.org/current/javadoc/org/gradle/api/flow/FlowProviders.html)
- [Gradle FlowScope Documentation](https://docs.gradle.org/current/javadoc/org/gradle/api/flow/FlowScope.html)
- [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)

