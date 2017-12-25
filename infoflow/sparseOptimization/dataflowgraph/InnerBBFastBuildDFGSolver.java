package soot.jimple.infoflow.sparseOptimization.dataflowgraph;

import heros.solver.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlock;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.summary.SummarySolver;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.ReachableMethods;

import java.util.*;

/**
 * @author wanglei
 */
public class InnerBBFastBuildDFGSolver {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private IInfoflowCFG iCfg;
    protected InfoflowConfiguration config = new InfoflowConfiguration();

    private Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg = new HashMap<>();
    private Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> newDfg = new HashMap<>();

    private Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> newBackwardDfg = new HashMap<>();
    private Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> backwardDfg = new HashMap<>();

    private Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> returnInfo = new HashMap<>();

    private Map<SootMethod, BasicBlockGraph> unitOrderComputingMap = new HashMap<>();

    private Map<SootMethod, Map<Value, BaseInfoStmtSet> > BaseInfoSetGroupMap = new HashMap();

    public Map<SootMethod, BasicBlockGraph> getUnitOrderComputingMap() {
        return unitOrderComputingMap;
    }

    public InnerBBFastBuildDFGSolver(IInfoflowCFG iCfg ) {
        this.iCfg = iCfg;
    }

    public Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> getNewDfg() {
        return newDfg;
    }

    public Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> getNewBackwardDfg() {
        return newBackwardDfg;
    }

    public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getDfg() {
        return dfg;
    }

    public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getBackwardDfg() {
        return backwardDfg;
    }

    public  Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> getReturnInfo() {
        return returnInfo;
    }

    //final public static String[] debugFunc = {"main","funcA","funcB","funcC"};
    //final public static String[] debugFunc = {"<com.clixap.sdk.RequestService: void onHandleIntent(android.content.Intent)>"};
    //final public static String[] debugFunc = {"<com.appbrain.a.t: void run()>"};
    final public static String[] debugFunc = {"<com.appbrain.a.w: void a()>"};
    //final public static String[] debugFunc = {"<com.appbrain.a.v: void a(com.appbrain.a.v)>"};
    //final public static String[] debugFunc = {"<com.wEditingHDVideo.ads.AdsLoader: void init(java.lang.String,com.wEditingHDVideo.MainNavigationActivity)>"};
    //final public static String[] debugFunc = {"<com.wEditingHDVideo.ads.AdsLoader$5: void run()>"};
    //final public static String[] debugFunc = {"<com.wEditingHDVideo.Server.AppsGeyserServerClient: java.lang.String GetBannerUrl()>"};
    //final public static String[] debugFunc = {"<com.wEditingHDVideo.ads.AdsLoader: void reload()>"};

    public void solve() {
        for (SootMethod sm : getMethodsForSeeds(iCfg))
            buildDFGForEachSootMethod(sm);
    }

    public void solveSummary() {

        for(Map.Entry<SootMethod, Map<Value, BaseInfoStmtSet>> e : BaseInfoSetGroupMap.entrySet()) {
            SootMethod m = e.getKey();
            if(m.toString().contains("main")) {
                Map<Value, BaseInfoStmtSet> group = e.getValue();
                for(Map.Entry<Value, BaseInfoStmtSet> entry : group.entrySet()) {
                    Value base = entry.getKey();
                    BaseInfoStmtSet baseInfoStmtSet = entry.getValue();

                    SummarySolver summarySolver = new SummarySolver(newDfg, newBackwardDfg,  baseInfoStmtSet, m, iCfg);
                    summarySolver.solve();

                }
            }

        }


    }



    public  Map<BasicBlock, Set<BasicBlock>> computeBBOrder(BasicBlockGraph bbg) {

        Map<BasicBlock, Set<BasicBlock>> bbToReachableBbMap = new HashMap<>();
        List<BasicBlock> bbList =  bbg.getBlocks();
        for(BasicBlock bb : bbList) {
            Set<BasicBlock> reached = new HashSet<>();
            Queue<BasicBlock> worklist = new LinkedList<>();
            worklist.add(bb);
            while(!worklist.isEmpty()) {
                BasicBlock cur = worklist.poll();
                if(reached.contains(cur))
                    continue;
                reached.add(cur);
                for(BasicBlock next : cur.getSuccs()) {
                    worklist.offer(next);
                }
            }

            bbToReachableBbMap.put(bb, reached);
        }
        return bbToReachableBbMap;

    }

    private void buildDFGForEachSootMethod(SootMethod m) {

        if (m.hasActiveBody()) {
            // Check whether this is a system class we need to ignore
            final String className = m.getDeclaringClass().getName();
            if (config.getIgnoreFlowsInSystemPackages()
                    && SystemClassHandler.isClassInSystemPackage(className))
                return ;

            //debug 遍历需要调试的名字，匹配则打印、或断点
            for(String func :debugFunc) {
                if(m.toString().contains(func))
                    System.out.println(m.getActiveBody());
            }

            // first we build the basic block of the method, which is used for opt
            // compute each index of Unit in their basic block
            // use unitToBBMap, unitToInnerBBIndexMap to store the Map (unit -> BB , unit -> index )
            final BasicBlockGraph bbg = new BasicBlockGraph(iCfg, m) ;
            // 计算一个方法内部两个语句的"序关系"
            // 预先已经计算好了各个基本快之间的可达性
            // 基本快内部通过index大小比较
            unitOrderComputingMap.put(m, bbg);

            final Map<Value, BaseInfoStmtSet> baseInfoStmtMapGbyBase = bbg.computeBaseInfo();

            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseToDfg = new HashMap<>();

            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> backwardBaseToDfg = new HashMap<>();

            Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>> returnInfoMap = new HashMap<>();

            for(Map.Entry<Value, BaseInfoStmtSet> entry : baseInfoStmtMapGbyBase.entrySet()) {
                Value base = entry.getKey();
                BaseInfoStmtSet baseInfoStmtSet = entry.getValue();
                Pair<Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> resPair = baseInfoStmtSet.solve();
                baseToDfg.put(base, resPair.getO1());
                backwardBaseToDfg.put(base, resPair.getO2());
                newDfg.putAll(resPair.getO1());
                newBackwardDfg.putAll(resPair.getO2());
                returnInfoMap.put(base, baseInfoStmtSet.getReturnInfo());
            }

            BaseInfoSetGroupMap.put(m, baseInfoStmtMapGbyBase);

            //printer(m, baseToDfg, backwardBaseToDfg);

            //dfg.put(m, baseToDfg);
            //backwardDfg.put(m, backwardBaseToDfg);
            //returnInfo.put(m, returnInfoMap);

        }
        return ;
    }


    private Collection<SootMethod> getMethodsForSeeds(IInfoflowCFG icfg) {
        List<SootMethod> seeds = new LinkedList<SootMethod>();
        // If we have a callgraph, we retrieve the reachable methods. Otherwise,
        // we have no choice but take all application methods as an approximation
        if (Scene.v().hasCallGraph()) {
            List<MethodOrMethodContext> eps = new ArrayList<MethodOrMethodContext>(Scene.v().getEntryPoints());
            ReachableMethods reachableMethods = new ReachableMethods(Scene.v().getCallGraph(), eps.iterator(), null);
            reachableMethods.update();
            for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext();)
                seeds.add(iter.next().method());
        }
        else {
            long beforeSeedMethods = System.nanoTime();
            Set<SootMethod> doneSet = new HashSet<SootMethod>();
            for (SootMethod sm : Scene.v().getEntryPoints())
                getMethodsForSeedsIncremental(sm, doneSet, seeds, icfg);
            logger.info("Collecting seed methods took {} seconds", (System.nanoTime() - beforeSeedMethods) / 1E9);
        }
        return seeds;
    }


    private void getMethodsForSeedsIncremental(SootMethod sm,
                                               Set<SootMethod> doneSet, List<SootMethod> seeds, IInfoflowCFG icfg) {
        assert Scene.v().hasFastHierarchy();
        if (!sm.isConcrete() || !sm.getDeclaringClass().isApplicationClass() || !doneSet.add(sm))
            return;
        seeds.add(sm);
        for (Unit u : sm.retrieveActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            if (stmt.containsInvokeExpr())
                for (SootMethod callee : icfg.getCalleesOfCallAt(stmt))
                    getMethodsForSeedsIncremental(callee, doneSet, seeds, icfg);
        }
    }


    public void printer(SootMethod m, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseToVarInfoMap , Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> backBaseToVarInfoMap) {
        boolean found = false;
        for(String func : debugFunc) {
            if(m.toString().contains(func))
                found = true;
        }
        if(!found)
            return;

        System.out.println("================Forward  ==========================");
        System.out.print(printInnerMethodBaseInfo(baseToVarInfoMap));
        System.out.println("================Backward ==========================");
        System.out.print(printInnerMethodBaseInfo(backBaseToVarInfoMap));
        System.out.println("===================================================");

    }

    public String printDfg() {
        if(dfg.size() == 0)
            return "Error!";
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> entry : dfg.entrySet()) {
            SootMethod method = entry.getKey();
            sb.append("=============================================================\n");
            sb.append(method.toString() + "\n");
            sb.append("-------------------------------------------------------------\n");
            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1Dfg = entry.getValue();
            for(Map.Entry<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> entry1 : l1Dfg.entrySet()) {
                Value base = entry1.getKey();
                sb.append("BASE[ " + base.toString() + " ]: \n");
                Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg = entry1.getValue();
                sb.append(subprint(l2Dfg));
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public String printInnerMethodBaseInfo(Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseinfo) {

        StringBuilder sb = new StringBuilder();
        sb.append("-------------------------------------------------------------\n");
        for(Map.Entry<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> entry1 : baseinfo.entrySet()) {
            Value base = entry1.getKey();
            sb.append("BASE[ " + base.toString() + " ]: \n");
            Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg = entry1.getValue();
            sb.append(subprint(l2Dfg));
            sb.append("\n");
        }
        sb.append("-------------------------------------------------------------\n");
        return sb.toString();
    }

    private String subprint(Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg) {
        StringBuilder sb = new StringBuilder();
        Set<DataFlowNode> visited = new HashSet<>();
        Queue<DataFlowNode> list = new LinkedList<>();
        for(Pair<BaseInfoStmt, DataFlowNode> pair : l2Dfg.values() ) {
            list.offer(pair.getO2());
            visited.add(pair.getO2());
        }
        int count = 1 ;
        while (!list.isEmpty()) {
            DataFlowNode cur = list.poll();
            sb.append("  ("+count +") ");
            count++;
            sb.append(cur.toString() + "\n");
            if(cur.getSuccs() != null) {
                for(Map.Entry<SootField, Set<DataFlowNode>> entry : cur.getSuccs().entrySet()) {
                    SootField f = entry.getKey();
                    String fs ;
                    if(f == DataFlowNode.baseField)
                        fs = "NULL";
                    else
                        fs = f.toString();

                    sb.append("      " + fs + "  ->  \n");
                    Set<DataFlowNode> nextSet = entry.getValue();
                    for(DataFlowNode next : nextSet) {
                        sb.append("         " + next + "\n");
                        if(!visited.contains(next)) {
                            list.offer(next);
                            visited.add(next);
                        }
                    }
                    sb.append("\n");

                }
            }
//            if(cur.getKillFields() != null) {
//                sb.append("      Kill Sets:\n");
//                sb.append("        " + cur.getKillFields().toString() + "\n");
//            }
//
//            sb.append("\n");

        }
        return sb.toString();
    }
}
