/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.InfoflowConfiguration.DataFlowSolver;
import soot.jimple.infoflow.aliasing.*;
import soot.jimple.infoflow.cfg.BiDirICFGFactory;
import soot.jimple.infoflow.codeOptimization.DeadCodeEliminator;
import soot.jimple.infoflow.codeOptimization.ICodeOptimizer;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.data.FlowDroidMemoryManager.PathDataErasureMode;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder;
import soot.jimple.infoflow.data.pathBuilders.IAbstractionPathBuilder.OnPathBuilderResultAvailable;
import soot.jimple.infoflow.data.pathBuilders.IPathBuilderFactory;
import soot.jimple.infoflow.entryPointCreators.IEntryPointCreator;
import soot.jimple.infoflow.handlers.PostAnalysisHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler2;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.memory.FlowDroidMemoryWatcher;
import soot.jimple.infoflow.memory.FlowDroidTimeoutWatcher;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.problems.BackwardsInfoflowProblem;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.TaintPropagationResults.OnTaintPropagationResultAdded;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.BackwardsInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.solver.executors.SetPoolExecutor;
import soot.jimple.infoflow.solver.fastSolver.IFDSSolver;
import soot.jimple.infoflow.solver.memory.DefaultMemoryManagerFactory;
import soot.jimple.infoflow.solver.memory.IMemoryManager;
import soot.jimple.infoflow.solver.memory.IMemoryManagerFactory;
import soot.jimple.infoflow.source.IOneSourceAtATimeManager;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.DataFlowGraphQuery;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.InnerBBFastBuildDFGSolver;
import soot.jimple.infoflow.sparseOptimization.dataflowgraph.data.DataFlowNode;
import soot.jimple.infoflow.sparseOptimization.problem.BackwardsSparseInfoflowProblem;
import soot.jimple.infoflow.sparseOptimization.problem.SparseInfoflowProblem;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.options.Options;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * main infoflow class which triggers the analysis and offers method to
 * customize it.
 *
 */
public class Infoflow extends AbstractInfoflow {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private InfoflowResults results = null;
	private InfoflowManager manager;

	private Set<ResultsAvailableHandler> onResultsAvailable = new HashSet<ResultsAvailableHandler>();
	private TaintPropagationHandler taintPropagationHandler = null;
	private TaintPropagationHandler backwardsPropagationHandler = null;
	private IMemoryManagerFactory memoryManagerFactory = new DefaultMemoryManagerFactory();

	private long maxMemoryConsumption = -1;
	private FlowDroidMemoryWatcher memoryWatcher = null;

	private Set<Stmt> collectedSources = null;
	private Set<Stmt> collectedSinks = null;

	protected SootMethod dummyMainMethod = null;

	/**
	 * Creates a new instance of the InfoFlow class for analyzing plain Java
	 * code without any references to APKs or the Android SDK.
	 */
	public Infoflow() {
		super();
	}

	/**
	 * Creates a new instance of the Infoflow class for analyzing Android APK
	 * files.
	 * 
	 * @param androidPath
	 *            If forceAndroidJar is false, this is the base directory of the
	 *            platform files in the Android SDK. If forceAndroidJar is true,
	 *            this is the full path of a single android.jar file.
	 * @param forceAndroidJar
	 *            True if a single platform JAR file shall be forced, false if
	 *            Soot shall pick the appropriate platform version
	 */
	public Infoflow(String androidPath, boolean forceAndroidJar) {
		super(null, androidPath, forceAndroidJar);
		this.pathBuilderFactory = new DefaultPathBuilderFactory();
	}

	/**
	 * Creates a new instance of the Infoflow class for analyzing Android APK
	 * files.
	 * 
	 * @param androidPath
	 *            If forceAndroidJar is false, this is the base directory of the
	 *            platform files in the Android SDK. If forceAndroidJar is true,
	 *            this is the full path of a single android.jar file.
	 * @param forceAndroidJar
	 *            True if a single platform JAR file shall be forced, false if
	 *            Soot shall pick the appropriate platform version
	 * @param icfgFactory
	 *            The interprocedural CFG to be used by the InfoFlowProblem
	 * @param pathBuilderFactory
	 *            The factory class for constructing a path builder algorithm
	 */
	public Infoflow(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory,
			IPathBuilderFactory pathBuilderFactory) {
		super(icfgFactory, androidPath, forceAndroidJar);
		this.pathBuilderFactory = pathBuilderFactory == null ? new DefaultPathBuilderFactory() : pathBuilderFactory;
	}

	@Override
	public void computeInfoflow(String appPath, String libPath, IEntryPointCreator entryPointCreator,
			ISourceSinkManager sourcesSinks) {
		if (sourcesSinks == null) {
			logger.error("Sources are empty!");
			return;
		}

		initializeSoot(appPath, libPath, entryPointCreator.getRequiredClasses());

		// entryPoints are the entryPoints required by Soot to calculate Graph -
		// if there is no main method,
		// we have to create a new main method and use it as entryPoint and
		// store our real entryPoints
		this.dummyMainMethod = entryPointCreator.createDummyMain();
		Scene.v().setEntryPoints(Collections.singletonList(dummyMainMethod));

		// Run the analysis
		runAnalysis(sourcesSinks, null);
	}

	@Override
	public void computeInfoflow(String appPath, String libPath, String entryPoint, ISourceSinkManager sourcesSinks) {
		if (sourcesSinks == null) {
			logger.error("Sources are empty!");
			return;
		}

		initializeSoot(appPath, libPath, SootMethodRepresentationParser.v()
				.parseClassNames(Collections.singletonList(entryPoint), false).keySet(), entryPoint);

		if (!Scene.v().containsMethod(entryPoint)) {
			logger.error("Entry point not found: " + entryPoint);
			return;
		}
		SootMethod ep = Scene.v().getMethod(entryPoint);
		if (ep.isConcrete())
			ep.retrieveActiveBody();
		else {
			logger.debug("Skipping non-concrete method " + ep);
			return;
		}
		this.dummyMainMethod = null;
		Scene.v().setEntryPoints(Collections.singletonList(ep));
		Options.v().set_main_class(ep.getDeclaringClass().getName());

		// Compute the additional seeds if they are specified
		Set<String> seeds = Collections.emptySet();
		if (entryPoint != null && !entryPoint.isEmpty())
			seeds = Collections.singleton(entryPoint);
		ipcManager.updateJimpleForICC();

		// Run the analysis
		runAnalysis(sourcesSinks, seeds);
	}

	/**
	 * Releases the callgraph and all intermediate objects associated with it
	 */
	private void releaseCallgraph() {
		Scene.v().releaseCallGraph();
		Scene.v().releasePointsToAnalysis();
		Scene.v().releaseReachableMethods();
		G.v().resetSpark();
	}

	/**
	 * Conducts a taint analysis on an already initialized callgraph
	 * 
	 * @param sourcesSinks
	 *            The sources and sinks to be used
	 */
	protected void runAnalysis(final ISourceSinkManager sourcesSinks) {
		runAnalysis(sourcesSinks, null);
	}

	/**
	 * Conducts a taint analysis on an already initialized callgraph
	 * 
	 * @param sourcesSinks
	 *            The sources and sinks to be used
	 * @param additionalSeeds
	 *            Additional seeds at which to create A ZERO fact even if they
	 *            are not sources
	 */
	private void runAnalysis(final ISourceSinkManager sourcesSinks, final Set<String> additionalSeeds) {

		try {
			// Clear the data from previous runs
			maxMemoryConsumption = -1;
			// results = null;
			results = new InfoflowResults();

			// Print and check our configuration
			checkAndFixConfiguration();
			config.printSummary();

			// Register a memory watcher
			if (memoryWatcher != null) {
				memoryWatcher.clearSolvers();
				memoryWatcher = null;
			}
			memoryWatcher = new FlowDroidMemoryWatcher(results);

			// Build the callgraph
			long beforeCallgraph = System.nanoTime();
			constructCallgraph();
			logger.info("Callgraph construction took " + (System.nanoTime() - beforeCallgraph) / 1E9 + " seconds");

			// Initialize the source sink manager
			if (sourcesSinks != null)
				sourcesSinks.initialize();

			// Perform constant propagation and remove dead code
			if (config.getCodeEliminationMode() != CodeEliminationMode.NoCodeElimination) {
				long currentMillis = System.nanoTime();
				eliminateDeadCode(sourcesSinks);
				logger.info("Dead code elimination took " + (System.nanoTime() - currentMillis) / 1E9 + " seconds");
			}

			// After constant value propagation, we might find more call edges
			// for
			// reflective method calls
			if (config.getEnableReflection()) {
				releaseCallgraph();
				constructCallgraph();
			}

			if (config.getCallgraphAlgorithm() != CallgraphAlgorithm.OnDemand)
				logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());

			if (!config.isTaintAnalysisEnabled())
				return;

			logger.info("Starting Taint Analysis");
			IInfoflowCFG iCfg = icfgFactory.buildBiDirICFG(config.getCallgraphAlgorithm(),
					config.getEnableExceptionTracking());

			if(config.isSparseOptEnabled()) {
				if(config.getDataFlowSolver() != DataFlowSolver.ContextFlowSensitive)
					throw new RuntimeException("Sparse optimization just is support for contextFlowSensitive solver!");

				logger.info("Starting Data Flow Graph building!");
				InnerBBFastBuildDFGSolver dfgSolver = new InnerBBFastBuildDFGSolver(iCfg);
				long beforeDfgBuild = System.nanoTime();
				dfgSolver.solve();
				DataFlowGraphQuery.newInitialize(iCfg, dfgSolver.getNewDfg(), dfgSolver.getNewBackwardDfg(), dfgSolver.getUnitOrderComputingMap());
				dfgSolver.solveSummary();

				logger.info("Data Flow Graph building took " + (System.nanoTime() - beforeDfgBuild) / 1E9
						+ " seconds");
				logger.info("Data Flow Graph building memory consumption " + (getUsedMemory()) / 1E6
						+ " MB");
				//DataFlowGraphQuery.initialize(iCfg, dfgSolver.getDfg(), dfgSolver.getBackwardDfg(), dfgSolver.getUnitOrderComputingMap());

//				Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>>
//						dfg = dfgSolver.getDfg();
//				manager.setDfg(dfg);
//				manager.setUnitOrderComputingMap(dfgSolver.getUnitOrderComputingMap());

			}

			// Check whether we need to run with one source at a time
			IOneSourceAtATimeManager oneSourceAtATime = config.getOneSourceAtATime() && sourcesSinks != null
					&& sourcesSinks instanceof IOneSourceAtATimeManager ? (IOneSourceAtATimeManager) sourcesSinks
							: null;

			// Reset the current source
			if (oneSourceAtATime != null)
				oneSourceAtATime.resetCurrentSource();
			boolean hasMoreSources = oneSourceAtATime == null || oneSourceAtATime.hasNextSource();

			while (hasMoreSources) {
				// Fetch the next source
				if (oneSourceAtATime != null)
					oneSourceAtATime.nextSource();

				// Create the executor that takes care of the workers
				int numThreads = Runtime.getRuntime().availableProcessors();
				InterruptableExecutor executor = createExecutor(numThreads, true);
				logger.info("Total number of current running threads : " + executor.getCorePoolSize());


				// Initialize the memory manager
				IMemoryManager<Abstraction, Unit> memoryManager = createMemoryManager();

				// Initialize the data flow manager
				manager = new InfoflowManager(config, null, iCfg, sourcesSinks, taintWrapper, hierarchy,
						new AccessPathFactory(config));

				// Initialize the alias analysis
				IAliasingStrategy aliasingStrategy = createAliasAnalysis(sourcesSinks, iCfg, executor, memoryManager);

				// Get the zero fact
				Abstraction zeroValue = aliasingStrategy.getSolver() != null
						? aliasingStrategy.getSolver().getTabulationProblem().createZeroValue() : null;

				// Initialize the aliasing infrastructure
				Aliasing aliasing = new Aliasing(aliasingStrategy, manager);
				if (dummyMainMethod != null)
					aliasing.excludeMethodFromMustAlias(dummyMainMethod);

				// Initialize the data flow problem
				//InfoflowProblem forwardProblem = new InfoflowProblem(manager, aliasingStrategy, aliasing, zeroValue);
				AbstractInfoflowProblem forwardProblem = null;
				if(config.isSparseOptEnabled())
					forwardProblem = new SparseInfoflowProblem(manager, aliasingStrategy, aliasing, zeroValue);
				else
					forwardProblem = new InfoflowProblem(manager, aliasingStrategy, aliasing, zeroValue);

				// We need to create the right data flow solver
				IInfoflowSolver forwardSolver = createForwardSolver(executor, forwardProblem);

				// Set the options

				manager.setForwardSolver(forwardSolver);
				if (aliasingStrategy.getSolver() != null)
					aliasingStrategy.getSolver().getTabulationProblem().getManager().setForwardSolver(forwardSolver);


				memoryWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);

				forwardSolver.setMemoryManager(memoryManager);
				// forwardSolver.setEnableMergePointChecking(true);

				forwardProblem.setTaintPropagationHandler(taintPropagationHandler);
				forwardProblem.setTaintWrapper(taintWrapper);
				if (nativeCallHandler != null)
					forwardProblem.setNativeCallHandler(nativeCallHandler);

				if (aliasingStrategy.getSolver() != null) {
					aliasingStrategy.getSolver().getTabulationProblem().setActivationUnitsToCallSites(forwardProblem);
					if(config.isSparseOptEnabled()) {
						manager.setActivationUnitsToUnits(forwardProblem.getActivationUnitsToUnits());
						aliasingStrategy.getSolver().getTabulationProblem().getManager().setActivationUnitsToUnits(forwardProblem.getActivationUnitsToUnits());
					}
				}
				// Start a thread for enforcing the timeout
				FlowDroidTimeoutWatcher timeoutWatcher = null;
				FlowDroidTimeoutWatcher pathTimeoutWatcher = null;
				if (config.getDataFlowTimeout() > 0) {
					timeoutWatcher = new FlowDroidTimeoutWatcher(config.getDataFlowTimeout(), results);
					timeoutWatcher.addSolver((IMemoryBoundedSolver) forwardSolver);
					if (aliasingStrategy.getSolver() != null)
						timeoutWatcher.addSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
					timeoutWatcher.start();
				}

				try {
					// Print our configuration
					if (config.getFlowSensitiveAliasing() && !aliasingStrategy.isFlowSensitive())
						logger.warn("Trying to use a flow-sensitive aliasing with an "
								+ "aliasing strategy that does not support this feature");
					if (config.getFlowSensitiveAliasing() && config.getSingleJoinPointAbstraction())
						logger.warn("Running with a single join point abstraction can break context-"
								+ "sensitive path builders");

					// We have to look through the complete program to find
					// sources
					// which are then taken as seeds.
					int sinkCount = 0;
					logger.info("Looking for sources and sinks...");

					for (SootMethod sm : getMethodsForSeeds(iCfg))
						sinkCount += scanMethodForSourcesSinks(sourcesSinks, forwardProblem, sm);

					// We optionally also allow additional seeds to be specified
					if (additionalSeeds != null)
						for (String meth : additionalSeeds) {
							SootMethod m = Scene.v().getMethod(meth);
							if (!m.hasActiveBody()) {
								logger.warn("Seed method {} has no active body", m);
								continue;
							}
							forwardProblem.addInitialSeeds(m.getActiveBody().getUnits().getFirst(),
									Collections.singleton(forwardProblem.zeroValue()));
						}

					// Report on the sources and sinks we have found
					if (!forwardProblem.hasInitialSeeds()) {
						logger.error("No sources found, aborting analysis");
						continue;
					}
					if (sinkCount == 0) {
						logger.error("No sinks found, aborting analysis");
						continue;
					}
					logger.info("Source lookup done, found {} sources and {} sinks.",
							forwardProblem.getInitialSeeds().size(), sinkCount);

					// Initialize the taint wrapper if we have one
					if (taintWrapper != null)
						taintWrapper.initialize(manager);
					if (nativeCallHandler != null)
						nativeCallHandler.initialize(manager);

					// Register the handler for interim results
					TaintPropagationResults propagationResults = forwardProblem.getResults();
					final InterruptableExecutor resultExecutor = createExecutor(numThreads, false);
					final IAbstractionPathBuilder builder = pathBuilderFactory.createPathBuilder(config, resultExecutor,
							iCfg);

					// If we want incremental result reporting, we have to
					// initialize
					// it before we start the taint tracking
					if (config.getIncrementalResultReporting())
						initializeIncrementalResultReporting(propagationResults, builder);

					long beforeFsolver = System.nanoTime();
					forwardSolver.solve();
					logger.info("Taint OPfSolver took " + (System.nanoTime() - beforeFsolver) / 1E9
							+ " seconds");
					logger.info("Hash DataFlowGraphQuery took: " + DataFlowGraphQuery.count / 1E9);
					logger.info("Set usedef took: " + DataFlowNode.count / 1E9);
					logger.info("Forward Normal took: " + SparseInfoflowProblem.countNormal1 / 1E9);
					logger.info("Forward Normal2 took: " + SparseInfoflowProblem.countNormal2 / 1E9);
					logger.info("Forward Normal3 took: " + SparseInfoflowProblem.countNormal3 / 1E9);
					logger.info("Backward Normal1 took: " + BackwardsSparseInfoflowProblem.countNormal1 / 1E9);
					logger.info("Backward Normal2 took: " + BackwardsSparseInfoflowProblem.countNormal2 / 1E9);
					logger.info("IFDS Normal took: " + IFDSSolver.countNormal / 1E9);
					logger.info("IFDS Call took: " + IFDSSolver.countCall / 1E9);
					logger.info("IFDS Exit took: " + IFDSSolver.countExit / 1E9);

					maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());

					// Not really nice, but sometimes Heros returns before all
					// executor tasks are actually done. This way, we give it a
					// chance to terminate gracefully before moving on.
					int terminateTries = 0;
					while (terminateTries < 10) {
						if (executor.getActiveCount() != 0 || !executor.isTerminated()) {
							terminateTries++;
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								logger.error("Could not wait for executor termination", e);
							}
						} else
							break;
					}
					if (executor.getActiveCount() != 0 || !executor.isTerminated())
						logger.error("Executor did not terminate gracefully");

					// Print taint wrapper statistics
					if (taintWrapper != null) {
						logger.info("Taint wrapper hits: " + taintWrapper.getWrapperHits());
						logger.info("Taint wrapper misses: " + taintWrapper.getWrapperMisses());
					}

					// Get the result abstractions
					Set<AbstractionAtSink> res = propagationResults.getResults();
					propagationResults = null;

					// We need to prune access paths that are entailed by
					// another one
					removeEntailedAbstractions(res);

					// Shut down the native call handler
					if (nativeCallHandler != null)
						nativeCallHandler.shutdown();

					logger.info(
							"IFDS problem with {} forward and {} backward edges solved, " + "processing {} results...",
							forwardSolver.getPropagationCount(), aliasingStrategy.getSolver() == null ? 0
									: aliasingStrategy.getSolver().getPropagationCount(),
							res == null ? 0 : res.size());

					// Force a cleanup. Everything we need is reachable through
					// the
					// results set, the other abstractions can be killed now.
					maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());
					logger.info("Current memory consumption: " + (getUsedMemory() / 1000 / 1000) + " MB");

					if (timeoutWatcher != null)
						timeoutWatcher.stop();
					memoryWatcher.removeSolver((IMemoryBoundedSolver) forwardSolver);
					forwardSolver.cleanup();
					forwardSolver = null;
					forwardProblem = null;

					// Remove the alias analysis from memory
					aliasing = null;
					if (aliasingStrategy.getSolver() != null)
						memoryWatcher.removeSolver((IMemoryBoundedSolver) aliasingStrategy.getSolver());
					aliasingStrategy.cleanup();
					aliasingStrategy = null;

					if (config.getIncrementalResultReporting())
						res = null;
					iCfg.purge();
					manager = null;

					Runtime.getRuntime().gc();
					logger.info("Memory consumption after cleanup: " + (getUsedMemory() / 1000 / 1000) + " MB");

					// Apply the timeout to path reconstruction
					if (config.getPathReconstructionTimeout() > 0) {
						pathTimeoutWatcher = new FlowDroidTimeoutWatcher(config.getPathReconstructionTimeout(),
								results);
						pathTimeoutWatcher.addSolver(builder);
						pathTimeoutWatcher.start();
					}

					// Do the normal result computation in the end unless we
					// have used
					// incremental path building
					if (config.getIncrementalResultReporting()) {
						// After the last intermediate result has been computed,
						// we need to
						// re-process those abstractions that received new
						// neighbors in the
						// meantime
						builder.runIncrementalPathCompuation();

						try {
							resultExecutor.awaitCompletion();
						} catch (InterruptedException e) {
							logger.error("Could not wait for executor termination", e);
						}
					} else {
						memoryWatcher.addSolver(builder);
						builder.computeTaintPaths(res);
						res = null;

						// Wait for the path builders to terminate
						try {
							resultExecutor.awaitCompletion();
						} catch (InterruptedException e) {
							logger.error("Could not wait for executor termination", e);
						}

						// Get the results once the path builder is done
						if (this.results == null)
							this.results = builder.getResults();
						else
							this.results.addAll(builder.getResults());
					}
					resultExecutor.shutdown();

					// If the path builder was aborted, we warn the user
					if (builder.isKilled())
						logger.warn("Path reconstruction aborted. The reported results may be incomplete. "
								+ "You might want to try again with sequential path processing enabled.");
				} finally {
					// Make sure to stop the watcher thread
					if (timeoutWatcher != null)
						timeoutWatcher.stop();
					if (pathTimeoutWatcher != null)
						pathTimeoutWatcher.stop();

					// Do we have any more sources?
					hasMoreSources = oneSourceAtATime != null && oneSourceAtATime.hasNextSource();

					// Shut down the memory watcher
					memoryWatcher.close();

					// Get rid of all the stuff that's still floating around in
					// memory
					forwardProblem = null;
					forwardSolver = null;
					manager = null;
				}

				// Make sure that we are in a sensible state even if we ran out
				// of memory before
				Runtime.getRuntime().gc();
				logger.info("Memory consumption after path building: " + (getUsedMemory() / 1000 / 1000) + " MB");
			}

			// Execute the post-processors
			for (PostAnalysisHandler handler : this.postProcessors)
				results = handler.onResultsAvailable(results, iCfg);

			if (results == null || results.getResults().isEmpty())
				logger.warn("No results found.");
			else
				for (ResultSinkInfo sink : results.getResults().keySet()) {
					logger.info("The sink {} in method {} was called with values from the following sources:", sink,
							iCfg.getMethodOf(sink.getSink()).getSignature());
					for (ResultSourceInfo source : results.getResults().get(sink)) {
						logger.info("- {} in method {}", source, iCfg.getMethodOf(source.getSource()).getSignature());
						if (source.getPath() != null) {
							logger.info("\ton Path: ");
							for (Unit p : source.getPath()) {
								logger.info("\t -> " + iCfg.getMethodOf(p));
								logger.info("\t\t -> " + p);
							}
						}
					}
				}

			// Provide the handler with the final results
			for (ResultsAvailableHandler handler : onResultsAvailable)
				handler.onResultsAvailable(iCfg, results);

			// Write the Jimple files to disk if requested
			if (config.getWriteOutputFiles())
				PackManager.v().writeOutput();

			maxMemoryConsumption = Math.max(maxMemoryConsumption, getUsedMemory());
			System.out.println("Maximum memory consumption: " + maxMemoryConsumption / 1E6 + " MB");
		} catch (Exception ex) {
			results.addException(ex.getClass().getName() + ": " + ex.getMessage());
		}
	}

	/**
	 * Initializes the mechanism for incremental result reporting
	 * 
	 * @param propagationResults
	 *            A reference to the result object of the forward data flow
	 *            solver
	 * @param builder
	 *            The path builder to use for reconstructing the taint
	 *            propagation paths
	 */
	private void initializeIncrementalResultReporting(TaintPropagationResults propagationResults,
			final IAbstractionPathBuilder builder) {
		// Create the path builder
		memoryWatcher.addSolver(builder);
		this.results = new InfoflowResults();
		propagationResults.addResultAvailableHandler(new OnTaintPropagationResultAdded() {

			@Override
			public boolean onResultAvailable(AbstractionAtSink abs) {
				builder.addResultAvailableHandler(new OnPathBuilderResultAvailable() {

					@Override
					public void onResultAvailable(ResultSourceInfo source, ResultSinkInfo sink) {
						// Notify our external handlers
						for (ResultsAvailableHandler handler : onResultsAvailable) {
							if (handler instanceof ResultsAvailableHandler2) {
								ResultsAvailableHandler2 handler2 = (ResultsAvailableHandler2) handler;
								handler2.onSingleResultAvailable(source, sink);
							}
						}
						results.addResult(sink, source);
					}

				});

				// Compute the result paths
				builder.computeTaintPaths(Collections.singleton(abs));
				return true;
			}

		});
	}

	/**
	 * Checks the configuration of the data flow solver for errors and
	 * automatically fixes some common issues
	 */
	private void checkAndFixConfiguration() {
		if (config.getEnableStaticFieldTracking() && config.getAccessPathLength() == 0)
			throw new RuntimeException("Static field tracking must be disabled " + "if the access path length is zero");
		if (config.getAccessPathLength() < 0)
			throw new RuntimeException("The access path length may not be negative");
		if (config.getDataFlowSolver() == DataFlowSolver.FlowInsensitive) {
			config.setFlowSensitiveAliasing(false);
			config.setEnableTypeChecking(false);
			logger.warn("Disabled flow-sensitive aliasing because we are running with "
					+ "a flow-insensitive data flow solver");
		}
	}

	/**
	 * Removes all abstractions from the given set that arrive at the same sink
	 * statement as another abstraction, but cover less tainted variables. If,
	 * e.g., a.b.* and a.* arrive at the same sink, a.b.* is already covered by
	 * a.* and can thus safely be removed.
	 * 
	 * @param res
	 *            The result set from which to remove all entailed abstractions
	 */
	private void removeEntailedAbstractions(Set<AbstractionAtSink> res) {
		for (Iterator<AbstractionAtSink> absAtSinkIt = res.iterator(); absAtSinkIt.hasNext();) {
			AbstractionAtSink curAbs = absAtSinkIt.next();
			for (AbstractionAtSink checkAbs : res)
				if (checkAbs != curAbs && checkAbs.getSinkStmt() == curAbs.getSinkStmt()
						&& checkAbs.getAbstraction().isImplicit() == curAbs.getAbstraction().isImplicit()
						&& checkAbs.getAbstraction().getSourceContext() == curAbs.getAbstraction().getSourceContext())
					if (checkAbs.getAbstraction().getAccessPath().entails(curAbs.getAbstraction().getAccessPath())) {
						absAtSinkIt.remove();
						break;
					}
		}
	}

	/**
	 * Initializes the alias analysis
	 * 
	 * @param sourcesSinks
	 *            The set of sources and sinks
	 * @param iCfg
	 *            The interprocedural control flow graph
	 * @param executor
	 *            The executor in which to run concurrent tasks
	 * @param memoryManager
	 *            The memory manager for rducing the memory load during IFDS
	 *            propagation
	 * @return The alias analysis implementation to use for the data flow
	 *         analysis
	 */
	@SuppressWarnings("deprecation")
	private IAliasingStrategy createAliasAnalysis(final ISourceSinkManager sourcesSinks, IInfoflowCFG iCfg,
			InterruptableExecutor executor, IMemoryManager<Abstraction, Unit> memoryManager) {
		IAliasingStrategy aliasingStrategy;
		IInfoflowSolver backSolver = null;
		BackwardsInfoflowProblem backProblem = null;
		InfoflowManager backwardsManager = null;
		switch (getConfig().getAliasingAlgorithm()) {
		case FlowSensitive:
			backwardsManager = new InfoflowManager(config, null, new BackwardsInfoflowCFG(iCfg), sourcesSinks,
					taintWrapper, hierarchy, manager.getAccessPathFactory());
			backProblem = new BackwardsInfoflowProblem(backwardsManager);
			// We need to create the right data flow solver
			switch (config.getDataFlowSolver()) {
			case Heros:
				backSolver = new soot.jimple.infoflow.solver.heros.InfoflowSolver(backProblem, executor);
				break;
			case ContextFlowSensitive:
				if(config.isSparseOptEnabled())
					backSolver = new soot.jimple.infoflow.sparseOptimization.solver.BackwardsInfoflowSparseSolver(
							new BackwardsSparseInfoflowProblem(backwardsManager), executor);
				else
					backSolver = new soot.jimple.infoflow.solver.fastSolver.InfoflowSolver(backProblem, executor);

				break;
			case FlowInsensitive:
				backSolver = new soot.jimple.infoflow.solver.fastSolver.flowInsensitive.InfoflowSolver(backProblem,
						executor);
				break;
			default:
				throw new RuntimeException("Unsupported data flow solver");
			}

			backSolver.setMemoryManager(memoryManager);
			backSolver.setJumpPredecessors(!pathBuilderFactory.supportsPathReconstruction());
			// backSolver.setEnableMergePointChecking(true);
			backSolver.setSingleJoinPointAbstraction(config.getSingleJoinPointAbstraction());
			backSolver.setSolverId(false);
			backProblem.setTaintPropagationHandler(backwardsPropagationHandler);
			backProblem.setTaintWrapper(taintWrapper);
			if (nativeCallHandler != null)
				backProblem.setNativeCallHandler(nativeCallHandler);

			memoryWatcher.addSolver((IMemoryBoundedSolver) backSolver);

			aliasingStrategy = new FlowSensitiveAliasStrategy(manager, backSolver);
			break;
		case PtsBased:
			backProblem = null;
			backSolver = null;
			aliasingStrategy = new PtsBasedAliasStrategy(manager);
			break;
		case None:
			backProblem = null;
			backSolver = null;
			aliasingStrategy = new NullAliasStrategy();
			break;
		case Lazy:
			backProblem = null;
			backSolver = null;
			aliasingStrategy = new LazyAliasingStrategy(manager);
			break;
		default:
			throw new RuntimeException("Unsupported aliasing algorithm");
		}
		return aliasingStrategy;
	}

	/**
	 * Creates the memory manager that helps reduce the memory consumption of
	 * the data flow analysis
	 * 
	 * @return The memory manager object
	 */
	private IMemoryManager<Abstraction, Unit> createMemoryManager() {
		PathDataErasureMode erasureMode = PathDataErasureMode.EraseAll;
		if (pathBuilderFactory.isContextSensitive())
			erasureMode = PathDataErasureMode.KeepOnlyContextData;
		if (pathBuilderFactory.supportsPathReconstruction())
			erasureMode = PathDataErasureMode.EraseNothing;
		IMemoryManager<Abstraction, Unit> memoryManager = memoryManagerFactory.getMemoryManager(false, erasureMode);
		return memoryManager;
	}

	/**
	 * Creates the IFDS solver for the forward data flow problem
	 * 
	 * @param executor
	 *            The executor in which to run the tasks or propagating IFDS
	 *            edges
	 * @param forwardProblem
	 *            The implementation of the forward problem
	 * @return The solver that solves the forward taint analysis problem
	 */
	@SuppressWarnings("deprecation")
	private IInfoflowSolver createForwardSolver(InterruptableExecutor executor, AbstractInfoflowProblem forwardProblem) {
		// Depending on the configured solver algorithm, we have to create a
		// different solver object
		IInfoflowSolver forwardSolver;
		switch (config.getDataFlowSolver()) {
		case Heros:
			logger.info("Using legacy Heros-based data flow solver");
			forwardSolver = new soot.jimple.infoflow.solver.heros.InfoflowSolver(forwardProblem, executor);
			break;
		case ContextFlowSensitive:
			logger.info("Using context- and flow-sensitive solver");
			if(config.isSparseOptEnabled()) {
				logger.info("Using sparse infoflow solver");
				forwardSolver = new soot.jimple.infoflow.sparseOptimization.solver.InfoflowSparseSolver(forwardProblem, executor);
			}
			else
				forwardSolver = new soot.jimple.infoflow.solver.fastSolver.InfoflowSolver(forwardProblem, executor);

			break;
		case FlowInsensitive:
			logger.info("Using context-sensitive, but flow-insensitive solver");
			forwardSolver = new soot.jimple.infoflow.solver.fastSolver.flowInsensitive.InfoflowSolver(forwardProblem,
					executor);
			break;
		default:
			throw new RuntimeException("Unsupported data flow solver");
		}

		// Configure the solver
		forwardSolver.setSolverId(true);
		forwardSolver.setJumpPredecessors(!pathBuilderFactory.supportsPathReconstruction());
		forwardSolver.setSingleJoinPointAbstraction(config.getSingleJoinPointAbstraction());

		return forwardSolver;
	}

	/**
	 * Gets the memory used by FlowDroid at the moment
	 * 
	 * @return FlowDroid's current memory consumption in bytes
	 */
	private long getUsedMemory() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	/**
	 * Runs all code optimizers
	 * 
	 * @param sourcesSinks
	 *            The SourceSinkManager
	 */
	private void eliminateDeadCode(ISourceSinkManager sourcesSinks) {
		InfoflowManager dceManager = new InfoflowManager(config, null, new InfoflowCFG(), null, null, null,
				new AccessPathFactory(config));

		ICodeOptimizer dce = new DeadCodeEliminator();
		dce.initialize(config);
		dce.run(dceManager, Scene.v().getEntryPoints(), sourcesSinks, taintWrapper);
	}

	/**
	 * Creates a new executor object for spawning worker threads
	 * 
	 * @param numThreads
	 *            The number of threads to use
	 * @param allowSetSemantics
	 *            True if the executor shall have thread semantics, i.e., never
	 *            schedule the same task twice
	 * @return The generated executor
	 */
	private InterruptableExecutor createExecutor(int numThreads, boolean allowSetSemantics) {
		if (allowSetSemantics) {
			return new SetPoolExecutor(
					config.getMaxThreadNum() == -1 ? numThreads : Math.min(config.getMaxThreadNum(), numThreads),
					Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		} else {
			return new InterruptableExecutor(
					config.getMaxThreadNum() == -1 ? numThreads : Math.min(config.getMaxThreadNum(), numThreads),
					Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		}
	}

	private Collection<SootMethod> getMethodsForSeeds(IInfoflowCFG icfg) {
		List<SootMethod> seeds = new LinkedList<SootMethod>();
		// If we have a callgraph, we retrieve the reachable methods. Otherwise,
		// we have no choice but take all application methods as an
		// approximation
		if (Scene.v().hasCallGraph()) {
			List<MethodOrMethodContext> eps = new ArrayList<MethodOrMethodContext>(Scene.v().getEntryPoints());
			ReachableMethods reachableMethods = new ReachableMethods(Scene.v().getCallGraph(), eps.iterator(), null);
			reachableMethods.update();
			for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext();) {
				SootMethod sm = iter.next().method();
				if (isValidSeedMethod(sm))
					seeds.add(sm);
			}
		} else {
			long beforeSeedMethods = System.nanoTime();
			Set<SootMethod> doneSet = new HashSet<SootMethod>();
			for (SootMethod sm : Scene.v().getEntryPoints())
				getMethodsForSeedsIncremental(sm, doneSet, seeds, icfg);
			logger.info("Collecting seed methods took {} seconds", (System.nanoTime() - beforeSeedMethods) / 1E9);
		}
		return seeds;
	}

	private void getMethodsForSeedsIncremental(SootMethod sm, Set<SootMethod> doneSet, List<SootMethod> seeds,
			IInfoflowCFG icfg) {
		assert Scene.v().hasFastHierarchy();
		if (!sm.isConcrete() || !sm.getDeclaringClass().isApplicationClass() || !doneSet.add(sm))
			return;
		seeds.add(sm);
		for (Unit u : sm.retrieveActiveBody().getUnits()) {
			Stmt stmt = (Stmt) u;
			if (stmt.containsInvokeExpr())
				for (SootMethod callee : icfg.getCalleesOfCallAt(stmt))
					if (isValidSeedMethod(callee))
						getMethodsForSeedsIncremental(callee, doneSet, seeds, icfg);
		}
	}

	/**
	 * Gets whether the given method is a valid seen when scanning for sources
	 * and sinks. A method is a valid seed it it (or one of its transitive
	 * callees) can contain calls to source or sink methods.
	 * 
	 * @param sm
	 *            The method to check
	 * @return True if this method or one of its transitive callees can contain
	 *         sources or sinks, otherwise false
	 */
	private boolean isValidSeedMethod(SootMethod sm) {
		// Exclude system classes
		if (config.getIgnoreFlowsInSystemPackages()
				&& SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName()))
			return false;

		// Exclude library classes
		if (config.getExcludeSootLibraryClasses() && sm.getDeclaringClass().isLibraryClass())
			return false;

		return true;
	}

	/**
	 * Scans the given method for sources and sinks contained in it. Sinks are
	 * just counted, sources are added to the InfoflowProblem as seeds.
	 * 
	 * @param sourcesSinks
	 *            The SourceSinkManager to be used for identifying sources and
	 *            sinks
	 * @param forwardProblem
	 *            The InfoflowProblem in which to register the sources as seeds
	 * @param m
	 *            The method to scan for sources and sinks
	 * @return The number of sinks found in this method
	 */
	private int scanMethodForSourcesSinks(final ISourceSinkManager sourcesSinks, AbstractInfoflowProblem forwardProblem,
			SootMethod m) {
		if (getConfig().getLogSourcesAndSinks() && collectedSources == null) {
			collectedSources = new HashSet<>();
			collectedSinks = new HashSet<>();
		}

		int sinkCount = 0;
		if (m.hasActiveBody()) {
			// Check whether this is a system class we need to ignore
			if (!isValidSeedMethod(m))
				return sinkCount;

			// Look for a source in the method. Also look for sinks. If we
			// have no sink in the program, we don't need to perform any
			// analysis
			PatchingChain<Unit> units = m.getActiveBody().getUnits();
			for (Unit u : units) {
				Stmt s = (Stmt) u;
				if (sourcesSinks.getSourceInfo(s, manager) != null) {
					forwardProblem.addInitialSeeds(u, Collections.singleton(forwardProblem.zeroValue()));
					if (getConfig().getLogSourcesAndSinks())
						collectedSources.add(s);
					logger.debug("Source found: {}", u);
				}
				if (sourcesSinks.isSink(s, manager, null)) {
					sinkCount++;
					if (getConfig().getLogSourcesAndSinks())
						collectedSinks.add(s);
					logger.debug("Sink found: {}", u);
				}
			}

		}
		return sinkCount;
	}

	@Override
	public InfoflowResults getResults() {
		return results;
	}

	@Override
	public boolean isResultAvailable() {
		if (results == null) {
			return false;
		}
		return true;
	}

	/**
	 * Adds a handler that is called when information flow results are available
	 * 
	 * @param handler
	 *            The handler to add
	 */
	public void addResultsAvailableHandler(ResultsAvailableHandler handler) {
		this.onResultsAvailable.add(handler);
	}

	/**
	 * Sets a handler which is invoked whenever a taint is propagated
	 * 
	 * @param handler
	 *            The handler to be invoked when propagating taints
	 */
	public void setTaintPropagationHandler(TaintPropagationHandler handler) {
		this.taintPropagationHandler = handler;
	}

	/**
	 * Sets a handler which is invoked whenever an alias is propagated backwards
	 * 
	 * @param handler
	 *            The handler to be invoked when propagating aliases
	 */
	public void setBackwardsPropagationHandler(TaintPropagationHandler handler) {
		this.backwardsPropagationHandler = handler;
	}

	/**
	 * Removes a handler that is called when information flow results are
	 * available
	 * 
	 * @param handler
	 *            The handler to remove
	 */
	public void removeResultsAvailableHandler(ResultsAvailableHandler handler) {
		onResultsAvailable.remove(handler);
	}

	/**
	 * Gets the maximum memory consumption during the last analysis run
	 * 
	 * @return The maximum memory consumption during the last analysis run if
	 *         available, otherwise -1
	 */
	public long getMaxMemoryConsumption() {
		return this.maxMemoryConsumption;
	}

	/**
	 * Gets the concrete set of sources that have been collected in preparation
	 * for the taint analysis. This method will return null if source and sink
	 * logging has not been enabled (see InfoflowConfiguration.
	 * setLogSourcesAndSinks()),
	 * 
	 * @return The set of sources collected for taint analysis
	 */
	public Set<Stmt> getCollectedSources() {
		return this.collectedSources;
	}

	/**
	 * Gets the concrete set of sinks that have been collected in preparation
	 * for the taint analysis. This method will return null if source and sink
	 * logging has not been enabled (see InfoflowConfiguration.
	 * setLogSourcesAndSinks()),
	 * 
	 * @return The set of sinks collected for taint analysis
	 */
	public Set<Stmt> getCollectedSinks() {
		return this.collectedSinks;
	}

	/**
	 * Sets the factory to be used for creating memory managers
	 * 
	 * @param factory
	 *            The memory manager factory to use
	 */
	public void setMemoryManagerFactory(IMemoryManagerFactory factory) {
		this.memoryManagerFactory = factory;
	}


	/**
	 * 仅供内部测试使用的soot 入口，发布之前请删除。
	 *
	 */

	void computInfoflowForTest(String appPath, String libPath, Collection<String> classes, ISourceSinkManager sourcesSinks) {
		// 1 Config soot: load classes...
		initializeSootForTest(appPath,libPath, classes);

		// 2.1 Build the callgraph
		long beforeCallgraph = System.nanoTime();
		constructSimpleCallGraphForTest();
		logger.info("Callgraph construction took " + (System.nanoTime() - beforeCallgraph) / 1E9
				+ " seconds");
		logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());

		logger.info("Starting Taint Analysis");

//		// 2.2 Construct interprocedural CFG
//		BiDiInterproceduralCFG<Unit, SootMethod> baseICFG  = new JimpleBasedInterproceduralCFG(true);
//		iCfg = new InfoflowCFG(baseICFG) ;

		runAnalysis(sourcesSinks , null ) ;
	}

	void  initializeSootForTest(String appPath, String libPath , Collection<String> classes ) {
		logger.info("Resetting Soot...");
		soot.G.reset();

		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);

		Options.v().set_output_format(Options.output_format_none);
		//String path1 = "/Users/wanglei/Downloads/examples/call_graph/src/";
		// Options.v().set_soot_classpath(libPath);

		List<String> processDirs = new LinkedList<String>();
		processDirs.add(appPath);
		logger.info("prcocess dir :" + appPath);
		Options.v().set_process_dir(processDirs);

		Options.v().setPhaseOption("jb.ulp", "off");

		Options.v().set_src_prec(Options.src_prec_java);

		Options.v().set_whole_program(true);
		Options.v().setPhaseOption("cg", "trim-clinit:false");

		Options.v().setPhaseOption("cg.cha", "on");

		// load all entryPoint classes with their bodies
		for (String className : classes)
			Scene.v().addBasicClass(className, SootClass.BODIES);

		Scene.v().loadNecessaryClasses();
		logger.info("Basic class loading done.");

		boolean hasClasses = false;
		for (String className : classes) {
			SootClass c = Scene.v().forceResolve(className, SootClass.BODIES);
			if (c != null){
				c.setApplicationClass();
				if(!c.isPhantomClass() && !c.isPhantom())
					hasClasses = true;
			}
		}
		if (!hasClasses) {
			System.out.println("Only phantom classes loaded, skipping analysis...");
			return;
		}
		logger.info("InitializeSoot phase done.");

	}

	void constructSimpleCallGraphForTest() {

		PackManager.v().getPack("wjpp").apply();
		PackManager.v().getPack("cg").apply();
		//CallGraph cg = Scene.v().getCallGraph();

		hierarchy = Scene.v().getOrMakeFastHierarchy();

	}


}
