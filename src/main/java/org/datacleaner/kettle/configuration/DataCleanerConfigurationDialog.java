package org.datacleaner.kettle.configuration;

import java.io.File;
import java.io.IOException;

import org.datacleaner.kettle.ui.DataCleanerBanner;
import org.datacleaner.kettle.ui.DataCleanerFooter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class DataCleanerConfigurationDialog extends Dialog {

    // The spaces are necessary because the label length cannot be modified at
    // runtime
    private static final String EDITION = "Edition:                     ";
    private static final String VERSION = "Version:                     ";

    public static final String DATACLEANER_COMMUNITY = "Community";
    public static final String DATACLEANER_ENTERPRISE = "Enterprise";

    protected String _result;
    protected Shell _shell;

    public static class SoftwareVersion {

        private final String _name;
        private final String _version;

        SoftwareVersion(String name, String version) {
            _name = name;
            _version = version;
        }

        public String getName() {
            return _name;
        }

        public String getVersion() {
            return _version;
        }
    }

    /**
     * Create the dialog.
     * 
     * @param parent
     * @param style
     */
    public DataCleanerConfigurationDialog(Shell parent, int style) {
        super(parent, style);
        setText("DataCleaner configuration");
    }

    /**
     * Open the dialog.
     * 
     * @return the result
     */
    public String open() {
        createContents();
        _shell.open();
        _shell.layout();
        final Display display = getParent().getDisplay();
        while (!_shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return _result;
    }

    /**
     * Create contents of the dialog.
     */

    private void createContents() {
        _shell = new Shell(getParent(), getStyle());
        _shell.setSize(500, 425);
        _shell.setText(getText());
        final GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginLeft = -5;
        gridLayout.marginRight = -5;
        gridLayout.marginTop = -5;
        gridLayout.marginBottom = -5;

        _shell.setLayout(gridLayout);
        _shell.setOrientation(getStyle());
        final DataCleanerBanner banner = new DataCleanerBanner(_shell);
        banner.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        final Label lblSelectDatacleanerApplication = new Label(_shell, SWT.NONE);
        lblSelectDatacleanerApplication.setText("Select DataCleaner application directory:");
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        final Text _text = new Text(_shell, SWT.BORDER);
        _text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        final Button btnBrowse = new Button(_shell, SWT.NONE);
        btnBrowse.setText("Browse");

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        final Label errorLabel = new Label(_shell, SWT.NONE);
        errorLabel.setText("The selected folder does not contain a DataCleaner installation");
        errorLabel.setForeground(getParent().getDisplay().getSystemColor(SWT.COLOR_RED));
        errorLabel.setVisible(false);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        final Label labelEdition = new Label(_shell, SWT.NONE);
        labelEdition.setText(EDITION);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        final Label labelVersion = new Label(_shell, SWT.NONE);
        labelVersion.setText(VERSION);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);

        new Label(_shell, SWT.NONE);
        new Label(_shell, SWT.NONE);
        final Button btnOK = new Button(_shell, SWT.NONE);
        btnOK.setText("OK");
        btnOK.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                _shell.close();
            }
        });

        final DataCleanerFooter footer = new DataCleanerFooter(_shell);
        footer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

        btnBrowse.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                final DirectoryDialog directoryChooser = new DirectoryDialog(_shell);
                directoryChooser.setFilterPath(_text.getText());
                directoryChooser.setText("DataCleaner instalation folder");
                directoryChooser.setMessage("Select a directory");
                final String dir = directoryChooser.open();
                if (dir != null) {
                    _text.setText(dir);
                    try {
                        final SoftwareVersion editionDetails = getEditionDetails(dir);
                        if (editionDetails != null) {
                            final String edition = EDITION.trim() + " " + editionDetails.getName();
                            labelEdition.setText(edition);
                            labelVersion.setText(VERSION.trim() + " " + editionDetails.getVersion());
                            errorLabel.setVisible(false);
                            _result = dir;
                        } else {
                            errorLabel.setVisible(true);
                            _result = null;
                        }
                    } catch (IOException e) {
                        errorLabel.setText("Exception while reading the directory");
                        e.printStackTrace();
                    }

                }
            }
        });

    }

    public static SoftwareVersion getEditionDetails(String path) throws IOException {
        final File folder = new File(path + "/lib");
        if (!folder.exists()) {
            return null;
        }
        final String fileEnterprise = getEdition("DataCleaner-enterprise-edition-core", folder);
        if (fileEnterprise != null) {
            return new SoftwareVersion(DATACLEANER_ENTERPRISE, getVersion(fileEnterprise));
        } else {
            final String fileCommunity = getEdition("DataCleaner-engine-core", folder);
            if (fileCommunity != null) {
                return new SoftwareVersion(DATACLEANER_COMMUNITY, getVersion(fileCommunity));
            }
        }

        return null;

    }

    private static String getEdition(String file, File folder) {
        for (final File fileEntry : folder.listFiles()) {
            if (!fileEntry.isDirectory()) {
                final String fileName = fileEntry.getName();
                if (fileName.contains(file)) {
                    return fileName;
                }
            }
        }
        return null;
    }

    private static String getVersion(String fileName) {
        if (fileName == null) {
            return "Unknown";
        }
        final int lastIndexOfDash = fileName.lastIndexOf("-");
        final int lastIndexOfDot = fileName.lastIndexOf(".");
        if (lastIndexOfDash == -1 || lastIndexOfDot == -1) {
            return "Unknown";
        }
        return fileName.substring(lastIndexOfDash + 1, lastIndexOfDot);
    }

    public static void main(String[] args) {

        final Display display = Display.getDefault();
        final Shell shell = new Shell(display, SWT.SHELL_TRIM);
        final DataCleanerConfigurationDialog dataCleanerSettingsDialog = new DataCleanerConfigurationDialog(shell,
                SWT.SHELL_TRIM);

        dataCleanerSettingsDialog.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        display.dispose();

    }

}