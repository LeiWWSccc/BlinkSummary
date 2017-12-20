package soot.jimple.infoflow.sparseOptimization.basicblock;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmtFactory;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmtSet;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.util.BaseSelector;
import soot.toolkits.graph.DirectedGraph;
import soot.util.Chain;

import java.util.*;

/**
 * BasicBlock for ICFG
 * 自己定制的基本快图
 *
 * @author wanglei
 */

public  class BasicBlockGraph implements DirectedGraph<BasicBlock> {
    protected Body mBody;
    protected Chain<Unit> mUnits;
    protected SootMethod method;
    protected List<BasicBlock> mBlocks;
    protected List<BasicBlock> mHeads = new ArrayList<BasicBlock>();
    protected List<BasicBlock> mTails = new ArrayList<BasicBlock>();

    //通过每个unit查找到对应的基本快
    final protected Map<Unit, BasicBlock> unitToBBMap = new HashMap<>();
    //通过每个unit找到基本块内部的index
    final protected Map<Unit, Integer> unitToInnerBBIndexMap = new HashMap<>();

    final protected     Map<BasicBlock, Set<BasicBlock>> bbToReachableBbMap = new HashMap<>();

    public BasicBlockGraph(IInfoflowCFG unitGraph, SootMethod m) {
        mBody = m.getActiveBody();
        mUnits = mBody.getUnits();
        method = m;
        Set<Unit> leaders = computeLeaders(unitGraph, m);
        buildBlocks(leaders, unitGraph);

        //计算unit序关系使用
        computeBBReachableMap();
    }


    public Set<Unit> computeLeaders(IInfoflowCFG unitGraph, SootMethod m) {

        Body body = m.getActiveBody();
        if (body != mBody) {
            throw new RuntimeException(
                    "BlockGraph.computeLeaders() called with a UnitGraph that doesn't match its mBody.");
        }
        Set<Unit> leaders = new HashSet<Unit>();

        // Trap handlers start new basic blocks, no matter how many
        // predecessors they have.
        Chain<Trap> traps = body.getTraps();
        for (Iterator<Trap> trapIt = traps.iterator(); trapIt.hasNext();) {
            Trap trap = trapIt.next();
            leaders.add(trap.getHandlerUnit());
        }

        for (Iterator<Unit> unitIt = body.getUnits().iterator(); unitIt.hasNext();) {
            Unit u = unitIt.next();
            List<Unit> predecessors = unitGraph.getPredsOf(u);
            int predCount = predecessors.size();
            List<Unit> successors = unitGraph.getSuccsOf(u);
            int succCount = successors.size();

            if (predCount != 1) { // If predCount == 1 but the predecessor
                leaders.add(u); // is a branch, u will get added by that
            } // branch's successor test.
            if ((succCount > 1) || (u.branches())) {
                for (Iterator<Unit> it = successors.iterator(); it.hasNext();) {
                    leaders.add((Unit) it.next()); // The cast is an
                } // assertion check.
            }
        }
        return leaders;

    }


    protected Map<Unit, BasicBlock> buildBlocks(Set<Unit> leaders, IInfoflowCFG unitGraph) {
        List<BasicBlock> blockList = new ArrayList<BasicBlock>(leaders.size());
        Map<Unit, BasicBlock> unitToBlock = new HashMap<Unit, BasicBlock>(); // Maps head
        // and tail
        // units to
        // their blocks, for building
        // predecessor and successor lists.
        Unit blockHead = null;
        int blockLength = 0;
        Iterator<Unit> unitIt = mUnits.iterator();
        if (unitIt.hasNext()) {
            blockHead = unitIt.next();
            if (!leaders.contains(blockHead)) {
                throw new RuntimeException("BlockGraph: first unit not a leader!");
            }
            blockLength++;
        }
        Unit blockTail = blockHead;
        int indexInMethod = 0;

        while (unitIt.hasNext()) {
            Unit u = unitIt.next();
            if (leaders.contains(u)) {
                addBlock(blockHead, blockTail, indexInMethod, blockLength, blockList, unitToBlock, unitGraph);
                indexInMethod++;
                blockHead = u;
                blockLength = 0;
            }
            blockTail = u;
            blockLength++;
        }
        if (blockLength > 0) {
            // Add final block.
            addBlock(blockHead, blockTail, indexInMethod, blockLength, blockList, unitToBlock, unitGraph);
        }

        // The underlying UnitGraph defines heads and tails.
        //mHead get the head basic block
        for (Iterator<Unit> it = unitGraph.getStartPointsOf(method).iterator(); it.hasNext();) {
            Unit headUnit = (Unit) it.next();
            BasicBlock headBlock = unitToBlock.get(headUnit);
            if (headBlock.getHead() == headUnit) {
                mHeads.add(headBlock);
            } else {
                throw new RuntimeException("BlockGraph(): head Unit is not the first unit in the corresponding Block!");
            }
        }

        //mTails get the head basic block
        for (Iterator<Unit> it = unitGraph.getEndPointsOf(method).iterator(); it.hasNext();) {
            Unit tailUnit = (Unit) it.next();
            BasicBlock tailBlock = unitToBlock.get(tailUnit);
            if (tailBlock.getTail() == tailUnit) {
                mTails.add(tailBlock);
            } else {
                throw new RuntimeException("BlockGraph(): tail Unit is not the last unit in the corresponding Block!");
            }
        }

        for (Iterator<BasicBlock> blockIt = blockList.iterator(); blockIt.hasNext();) {
            BasicBlock block = blockIt.next();

            List<Unit> predUnits = unitGraph.getPredsOf(block.getHead());
            List<BasicBlock> predBlocks = new ArrayList<BasicBlock>(predUnits.size());
            for (Iterator<Unit> predIt = predUnits.iterator(); predIt.hasNext();) {
                Unit predUnit = predIt.next();
                BasicBlock predBlock = unitToBlock.get(predUnit);
                if (predBlock == null) {
                    throw new RuntimeException("BlockGraph(): block head mapped to null block!");
                }
                predBlocks.add(predBlock);
            }

            if (predBlocks.size() == 0) {
                block.setPreds(Collections.<BasicBlock> emptyList());

                // If the UnreachableCodeEliminator is not eliminating
                // unreachable handlers, then they will have no
                // predecessors, yet not be heads.
				/*
				 * if (! mHeads.contains(block)) { throw new
				 * RuntimeException("Block with no predecessors is not a head!"
				 * );
				 *
				 * // Note that a block can be a head even if it has //
				 * predecessors: a handler that might catch an exception //
				 * thrown by the first Unit in the method. }
				 */
            } else {
                block.setPreds(Collections.unmodifiableList(predBlocks));
                if (block.getHead() == mUnits.getFirst()) {
                    mHeads.add(block); // Make the first block a head
                    // even if the Body is one huge loop.
                }
            }

            List<Unit> succUnits = unitGraph.getSuccsOf(block.getTail());
            List<BasicBlock> succBlocks = new ArrayList<BasicBlock>(succUnits.size());
            for (Iterator<Unit> succIt = succUnits.iterator(); succIt.hasNext();) {
                Unit succUnit = succIt.next();
                BasicBlock succBlock = unitToBlock.get(succUnit);
                if (succBlock == null) {
                    throw new RuntimeException("BlockGraph(): block tail mapped to null block!");
                }
                succBlocks.add(succBlock);
            }

            if (succBlocks.size() == 0) {
                block.setSuccs(Collections.<BasicBlock> emptyList());
                if (!mTails.contains(block)) {
                    // if this block is totally empty and unreachable, we remove it
                    if (block.getPreds().isEmpty()
                            && block.getHead() == block.getTail()
                            && block.getHead() instanceof NopStmt)
                        blockIt.remove();
                    else
                        throw new RuntimeException("Block with no successors is not a tail!: " + block.toString());
                    // Note that a block can be a tail even if it has
                    // successors: a return that throws a caught exception.
                }
            } else {
                block.setSuccs(Collections.unmodifiableList(succBlocks));
            }
        }
        mBlocks = Collections.unmodifiableList(blockList);
        mHeads = Collections.unmodifiableList(mHeads);
        if (mTails.size() == 0) {
            mTails = Collections.emptyList();
        } else {
            mTails = Collections.unmodifiableList(mTails);
        }
        return unitToBlock;
    }

    private void addBlock(Unit head, Unit tail, int index, int length, List<BasicBlock> blockList,
                          Map<Unit, BasicBlock> unitToBlock, IInfoflowCFG unitGraph) {
        BasicBlock block = new BasicBlock(head, tail, mBody, index, length, this);
        blockList.add(block);
        unitToBlock.put(tail, block);
        unitToBlock.put(head, block);
        addOtherBlockInfo(head, tail, block, unitGraph);
    }

    private void addOtherBlockInfo(Unit head, Unit tail, BasicBlock block, IInfoflowCFG unitGraph) {
        int idx = 0;
        Unit cur = head;
        while(cur != tail) {
            unitToBBMap.put(cur, block);
            unitToInnerBBIndexMap.put(cur, idx);
            idx++;
            List<Unit> succs = unitGraph.getSuccsOf(cur);
            if(succs.size() != 1)
                throw new RuntimeException("inner BB's Unit has more than 1 succ!");
            cur = succs.get(0);
        }
        unitToBBMap.put(cur, block);
        unitToInnerBBIndexMap.put(cur, idx);
    }


    private Map<BasicBlock, Set<BasicBlock>> computeBBReachableMap() {

        List<BasicBlock> bbList =  this.mBlocks;
        for(BasicBlock bb : bbList) {
            Set<BasicBlock> reached = new HashSet<>();
            Queue<BasicBlock> worklist = new LinkedList<>();
            worklist.add(bb);
            while(!worklist.isEmpty()) {
                BasicBlock cur = worklist.poll();
                if(reached.contains(cur))
                    continue;
                reached.add(cur);
                for(BasicBlock next : cur.getSuccs()) {
                    worklist.offer(next);
                }
            }

            bbToReachableBbMap.put(bb, reached);
        }
        return bbToReachableBbMap;

    }
    private List<BaseInfoStmt> getEntryBaseStmtList() {
        List<BaseInfoStmt> entryStmtList = new ArrayList<>();
        for(BasicBlock bb  : mHeads) {
            if(bb.getPreds().size() != 0)
                throw new RuntimeException("mHeads computing is wrong");
            Unit head = bb.getHead();
            Integer innerBBIdx = unitToInnerBBIndexMap.get(head);
            BaseInfoStmt baseInfoStmt = BaseInfoStmtFactory.v().
                    createBaseInfo(null, null, null, null, bb, innerBBIdx, head);
            entryStmtList.add(baseInfoStmt);
        }
        return entryStmtList;
    }

    private List<BaseInfoStmt> getExitBaseStmtList() {
        List<BaseInfoStmt> exitStmtList = new ArrayList<>();

        for(BasicBlock bb  : mTails) {
            if(bb.getSuccs().size() != 0)
                throw new RuntimeException("mTails computing is wrong");

            Unit tail = bb.getTail();
            Integer innerBBIdx = unitToInnerBBIndexMap.get(tail);
            BaseInfoStmt baseInfoStmt = BaseInfoStmtFactory.v().
                    createBaseInfo(null, null, null, null, bb, innerBBIdx, tail);
            exitStmtList.add(baseInfoStmt);
        }
        return exitStmtList;
    }

    private enum ValueLocation {
        ParmAndThis,
        Left,
        Right,
        Arg
    }

    private void addValueToInfoMap(Map<Value, Set<Pair<ValueLocation, SootField>>> splitedInfoMap,
                                   Value value, ValueLocation location) {

        Pair<Value, SootField> ret = getBaseAndField(value);
        Value base = ret.getO1();
        SootField field = ret.getO2();

        if(base != null) {
            Set<Pair<ValueLocation, SootField>> tmpSet = null;
            if(splitedInfoMap.containsKey(base)) {
                tmpSet = splitedInfoMap.get(base);
            }else {
                tmpSet = new HashSet<>();
                splitedInfoMap.put(base, tmpSet);
            }
            tmpSet.add(new Pair<ValueLocation, SootField>(location, field));
        }

    }


    public Map<Value, BaseInfoStmtSet> computeBaseInfo() {

        final Map<Value, BaseInfoStmtSet> baseInfoStmtMapGbyBase = new HashMap<>();

        final PatchingChain<Unit> units = method.getActiveBody().getUnits();

        final List<BaseInfoStmt> entryStmtList = getEntryBaseStmtList();
        final List<BaseInfoStmt> exitStmtList = getExitBaseStmtList();

        Set<Value> paramAndThis = new HashSet<>();

        // Collect all units' variables , store it in BaseInfoStmt class,
        // then group by the BaseInfoStmt instances by their base value
        // store them into baseInfoStmtMapGbyBase HashMap
        // [Read]   S1: b = a      ::   Base : a -> { <S1:a> }
        // [Write]  S2: a = b      ::   Base : a -> { <S1:a> <S2:a> }
        // [Load]   S3: c = a.f1   ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> }
        // [Store]  S4: a.f2 = d   ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> <S4:a.f2> }
        // [invoke] s5: foo(a.f2)  ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> <S4:a.f2> <S5:a.f2>}
        for (Unit u : units) {
            if(!(u instanceof Stmt))
                continue;
            final Stmt stmt = (Stmt) u;

            int a = 0;
            if(stmt.toString().equals("$i1 = staticinvoke <com.appbrain.c.e: int b(int,com.appbrain.c.c)>(36, $r1)"))
                a ++;




            final  BasicBlock bb = unitToBBMap.get(u);
            final int innerBBIdx = unitToInnerBBIndexMap.get(u);

            final InvokeExpr ie = (stmt != null && stmt.containsInvokeExpr())
                    ? stmt.getInvokeExpr() : null;

            final Map<Value, Set<Pair<ValueLocation, SootField>>> splitedInfoMap = new HashMap<>();

            if(stmt instanceof AssignStmt) {
                final AssignStmt assignStmt = (AssignStmt) stmt;
                final Value left = assignStmt.getLeftOp();
                final Value right = assignStmt.getRightOp();
                final Value[] rightVals = BaseSelector.selectBaseList(right, true);

                //get left value :
                addValueToInfoMap(splitedInfoMap ,left, ValueLocation.Left);

                //get right value :
                for(Value rightVal : rightVals) {
                    addValueToInfoMap(splitedInfoMap ,rightVal, ValueLocation.Right);
                }

            }else if(stmt instanceof IdentityStmt) {
                final IdentityStmt is = ((IdentityStmt)u);
                if (is.getRightOp() instanceof ParameterRef || is.getRightOp() instanceof ThisRef){
                    paramAndThis.add(is.getLeftOp());

                    addValueToInfoMap(splitedInfoMap, is.getLeftOp(), ValueLocation.ParmAndThis);

                }

            }else if(stmt instanceof InvokeStmt) {

            }else if (stmt instanceof IfStmt) {
                continue;
            }

            if(ie != null) {

                if((stmt instanceof IdentityStmt) &&
                        ((IdentityStmt)u).getRightOp() instanceof ThisRef) {

                }
                if(ie instanceof InstanceInvokeExpr) {
                    // add invoke stmt's base, such as  a.foo()
                    InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
                    addValueToInfoMap(splitedInfoMap ,vie.getBase(), ValueLocation.Arg);
                }

                for (int i = 0; i < ie.getArgCount(); i++) {
                    addValueToInfoMap(splitedInfoMap , ie.getArg(i), ValueLocation.Arg);
                }
            }


            /*
                收集完所有的变量信息以后，将他们一一处理
                根据不同的位置类型写入不同的Baseinfo信息中
                根据base放入各自的BaseinfoSet中
             */

            for(Map.Entry<Value, Set<Pair<ValueLocation, SootField>>> entry : splitedInfoMap.entrySet()) {
                Value base = entry.getKey();
                SootField leftField = null;
                List<SootField> rightFields = new ArrayList<>();
                List<SootField> argsFields = new ArrayList<>();

                Set<Pair<ValueLocation, SootField>> tmpSet = entry.getValue();

                for(Pair<ValueLocation, SootField> pair : tmpSet) {
                    ValueLocation location = pair.getO1();
                    SootField field = pair.getO2();
                    if(field == null)
                        field = DataFlowNode.baseField;

                    switch (location) {
                        case ParmAndThis:
                            leftField = field;
                            if(leftField != DataFlowNode.baseField)
                                throw new RuntimeException("the field of parm and this is not null! ");
                            break;
                        case Left :
                            leftField = field;
                            break;
                        case Right:
                            rightFields.add(field);
                            break;
                        case Arg:
                            argsFields.add(field);
                            break;
                    }
                }

                BaseInfoStmtSet BaseInfoStmtSet = null;
                if(baseInfoStmtMapGbyBase.containsKey(base)){
                    BaseInfoStmtSet =  baseInfoStmtMapGbyBase.get(base);
                }else {
                    BaseInfoStmtSet = new BaseInfoStmtSet(method, base, exitStmtList, paramAndThis);
                    BaseInfoStmtSet.addAll(exitStmtList);
                    BaseInfoStmtSet.addAll(entryStmtList);
                    baseInfoStmtMapGbyBase.put(base, BaseInfoStmtSet);
                }
                BaseInfoStmtSet.add(
                        BaseInfoStmtFactory.v().createBaseInfo(base,
                                leftField, rightFields, argsFields, bb, innerBBIdx, stmt));
            }

        }

        return baseInfoStmtMapGbyBase;

    }


    public static Pair<Value, SootField> getBaseAndField(Value value) {
//        Value rightBase = null;
//        SootField rightField = null;
//
//        if(value instanceof Local) {
//            rightBase = value;
//        }else if(value instanceof FieldRef) {
//            if(value instanceof  InstanceFieldRef) {
//                //Value base = BaseSelector.selectBase(left, true);
//                rightBase = ((InstanceFieldRef) value).getBase();
//                rightField = ((InstanceFieldRef)value).getField();
//            }
//        }
        Value base = null;
        SootField field = null;
        if(value instanceof BinopExpr) {
            throw new RuntimeException("getBaseAndValue method should't be BinopExpr!");
        }
        if(value instanceof StaticFieldRef) {
            base = DataFlowNode.staticValue;
            field = ((StaticFieldRef) value).getField();

        }else if(value instanceof Local) {

            // a   : base : a
            base = value;
        }else if(value instanceof FieldRef) {
            if(value instanceof InstanceFieldRef) {
                //a.f  : base : a  field : f

                //Value base = BaseSelector.selectBase(left, true);
                base = ((InstanceFieldRef) value).getBase();
                field = ((InstanceFieldRef)value).getField();
            }

        }else if (value instanceof ArrayRef) {
            ArrayRef ref = (ArrayRef) value;
            base = (Local) ref.getBase();
            Value rightIndex = ref.getIndex();
        } if(value instanceof LengthExpr) {
            LengthExpr lengthExpr = (LengthExpr) value;
            base = lengthExpr.getOp();
        } else if (value instanceof NewArrayExpr) {
            NewArrayExpr newArrayExpr = (NewArrayExpr) value;
            base = newArrayExpr.getSize();
        }

        return new Pair<>(base, field);
    }








    public  boolean computeOrder(Unit u1, Unit u2) {

        //source or start of the method
        if(u1 == null)
            return true;

        BasicBlock bb1 = unitToBBMap.get(u1);
        BasicBlock bb2 = unitToBBMap.get(u2);
        if(bb1 == bb2) {
            int idx1 = unitToInnerBBIndexMap.get(u1);
            int idx2 = unitToInnerBBIndexMap.get(u2);
            return idx1 <= idx2;
        }else {
            if(bbToReachableBbMap.get(bb1).contains(bb2))
                return true;
        }
        return false;

    }



    public Body getBody() {
        return mBody;
    }

    public List<BasicBlock> getBlocks() {
        return mBlocks;
    }

    public int getInnerBlockId(Unit u) {

        return 0;

    }

    public BasicBlock getBasicBlock(Unit u) {
        return null;
    }


    @Override
    public List<BasicBlock> getHeads() {
        return mHeads;
    }

    @Override
    public List<BasicBlock> getTails() {
        return mTails;
    }

    @Override
    public List<BasicBlock> getPredsOf(BasicBlock s) {
        return s.getPreds();
    }

    @Override
    public List<BasicBlock> getSuccsOf(BasicBlock s) {
        return s.getSuccs();
    }

    @Override
    public int size() {
        return mBlocks.size();
    }

    @Override
    public Iterator<BasicBlock> iterator() {
        return mBlocks.iterator();
    }

    public String toString() {

        Iterator<BasicBlock> it = mBlocks.iterator();
        StringBuffer buf = new StringBuffer();
        while (it.hasNext()) {
            BasicBlock someBlock = it.next();

            buf.append(someBlock.toString() + '\n');
        }

        return buf.toString();
    }
}
