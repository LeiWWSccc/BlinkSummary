package soot.jimple.infoflow.sparseOptimization.dataflowgraph.data;

import soot.SootField;
import soot.Unit;
import soot.Value;

/**
 * @author wanglei
 */

public class DFGEntryKey {

    private final Value base;

    private final Unit stmt;

    private SootField field;

    private boolean isOriginal = true;
    private boolean isLeft = true;

    private final int hashCode;

    public DFGEntryKey(Unit u, Value base, SootField field) {
        this.stmt = u;
        this.base = base;
        this.field = field;

        final int prime = 31;
        int result = 1;
        result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
        result = prime * result + ((base == null) ? 0 : base.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((isOriginal == false) ? 1231:1237);
        result = prime * result + ((isLeft == false) ? 1231:1237);
        this.hashCode = result;
    }

    public DFGEntryKey(Unit u, Value base, SootField field, boolean isOriginal) {
        this.stmt = u;
        this.base = base;
        this.field = field;
        this.isOriginal = isOriginal;

        final int prime = 31;
        int result = 1;
        result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
        result = prime * result + ((base == null) ? 0 : base.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((isOriginal == false) ? 1231:1237);
        result = prime * result + ((isLeft == false) ? 1231:1237);
        this.hashCode = result;
    }

    public DFGEntryKey(Unit u, Value base, SootField field, boolean isOriginal, boolean isLeft) {
        this.stmt = u;
        this.base = base;
        this.field = field;
        this.isOriginal = isOriginal;
        this.isLeft = isLeft;

        final int prime = 31;
        int result = 1;
        result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
        result = prime * result + ((base == null) ? 0 : base.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((isOriginal == false) ? 1231:1237);
        result = prime * result + ((isLeft == false) ? 1231:1237);
        this.hashCode = result;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DFGEntryKey other = (DFGEntryKey) obj;
        if (stmt == null) {
            if (other.stmt!= null)
                return false;
        } else if (!stmt.equals(other.stmt))
            return false;
        if (base == null) {
            if (other.base != null)
                return false;
        } else if (!base.equals(other.base))
            return false;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if(isOriginal != other.isOriginal)
            return false;
        if(isLeft != other.isLeft)
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("<");
        result.append(stmt);
        result.append("> , [");
        result.append(base.toString());
        result.append(" . ");
        result.append(field);
        result.append("]");
        return result.toString();
    }

}
