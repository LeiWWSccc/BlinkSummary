package soot.jimple.infoflow.sparseOptimization.basicblock;

import soot.Unit;

import java.util.Map;
import java.util.Set;

/**
 * 没用了，备份，  请删除
 * @author wanglei
 */
public class UnitOrderComputing {

    private final Map<Unit, BasicBlock> unitToBBMap ;
    final private Map<Unit, Integer> unitToInnerBBIndexMap ;
    final private Map<BasicBlock, Set<BasicBlock>> bbOrderMap ;

    public UnitOrderComputing (Map<Unit, BasicBlock> unitToBBMap, Map<Unit, Integer> unitToInnerBBIndexMap,
                               Map<BasicBlock, Set<BasicBlock>> bbOrderMap) {
        this.unitToBBMap = unitToBBMap;
        this.unitToInnerBBIndexMap = unitToInnerBBIndexMap;
        this.bbOrderMap = bbOrderMap;
    }


}
