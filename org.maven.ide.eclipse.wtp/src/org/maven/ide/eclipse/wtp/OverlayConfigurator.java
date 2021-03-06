/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wtp;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectChangedEvent;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualReference;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.maven.ide.eclipse.wtp.overlay.IOverlay;
import org.maven.ide.eclipse.wtp.overlay.modulecore.IOverlayVirtualComponent;
import org.maven.ide.eclipse.wtp.overlay.modulecore.OverlayComponentCore;

/**
 * OverlayConfigurator
 *
 * @author Fred Bricon
 */
public class OverlayConfigurator extends WTPProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor)
      throws CoreException {
  }

  @Override
  public void mavenProjectChanged(MavenProjectChangedEvent event, IProgressMonitor monitor) throws CoreException {
    IMavenProjectFacade facade = event.getMavenProject();
    if(facade != null) {
      IProject project = facade.getProject();
      if (project.getResourceAttributes().isReadOnly()){
        return;
      }

      IFacetedProject facetedProject = ProjectFacetsManager.create(project, true, monitor);
      if(!facetedProject.hasProjectFacet(WebFacetUtils.WEB_FACET)) {
        return;
      }

      MavenProject mavenProject = facade.getMavenProject(monitor);
      setModuleDependencies(project, mavenProject, monitor);
    }
  }

  /**
   * @param project
   * @param mavenProject
   * @param monitor
   * @throws CoreException 
   */
  private void setModuleDependencies(IProject project, MavenProject mavenProject, IProgressMonitor monitor) throws CoreException {

    IVirtualComponent warComponent = ComponentCore.createComponent(project);
    if (warComponent == null) {
      return;
    }
    
    Set<IVirtualReference> newOverlayRefs = new LinkedHashSet<IVirtualReference>();
    
    WarPluginConfiguration config = new WarPluginConfiguration(mavenProject, project);
    
    List<IOverlay> overlays = config.getOverlays();
    //Component order must be inverted to follow maven's overlay order behaviour 
    //as in WTP, last components supersede the previous ones
    Collections.reverse(overlays);
    
    for(IOverlay overlay : overlays) {

      Artifact artifact = overlay.getArtifact();
      IOverlayVirtualComponent overlayComponent = null;
      IMavenProjectFacade workspaceDependency = projectManager.getMavenProject(
          artifact.getGroupId(), 
          artifact.getArtifactId(),
          artifact.getVersion());

      if(workspaceDependency != null 
          && !workspaceDependency.getProject().equals(project) //ignore for now
          && workspaceDependency.getFullPath(artifact.getFile()) != null) {
        //artifact dependency is a workspace project
        IProject overlayProject = workspaceDependency.getProject();
        overlayComponent = OverlayComponentCore.createOverlayComponent(overlayProject);
      } else {
        overlayComponent = createOverlayArchiveComponent(project, mavenProject, overlay);
      }

      if (overlayComponent != null) {
        IVirtualReference depRef = ComponentCore.createReference(warComponent, overlayComponent);
        depRef.setRuntimePath(new Path(overlay.getTargetPath()));
        newOverlayRefs.add(depRef);
      }
    }
    
    //add self overlay at the end
    //TODO Handle that in config.getOverlays(); 
    if (!newOverlayRefs.isEmpty()) {
      IVirtualComponent selfOverlayComponent = OverlayComponentCore.createSelfOverlayComponent(project);
      IVirtualReference selfRef = ComponentCore.createReference(warComponent, selfOverlayComponent);
      selfRef.setRuntimePath(new Path("/"));
      newOverlayRefs.add(selfRef);
    }
    
    IVirtualReference[] oldOverlayRefs = WTPProjectsUtil.extractHardReferences(warComponent, true);
    
    IVirtualReference[] updatedOverlayRefs = newOverlayRefs.toArray(new IVirtualReference[newOverlayRefs.size()]);
    
    if (WTPProjectsUtil.hasChanged2(oldOverlayRefs, updatedOverlayRefs)){
      //Only write in the .component file if necessary 
      IVirtualReference[] nonOverlayRefs = WTPProjectsUtil.extractHardReferences(warComponent, false);
      IVirtualReference[] allRefs = new IVirtualReference[nonOverlayRefs.length + updatedOverlayRefs.length];
      System.arraycopy(nonOverlayRefs, 0, allRefs, 0, nonOverlayRefs.length);
      System.arraycopy(updatedOverlayRefs, 0, allRefs, nonOverlayRefs.length, updatedOverlayRefs.length);
      warComponent.setReferences(allRefs);
    }
  }

  private IOverlayVirtualComponent createOverlayArchiveComponent(IProject project, MavenProject mavenProject, IOverlay overlay) throws CoreException {
    IPath m2eWtpFolder = ProjectUtils.getM2eclipseWtpFolder(mavenProject, project);
    IPath unpackDirPath = new Path(m2eWtpFolder.toOSString()+"/overlays");
    String archiveLocation = ArtifactHelper.getM2REPOVarPath(overlay.getArtifact());
    IOverlayVirtualComponent component = OverlayComponentCore.createOverlayArchiveComponent(
                                                                project, 
                                                                archiveLocation, 
                                                                unpackDirPath, 
                                                                new Path(overlay.getTargetPath()));
    return component;
  }
  
  
  public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor)
      throws CoreException {
  }
}
