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

import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.sparseOptimization.utils.Utils;


public class BackwardsInfoflowSparseSolver  extends InfoflowSparseSolver {
	public BackwardsInfoflowSparseSolver(AbstractInfoflowProblem problem, InterruptableExecutor executor) {
		super(problem, executor);
	}


	protected PathEdge<Unit, Abstraction> activateEdge(PathEdge<Unit, Abstraction> oldEdge, Unit defStmt) {

		return oldEdge;
	}

	@Override
	protected Abstraction addAliasInComing(SootMethod m, Abstraction d) {

		Unit activeStmt = d.getActivationUnit();
		Abstraction newD = d.clone();
		newD.setUseStmts(d.getUseStmts());
		newD.setActivationUnit(Utils.reusedStmt);

		if(activeStmt == null)
			return d;  //unexpected!!!
		aliasIncoming.put(new Pair<SootMethod, Abstraction>(m, newD), activeStmt);

		return newD;
	}
	@Override
	protected Abstraction computeAliasSummary(SootMethod m , Abstraction d) {
		Unit activeStmt = aliasIncoming.get(new Pair<SootMethod, Abstraction>(m, d));

		if(activeStmt == null)
			return d;

		Abstraction newD = d.clone();
		newD.setUseStmts(d.getUseStmts());
		newD.setActivationUnit(activeStmt);


		return newD;
	}



}