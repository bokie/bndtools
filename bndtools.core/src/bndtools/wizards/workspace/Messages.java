package bndtools.wizards.workspace;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "bndtools.wizards.workspace.messages"; //$NON-NLS-1$

	public static String InitialiseCnfProjectIntroWizardPage_message;
    public static String InitialiseCnfProjectIntroWizardPage_title;
    public static String InitialiseCnfProjectWizard_info_dialog_donotshow;
    public static String InitialiseCnfProjectWizard_info_dialog_message;
    public static String InitialiseCnfProjectWizard_info_dialog_popup;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}