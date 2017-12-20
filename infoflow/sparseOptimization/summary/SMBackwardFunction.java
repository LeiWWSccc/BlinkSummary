package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.util.BaseSelector;

import java.util.*;

/**
 * @author wanglei
 */
public class SMBackwardFunction {


    final private Map<Pair<Unit, Value>, SummaryGraph> summary = new HashMap<>();

    final private Set<SummaryPath> jumpFunction;


    public SMBackwardFunction(Set<SummaryPath> jumpFunction) {
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

        if (stmt instanceof DefinitionStmt) {
            final DefinitionStmt defStmt = (DefinitionStmt) stmt;

            res = computeAliases(defStmt, path);

        }
        return res;

    }
    private Set<SummaryPath> computeAliases(DefinitionStmt defStmt,  SummaryPath path) {

        final Value leftValue = BaseSelector.selectBase(defStmt.getLeftOp(), true);

        MyAccessPath target = path.getdTarget();
        DataFlowNode targetNode = path.getTargetNode();

        final boolean leftSideMatches = SummarySolver.baseMatches(leftValue, target);

        Set<SummaryPath> res = new HashSet<>();
        if(leftSideMatches) {

            DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(leftValue, defStmt);
            if(dataFlowNode.getSuccs() != null) {
                for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                    for(DataFlowNode nextNode : tmpSet) {
                        SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), target, nextNode);
                        nextPath.setForward(true);
                        res.add(nextPath);
                    }
                }
            }

        }

        // We only handle assignments and identity statements
        if (defStmt instanceof IdentityStmt) {
            //res.add(source);
            return res;
        }
        if (!(defStmt instanceof AssignStmt))
            return res;

        // Get the right side of the assignment
        final Value rightValue = BaseSelector.selectBase(defStmt.getRightOp(), false);

        // Is the left side overwritten completely?
        if (leftSideMatches) {
            // Termination shortcut: If the right side is a value we do not track,
            // we can stop here.
            if (!(rightValue instanceof Local || rightValue instanceof FieldRef))
                return Collections.emptySet();
        }

        // If we assign a constant, there is no need to track the right side
        // any further or do any forward propagation since constants cannot
        // carry taint.
        if (rightValue instanceof Constant)
            return res;

        // If this statement creates a new array, we cannot track upwards the size
        if (defStmt.getRightOp() instanceof NewArrayExpr)
            return res;

        // We only process heap objects. Binary operations can only
        // be performed on primitive objects.
        if (defStmt.getRightOp() instanceof BinopExpr)
            return res;
        if (defStmt.getRightOp() instanceof UnopExpr)
            return res;


        // 这个地方是直接拷贝FlowDroid的代码的，很关键的。
        // b = a , a/ a.f 是source ，是否需要标记b的情况
        // 更特殊的其实是这种情况 b = a.f ，source是a.f
        // 这种情况就说明了之后再遇到a.f的话，就会被SP掉，所以不应该在传播了
        // 所以这里面就是一个kill的问题了



        // If we have a = x with the taint "x" being inactive,
        // we must not taint the left side. We can only taint
        // the left side if the tainted value is some "x.y".



        boolean aliasOverwritten = SummarySolver.baseMatchesStrict(rightValue, target)
                && rightValue.getType() instanceof RefType ;

        if (!aliasOverwritten && !(rightValue.getType() instanceof PrimType)) {
            // If the tainted value 'b' is assigned to variable 'a' and 'b'
            // is a heap object, we must also look for aliases of 'a' upwards
            // from the current statement.
            MyAccessPath newLeftAp = null;
            if (rightValue instanceof InstanceFieldRef) {
//                InstanceFieldRef ref = (InstanceFieldRef) rightValue;
//                if (source.getAccessPath().isInstanceFieldRef()
//                        && ref.getBase() == source.getAccessPath().getPlainValue()
//                        && source.getAccessPath().firstFieldMatches(ref.getField())) {
//                    AccessPath ap = manager.getAccessPathFactory().copyWithNewValue(
//                            source.getAccessPath(), leftValue, source.getAccessPath().getFirstFieldType(), true);
//                    newLeftAbs = checkAbstraction(source.deriveNewAbstraction(ap, defStmt));
//                }
            }
//            else if (manager.getConfig().getEnableStaticFieldTracking()
//                    && rightValue instanceof StaticFieldRef) {
//                StaticFieldRef ref = (StaticFieldRef) rightValue;
//                if (source.getAccessPath().isStaticFieldRef()
//                        && source.getAccessPath().firstFieldMatches(ref.getField())) {
//                    AccessPath ap = manager.getAccessPathFactory().copyWithNewValue(source.getAccessPath(),
//                            leftValue, source.getAccessPath().getBaseType(), true);
//                    newLeftAbs = checkAbstraction(source.deriveNewAbstraction(ap, defStmt));
//                }
//            }
            else if (rightValue == target.getValue()) {


//                Type newType = source.getAccessPath().getBaseType();
//                if (leftValue instanceof ArrayRef)
//                    newType = TypeUtils.buildArrayOrAddDimension(newType);
//                else if (defStmt.getRightOp() instanceof ArrayRef)
//                    newType = ((ArrayType) newType).getElementType();
//
//                // Type check
//                if (!manager.getTypeUtils().checkCast(source.getAccessPath(),
//                        defStmt.getRightOp().getType()))
//                    return Collections.emptySet();
//
//                // If the cast was realizable, we can assume that we had the
//                // type to which we cast. Do not loosen types, though.
//                if (defStmt.getRightOp() instanceof CastExpr) {
//                    CastExpr ce = (CastExpr) defStmt.getRightOp();
//                    if (!manager.getHierarchy().canStoreType(newType, ce.getCastType()))
//                        newType = ce.getCastType();
//                }
                // Special type handling for certain operations
                //else
                if (defStmt.getRightOp() instanceof LengthExpr) {
                    // ignore. The length of an array is a primitive and thus
                    // cannot have aliases
                    return res;
                }
                else if (defStmt.getRightOp() instanceof InstanceOfExpr) {
                    // ignore. The type check of an array returns a
                    // boolean which is a primitive and thus cannot
                    // have aliases
                    return res;
                }

                if (newLeftAp == null) {
                     newLeftAp = SMForwardFunction.getLeftMyAccessPath(leftValue, targetNode.getValue(),
                            targetNode.getField(), path.getdTarget());

                }
            }

            if (newLeftAp != null) {
                // If we ran into a new abstraction that points to a
                // primitive value, we can remove it
//                if (newLeftAbs.getAccessPath().getLastFieldType() instanceof PrimType)
//                    return res;

                if (!newLeftAp.equals(target)) {

                    // b = a;
                    // a.f = xxx;

                    //backward:

                    DataFlowNode backNode = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(leftValue, defStmt);
                    if(backNode.getSuccs() != null) {
                        for(Set<DataFlowNode> tmpSet : backNode.getSuccs().values()) {
                            for(DataFlowNode nextNode : tmpSet) {
                                SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newLeftAp, nextNode);
                                res.add(nextPath);
                            }
                        }
                    }

                    //forward:
                    DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(leftValue, defStmt);
                    if(dataFlowNode.getSuccs() != null) {
                        for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                            for(DataFlowNode nextNode : tmpSet) {
                                SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newLeftAp, nextNode);
                                nextPath.setForward(true);
                                res.add(nextPath);
                            }
                        }
                    }


                }
            }
        }



        // 下面是左边是source，然后标记右边的例子，例如
        // a = b , source : a  则会产生 b
        //

        if ((rightValue instanceof Local || rightValue instanceof FieldRef)
                && !(leftValue.getType() instanceof PrimType)) {

            boolean addRightValue = false;

            if (leftValue instanceof InstanceFieldRef) {

            }else if(leftValue instanceof Local ) {
                Local base = (Local) target.getValue();
                //a = xxx;  target: a / a.f /
                //只要base相同就应该把right标记

                if (leftValue == base) {
                    addRightValue = true;
                }
            }


            // if one of them is true -> add rightValue
            if (addRightValue) {
                MyAccessPath newAp = SMForwardFunction.getLeftMyAccessPath(rightValue, targetNode.getValue(),
                        targetNode.getField(), path.getdTarget());

                //backward:

                DataFlowNode backNode = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(rightValue, defStmt);
                if(backNode.getSuccs() != null) {
                    for(Set<DataFlowNode> tmpSet : backNode.getSuccs().values()) {
                        for(DataFlowNode nextNode : tmpSet) {
                            SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newAp, nextNode);
                            res.add(nextPath);
                        }
                    }
                }


                //forward:
                DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(rightValue, defStmt, true, false);
                if(dataFlowNode.getSuccs() != null) {
                    for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                        for(DataFlowNode nextNode : tmpSet) {
                            SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), newAp, nextNode);
                            nextPath.setForward(true);
                            res.add(nextPath);
                        }
                    }
                }

            }


        }

        return res;

    }

//    private MyAccessPath getLeftMyAccessPath(Value left, Value rightBase,
//                                             SootField rightField, MyAccessPath rightAp) {
//
//        SootField[] remainFields = null;
//        if(!rightAp.getValue().equals(rightBase)) {
//            throw new RuntimeException("base not equal !");
//        }
//        SootField[] rightFields = rightAp.getFields();
//        if(rightField.equals(DataFlowNode.baseField)) {
//            remainFields = rightFields;
//        }else  {
//
//        }
//
//        MyAccessPath ret = SummarySolver.getMyAccessPath(left, remainFields);
//        return ret;
//    }




}
