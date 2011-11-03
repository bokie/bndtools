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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import aQute.lib.jardiff.Diff;
import aQute.lib.jardiff.Group;
import aQute.lib.jardiff.JarDiff;
import aQute.lib.jardiff.java.ClassInfo;
import aQute.lib.jardiff.java.FieldInfo;
import aQute.lib.jardiff.java.JavaDiff;
import aQute.lib.jardiff.java.MethodInfo;
import aQute.lib.jardiff.java.PackageInfo;

/**
 * @see org.eclipse.jface.viewers.ITreeContentProvider
 */
public class TreeContentProvider implements ITreeContentProvider {

	private boolean showAll = false;

	/*
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object parent) {
		if (parent instanceof List<?>) {
			List<?> list = (List<?>) parent;
			if (list.size() == 0) {
				return new Object[0];
			}
			Object[] ret = new Object[list.size()];
			return list.toArray(ret);
		} 
		if (parent instanceof JavaDiff) {
			Collection<PackageInfo> pkgs = new TreeSet<PackageInfo>();
			if (showAll) {
				pkgs.addAll(((JavaDiff)parent).getImportedPackages());
				pkgs.addAll(((JavaDiff)parent).getExportedPackages());
			} else {
				pkgs.addAll(((JavaDiff)parent).getChangedImportedPackages());
				pkgs.addAll(((JavaDiff)parent).getChangedExportedPackages());
			}
			return pkgs.toArray(new Object[pkgs.size()]);
		}
		if (parent instanceof JarDiff) {
			Collection<? extends Diff> diffs = ((JarDiff)parent).getContained();
			Object[] ret = new Object[diffs.size()];
			return diffs.toArray(ret);
		}
		if (parent instanceof PackageInfo) {
			Set<ClassInfo> classes;
			if (showAll)  {
				classes = ((PackageInfo)parent).getClasses();
			} else {
				classes = ((PackageInfo)parent).getChangedClasses();
			}
			return classes.toArray(new Object[classes.size()]);
		}
		if (parent instanceof ClassInfo) {
			Set<MethodInfo> methods;
			if (showAll)  {
				methods =  ((ClassInfo)parent).getMethods();
			} else {
				methods =  ((ClassInfo)parent).getChangedMethods();
			}
			Set<FieldInfo> fields;
			if (showAll)  {
				fields =  ((ClassInfo)parent).getFields();
			} else {
				fields =  ((ClassInfo)parent).getChangedFields();
			}
			Object[] objs = new Object[methods.size() + fields.size()];
			int i = 0;
			for (FieldInfo field : fields) {
				objs[i] = field;
				i++;
			}
			for (MethodInfo method : methods) {
				objs[i] = method;
				i++;
			}
			return objs;
		}
		if (parent instanceof Group) {
			Collection<? extends Diff> diffs = ((Group)parent).getContained();
			Object[] ret = new Object[diffs.size()];
			return diffs.toArray(ret);
		}
		return new Object[0];
	}
	/*
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
	 */
	public Object getParent(Object item) {
		return null;
	}
	/*
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
	 */
	public boolean hasChildren(Object parent) {
		
		if (parent instanceof JarDiff) {
			return ((JarDiff)parent).getContained().size() > 0;
		}

		if (parent instanceof JavaDiff) {
			Collection<PackageInfo> pkgs = new LinkedHashSet<PackageInfo>();
			if (showAll) {
				pkgs.addAll(((JavaDiff)parent).getImportedPackages());
				pkgs.addAll(((JavaDiff)parent).getExportedPackages());
			} else {
				pkgs.addAll(((JavaDiff)parent).getChangedImportedPackages());
				pkgs.addAll(((JavaDiff)parent).getChangedExportedPackages());
			}
			return pkgs.size() > 0;
		}
		if (parent instanceof List<?>) {
			List<?> list = (List<?>) parent;
			if (list.size() == 0) {
				return false;
			}
			return true;
		}
		if (parent instanceof PackageInfo) {
			Set<ClassInfo> classes;
			if (showAll)  {
				classes = ((PackageInfo)parent).getClasses();
			} else {
				classes = ((PackageInfo)parent).getChangedClasses();
			}
			return classes.size() > 0;
		}
		if (parent instanceof ClassInfo) {
			Set<MethodInfo> methods;
			if (showAll)  {
				methods =  ((ClassInfo)parent).getMethods();
			} else {
				methods =  ((ClassInfo)parent).getChangedMethods();
			}
			Set<FieldInfo> fields;
			if (showAll)  {
				fields =  ((ClassInfo)parent).getFields();
			} else {
				fields =  ((ClassInfo)parent).getChangedFields();
			}
			return methods.size() > 0 || fields.size() > 0;
		}
		if (parent instanceof Group) {
			return ((Group)parent).getContained().size() > 0;
		}
		return false;
	}
	/*
	 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
	 */
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}
	/*
	 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
	 */
	public void dispose() {
	}
	/*
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}
	
	public boolean isShowAll() {
		return showAll;
	}
	public void setShowAll(boolean showAll) {
		this.showAll = showAll;
	}
}
