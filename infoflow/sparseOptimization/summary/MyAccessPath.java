package soot.jimple.infoflow.sparseOptimization.summary;

import soot.Local;
import soot.SootField;
import soot.Unit;
import soot.Value;

import java.util.Arrays;

/**
 * @author wanglei
 */
public class MyAccessPath {




    private final Value value;


    /**
     * list of fields, either they are based on a concrete @value or they indicate a static field
     */
    private final SootField[] fields;

    private Unit activeStmt ;

    public MyAccessPath() {
        this.value = null;
        this.fields = null;
    }


    public MyAccessPath(Value val, SootField[] appendingFields) {
        this.value = val;
        this.fields = appendingFields;
    }



    public Value getValue() {
        return value;
    }

    public SootField[] getFields() {
        return fields;
    }
    public Unit getActiveStmt() {
        return activeStmt;
    }

    public void setActiveStmt(Unit activeStmt) {
        this.activeStmt = activeStmt;
    }
    public boolean isActive() {
        return activeStmt == null;
    }

    public MyAccessPath deriveInactiveAp(Unit activeStmt) {
        MyAccessPath ap = this.clone();
        ap.activeStmt = activeStmt;
        return ap;
    }

    public MyAccessPath clone() {

        return new MyAccessPath(value, fields);
    }

    public boolean isLocal(){
        return value != null && value instanceof Local && (fields == null || fields.length == 0);
    }

    public int getFieldCount() {
        return fields == null ? 0 : fields.length;
    }

    public boolean firstFieldMatches(SootField field) {
        if (fields == null || fields.length == 0)
            return false;
        if (field == fields[0])
            return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyAccessPath that = (MyAccessPath) o;

        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(fields);
        return result;
    }

    @Override
    public String toString(){
        String str = "";
        if(value != null)
            str += value.toString() +"(" + value.getType() +")";
        if (fields != null)
            for (int i = 0; i < fields.length; i++)
                if (fields[i] != null) {
                    if (!str.isEmpty())
                        str += " ";
                    str += fields[i];
                }

        str += " | " + activeStmt;
//        if (taintSubFields)
//            str += " *";
//
//        if (arrayTaintType == AccessPath.ArrayTaintType.ContentsAndLength)
//            str += " <+length>";
//        else if (arrayTaintType == AccessPath.ArrayTaintType.Length)
//            str += " <length>";

        return str;
    }



}
