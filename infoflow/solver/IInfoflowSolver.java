package soot.jimple.infoflow.solver;

import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.solver.memory.IMemoryManager;

import java.util.Set;

public interface IInfoflowSolver {
	
	/**
	 * Schedules the given edge for processing in the solver
	 * @param edge The edge to schedule for processing
	 * @return True if the edge was scheduled, otherwise (e.g., if the edge has
	 * already been processed earlier) false
	 */
	public boolean processEdge(PathEdge<Unit, Abstraction> edge);
	public boolean processEdge(PathEdge<Unit, Abstraction> edge, Unit defStmt);

	/**
	 * Gets the end summary of the given method for the given incoming
	 * abstraction
	 * @param m The method for which to get the end summary
	 * @param d3 The incoming fact (context) for which to get the end summary
	 * @return The end summary of the given method for the given incoming
	 * abstraction
	 */
	public Set<Pair<Unit, Abstraction>> endSummary(SootMethod m, Abstraction d3);
	
	public void injectContext(IInfoflowSolver otherSolver, SootMethod callee, Abstraction d3,
			Unit callSite, Abstraction d2, Abstraction d1);
	
	/**
	 * Cleans up some unused memory. Results will still be available afterwards,
	 * but no intermediate computation values.
	 */
	public void cleanup();
	
	/**
	 * Sets a handler that will be called when a followReturnsPastSeeds case
	 * happens, i.e., a taint leaves a method for which we have not seen any
	 * callers
	 * @param handler The handler to be called when a followReturnsPastSeeds
	 * case happens
	 */
	public void setFollowReturnsPastSeedsHandler(IFollowReturnsPastSeedsHandler handler);
	
	/**
	 * Sets the memory manager that shall be used to manage the abstractions
	 * @param memoryManager The memory manager that shall be used to manage the
	 * abstractions
	 */
	public void setMemoryManager(IMemoryManager<Abstraction, Unit> memoryManager);

	/**
	 * Gets the memory manager used by this solver to reduce memory consumption
	 * @return The memory manager registered with this solver
	 */
	public IMemoryManager<Abstraction, Unit> getMemoryManager();

	/**
	 * Sets whether abstractions on method returns shall be connected to the
	 * respective call abstractions to shortcut paths.
	 * @param setJumpPredecessors True if return abstractions shall be connected
	 * to call abstractions as predecessors, otherwise false.
	 */
	public void setJumpPredecessors(boolean setJumpPredecessors);
	
	/**
	 * Gets the number of edges propagated by the solver
	 * @return The number of edges propagated by the solver
	 */
	public long getPropagationCount();
	
	/**
	 * Solves the data flow problem
	 */
	public void solve();
	
	public void setSolverId(boolean solverId);
	
	/**
	 * Gets the IFDS problem solved by this solver
	 * @return The IFDS problem solved by this solver
	 */
	public AbstractInfoflowProblem getTabulationProblem();
	
	/**
	 * Sets whether only a single abstraction shall be recorded per join point.
	 * In other words, enabling this option disables the recording of neighbors.
	 * @param singleJoinPointAbstraction True to only record a single abstraction
	 * per join point, false to record all incoming neighbors
	 */
	public void setSingleJoinPointAbstraction(boolean singleJoinPointAbstraction);
	
}