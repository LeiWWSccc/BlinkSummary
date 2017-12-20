package soot.jimple.infoflow.sparseOptimization.dataflowgraph.function;

import heros.solver.Pair;
import soot.Value;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;

import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public abstract class AbstractFunction {


    private Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > jumpFunc ;

    final private Set<Value> parmAndThis ;

    final protected Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed ;

    AbstractFunction(Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited,
                     Set<Value> parmAndThis,
                     Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed) {
        this.jumpFunc = visited;
        this.parmAndThis = parmAndThis;
        this.seed = seed;
    }

    public abstract Set<Pair<BaseInfoStmt, DataFlowNode>>  flowFunction(
            BaseInfoStmt target, DataFlowNode source);

    protected DataFlowNode getNewDataFlowNode(BaseInfoStmt baseInfoStmt, DataFlowNode oldNode) {
        Pair<BaseInfoStmt, DataFlowNode> key = new Pair<>(baseInfoStmt, oldNode);
        if(jumpFunc.containsKey(key))
            return jumpFunc.get(key);
        else
            return oldNode;

    }

    protected boolean canNodeReturn(Value base) {
        if(parmAndThis.contains(base))
            return true;
        if(base.equals(DataFlowNode.staticValue))
            return true;
        return false;
    }

    protected void addResult(Set<Pair<BaseInfoStmt, DataFlowNode>>  res, BaseInfoStmt target , DataFlowNode newDfn) {
        Pair<BaseInfoStmt, DataFlowNode> path = new Pair<BaseInfoStmt, DataFlowNode>(target, newDfn);
        addResult(res, path);

    }

    protected void addResult(Set<Pair<BaseInfoStmt, DataFlowNode>>  res, Pair<BaseInfoStmt, DataFlowNode> path) {
        if(this.jumpFunc.containsKey(path))
            return;
        if(path.getO1().base != null && !path.getO1().base.equals(path.getO2().getValue()))
            throw new RuntimeException("base not ok");

        jumpFunc.put(path, path.getO2());
        res.add(path);
    }

    public Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> getSeed() {
        return seed;
    }
}
