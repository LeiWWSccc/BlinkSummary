package soot.jimple.infoflow.sparseOptimization.dataflowgraph.data;

import soot.SootField;
import soot.Unit;
import soot.Value;

/**
 * @author wanglei
 */
public class DataFlowNodeFactory {

    public static DataFlowNodeFactory instance = new DataFlowNodeFactory();

    public static DataFlowNodeFactory v() {return instance;}

    public DataFlowNode createDataFlowNode(Unit u, Value val, SootField field, boolean isLeft) {


        return new DataFlowNode(u, val, field, isLeft);
    }



}
