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

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import aQute.bnd.build.Project;
import aQute.bnd.service.RepositoryPlugin;
import aQute.lib.jardiff.JarDiff;
import bndtools.release.nl.Messages;

public class ReleaseDialogJob extends Job {

	private final Shell shell;
	private final Project project;
	private final List<RepositoryPlugin> repos;
	private final List<File> subBundles;
	
	public ReleaseDialogJob(Project project, List<RepositoryPlugin> repos, List<File> subBundles) {
		super(Messages.releaseJob);
		this.project = project;
		this.repos = repos;
		this.shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
		this.subBundles = subBundles;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		
        try {
	    	monitor.beginTask(Messages.cleaningProject, 100);
	        try {
				IProject proj = ResourcesPlugin.getWorkspace().getRoot().getProject(project.getName());
				proj.build(IncrementalProjectBuilder.FULL_BUILD, null);
			} catch (CoreException e) {
				return e.getStatus();
			}
			monitor.setTaskName(Messages.releasing);
			monitor.worked(33);
			monitor.subTask(Messages.checkingExported);
			final List<JarDiff> diffs = JarDiff.createJarDiffs(project, repos, subBundles);
			if (diffs == null || diffs.size() == 0) {
				return Status.OK_STATUS;
			}
			monitor.worked(33);
			
			Runnable runnable = new Runnable() {
				public void run() {
					BundleReleaseDialog dialog = new BundleReleaseDialog(shell, project, diffs);
					dialog.open();
				}
			};

			if (Display.getCurrent() == null) {
				Display.getDefault().asyncExec(runnable);
			} else {
				runnable.run();
			}
			
			monitor.worked(33);
	        return Status.OK_STATUS;
        } finally {
        	
        	monitor.done();
        }

	}
	
}
