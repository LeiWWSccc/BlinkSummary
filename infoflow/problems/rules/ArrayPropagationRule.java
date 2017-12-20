package soot.jimple.infoflow.problems.rules;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPath.ArrayTaintType;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Rule for propagating array accesses
 * 
 * @author Steven Arzt
 *
 */
public class ArrayPropagationRule extends AbstractTaintPropagationRule {

	public ArrayPropagationRule(InfoflowManager manager, Aliasing aliasing,
			Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, aliasing, zeroValue, results);
	}

	@Override
	public Collection<Abstraction> propagateNormalFlow(Abstraction d1,
			Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		// Get the assignment
		if (!(stmt instanceof AssignStmt))
			return null;
		AssignStmt assignStmt = (AssignStmt) stmt;
		
		Abstraction newAbs = null;
		final Value leftVal = assignStmt.getLeftOp();
		final Value rightVal = assignStmt.getRightOp();
		
		if (rightVal instanceof LengthExpr) {
			LengthExpr lengthExpr = (LengthExpr) rightVal;
			if (getAliasing().mayAlias(source.getAccessPath().getPlainValue(), lengthExpr.getOp())) {
				// Is the length tainted? If only the contents are tainted, we the
				// incoming abstraction does not match
				if (source.getAccessPath().getArrayTaintType() == ArrayTaintType.Contents)
					return null;
				
				// Taint the array length
				AccessPath ap = getManager().getAccessPathFactory().createAccessPath(
						leftVal, null, IntType.v(),
						(Type[]) null, true, false, true, ArrayTaintType.ContentsAndLength);
				newAbs = source.deriveNewAbstraction(ap, assignStmt);
			}
		}
		//y = x[i] && x tainted -> x, y tainted
		else if (rightVal instanceof ArrayRef) {
			Value rightBase = ((ArrayRef) rightVal).getBase();
			Value rightIndex = ((ArrayRef) rightVal).getIndex();
			if (source.getAccessPath().getArrayTaintType() != ArrayTaintType.Length
					&& getAliasing().mayAlias(rightBase, source.getAccessPath().getPlainValue())) {
				// We must remove one layer of array typing, e.g., A[][] -> A[]
				Type targetType = source.getAccessPath().getBaseType();
				assert targetType instanceof ArrayType;
				targetType = ((ArrayType) targetType).getElementType();
				
				// Create the new taint abstraction
				ArrayTaintType arrayTaintType = source.getAccessPath().getArrayTaintType();
				AccessPath ap = getManager().getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
						leftVal, targetType, false, true, arrayTaintType);
				newAbs = source.deriveNewAbstraction(ap, assignStmt);
			}
			
			// y = x[i] with i tainted
			else if (source.getAccessPath().getArrayTaintType() != ArrayTaintType.Length
					&& rightIndex == source.getAccessPath().getPlainValue()
					&& getManager().getConfig().getEnableImplicitFlows()) {
				// Create the new taint abstraction
				ArrayTaintType arrayTaintType = ArrayTaintType.ContentsAndLength;
				AccessPath ap = getManager().getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
						leftVal, null, false, true, arrayTaintType);
				newAbs = source.deriveNewAbstraction(ap, assignStmt);
			}
		}
		// y = new A[i] with i tainted
		else if (rightVal instanceof NewArrayExpr
				&& getManager().getConfig().getEnableArraySizeTainting()) {
			NewArrayExpr newArrayExpr = (NewArrayExpr) rightVal;
			if (getAliasing().mayAlias(source.getAccessPath().getPlainValue(), newArrayExpr.getSize())) {
				// Create the new taint abstraction
				AccessPath ap = getManager().getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
						leftVal, null, false, true, ArrayTaintType.Length);
				newAbs = source.deriveNewAbstraction(ap, assignStmt);
			}
		}
		
		if (newAbs == null)
			return null;
		
		Set<Abstraction> res = new HashSet<>();

		if(getManager().getConfig().isSparseOptEnabled()) {
			DataFlowNode node = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(leftVal, stmt);
			res.add(node.deriveNewAbsbyAbs(newAbs));

		}else
			res.add(newAbs);
		
		// Compute the aliases
		if (Aliasing.canHaveAliases(assignStmt, leftVal, newAbs))
			getAliasing().computeAliases(d1, assignStmt, leftVal, res,
					getManager().getICFG().getMethodOf(assignStmt), newAbs);
		
		return res;
	}

	@Override
	public Collection<Abstraction> propagateCallFlow(Abstraction d1,
			Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateReturnFlow(
			Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

}
