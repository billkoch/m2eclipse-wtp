/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wtp.overlay.modulecore;

import java.util.Set;

import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;

/**
 * IOverlayVirtualComponent
 *
 * @author Fred Bricon
 */
public interface IOverlayVirtualComponent extends IVirtualComponent{

	/**
	 * Specify the resources to include.
	 */
	void setInclusions(Set<String> inclusionPatterns);
	
	/**
	 * Specify the resources to exclude.
	 */
	void setExclusions(Set<String> inclusionPatterns);
}
