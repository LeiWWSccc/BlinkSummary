package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmtSet;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.utils.Utils;

import java.util.*;

/**
 * @author wanglei
 */
public class SummarySolver {

    Map<SummaryPath, SummaryPath> seedSet = new HashMap<>();

    Map<DFGEntryKey, MyAccessPath> summaryEntry = new HashMap<>();

    private SootMethod method;
    private IInfoflowCFG iCfg;
    private IInfoflowCFG backwardsICfg;


    private MyAccessPath createBackwardsMyAccessPathFormNode(DataFlowNode node) {

        SootField[] sootFields = null;
        if(node.getField() != DataFlowNode.baseField) {
            sootFields = new SootField[1];
            sootFields[0] = node.getField();
        }

        MyAccessPath myAp = new MyAccessPath((Local) node.getValue(), sootFields, Utils.unknownStmt, false );

        return myAp;
    }

    private MyAccessPath createMyAccessPathFormNode(DataFlowNode node) {

        SootField[] sootFields = null;
        if(node.getField() != DataFlowNode.baseField) {
            sootFields = new SootField[1];
            sootFields[0] = node.getField();
        }

        MyAccessPath myAp = new MyAccessPath((Local) node.getValue(), sootFields);

        return myAp;
    }

    private Set<SummaryPath> createSummaryPathFromNode(MyAccessPath source, DataFlowNode node) {
        Set<SummaryPath> ret = new HashSet<>();
        if(node.getSuccs() != null) {
            for(Set<DataFlowNode> tmpset : node.getSuccs().values()) {
                for(DataFlowNode next : tmpset) {
                    Unit nextStmt = next.getStmt();
                    MyAccessPath target = null;
                    if(next.getValue() == null) {
                        //return stmt
                        target = source.clone();
                    }else {
                        target = createMyAccessPathFormNode(next);
                    }
                    ret.add(new SummaryPath(node.getStmt(), source, nextStmt, target, next,true, null));
                }
            }
        }

        return ret;
    }


    private Set<SummaryPath> createBackwardsSummaryPathFromNode(MyAccessPath source, DataFlowNode node) {
        Set<SummaryPath> ret = new HashSet<>();
        if(node.getSuccs() != null) {
            for(Set<DataFlowNode> tmpset : node.getSuccs().values()) {
                for(DataFlowNode next : tmpset) {
                    Unit nextStmt = next.getStmt();
                    MyAccessPath target = null;
                    if(next.getValue() == null) {
                        //return stmt
                        target = source.clone();
                    }else {
                        target = createBackwardsMyAccessPathFormNode(next);
                    }
                    ret.add(new SummaryPath(node.getStmt(), source, nextStmt, target, next, false,null));
                }
            }
        }

        return ret;
    }

    public SummarySolver( Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> dfg ,
                              Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> backwardDfg, BaseInfoStmtSet baseInfoStmtSet,
                          SootMethod m, IInfoflowCFG cfg, IInfoflowCFG backwardsICfg) {
        this.iCfg = cfg;
        this.backwardsICfg = backwardsICfg;

        this.method = m;

        Set<DFGEntryKey> dfgEntryKeyForSummarySet = baseInfoStmtSet.getDfgEntryKeyForSummarySet();

        Set<DFGEntryKey> backwardDfgEntryKeyForSummarySet = baseInfoStmtSet.getBackwardDfgEntryKeyForSummarySet();

        for(DFGEntryKey key : dfgEntryKeyForSummarySet) {

            Pair<BaseInfoStmt, DataFlowNode> ret = dfg.get(key);
            DataFlowNode node = ret.getO2();
            MyAccessPath source = createMyAccessPathFormNode(node);
           // summaryEntry.put(key, source);
            Set<SummaryPath> paths = createSummaryPathFromNode(source, node);
            for(SummaryPath path : paths) {
                seedSet.put(path, path);
            }

        }

        for(DFGEntryKey backKey : backwardDfgEntryKeyForSummarySet) {

            Pair<BaseInfoStmt, DataFlowNode> ret = backwardDfg.get(backKey);
            DataFlowNode node = ret.getO2();
            MyAccessPath source = createBackwardsMyAccessPathFormNode(node);
           // summaryEntry.put(backKey, source);
            Set<SummaryPath> paths = createBackwardsSummaryPathFromNode(source, node);
            for(SummaryPath path : paths) {
                seedSet.put(path, path);
            }

        }



    }


    public Pair<SMForwardFunction, SMBackwardFunction> solve () {

        Queue<SummaryPath> worklist = new LinkedList<>();
        worklist.addAll(seedSet.keySet());
        SMForwardFunction forwardFunction = new SMForwardFunction(iCfg);
        SMBackwardFunction backwardFunction = new SMBackwardFunction(backwardsICfg);

        while(!worklist.isEmpty()) {
            SummaryPath source = worklist.poll();
            Set<SummaryPath> ret = null;
            if(source.isForward()) {
                ret = forwardFunction.compute(source);
            } else {
                ret = backwardFunction.compute(source);
            }

            if(ret != null && !ret.isEmpty())
                for(SummaryPath next : ret) {

                    if(next.isForward() && !next.getTargetAccessPath().isActive() ) {
                        if(next.getTargetAccessPath().getActiveStmt() != Utils.unknownStmt
                                && isActiveTaint(source.getTarget(), next.getTargetAccessPath().getActiveStmt(), next.getTarget())){
                            next = next.getInactiveCopy();

                        }else if(next.getTargetAccessPath().getActiveStmt() == Utils.unknownStmt
                                && isActiveTaint(source.getTarget(), next.getSrc(), next.getTarget())) {
                            next = next.getInactiveCopy();
                        }
                    }

                    if(seedSet.containsKey(next)) {
                        if(next.getKillSet() != null) {
                            SummaryPath origin = seedSet.get(next);
                            Set<SummaryPath> res = new HashSet<>();

                            SummaryPath remain = computeMergeKillPath(origin, next, res);
                            seedSet.put(remain, remain);

                            for(SummaryPath path : res) {
                                if(!seedSet.containsKey(path)) {
                                    seedSet.put(path, path);
                                    worklist.offer(path);
                                }
                            }
                        }
                        continue;
                    }
                    seedSet.put(next, next);
                    worklist.offer(next);
                }

        }

//        Map<Pair<Unit, Value>, SummaryGraph> forwardSummary = forwardFunction.getSummary();
//        Map<Pair<Unit, Value>, SummaryGraph> backwardsSummary = backwardFunction.getSummary();

        return new Pair<SMForwardFunction, SMBackwardFunction>(forwardFunction, backwardFunction);
    }

    private SummaryPath computeMergeKillPath(SummaryPath origin, SummaryPath next, Set<SummaryPath> res) {
        // S1 = A* - A1
        // S2 = A* - A2
        // S3 =  S1 U S2 = A* - (A1 ^ A2)
        // S4 = S3 - S1 =  A1 - (A1 ^ A2)
        Set<SootField> A1 = origin.getKillSet();
        Set<SootField> A2 = next.getKillSet();
        Set<SootField> S3 = new HashSet<>();
        Set<SootField> S4 = new HashSet<>();
        for(SootField f : A1) {
            if(A2.contains(f)) {
                S3.add(f);
            }else {
                S4.add(f);
            }
        }
        SummaryPath ret = origin.clone();
        if(S3.isEmpty()) {
            ret.setKillSet(null);
        }else {
            ret.setKillSet(S3);
        }
        for(SootField f : S4) {
            MyAccessPath newSourceAp = origin.getSourceAccessPath().deriveNewApAddfield(f);
            MyAccessPath newTargetAp = origin.getTargetAccessPath().deriveNewApAddfield(f);
            SummaryPath path = origin.deriveNewMyAccessPath(newSourceAp, origin.getTarget(), newTargetAp, origin.getTargetNode());
            path.setKillSet(null);
            res.add(path);
        }
        return ret;
    }



    private boolean isActiveTaint(Unit defStmt, Unit activeStmt, Unit curStmt) {

        BasicBlockGraph orderComputing = DataFlowGraphQuery.v().getMethodToBasicBlockGraphMap().get(this.method);
        if(orderComputing.computeOrder(defStmt, activeStmt) &&
                orderComputing.computeOrder(activeStmt, curStmt))
            return true;

        return false;
    }

    public static MyAccessPath getMyAccessPath(Value value, SootField[] remainFields) {

        Value base = null;
        SootField field = null;
        if(value instanceof BinopExpr) {
            throw new RuntimeException("getBaseAndValue method should't be BinopExpr!");
        }
        if(value instanceof StaticFieldRef) {
            base = DataFlowNode.staticValue;
            field = ((StaticFieldRef) value).getField();

        }else if(value instanceof Local) {

            // a   : base : a
            base = value;
        }else if(value instanceof FieldRef) {
            if(value instanceof InstanceFieldRef) {
                //a.f  : base : a  field : f

                //Value base = BaseSelector.selectBase(left, true);
                base = ((InstanceFieldRef) value).getBase();
                field = ((InstanceFieldRef)value).getField();
            }

        }else if (value instanceof ArrayRef) {
            ArrayRef ref = (ArrayRef) value;
            base = (Local) ref.getBase();
            Value rightIndex = ref.getIndex();
        }else if(value instanceof LengthExpr) {
            LengthExpr lengthExpr = (LengthExpr) value;
            base = lengthExpr.getOp();
        } else if (value instanceof NewArrayExpr) {
            NewArrayExpr newArrayExpr = (NewArrayExpr) value;
            base = newArrayExpr.getSize();
        }
        SootField[] newFields = null;
        if(field == null) {
          newFields = remainFields;
        }else if(remainFields == null) {
            newFields = new SootField[1];
            newFields[0] = field;
        }else {
            newFields = new SootField[remainFields.length + 1];
            newFields[0] = field;
            System.arraycopy(remainFields, 0, newFields, 1, remainFields.length);
        }
        return new MyAccessPath(base, newFields);

    }


    public static boolean baseMatches(final Value baseValue, MyAccessPath source) {
        if (baseValue instanceof Local) {
            if (baseValue.equals(source.getValue()))
                return true;
        }
        else if (baseValue instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) baseValue;
            if (ifr.getBase().equals(source.getValue())
                    && source.firstFieldMatches(ifr.getField()))
                return true;
        }
        else if (baseValue instanceof StaticFieldRef) {
            StaticFieldRef sfr = (StaticFieldRef) baseValue;
            if (source.firstFieldMatches(sfr.getField()))
                return true;
        }
        return false;
    }


    public static boolean canHaveAliases(Stmt stmt, Value val, MyAccessPath source) {
        if (stmt instanceof DefinitionStmt) {
            DefinitionStmt defStmt = (DefinitionStmt) stmt;
            // If the left side is overwritten completely, we do not need to
            // look for aliases. This also covers strings.
            if (defStmt.getLeftOp() instanceof Local
                    && defStmt.getLeftOp() == source.getValue())
                return false;

            // Arrays are heap objects
            if (val instanceof ArrayRef)
                return true;
            if (val instanceof FieldRef)
                return true;
        }

        // Primitive types or constants do not have aliases
        if (val.getType() instanceof PrimType)
            return false;
        if (val instanceof Constant)
            return false;

//        // String cannot have aliases
//        if (TypeUtils.isStringType(val.getType())
//                && !source.getAccessPath().getCanHaveImmutableAliases())
//            return false;

        return val instanceof FieldRef
                || (val instanceof Local && ((Local)val).getType() instanceof ArrayType);
    }

    public static boolean baseMatchesStrict(final Value baseValue, MyAccessPath source) {
        if (!baseMatches(baseValue, source))
            return false;

        if (baseValue instanceof Local)
            return source.isLocal();
        else if (baseValue instanceof InstanceFieldRef || baseValue instanceof StaticFieldRef)
            return source.getFieldCount() == 1;

        throw new RuntimeException("Unexpected left side");
    }


}
