/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.sparseOptimization.solver;

import heros.solver.PathEdge;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;


public class BackwardsInfoflowSparseSolver  extends InfoflowSparseSolver {
	public BackwardsInfoflowSparseSolver(AbstractInfoflowProblem problem, InterruptableExecutor executor) {
		super(problem, executor);
	}


	protected PathEdge<Unit, Abstraction> activateEdge(PathEdge<Unit, Abstraction> oldEdge, Unit defStmt) {

		return oldEdge;
	}



}