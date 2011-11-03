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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import aQute.bnd.build.Project;
import aQute.bnd.service.RepositoryPlugin.Strategy;
import aQute.lib.io.IO;
import aQute.lib.jardiff.Diff;
import aQute.lib.jardiff.JarDiff;
import aQute.lib.jardiff.java.JavaDiff;
import aQute.lib.jardiff.java.PackageInfo;
import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;
import aQute.libg.reporter.Reporter;
import aQute.libg.version.Version;
import bndtools.editor.model.BndEditModel;
import bndtools.release.api.IReleaseParticipant;
import bndtools.release.api.IReleaseParticipant.Scope;
import bndtools.release.api.ReleaseContext;
import bndtools.release.api.ReleaseContext.Error;
import bndtools.release.api.ReleaseUtils;

public class ReleaseHelper {

	public static void updateProject(ReleaseContext context) throws Exception {

		Collection<? extends Builder> builders = context.getProject().getBuilder(null).getSubBuilders();
		for (Builder builder : builders) {

			JarDiff current = getJarDiffForBuilder(builder, context);
			if (current == null) {
				continue;
			}
			
			JavaDiff javaDiff = getJavaDiff(current);
			
			for (PackageInfo pi : javaDiff.getModifiedExportedPackages()) {
				if (pi.getOldVersion() != null && !pi.getOldVersion().equals(pi.getSuggestedVersion())) {
					updatePackageInfoFile(context.getProject(), pi);
				}
			}

			for (PackageInfo pi : javaDiff.getNewExportedPackages()) {
				updatePackageInfoFile(context.getProject(), pi);
			}

			updateBundleVersion(context, current, builder);
   		}
	}

	public static JavaDiff getJavaDiff(JarDiff jarDiff) {
		for (Diff diff : jarDiff.getContained()) {
			if (diff instanceof JavaDiff) {
				return (JavaDiff) diff;
			}
		}
		return null;

	}
	
	private static void updateBundleVersion(ReleaseContext context, JarDiff current, Builder builder) throws IOException, CoreException {

		Version bundleVersion = current.getNewVersion();
		if (bundleVersion != null) {

			File file = builder.getPropertiesFile();
			if (file == null) {
				file = context.getProject().getPropertiesFile();
			}
			final IFile resource = (IFile) ReleaseUtils.toResource(file);

			IDocument document = FileUtils.readFully(resource);

			final BndEditModel model = new BndEditModel();
			model.loadFrom(document);

			String savedVersion = model.getBundleVersionString();
			if (savedVersion != null && savedVersion.indexOf('$') > -1) {
				//TODO: Handle macros / variables
			}
			model.setBundleVersion(bundleVersion.toString());

			final IDocument finalDoc = document;
			Runnable run = new Runnable() {

				public void run() {
					model.saveChangesTo(finalDoc);

					try {
						FileUtils.writeFully(finalDoc, resource, false);
						resource.refreshLocal(IResource.DEPTH_ZERO, null);
					} catch (CoreException e) {
						throw new RuntimeException(e);
					}
				}
			};
	        if (Display.getCurrent() == null) {
	            Display.getDefault().syncExec(run);
	        } else
	            run.run();
		}
	}

	private static JarDiff getJarDiffForBuilder(Builder builder, ReleaseContext context) {
		JarDiff current = null;
		for (JarDiff jd : context.getJarDiffs()) {
			if (jd.getSymbolicName().equals(builder.getBsn())) {
				current = jd;
				break;
			}
		}
		return current;
	}

	public static boolean release(ReleaseContext context, List<JarDiff> jarDiffs) throws Exception {

		boolean ret = true;

		List<IReleaseParticipant> participants = Activator.getReleaseParticipants();

		if (!preUpdateProjectVersions(context, participants)) {
			postRelease(context, participants, false);
			displayErrors(context, Scope.PRE_UPDATE_VERSIONS);
			return false;
		}

		ReleaseHelper.updateProject(context);

		IProject proj = ReleaseUtils.getProject(context.getProject());
		proj.refreshLocal(IResource.DEPTH_INFINITE, context.getProgressMonitor());

		if (!preRelease(context, participants)) {
			postRelease(context, participants, false);
			displayErrors(context, Scope.PRE_RELEASE);
			return false;
		}

		for (JarDiff jarDiff : jarDiffs) {
			Collection<? extends Builder> builders = context.getProject().getBuilder(null).getSubBuilders();
			Builder builder = null;
			for (Builder b : builders) {
				if (b.getBsn().equals(jarDiff.getSymbolicName())) {
					builder = b;
					break;
				}
			}
			if (builder != null) {
				if (!release(context, participants, builder)) {
					ret = false;
				}
			}
		}

		postRelease(context, participants, ret);
		return ret;
	}

	private static void handleBuildErrors(ReleaseContext context, Reporter reporter, Jar jar) {
		String symbName = null;
		String version = null;
		if (jar != null) {
			symbName = ReleaseUtils.getBundleSymbolicName(jar);
			version = ReleaseUtils.getBundleVersion(jar);
		}
		for (String message : reporter.getErrors()) {
			context.getErrorHandler().error(symbName, version, message);
		}
	}

	private static void handleReleaseErrors(ReleaseContext context, Reporter reporter, String symbolicName, String version) {
		for (String message : reporter.getErrors()) {
			context.getErrorHandler().error(symbolicName, version, message);
		}
	}

	private static void displayErrors(ReleaseContext context, Scope scope) {

		final String name = context.getProject().getName();
		final List<Error> errors = context.getErrorHandler().getErrors();
		if (errors.size() > 0) {
			Runnable runnable = new Runnable() {
				public void run() {
					Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
					ErrorDialog error = new ErrorDialog(shell, name, errors);
					error.open();
				}
			};

			if (Display.getCurrent() == null) {
				Display.getDefault().asyncExec(runnable);
			} else {
				runnable.run();
			}

		}

	}

	private static boolean release(ReleaseContext context, List<IReleaseParticipant> participants, Builder builder) throws Exception {

		context.getProject().refresh();
		context.getProject().setChanged();

		Jar jar = builder.build();

		handleBuildErrors(context, builder, jar);

		String symbName = ReleaseUtils.getBundleSymbolicName(jar);
		String version = ReleaseUtils.getBundleVersion(jar);

		boolean proceed = preJarRelease(context, participants, jar);
		if (!proceed) {
			postRelease(context, participants, false);
			displayErrors(context, Scope.PRE_JAR_RELEASE);
			return false;
		}

		context.getProject().release(context.getRepository().getName(), jar);
		context.getProject().refresh();

		File file = context.getRepository().get(symbName, '[' + version + ',' + version + ']', Strategy.HIGHEST, null);
		Jar releasedJar = null;
		if (file != null && file.exists()) {
			IResource resource = ReleaseUtils.toResource(file);
			if (resource != null) {
				resource.refreshLocal(IResource.DEPTH_ZERO, null);
			}
			releasedJar = new Jar(file);
		}
		if (releasedJar == null) {
			handleReleaseErrors(context, context.getProject(), symbName, version);

			postRelease(context, participants, false);
			displayErrors(context, Scope.POST_JAR_RELEASE);
			return false;
		}
		context.addReleasedJar(releasedJar);

		postJarRelease(context, participants, releasedJar);
		return true;
	}

	private static void updatePackageInfoFile(Project project, PackageInfo packageInfo) throws Exception {
		String path = packageInfo.getPackageName().replace('.', '/') + "/packageinfo";
		File file = getSourceFile(project, path);

		// If package/classes are copied into the bundle through Private-Package etc, there will be no source
		if (!file.getParentFile().exists()) {
			return;
		}

		if (file.exists() && equalsPackageInfoFileVersion(file, packageInfo.getSuggestedVersion())) {
			return;
		} else {
			FileOutputStream fos = new FileOutputStream(file);
			PrintWriter pw = new PrintWriter(fos);
			pw.println("version " + packageInfo.getSuggestedVersion());
			pw.flush();
			pw.close();
			ReleaseUtils.toResource(file).refreshLocal(IResource.DEPTH_ZERO, null);
	
			File binary = IO.getFile(project.getOutput(), path);
			IO.copy(file, binary);
			ReleaseUtils.toResource(binary).refreshLocal(IResource.DEPTH_ZERO, null);
		}
	}

	private static boolean equalsPackageInfoFileVersion(File packageInfoFile, Version version) throws IOException {
		// Check existing version
		if (packageInfoFile.exists()) {
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(packageInfoFile));
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("version ")) {
						Version fileVersion = Version.parseVersion(line.substring(8));
						if (fileVersion.equals(version)) {
							return true;
						}
						return false;
					}
				}
			} finally {
				if (reader != null) {
					IO.close(reader);
				}
			}
		}
		return false;
	}
	
	private static File getSourceFile(Project project, String path) {
		String src = project.getProperty("src", "src");
		return project.getFile(src + "/" + path);
	}

	private static boolean preUpdateProjectVersions(ReleaseContext context, List<IReleaseParticipant> participants) {
		context.setCurrentScope(Scope.PRE_UPDATE_VERSIONS);
		for (IReleaseParticipant participant : participants) {
			if (!participant.preUpdateProjectVersions(context)) {
				return false;
			}
		}
		return true;
	}

	private static boolean preRelease(ReleaseContext context, List<IReleaseParticipant> participants) {
		context.setCurrentScope(Scope.PRE_RELEASE);
		for (IReleaseParticipant participant : participants) {
			if (!participant.preRelease(context)) {
				return false;
			}
		}
		return true;
	}

	private static boolean preJarRelease(ReleaseContext context, List<IReleaseParticipant> participants, Jar jar) {
		context.setCurrentScope(Scope.PRE_JAR_RELEASE);
		for (IReleaseParticipant participant : participants) {
			if (!participant.preJarRelease(context, jar)) {
				return false;
			}
		}
		return true;
	}

	private static void postJarRelease(ReleaseContext context, List<IReleaseParticipant> participants, Jar jar) {
		context.setCurrentScope(Scope.POST_JAR_RELEASE);
		for (IReleaseParticipant participant : participants) {
			participant.postJarRelease(context, jar);
		}
	}

	private static void postRelease(ReleaseContext context, List<IReleaseParticipant> participants, boolean success) {
		context.setCurrentScope(Scope.POST_RELEASE);
		for (IReleaseParticipant participant : participants) {
			participant.postRelease(context, success);
		}
	}
}
