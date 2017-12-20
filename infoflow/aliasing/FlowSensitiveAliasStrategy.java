package soot.jimple.infoflow.aliasing;

import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.collect.ConcurrentHashSet;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;

import java.util.HashSet;
import java.util.Set;

/**
 * A fully flow-sensitive aliasing strategy
 * 
 * @author Steven Arzt
 */
public class FlowSensitiveAliasStrategy extends AbstractBulkAliasStrategy {
	
	private final IInfoflowSolver bSolver;
	
	public FlowSensitiveAliasStrategy(InfoflowManager manager, IInfoflowSolver backwardsSolver) {
		super(manager);
		this.bSolver = backwardsSolver;
	}

	@Override
	public void computeAliasTaints
			(final Abstraction d1, final Stmt src,
			final Value targetValue, Set<Abstraction> taintSet,
			SootMethod method, Abstraction newAbs) {

		// Start the backwards solver
		Abstraction bwAbs = newAbs.deriveInactiveAbstraction(src);

		if(manager.getConfig().isSparseOptEnabled()) {
			if(newAbs.isAbstractionActive()) {
				Set<Unit> activeUnits = getSolver().getTabulationProblem().getManager().getActivationUnitsToUnits().putIfAbsentElseGet(new Pair<SootMethod, Unit>
						(manager.getICFG().getMethodOf(src), src), new ConcurrentHashSet<Unit>());
				activeUnits.add(src);
			}
			for(Unit predUnit : getNextStmtFromDfg(targetValue, src, bwAbs))
				bSolver.processEdge(new PathEdge<Unit, Abstraction>(d1,
						predUnit, bwAbs), null);
		}else {
			for (Unit predUnit : manager.getICFG().getPredsOf(src))
				bSolver.processEdge(new PathEdge<Unit, Abstraction>(d1,
						predUnit, bwAbs), null);
		}

	}
	
	@Override
	public void injectCallingContext(Abstraction d3, IInfoflowSolver fSolver,
			SootMethod callee, Unit callSite, Abstraction source, Abstraction d1) {
		bSolver.injectContext(fSolver, callee, d3, callSite, source, d1);
	}


	private Set<Unit> getNextStmtFromDfg(Value value , Unit stmt, Abstraction abs) {
		Set<Unit> res = new HashSet<>();
		DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(value, stmt);
		AccessPath ap = abs.getAccessPath();
		SootField firstField = ap.getFirstField();
		if(dataFlowNode != null && dataFlowNode.getSuccs() != null) {
			Set<DataFlowNode> next = dataFlowNode.getSuccs().get(DataFlowNode.baseField);
			if(next != null)
				for(DataFlowNode d : next) {
					res.add(d.getStmt());
				}

			if(firstField != null) {
				Set<DataFlowNode> next1 = dataFlowNode.getSuccs().get(firstField);
				if(next1 != null)
					for(DataFlowNode d : next1) {
						res.add(d.getStmt());
					}
			}
		}
		return res;
	}

	@Override
	public boolean isFlowSensitive() {
		return true;
	}

	@Override
	public boolean requiresAnalysisOnReturn() {
		return false;
	}

	@Override
	public IInfoflowSolver getSolver() {
		return bSolver;
	}

	@Override
	public void cleanup() {
		bSolver.cleanup();
	}
	
}
