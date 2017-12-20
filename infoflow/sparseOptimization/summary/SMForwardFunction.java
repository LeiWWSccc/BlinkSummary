package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.Pair;
import soot.SootField;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.util.BaseSelector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class SMForwardFunction {


    final private Map<Pair<Unit, Value>, SummaryGraph> summary = new HashMap<>();

    final private Set<SummaryPath> jumpFunction;


    public SMForwardFunction(Set<SummaryPath> jumpFunction) {
        this.jumpFunction = jumpFunction;
    }


    public void propagate() {


    }

    public Set<SummaryPath> compute(SummaryPath path) {

        Stmt targetStmt = (Stmt)path.getTarget();

        Set<SummaryPath> res = null;
        if(targetStmt.containsInvokeExpr()) {
           res =  propagateCall(path);
        }else {
           res =  propagateNormal(path);
        }

        return res;
    }

    private Set<SummaryPath> propagateCall(SummaryPath path) {

        MyAccessPath source = path.getdSource();
        Value base = source.getValue();

        Pair<Unit, Value> key = new Pair<Unit, Value>(path.getSrc(), base);

        SummaryGraph graph = null;
        if(summary.containsKey(key)) {
            graph = summary.get(key);
            graph.merge(path);
        }else {
            graph = new SummaryGraph(path);
            summary.put(key, graph);
        }

        return null;
    }


    private Set<SummaryPath> propagateNormal(SummaryPath path) {

        Stmt stmt = (Stmt) path.getTarget();

        Set<SummaryPath> res = null;

        if (stmt instanceof AssignStmt) {

            res = createNewTaintOnAssignment(stmt, path);

        }
        return res;

    }

    public static MyAccessPath getLeftMyAccessPath(Value left, Value rightBase,
                                             SootField rightField, MyAccessPath rightAp) {

        // a. f : rightBase a , rightField : f / basefield

        SootField[] remainFields = null;
        if(!rightAp.getValue().equals(rightBase)) {
            throw new RuntimeException("base not equal !");
        }
        SootField[] apFields = rightAp.getFields();
        if(rightField.equals(DataFlowNode.baseField)) {
            remainFields = apFields;
        }else  {
            if(apFields == null)
                throw new RuntimeException("rightfield not equal !");

            if(rightField != apFields[0]) {
                return null;
            }else {
                if(apFields.length > 1) {
                    remainFields = new SootField[apFields.length - 1];
                    System.arraycopy(apFields, 1, remainFields, 0, apFields.length - 1);
                }else {
                    remainFields = null;
                }
            }
        }

        MyAccessPath ret = SummarySolver.getMyAccessPath(left, remainFields);

        ret.setActiveStmt(rightAp.getActiveStmt());
        return ret;
    }

    private Set<SummaryPath> createNewTaintOnAssignment(Stmt src,  SummaryPath path) {

        final AssignStmt assignStmt = (AssignStmt) src;
        final Value left = assignStmt.getLeftOp();
        final Value right = assignStmt.getRightOp();
        final Value[] rightVals = BaseSelector.selectBaseList(right, true);

        DataFlowNode targetNode = path.getTargetNode();
        MyAccessPath sourceAp = path.getdTarget();

        MyAccessPath targetAp = path.getdTarget();
        // p = a.f
        // p = a ;
        // p.f = a
        Set<SummaryPath> res = new HashSet<>();

        if(!targetNode.getIsLeft()) {
            // 这个是source在右边的情况
            // b = a / b = a.f  此时source是 a / a.f的情况
            // 要做的就是把 b 标记
            //但是存在 b = a 但是 source 是a.f的情况， 所以需要

            MyAccessPath newLeftAp = getLeftMyAccessPath(left, targetNode.getValue(), targetNode.getField(), path.getdTarget());

            // a.f1 但是 source is a.f2
            if(newLeftAp == null)
                return res;

            if(SummarySolver.canHaveAliases(assignStmt, left, targetAp)) {

                //alias
                MyAccessPath newBackwardAp = newLeftAp.deriveInactiveAp(src);

                DataFlowNode next = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(left, src);

                if(next.getSuccs() != null)
                    for(Set<DataFlowNode> tmpSet : next.getSuccs().values()) {
                        for(DataFlowNode nextNode : tmpSet) {
                            SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newBackwardAp, nextNode);
                            nextPath.setForward(false);
                            res.add(nextPath);
                        }
                    }

            }


            DataFlowNode next = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(left, src);

            if(next.getSuccs() != null)
                for(Set<DataFlowNode> tmpSet : next.getSuccs().values()) {
                    for(DataFlowNode nextNode : tmpSet) {
                        SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newLeftAp, nextNode);
                        res.add(nextPath);
                    }
                }
        }
        return res;



//
//
//        for (Value rightVal : rightVals) {
//
//            if (rightVal instanceof FieldRef) {
//                // Get the field reference
//                FieldRef rightRef = (FieldRef) rightVal;
//
//                // If the right side references a NULL field, we kill the taint
//                if (rightRef instanceof InstanceFieldRef
//                        && ((InstanceFieldRef) rightRef).getBase().getType() instanceof NullType)
//                    return null;
//
//                // Check for aliasing
//                mappedAP = aliasing.mayAlias(newSource.getAccessPath(), rightRef);
//
//                // check if static variable is tainted (same name, same class)
//                //y = X.f && X.f tainted --> y, X.f tainted
//                if (rightVal instanceof StaticFieldRef) {
//                    if (manager.getConfig().getEnableStaticFieldTracking() && mappedAP != null) {
//                        addLeftValue = true;
//                        cutFirstField = true;
//                    }
//                }
//                // check for field references
//                //y = x.f && x tainted --> y, x tainted
//                //y = x.f && x.f tainted --> y, x tainted
//                else if (rightVal instanceof InstanceFieldRef) {
//                    Local rightBase = (Local) ((InstanceFieldRef) rightRef).getBase();
//                    Local sourceBase = newSource.getAccessPath().getPlainValue();
//                    final SootField rightField = rightRef.getField();
//
//                    // We need to compare the access path on the right side
//                    // with the start of the given one
//                    if (mappedAP != null) {
//                        addLeftValue = true;
//                        cutFirstField = (mappedAP.getFieldCount() > 0
//                                && mappedAP.getFirstField() == rightField);
//                    }
//                    else if (aliasing.mayAlias(rightBase, sourceBase)
//                            && newSource.getAccessPath().getFieldCount() == 0
//                            && newSource.getAccessPath().getTaintSubFields()) {
//                        addLeftValue = true;
//                        targetType = rightField.getType();
//                        if (mappedAP == null)
//                            mappedAP = manager.getAccessPathFactory().createAccessPath(rightBase, true);
//                    }
//                }
//            }
//            // indirect taint propagation:
//            // if rightvalue is local and source is instancefield of this local:
//            // y = x && x.f tainted --> y.f, x.f tainted
//            // y.g = x && x.f tainted --> y.g.f, x.f tainted
//            else if (rightVal instanceof Local && newSource.getAccessPath().isInstanceFieldRef()) {
//                Local base = newSource.getAccessPath().getPlainValue();
//                if (aliasing.mayAlias(rightVal, base)) {
//                    addLeftValue = true;
//                    targetType = newSource.getAccessPath().getBaseType();
//                }
//            }
//            // generic case, is true for Locals, ArrayRefs that are equal etc..
//            //y = x && x tainted --> y, x tainted
//            else if (aliasing.mayAlias(rightVal, newSource.getAccessPath().getPlainValue())) {
//                if (!(assignStmt.getRightOp() instanceof NewArrayExpr)) {
//                    if (manager.getConfig().getEnableArraySizeTainting()
//                            || !(rightValue instanceof NewArrayExpr)) {
//                        addLeftValue = true;
//                        targetType = newSource.getAccessPath().getBaseType();
//                    }
//                }
//            }
//
//
//        }


    }


}
