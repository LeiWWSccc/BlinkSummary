package soot.jimple.infoflow.android.iccta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import soot.jimple.infoflow.android.iccta.Ic3Data.Application.Component;


public class Ic3Provider implements IccLinkProvider 
{
	private String ic3Model = null;
	
	public Ic3Provider(String ic3Model)
	{
		this.ic3Model = ic3Model;
	}
	
	@Override
	public List<IccLink> getIccLinks() 
	{
		List<IccLink> iccLinks = new ArrayList<IccLink>();
		
		App app = Ic3ResultLoader.load(ic3Model);
		
		if (null == app)
		{
			System.out.println("[IccTA] " + ic3Model + " is not a valid IC3 model");

			return iccLinks;
		}
		
		Set<Intent> intents = app.getIntents();
		for (Intent intent : intents)
		{
			if (intent.isImplicit())
			{
				if (null == intent.getAction())
				{
					continue;
				}
				List<Component> targetedComps = intent.resolve(app.getComponentList());
				
				for (Component targetComp : targetedComps)
				{
					if (! availableTargetedComponent(intent.getApp(), targetComp.getName()))
					{
						continue;
					}
					
					IccLink iccLink = new IccLink();
					iccLink.setFromSMString(intent.getLoggingPoint().getCallerMethodSignature());
					iccLink.setIccMethod(intent.getLoggingPoint().getCalleeMethodSignature());
					iccLink.setInstruction(intent.getLoggingPoint().getStmtSequence());
					
					iccLink.setExit_kind(targetComp.getKind().name());
					iccLink.setDestinationC(targetComp.getName());
					
					iccLinks.add(iccLink);
				}
			}
			else
			{
				String targetCompName = intent.getComponentClass();
				if (! availableTargetedComponent(intent.getApp(), targetCompName))
				{
					continue;
				}
				
				IccLink iccLink = new IccLink();
				iccLink.setFromSMString(intent.getLoggingPoint().getCallerMethodSignature());
				iccLink.setIccMethod(intent.getLoggingPoint().getCalleeMethodSignature());
				iccLink.setInstruction(intent.getLoggingPoint().getStmtSequence());
				
				
				
				for (Component comp : intent.getApp().getComponentList())
				{
					if (comp.getName().equals(targetCompName))
					{
						iccLink.setExit_kind(comp.getKind().name());
					}
				}
				iccLink.setDestinationC(targetCompName);
				
				iccLinks.add(iccLink);
			}
		}
		int j=1;
		System.out.println("Start linking the icc links with their target...");
		for (IccLink il : iccLinks)
		{	
			il.linkWithTarget();
			j++;
		}
		j--;
		System.out.println("... End the icc links with their target for all the "+j+" iccLinks.");
		return iccLinks;
	}

	private boolean availableTargetedComponent(App app, String targetedComponentName)
	{
		for (Component comp : app.getComponentList())
		{
			if (comp.getName().equals(targetedComponentName))
			{
				return true;
			}
		}
		
		return false;
	}
}
