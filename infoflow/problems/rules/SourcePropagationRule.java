package soot.jimple.infoflow.problems.rules;

import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.summary.SummaryQuery;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Rule to introduce unconditional taints at sources
 * 
 * @author Steven Arzt
 *
 */
public class SourcePropagationRule extends AbstractTaintPropagationRule {

	public SourcePropagationRule(InfoflowManager manager, Aliasing aliasing,
			Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, aliasing, zeroValue, results);
	}

	private Collection<Abstraction> propagate(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		if (source == getZeroValue()) {
			// Check whether this can be a source at all
			final SourceInfo sourceInfo = getManager().getSourceSinkManager() != null
					? getManager().getSourceSinkManager().getSourceInfo(stmt, getManager()) : null;
					
			// We never propagate zero facts onwards
			killSource.value = true;
			
			// Is this a source?
			if (sourceInfo != null && !sourceInfo.getAccessPaths().isEmpty()) {
				Set<Abstraction> res = new HashSet<>();
				for (AccessPath ap : sourceInfo.getAccessPaths()) {
					// Create the new taint abstraction
					Abstraction abs = new Abstraction(ap,
							stmt,
							sourceInfo.getUserData(),
							false,
							false);

					if(getManager().getConfig().isSparseOptEnabled()) {
						if(getManager().getConfig().isSummaryOptEnabled()) {
							if(ap.isLocal()) {
								SummaryQuery.v().propagateAbsUsingSummaries(Collections.singleton(d1), getManager().getForwardSolver() , getAliasing().getBackwardsSolver(), abs, stmt);
							//	Set<Abstraction> newAbsSet = SummaryQuery.v().getForwardAbs(abs, stmt);
							//	res.addAll(newAbsSet);
							}else {
								System.out.println("Source propagation problem , it dont process ap's a instance field accesss");
							}
						}else {
							DataFlowNode dfg = DataFlowGraphQuery.v().useApTofindDataFlowGraph(ap, stmt);
							if(dfg == null)
								throw new RuntimeException("Source AccessPath cant find the relative Dfg, it should be built before using! ");
							res.add(dfg.deriveNewAbsbyAbs(abs));
						}
					}else {
						res.add(abs);

					}


					// Compute the aliases
					for (ValueBox vb : stmt.getUseAndDefBoxes()) {
						if (ap.startsWith(vb.getValue())) {
							// We need a relaxed "can have aliases" check here. Even if we have
							// a local, the source/sink manager is free to taint the complete local
							// while keeping alises valid (no overwrite).
							// The startsWith() above already gets rid of constants, etc.
							if (!TypeUtils.isStringType(vb.getValue().getType())
									|| ap.getCanHaveImmutableAliases())
								getAliasing().computeAliases(d1, stmt, vb.getValue(),
										res, getManager().getICFG().getMethodOf(stmt), abs);
						}
					}
					
					// Set the corresponding call site
					if (stmt.containsInvokeExpr())
						abs.setCorrespondingCallSite(stmt);
				}
				return res;
			}
			if (killAll != null)
				killAll.value = true;
		}
		return null;
	}

	@Override
	public Collection<Abstraction> propagateNormalFlow(Abstraction d1,
			Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, killAll);
	}

	@Override
	public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, null);
	}

	@Override
	public Collection<Abstraction> propagateReturnFlow(
			Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateCallFlow(Abstraction d1,
			Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		// Normally, we don't inspect source methods
		if (!getManager().getConfig().getInspectSources()
				&& getManager().getSourceSinkManager() != null) {
			final SourceInfo sourceInfo = getManager().getSourceSinkManager().getSourceInfo(
					stmt, getManager());
			if (sourceInfo != null)
				killAll.value = true;
		}
		
		// By default, we don't inspect sinks either
		if (!getManager().getConfig().getInspectSinks()
				&& getManager().getSourceSinkManager() != null) {
			final boolean isSink = getManager().getSourceSinkManager().isSink(
					stmt, getManager(), source.getAccessPath());
			if (isSink)
				killAll.value = true;
		}
		
		return null;
	}

}
