package soot.jimple.infoflow.sparseOptimization.dataflowgraph;

import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlock;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.Pair;

import java.util.*;

/**
 * @author wanglei
 */
public class BaseInfoStmtCFG implements DirectedGraph {
    private HashMap<BasicBlock, Set<BaseInfoStmt>> bbToBaseInfoMap = null;

    public BaseInfoStmtCFG(HashMap<BasicBlock, Set<BaseInfoStmt>> bbToBaseInfoMap) {
        this.bbToBaseInfoMap = bbToBaseInfoMap;
    }

//    public void solve1(List<BasicBlock> heads) {
//        Set<BasicBlock> visited = new HashSet<>();
//        for(BasicBlock head : heads) {
//            dfs(head, null, visited);
//        }
//    }
//    private void dfs(BasicBlock bb, BaseInfoStmt pre, Set<BasicBlock> visited) {
//        if(visited.contains(bb))
//            return ;
//        visited.add(bb);
//        Pair<BaseInfoStmt, BaseInfoStmt> ret = innerBasicBlock(bb);
//        BaseInfoStmt tail = null;
//        if(ret == null) {
//            tail = pre;
//        }else {
//            tail = ret.getO2();
//        }
//
//        if(pre != null && ret != null) {
//            if(pre.Succs == null)
//                pre.Succs = new HashSet<>();
//            pre.Succs.add(ret.getO1());
//
//            if(ret.getO1().Preds == null)
//                ret.getO1().Preds = new HashSet<>();
//            ret.getO1().Preds.add(pre);
//
//        }
//
//        for(BasicBlock succ : bb.getSuccs()) {
//            dfs(succ, tail, visited);
//        }
//
//    }

    public void solve() {
        Map<BasicBlock, Pair<BaseInfoStmt, BaseInfoStmt>> result = new HashMap<>();
        for(BasicBlock bb : bbToBaseInfoMap.keySet()) {
            solverBB(bb, result);
        }
    }
    private void solverBB(BasicBlock bb, Map<BasicBlock, Pair<BaseInfoStmt, BaseInfoStmt>> result) {
        Pair<BaseInfoStmt, BaseInfoStmt> ret = null;
        if(result.containsKey(bb)) {
            ret = result.get(bb);
        }else {
            ret = innerBasicBlock(bb);
            if(ret != null)
                result.put(bb, ret);
        }

        BaseInfoStmt tail = ret.getO2();
        Set<BasicBlock> visited = new HashSet<>();
        for(BasicBlock succ : bb.getSuccs()) {
            subSolverBB(succ, tail, result, visited);
        }

    }
    private void subSolverBB(BasicBlock bb, BaseInfoStmt preTail, Map<BasicBlock,
            Pair<BaseInfoStmt, BaseInfoStmt>> result, Set<BasicBlock> visited ) {
        if(visited.contains(bb))
            return ;
        visited.add(bb);

        Pair<BaseInfoStmt, BaseInfoStmt> ret = null;
        if(result.containsKey(bb)) {
            ret = result.get(bb);
        }else {
            ret = innerBasicBlock(bb);
            if(ret != null)
                result.put(bb, ret);
        }
        if(ret != null ) {
            if(preTail.Succs == null)
                preTail.Succs = new HashSet<>();
            preTail.Succs.add(ret.getO1());

            if(ret.getO1().Preds == null)
                ret.getO1().Preds = new HashSet<>();
            ret.getO1().Preds.add(preTail);

            return;
        }

        BaseInfoStmt tail = null;
        if(ret == null)
            tail = preTail;
        else
            tail = ret.getO2();
        for(BasicBlock succ : bb.getSuccs()) {
            subSolverBB(succ, tail, result, visited);
        }

    }

    private Pair<BaseInfoStmt, BaseInfoStmt> innerBasicBlock(BasicBlock bb) {
        if(!bbToBaseInfoMap.containsKey(bb))
            return null;

        List<BaseInfoStmt> col = new ArrayList<>(bbToBaseInfoMap.get(bb));
        Collections.sort(col, new Comparator<BaseInfoStmt>() {
            public int compare(BaseInfoStmt arg0, BaseInfoStmt arg1) {
                return arg0.idx - arg1.idx;
            }
        });
        BaseInfoStmt head = null;
        BaseInfoStmt tail = null;
        BaseInfoStmt pre = null;
        for(int i = 0; i < col.size(); i++) {
            BaseInfoStmt cur = col.get(i);
            if(i == 0) {
                head = cur;
            }
            if(i == col.size() - 1) {
                tail = cur;
            }
            if(pre != null) {
                if(pre.Succs == null)
                    pre.Succs = new HashSet<>();
                if(cur.Preds == null)
                    cur.Preds = new HashSet<>();
                pre.Succs.add(col.get(i));
                cur.Preds.add(pre);

            }
            pre = col.get(i);

        }
//        if(bb.getSuccs().size() == 0)
//            tail.returnStmt = bb.getTail();

        return new Pair<>(head, tail);
    }

    @Override
    public List getHeads() {
        return null;
    }

    @Override
    public List getTails() {
        return null;
    }

    @Override
    public List getPredsOf(Object s) {
        return null;
    }

    @Override
    public List getSuccsOf(Object s) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Iterator iterator() {
        return null;
    }
}
