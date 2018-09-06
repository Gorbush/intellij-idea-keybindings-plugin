package ua.dipdev.keybindings

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.JavaReferenceEditorUtil
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.jvm.internal.Reflection

/**
 * ToolWindow Factory for Keybindings plugin.
 */
class KeybindingsToolWindowFactory : ToolWindowFactory {

    private var textEditor: EditorTextField? = null

    private var mainPanel: JPanel? = null

    private var consoleView: ConsoleView? = null

    private var outputStream: ByteArrayOutputStream? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val actionGroup = DefaultActionGroup()

        actionGroup.add(RunAnAction())

        val actionManager = ActionManager.getInstance()
        val actionToolbar = actionManager.createActionToolbar("Keybindings", actionGroup, true)

        textEditor = EditorTextField(JavaReferenceEditorUtil
                .createDocument("", project, false), project, StdFileTypes.JAVA)

//        textEditor!!.setText("public class HelloWorld {\n" +
//                "    public HelloWorld() {\n" +
//                "        System.out.println(\"Hello, World\");\n" +
//                "    }\n" +
//                "}")

        textEditor!!.setText("class Greeter() { \n" +
                "  fun greet() { \n" +
                "    println(\"Hello, World\"); \n" +
                "  } \n" +
                "} \n" +
                " \n" +
                "fun main(args : Array<String>) { \n" +
                "  Greeter().greet() \n" +
                "}");

        val contentManager = toolWindow.contentManager

        mainPanel = JPanel(BorderLayout())

        mainPanel!!.add(actionToolbar.component, BorderLayout.NORTH)
        mainPanel!!.add(textEditor, BorderLayout.CENTER)

        val content = contentManager.factory.createContent(mainPanel, null, false)

        contentManager.addContent(content)
    }

    private inner class RunAnAction internal constructor() : AnAction("Run", "", AllIcons.General.Run) {

        private fun ideJdkClassesRoots(): List<File> =
                JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

        private fun ideLibFiles(): List<File> {
            val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")

            return File(ideJarPath).parentFile.listFiles().toList()
        }

        private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
//            val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") })
//            return pluginDescriptors.map { it -> it.path }

            return arrayListOf();
        }

        override fun actionPerformed(anActionEvent: AnActionEvent) {
            val sourceCode = textEditor!!.text

            val rootDirectory = File("hello_world_test")
            val sourceFile = File(rootDirectory, "Greeter.kt")

            sourceFile.parentFile.mkdirs()

            Files.write(sourceFile.toPath(), sourceCode.toByteArray(StandardCharsets.UTF_8))

            println(sourceFile.absoluteFile)

            val compilerClasspath = ideJdkClassesRoots() + ideLibFiles()

            val result = compile(sourceFile.absolutePath, compilerClasspath, rootDirectory)

            println(result)

            val classLoader = URLClassLoader.newInstance(arrayOf(rootDirectory.toURI().toURL()))

            val helloWorldClass = Class.forName("Greeter", true, classLoader)

            val helloWorldClassInstance = helloWorldClass.newInstance()

            val method = helloWorldClass.methods[0]

            val methodResult = method.invoke(helloWorldClassInstance)

            println(methodResult)

            if (consoleView == null) {
                val textConsoleBuilderFactory = TextConsoleBuilderFactory.getInstance()

                val project = anActionEvent.project!!
                val consoleView = textConsoleBuilderFactory.createBuilder(project).console

                outputStream = ByteArrayOutputStream()

                val processHandler = object : ProcessHandler() {
                    override fun getProcessInput(): OutputStream? {
                        return outputStream
                    }

                    override fun detachIsDefault(): Boolean {
                        return false
                    }

                    override fun detachProcessImpl() {
                    }

                    override fun destroyProcessImpl() {
                    }
                }

                consoleView.attachToProcess(processHandler)
                processHandler.startNotify()

                mainPanel!!.add(consoleView.component, BorderLayout.SOUTH)
            }

            consoleView?.print("Test me", ConsoleViewContentType.SYSTEM_OUTPUT)

            outputStream?.write("Test it".toByteArray())
            outputStream?.flush()

            /*val javacCompilerTool = JavacCompilerTool()

            val textConsoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project!!)
            val consoleView = textConsoleBuilder.console
            consoleView.print("Test", ConsoleViewContentType.SYSTEM_OUTPUT)

            mainPanel!!.add(consoleView.component, BorderLayout.SOUTH)

            try {
                val compiler = javacCompilerTool.createCompiler()

                val sourceCode = textEditor!!.text

                val rootDirectory = File("hello_world_test")
                val sourceFile = File(rootDirectory, "HelloWorld.java")
                sourceFile.parentFile.mkdirs()
                Files.write(sourceFile.toPath(), sourceCode.toByteArray(StandardCharsets.UTF_8))

                compiler.run(null, null, null, sourceFile.path)

                consoleView.print("Compiled.", ConsoleViewContentType.SYSTEM_OUTPUT)

                val classLoader = URLClassLoader.newInstance(arrayOf(rootDirectory.toURI().toURL()))

                val helloWorldClass = Class.forName("HelloWorld", true, classLoader)

                val helloWorldClassInstance = helloWorldClass.newInstance()

                val method = helloWorldClass.methods[0]

                val methodResult = method.invoke(helloWorldClassInstance)

                consoleView.print(methodResult.toString(), ConsoleViewContentType.SYSTEM_OUTPUT)

                //                MessageView messageView = MessageView.SERVICE.getInstance(project);
                //
                //                Content content = ContentFactory.SERVICE.getInstance().createContent(
                //                        consoleView.getComponent(), "Test title", true);
                //                messageView.getContentManager().addContent(content);
                //                messageView.getContentManager().setSelectedContent(content);
            } catch (e: CannotCreateJavaCompilerException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            } catch (e: IOException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            } catch (e: InstantiationException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            } catch (e: ClassNotFoundException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            } catch (e: IllegalAccessException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            } catch (e: InvocationTargetException) {
                consoleView.print(e.message!!, ConsoleViewContentType.ERROR_OUTPUT)
            }*/

            /*CompilerManagerImpl compilerManager = (CompilerManagerImpl) CompilerManager.getInstance(project);
            compilerManager.compileJavaCode()*/


            /*File projectDirectory = new File("HelloWorld");

            if (!projectDirectory.exists()) {
                projectDirectory.mkdirs();
            }

            try (PrintWriter printWriter = new PrintWriter("HelloWorld.java")) {
                printWriter.println(textEditor.getText());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            RunManager runManager = RunManager.getInstance(anActionEvent.getProject());
            ApplicationConfiguration applicationConfiguration =
                    new ApplicationConfiguration("HelloWorld", anActionEvent.getProject(), ApplicationConfigurationType.getInstance());

            applicationConfiguration.MAIN_CLASS_NAME = "HelloWorld";
            applicationConfiguration.WORKING_DIRECTORY = projectDirectory.getAbsolutePath();

            //applicationConfiguration.setModule(ModuleManager.getInstance(anActionEvent.getProject()).findModuleByName("myplugin"));

            RunnerAndConfigurationSettings runManagerConfiguration = runManager.createConfiguration(applicationConfiguration, applicationConfiguration.getFactory());
            runManager.addConfiguration(runManagerConfiguration, false);

            Executor runExecutorInstance = DefaultRunExecutor.getRunExecutorInstance();
            ProgramRunnerUtil.executeConfiguration(anActionEvent.getProject(), runManagerConfiguration, runExecutorInstance);*/


            //            TextConsoleBuilder textConsoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
            //            ConsoleView consoleView = textConsoleBuilder.getConsole();
            //            consoleView.print("Test", ConsoleViewContentType.SYSTEM_OUTPUT);


            //            ConfigurationContext configurationContext = ConfigurationContext.getFromContext(anActionEvent.getDataContext());
            //
            //            RunnerAndConfigurationSettings runnerAndConfigurationSettings = configurationContext.findExisting();
            //
            //            if (runnerAndConfigurationSettings != null) {
            //                ExecutionUtil.runConfiguration(runnerAndConfigurationSettings, DefaultRunExecutor.getRunExecutorInstance());
            //            }
        }
    }

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
