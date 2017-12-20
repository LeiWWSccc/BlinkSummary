package soot.jimple.infoflow;

import heros.solver.Pair;
import soot.FastHierarchy;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.collect.MyConcurrentHashMap;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.sparseOptimization.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.Map;
import java.util.Set;

/**
 * Manager class for passing internal data flow objects to interface
 * implementors
 * 
 * @author Steven Arzt
 *
 */
public class InfoflowManager {
	
	private final InfoflowConfiguration config;
	private IInfoflowSolver forwardSolver;
	private final IInfoflowCFG icfg;
	private final ISourceSinkManager sourceSinkManager;
	private final ITaintPropagationWrapper taintWrapper;
	private final TypeUtils typeUtils;
	private final FastHierarchy hierarchy;
	private final AccessPathFactory accessPathFactory;

	//op
	private  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg ;

	private  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> backwardDfg ;

	private Map<SootMethod, BasicBlockGraph> methodToBasicBlockGraphMap ;

	private MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>> activationUnitsToUnits;





	InfoflowManager(InfoflowConfiguration config,
			IInfoflowSolver forwardSolver,
			IInfoflowCFG icfg,
			ISourceSinkManager sourceSinkManager,
			ITaintPropagationWrapper taintWrapper,
			FastHierarchy hierarchy,
			AccessPathFactory accessPathFactory) {
		this.config = config;
		this.forwardSolver = forwardSolver;
		this.icfg = icfg;
		this.sourceSinkManager = sourceSinkManager;
		this.taintWrapper = taintWrapper;
		this.typeUtils = new TypeUtils(this);
		this.hierarchy = hierarchy;
		this.accessPathFactory = accessPathFactory;
	}
	
	/**
	 * Gets the configuration for this data flow analysis
	 * @return The configuration for this data flow analysis
	 */
	public InfoflowConfiguration getConfig() {
		return this.config;
	}
	
	/**
	 * Sets the IFDS solver that propagates edges forward
	 * @param solver The IFDS solver that propagates edges forward
	 */
	public void setForwardSolver(IInfoflowSolver solver) {
		this.forwardSolver = solver;
	}
	
	/**
	 * Gets the IFDS solver that propagates edges forward
	 * @return The IFDS solver that propagates edges forward
	 */
	public IInfoflowSolver getForwardSolver() {
		return this.forwardSolver;
	}
	
	/**
	 * Gets the interprocedural control flow graph
	 * @return The interprocedural control flow graph
	 */
	public IInfoflowCFG getICFG() {
		return this.icfg;
	}
	
	/**
	 * Gets the SourceSinkManager implementation
	 * @return The SourceSinkManager implementation
	 */
	public ISourceSinkManager getSourceSinkManager() {
		return this.sourceSinkManager;
	}
	
	/**
	 * Gets the taint wrapper to be used for handling library calls
	 * @return The taint wrapper to be used for handling library calls
	 */
	public ITaintPropagationWrapper getTaintWrapper() { 
		return this.taintWrapper;
	}
	
	/**
	 * Gets the utility class for type checks
	 * @return The utility class for type checks
	 */
	public TypeUtils getTypeUtils() {
		return this.typeUtils;
	}
	
	/**
	 * Gets the Soot type hierarchy that was constructed together with the
	 * callgraph. In contrast to Scene.v().getFastHierarchy, this object is
	 * guaranteed to be available.
	 * @return The fast hierarchy
	 */
	public FastHierarchy getHierarchy() {
		return hierarchy;
	}
	
	/**
	 * Gets the factory object for creating new access paths
	 * @return The factory object for creating new access paths
	 */
	public AccessPathFactory getAccessPathFactory() {
		return this.accessPathFactory;
	}


	public Map<SootMethod, BasicBlockGraph> getMethodToBasicBlockGraphMap() {
		return this.methodToBasicBlockGraphMap;
	}
	/**
	 * Checks whether the analysis has been aborted
	 * @return True if the analysis has been aborted, otherwise false
	 */
	public boolean isAnalysisAborted() {
		if (forwardSolver instanceof IMemoryBoundedSolver)
			return ((IMemoryBoundedSolver) forwardSolver).isKilled();
		return false;
	}

	public void setActivationUnitsToUnits (MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>>  map) {
		this.activationUnitsToUnits = map;
	}

	public MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>> getActivationUnitsToUnits () {
		return this.activationUnitsToUnits;
	}
	
}
