package soot.jimple.infoflow.sparseOptimization.summary;

import soot.SootField;
import soot.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class SummaryGraph {

    private class GraphNode {

        private Set<MyAccessPath> summaries;
        private Set<MyAccessPath> spSummaries;

        private Map<SootField, GraphNode> childrens;

        GraphNode(MyAccessPath summary, boolean isSp) {
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
        if(source.getFields() == null) {
            root = new GraphNode(path.getTargetAccessPath(), isSp);
        }else {
            root = new GraphNode();
            addChildrenSummary(0, root, source.getFields(), path.getTargetAccessPath(), isSp);
        }

    }

    private void addChildrenSummary( int index, GraphNode root, SootField[] fields,
                                     MyAccessPath summaryAp, boolean isSp) {
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

    public void merge(Value base, SootField[] sootFields, MyAccessPath target) {


    }

    public void merge(SummaryPath path) {

    }

}
