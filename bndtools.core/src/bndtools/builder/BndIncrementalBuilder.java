/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.builder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import aQute.bnd.build.BuildPathException;
import aQute.bnd.build.CircularDependencyException;
import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import aQute.lib.osgi.Builder;
import bndtools.Plugin;
import bndtools.RepositoryIndexerJob;
import bndtools.classpath.BndContainer;
import bndtools.classpath.BndContainerException;
import bndtools.classpath.BndContainerInitializer;
import bndtools.utils.FileUtils;
import bndtools.utils.ResourceDeltaAccumulator;

public class BndIncrementalBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = Plugin.PLUGIN_ID + ".bndbuilder";
	public static final String MARKER_BND_PROBLEM = Plugin.PLUGIN_ID + ".bndproblem";
	public static final String MARKER_BND_CLASSPATH_PROBLEM = Plugin.PLUGIN_ID + ".bnd_classpath_problem";

	private static final String BND_SUFFIX = ".bnd";

	private static final long NEVER = -1;

	private final Map<String, Long> projectLastBuildTimes = new HashMap<String, Long>();
	private final Map<File, Container> bndsToDeliverables = new HashMap<File, Container>();

	@Override protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args, IProgressMonitor monitor)
			throws CoreException {

        IProject project = getProject();
        
        try {
            if (monitor != null)
                monitor.beginTask("Building " + project.getName() + "...", 2);
            
    		ensureBndBndExists(project);
    
    	      if (isClasspathBroken(project)) 
    	            kind = FULL_BUILD;

    		if (getLastBuildTime(project) == -1 || kind == FULL_BUILD) {
    			rebuildBndProject(project, monitor);
    		} else {
    			IResourceDelta delta = getDelta(project);
    			if(delta == null)
    				rebuildBndProject(project, monitor);
    			else
    				incrementalRebuild(delta, project, monitor);
    		}
    		setLastBuildTime(project, System.currentTimeMillis());
    		
    		checkCancel(monitor);
    		if (monitor != null)
    		    monitor.worked(1);
    		
    		RepositoryIndexerJob.runIfNeeded();
        } finally {
            if (monitor != null) 
                monitor.done();
        }
		return new IProject[]{ project.getWorkspace().getRoot().getProject(Project.BNDCNF)};
	}
	private void setLastBuildTime(IProject project, long time) {
		projectLastBuildTimes.put(project.getName(), time);
	}
	private long getLastBuildTime(IProject project) {
		Long time = projectLastBuildTimes.get(project.getName());
		return time != null ? time.longValue() : NEVER;
	}
	Collection<IPath> enumerateBndFiles(IProject project) throws CoreException {
		final Collection<IPath> paths = new LinkedList<IPath>();
		project.accept(new IResourceProxyVisitor() {
			public boolean visit(IResourceProxy proxy) throws CoreException {
				if(proxy.getType() == IResource.FOLDER || proxy.getType() == IResource.PROJECT)
					return true;

				String name = proxy.getName();
				if(name.toLowerCase().endsWith(BND_SUFFIX)) {
					IPath path = proxy.requestFullPath();
					paths.add(path);
				}
				return false;
			}
		}, 0);
		return paths;
	}
	void ensureBndBndExists(IProject project) throws CoreException {
		IFile bndFile = project.getFile(Project.BNDFILE);
		bndFile.refreshLocal(0, null);
		if(!bndFile.exists()) {
			bndFile.create(new ByteArrayInputStream(new byte[0]), 0, null);
		}
	}
	@Override protected void clean(IProgressMonitor monitor)
			throws CoreException {

        try {
            if (monitor != null)
                monitor.beginTask("Cleaning " + getProject().getName() + "...", 1);

    	    // Clear markers
    		getProject().deleteMarkers(MARKER_BND_PROBLEM, true,
    				IResource.DEPTH_INFINITE);
            getProject().deleteMarkers(MARKER_BND_CLASSPATH_PROBLEM, true,
                    IResource.DEPTH_INFINITE);
    
    		// Delete target files
    		Project model = Plugin.getDefault().getCentral().getModel(JavaCore.create(getProject()));
    		try {
    			model.clean();
    		} catch (Exception e) {
    			throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error cleaning project outputs.", e));
    		}
        } finally {
            if (monitor != null) 
                monitor.done();
        }
	}
	void incrementalRebuild(IResourceDelta delta, IProject project, IProgressMonitor monitor) {
		SubMonitor progress = SubMonitor.convert(monitor);
		Project model = Plugin.getDefault().getCentral().getModel(JavaCore.create(project));
        if (model == null) {
            // Don't try to build... no bnd workspace configured
            Plugin.log(new Status(IStatus.WARNING, Plugin.PLUGIN_ID, 0, MessageFormat.format("Unable to run Bnd on project {0}: Bnd workspace not configured.",
                    project.getName()), null));
            return;
        }

		try {
            List<File> affectedFiles = new ArrayList<File>();
            final File targetDir = model.getTarget();
            FileFilter generatedFilter = new FileFilter() {
                public boolean accept(File pathname) {
                    return !FileUtils.isAncestor(targetDir, pathname);
                }
			};
			ResourceDeltaAccumulator visitor = new ResourceDeltaAccumulator(IResourceDelta.ADDED | IResourceDelta.CHANGED | IResourceDelta.REMOVED, affectedFiles, generatedFilter);
			delta.accept(visitor);

			if (progress != null)
			    progress.setWorkRemaining(affectedFiles.size() + 10);

			boolean rebuild = false;
			List<File> deletedBnds = new LinkedList<File>();

			// Check if any affected file is a bnd file
			for (File file : affectedFiles) {
				if(file.getName().toLowerCase().endsWith(BND_SUFFIX)) {
					rebuild = true;
					int deltaKind = visitor.queryDeltaKind(file);
					if((deltaKind & IResourceDelta.REMOVED) > 0) {
						deletedBnds.add(file);
					}
					break;
				}
			}
			if(!rebuild && !affectedFiles.isEmpty()) {
				// Check if any of the affected files are members of bundles built by a sub builder
                Collection<? extends Builder> builders = model.getSubBuilders();
				for (Builder builder : builders) {
					File buildPropsFile = builder.getPropertiesFile();
					if(affectedFiles.contains(buildPropsFile)) {
						rebuild = true;
						break;
					} else if(builder.isInScope(affectedFiles)) {
						rebuild = true;

                        // Delete the bundle if any contained resource was
                        // deleted... to force rebuild
                        for (File file : affectedFiles) {
                            if ((IResourceDelta.REMOVED & visitor.queryDeltaKind(file)) > 0) {
                                String bsn = builder.getBsn();
                                File f = new File(model.getTarget(), bsn + ".jar");
                                try {
                                    if (f.isFile()) f.delete();
                                } catch (Exception e) {
                                    Plugin.logError("Error deleting file: " + f.getAbsolutePath(), e);
                                }
                            }
                        }

						break;
					}
					progress.worked(1);
				}
			}

			// Delete corresponding bundles for deleted Bnds
			for (File bndFile : deletedBnds) {
				Container container = bndsToDeliverables.get(bndFile);
				if(container != null) {
					IResource resource = FileUtils.toWorkspaceResource(container.getFile());
					resource.delete(false, null);
				}
			}
			checkCancel(progress);
			if(rebuild)
				rebuildBndProject(project, monitor);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		model.refresh();
	}
	void rebuildBndProject(IProject project, IProgressMonitor monitor) throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, "Rebuilding " + project.getName() + "...",  2);
		IJavaProject javaProject = JavaCore.create(project);

        Project model = Plugin.getDefault().getCentral().getModel(javaProject);
        if (model == null) {
            // Don't try to build... no bnd workspace configured
            Plugin.log(new Status(IStatus.WARNING, Plugin.PLUGIN_ID, 0,
                    MessageFormat.format("Unable to run Bnd on project {0}: Bnd workspace not configured.", project.getName()), null));
            return;
        }

		model.refresh();
		model.setChanged();

		// Get or create the build model for this bnd file
		IFile bndFile = project.getFile(Project.BNDFILE);

		// Clear markers
		if (bndFile.exists()) 
		    bndFile.deleteMarkers(MARKER_BND_PROBLEM, true, IResource.DEPTH_INFINITE);
		project.deleteMarkers(MARKER_BND_CLASSPATH_PROBLEM, true, IResource.DEPTH_INFINITE);

		IClasspathEntry[] entries;
        try {
            entries = BndContainerInitializer.calculateEntries(model);
        } catch (BndContainerException e) {
            IMarker marker = project.createMarker(MARKER_BND_CLASSPATH_PROBLEM);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            marker.setAttribute(IMarker.MESSAGE, "PK:" + e.getCause().getMessage());
            marker.setAttribute(IMarker.LINE_NUMBER, 1);
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error building project.", e));
        }
		
        // When kind = CLEAN_BUILD artifacts in other projects may be missing, so tell the builder to rebuild later
        if (!isBuildPathValid(model)) {
            super.forgetLastBuiltState();
            super.needRebuild();
            return;
        }

		// Update classpath
		JavaCore.setClasspathContainer(BndContainerInitializer.ID, new IJavaProject[] { javaProject } , new IClasspathContainer[] { new BndContainer(javaProject, entries) }, null);

		// Build
		try {
		    final Set<File> deliverableJars = new HashSet<File>();
			bndsToDeliverables.clear();
            Collection<? extends Builder> builders = model.getSubBuilders();
			for (Builder builder : builders) {
				File subBndFile = builder.getPropertiesFile();
				String bsn = builder.getBsn();
				Container deliverable = model.getDeliverable(bsn, null);
				bndsToDeliverables.put(subBndFile, deliverable);
				deliverableJars.add(deliverable.getFile());
			}
						
		    model.buildLocal(false);
			progress.worked(1);

			File targetDir = model.getTarget();
			IContainer target = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(new Path(targetDir.getAbsolutePath()));
			target.refreshLocal(IResource.DEPTH_INFINITE, null);

			// Clear any JARs in the target directory that have not just been built by Bnd
			final File[] targetJars = model.getTarget().listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.getName().endsWith(".jar");
                }
            });
			WorkspaceJob deleteJob = new WorkspaceJob("delete") {
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                    SubMonitor progress = SubMonitor.convert(monitor);
                    for (File targetJar : targetJars) {
                        if(!deliverableJars.contains(targetJar)) {
                            IFile wsFile = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(new Path(targetJar.getAbsolutePath()));
                            if(wsFile != null && wsFile.exists()) {
                                wsFile.delete(true, progress.newChild(1));
                            }
                        }
                    }
                    return Status.OK_STATUS;
                }
			};
			deleteJob.schedule();
		} catch (CircularDependencyException e) {
            IMarker marker = project.createMarker(MARKER_BND_CLASSPATH_PROBLEM);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            marker.setAttribute(IMarker.MESSAGE, "PK2:" + e.getCause().getMessage());
            marker.setAttribute(IMarker.LINE_NUMBER, 1);
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Circular dependencies building project.", e));
        } catch (BuildPathException e) {
            IMarker marker = project.createMarker(MARKER_BND_CLASSPATH_PROBLEM);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            marker.setAttribute(IMarker.MESSAGE, "PK3:" + e.getCause().getMessage());
            marker.setAttribute(IMarker.LINE_NUMBER, 1);
            throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error building project.", e));
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error building project.", e));
		}

		// Report errors
		List<String> errors = new ArrayList<String>(model.getErrors());
		for (String errorMessage : errors) {
			IMarker marker = bndFile.createMarker(MARKER_BND_PROBLEM);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.MESSAGE, errorMessage);
			marker.setAttribute(IMarker.LINE_NUMBER, 1);
			model.clear();
		}
	}
	
    protected void checkCancel(IProgressMonitor monitor) {
        if (monitor == null)
            return;
    
        if (monitor.isCanceled()) {
           forgetLastBuiltState();
           throw new OperationCanceledException();
        }
     }

    private boolean isBuildPathValid(Project project) {
        try {
            for (Container file : project.getBuildpath()) {
                if (!file.getFile().exists()) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    private boolean isClasspathBroken(IProject project) throws CoreException {
        IMarker[] markers = project.findMarkers(MARKER_BND_CLASSPATH_PROBLEM, false, IResource.DEPTH_ZERO);
        for (int i = 0, l = markers.length; i < l; i++)
            if (((Integer) markers[i].getAttribute(IMarker.SEVERITY)).intValue() == IMarker.SEVERITY_ERROR)
                return true;
        return false;
    }

}
