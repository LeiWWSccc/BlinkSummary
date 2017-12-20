package soot.jimple.infoflow.android.iccta;

import java.util.List;

import soot.jimple.infoflow.handlers.PreAnalysisHandler;

public class IccInstrumenter implements PreAnalysisHandler 
{
	
	private final String iccModel;

	public IccInstrumenter(String iccModel) {
		this.iccModel = iccModel;
	}
	
	@Override
	public void onBeforeCallgraphConstruction() {
		System.out.println("[IccTA] Launching IccTA Transformer...");
		
		System.out.println("[IccTA] Loading the ICC Model...");
		Ic3Provider provider = new Ic3Provider(iccModel);
		List<IccLink> iccLinks = provider.getIccLinks();
		System.out.println("[IccTA] ...End Loading the ICC Model");
		
		System.out.println("[IccTA] Lauching ICC Redirection Creation...");
		for (IccLink link : iccLinks) {			
			if (link.fromU == null) {
				continue;
			}
	        IccRedirectionCreator.v().redirectToDestination(link);
		}
		System.out.println("[IccTA] ...End ICC Redirection Creation");
	}

	@Override
	public void onAfterCallgraphConstruction() {
		//
	}
}
