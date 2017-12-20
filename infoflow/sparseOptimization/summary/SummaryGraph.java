package soot.jimple.infoflow.sparseOptimization.summary;

import soot.SootField;
import soot.Value;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class SummaryGraph {

    private class GraphNode {



        Set<MyAccessPath> summaries;

        Map<SootField, Set<GraphNode>> childrens;

        GraphNode(MyAccessPath summary) {
            summaries = new HashSet<>();
            summaries.add(summary);
        }

    }

    private GraphNode root ;
    SummaryGraph(SummaryPath path) {
        MyAccessPath source = path.getdSource();
        if(source.getFields() == null) {
            root = new GraphNode(path.getdTarget());
        }

    }

    public void merge(Value base, SootField[] sootFields, MyAccessPath target) {


    }

    public void merge(SummaryPath path) {

    }

}
