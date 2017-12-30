package soot.jimple.infoflow.sparseOptimization.summary;

import heros.solver.PathEdge;
import soot.SootField;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.InnerBBFastBuildDFGSolver;

import java.util.*;

/**
 * @author wanglei
 */
public class SummaryGraph {

    private class GraphNode {

        private Set<SummaryPath> summaries;
        private Set<SummaryPath> spSummaries;
        private Set<SummaryPath> killSummaries;

        private Map<SootField, GraphNode> childrens;

        GraphNode(SummaryPath summary, boolean isSp) {
            if(isSp) {
                spSummaries = new HashSet<>();
                spSummaries.add(summary);
            }else {
                summaries = new HashSet<>();
                summaries.add(summary);
            }
        }

        GraphNode() {

        }

        public void setChildrens(Map<SootField, GraphNode> childrens) {
            this.childrens = childrens;
        }

        public void addChildren(SootField field, GraphNode graphNode) {
            if(this.childrens == null)
                childrens = new HashMap<>();
         childrens.put(field, graphNode);
        }
    }

    private GraphNode root ;


    SummaryGraph(SummaryPath path) {
        MyAccessPath source = path.getSourceAccessPath();
        boolean isSp = path.getSourceAccessPath().isStrongUpdateSource();
        if(isKillPath(path)) {
            root = new GraphNode();
            initKillPathGraph(-1, root, source.getFields(), path);
        }else if(isSpPath(path)) {
            root = new GraphNode();
            mergeChildrenSummarySp(-1, root, source.getFields(), path);
        } else {

            root = new GraphNode();
            mergeChildrenSummary(-1, root, source.getFields(), path);

//            if(source.getFields() == null) {
//                root = new GraphNode(path, isSp);
//            }else {
//                root = new GraphNode();
//                addChildrenSummary(0, root, source.getFields(), path, isSp);
//            }


        }


    }

    private void mergeChildrenSummarySp( int index, GraphNode root, SootField[] fields,
                                       SummaryPath summaryAp) {
        int remainLen = (fields == null ? 0 : fields.length - index - 1);

        if(remainLen == 0) {
            if(root.spSummaries == null)
                root.spSummaries = new HashSet<>(4);
            root.spSummaries.add(summaryAp);
            return;
        }

        index++;
        SootField f = fields[index];
        if(f != null) {
            GraphNode next = null;
            if(root.childrens == null) {
                next = new GraphNode();
            }else {
                next = root.childrens.get(f);
            }
            mergeChildrenSummarySp(index, next, fields, summaryAp);
        }
        return ;
    }


    private void initKillPathGraph( int index, GraphNode root, SootField[] fields,
                                       SummaryPath summaryAp) {
        int remainLen = (fields == null ? 0 : fields.length - index - 1);

        if(remainLen == 0) {
            if(root.killSummaries == null) {
                root.killSummaries = new HashSet<>(4);
                root.killSummaries.add(summaryAp);
                addKillChildren(root, summaryAp.getKillSet());
            }else {
                //TODO
            }
            return;
        }

        index++;
        SootField f = fields[index];
        if(f != null) {
            GraphNode next = null;
            if(root.childrens == null) {
                next = new GraphNode();
            }else {
                next = root.childrens.get(f);
            }
            initKillPathGraph(index, next, fields, summaryAp);
        }
        return ;
    }
    private void addKillChildren(GraphNode root , Set<SootField> killSet) {

        if(root.childrens == null)
            root.childrens = new HashMap<>();

        for(SootField f : killSet) {
            if(root.childrens.containsKey(f)) {
                GraphNode node  = root.childrens.get(f);
                if( node.killSummaries != null)
                    node.killSummaries = new HashSet<>(4);
            }else {
                GraphNode node = new GraphNode();
                node.killSummaries = new HashSet<>(4);
                root.childrens.put(f, node);
            }
        }

    }


    public void merge(SummaryPath path) {
        MyAccessPath source = path.getTargetAccessPath();
        if(isSpPath(path)) {
            mergeChildrenSummarySp(-1, root, source.getFields(), path);
        }else if (isKillPath(path)) {
            initKillPathGraph(-1, root, source.getFields(), path);
        }else {
            mergeChildrenSummary(-1, root, source.getFields(), path);
        }
    }

    private void addChildrenSummary( int index, GraphNode root, SootField[] fields,
                                     SummaryPath summaryAp, boolean isSp) {
        SootField targetField = fields[index];
        GraphNode fieldNode = null;
        if(index == fields.length - 1) {
             fieldNode = new GraphNode(summaryAp, isSp);

        }else {
             fieldNode = new GraphNode();
             addChildrenSummary(index+1, fieldNode, fields, summaryAp, isSp);
        }
        root.addChildren(targetField, fieldNode);
    }

    private boolean isSpPath(SummaryPath path) {
        return  path.getSourceAccessPath().isStrongUpdateSource();
    }

    private  boolean isKillPath(SummaryPath path) {
        return path.getKillSet() != null;
    }

    private void mergeChildrenSummary( int index, GraphNode root, SootField[] fields,
                                     SummaryPath summaryAp) {
        int remainLen = (fields == null ? 0 : fields.length - index - 1);

        if(remainLen == 0) {
            if(root.summaries == null)
                root.summaries = new HashSet<>(4);
            root.summaries.add(summaryAp);
            return;
        }

        index++;
        SootField f = fields[index];
        if(f != null) {
            GraphNode next = null;
            if(root.childrens == null) {
                next = new GraphNode();
            }else {
                next = root.childrens.get(f);
            }
            mergeChildrenSummary(index, next, fields, summaryAp);
        }
        return ;
    }

//    private void mergeChildrenSummarySp( int index, GraphNode root, SootField[] fields,
//                                       SummaryPath summaryAp) {
//        SootField targetField = fields[index];
//        GraphNode fieldNode = null;
//        int remainLen = fields.length - index + 1;
//
//        if(remainLen == 0) {
//            if(isSpPath(summaryAp)) {
//                root.spSummaries.add(summaryAp);
//            }else if {
//
//            }else {
//                root.summaries.add(summaryAp);
//            }
//
//
//        }else {
//
//        }
//
//        if(index == fields.length - 1) {
//            fieldNode = new GraphNode(summaryAp, isSp);
//
//        }else {
//            fieldNode = new GraphNode();
//            addChildrenSummary(index+1, fieldNode, fields, summaryAp, isSp);
//        }
//        root.addChildren(targetField, fieldNode);
//    }
//
//    private void mergeChildrenSummaryKill( int index, GraphNode root, SootField[] fields,
//                                       SummaryPath summaryAp) {
//        SootField targetField = fields[index];
//        GraphNode fieldNode = null;
//        int remainLen = fields.length - index + 1;
//
//        if(remainLen == 0) {
//            if(isSpPath(summaryAp)) {
//                root.spSummaries.add(summaryAp);
//            }else if {
//
//            }else {
//                root.summaries.add(summaryAp);
//            }
//
//
//        }else {
//
//        }
//
//        if(index == fields.length - 1) {
//            fieldNode = new GraphNode(summaryAp, isSp);
//
//        }else {
//            fieldNode = new GraphNode();
//            addChildrenSummary(index+1, fieldNode, fields, summaryAp, isSp);
//        }
//        root.addChildren(targetField, fieldNode);
//    }
//







    public void merge(Value base, SootField[] sootFields, MyAccessPath target) {


    }






    public void propagate(Collection<Abstraction> d0s, Unit targetSmt, Abstraction target, IInfoflowSolver solver, boolean isForward) {
        for(Abstraction d0 : d0s) {
            PathEdge<Unit, Abstraction> pathEdge = new PathEdge<>(d0, targetSmt, target);
            solver.processEdge(pathEdge, null);
        }

    }


    public void propagateAbsUsingSummaries(Collection<Abstraction> d0s, IInfoflowSolver forwardSolver, IInfoflowSolver backwardsSolver, Abstraction source) {
        AccessPath ap = source.getAccessPath();
        SootField[] fields = ap.getFields();
        recurPropagateAbs(d0s, forwardSolver, backwardsSolver, source, fields, -1, root);
    }


    private void recurPropagateAbs(Collection<Abstraction> d0s, IInfoflowSolver forwardSolver, IInfoflowSolver backwardsSolver, Abstraction source, SootField[] fields, int index, GraphNode node) {
        int len = (fields == null ? 0 : fields.length);
        if(node.summaries != null) {
            for(SummaryPath path : node.summaries ) {
                AccessPath sourceAp = source.getAccessPath();
                Unit activeStmt = path.getTargetAccessPath().getActiveStmt();
                AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                if(activeStmt != null)
                    newAbs.setActivationUnit(activeStmt);

                propagate(d0s, path.getTarget(), newAbs,  path.isForward() ? forwardSolver : backwardsSolver, path.isForward());
            }
        }
        if(node.spSummaries != null) {
            if(index < len - 1) {
                for(SummaryPath path : node.spSummaries) {
                    AccessPath sourceAp = source.getAccessPath();
                    Unit activeStmt = path.getTargetAccessPath().getActiveStmt();
                    AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                    Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                    if(activeStmt != null)
                        newAbs.setActivationUnit(activeStmt);
                    propagate(d0s, path.getTarget(), newAbs,  path.isForward() ? forwardSolver : backwardsSolver, path.isForward());

                }
            }
        }
        index++;
        SootField f = ((len > 0 && index < len) ? fields[index] : null);
        boolean isKilled = false;
        if(f != null) {
            GraphNode next = node.childrens.get(f);
            if(next == null || next.killSummaries == null)
                isKilled = true;
            recurPropagateAbs(d0s, forwardSolver, backwardsSolver, source, fields, index, next);
        }else {
            isKilled = true;
        }
        if(isKilled && node.killSummaries != null) {
            if(index < len ) {
                for(SummaryPath path : node.killSummaries) {
                    AccessPath sourceAp = source.getAccessPath();
                    Unit activeStmt = path.getTargetAccessPath().getActiveStmt();
                    AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                    Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                    if(activeStmt != null)
                        newAbs.setActivationUnit(activeStmt);
                    propagate(d0s, path.getTarget(), newAbs,  path.isForward() ? forwardSolver : backwardsSolver, path.isForward());

                }
            }
        }


    }





    public Set<Abstraction> getForwardAbs(Abstraction source ) {
        AccessPath ap = source.getAccessPath();
        SootField[] fields = ap.getFields();
        Set<Abstraction> res = new HashSet<>();
        return recurGetAbs(source, fields, -1, root, res);
    }

//    public Set<Abstraction> getForwardAbs(AccessPath ap ) {
//        SootField[] fields = ap.getFields();
//        Set<Abstraction> res = new HashSet<>();
//        return recurGetAccessPath(ap, fields, -1, root, res);
//
//    }

//    private Set<Abstraction> recurGetAccessPath(Abstraction source, SootField[] fields, int index, GraphNode node, Set<Abstraction> res) {
//        int len = (fields == null ? 0 : fields.length);
//        if(node.summaries != null) {
//            for(SummaryPath path : node.summaries ) {
//                AccessPath sourceAp = source.getAccessPath();
//                AccessPath newAp = sourceAp.clone(); // TODO
//                Abstraction newAbs = source.deriveNewAbstraction(newAp, null);
//                res.add(newAbs);
//            }
//        }
//        if(node.spSummaries != null) {
//            if(index < len - 1) {
//                AccessPath sourceAp = source.getAccessPath();
//                AccessPath newAp = sourceAp.clone(); // TODO
//                Abstraction newAbs = source.deriveNewAbstraction(newAp, null);
//                res.add(newAbs);
//            }
//        }
//        SootField f = (index < len ? fields[index] : null);
//        boolean isKilled = false;
//        if(f != null) {
//            GraphNode next = node.childrens.get(f);
//            if(next == null || next.killSummaries == null)
//                isKilled = true;
//
//            recur(source, fields, index + 1 , next, res);
//        }
//        if(isKilled && node.killSummaries != null) {
//            if(index < len - 1) {
//                AccessPath sourceAp = source.getAccessPath();
//                AccessPath newAp = sourceAp.clone(); // TODO
//                Abstraction newAbs = source.deriveNewAbstraction(newAp, null);
//                res.add(newAbs);
//            }
//        }
//
//        return res;
//
//    }

//    private Set<Abstraction> getAbs(Abstraction source, SootField[] fields, int index, GraphNode node, Set<Abstraction> res) {
//        if(node.summaries != null) {
//            for(SummaryPath path : node.summaries ) {
//                AccessPath sourceAp = source.getAccessPath();
//                AccessPath newAp = sourceAp.clone(); // TODO
//                Abstraction newAbs = source.deriveNewAbstraction(newAp, null);
//                res.add(newAbs);
//            }
//        }
//        if(node.spSummaries != null) {
//            if(index < len - 1) {
//                AccessPath sourceAp = source.getAccessPath();
//                AccessPath newAp = sourceAp.clone(); // TODO
//                Abstraction newAbs = source.deriveNewAbstraction(newAp, null);
//                res.add(newAbs);
//            }
//        }
//        if(fields == null) {
//            return res;
//        }else {
//            return recurGetAbs(source, fields, 0, )
//        }
//    }


    public AccessPath convertToAccessPath(MyAccessPath myAccessPath, boolean taintSubFields, int index, SootField[] appendFields) {
        SootField[] newFields = myAccessPath.getFields();
        if(appendFields == null) {

        }else {
            newFields = new SootField[myAccessPath.getFieldCount() + appendFields.length - index];
            System.arraycopy(myAccessPath.getFields(), 0,newFields,0, myAccessPath.getFieldCount());
            System.arraycopy(appendFields, index,newFields,myAccessPath.getFieldCount(),appendFields.length - index);

        }

        return InnerBBFastBuildDFGSolver.pathFactory.createAccessPath(myAccessPath.getValue(), newFields, taintSubFields);
    }


    private Set<Abstraction> recurGetAbs(Abstraction source, SootField[] fields, int index, GraphNode node, Set<Abstraction> res) {
        int len = (fields == null ? 0 : fields.length);
        if(node.summaries != null) {
            for(SummaryPath path : node.summaries ) {
                AccessPath sourceAp = source.getAccessPath();

                AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                res.add(newAbs);
            }
        }
        if(node.spSummaries != null) {
            if(index < len - 1) {
                for(SummaryPath path : node.spSummaries) {
                    AccessPath sourceAp = source.getAccessPath();
                    AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                    Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                    res.add(newAbs);
                }
            }
        }
        index++;
        SootField f = ((len > 0 && index < len) ? fields[index] : null);
        boolean isKilled = false;
        if(f != null) {
            GraphNode next = node.childrens.get(f);
            if(next == null || next.killSummaries == null)
                isKilled = true;
            recurGetAbs(source, fields, index, next, res);
        }else {
            isKilled = true;
        }
        if(isKilled && node.killSummaries != null) {
            if(index < len ) {
                for(SummaryPath path : node.killSummaries) {
                    AccessPath sourceAp = source.getAccessPath();
                    AccessPath newAp =  convertToAccessPath(path.getTargetAccessPath(), sourceAp.getTaintSubFields(), index, fields);
                    Abstraction newAbs = source.deriveNewAbstraction(newAp, source.getCurrentStmt());
                    res.add(newAbs);
                }
            }
        }

        return res;

    }

}
