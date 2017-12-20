package soot.jimple.infoflow.sparseOptimization.dataflowgraph.function;

import heros.solver.Pair;
import soot.SootField;
import soot.Value;
import soot.jimple.IdentityStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNodeFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class BackwardFunction extends AbstractFunction {

    BackwardFunction(Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited,
                     Set<Value> parmAndThis,
                     Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed) {
        super(visited, parmAndThis, seed);
    }

    public Set<Pair<BaseInfoStmt, DataFlowNode>> flowFunction(BaseInfoStmt target, DataFlowNode source) {
        Set<Pair<BaseInfoStmt, DataFlowNode>> res = new HashSet<>();


        if (target.base == null) {
            //return
            if(canNodeReturn(source.getValue())) {
                DataFlowNode returnNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, null, false);
                returnNode = getNewDataFlowNode(target, returnNode);
                source.setSuccs(DataFlowNode.baseField, returnNode);
            }
            //res.add(newNode);
            return res;
        }

        SootField baseField = DataFlowNode.baseField;

        SootField sourceField = source.getField();

        SootField targetLeftField = target.leftField;
        SootField[] targetRightFields = target.rightFields;
        SootField[] targetArgFields = target.argsFields;

        boolean isKillSource = false;
        DataFlowNode newNode = null;

        if (sourceField != baseField) {
            //(1) source like  :  a.f1

            //a = b;   gen  b.f
            //a.f1 = xxx;

            //a.f1 = b;
            //a.f1 =

            if (targetLeftField != null) {
                //(1.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;

                if (targetLeftField == baseField ) {
                    //(1.1.1) a = xxx;  source : a.f1 , gen f1 -> <a>
                    // a = b;
                    // a.f1 = pwd;

                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, targetLeftField, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(sourceField, newNode);
                    //res.add(newNode);

                }else if(targetLeftField.equals(sourceField)) {
                    //(1.1.2) a.f1 = xxx; source : a.f1  , gen f1 -> <a.f1>
                    // a.f1 = b;
                    // a.f1 = pwd;
                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, targetLeftField, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(sourceField, newNode);
                   // source.setKillField(targetLeftField);

                }  else {
                    //(1.1.3) a.f2 = xxx;  source : a.f1, do nothing.

                    //a.f2 = b;
                    //a.f1 = pwd;
                }

            }

            if (targetRightFields != null) {
                //(1.2) like : xxx = a; or xxx = a.f1; or xxx = a.f2;
                if(targetRightFields.length != 1) {
                    throw new RuntimeException("Sparse Op: Alias analysi should't at two or more rightOP ");
                }

                SootField right = targetRightFields[0];

                if (right == baseField || sourceField.equals(right)) {
                    //(1.2.1) xxx = a;  source : a.f1  , kill source
                    //(1.2.2) xxx = a.f1;   source : a.f1 , kill source
                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(sourceField, newNode);
                    //res.add(newNode);
                } else {
                    //(1.2.3) xxx = a.f2; source : a.f1  , do nothing.
                }

            }

            if (targetArgFields != null) {
                for (int i = 0; i < targetArgFields.length; i++) {
                    SootField arg = targetArgFields[i];
                    if (arg == baseField) {
                        //(1.3.1) foo(a);    source : a.f1 , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(sourceField, newNode);
                        //res.add(newNode);
                        isKillSource = true;

                    } else if (arg.equals(sourceField)) {
                        //(1.3.2) foo(a.f1); source : a.f1  , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(sourceField, newNode);
                        //res.add(newNode);
                    } else {
                        //(1.3.3) foo(a.f2); source : a.f1, do nothing.

                    }
                }
            }

        } else if (sourceField != null) {
            //(2) source like  :  a

            if (targetLeftField != null) {
                //(2.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;

                if (targetLeftField == baseField) {
                    // a = xxx;    source : a , gen new a

                    // a = c;

                    // a = b;   kill source
                    // a.f = pwd;


                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, targetLeftField, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(sourceField, newNode);
                    if(!(target.stmt instanceof IdentityStmt))
                        isKillSource = true;

                } else {
                    //(1) a.f1 = xxx ; source : a  , gen new a.f1
                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, targetLeftField, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(sourceField, newNode);
                }

            }

            if (targetRightFields != null) {
                //like xxx = a;  or xxx = a.f1 ;

                if(targetRightFields.length != 1) {
                    throw new RuntimeException("Sparse Op: Alias analysi should't at two or more rightOP ");
                }

                SootField right = targetRightFields[0];

                if (right == baseField) {
                    // xxx = a;   source : a , kill source

                    // c = a;

                    // b = a;
                    // a.f = pwd;

                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(right, newNode);

                } else {
                    // xxx = a.f1  or xxx = a.f2; source : a ,  just kill field f1.


                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                    newNode = getNewDataFlowNode(target, newNode);
                    source.setSuccs(right, newNode);
                    // b = a.f1
                    // a.f1 = pwd;   strong update !!!

                    // b = a.f2;
                    // a.f1 = pwd;    no strong update

                   // source.setKillField(right);
                }
            }

            if (targetArgFields != null) {
                for (int i = 0; i < targetArgFields.length; i++) {
                    SootField arg = targetArgFields[i];
                    if (arg == baseField) {
                        // foo(a);    source : a , gen "base" -> <a>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(arg, newNode);

                        isKillSource = true;

                    } else if (arg.equals(sourceField)) {
                        // foo(a.f1); source : a , gen f1 -> <a.f1>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(arg, newNode);
                    }
                }
            }

        } else {
            throw new RuntimeException("source's base field can not be null ");
        }

        if (!isKillSource)
            addResult(res, target, source);
        return res;

    }
}
