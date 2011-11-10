/*******************************************************************************
 * Copyright (c) 2010 Per Kr. Soreide.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Per Kr. Soreide - initial API and implementation
 *******************************************************************************/
package bndtools.release;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import aQute.bnd.build.Project;
import aQute.bnd.service.RepositoryPlugin;
import aQute.lib.jardiff.JarDiff;
import aQute.lib.osgi.Constants;
import bndtools.release.api.ReleaseContext;
import bndtools.release.nl.Messages;

public class BundleReleaseDialog extends Dialog {

	private static final int UPDATE_RELEASE_BUTTON = IDialogConstants.CLIENT_ID + 1;
	private static final int UPDATE_BUTTON = IDialogConstants.CLIENT_ID + 3;
	private static final int CANCEL_BUTTON = IDialogConstants.CLIENT_ID + 2;

	private BundleRelease release;
	private Project project;
	private List<JarDiff> diffs;
	private Combo releaseRepoCombo;
	
	public BundleReleaseDialog(Shell parentShell, Project project, List<JarDiff> compare) {
		super(parentShell);
		super.setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
		this.project = project;
		release = new BundleRelease(compare);
		this.diffs = compare;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);

		Composite c2 = new Composite(composite, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 10;
		c2.setLayout(gridLayout);
		c2.setLayoutData(new GridData(SWT.HORIZONTAL, SWT.VERTICAL, true, true));

		Label label = new Label(c2, SWT.NONE);
		label.setText(Messages.releaseToRepo);

		String[] items = ReleaseHelper.getReleaseRepositories();
		String defaultRepo = project.getProperty(Constants.RELEASEREPO);
		int idx = 0;
		for (int i = 0; i < items.length; i++) {
			if (defaultRepo != null) {
				if (items[i].equals(defaultRepo)) {
					idx = i;
					break;
				}
			}
		}
		
		releaseRepoCombo = new Combo (c2, SWT.READ_ONLY);
		//combo.setLayout(gridLayout);
		releaseRepoCombo.setItems (items);
		releaseRepoCombo.setSize (200, 200);
		if (items.length > 0) {
			releaseRepoCombo.setText(items[idx]);
		} else {
			releaseRepoCombo.setText("");
		}
		
		ScrolledComposite scrolled = new ScrolledComposite(composite, SWT.H_SCROLL | SWT.V_SCROLL);

		gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginWidth = 10;
		gridLayout.marginHeight = 10;

		scrolled.setLayout(gridLayout);
		scrolled.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));

		release.createControl(scrolled);

		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);
		scrolled.setContent(release.getControl());
		scrolled.setMinSize(500, 500);
		scrolled.layout(true);

		return composite;
	}
	
	protected void buttonPressed(int buttonId) {
		if (CANCEL_BUTTON == buttonId) {
			cancelPressed();
			return;
		}
		
		boolean updateOnly = false;
		if (UPDATE_BUTTON == buttonId) {
			updateOnly = true;
		}

		String releaseRepo = releaseRepoCombo.getText();

		RepositoryPlugin release = null;
		if (releaseRepo != null) {
			release = Activator.getRepositoryPlugin(releaseRepo);
		}

		ReleaseContext context = new ReleaseContext(project, diffs, release, updateOnly);
		
		ReleaseJob job = new ReleaseJob(context);
		job.schedule();
		
		super.okPressed();
		
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(Messages.releaseDialogTitle);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, UPDATE_RELEASE_BUTTON, Messages.updateVersionsAndRelease, true);
		createButton(parent, UPDATE_BUTTON, Messages.updateVersions, false);
		createButton(parent, CANCEL_BUTTON, IDialogConstants.CANCEL_LABEL, false);
	}
}
