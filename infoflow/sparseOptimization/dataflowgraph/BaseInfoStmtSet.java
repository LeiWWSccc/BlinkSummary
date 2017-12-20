package soot.jimple.infoflow.sparseOptimization.dataflowgraph;

import heros.solver.Pair;
import soot.SootField;
import soot.SootMethod;
import soot.Value;
import soot.jimple.ReturnStmt;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlock;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNodeFactory;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.function.AbstractFunction;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.function.FunctionFactory;

import java.util.*;

/**
 * @author wanglei
 */
public class BaseInfoStmtSet {
    Set<BaseInfoStmt> varInfoSets = new HashSet<>();
    Value base;
    SootMethod m ;

    List<BaseInfoStmt> returnStmtList = new ArrayList<>();

    private Set<Value> paramAndThis ;



    Set<DFGEntryKey> dfgEntryKeyForSummarySet = new HashSet<>();



    Set<DFGEntryKey> backwardDfgEntryKeyForSummarySet = new HashSet<>();

    Map<DFGEntryKey, Set<DataFlowNode>> returnInfo = null;
    public BaseInfoStmtSet(SootMethod m, Value base, List<BaseInfoStmt> returnStmtList, Set<Value> paramAndThis ) {
        this.base = base;
        this.m = m;
        this.returnStmtList = returnStmtList;
        this.paramAndThis = paramAndThis;
    }

    public Set<DFGEntryKey> getDfgEntryKeyForSummarySet() {
        return dfgEntryKeyForSummarySet;
    }

    public Set<DFGEntryKey> getBackwardDfgEntryKeyForSummarySet() {
        return backwardDfgEntryKeyForSummarySet;
    }

    public void add(BaseInfoStmt varInfo) {
        varInfoSets.add(varInfo);
    }
    public void addAll(List<BaseInfoStmt> varInfo) {
        varInfoSets.addAll(varInfo);
    }

    public Pair<Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>,
            Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> solve() {
        HashMap<BasicBlock, Set<BaseInfoStmt>> bbToBaseInfoMap = new HashMap<>();

        //System.out.println("CC1" + m.toString() + base.toString());
        for(BaseInfoStmt varInfo : varInfoSets) {

            int a = 0;
            if(varInfo.stmt.toString().equals("$i1 = staticinvoke <com.appbrain.c.e: int b(int,com.appbrain.c.c)>(36, $r1)"))
                a ++;


            if(varInfo.base != null && !varInfo.base.equals(base))
                throw  new RuntimeException("base should equal or return stmt!");
            //System.out.println(varInfo.toString());
            BasicBlock bb = varInfo.bb;
            Set<BaseInfoStmt> set = null;
            if(bbToBaseInfoMap.containsKey(bb)){
                set =  bbToBaseInfoMap.get(bb);
            }else {
                set = new HashSet<BaseInfoStmt>();
                bbToBaseInfoMap.put(bb, set);
            }
            set.add(varInfo);
        }
       // System.out.println("CC2");
        BaseInfoStmtCFG baseCFG = new BaseInfoStmtCFG(bbToBaseInfoMap);
        baseCFG.solve();
        int count = 0;
        //printBaseInfoStmtSet();

        Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed = new HashMap<>();
        Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seedbackward = new HashMap<>();

        Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > tmpforward = new HashMap<>();
        Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > tmpbackward = new HashMap<>();

        //System.out.println("CC3");
       // Set<Pair<VariableInfo, DataFlowNode>> seed = new HashSet<>();



        for(BaseInfoStmt baseInfo : varInfoSets) {

            boolean isInvoker = false;
            if(baseInfo.stmt.containsInvokeExpr()) {
                isInvoker = true;
            }

            if(baseInfo.leftField != null) {
               // Unit u = baseInfo.stmt;
                DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                        (baseInfo.stmt, baseInfo.base, baseInfo.leftField, true);
                Pair<BaseInfoStmt, DataFlowNode> path = new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNode);
                DFGEntryKey dfgForwardKey = new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.leftField);
                seed.put(dfgForwardKey,path);
               // seed.add(new Pair<VariableInfo, DataFlowNode>(baseInfo, dataFlowNode));

                tmpforward.put(path, dataFlowNode);

                DataFlowNode backDataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                        (baseInfo.stmt, baseInfo.base, baseInfo.leftField, true);
                Pair<BaseInfoStmt, DataFlowNode> pathb =  new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, backDataFlowNode);

                seedbackward.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.leftField),pathb);

                tmpbackward.put(pathb , backDataFlowNode);

                //为了之后计算summary 存储入口
                if(isInvoker) {
                    dfgEntryKeyForSummarySet.add(dfgForwardKey);
                    backwardDfgEntryKeyForSummarySet.add(dfgForwardKey);
                }



            }
            if(baseInfo.rightFields != null && baseInfo.rightFields.length == 1) {
                DataFlowNode fordataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                        (baseInfo.stmt, baseInfo.base, baseInfo.rightFields[0], false);
                Pair<BaseInfoStmt, DataFlowNode> forpath = new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, fordataFlowNode);
                seed.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.rightFields[0], true, false), forpath);

                tmpforward.put(forpath, fordataFlowNode);

                DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                        (baseInfo.stmt, baseInfo.base, baseInfo.rightFields[0], true);
                Pair<BaseInfoStmt, DataFlowNode> path = new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNode);
                seedbackward.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.rightFields[0]), path);

                tmpbackward.put(path, dataFlowNode);

            }

            if(baseInfo.argsFields != null) {
                for(int i = 0; i < baseInfo.argsFields.length; i++) {
                    DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                            (baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i], true);

                    Pair<BaseInfoStmt, DataFlowNode> path = new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNode);
                    DFGEntryKey forKey =  new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i]);
                    if(!seed.containsKey(forKey)) {
                        seed.put(forKey, path);
                        tmpforward.put(path, dataFlowNode);
                    }else {
                        int count1 = 0;
                    }

                    DataFlowNode dataFlowNodeback = DataFlowNodeFactory.v().createDataFlowNode
                            (baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i], true);
                    Pair<BaseInfoStmt, DataFlowNode> pathback = new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNodeback);

                    seedbackward.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i]), pathback);

                    tmpbackward.put(pathback, dataFlowNodeback);
                }
            }

        }

        //why to add <parm, returnStmt> to the seed?
        //because :
        /*
        foo(Obj a, Obj b) {
            a = b;
            return ;
        }
        for a in the backward process, we start from the "return" stmt,
        and  we should find its use, which is "a = b";
        but we dont have the info in return stmt,
        so we just add all the parms ,this , static variables to form  a <parm , returnStmt> Path
         */
        for(BaseInfoStmt exitStmt : returnStmtList) {

            if(exitStmt.stmt instanceof ReturnStmt) {
                ReturnStmt returnStmt = (ReturnStmt)exitStmt.stmt;
                Value retLocal = returnStmt.getOp();
                Pair<Value, SootField> pair = BasicBlockGraph.getBaseAndField(retLocal);
                Value retBase = pair.getO1();
                SootField retfield = pair.getO2();

                // return null; !!!
                if(retBase != null && retBase.equals(this.base)) {
                    if(retfield == null)
                        retfield = DataFlowNode.baseField;

                    DataFlowNode dataFlowNodeback = DataFlowNodeFactory.v().createDataFlowNode
                            (exitStmt.stmt, retBase, retfield, true);
                    Pair<BaseInfoStmt, DataFlowNode> pathback = new Pair<BaseInfoStmt, DataFlowNode>(exitStmt, dataFlowNodeback);

                    seedbackward.put(new DFGEntryKey(exitStmt.stmt, retBase, retfield), pathback);

                    tmpbackward.put(pathback, dataFlowNodeback);
                }

            }

            for(Value base: paramAndThis) {

                if(!base.equals(this.base))
                    continue;
                SootField field = DataFlowNode.baseField;

//                SootField field = pair.getO2();
//
//                if(field == null)
//                    field = DataFlowNode.baseField;

                DataFlowNode dataFlowNodeback = DataFlowNodeFactory.v().createDataFlowNode
                        (exitStmt.stmt, base, field, true);
                Pair<BaseInfoStmt, DataFlowNode> pathback = new Pair<BaseInfoStmt, DataFlowNode>(exitStmt, dataFlowNodeback);

                seedbackward.put(new DFGEntryKey(exitStmt.stmt, base, field), pathback);

                tmpbackward.put(pathback, dataFlowNodeback);

            }
        }
        for(Pair<BaseInfoStmt, DataFlowNode> pair : tmpforward.keySet()) {
            Value a = pair.getO1().base;
            if(a != null && !a.equals(base))
                throw  new RuntimeException("base should equal or return stmt!");
        }
        for(Pair<BaseInfoStmt, DataFlowNode> pair : tmpbackward.keySet()) {
            Value a = pair.getO1().base;
            if(a != null && !a.equals(base))
                throw  new RuntimeException("base should equal or return stmt!");
        }

       // System.out.println("CC4");
        computeDataFlow(tmpforward, seed,true);
        computeDataFlow(tmpbackward, seedbackward, false);
       // System.out.println("CC5");
        return new Pair<Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>(seed, seedbackward);

    }

    public Map<DFGEntryKey, Set<DataFlowNode>> getReturnInfo() {
        return returnInfo;
    }


//    private void computeBackwardDataFlow( Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > seed) {
//
//        Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > visited = seed;
//        Queue<Pair<BaseInfoStmt, DataFlowNode>> worklist = new LinkedList<>();
//        worklist.addAll(visited.keySet());
//        while(!worklist.isEmpty()) {
//            Pair<BaseInfoStmt, DataFlowNode> curPath = worklist.poll();
//
//            BaseInfoStmt curBaseInfo = curPath.getO1();
//            DataFlowNode curNode = curPath.getO2();
//
//            if(curBaseInfo.Preds == null)
//                continue;
//            for(BaseInfoStmt pre : curBaseInfo.Preds) {
//                BackwardFunction backwardFunction = new BackwardFunction();
//                Set<DataFlowNode> ret = backwardFunction.flowFunction(pre, curNode, visited);
//
//                for(DataFlowNode dataFlowNode : ret) {
//                    worklist.offer(new Pair<BaseInfoStmt, DataFlowNode>(pre, dataFlowNode));
//                }
//            }
//
//        }
//    }

    private void computeDataFlow(Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > pathSet, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed, boolean isForward) {

        Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > visited = pathSet;
        Queue<Pair<BaseInfoStmt, DataFlowNode>> worklist = new LinkedList<>();
        worklist.addAll(visited.keySet());
        while(!worklist.isEmpty()) {
            Pair<BaseInfoStmt, DataFlowNode> curPath = worklist.poll();

            BaseInfoStmt curBaseInfo = curPath.getO1();
            DataFlowNode curNode = curPath.getO2();

            if((curBaseInfo.base != null && !curBaseInfo.base.equals(this.base)) || !curNode.getValue().equals(this.base))
                continue;

            Set<BaseInfoStmt> nexts = getNexts(curBaseInfo, isForward);

            if(nexts == null)
                continue;
            for(BaseInfoStmt next : nexts) {

                //因为return stmt的next 会有很多不同的base， 所以会产生bug
                if(next.base != null && !next.base.equals(base))
                    continue;
                if(next.base != null && next.base != base)
                    continue;

                AbstractFunction forwardFunction = FunctionFactory.getFunction(isForward, visited, paramAndThis, seed);
                Set<Pair<BaseInfoStmt, DataFlowNode>> ret = forwardFunction.flowFunction(next, curNode);
//                if(isForward) {
//                    this.returnInfo = forwardFunction.getReturnInfo();
//                }

                for(Pair<BaseInfoStmt, DataFlowNode> path : ret) {
                    worklist.offer(path);
                }
            }

        }
    }

    private Set<BaseInfoStmt> getNexts(BaseInfoStmt baseInfoStmt , boolean isForward) {
        if(isForward)
            return baseInfoStmt.Succs;
        else
            return baseInfoStmt.Preds;
    }

//
//
//    private  Set<DataFlowNode> backwardFlowFunction(BaseInfoStmt target, DataFlowNode source, Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
//        Set<DataFlowNode> res = new HashSet<>();
//
//        if (target.base == null) {
//            //return
//            DataFlowNode returnNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, null, false);
//            returnNode = getNewDataFlowNode(target, returnNode, visited);
//            source.setSuccs(DataFlowNode.baseField, returnNode);
//            //res.add(newNode);
//            return res;
//        }
//
//        SootField baseField = DataFlowNode.baseField;
//
//        SootField sourceField = source.getField();
//
//        SootField targetLeftField = target.leftField;
//        SootField[] targetRightFields = target.rightFields;
//        SootField[] targetArgFields = target.argsFields;
//
//        boolean isKillSource = false;
//        DataFlowNode newNode = null;
//
//        if (sourceField != baseField) {
//            //(1) source like  :  a.f1
//
//            if (targetLeftField != null) {
//                //(1.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
//                if (targetLeftField == baseField || sourceField.equals(targetLeftField)) {
//                    //(1.1.1) a = xxx;  source : a.f1  , kill source
//                    //(1.1.2) a.f1 = xxx;   source : a.f1 , kill source
//                    isKillSource = true;
//                } else {
//                    //(1.1.3) a.f2 = xx; source : a.f1  , do nothing.
//                }
//
//            }
//
//            if (targetRightFields != null) {
//                //(1.2) like : xxx = a; or xxx = a.f1; or xxx = a.f2;
//
//                for (int i = 0; i < targetRightFields.length; i++) {
//                    SootField right = targetRightFields[i];
//                    if (right == baseField) {
//                        //(1.2.1) xxx = a;  source : a.f1 , gen f1 -> <a>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                        res.add(newNode);
//
//                    } else if (right.equals(sourceField)) {
//                        //(1.2.2) xxx = a.f1; source : a.f1  , gen f1 -> <a.f1>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    } else {
//                        //(1.2.3) xxx= a.f2  source : a.f1, do nothing.
//
//                    }
//                }
//            }
//
//            if (targetArgFields != null) {
//                for (int i = 0; i < targetArgFields.length; i++) {
//                    SootField arg = targetArgFields[i];
//                    if (arg == baseField) {
//                        //(1.3.1) foo(a);    source : a.f1 , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//
//                    } else if (arg.equals(sourceField)) {
//                        //(1.3.2) foo(a.f1); source : a.f1  , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    } else {
//                        //(1.3.3) foo(a.f2); source : a.f1, do nothing.
//
//                    }
//                }
//            }
//
//        } else if (sourceField != null) {
//            //(2) source like  :  a
//
//            if (targetLeftField != null) {
//                //(2.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
//                if (targetLeftField == baseField) {
//                    // a = xxxx;   source : a , kill source
//                    isKillSource = true;
//                } else {
//                    // a.f1 = xx; source : a ,  just kill field f1.
//                    source.setKillField(targetLeftField);
//                }
//
//            }
//
//            if (targetRightFields != null) {
//                //like xxx = a;  or xxx = a.f1 ;
//                for (int i = 0; i < targetRightFields.length; i++) {
//                    SootField right = targetRightFields[i];
//                    if (right == baseField) {
//                        // xxx = a;    source : a , gen new a
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//
//                    } else {
//                        //(1) xxx = a.f1 ; source : a  , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    }
//                }
//            }
//
//            if (targetArgFields != null) {
//                for (int i = 0; i < targetArgFields.length; i++) {
//                    SootField arg = targetArgFields[i];
//                    if (arg == baseField) {
//                        // foo(a);    source : a , gen "base" -> <a>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(arg, newNode);
//
//                    } else if (arg.equals(sourceField)) {
//                        // foo(a.f1); source : a , gen f1 -> <a.f1>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(arg, newNode);
//                    }
//                }
//            }
//
//        } else {
//            throw new RuntimeException("source's base field can not be null ");
//        }
//
//        if (!isKillSource)
//            res.add(source);
//        return res;
//    }
//
////    private Set<DataFlowNode> callFlowFunction(Unit returnStmt, DataFlowNode source, ) {
////
////    }
//
////    private Set<DataFlowNode> returnFlowFunction(Unit returnStmt, DataFlowNode source, ) {
////
////
////    }
//
//
//    private  Set<DataFlowNode> flowFunction(BaseInfoStmt target, DataFlowNode source, Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
//        Set<DataFlowNode> res = new HashSet<>();
//
//        if(target.base == null) {
//            //return
//            DataFlowNode returnNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, null, false);
//            returnNode = getNewDataFlowNode(target, returnNode, visited);
//            source.setSuccs(DataFlowNode.baseField, returnNode);
//            //res.add(newNode);
//            return res;
//        }
//
//        SootField baseField = DataFlowNode.baseField;
//
//        SootField sourceField = source.getField();
//
//        SootField targetLeftField = target.leftField;
//        SootField[] targetRightFields = target.rightFields;
//        SootField[] targetArgFields = target.argsFields;
//
//        boolean isKillSource = false;
//        DataFlowNode newNode = null;
//
//        if(sourceField != baseField) {
//            //(1) source like  :  a.f1
//
//            if(targetLeftField != null) {
//                //(1.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
//                if(targetLeftField == baseField || sourceField.equals(targetLeftField)) {
//                    //(1.1.1) a = xxx;  source : a.f1  , kill source
//                    //(1.1.2) a.f1 = xxx;   source : a.f1 , kill source
//                    isKillSource = true;
//                }else {
//                    //(1.1.3) a.f2 = xx; source : a.f1  , do nothing.
//                }
//
//            }
//
//            if(targetRightFields != null) {
//                //(1.2) like : xxx = a; or xxx = a.f1; or xxx = a.f2;
//
//                for(int i = 0; i < targetRightFields.length; i++) {
//                    SootField right = targetRightFields[i];
//                    if(right == baseField ) {
//                        //(1.2.1) xxx = a;  source : a.f1 , gen f1 -> <a>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                        res.add(newNode);
//
//                    }else if (right.equals(sourceField)) {
//                        //(1.2.2) xxx = a.f1; source : a.f1  , gen f1 -> <a.f1>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    }else {
//                        //(1.2.3) xxx= a.f2  source : a.f1, do nothing.
//
//                    }
//                }
//            }
//
//            if(targetArgFields != null) {
//                for(int i = 0; i < targetArgFields.length; i++) {
//                    SootField arg = targetArgFields[i];
//                    if(arg == baseField ) {
//                        //(1.3.1) foo(a);    source : a.f1 , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//
//                    }else if (arg.equals(sourceField)) {
//                        //(1.3.2) foo(a.f1); source : a.f1  , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    }else {
//                        //(1.3.3) foo(a.f2); source : a.f1, do nothing.
//
//                    }
//                }
//            }
//
//        }else if(sourceField != null) {
//            //(2) source like  :  a
//
//            if(targetLeftField != null) {
//                //(2.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
//                if(targetLeftField == baseField ) {
//                    // a = xxxx;   source : a , kill source
//                    isKillSource = true;
//                }else {
//                    // a.f1 = xx; source : a ,  just kill field f1.
//                    source.setKillField(targetLeftField);
//                }
//
//            }
//
//            if(targetRightFields != null) {
//                //like xxx = a;  or xxx = a.f1 ;
//                for(int i = 0; i < targetRightFields.length; i++) {
//                    SootField right = targetRightFields[i];
//                    if(right == baseField ) {
//                        // xxx = a;    source : a , gen new a
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//
//                    }else {
//                        //(1) xxx = a.f1 ; source : a  , gen new a.f1
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(sourceField, newNode);
//                    }
//                }
//            }
//
//            if(targetArgFields != null) {
//                for(int i = 0; i < targetArgFields.length; i++) {
//                    SootField arg = targetArgFields[i];
//                    if(arg == baseField ) {
//                        // foo(a);    source : a , gen "base" -> <a>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(arg, newNode);
//
//                    }else if (arg.equals(sourceField)) {
//                        // foo(a.f1); source : a , gen f1 -> <a.f1>
//                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
//                        newNode = getNewDataFlowNode(target, newNode, visited);
//                        source.setSuccs(arg, newNode);
//                    }
//                }
//            }
//
//        }else {
//            throw new  RuntimeException("source's base field can not be null ");
//        }
//
//        if(!isKillSource)
//            res.add(source);
//        return res;
//
//
////
////
////        if(sourceField != null && targetField != null) {
////
////            if(sourceField.equals(targetField)) {
////                if(target.isLeft == false) {
////                    //a.f1   read
////                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
////                    if(visited.containsKey(newNode))
////                        newNode = visited.get(newNode);
////                    source.setSuccs(sourceField, newNode);
////
////                }else {
////                    isKillSource = true;
////                }
////
////            }else {
////                // a.f1  b.f1
////
////            }
////        }else if(sourceField != null) {
////            if(target.isLeft == false) {
////                // b = a ; < a.f > ;
////                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
////                if(visited.containsKey(newNode))
////                    newNode = visited.get(newNode);
////                source.setSuccs(sourceField, newNode);
////            }else {
////                // a = b ; < a.f > ;  kill : a.f
////                isKillSource = true;
////            }
////
////        }else if(targetField != null) {
////
////            if(target.isLeft == false) {
////                // b = a.f ; < a > ;
////                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
////                if(visited.containsKey(newNode))
////                    newNode = visited.get(newNode);
////                source.setSuccs(targetField, newNode);
////
////            }else {
////                // a.f = b ; < a > ;  kill : a.f
//////                isKillSource = true;
////                source.setKillField(targetField);
////            }
////
////        }else {
////            if(target.isLeft == false) {
////                // a = "xxx";  source : < a >
////                // b = a ; < a > ;    use : a
////                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
////                if(visited.containsKey(newNode))
////                    newNode = visited.get(newNode);
////                source.setSuccs(sourceField, newNode);
////            }else {
////
////                // a = "xxx";  source : < a >
////                //target:  a = b ;  < a > ;  kill : a
////                isKillSource = true;
////            }
////
////        }
//
//    }

    private void printBaseInfoStmtSet() {
        System.out.print("====================================================\n");
        System.out.print("<<" + m + ">>, BASE{ " + base +" }\n");
        System.out.print("----------------------------------------------------\n");
        System.out.print(subPrint());
        System.out.print("----------------------------------------------------\n");
    }
    private String subPrint()  {
        StringBuilder sb = new StringBuilder();
        Set<BaseInfoStmt> visited = new HashSet<>();
        Queue<BaseInfoStmt> list = new LinkedList<>();
        for(BaseInfoStmt baseInfoStmt : varInfoSets ) {
            list.offer(baseInfoStmt);
        }
        int count = 1 ;
        while (!list.isEmpty()) {
            BaseInfoStmt cur = list.poll();
            visited.add(cur);
            sb.append("  ("+count +") ");
            count++;
            sb.append(cur.toString() + "\n");
            if(cur.Succs != null) {
                sb.append("      Succs:\n");

                for(BaseInfoStmt next: cur.Succs) {
                    sb.append("        " + next.toString() + "\n");
                }
            }

            if(cur.Preds != null) {
                sb.append("      Preds:\n");

                for(BaseInfoStmt pre: cur.Preds) {
                    sb.append("        " + pre.toString() + "\n");
                }
            }

            sb.append("\n");

        }
        return sb.toString();
    }


    private DataFlowNode getNewDataFlowNode(BaseInfoStmt baseInfoStmt, DataFlowNode oldNode,
                                            Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
        Pair<BaseInfoStmt, DataFlowNode> key = new Pair<>(baseInfoStmt, oldNode);
        if(visited.containsKey(key))
            return visited.get(key);
        else
            return oldNode;

    }

}
