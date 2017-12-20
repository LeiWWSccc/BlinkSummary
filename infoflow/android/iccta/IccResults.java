package soot.jimple.infoflow.android.iccta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

public class IccResults 
{
	public static InfoflowResults expand(IInfoflowCFG cfg, InfoflowResults results)
	{
		if (null == results)
		{
			return results;
		}
		
		InfoflowResults expandResults = new InfoflowResults();
		
		for (ResultSinkInfo sink : results.getResults().keySet()) 
		{
			for (ResultSourceInfo source : results.getResults().get(sink)) 
			{
				expandResults.addResult(sink, source);
				
				if (isICCPath(cfg, source, sink))
				{
					if (source.getPath() != null)
						System.out.println("\t\ton Path " + Arrays.toString(source.getPath()));
				}
				
				
			}
		}
		
		
		return expandResults;
	}
	
	public static InfoflowResults clean(IInfoflowCFG cfg, InfoflowResults results)
	{
		if (null == results)
		{
			return results;
		}
		
		InfoflowResults cleanResults = new InfoflowResults();
		
		Set<String> iccSources = new HashSet<String>();
		Set<String> iccSinks = new HashSet<String>();
		
		for (ResultSinkInfo sink : results.getResults().keySet()) 
		{
			for (ResultSourceInfo source : results.getResults().get(sink)) 
			{
				String sourceBelongingClass = cfg.getMethodOf(source.getSource()).getDeclaringClass().getName();
				String sinkBelongingClass = cfg.getMethodOf(sink.getSink()).getDeclaringClass().getName();
				
				if (! sourceBelongingClass.equals(sinkBelongingClass))
				{
					String iccSource = cfg.getMethodOf(source.getSource()).getSignature() + "/" + source.getSource();
					iccSources.add(iccSource);
					
					String iccSink = cfg.getMethodOf(sink.getSink()).getSignature() + "/" + sink.getSink();
					iccSinks.add(iccSink);
					
					cleanResults.addResult(sink, source);
				}
			}
		}
		
		/*
		for (ResultSinkInfo sink : results.getResults().keySet()) 
		{
			for (ResultSourceInfo source : results.getResults().get(sink)) 
			{
				if (isIrrelevantSource(source))
				{
					continue;
				}
				
				String tmpSource = cfg.getMethodOf(source.getSource()).getSignature() + "/" + source.getSource();
				String tmpSink = cfg.getMethodOf(sink.getSink()).getSignature() + "/" + sink.getSink();
				
				if (! iccSources.contains(tmpSource) && ! iccSinks.contains(tmpSink))
				{
					cleanResults.addResult(sink, source);
				}
			}
		}
		*/
		
		return cleanResults;
	}
	
	public static boolean isICCPath(IInfoflowCFG cfg, ResultSourceInfo source, ResultSinkInfo sink)
	{
		String sourceBelongingClass = cfg.getMethodOf(source.getSource()).getDeclaringClass().getName();
		String sinkBelongingClass = cfg.getMethodOf(sink.getSink()).getDeclaringClass().getName();
		
		if (! sourceBelongingClass.equals(sinkBelongingClass))
		{
			return true;
		}
		
		return false;
	}
	
	public static boolean isIrrelevantSource(ResultSourceInfo source)
	{
		if (source.getSource().containsInvokeExpr())
		{
			if (source.getSource().getInvokeExpr().getMethod().getName().equals("getIntent"))
			{
				return true;
			}
		}
		
		return false;
	}
}
