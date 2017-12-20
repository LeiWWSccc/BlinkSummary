package soot.jimple.infoflow.sparseOptimization.summary;

import soot.Unit;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;

/**
 * @author wanglei
 */
public class SummaryPath {

    private MyAccessPath dSource;

    private MyAccessPath dTarget;

    private DataFlowNode targetNode;

    private Unit src;

    private Unit target;

    private boolean isForward = true;


    public SummaryPath(Unit src, MyAccessPath dSource, Unit target, MyAccessPath dTarget, DataFlowNode targetNode) {
        this.src = src;
        this.dSource = dSource;
        this.target = target;
        this.dTarget = dTarget;
        this.targetNode = targetNode;
    }


    public MyAccessPath getdSource() {
        return dSource;
    }

    public Unit getSrc() {
        return src;
    }

    public void setForward(boolean forward) {
        isForward = forward;
    }

    public boolean isForward() {
        return isForward;
    }

    public Unit getTarget() {
        return target;
    }

    public DataFlowNode getTargetNode() {
        return targetNode;
    }

    public MyAccessPath getdTarget() {

        return dTarget;
    }
    public SummaryPath getInactiveCopy() {
        MyAccessPath newtargetNode = dTarget.deriveInactiveAp(null);

        return new SummaryPath(src, dSource, target, newtargetNode, targetNode);
    }

    public SummaryPath deriveNewMyAccessPath(Unit newStmt, MyAccessPath newTarget, DataFlowNode targetNode) {
        return new SummaryPath(src, dSource, newStmt, newTarget, targetNode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SummaryPath that = (SummaryPath) o;

        if (isForward != that.isForward) return false;
        if (dSource != null ? !dSource.equals(that.dSource) : that.dSource != null) return false;
        if (dTarget != null ? !dTarget.equals(that.dTarget) : that.dTarget != null) return false;
        if (targetNode != null ? !targetNode.equals(that.targetNode) : that.targetNode != null) return false;
        if (src != null ? !src.equals(that.src) : that.src != null) return false;
        return target != null ? target.equals(that.target) : that.target == null;
    }

    @Override
    public int hashCode() {
        int result = dSource != null ? dSource.hashCode() : 0;
        result = 31 * result + (dTarget != null ? dTarget.hashCode() : 0);
        result = 31 * result + (targetNode != null ? targetNode.hashCode() : 0);
        result = 31 * result + (src != null ? src.hashCode() : 0);
        result = 31 * result + (target != null ? target.hashCode() : 0);
        result = 31 * result + (isForward ? 1 : 0);
        return result;
    }

    @Override
    public String toString(){
        return (isForward()?"[F]":"[B]")+" Target{ " + dTarget.toString() + " }"  ;
    }
}
