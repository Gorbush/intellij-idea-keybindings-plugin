package ua.dipdev.keybindings;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;

import javax.swing.*;
import javax.tools.JavaCompiler;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * ToolWindow Factory for Keybindings plugin.
 */
public class KeybindingsToolWindowFactory implements ToolWindowFactory {

    private EditorTextField textEditor;

    private JPanel mainPanel;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        actionGroup.add(new RunAnAction());

        ActionManager actionManager = ActionManager.getInstance();
        ActionToolbar actionToolbar = actionManager.createActionToolbar("Keybindings", actionGroup, true);

        textEditor = new EditorTextField(JavaReferenceEditorUtil
                            .createDocument("", project, false), project, StdFileTypes.JAVA);

        textEditor.setText("public class HelloWorld {\n" +
                "    public HelloWorld() {\n" +
                "        System.out.println(\"Hello, World\");\n" +
                "    }\n" +
                "}");

        ContentManager contentManager = toolWindow.getContentManager();

        mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
        mainPanel.add(textEditor, BorderLayout.CENTER);

        Content content = contentManager.getFactory().createContent(mainPanel, null, false);

        contentManager.addContent(content);
    }

    private class RunAnAction extends AnAction {

        RunAnAction() {
            super("Run", "", AllIcons.General.Run);
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            Project project = anActionEvent.getProject();

            JavacCompilerTool javacCompilerTool = new JavacCompilerTool();

            TextConsoleBuilder textConsoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
            ConsoleView consoleView = textConsoleBuilder.getConsole();
            consoleView.print("Test", ConsoleViewContentType.SYSTEM_OUTPUT);

            mainPanel.add(consoleView.getComponent(), BorderLayout.SOUTH);

            try {
                JavaCompiler compiler = javacCompilerTool.createCompiler();

                String sourceCode = textEditor.getText();

                File rootDirectory = new File("hello_world_test");
                File sourceFile = new File(rootDirectory, "HelloWorld.java");
                sourceFile.getParentFile().mkdirs();
                Files.write(sourceFile.toPath(), sourceCode.getBytes(StandardCharsets.UTF_8));

                compiler.run(null, null, null, sourceFile.getPath());

                consoleView.print("Compiled.", ConsoleViewContentType.SYSTEM_OUTPUT);

                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { rootDirectory.toURI().toURL() });

                Class<?> helloWorldClass = Class.forName("HelloWorld", true, classLoader);

                Object helloWorldClassInstance = helloWorldClass.newInstance();

                Method method = helloWorldClass.getMethods()[0];

                Object methodResult = method.invoke(helloWorldClassInstance);

                consoleView.print(methodResult.toString(), ConsoleViewContentType.SYSTEM_OUTPUT);

//                MessageView messageView = MessageView.SERVICE.getInstance(project);
//
//                Content content = ContentFactory.SERVICE.getInstance().createContent(
//                        consoleView.getComponent(), "Test title", true);
//                messageView.getContentManager().addContent(content);
//                messageView.getContentManager().setSelectedContent(content);
            } catch (CannotCreateJavaCompilerException | IOException | InstantiationException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
                consoleView.print(e.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
            }

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

        private VirtualFile getCurrentFile(final Project project) {
            Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();

            if (selectedTextEditor == null) {
                return null;
            }

            Document document = selectedTextEditor.getDocument();

            return FileDocumentManager.getInstance().getFile(document);
        }
    }

    private class MyConfigurationType extends ApplicationConfigurationType {
        private ConfigurationFactory myConfigurationFactory = new ConfigurationFactoryEx(this) {
            @NotNull
            @Override
            public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
                //second parameter is used to pass the project
                return createConfiguration("MyConfig",
                        ApplicationConfigurationType.getInstance().getConfigurationFactories()[0].createTemplateConfiguration(project));
            }

            @Override
            public RunConfiguration createConfiguration(String name, RunConfiguration template) {
                return new ApplicationConfiguration(name, template.getProject(), this) {

                    @Override
                    public boolean isCompileBeforeLaunchAddedByDefault() {
                        return false;
                    }

//                @Override  //Not yet available in 2017.2,
//                public  boolean isBuildBeforeLaunchAddedByDefault() {
//                    System.out.println("legacy.MyCustomLaunchAction.isBuildBeforeLaunchAddedByDefault");
//                    return true;
//                }

                };
            }


            @Override
            public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
                ((ModuleBasedConfiguration) configuration).onNewConfigurationCreated();
            }
        };

        @Override
        public String getDisplayName() {
            return "ConfigWithoutCompile";
        }

        @Override
        public ConfigurationFactory[] getConfigurationFactories() {
            return new ConfigurationFactory[]{myConfigurationFactory};
        }

        @NotNull
        @Override
        public String getId() {
            return "ConfigWithoutCompile";
        }
    }
}
