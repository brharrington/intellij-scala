package org.jetbrains.plugins.scala
package console

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.execution.configurations._
import com.intellij.execution.filters.{Filter, TextConsoleBuilder, TextConsoleBuilderImpl, TextConsoleBuilderFactory}

import com.intellij.execution.impl.{ConsoleInputListener, ConsoleViewImpl}
import com.intellij.execution.runners.{ExecutionEnvironment}
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.{CantRunException, ExecutionException, Executor}
import com.intellij.ide.util.{PropertiesComponent, DirectoryChooser}
import com.intellij.ide.{IdeBundle, CommonActionsManager}
import com.intellij.openapi.actionSystem.{CustomShortcutSet, ShortcutSet, AnActionEvent, AnAction}
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.openapi.fileTypes.{FileTypeManager, FileType}

import com.intellij.openapi.vfs.{LocalFileSystem, JarFileSystem, VirtualFile}
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.{PsiDocumentManager, PsiFileFactory, PsiManager}
import java.awt.event.{KeyEvent, InputEvent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import icons.Icons
import java.lang.String
import javax.swing.filechooser.{FileFilter, FileView}
import javax.swing.{Icon, JFileChooser, KeyStroke}
import org.jdom.Element
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.module.{ModuleUtil, ModuleManager, Module}
import com.intellij.vcsUtil.VcsUtil
import java.util.Arrays
import com.intellij.facet.FacetManager
import lang.psi.api.ScalaFile
import settings.ScalaApplicationSettings
import util.ScalaUtils
import com.intellij.openapi.projectRoots.{JdkUtil, JavaSdkType}
import com.intellij.execution.console.{LanguageConsoleViewImpl, LanguageConsoleImpl}
import java.io._
import lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.{TextRange, JDOMExternalizer}
import config.ScalaFacet
import collection.JavaConversions._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaScriptConsoleRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "org.jetbrains.plugins.scala.compiler.rt.ConsoleRunner"
  private var javaOptions = ""
  private var consoleArgs = ""
  private var workingDirectory = {
    val base = getProject.getBaseDir
    if (base != null) base.getPath
    else ""
  }

  def getJavaOptions = javaOptions

  def setJavaOptions(s: String) {javaOptions = s}

  def getConsoleArgs: String = consoleArgs

  def setConsoleArgs(s: String) {consoleArgs = s}

  def getWorkingDirectory = workingDirectory

  def setWorkingDirecoty(s: String) {workingDirectory = s}

  def apply(params: ScalaScriptConsoleRunConfigurationForm) {
    setJavaOptions(params.getJavaOptions)
    setConsoleArgs(params.getConsoleArgs)
    setWorkingDirecoty(params.getWorkingDirectory)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module);
    val sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module);
    }
    val sdkType = sdk.getSdkType

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters();

        params.setJdk(sdk)

        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
        //params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")
//        params.getVMParametersList.add(SCALA_HOME  + scalaSdkPath)

        params.getClassPath.addAllFiles(facet.files)

        val rtJarPath = PathUtil.getJarPathForClass(classOf[_root_.org.jetbrains.plugins.scala.compiler.rt.ConsoleRunner])
        params.getClassPath.add(rtJarPath)
        params.setWorkingDirectory(workingDirectory)
        params.setMainClass(MAIN_CLASS)
        if (JdkUtil.useDynamicClasspath(getProject)) {
          try {
            val fileWithParams: File = File.createTempFile("scalaconsole", ".tmp")
            val printer: PrintStream = new PrintStream(new FileOutputStream(fileWithParams))
            printer.println("-classpath")
            printer.println(getClassPath(project, facet))
            val parms: Array[String] = ParametersList.parse(consoleArgs)
            for (parm <- parms) {
              printer.println(parm)
            }
            printer.close
            params.getProgramParametersList.add("@" + fileWithParams.getPath)
          }
          catch {
            case e: IOException => {
              //todo:
            }
          }
        } else {
          params.getProgramParametersList.add("-classpath")
          params.getProgramParametersList.add(getClassPath(project, facet))
          params.getProgramParametersList.addParametersString(consoleArgs)
        }
        return params
      }
    }

    val consoleBuilder = new TextConsoleBuilderImpl(project) {
      val filters = new ArrayBuffer[Filter]
      override def getConsole: ConsoleView = {
        val consoleView = new ConsoleViewImpl(project, false, ScalaFileType.SCALA_FILE_TYPE)
        val builder = new StringBuilder()
        consoleView.addConsoleUserInputListener(new ConsoleInputListener {
          def textEntered(userText: String) {
            if (builder.toString != "") builder.append("\n")
            builder.append(userText)
          }
        })

        val saveAction = new AnAction {
          private val shortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
          setShortcutSet(shortcutSet)
          registerCustomShortcutSet(shortcutSet, consoleView.getComponent)
          def actionPerformed(e: AnActionEvent) {
            val lastFilePath = PropertiesComponent.getInstance(project).getValue("last_opened_file_path")
            val fileChooser = new JFileChooser(lastFilePath)
            val fileView = new FileView() {
              override def getIcon(f: File): Icon = {
                if (f.isDirectory) return super.getIcon(f)
                val fileType = FileTypeManager.getInstance.getFileTypeByFileName(f.getName)
                return fileType.getIcon
              }
            };
            fileChooser.setFileView(fileView)
            fileChooser.setMultiSelectionEnabled(true)
            fileChooser.setAcceptAllFileFilterUsed(false)
            fileChooser.setDialogTitle("Choose Scala File or input new Scala File name")
            val filesFilter = new FileFilter() {
              def accept(f: File): Boolean = {
                f.getName.endsWith(".scala") || f.isDirectory
              }

              def getDescription(): String = {
                return "Scala File"
              }
            };

            fileChooser.addChoosableFileFilter(filesFilter)
            fileChooser.setFileFilter(filesFilter)
            if (fileChooser.showOpenDialog(WindowManager.getInstance.suggestParentWindow(project)) !=
                    JFileChooser.APPROVE_OPTION) {
              return
            }
            val files = fileChooser.getSelectedFiles
            if (files == null) return

            val choosedFile: File = if (!files(0).getName.endsWith(".scala")) {
              new File(files(0).getPath + ".scala")
            } else files(0)
            PropertiesComponent.getInstance(project).setValue("last_opened_file_path", choosedFile.getParent)
            if (!choosedFile.exists) {
              if (!choosedFile.createNewFile) return
            }
            val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(choosedFile)
            val editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), true)
            ScalaUtils.runWriteAction(new Runnable() {
              def run() {
                editor.getDocument.replaceString(0, editor.getDocument.getTextLength, builder.toString)
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
              }
            }, project, "Save script")
          }
        }
        saveAction.getTemplatePresentation.setIcon(Icons.SCRIPT_FILE_LOGO)
        saveAction.getTemplatePresentation.setEnabled(true)
        saveAction.getTemplatePresentation.setText("Save content to Script")

        consoleView.addCustomConsoleAction(saveAction)
        for (filter <- filters) {
          consoleView.addMessageFilter(filter)
        }
        /*val consoleView = new ScalaLanguageConsoleView(getProject)
        consoleView.getConsole.setPrompt("")
        val saveAction = new AnAction {
          private val shortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
          setShortcutSet(shortcutSet)
          registerCustomShortcutSet(shortcutSet, consoleView.getComponent)
          def actionPerformed(e: AnActionEvent): Unit = {
            val document = consoleView.getConsole.getConsoleEditor.getDocument
            val text = consoleView.getConsole.addCurrentToHistory(new TextRange(0, document.getTextLength), true)
            val stream = new OutputStreamWriter(consoleView.getHandler.getProcessInput)
            stream.write(text + "\n")
            stream.flush
          }
        }*/
        return consoleView
      }

      override def addFilter(filter: Filter): Unit = {
        filters += filter
      }
    }
    state.setConsoleBuilder(consoleBuilder);
    return state;
  }

  def getModule: Module = if (getValidModules.size > 0) getValidModules.get(0) else null

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaScriptConsoleRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptConsoleRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    JDOMExternalizer.write(element, "consoleArgs", getConsoleArgs)
    JDOMExternalizer.write(element, "workingDirectory", getWorkingDirectory)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    consoleArgs = JDOMExternalizer.readString(element, "consoleArgs")
    val str = JDOMExternalizer.readString(element, "workingDirectory")
    if (str != null)
      workingDirectory = str
  }

  private def getClassPath(project: Project, facet: ScalaFacet): String = {
    val pathes: Seq[String] = (for (module <- ModuleManager.getInstance(project).getModules) yield
      getClassPath(module)).toSeq
    pathes.mkString(File.pathSeparator) + File.pathSeparator + getClassPath(facet)
  }
  private def getClassPath(module: Module): String = {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val entries = moduleRootManager.getOrderEntries
    val cpVFiles = new HashSet[VirtualFile];
    for (orderEntry <- entries) {
      cpVFiles ++= orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)
    }
    val res = new StringBuilder("")
    for (file <- cpVFiles) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      res.append(path).append(File.pathSeparator)
    }
    return res.toString
  }

  private def getClassPath(facet: ScalaFacet): String = {
    val res = new StringBuilder("")
    for (file <- facet.files) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      path = PathUtil.getCanonicalPath(path).replace('/', File.separatorChar)
      res.append(path).append(File.pathSeparator)
    }
    return res.toString
  }
}