package soot.jimple.infoflow.android;

import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.source.AndroidSourceSinkManager.LayoutMatchingMode;
import soot.jimple.infoflow.data.pathBuilders.DefaultPathBuilderFactory.PathBuilder;

/**
 * Configuration class for the Android-specific data flow analysis
 * 
 * @author Steven Arzt
 *
 */
public class InfoflowAndroidConfiguration extends InfoflowConfiguration {
	
	/**
	 * Enumeration containing the supported callback analyzers
	 */
	public enum CallbackAnalyzer {
		/**
		 * The highly-precise default analyzer
		 */
		Default,
		/**
		 * An analyzer that favors performance over precision
		 */
		Fast
	}
	
	private boolean computeResultPaths = false;
	private PathBuilder pathBuilder = PathBuilder.ContextInsensitiveSourceFinder;
	
	private boolean enableCallbacks = true;
	private boolean enableCallbackSources = false;
	//private boolean enableCallbackSources = true;
	private boolean enableLifecycleSources = false;
	private boolean filterThreadCallbacks = true;
	private int maxCallbacksPerComponent = 100;
	private int callbackAnalysisTimeout = 0;
	private int maxCallbackAnalysisDepth = -1;
	
	private CallbackAnalyzer callbackAnalyzer = CallbackAnalyzer.Default;
	private boolean oneComponentAtATime = false;
	
	private LayoutMatchingMode layoutMatchingMode = LayoutMatchingMode.MatchSensitiveOnly;
	
	private boolean iccEnabled = false;
	private String iccModel = null;
	private boolean iccResultsPurify = true;
	
	private boolean useExistingSootInstance = false;
	
	public InfoflowAndroidConfiguration() {
		// We need to adapt some of the defaults. Most people don't care about
		// this stuff, but want a faster analysis.
		this.setEnableArraySizeTainting(false);
		this.setInspectSources(false);
		this.setInspectSinks(false);
		this.setIgnoreFlowsInSystemPackages(true);
		this.setExcludeSootLibraryClasses(true);
	}
	
	@Override
	public void merge(InfoflowConfiguration config) {
		super.merge(config);
		if (config instanceof InfoflowAndroidConfiguration) {
			InfoflowAndroidConfiguration androidConfig = (InfoflowAndroidConfiguration) config;
			this.computeResultPaths = androidConfig.computeResultPaths;
			this.pathBuilder = androidConfig.pathBuilder;
			this.enableCallbacks = androidConfig.enableCallbacks;
			this.enableCallbackSources = androidConfig.enableCallbackSources;
			this.enableLifecycleSources = androidConfig.enableLifecycleSources;
			this.filterThreadCallbacks = androidConfig.filterThreadCallbacks;
			this.maxCallbacksPerComponent = androidConfig.maxCallbacksPerComponent;
			this.callbackAnalysisTimeout = androidConfig.callbackAnalysisTimeout;
			this.maxCallbackAnalysisDepth = androidConfig.maxCallbackAnalysisDepth;
			
			this.callbackAnalyzer = androidConfig.callbackAnalyzer;
			this.oneComponentAtATime = androidConfig.oneComponentAtATime;
			
			this.layoutMatchingMode = androidConfig.layoutMatchingMode;
			
			this.iccEnabled = androidConfig.iccEnabled;
			this.iccModel = androidConfig.iccModel;
			
			this.useExistingSootInstance = androidConfig.useExistingSootInstance;
		}
	}
	
	/**
	 * Sets the algorithm to be used for reconstructing the paths between sources and sinks
	 * 
	 * @param builder
	 *            The path reconstruction algorithm to be used
	 */
	public void setPathBuilder(PathBuilder builder) {
		this.pathBuilder = builder;
	}
	
	/**
	 * Gets the algorithm to be used for reconstructing the paths between sources and sinks
	 * 
	 * @return The path reconstruction algorithm to be used
	 */
	public PathBuilder getPathBuilder() {
		return this.pathBuilder;
	}

	/**
	 * Sets whether the exact paths between source and sink shall be computed. If this feature is disabled, only the
	 * source-and-sink pairs are reported. This option only applies if the selected path reconstruction algorithm
	 * supports path computations.
	 * 
	 * @param computeResultPaths
	 *            True if the exact propagation paths shall be computed, otherwise false
	 */
	public void setComputeResultPaths(boolean computeResultPaths) {
		this.computeResultPaths = computeResultPaths;
	}
	
	/**
	 * Gets whether the exact paths between source and sink shall be computed. If this feature is disabled, only the
	 * source-and-sink pairs are reported. This option only applies if the selected path reconstruction algorithm
	 * supports path computations.
	 * 
	 * @return True if the exact propagation paths shall be computed, otherwise false
	 */
	public boolean getComputeResultPaths() {
		return this.computeResultPaths;
	}
	
	/**
	 * Sets whether the taint analysis shall consider callbacks
	 * 
	 * @param enableCallbacks
	 *            True if taints shall be tracked through callbacks, otherwise false
	 */
	public void setEnableCallbacks(boolean enableCallbacks) {
		this.enableCallbacks = enableCallbacks;
	}
	
	/**
	 * Gets whether the taint analysis shall consider callbacks
	 * 
	 * @return True if taints shall be tracked through callbacks, otherwise false
	 */
	public boolean getEnableCallbacks() {
		return this.enableCallbacks;
	}
	
	/**
	 * Sets whether the taint analysis shall consider callback as sources
	 * 
	 * @param enableCallbackSources
	 *            True if setting callbacks as sources
	 */
	public void setEnableCallbackSources(boolean enableCallbackSources) {
		this.enableCallbackSources = enableCallbackSources;
	}
	
	/**
	 * Gets whether the taint analysis shall consider callback as sources
	 * 
	 * @return True if setting callbacks as sources
	 */
	public boolean getEnableCallbackSources() {
		return this.enableCallbackSources;
	}
	
	/**
	 * Sets whether the parameters of lifecycle methods shall be considered as
	 * sources
	 * @param enableLifecycleSoures True if the parameters of lifecycle methods
	 * shall be considered as sources, otherwise false
	 */
	public void setEnableLifecycleSources(boolean enableLifecycleSources) {
		this.enableLifecycleSources = enableLifecycleSources;
	}
	
	/**
	 * Gets whether the parameters of lifecycle methods shall be considered as
	 * sources
	 * @return True if the parameters of lifecycle methods shall be considered as
	 * sources, otherwise false
	 */
	public boolean getEnableLifecycleSources() {
		return this.enableLifecycleSources;
	}
	
	/**
	 * Sets the mode to be used when deciding whether a UI control is a source or not
	 * 
	 * @param mode
	 *            The mode to be used for classifying UI controls as sources
	 */
	public void setLayoutMatchingMode(LayoutMatchingMode mode) {
		this.layoutMatchingMode = mode;
	}

	/**
	 * Gets the mode to be used when deciding whether a UI control is a source or not
	 * 
	 * @return The mode to be used for classifying UI controls as sources
	 */
	public LayoutMatchingMode getLayoutMatchingMode() {
		return this.layoutMatchingMode;
	}
	
	/**
	 * Sets the callback analyzer to be used in preparation for the taint analysis
	 * @param callbackAnalyzer The callback analyzer to be used
	 */
	public void setCallbackAnalyzer(CallbackAnalyzer callbackAnalyzer) {
		this.callbackAnalyzer = callbackAnalyzer;
	}
	
	/**
	 * Gets the callback analyzer that is being used in preparation for the taint
	 * analysis
	 * @return The callback analyzer being used
	 * @return
	 */
	public CallbackAnalyzer getCallbackAnalyzer() {
		return this.callbackAnalyzer;
	}
	
	/**
	 * Sets whether FlowDroid shall analyze one component at a time instead of
	 * generating one biug dummy main method containing all components
	 * @param oneComponentAtATime True if FlowDroid shall analyze one component
	 * at a time, otherwise false
	 */
	public void setOneComponentAtATime(boolean oneComponentAtATime) {
		this.oneComponentAtATime = oneComponentAtATime;
	}
	
	/**
	 * Gets whether FlowDroid shall analyze one component at a time instead of
	 * generating one biug dummy main method containing all components
	 * @return True if FlowDroid shall analyze one component at a time, otherwise
	 * false
	 */
	public boolean getOneComponentAtATime() {
		return this.oneComponentAtATime;
	}
	
	public String getIccModel() {
		return iccModel;
	}

	public void setIccModel(String iccModel) {
		this.iccModel = iccModel;
	}
	
	/**
	 * Gets whether inter-component data flow tracking is enabled or not
	 * @return True if inter-component data flow tracking is enabled, otherwise
	 * false
	 */
	public boolean isIccEnabled() {
		return this.iccModel != null && !this.iccModel.isEmpty();
	}
	
	/**
	 * Gets whether the ICC results shall be purified after the data flow
	 * computation. Purification means that flows inside components are
	 * dropped if the same flow is also part of an inter-component flow.
	 * @return True if the ICC results shall be purified, otherwise false.
	 * Note that this method also returns false if ICC processing is
	 * disabled.
	 */
	public boolean isIccResultsPurifyEnabled() {
		return isIccEnabled() && iccResultsPurify;
	}
	
	/**
	 * Sets whether the ICC results shall be purified after the data flow
	 * computation. Purification means that flows inside components are
	 * dropped if the same flow is also part of an inter-component flow.
	 * @param iccResultsPurify
	 */
	public void setIccResultsPurify(boolean iccResultsPurify) {
		this.iccResultsPurify = iccResultsPurify;
	}

	/**
	 * Sets whether the data flow analysis shall use an existing Soot instance
	 * rather than create a new one. Note that it is the responsibility of the
	 * caller to make sure that pre-existing Soot instances are configured
	 * correctly for the use with FlowDroid.
	 * @param useExistingSootInstance True if a pre-existing Soot instance shall
	 * be used, false if FlowDroid shall initialize Soot on its own
	 */
	public void setUseExistingSootInstance(boolean useExistingSootInstance) {
		this.useExistingSootInstance = useExistingSootInstance;
	}
	
	/**
	 * Gets whether the data flow analysis shall use an existing Soot instance
	 * rather than create a new one. Note that it is the responsibility of the
	 * caller to make sure that pre-existing Soot instances are configured
	 * correctly for the use with FlowDroid.
	 * @return True if a pre-existing Soot instance shall be used, false if
	 * FlowDroid shall initialize Soot on its own
	 */
	public boolean getUseExistingSootInstance() {
		return this.useExistingSootInstance;
	}
	
	/**
	 * Sets whether the callback analysis algorithm should follow paths that
	 * contain threads. If this option is disabled, callbacks only registered in
	 * threads will be missed. If it is enabled, context-insensitive callgraph
	 * algorithms can lead to a high number of false positives for the callback
	 * analyzer.
	 * @param filterThreadCallbacks True to discover callbacks registered in
	 * threads, otherwise false
	 */
	public void setFilterThreadCallbacks(boolean filterThreadCallbacks) {
		this.filterThreadCallbacks = filterThreadCallbacks;
	}
	
	/**
	 * Gets whether the callback analysis algorithm should follow paths that
	 * contain threads. If this option is disabled, callbacks only registered in
	 * threads will be missed. If it is enabled, context-insensitive callgraph
	 * algorithms can lead to a high number of false positives for the callback
	 * analyzer.
	 * @return True to discover callbacks registered in threads, otherwise false
	 */
	public boolean getFilterThreadCallbacks() {
		return this.filterThreadCallbacks;
	}
	
	/**
	 * Gets the maximum number of callbacks per component. If the callback
	 * collector finds more callbacks than this number for one given component,
	 * the analysis will assume that precision has degraded too much and will
	 * analyze this component without callbacks.
	 * @return The maximum number of callbacks per component
	 */
	public int getMaxCallbacksPerComponent() {
		return this.maxCallbacksPerComponent;
	}
	
	/**
	 * Sets the maximum number of callbacks per component. If the callback
	 * collector finds more callbacks than this number for one given component,
	 * the analysis will assume that precision has degraded too much and will
	 * analyze this component without callbacks.
	 * @param maxCallbacksPerComponent The maximum number of callbacks per
	 * component
	 */
	public void setMaxCallbacksPerComponent(int maxCallbacksPerComponent) {
		this.maxCallbacksPerComponent = maxCallbacksPerComponent;
	}
	
	/**
	 * Gets the timeout in seconds after which the callback analysis shall be
	 * stopped. After the timeout, the data flow analysis will continue with
	 * those callbacks that have been found so far.
	 * @return The callback analysis timeout in seconds
	 */
	public int getCallbackAnalysisTimeout() {
		return this.callbackAnalysisTimeout;
	}
	
	/**
	 * Sets the timeout in seconds after which the callback analysis shall be
	 * stopped. After the timeout, the data flow analysis will continue with
	 * those callbacks that have been found so far.
	 * @param callbackAnalysisTimeout The callback analysis timeout in seconds
	 */
	public void setCallbackAnalysisTimeout(int callbackAnalysisTimeout) {
		this.callbackAnalysisTimeout = callbackAnalysisTimeout;
	}
	
	/**
	 * Gets the maximum depth up to which the callback analyzer shall look into
	 * chains of callbacks registering other callbacks. A value equal to or
	 * smaller than zero indicates an infinite maximum depth.
	 * @return The maximum depth up to which to look into callback registration
	 * chains.
	 */
	public int getMaxAnalysisCallbackDepth() {
		return this.maxCallbackAnalysisDepth;
	}

	/**
	 * Sets the maximum depth up to which the callback analyzer shall look into
	 * chains of callbacks registering other callbacks. A value equal to or
	 * smaller than zero indicates an infinite maximum depth.
	 * @param maxCallbackAnalysisDepth The maximum depth up to which to look
	 * into callback registration chains.
	 */
	public void setMaxAnalysisCallbackDepth(int maxCallbackAnalysisDepth) {
		this.maxCallbackAnalysisDepth = maxCallbackAnalysisDepth;
	}

}
