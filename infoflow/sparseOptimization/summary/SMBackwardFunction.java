package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.utils.Utils;
import soot.jimple.infoflow.util.BaseSelector;

import java.util.*;

/**
 * @author wanglei
 */
public class SMBackwardFunction {



    final private Map<Pair<Unit, Value>, SummaryGraph> forwardSummary = new HashMap<>();
    final private Map<Pair<Unit, Value>, SummaryGraph> backwardsSummary = new HashMap<>();

    final IInfoflowCFG backwardsICfg;

//    public SMBackwardFunction(Set<SummaryPath> jumpFunction, IInfoflowCFG cfg) {
//        this.jumpFunction = jumpFunction;
//        this.backwardsICfg = cfg;
//    }
    public SMBackwardFunction(IInfoflowCFG cfg) {
        this.backwardsICfg = cfg;
    }



    public Map<Pair<Unit, Value>, SummaryGraph> getForwardSummary() {
        return forwardSummary;
    }

    public Map<Pair<Unit, Value>, SummaryGraph> getBackwardsSummary() {
        return backwardsSummary;
    }

    public void propagate() {


    }

    public Set<SummaryPath> compute(SummaryPath path) {

        Stmt targetStmt = (Stmt)path.getTarget();

        Set<SummaryPath> res = null;
        if(targetStmt.containsInvokeExpr() || backwardsICfg.isExitStmt(targetStmt)) {
           res =  propagateCall(path);
        }else {
           res =  propagateNormal(path);
        }

        return res;
    }

    private Set<SummaryPath> propagateCall(SummaryPath path) {

        MyAccessPath source = path.getSourceAccessPath();
        Value base = source.getValue();

        Pair<Unit, Value> key = new Pair<Unit, Value>(path.getSrc(), base);

        SummaryGraph graph = null;
        if(source.getActiveStmt() == null) {
            if(forwardSummary.containsKey(key)) {
                graph = forwardSummary.get(key);
                graph.merge(path);
            }else {
                graph = new SummaryGraph(path);
                forwardSummary.put(key, graph);
            }


        }else if(source.getActiveStmt().equals(Utils.unknownStmt)) {

            if(backwardsSummary.containsKey(key)) {
                graph = backwardsSummary.get(key);
                graph.merge(path);
            }else {
                graph = new SummaryGraph(path);
                backwardsSummary.put(key, graph);
            }

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

        MyAccessPath targetAp = path.getTargetAccessPath();

        final boolean leftSideMatches = SummarySolver.baseMatches(leftValue, targetAp);

        Set<SummaryPath> res = new HashSet<>();
        if(leftSideMatches) {

            DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(leftValue, defStmt);
            if(dataFlowNode.getSuccs() != null) {
                for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                    for(DataFlowNode nextNode : tmpSet) {
                        SummaryPath nextPath = path.deriveNewMyAccessPath(nextNode.getStmt(), targetAp, nextNode);
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

        DataFlowNode targetDataFlowNode = path.getTargetNode();

        if(targetDataFlowNode.getIsLeft() && (rightValue instanceof Local || rightValue instanceof FieldRef)
                && !(leftValue.getType() instanceof PrimType)) {
            // 下面是左边是source，然后标记右边的例子，例如
            // a = b , source : a  则会产生 b
            // a.f = source

            boolean addRightValue = false;

            Pair<Value, SootField> pair = BasicBlockGraph.getBaseAndField(leftValue);
            Value leftBase = pair.getO1();
            SootField leftField = pair.getO2();
            MyAccessPath newSourceAp = null;


            if(leftBase.equals(targetAp.getValue()) && leftField == null ) {
                // a = b;
                // source是 a /a.f 的情况
                // 例如 xxxx = a ;  xxxx = a.f  or  xxxx = a.f2;  a.f =  xxxx; func(a);
                addRightValue = true;

            }else if (leftBase.equals(targetAp.getValue())) {
                if(targetAp.getFields() == null) {
                    // a.f = b ;
                    // source 是 a
                    // 这种情况说明使用了 a 的f域，除了函数调用是不会出现的，但是函数返回值情况会出现
                    // 比如 func(a) ;
                    // 不存在的情况 xxxx = a ;  xxxx = a.f  or  xxxx = a.f2;  a.f =  xxxx;
                    // 但是还有一种特殊情况，就是 a.f 恰好就是source，那么会被strong update 掉
                    addRightValue = true;
                    newSourceAp = path.getSourceAccessPath().deriveNewApAddfield(leftField);
                    newSourceAp.setStrongUpdateSource(true);

                }else {
                    SootField firstField = targetAp.getFields()[0];
                    if(firstField.equals(leftField)) {
                        // a.f1 = b;
                        // xxxx = a.f  or  xxxx = a.f2;

                        addRightValue = true;
                    } else {

                        //其实不应该到这里
                        //System.out.println("should not reach here!");
                        // a.f1 = source
                        // a.f2 = xxx
                    }

                }

            } else {
                //其实不应该到这里
                System.out.println("Back A2 should not reach here!");
            }

            // if one of them is true -> add rightValue
            if (addRightValue) {
                MyAccessPath newAp = SMForwardFunction.getLeftMyAccessPath(rightValue, targetDataFlowNode.getValue(),
                        targetDataFlowNode.getField(), path.getTargetAccessPath());

                //backward:

                DataFlowNode backNode = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(rightValue, defStmt);
                if(backNode.getSuccs() != null) {
                    for(Set<DataFlowNode> tmpSet : backNode.getSuccs().values()) {
                        for(DataFlowNode nextNode : tmpSet) {
                            SummaryPath nextPath = path.deriveNewMyAccessPath(newSourceAp, nextNode.getStmt(), newAp, nextNode);
                            res.add(nextPath);
                        }
                    }
                }

                //forward:
                DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(rightValue, defStmt, true, false);
                if(dataFlowNode.getSuccs() != null) {
                    for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                        for(DataFlowNode nextNode : tmpSet) {
                            SummaryPath nextPath = path.deriveNewMyAccessPath(newSourceAp, nextNode.getStmt(), newAp, nextNode);
                            nextPath.setForward(true);
                            res.add(nextPath);
                        }
                    }
                }

            }

        }else if(!targetDataFlowNode.getIsLeft() && !(rightValue.getType() instanceof PrimType)) {
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


            // 这个地方是直接拷贝FlowDroid的代码的，很关键的。
            // b = a , a/ a.f 是source ，是否需要标记b的情况
            // 更特殊的其实是这种情况 b = a.f ，source是a.f
            // 这种情况就说明了之后再遇到a.f的话，就会被SP掉，所以不应该在传播了
            // 所以这里面就是一个kill的问题了

            // If we have a = x with the taint "x" being inactive,
            // we must not taint the left side. We can only taint
            // the left side if the tainted value is some "x.y".


            if(!targetAp.getValue().equals(targetDataFlowNode.getValue())) {
                throw new RuntimeException("B1 base not equal!");
            }
            SootField rightField = targetDataFlowNode.getField();
            boolean addLeftValue = false;
            MyAccessPath newLeftAp = null;

            //首先处理kill set
            if(path.getKillSet() != null && !rightField.equals(DataFlowNode.baseField) && targetAp.getFields() == null) {
                if(path.getKillSet().contains(rightField)) {
                    return res;
                }
            }

            SootField[] apFileds = path.getTargetAccessPath().getFields();
            MyAccessPath newSourceAp = null;
            if(apFileds == null) {
                // source 是 a 的情况
                if(rightField.equals(DataFlowNode.baseField)) {
                    // b = a : source : a
                    addLeftValue = true;
                }else {
                    // b = a.f : source a
                    //
                    newSourceAp = path.getSourceAccessPath().deriveNewApAddfield(rightField);
                    newSourceAp.setStrongUpdateSource(true);
                }

            }else {

                if(rightField.equals(DataFlowNode.baseField)) {
                    // b = a : source a.f
                    addLeftValue = true;


                }else if(apFileds[0].equals(rightField)) {
                    // b = a.f : source a.f
                    newSourceAp = path.getSourceAccessPath().clone();
                    newSourceAp.setStrongUpdateSource(true);
                    addLeftValue = true;

                }else {
                    //should not reach here!
                    return res;
                }
            }

            if(addLeftValue) {
                newLeftAp = SMForwardFunction.getLeftMyAccessPath(leftValue, targetDataFlowNode.getValue(),
                        targetDataFlowNode.getField(), path.getTargetAccessPath());
            }

            // a.f1 但是 source is a.f2
            if(newLeftAp == null)
                return res;
            // b = a;
            // a.f = xxx;

            //backward:

            DataFlowNode backNode = DataFlowGraphQuery.v().useValueTofindBackwardDataFlowGraph(leftValue, defStmt);
            if(backNode.getSuccs() != null) {
                for(Set<DataFlowNode> tmpSet : backNode.getSuccs().values()) {
                    for(DataFlowNode nextNode : tmpSet) {
                        SummaryPath nextPath = path.deriveNewMyAccessPath(newSourceAp , nextNode.getStmt(), newLeftAp, nextNode);
                        res.add(nextPath);
                    }
                }
            }

            //forward:
            DataFlowNode dataFlowNode = DataFlowGraphQuery.v().useValueTofindForwardDataFlowGraph(leftValue, defStmt);
            if(dataFlowNode.getSuccs() != null) {
                for(Set<DataFlowNode> tmpSet : dataFlowNode.getSuccs().values()) {
                    for(DataFlowNode nextNode : tmpSet) {
                        SummaryPath nextPath = path.deriveNewMyAccessPath(newSourceAp, nextNode.getStmt(), newLeftAp, nextNode);
                        nextPath.setForward(true);
                        res.add(nextPath);
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
