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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import aQute.bnd.build.Project;
import aQute.bnd.service.RepositoryPlugin;
import aQute.lib.jardiff.JarDiff;
import aQute.lib.osgi.Jar;
import bndtools.release.api.ReleaseContext;
import bndtools.release.api.ReleaseUtils;
import bndtools.release.nl.Messages;

public class ReleaseJob  extends Job {
	
	private ReleaseContext context;

	public ReleaseJob(ReleaseContext context) {
		super(Messages.bundleReleaseJob);
		this.context = context;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		
		try {
			
			context.setProgressMonitor(monitor);
			
			IProject proj = ReleaseUtils.getProject(context.getProject());
			proj.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			
			boolean ok = ReleaseHelper.release(context, context.getJarDiffs());
			
			ResourcesPlugin.getWorkspace().getRoot().getProject(context.getProject().getName()).refreshLocal(IResource.DEPTH_INFINITE, context.getProgressMonitor());
			
			if (context.getReleaseRepository() != null) {
				File f = Activator.getLocalRepoLocation(context.getReleaseRepository());
				if (f != null && f.exists()) {
					Activator.refreshFile(f);
				}
			}
			if (ok) {
				StringBuilder sb = new StringBuilder();
				sb.append(Messages.project);
				sb.append(" : ");
				sb.append(context.getProject().getName());
				sb.append("\n\n");
				if (context.isUpdateOnly()) {
					sb.append(Messages.updatedVersionInfo);
				} else {
					sb.append(Messages.released);
					sb.append(" :\n");
				}

				for (Jar jar : context.getReleasedJars()) {
					sb.append(ReleaseUtils.getBundleSymbolicName(jar) + "-" + ReleaseUtils.getBundleVersion(jar) + "\n");
				}
				
				if (!context.isUpdateOnly()) {
					sb.append("\n\n");
					sb.append(Messages.releasedTo);
					sb.append(" : ");
					sb.append(context.getReleaseRepository().getName());
				}
				
				Activator.getDefault().message(sb.toString());
			}

		} catch (Exception e) {
			return new Status(Status.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
		}

		return Status.OK_STATUS;
	}
}
