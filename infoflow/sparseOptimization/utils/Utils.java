package soot.jimple.infoflow.sparseOptimization.utils;

import soot.NullType;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JimpleLocal;

/**
 * @author wanglei
 */
public class Utils {

    public static Stmt unknownStmt = new JAssignStmt(new JimpleLocal("unknown", NullType.v()), new JimpleLocal("unknown", NullType.v()));

    public static Stmt reusedStmt = new JAssignStmt(new JimpleLocal("reusedVar", NullType.v()), new JimpleLocal("reusedVar", NullType.v()));

}
