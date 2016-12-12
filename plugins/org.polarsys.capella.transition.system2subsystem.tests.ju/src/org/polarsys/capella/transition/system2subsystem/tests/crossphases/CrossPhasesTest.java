/*******************************************************************************
 * Copyright (c) 2016 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *   
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.transition.system2subsystem.tests.crossphases;

import org.polarsys.capella.transition.system2subsystem.tests.System2SubsystemTest;

public abstract class CrossPhasesTest extends System2SubsystemTest {

  public CrossPhasesTest() {
    setKind(Kind.CROSS_PHASES);
  }

}