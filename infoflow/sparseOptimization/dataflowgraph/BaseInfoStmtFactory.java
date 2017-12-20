package soot.jimple.infoflow.sparseOptimization.dataflowgraph;

import soot.SootField;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlock;

import java.util.List;

/**
 * @author wanglei
 */
public class BaseInfoStmtFactory {

    public static BaseInfoStmtFactory instance = new BaseInfoStmtFactory();

    public static BaseInfoStmtFactory v() {return instance;}

    public BaseInfoStmt createBaseInfo(Value base, SootField leftField, List<SootField> rightFields,
                                       List<SootField> argsFields, BasicBlock bb, int idx, Unit stmt) {
        //returnStmt
        if(base == null) {
            return new BaseInfoStmt(null, null, null,
                    null, bb, idx, (Stmt)stmt );
        }


        SootField[] rightFieldsArray = null;
        if(!rightFields.isEmpty()) {
            rightFieldsArray = new SootField[rightFields.size()];
            rightFields.toArray(rightFieldsArray);
        }

        SootField[] argsFieldsArray = null;
        if(!argsFields.isEmpty()) {
            argsFieldsArray = new SootField[argsFields.size()];
            argsFields.toArray(argsFieldsArray);
        }

        return new BaseInfoStmt(base, leftField, rightFieldsArray,
                argsFieldsArray, bb, idx, (Stmt)stmt );

    }


}
