package org.pentaho.di.profiling.datacleaner;

import java.io.File;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.vfs2.FileObject;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.schema.Column;
import org.datacleaner.api.InputColumn;
import org.datacleaner.beans.BooleanAnalyzer;
import org.datacleaner.beans.CompletenessAnalyzer;
import org.datacleaner.beans.DateAndTimeAnalyzer;
import org.datacleaner.beans.NumberAnalyzer;
import org.datacleaner.beans.StringAnalyzer;
import org.datacleaner.beans.uniqueness.UniqueKeyCheckAnalyzer;
import org.datacleaner.configuration.DataCleanerConfiguration;
import org.datacleaner.configuration.DataCleanerConfigurationImpl;
import org.datacleaner.connection.Datastore;
import org.datacleaner.connection.DatastoreConnection;
import org.datacleaner.data.MetaModelInputColumn;
import org.datacleaner.descriptors.ConfiguredPropertyDescriptor;
import org.datacleaner.job.AnalysisJob;
import org.datacleaner.job.JaxbJobWriter;
import org.datacleaner.job.builder.AnalysisJobBuilder;
import org.datacleaner.job.builder.AnalyzerComponentBuilder;
import org.datacleaner.kettle.configuration.DataCleanerSpoonConfiguration;
import org.datacleaner.kettle.configuration.DataCleanerSpoonConfigurationException;
import org.datacleaner.kettle.configuration.utils.SoftwareVersionHelper;
import org.datacleaner.kettle.configuration.utils.SoftwareVersionHelper.SoftwareVersion;
import org.datacleaner.kettle.ui.DataCleanerConfigurationDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.gui.SpoonFactory;
import org.pentaho.di.core.logging.LogChannel;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransExecutionConfiguration;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.csvinput.CsvInputMeta;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.spoon.ISpoonMenuController;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.trans.dialog.TransExecutionConfigurationDialog;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public class ModelerHelper extends AbstractXulEventHandler implements ISpoonMenuController {

    private static final String LOGCHANNEL_NAME = "DataCleaner";

    private static final String MAIN_CLASS_COMMUNITY = "org.datacleaner.Main";
    private static final String MAIN_CLASS_ENTERPRISE = "com.hi.datacleaner.Main";

    private static final Set<String> ID_COLUMN_TOKENS =
            new HashSet<>(Arrays.asList("id", "pk", "number", "no", "nr", "key"));

    private static ModelerHelper instance = null;

    private ModelerHelper() {
    }

    public static ModelerHelper getInstance() {
        if (instance == null) {
            instance = new ModelerHelper();
            Spoon spoon = ((Spoon) SpoonFactory.getInstance());
            spoon.addSpoonMenuController(instance);
        }
        return instance;
    }

    /**
     * this method is used to see if a valid TableOutput step has been selected in a trans graph before attempting to
     * model or quick vis
     * 
     * @return true if valid
     */
    public static boolean isValidStepSelected() {
        StepMeta stepMeta = getCurrentStep();
        if (stepMeta == null) {
            return false;
        }

        if (stepMeta.getStepMetaInterface() instanceof CsvInputMeta) {
            return true;
        }
        return false;
    }

    public static StepMeta getCurrentStep() {
        Spoon spoon = ((Spoon) SpoonFactory.getInstance());
        TransMeta transMeta = spoon.getActiveTransformation();
        if (transMeta == null || spoon.getActiveTransGraph() == null) {
            return null;
        }
        StepMeta stepMeta = spoon.getActiveTransGraph().getCurrentStep();
        return stepMeta;
    }

    public String getName() {
        return "profiler"; //$NON-NLS-1$
    }

    public static DataCleanerSpoonConfiguration getDataCleanerSpoonConfigurationOrShowError() {
        try {
            DataCleanerSpoonConfiguration result = DataCleanerSpoonConfiguration.load();
            return result;
        } catch (DataCleanerSpoonConfigurationException e) {
            showErrorMessage("DataCleaner configuration error", "DataCleaner configuration not available", e);
            return null;
        }
    }

    public static int launchDataCleanerSimple(DataCleanerSpoonConfiguration dataCleanerSpoonConfiguration,
            String jobFile, String outputFiletype, String outputFilename, String additionalArguments) {
        return launchDataCleaner(dataCleanerSpoonConfiguration, null, jobFile, null, null, outputFiletype,
                outputFilename, additionalArguments, false);
    }

    public static int launchDataCleaner(DataCleanerSpoonConfiguration dataCleanerSpoonConfiguration, String confFile,
            String jobFile, String datastore, String dataFile, String outputFiletype, String outputFilename,
            String additionalArguments, boolean profileStep) {

        final LogChannelInterface log = new LogChannel(LOGCHANNEL_NAME);
        int exitCode = 0;

        try {
            final String dcInstallationPath = dataCleanerSpoonConfiguration.getDataCleanerInstallationFolderPath()
                    + "/DataCleaner.jar" + File.pathSeparatorChar
                    + dataCleanerSpoonConfiguration.getDataCleanerInstallationFolderPath() + "/modules/*"
                    + File.pathSeparatorChar + dataCleanerSpoonConfiguration.getDataCleanerInstallationFolderPath()
                    + "/lib/*";
            final StringBuilder classPathBuilder;
            if (profileStep) {
                classPathBuilder = addAdditionalJars(dataCleanerSpoonConfiguration, dcInstallationPath);
            } else {
                classPathBuilder = new StringBuilder();
                classPathBuilder.append(dcInstallationPath);
            }
            final List<String> cmds = new ArrayList<String>();
            cmds.add(System.getProperty("java.home") + "/bin/java");
            cmds.add("-cp");
            cmds.add(classPathBuilder.toString());
            if (profileStep) {
                cmds.add("-Ddatacleaner.ui.visible=true");
                cmds.add("-Ddatacleaner.embed.client=Kettle");
            }
            // Finally, the class to launch
            //
            final SoftwareVersion editionDetails =
                    SoftwareVersionHelper.getEditionDetails(dataCleanerSpoonConfiguration);

            if (editionDetails != null) {
                if (editionDetails.getName() == SoftwareVersionHelper.DATACLEANER_COMMUNITY) {
                    cmds.add(MAIN_CLASS_COMMUNITY);
                } else {
                    cmds.add(MAIN_CLASS_ENTERPRISE);
                }
            }

            // The optional arguments for DataCleaner
            //
            if (!Strings.isNullOrEmpty(confFile)) {
                cmds.add("-conf");
                cmds.add(confFile);
            }
            if (!Strings.isNullOrEmpty(jobFile)) {
                cmds.add("-job");
                cmds.add(jobFile);
            }
            if (!Strings.isNullOrEmpty(datastore)) {
                cmds.add("-ds");
                cmds.add(datastore);
            }
            if (!Strings.isNullOrEmpty(outputFiletype)) {
                cmds.add("-ot");
                cmds.add(outputFiletype);

            }
            if (!Strings.isNullOrEmpty(outputFilename)) {
                cmds.add("-of");
                cmds.add(outputFilename);

            }

            if (!Strings.isNullOrEmpty(additionalArguments)) {
                if (additionalArguments != null && additionalArguments.length() != 0) {
                    final String[] args = additionalArguments.split(" ");
                    for (String arg : args) {
                        cmds.add(arg);
                    }

                }
            }
            // Log the command

            StringBuilder commandString = new StringBuilder();
            for (String cmd : cmds) {
                commandString.append(cmd).append(" ");
            }

            log.logBasic("DataCleaner launch commands : " + commandString);

            final ProcessBuilder processBuilder = new ProcessBuilder(cmds);
            processBuilder.environment().put("DATACLEANER_HOME", dataCleanerSpoonConfiguration.getPluginFolderPath());

            final Process process = processBuilder.start();

            ProcessStreamReader psrStdout = new ProcessStreamReader(process.getInputStream(), log, false);
            ProcessStreamReader psrStderr = new ProcessStreamReader(process.getErrorStream(), log, true);
            psrStdout.start();
            psrStderr.start();

            exitCode = process.waitFor();

            psrStdout.join();
            psrStderr.join();

            // When DC finishes we clean up the temporary files... only if it'a profiling step job
            if (profileStep) {
                if (!Strings.isNullOrEmpty(confFile)) {
                    new File(confFile).delete();
                }
                if (!Strings.isNullOrEmpty(jobFile)) {
                    new File(jobFile).delete();
                }
                if (!Strings.isNullOrEmpty(dataFile)) {
                    new File(dataFile).delete();
                }
            }

            if (exitCode != 0) {
                showErrorMessage("Unexpected exit code", "DataCleaner exited with exit code: " + exitCode, null);
            }
        } catch (final Exception e) {
            showErrorMessage("Error launching DataCleaner", "An error occurred launching DataCleaner", e);
        }
        return exitCode;
    }

    private static StringBuilder addAdditionalJars(DataCleanerSpoonConfiguration dataCleanerSpoonConfiguration,
            final String dcInstallationPath) {
        final String pluginPath = dataCleanerSpoonConfiguration.getPluginFolderPath() + "/DataCleaner-PDI-plugin.jar";
        final String kettleLibPath = dataCleanerSpoonConfiguration.getPluginFolderPath() + "/../../lib";
        final String kettleCorePath = getJarFile(kettleLibPath, "kettle-core");
        final String commonsVfsPath = getJarFile(kettleLibPath, "commons-vfs");
        final String scannotationPath = getJarFile(kettleLibPath, "scannotation");
        final String javassistPath = getJarFile(kettleLibPath, "javassist");

        // Assemble the class path for DataCleaner
        final String[] paths = new String[] { dcInstallationPath, kettleCorePath, commonsVfsPath, scannotationPath,
                javassistPath, pluginPath };
        final StringBuilder classPathBuilder = new StringBuilder();
        for (String path : paths) {
            if (classPathBuilder.length() > 0) {
                classPathBuilder.append(File.pathSeparator);
            }
            classPathBuilder.append(path);
        }
        return classPathBuilder;
    }

    public static void showErrorMessage(final String title, final String message, final Throwable e) {
        final Spoon spoon = getSpoon();
        spoon.getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                final Shell shell = spoon.getShell();
                new ErrorDialog(shell, title, message, e);
            }
        });
    }

    public static String getJarFile(String libPath, final String filename) {
        final File directory = new File(libPath);
        assert directory.exists() && directory.isDirectory();

        final String[] filenames = directory.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(filename);
            }
        });

        return libPath + "/" + filenames[0];
    }

    public void openProfiler() {
        final DataCleanerSpoonConfiguration dataCleanerSpoonConfiguration =
                getDataCleanerSpoonConfigurationOrShowError();
        if (dataCleanerSpoonConfiguration == null) {
            return;
        }
        new Thread() {
            @Override
            public void run() {
                launchDataCleaner(dataCleanerSpoonConfiguration, null, null, null, null, null, null, null, true);
            }
        }.start();
    }

    public void openConfiguration() {
        final Display display = getSpoon().getDisplay();
        final Shell shell = new Shell(display, SWT.DIALOG_TRIM);
        final DataCleanerConfigurationDialog dataCleanerSettingsDialog =
                new DataCleanerConfigurationDialog(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);

        dataCleanerSettingsDialog.open();
    }

    public void readMore() {
        Program.launch("http://datacleaner.org/focus/pentaho");
    }

    private static Spoon getSpoon() {
        final Spoon spoon = ((Spoon) SpoonFactory.getInstance());
        return spoon;
    }

    public void profileStep(final boolean buildJob) throws Exception {
        final DataCleanerSpoonConfiguration dataCleanerSpoonConfiguration =
                getDataCleanerSpoonConfigurationOrShowError();
        if (dataCleanerSpoonConfiguration == null) {
            return;
        }

        final Spoon spoon = getSpoon();
        try {

            final TransMeta transMeta = spoon.getActiveTransformation();
            if (transMeta == null || spoon.getActiveTransGraph() == null) {
                return;
            }

            StepMeta stepMeta = spoon.getActiveTransGraph().getCurrentStep();
            if (stepMeta == null) {
                return;
            }

            // Show the transformation execution configuration dialog
            TransExecutionConfiguration executionConfiguration = spoon.getTransPreviewExecutionConfiguration();
            TransExecutionConfigurationDialog tecd =
                    new TransExecutionConfigurationDialog(spoon.getShell(), executionConfiguration, transMeta);
            if (!tecd.open()) {
                return;
            }

            // Pass the configuration to the transMeta object:
            final String[] args = convertArguments(executionConfiguration.getArguments());
            transMeta.injectVariables(executionConfiguration.getVariables());

            // Set the named parameters
            final Map<String, String> paramMap = executionConfiguration.getParams();
            {
                final Set<String> keys = paramMap.keySet();
                for (String key : keys) {
                    transMeta.setParameterValue(key, Const.NVL(paramMap.get(key), "")); //$NON-NLS-1$
                }
            }

            transMeta.activateParameters();

            // Do we need to clear the log before running?
            if (executionConfiguration.isClearingLog()) {
                spoon.getActiveTransGraph().transLogDelegate.clearLog();
            }

            // Now that we have the transformation and everything we can run it
            // and profile it...
            Trans trans = new Trans(transMeta, Spoon.loggingObject);
            trans.prepareExecution(executionConfiguration.getArgumentStrings());
            trans.setSafeModeEnabled(executionConfiguration.isSafeModeEnabled());
            trans.setPreview(true);
            trans.prepareExecution(args);
            trans.setRepository(spoon.rep);

            // Write the data to a file that DataCleaner will read
            final DataCleanerKettleFileWriter writer = new DataCleanerKettleFileWriter(trans, stepMeta);
            try {
                writer.run();
            } finally {
                writer.close();
            }

            // Pass along the configuration of the KettleDatabaseStore...
            final DataCleanerConfiguration dataCleanerConfiguration = new DataCleanerConfigurationImpl();
            final AnalysisJob analysisJob = createAnalysisJob(transMeta, stepMeta, dataCleanerConfiguration, buildJob);

            // Write the job.xml to a temporary file...
            FileObject jobFile = KettleVFS.createTempFile("datacleaner-job", ".xml",
                    System.getProperty("java.io.tmpdir"), new Variables());
            OutputStream jobOutputStream = null;

            try {
                jobOutputStream = KettleVFS.getOutputStream(jobFile, false);
                final JaxbJobWriter jobWriter = new JaxbJobWriter(dataCleanerConfiguration);
                jobWriter.write(analysisJob, jobOutputStream);
            } catch (Exception e) {
                final LogChannelInterface log = new LogChannel(LOGCHANNEL_NAME);
                log.logError("Failed to save DataCleaner job", e);
                jobFile = null;
            } finally {
                if (jobOutputStream != null) {
                    jobOutputStream.close();
                }
            }

            // Write the conf.xml to a temporary file...
            String confXml = generateConfXml(transMeta.getName(), stepMeta.getName(), writer.getFilename());
            final FileObject confFile = KettleVFS.createTempFile("datacleaner-conf", ".xml",
                    System.getProperty("java.io.tmpdir"), new Variables());
            OutputStream confOutputStream = null;
            try {
                confOutputStream = KettleVFS.getOutputStream(confFile, false);
                confOutputStream.write(confXml.getBytes(Const.XML_ENCODING));
                confOutputStream.close();
            } finally {
                if (confOutputStream != null) {
                    confOutputStream.close();
                }
            }

            // Launch DataCleaner and point to the generated
            // configuration and job XML files...

            final String jobFilename;
            if (jobFile == null) {
                jobFilename = null;
            } else {
                jobFilename = KettleVFS.getFilename(jobFile);
            }

            new Thread() {
                @Override
                public void run() {
                    launchDataCleaner(dataCleanerSpoonConfiguration, KettleVFS.getFilename(confFile), jobFilename,
                            transMeta.getName(), writer.getFilename(), null, null, null, true);
                }
            }.start();
        } catch (final NoClassDefFoundError e) {
            showErrorMessage("Unexpected error", "Failed to load DataCleaner plugin class: " + e.getMessage(), e);
        } catch (final Exception e) {
            showErrorMessage("Unexpected error", "An unexpected error occurred", e);
        }
    }

    private AnalysisJob createAnalysisJob(final TransMeta transMeta, final StepMeta stepMeta,
            final DataCleanerConfiguration dataCleanerConfiguration, final boolean buildJob)
            throws KettleStepException {
        try (AnalysisJobBuilder analysisJobBuilder = new AnalysisJobBuilder(dataCleanerConfiguration)) {
            final Datastore datastore =
                    new KettleDatastore(transMeta.getName(), stepMeta.getName(), transMeta.getStepFields(stepMeta));
            analysisJobBuilder.setDatastore(datastore);

            try (DatastoreConnection connection = datastore.openConnection();) {
                final DataContext dataContext = connection.getDataContext();

                // add all columns of a table
                final List<Column> customerColumns =
                        dataContext.getTableByQualifiedLabel(stepMeta.getName()).getColumns();
                analysisJobBuilder.addSourceColumns(customerColumns);

                final List<MetaModelInputColumn> sourceColumns = analysisJobBuilder.getSourceColumns();

                if (buildJob && !sourceColumns.isEmpty()) {
                    // if something looks like an ID, add a unique key analyzer
                    // for it.
                    final Set<InputColumn<?>> idColumns = new HashSet<>();
                    {
                        final CharMatcher charMatcher =
                                CharMatcher.BREAKING_WHITESPACE.or(CharMatcher.anyOf("_-|#.,/+-!@&()[]"));
                        final Splitter splitter = Splitter.on(charMatcher).trimResults().omitEmptyStrings();
                        for (InputColumn<?> sourceColumn : sourceColumns) {
                            final String columnName = sourceColumn.getName().toLowerCase();
                            final List<String> columnTokens = splitter.splitToList(columnName);
                            for (String token : columnTokens) {
                                if (ID_COLUMN_TOKENS.contains(token)) {
                                    // this looks like an ID column
                                    idColumns.add(sourceColumn);
                                    break;
                                }
                            }
                        }
                    }
                    for (InputColumn<?> idColumn : idColumns) {
                        final AnalyzerComponentBuilder<UniqueKeyCheckAnalyzer> uniqueKeyCheck =
                                analysisJobBuilder.addAnalyzer(UniqueKeyCheckAnalyzer.class);
                        uniqueKeyCheck.setName("Uniqueness of " + idColumn.getName());
                        uniqueKeyCheck.addInputColumn(idColumn);
                    }

                    // add a completeness analyzer for all columns
                    final AnalyzerComponentBuilder<CompletenessAnalyzer> completenessAnalyzer =
                            analysisJobBuilder.addAnalyzer(CompletenessAnalyzer.class);
                    completenessAnalyzer.addInputColumns(sourceColumns);
                    final CompletenessAnalyzer.Condition[] conditions =
                            new CompletenessAnalyzer.Condition[sourceColumns.size()];
                    Arrays.fill(conditions, CompletenessAnalyzer.Condition.NOT_BLANK_OR_NULL);
                    completenessAnalyzer.setConfiguredProperty(CompletenessAnalyzer.PROPERTY_CONDITIONS, conditions);

                    // add a number analyzer for all number columns
                    final List<InputColumn<?>> numberColumns =
                            analysisJobBuilder.getAvailableInputColumns(Number.class);
                    if (!numberColumns.isEmpty()) {
                        final AnalyzerComponentBuilder<NumberAnalyzer> numberAnalyzer =
                                analysisJobBuilder.addAnalyzer(NumberAnalyzer.class);
                        final ConfiguredPropertyDescriptor descriptiveStatisticsProperty =
                                numberAnalyzer.getDescriptor().getConfiguredProperty("Descriptive statistics");
                        if (descriptiveStatisticsProperty != null) {
                            numberAnalyzer.setConfiguredProperty(descriptiveStatisticsProperty, true);
                        }
                        numberAnalyzer.addInputColumns(numberColumns);
                    }

                    // add a date/time analyzer for all date columns
                    final List<InputColumn<?>> dateColumns = analysisJobBuilder.getAvailableInputColumns(Date.class);
                    if (!dateColumns.isEmpty()) {
                        final AnalyzerComponentBuilder<DateAndTimeAnalyzer> dateAndTimeAnalyzer =
                                analysisJobBuilder.addAnalyzer(DateAndTimeAnalyzer.class);
                        dateAndTimeAnalyzer.addInputColumns(dateColumns);
                    }

                    // add a boolean analyzer for all boolean columns
                    final List<InputColumn<?>> booleanColumns =
                            analysisJobBuilder.getAvailableInputColumns(Boolean.class);
                    if (!booleanColumns.isEmpty()) {
                        final AnalyzerComponentBuilder<BooleanAnalyzer> booleanAnalyzer =
                                analysisJobBuilder.addAnalyzer(BooleanAnalyzer.class);
                        booleanAnalyzer.addInputColumns(booleanColumns);
                    }

                    // add a string analyzer for all string columns
                    final List<InputColumn<?>> stringColumns =
                            analysisJobBuilder.getAvailableInputColumns(String.class);
                    if (!stringColumns.isEmpty()) {
                        final AnalyzerComponentBuilder<StringAnalyzer> stringAnalyzer =
                                analysisJobBuilder.addAnalyzer(StringAnalyzer.class);
                        stringAnalyzer.addInputColumns(stringColumns);
                    }
                }
            }

            if (buildJob) {
                return analysisJobBuilder.toAnalysisJob();
            } else {
                return analysisJobBuilder.toAnalysisJob(false);
            }
        }
    }

    private String generateConfXml(String name, String stepname, String filename) {
        StringBuilder xml = new StringBuilder();

        xml.append(XMLHandler.getXMLHeader());
        xml.append(
                "<configuration xmlns=\"http://eobjects.org/analyzerbeans/configuration/1.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        xml.append(XMLHandler.openTag("datastore-catalog"));

        // add a custom datastore
        xml.append("<custom-datastore class-name=\"" + KettleDatastore.class.getName() + "\">");
        xml.append("<property name=\"Name\" value=\"" + name + "\" />");
        xml.append("<property name=\"Filename\" value=\"" + filename + "\" />");
        xml.append("</custom-datastore>");

        xml.append(XMLHandler.closeTag("datastore-catalog"));

        // TODO: Eventually we want to rely on default here, not specifying the
        // task runner type
        xml.append("<multithreaded-taskrunner max-threads=\"30\" />");

        // TODO: Eventually we want to rely on defaults here, not specifying any
        // package names
        xml.append(XMLHandler.openTag("classpath-scanner"));
        xml.append(" <package recursive=\"true\">org.datacleaner</package>");
        xml.append(" <package recursive=\"true\">com.hi</package>");
        xml.append(" <package recursive=\"true\">com.neopost</package>");
        xml.append(XMLHandler.closeTag("classpath-scanner"));

        xml.append(XMLHandler.closeTag("configuration"));

        return xml.toString();
    }

    private String[] convertArguments(Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new String[0];
        }
        final String[] argumentNames = arguments.keySet().toArray(new String[arguments.size()]);
        Arrays.sort(argumentNames);

        final String[] args = new String[argumentNames.length];
        for (int i = 0; i < args.length; i++) {
            String argumentName = argumentNames[i];
            args[i] = arguments.get(argumentName);
        }
        return args;
    }

    @Override
    public void updateMenu(Document doc) {
    }
}
