package ua.dipdev.keybindings

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.KotlinSourceRoot
import org.jetbrains.kotlin.script.KotlinScriptDefinition

import javax.swing.*
import java.awt.*
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.jvm.internal.Reflection

/**
 * ToolWindow Factory for Keybindings plugin.
 */
class KeybindingsToolWindowFactory : ToolWindowFactory {

    private var fileEditor: FileEditor? = null

    private var kotlinVirtualFile: LightVirtualFile? = null

    private var mainPanel: JPanel? = null

    private var consoleView: ConsoleView? = null

    /**
     * Create and initialize plugin UI content.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val actionGroup = DefaultActionGroup()

        actionGroup.add(RunAnAction())

        val actionManager = ActionManager.getInstance()
        val actionToolbar = actionManager.createActionToolbar("Keybindings", actionGroup, true)

        val kotlinHelloWorldSourceCodeStr = "class Greeter() { \n" +
                "  fun greet() { \n" +
                "    println(\"Hello, World\"); \n" +
                "  } \n" +
                "}"

        kotlinVirtualFile = LightVirtualFile("main.kt", kotlinHelloWorldSourceCodeStr)

        val fileEditorProviderManager = FileEditorProviderManager.getInstance()

        val providers = fileEditorProviderManager.getProviders(project, kotlinVirtualFile!!)

        fileEditor = providers.get(0).createEditor(project, kotlinVirtualFile!!)

        val contentManager = toolWindow.contentManager

        mainPanel = JPanel(BorderLayout())

        mainPanel!!.add(actionToolbar.component, BorderLayout.NORTH)
        mainPanel!!.add(fileEditor!!.component, BorderLayout.CENTER)

        val textConsoleBuilderFactory = TextConsoleBuilderFactory.getInstance()

        consoleView = textConsoleBuilderFactory.createBuilder(project).console

        val consoleViewComponent = consoleView?.component

        val testActionGroup = DefaultActionGroup()

        val createConsoleActions = consoleView?.createConsoleActions()!!

        for (anAction in createConsoleActions) {
            testActionGroup.add(anAction)
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, testActionGroup, false);
        toolbar.setTargetComponent(consoleViewComponent);

        val ui = JPanel(BorderLayout())
        ui.add(consoleViewComponent, BorderLayout.CENTER);
        ui.add(toolbar.getComponent(), BorderLayout.WEST);

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel, ui)
        splitPane.dividerLocation = 600
        splitPane.dividerSize = 2

        val content = contentManager.factory.createContent(splitPane, null, false)

        contentManager.addContent(content)
    }

    /**
     * Run button action.
     */
    private inner class RunAnAction internal constructor() : AnAction("Run", "", AllIcons.General.Run) {

        /**
         * Action method.
         */
        override fun actionPerformed(anActionEvent: AnActionEvent) {
            consoleView?.clear();
            consoleView?.print("Start compiling...\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            val documents = TextEditorProvider.getDocuments(fileEditor!!)

            val sourceCode = documents?.get(0)!!.text

            val rootDirectory = File("hello_world_test")
            val sourceFile = File(rootDirectory, "main.kt")

            sourceFile.parentFile.mkdirs()

            Files.write(sourceFile.toPath(), sourceCode.toByteArray(StandardCharsets.UTF_8))

            val compilerClasspath = ideJdkClassesRoots() + ideLibFiles()

            val compilationResults = compile(sourceFile.absolutePath, compilerClasspath, rootDirectory)

            consoleView?.print("Compilation results $compilationResults\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            val classLoader = URLClassLoader.newInstance(arrayOf(rootDirectory.toURI().toURL()))

            val helloWorldClass = Class.forName("Greeter", true, classLoader)

            val helloWorldClassInstance = helloWorldClass.newInstance()

            val method = helloWorldClass.methods[0]

            val methodResults = method.invoke(helloWorldClassInstance)

            consoleView?.print("Method results $methodResults\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
    }

    /**
     * Return JDK class files.
     */
    private fun ideJdkClassesRoots(): List<File> =
            JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

    /**
     * Return IDE lib files
     */
    private fun ideLibFiles(): List<File> {
        val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")

        return File(ideJarPath).parentFile.listFiles().toList()
    }

    /**
     * Compile source Kotlin file.
     */
    @Suppress("unused") // Used via reflection.
    fun compile(sourceRoot: String, classpath: List<File>, compilerOutput: File): List<String> {
        val rootDisposable = Disposer.newDisposable()

        try {
            val messageCollector = ErrorMessageCollector()
            val configuration = createCompilerConfiguration(sourceRoot, classpath, compilerOutput, messageCollector)
            val kotlinEnvironment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(kotlinEnvironment)

            return when {
                messageCollector.hasErrors() -> messageCollector.errors
                state == null -> listOf("Compiler returned empty state.")
                else -> emptyList()
            }
        } finally {
            rootDisposable.dispose()
        }
    }

    private class ErrorMessageCollector : MessageCollector {
        val errors = ArrayList<String>()

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
            if (severity == CompilerMessageSeverity.ERROR || severity == CompilerMessageSeverity.EXCEPTION) {
                errors.add(MessageRenderer.PLAIN_FULL_PATHS.render(severity, message, location))
            }
        }

        override fun clear() {
            errors.clear()
        }

        override fun hasErrors() = errors.isNotEmpty()
    }

    /**
     * Create compile configuration for Kotlin compiler.
     */
    private fun createCompilerConfiguration(
            sourceRoot: String,
            classpath: List<File>,
            compilerOutput: File,
            messageCollector: MessageCollector
    ): CompilerConfiguration {
        return CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "LivePluginScript")
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)

            add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, KotlinScriptDefinition(Reflection.createKotlinClass(KotlinScriptTemplate::class.java)))
            add(JVMConfigurationKeys.CONTENT_ROOTS, KotlinSourceRoot(sourceRoot))

            for (path in classpath) {
                add(JVMConfigurationKeys.CONTENT_ROOTS, JvmClasspathRoot(path))
            }

            put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, false)
            put(JVMConfigurationKeys.OUTPUT_DIRECTORY, compilerOutput)
        }
    }
}
