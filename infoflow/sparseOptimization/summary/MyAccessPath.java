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
    private  SootField[] fields;

    private Unit activeStmt ;

    private boolean isStrongUpdateSource = false;

    public MyAccessPath() {
        this.value = null;
        this.fields = null;
    }


    public MyAccessPath(Value val, SootField[] appendingFields) {
        this.value = val;
        this.fields = appendingFields;
    }
    public MyAccessPath(Value val, SootField[] appendingFields, Unit activeStmt, boolean isStrongUpdateSource) {
        this.value = val;
        this.fields = appendingFields;
        this.activeStmt = activeStmt;
        this.isStrongUpdateSource = isStrongUpdateSource;
    }

    public boolean isStrongUpdateSource() {
        return isStrongUpdateSource;
    }

    public void setStrongUpdateSource(boolean strongUpdateSource) {
        isStrongUpdateSource = strongUpdateSource;
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

    public void setFields(SootField[] fields) {
        this.fields = fields;
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

    public MyAccessPath deriveNewApAddfield(SootField addField) {
        MyAccessPath ap = this.clone();
        SootField[] oldField = ap.getFields();
        SootField[] newField = null;
        if(oldField == null) {
            newField = new SootField[1];
            newField[0] = addField;
        }else {
            newField = new SootField[oldField.length + 1];
            System.arraycopy(oldField, 0, newField, 0, oldField.length );
            newField[oldField.length] = addField;
        }
        ap.setFields(newField);
        return ap;
    }

    public MyAccessPath clone() {

        return new MyAccessPath(value, fields, activeStmt, isStrongUpdateSource);
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

        if (isStrongUpdateSource != that.isStrongUpdateSource) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(fields, that.fields)) return false;
        return activeStmt != null ? activeStmt.equals(that.activeStmt) : that.activeStmt == null;
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(fields);
        result = 31 * result + (activeStmt != null ? activeStmt.hashCode() : 0);
        result = 31 * result + (isStrongUpdateSource ? 1 : 0);
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

        if(isStrongUpdateSource)
            str += " | SP";

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
