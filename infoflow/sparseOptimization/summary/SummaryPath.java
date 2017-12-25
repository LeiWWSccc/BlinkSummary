package soot.jimple.infoflow.sparseOptimization.summary;

import soot.SootField;
import soot.Unit;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wanglei
 */
public class SummaryPath {

    private MyAccessPath sourceAccessPath;

    private MyAccessPath targetAccessPath;

    private DataFlowNode targetNode;

    private Unit src;

    private Unit target;

    private boolean isForward = true;

    private Set<SootField> killSet;


    public SummaryPath(Unit src, MyAccessPath sourceAccessPath,
                       Unit target, MyAccessPath targetAccessPath, DataFlowNode targetNode,
                       Set<SootField> killSet) {
        this.src = src;
        this.sourceAccessPath = sourceAccessPath;
        this.target = target;
        this.targetAccessPath = targetAccessPath;
        this.targetNode = targetNode;
        this.killSet = killSet;
    }

    public Set<SootField> getKillSet() {
        return killSet;
    }

    public void setSourceAccessPath(MyAccessPath sourceAccessPath) {
        this.sourceAccessPath = sourceAccessPath;
    }

    public MyAccessPath getSourceAccessPath() {
        return sourceAccessPath;
    }

    public Unit getSrc() {
        return src;
    }

    public void setForward(boolean forward) {
        isForward = forward;
    }

    public void setKillSet(Set<SootField> killSet) {
        this.killSet = killSet;
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

    public MyAccessPath getTargetAccessPath() {

        return targetAccessPath;
    }
    public SummaryPath getInactiveCopy() {
        MyAccessPath newtargetNode = targetAccessPath.deriveInactiveAp(null);

        return new SummaryPath(src, sourceAccessPath, target, newtargetNode, targetNode, killSet);
    }

    public SummaryPath deriveNewMyAccessPath(Unit newStmt, MyAccessPath newTarget, DataFlowNode targetNode) {
        return deriveNewMyAccessPath(null, newStmt, newTarget, targetNode);
    }

    public SummaryPath deriveNewMyAccessPath(MyAccessPath newSrcAp,  Unit newStmt, MyAccessPath newTarget, DataFlowNode targetNode) {

        SummaryPath newSp =  new SummaryPath(src, sourceAccessPath, newStmt, newTarget, targetNode, killSet);
        if(newSrcAp != null)
            newSp.setSourceAccessPath(newSrcAp);
        return newSp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SummaryPath that = (SummaryPath) o;

        if (isForward != that.isForward) return false;
        if (sourceAccessPath != null ? !sourceAccessPath.equals(that.sourceAccessPath) : that.sourceAccessPath != null) return false;
        if (targetAccessPath != null ? !targetAccessPath.equals(that.targetAccessPath) : that.targetAccessPath != null) return false;
        if (targetNode != null ? !targetNode.equals(that.targetNode) : that.targetNode != null) return false;
        if (src != null ? !src.equals(that.src) : that.src != null) return false;
        return target != null ? target.equals(that.target) : that.target == null;
    }

    public SummaryPath clone() {
        return new SummaryPath(src, sourceAccessPath, target, targetAccessPath, targetNode, killSet);
    }

    public SummaryPath deriveNewPathWithKillSet(SootField field) {
        SummaryPath ret = null;
        if(this.killSet == null) {
            Set<SootField> set = new HashSet<>();
            set.add(field);
            ret = this.clone();
            ret.setKillSet(set);
        }else {
            killSet.add(field);
            ret =  this;
        }
        return ret;
    }


    @Override
    public int hashCode() {
        int result = sourceAccessPath != null ? sourceAccessPath.hashCode() : 0;
        result = 31 * result + (targetAccessPath != null ? targetAccessPath.hashCode() : 0);
        result = 31 * result + (targetNode != null ? targetNode.hashCode() : 0);
        result = 31 * result + (src != null ? src.hashCode() : 0);
        result = 31 * result + (target != null ? target.hashCode() : 0);
        result = 31 * result + (isForward ? 1 : 0);
        return result;
    }

    @Override
    public String toString(){
        return (isForward()?"[F]":"[B]")+" Target{ " + targetAccessPath.toString() + " }"  ;
    }
}
