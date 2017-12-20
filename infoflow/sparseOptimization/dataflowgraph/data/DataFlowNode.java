package soot.jimple.infoflow.sparseOptimization.dataflowgraph.data;

import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.internal.JimpleLocal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wanglei
 */
public class DataFlowNode {

    private final Value value;

    private final Unit stmt;

    public final static Value staticValue = new JimpleLocal("staticValue", NullType.v());

    public final static SootField baseField = getBaseField();

    private SootField field;

    private boolean isLeft;

    public int hashCode = 0;

    private HashMap<SootField, Set<DataFlowNode>> succs ;
    private Set<SootField> killFields ;

    DataFlowNode(Unit u, Value val, SootField f, boolean isLeft) {
        this.stmt = u;
        this.value = val;
        this.field = f;
        this.isLeft = isLeft;
    }

    private static SootField getBaseField() {
        SootField field =  new SootField("null",  NullType.v(), soot.Modifier.FINAL);
        SootClass cl = new SootClass("null");
        cl.addField(field);
        return field;
    }


    public boolean getIsLeft() {
        return isLeft;
    }

    public Value getValue() {
        return value;
    }

    public SootField getField() {
        return field;
    }

    public HashMap<SootField, Set<DataFlowNode>> getSuccs() {
        return this.succs;
    }

    public Unit getStmt() {
        return this.stmt;
    }

    public void setSuccs(SootField field, DataFlowNode target) {
        if(field == null)
            field = baseField;

        if(succs == null)
            succs = new HashMap<>();
        if(succs.containsKey(field)) {
            succs.get(field).add(target);
        } else {
            Set<DataFlowNode> tmp = new HashSet<>();
            tmp.add(target);
            succs.put(field, tmp);
        }
    }

//    public void setKillField(SootField field) {
//        if(killFields == null)
//            killFields = new HashSet<>();
//        killFields.add(field);
//    }
//
//    public Set<SootField> getKillFields() {
//        return killFields;
//    }
public static long count= 0;


    public Abstraction deriveNewAbsbyAbs(Abstraction abs) {
        long beforeFsolver = System.nanoTime();
        Set<Unit> uses = new HashSet<>();
        AccessPath ap = abs.getAccessPath();
        SootField firstField = ap.getFirstField();

        if(succs != null) {
            Set<DataFlowNode> next = succs.get(DataFlowNode.baseField);
            if(next != null)
                for(DataFlowNode d : next) {
                    uses.add((Stmt)d.stmt);
                }

            if(firstField != null) {
                Set<DataFlowNode> next1 = succs.get(firstField);
                if(next1 != null)
                    for(DataFlowNode d : next1) {
                        uses.add((Stmt)d.stmt);
                    }
            }
        }
        abs.setUseStmts(uses);
        count += (System.nanoTime() - beforeFsolver);

        return abs;
    }

    public Abstraction deriveNewAbsAllInfo(Abstraction abs) {
        long beforeFsolver = System.nanoTime();
        Set<Unit> uses = new HashSet<>();
        if(succs != null) {
            for(Set<DataFlowNode> nexts : succs.values()) {
                for(DataFlowNode n : nexts) {
                    if(n.getValue() == null || n.getValue() != null && n.getValue().equals(value)) {

                        uses.add(n.getStmt());
                    }
                }
            }
        }
        abs.setUseStmts(uses);
        count += (System.nanoTime() - beforeFsolver);

        return abs;
    }

    public Abstraction deriveCallNewAbsbyAbs(Abstraction abs) {
        Set<Unit> uses = new HashSet<>();
        AccessPath ap = abs.getAccessPath();
        SootField firstField = ap.getFirstField();
        if(succs != null) {
            Set<DataFlowNode> next = succs.get(DataFlowNode.baseField);
            if(next != null)
                for(DataFlowNode d : next) {
                    uses.add((Stmt)d.stmt);
                }

            if(firstField != null) {
                Set<DataFlowNode> next1 = succs.get(firstField);
                if(next1 != null)
                    for(DataFlowNode d : next1) {
                        uses.add((Stmt)d.stmt);
                    }
            }
        }
        abs.setUseStmts(uses);
        return abs;
    }


//
//    public Set<Abstraction> deriveNewDataFacts(Abstraction abs) {
//        Set<Abstraction> res = new HashSet<>();
//        AccessPath ap = abs.getAccessPath();
//        SootField firstField = ap.getFirstField();
//        if(succs != null) {
//            Set<DataFlowNode> next = succs.get(DataFlowNode.baseField);
//            if(next != null)
//                for(DataFlowNode d : next) {
//                    res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
//                }
//
//            if(firstField != null) {
//                Set<DataFlowNode> next1 = succs.get(firstField);
//                if(next1 != null)
//                    for(DataFlowNode d : next1) {
//                        res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
//                    }
//            }
//        }
//        return res;
//    }
//
//
//    public Set<Pair<DataFlowNode, Abstraction>> deriveNewDataFacts(Abstraction abs) {
//        Set<Pair<DataFlowNode, Abstraction>> res = new HashSet<>();
//        AccessPath ap = abs.getAccessPath();
//        SootField firstField = ap.getFirstField();
//        if(succs != null) {
//            Set<DataFlowNode> next = succs.get(DataFlowNode.baseField);
//            if(next != null)
//                for(DataFlowNode d : next) {
//                    res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
//                }
//
//            if(firstField != null) {
//                Set<DataFlowNode> next1 = succs.get(firstField);
//                if(next1 != null)
//                    for(DataFlowNode d : next1) {
//                        res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
//                    }
//            }
//        }
//        return res;
//    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DataFlowNode other = (DataFlowNode) obj;

        // If we have already computed hash codes, we can use them for
        // comparison
        if (this.hashCode != 0
                && other.hashCode != 0
                && this.hashCode != other.hashCode)
            return false;

        if (stmt == null) {
            if (other.stmt != null)
                return false;
        } else if (!stmt.equals(other.stmt))
            return false;

        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;

        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;

        if(isLeft != other.isLeft)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        if (this.hashCode != 0)
            return hashCode;

        final int prime = 31;
        int result = 1;

        // deliberately ignore prevAbs
        result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((isLeft) ? 1231 : 1237);
        this.hashCode = result;

        return this.hashCode;
    }

    public DataFlowNode clone() {
        DataFlowNode newNode = new DataFlowNode(stmt, value, field, isLeft);
        return newNode;
    }

    public String toString() {
        if(value == null) {
            return "RETURN Stmt :" + stmt;
        }
        String fs ;
        if(field == DataFlowNode.baseField)
            fs = "NULL";
        else
            fs = field.toString();


        return "STMT{ " + stmt + " }, BASE{ " + value + " }, FIELD{ " + fs +" }";
    }

//    public void setSuccs(DataFlowNode source, SootField field, DataFlowNode target) {
//        HashMap<SootField, Set<DataFlowNode>> succs = source.getSuccs();
//        if(succs == null)
//            succs = new HashMap<>();
//        if(succs.containsKey(field)) {
//            succs.get(field).add(target);
//        } else {
//            Set<DataFlowNode> tmp = new HashSet<>();
//            tmp.add(target);
//            succs.put(field, tmp);
//        }
//    }


}
