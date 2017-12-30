package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.Pair;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class SummaryQuery {


    public static SummaryQuery instance = null;

    final private IInfoflowCFG iCfg;

    final private Map<SootMethod, BasicBlockGraph> methodToBasicBlockGraphMap ;

    private Map<Pair<Unit, Value>, SummaryGraph> forwardSummary ;
    private Map<Pair<Unit, Value>, SummaryGraph> backwardsSummary ;


    public static SummaryQuery v() {
        if(instance == null)
            throw new RuntimeException("SummaryQuery doesn't initialize!");
        return instance;}

    public SummaryQuery(IInfoflowCFG iCfg,
                        Map<Pair<Unit, Value>, SummaryGraph> forwardSummary,
                        Map<Pair<Unit, Value>, SummaryGraph> backwardsSummary,
                        Map<SootMethod, BasicBlockGraph> methodToBasicBlockGraphMap) {
        this.iCfg = iCfg;
        this.forwardSummary = forwardSummary;
        this.backwardsSummary = backwardsSummary;
        this.methodToBasicBlockGraphMap = methodToBasicBlockGraphMap;
    }


    public static void newInitialize(IInfoflowCFG iCfg,
                                     Map<Pair<Unit, Value>, SummaryGraph> forwardSummary,
                                     Map<Pair<Unit, Value>, SummaryGraph> backwardsSummary,
                                  Map<SootMethod, BasicBlockGraph> methodToBasicBlockGraphMap) {
        instance = new SummaryQuery(iCfg, forwardSummary, backwardsSummary, methodToBasicBlockGraphMap);

    }

//    public static void initialize(IInfoflowCFG iCfg,
//                                  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg,
//                                  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> backwardDfg,
//                                  Map<SootMethod, BasicBlockGraph> methodToBasicBlockGraphMap) {
//        instance = new DataFlowGraphQuery(iCfg, dfg, backwardDfg, methodToBasicBlockGraphMap);
//
//    }


    public Set<Abstraction> getForwardAbs(Value base, Unit stmt , AccessPath sourceAp) {
        return null;
    }

    public Set<Abstraction> getForwardAbs(Abstraction abs, Unit stmt) {
        Value base = abs.getAccessPath().getPlainValue();
        SummaryGraph graph = forwardSummary.get(new Pair<Unit, Value>(stmt, base));
        if(graph!= null)
            return graph.getForwardAbs(abs);
        return Collections.emptySet();
    }

    public void propagateAbsUsingSummaries(Collection<Abstraction> d0s, IInfoflowSolver forwardSolver, IInfoflowSolver backwardsSolver,
                                           Abstraction target, Unit src) {
        Value base = target.getAccessPath().getPlainValue();
        SummaryGraph graph = forwardSummary.get(new Pair<Unit, Value>(src, base));
        if(graph!= null)
             graph.propagateAbsUsingSummaries(d0s, forwardSolver, backwardsSolver, target);

    }

    public void propagateBackwardsAbsUsingSummaries(Collection<Abstraction> d0s, IInfoflowSolver forwardSolver, IInfoflowSolver backwardsSolver,
                                           Abstraction target, Unit src) {
        Value base = target.getAccessPath().getPlainValue();
        SummaryGraph graph = forwardSummary.get(new Pair<Unit, Value>(src, base));
        if(graph!= null)
            graph.propagateAbsUsingSummaries(d0s, forwardSolver, backwardsSolver, target);

    }

}
