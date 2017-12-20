/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.android.source;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import heros.solver.IDESolver;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition.CallbackType;
import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.ResPackage;
import soot.jimple.infoflow.android.resources.LayoutControl;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.entryPointCreators.AndroidEntryPointUtils;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.source.IOneSourceAtATimeManager;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.scalar.ConstantPropagatorAndFolder;
import soot.tagkit.IntegerConstantValueTag;
import soot.tagkit.Tag;

/**
 * SourceManager implementation for AndroidSources
 * 
 * @author Steven Arzt
 */
public class AndroidSourceSinkManager implements ISourceSinkManager, IOneSourceAtATimeManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 * Possible modes for matching layout components as data flow sources
	 * 
	 * @author Steven Arzt
	 */
	public enum LayoutMatchingMode {
		/**
		 * Do not use Android layout components as sources
		 */
		NoMatch,

		/**
		 * Use all layout components as sources
		 */
		MatchAll,

		/**
		 * Only use sensitive layout components (e.g. password fields) as
		 * sources
		 */
		MatchSensitiveOnly
	}

	/**
	 * Types of sources supported by this SourceSinkManager
	 * 
	 * @author Steven Arzt
	 */
	public enum SourceType {
		/**
		 * Not a source
		 */
		NoSource,
		/**
		 * The data is obtained via a method call
		 */
		MethodCall,
		/**
		 * The data is retrieved through a callback parameter
		 */
		Callback,
		/**
		 * The data is read from a UI element
		 */
		UISource
	}

	protected final static String Activity_FindViewById = "<android.app.Activity: android.view.View findViewById(int)>";
	protected final static String View_FindViewById = "<android.app.View: android.view.View findViewById(int)>";
	
	protected SootMethod smActivityFindViewById;
	protected SootMethod smViewFindViewById;

	protected Map<String, SourceSinkDefinition> sourceDefs;
	protected Map<String, SourceSinkDefinition> sinkDefs;
	
	protected Map<SootMethod, SourceSinkDefinition> sourceMethods;
	protected Map<SootMethod, SourceSinkDefinition> sinkMethods;
	private Map<SootMethod, CallbackDefinition> callbackMethods;

	protected final LayoutMatchingMode layoutMatching;
	protected final Map<Integer, LayoutControl> layoutControls;
	protected List<ARSCFileParser.ResPackage> resourcePackages;

	protected String appPackageName = "";
	protected boolean enableCallbackSources = true;
	protected boolean enableLifecycleSources = false;

	protected final Set<SootMethod> analyzedLayoutMethods = new HashSet<SootMethod>();
	protected SootClass[] iccBaseClasses = null;
	protected AndroidEntryPointUtils entryPointUtils = new AndroidEntryPointUtils();
	
	protected boolean oneSourceAtATime = false;
	protected SourceType osaatType = SourceType.MethodCall;
	protected Iterator<SootMethod> osaatIterator = null;
	protected SootMethod currentSource = null;
	
	protected final LoadingCache<SootClass, Collection<SootClass>> interfacesOf =
			IDESolver.DEFAULT_CACHE_BUILDER.build(new CacheLoader<SootClass, Collection<SootClass>>() {
				
		@Override
		public Collection<SootClass> load(SootClass sc) throws Exception {
			Set<SootClass> set = new HashSet<SootClass>(sc.getInterfaceCount());
			for (SootClass i : sc.getInterfaces()) {
				set.add(i);
				set.addAll(interfacesOf.getUnchecked(i));
			}
			if (sc.hasSuperclass())
				set.addAll(interfacesOf.getUnchecked(sc.getSuperclass()));
			return set;
		}
		
	});
	
	/**
	 * Creates a new instance of the {@link AndroidSourceSinkManager} class with
	 * either strong or weak matching.
	 * 
	 * @param sources
	 *            The list of source methods
	 * @param sinks
	 *            The list of sink methods
	 */
	public AndroidSourceSinkManager(Set<SourceSinkDefinition> sources,
			Set<SourceSinkDefinition> sinks) {
		this(sources, sinks, Collections.<CallbackDefinition>emptySet(),
				LayoutMatchingMode.NoMatch, null);
	}

	/**
	 * Creates a new instance of the {@link AndroidSourceSinkManager} class with
	 * strong matching, i.e. the methods in the code must exactly match those in
	 * the list.
	 * 
	 * @param sources
	 *            The list of source methods
	 * @param sinks
	 *            The list of sink methods
	 * @param callbackMethods
	 *            The list of callback methods whose parameters are sources
	 *            through which the application receives data from the operating
	 *            system
	 * @param weakMatching
	 *            True for weak matching: If an entry in the list has no return
	 *            type, it matches arbitrary return types if the rest of the
	 *            method signature is compatible. False for strong matching: The
	 *            method signature in the code exactly match the one in the
	 *            list.
	 * @param layoutMatching
	 *            Specifies whether and how to use Android layout components as
	 *            sources for the information flow analysis
	 * @param layoutControls
	 *            A map from reference identifiers to the respective Android
	 *            layout controls
	 */
	public AndroidSourceSinkManager(Set<SourceSinkDefinition> sources,
			Set<SourceSinkDefinition> sinks,
			Set<CallbackDefinition> callbackMethods,
			LayoutMatchingMode layoutMatching,
			Map<Integer, LayoutControl> layoutControls) {
		this.sourceDefs = new HashMap<>();
		for (SourceSinkDefinition am : sources)
			this.sourceDefs.put(am.getMethod().getSignature(), am);

		this.sinkDefs = new HashMap<>();
		for (SourceSinkDefinition am : sinks)
			this.sinkDefs.put(am.getMethod().getSignature(), am);

		this.callbackMethods = new HashMap<>();
		for (CallbackDefinition cb : callbackMethods)
			this.callbackMethods.put(cb.getTargetMethod(), cb);

		this.layoutMatching = layoutMatching;
		this.layoutControls = layoutControls;

		System.out.println("Created a SourceSinkManager with " + this.sourceDefs.size()
				+ " sources, " + this.sinkDefs.size() + " sinks, and "
				+ this.callbackMethods.size() + " callback methods.");
	}

	/**
	 * Sets whether callback parameters shall be considered as sources
	 * 
	 * @param enableCallbackSources
	 *            True if callback parameters shall be considered as sources,
	 *            otherwise false
	 */
	public void setEnableCallbackSources(boolean enableCallbackSources) {
		this.enableCallbackSources = enableCallbackSources;
	}
	
	/**
	 * Sets whether the parameters of lifecycle methods shall be treated as sources
	 * @param enableLifecycleSources True if parameters of lifecycle methods shall
	 * be treated as sources, otherwise false
	 */
	public void setEnableLifecycleSources(boolean enableLifecycleSources) {
		this.enableLifecycleSources = enableLifecycleSources;
	}
	
	/**
	 * Gets the sink definition for the given call site and tainted access path
	 * @param sCallSite The call site
	 * @param manager The manager object providing access to the configuration
	 * and the interprocedural control flow graph
	 * @param ap The incoming tainted access path
	 * @return The sink definition of the method that is called at the given
	 * call site if such a definition exists, otherwise null
	 */
	protected SourceSinkDefinition getSinkDefinition(Stmt sCallSite,
			InfoflowManager manager, AccessPath ap) {		
		if (!sCallSite.containsInvokeExpr())
			return null;
		
		// Check whether the taint is even visible inside the callee
		final SootMethod callee = sCallSite.getInvokeExpr().getMethod();
		if (!SystemClassHandler.isTaintVisible(ap, callee))
			return null;
		
		// Do we have a direct hit?
		{
		SourceSinkDefinition def = this.sinkMethods.get(sCallSite.getInvokeExpr().getMethod());
		if (def != null)
			return def;
		}
		
		final SootClass sc = callee.getDeclaringClass();
		final String subSig = callee.getSubSignature();
		
		// Check whether we have any of the interfaces on the list
		for (SootClass i : interfacesOf.getUnchecked(sCallSite.getInvokeExpr().getMethod().getDeclaringClass())) {
			if (i.declaresMethod(subSig)) {
				SourceSinkDefinition def = this.sinkMethods.get(i.getMethod(subSig));
				if (def != null)
					return def;
			}
		}
		
		// Ask the CFG in case we don't know any better
		for (SootMethod sm : manager.getICFG().getCalleesOfCallAt(sCallSite)) {
			SourceSinkDefinition def = this.sinkMethods.get(sm);
			if (def != null)
				return def;
		}
		
		// If the target method is in a phantom class, we scan the hierarchy upwards
		// to see whether we have a sink definition for a parent class
		if (callee.getDeclaringClass().isPhantom()) {
			SourceSinkDefinition def = findDefinitionInHierarchy(callee, this.sinkMethods);
			if (def != null)
				return def;
		}
		
		// Do not consider ICC methods as sinks if only the base object is
		// tainted
		boolean isParamTainted = false;
		if (ap != null) {
			if (!sc.isInterface() && !ap.isStaticFieldRef()) {
				for (int i = 0; i < sCallSite.getInvokeExpr().getArgCount(); i++) {
					if (sCallSite.getInvokeExpr().getArg(i) == ap.getPlainValue()) {
						isParamTainted = true;
						break;
					}
				}
			}
		}
			
		if (isParamTainted || ap == null) {
			for (SootClass clazz : iccBaseClasses) {
				if (Scene.v().getOrMakeFastHierarchy().isSubclass(sc, clazz)) {
					SootMethod sm = clazz.getMethodUnsafe(subSig);
					if (sm != null) {
						SourceSinkDefinition def = this.sinkMethods.get(sm);
						if (def != null)
							return def;
						break;
					}
				}
			}
		}
		
		return null;
	}

	/**
	 * Scans the hierarchy of the class containing the given method to find any
	 * implementations of the same method further up in the hierarchy for which
	 * there is a SourceSinkDefinition in the given map
	 * @param callee The method for which to look for a SourceSinkDefinition
	 * @param map A map from methods to their corresponding SourceSinkDefinitions
	 * @return A SourceSinKDefinition for an implementation of the given method
	 * somewhere up in the class hiearchy if it exists, otherwise null.
	 */
	private SourceSinkDefinition findDefinitionInHierarchy(SootMethod callee,
			Map<SootMethod, SourceSinkDefinition> map) {
		final String subSig = callee.getSubSignature();
		SootClass curClass = callee.getDeclaringClass();
		while (curClass != null) {
			// Does the current class declare the requested method?
			SootMethod curMethod = curClass.getMethodUnsafe(subSig);
			if (curMethod != null) {
				SourceSinkDefinition def = map.get(curMethod);
				if (def != null) {
					// Patch the map to contain a direct link
					map.put(callee, def);
					return def;
				}
			}
			
			// Try the next class up the hierarchy
			if (curClass.hasSuperclass() && curClass.isPhantom())
				curClass = curClass.getSuperclass();
			else
				curClass = null;
		}
		
		return null;
	}

	@Override
	public boolean isSink(Stmt sCallSite, InfoflowManager manager, AccessPath ap) {		
		return getSinkDefinition(sCallSite, manager, ap) != null;
	}

	@Override
	public SourceInfo getSourceInfo(Stmt sCallSite, InfoflowManager manager) {
		SourceType type = getSourceType(sCallSite, manager.getICFG());
		if (type == SourceType.NoSource)
			return null;
		
		return getSourceInfo(sCallSite, type, manager);
	}

	protected SourceInfo getSourceInfo(Stmt sCallSite, SourceType type,
			InfoflowManager manager) {
		if (type == SourceType.UISource || type == SourceType.Callback) {
			if (sCallSite instanceof DefinitionStmt) {
				DefinitionStmt defStmt = (DefinitionStmt) sCallSite;
				return new SourceInfo(manager.getAccessPathFactory().createAccessPath(
						defStmt.getLeftOp(), true));
			}
			return null;
		}
		
		// The only other possibility is to have a method invocation
		if (!sCallSite.containsInvokeExpr())
			return null;
		
		// If this is a method call and we have a return value, we taint it.
		// Otherwise, if we have an instance invocation, we taint the base
		// object
		final InvokeExpr iexpr = sCallSite.getInvokeExpr();
		if (sCallSite instanceof DefinitionStmt && iexpr.getMethod().getReturnType() != null) {
			DefinitionStmt defStmt = (DefinitionStmt) sCallSite;
			return new SourceInfo(manager.getAccessPathFactory().createAccessPath(
					defStmt.getLeftOp(), true));
		}
		else if (iexpr instanceof InstanceInvokeExpr
				&& iexpr.getMethod().getReturnType() == VoidType.v()) {
			InstanceInvokeExpr iinv = (InstanceInvokeExpr) sCallSite.getInvokeExpr();
			return new SourceInfo(manager.getAccessPathFactory().createAccessPath(
					iinv.getBase(), true));
		}
		else
			return null;
	}
	
	/**
	 * Checks whether the given method is registered as a source method
	 * @param method The method to check
	 * @return True if the given method is a source method, otherwise false
	 */
	protected boolean isSourceMethod(SootMethod method) {
		if (oneSourceAtATime)
			return osaatType == SourceType.MethodCall && currentSource == method;
		else
			return this.sourceMethods.containsKey(method);
	}

	/**
	 * Checks whether the given method is registered as a source method
	 * @param method The method to check
	 * @return True if the given method is a source method, otherwise false
	 */
	protected SourceSinkDefinition getSourceDefinition(SootMethod method) {
		if (oneSourceAtATime) {
			if (osaatType == SourceType.MethodCall && currentSource == method)
				return this.sourceMethods.get(method);
			else
				return null;
		}
		else
			return this.sourceMethods.get(method);
	}

	/**
	 * Checks whether the given method is registered as a callback method. If so,
	 * the corresponding source definition is returned, otherwise null is returned.
	 * @param method The method to check
	 * @return The source definition object if the given method is a callback method,
	 * otherwise null
	 */
	protected CallbackDefinition getCallbackDefinition(SootMethod method) {
		if (oneSourceAtATime) {
			if (osaatType == SourceType.Callback && currentSource == method
					&& this.callbackMethods.containsKey(method))
				return this.callbackMethods.get(method);
			else
				return null;
		}
		else
			return this.callbackMethods.get(method);
	}

	/**
	 * Checks whether the given statement is a source, i.e. introduces new
	 * information into the application. If so, the type of source is returned,
	 * otherwise the return value is SourceType.NoSource.
	 * 
	 * @param sCallSite
	 *            The statement to check for a source
	 * @param cfg
	 *            An interprocedural CFG containing the statement
	 * @return The type of source that was detected in the statement of NoSource
	 *         if the statement does not contain a source
	 */
	protected SourceType getSourceType(Stmt sCallSite, IInfoflowCFG cfg) {
		assert cfg != null;
		assert cfg instanceof BiDiInterproceduralCFG;
		
		// This might be a normal source method
		if ((!oneSourceAtATime || osaatType == SourceType.MethodCall) && sCallSite.containsInvokeExpr()) {
			if (isSourceMethod(sCallSite.getInvokeExpr().getMethod()))
				return SourceType.MethodCall;
			
			// Check whether we have any of the interfaces on the list
			final SootMethod callee = sCallSite.getInvokeExpr().getMethod();
			final String subSig = callee.getSubSignature();
			for (SootClass i : interfacesOf.getUnchecked(sCallSite.getInvokeExpr()
					.getMethod().getDeclaringClass())) {
				if (i.declaresMethod(subSig))
					if (isSourceMethod(i.getMethod(subSig)))
						return SourceType.MethodCall;
			}
			
			// Ask the CFG in case we don't know any better
			for (SootMethod sm : cfg.getCalleesOfCallAt(sCallSite)) {
				if (isSourceMethod(sm))
					return SourceType.MethodCall;
			}
			
			// If the target method is in a phantom class, we scan the hierarchy upwards
			// to see whether we have a sink definition for a parent class
			if (callee.getDeclaringClass().isPhantom()) {
				SourceSinkDefinition def = findDefinitionInHierarchy(callee, this.sourceMethods);
				if (def != null)
					return SourceType.MethodCall;
			}
		}

		// This call might read out sensitive data from the UI
		if ((!oneSourceAtATime || osaatType == SourceType.UISource) && isUISource(sCallSite, cfg))
			return SourceType.UISource;

		// This statement might access a sensitive parameter in a callback
		// method
		if (enableCallbackSources && (!oneSourceAtATime || osaatType == SourceType.Callback)) {
			SootMethod parentMethod = cfg.getMethodOf(sCallSite);
			
			// We do not consider the parameters of lifecycle methods as sources by default
			if (enableLifecycleSources || !entryPointUtils.isEntryPointMethod(parentMethod)) {
				if (sCallSite instanceof IdentityStmt) {
					IdentityStmt is = (IdentityStmt) sCallSite;
					if (is.getRightOp() instanceof ParameterRef) {
						// If this is a UI element, we only consider it as a source
						// if we actually want to taint all UI elements
						CallbackDefinition def = getCallbackDefinition(parentMethod);
						if (def != null)
							if (def.getCallbackType() != CallbackType.Widget || layoutMatching == LayoutMatchingMode.MatchAll)
								return SourceType.Callback;
					}
				}
			}
		}

		return SourceType.NoSource;
	}
	
	/**
	 * Checks whether the given call site indicates a UI source, e.g. a password
	 * input
	 * 
	 * @param sCallSite
	 *            The call site that may potentially read data from a sensitive
	 *            UI control
	 * @param cfg
	 *            The bidirectional control flow graph
	 * @return True if the given call site reads data from a UI source, false
	 *         otherwise
	 */
	private boolean isUISource(Stmt sCallSite, IInfoflowCFG cfg) {
		// If we match input controls, we need to check whether this is a call
		// to one of the well-known resource handling functions in Android
		if (this.layoutMatching != LayoutMatchingMode.NoMatch && sCallSite.containsInvokeExpr()) {
			InvokeExpr ie = sCallSite.getInvokeExpr();
			SootMethod callee = ie.getMethod();
			
			// Is this a call to resource-handling method?
			boolean isResourceCall = callee == smActivityFindViewById || callee == smViewFindViewById;
			if (!isResourceCall) {
				for (SootMethod cfgCallee : cfg.getCalleesOfCallAt(sCallSite)) {
					if (cfgCallee == smActivityFindViewById || cfgCallee == smViewFindViewById) {
						isResourceCall = true;
						break;
					}
				}
			}
			
			// We need special treatment for the Android support classes
			if (!isResourceCall) {
				if (callee.getDeclaringClass().isPhantom()
						&& callee.getDeclaringClass().getName().startsWith("android.support.")
						&& callee.getSubSignature() == smActivityFindViewById.getSubSignature())
					isResourceCall = true;
			}
			
			if (isResourceCall) {
				// Perform a constant propagation inside this method exactly
				// once
				SootMethod uiMethod = cfg.getMethodOf(sCallSite);
				if (analyzedLayoutMethods.add(uiMethod))
					ConstantPropagatorAndFolder.v().transform(uiMethod.getActiveBody());

				// If we match all controls, we don't care about the specific
				// control we're dealing with
				if (this.layoutMatching == LayoutMatchingMode.MatchAll)
					return true;
				// If we don't have a layout control list, we cannot perform any
				// more specific checks
				if (this.layoutControls == null)
					return false;

				// If we match specific controls, we need to get the ID of
				// control and look up the respective data object
				if (ie.getArgCount() != 1) {
					System.err.println("Framework method call with unexpected number of arguments");
					return false;
				}
				int id = 0;
				if (ie.getArg(0) instanceof IntConstant)
					id = ((IntConstant) ie.getArg(0)).value;
				else if (ie.getArg(0) instanceof Local) {
					Integer idVal = findLastResIDAssignment(sCallSite, (Local) ie.getArg(0),
							cfg, new HashSet<Stmt>(cfg.getMethodOf(sCallSite).getActiveBody().getUnits().size()));
					if (idVal == null) {
						logger.debug("Could not find assignment to local "
								+ ((Local) ie.getArg(0)).getName()
								+ " in method "
								+ cfg.getMethodOf(sCallSite).getSignature());
						return false;
					} else
						id = idVal.intValue();
				} else {
					logger.warn("Framework method call with unexpected " + "parameter type: " + ie.toString() + ", " + "first parameter is of type " + ie.getArg(0).getClass());
					return false;
				}

				LayoutControl control = this.layoutControls.get(id);
				if (control == null) {
//					logger.warn("Layout control with ID " + id + " not found");
					return false;
				}
				if (this.layoutMatching == LayoutMatchingMode.MatchSensitiveOnly && control.isSensitive())
					return true;
			}
		}
		return false;
	}

	/**
	 * Finds the last assignment to the given local representing a resource ID
	 * by searching upwards from the given statement
	 * 
	 * @param stmt
	 *            The statement from which to look backwards
	 * @param local
	 *            The variable for which to look for assignments
	 * @return The last value assigned to the given variable
	 */
	private Integer findLastResIDAssignment(Stmt stmt, Local local, BiDiInterproceduralCFG<Unit, SootMethod> cfg, Set<Stmt> doneSet) {
		if (!doneSet.add(stmt))
			return null;

		// If this is an assign statement, we need to check whether it changes
		// the variable we're looking for
		if (stmt instanceof AssignStmt) {
			AssignStmt assign = (AssignStmt) stmt;
			if (assign.getLeftOp() == local) {
				// ok, now find the new value from the right side
				if (assign.getRightOp() instanceof IntConstant)
					return ((IntConstant) assign.getRightOp()).value;
				else if (assign.getRightOp() instanceof FieldRef) {
					SootField field = ((FieldRef) assign.getRightOp()).getField();
					for (Tag tag : field.getTags())
						if (tag instanceof IntegerConstantValueTag)
							return ((IntegerConstantValueTag) tag).getIntValue();
						else
							System.err.println("Constant " + field + " was of unexpected type");
				} else if (assign.getRightOp() instanceof InvokeExpr) {
					InvokeExpr inv = (InvokeExpr) assign.getRightOp();
					if (inv.getMethod().getName().equals("getIdentifier") && inv.getMethod().getDeclaringClass().getName().equals("android.content.res.Resources") && this.resourcePackages != null) {
						// The right side of the assignment is a call into the
						// well-known
						// Android API method for resource handling
						if (inv.getArgCount() != 3) {
							System.err.println("Invalid parameter count for call to getIdentifier");
							return null;
						}

						// Find the parameter values
						String resName = "";
						String resID = "";
						String packageName = "";

						// In the trivial case, these values are constants
						if (inv.getArg(0) instanceof StringConstant)
							resName = ((StringConstant) inv.getArg(0)).value;
						if (inv.getArg(1) instanceof StringConstant)
							resID = ((StringConstant) inv.getArg(1)).value;
						if (inv.getArg(2) instanceof StringConstant)
							packageName = ((StringConstant) inv.getArg(2)).value;
						else if (inv.getArg(2) instanceof Local)
							packageName = findLastStringAssignment(stmt, (Local) inv.getArg(2), cfg);
						else {
							System.err.println("Unknown parameter type in call to getIdentifier");
							return null;
						}

						// Find the resource
						ARSCFileParser.AbstractResource res = findResource(resName, resID, packageName);
						if (res != null)
							return res.getResourceID();
					}
				}
			}
		}

		// Continue the search upwards
		for (Unit pred : cfg.getPredsOf(stmt)) {
			if (!(pred instanceof Stmt))
				continue;
			Integer lastAssignment = findLastResIDAssignment((Stmt) pred, local, cfg, doneSet);
			if (lastAssignment != null)
				return lastAssignment;
		}
		return null;
	}

	/**
	 * Finds the given resource in the given package
	 * 
	 * @param resName
	 *            The name of the resource to retrieve
	 * @param resID
	 * @param packageName
	 *            The name of the package in which to look for the resource
	 * @return The specified resource if available, otherwise null
	 */
	private AbstractResource findResource(String resName, String resID, String packageName) {
		// Find the correct package
		for (ARSCFileParser.ResPackage pkg : this.resourcePackages) {
			// If we don't have any package specification, we pick the app's
			// default package
			boolean matches = (packageName == null || packageName.isEmpty()) && pkg.getPackageName().equals(this.appPackageName);
			matches |= pkg.getPackageName().equals(packageName);
			if (!matches)
				continue;

			// We have found a suitable package, now look for the resource
			for (ARSCFileParser.ResType type : pkg.getDeclaredTypes())
				if (type.getTypeName().equals(resID)) {
					AbstractResource res = type.getFirstResource(resName);
					return res;
				}
		}
		return null;
	}

	/**
	 * Finds the last assignment to the given String local by searching upwards
	 * from the given statement
	 * 
	 * @param stmt
	 *            The statement from which to look backwards
	 * @param local
	 *            The variable for which to look for assignments
	 * @return The last value assigned to the given variable
	 */
	private String findLastStringAssignment(Stmt stmt, Local local, BiDiInterproceduralCFG<Unit, SootMethod> cfg) {
		if (stmt instanceof AssignStmt) {
			AssignStmt assign = (AssignStmt) stmt;
			if (assign.getLeftOp() == local) {
				// ok, now find the new value from the right side
				if (assign.getRightOp() instanceof StringConstant)
					return ((StringConstant) assign.getRightOp()).value;
			}
		}

		// Continue the search upwards
		for (Unit pred : cfg.getPredsOf(stmt)) {
			if (!(pred instanceof Stmt))
				continue;
			String lastAssignment = findLastStringAssignment((Stmt) pred, local, cfg);
			if (lastAssignment != null)
				return lastAssignment;
		}
		return null;
	}
	
	/**
	 * Sets the resource packages to be used for finding sensitive layout
	 * controls as sources
	 * 
	 * @param resourcePackages
	 *            The resource packages to be used for looking up layout
	 *            controls
	 */
	public void setResourcePackages(List<ResPackage> resourcePackages) {
		this.resourcePackages = resourcePackages;
	}

	/**
	 * Sets the name of the app's base package
	 * 
	 * @param appPackageName
	 *            The name of the app's base package
	 */
	public void setAppPackageName(String appPackageName) {
		this.appPackageName = appPackageName;
	}

	@Override
	public void initialize() {
		// Get the Soot method for the signatures we have
		if (sourceDefs != null) {
			sourceMethods = new HashMap<>();
			for (Entry<String, SourceSinkDefinition> entry : sourceDefs.entrySet()) {
				SootMethod sm = Scene.v().grabMethod(entry.getKey());
				if (sm != null)
					sourceMethods.put(sm, entry.getValue());
			}
			sourceDefs = null;
		}
		if (sinkDefs != null) {
			sinkMethods = new HashMap<>();
			for (Entry<String, SourceSinkDefinition> entry : sinkDefs.entrySet()) {
				SootMethod sm = Scene.v().grabMethod(entry.getKey());
				if (sm != null)
					sinkMethods.put(sm, entry.getValue());
			}
			sinkDefs = null;
		}
		
		// For ICC methods (e.g., startService), the classes name of these
		// methods may change through user's definition. We match all the
		// ICC methods through their base class name.
		if (iccBaseClasses == null)
			iccBaseClasses = new SootClass[] { Scene.v().getSootClass("android.content.Context"), // activity,
																									// service
																									// and
																									// broadcast
					Scene.v().getSootClass("android.content.ContentResolver"), // provider
					Scene.v().getSootClass("android.app.Activity") // some
																	// methods
																	// (e.g.,
																	// onActivityResult)
																	// only
																	// defined
																	// in
																	// Activity
																	// class
			};
		
		// Get some frequently-used methods
		this.smActivityFindViewById = Scene.v().grabMethod(Activity_FindViewById);
		this.smViewFindViewById = Scene.v().grabMethod(View_FindViewById);
	}

	@Override
	public void setOneSourceAtATimeEnabled(boolean enabled) {
		this.oneSourceAtATime = enabled;
	}

	@Override
	public boolean isOneSourceAtATimeEnabled() {
		return this.oneSourceAtATime;
	}

	@Override
	public void resetCurrentSource() {
		this.osaatIterator = this.sourceMethods.keySet().iterator();
		this.osaatType = SourceType.MethodCall;
	}

	@Override
	public void nextSource() {
		if (osaatType == SourceType.MethodCall
				|| osaatType == SourceType.Callback)
			currentSource = this.osaatIterator.next();
	}

	@Override
	public boolean hasNextSource() {
		if (osaatType == SourceType.MethodCall) {
			if (this.osaatIterator.hasNext())
				return true;
			else {
				this.osaatType = SourceType.Callback;
				this.osaatIterator = this.callbackMethods.keySet().iterator();
				return hasNextSource();
			}
		}
		else if (osaatType == SourceType.Callback) {
			if (this.osaatIterator.hasNext())
				return true;
			else {
				this.osaatType = SourceType.UISource;
				return true;
			}
		}
		else if (osaatType == SourceType.UISource) {
			osaatType = SourceType.NoSource;
			return false;
		}
		return false;
	}
	
}
