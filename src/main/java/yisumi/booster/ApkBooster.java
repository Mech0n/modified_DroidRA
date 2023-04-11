package yisumi.booster;

import java.io.File;
import java.io.IOException;
import java.util.*;

import yisumi.ClassDescription;
import yisumi.GlobalRef;
import yisumi.model.ReflectionProfile;
import yisumi.model.ReflectionProfile.RClass;
import yisumi.model.SimpleStmtValue;
import yisumi.model.StmtKey;
import yisumi.model.StmtType;
import yisumi.model.StmtValue;
import yisumi.model.UniqStmt;

import org.apache.commons.io.FileUtils;

import soot.G;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.Transform;
import soot.options.Options;

public class ApkBooster extends SceneTransformer
{
	public static void apkBooster(String input, String clsPath, String outputDir) 
	{
		try
		{
			FileUtils.cleanDirectory(new File(outputDir));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		String[] args2 =
        {
            "-ire",
			"-pp",
			"-p", "cg", "enabled:true"
        };
		initSoot();
		
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.ApkBooster", new ApkBooster()));
		soot.Main.main(args2);
		
		G.reset();
	}

	private static void initSoot() {
		G.reset();
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_full_resolver(true);
		Options.v().set_drop_bodies_after_load(false);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);
		Options.v().ignore_resolution_errors();
		Options.v().set_no_writeout_body_releasing(true);
		Options.v().set_output_dir(GlobalRef.WORKSPACE + "_boosted_apps");
		Options.v().set_output_format(Options.output_format_dex);
		Options.v().set_whole_program(true);
		Options.v().set_process_multiple_dex(true);
		Options.v().set_android_jars(GlobalRef.android_jar);
		Options.v().set_process_dir(Collections.singletonList(GlobalRef.apkPath));
		Options.v().set_prepend_classpath(true);
		Options.v().set_keep_line_number(true);
		Options.v().set_app(true);
		Options.v().set_include_all(true);
	}
	
	private void sanitize()
	{
		Map<String, RClass> tmpRClasses = new HashMap<String, RClass>();
		
		for (Map.Entry<String, RClass> entry : ReflectionProfile.rClasses.entrySet())
		{
			String clsName = entry.getKey();
			RClass rClass = entry.getValue();
			
			if (! "(.*)".equals(clsName))
			{
				tmpRClasses.put(clsName, rClass);
			}
		}
		
		ReflectionProfile.rClasses = tmpRClasses;
		
		Map<UniqStmt, StmtValue> tmpUniqStmtKeyValues = new HashMap<UniqStmt, StmtValue>();
		
		for (Map.Entry<UniqStmt, StmtValue> entry : GlobalRef.uniqStmtKeyValues.entrySet())
		{
			UniqStmt uniqStmt = entry.getKey();
			StmtValue stmtValue = entry.getValue();
			
			Set<ClassDescription> tmpClsSet = new HashSet<ClassDescription>();
			for (ClassDescription clsDesc : stmtValue.getClsSet())
			{
				if (clsDesc.cls != null && !"(.*)".equals(clsDesc.cls))
				{
					if (stmtValue.getType().equals(StmtType.CONSTRUCTOR_CALL))
					{
						tmpClsSet.add(clsDesc);
					}
					else
					{
						if (clsDesc.name != null && ! "(.*)".equals(clsDesc.name))
						{
							tmpClsSet.add(clsDesc);
						}
					}
				}
			}
			
			stmtValue.setClsSet(tmpClsSet);
			
			if (stmtValue.getClsSet().size() != 0)
				tmpUniqStmtKeyValues.put(uniqStmt, stmtValue);
		}
		
		GlobalRef.uniqStmtKeyValues = tmpUniqStmtKeyValues;
		
		for (Map.Entry<UniqStmt, StmtValue> entry : GlobalRef.uniqStmtKeyValues.entrySet())
		{
			UniqStmt uniqStmt = entry.getKey();
			StmtValue stmtValue = entry.getValue();
		
			System.out.println(uniqStmt.stmt);
			for (ClassDescription desc : stmtValue.getClsSet())
			{
				System.out.println("---->" + desc.cls + "," + desc.name);
			}
		}
	}
	
	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) 
	{
		//Remove (.*) class before bossting the application
		sanitize();
		
		Alteration.v().init();
		
		InstrumentationUtils.mockSootClasses(ReflectionProfile.rClasses);
		
		Set<InstrumentPoint> instrumentPoints = toUniqStmtSimpleStmtValues(GlobalRef.uniqStmtKeyValues);
		
		Set<InstrumentPoint> ips = noAndroidSystemInstrumentation(instrumentPoints);
		
		Set<InstrumentPoint> ips2 = probeStmtKey(ips);
		
		for (InstrumentPoint ip : ips2)
		{
			System.out.println("==>IP:" + ip.simpleStmtValue.getClsDesc().cls + "," + ip.simpleStmtValue.getClsDesc().name);
		}
		
		//updateStmtKey much be called before the instrumentation, otherwise, the stmt line number will change because of the instrumentation.
		
		instrument(ips);
		
		System.out.println("==>CG_SIZE: " + Scene.v().getCallGraph().size());
	}

	public static void instrument(Set<InstrumentPoint> instrumentPoints)
	{
		for (InstrumentPoint ip : instrumentPoints)
		{
			//ip.updateStmtKey();
			StmtKey stmtKey = ip.stmtKey;
			UniqStmt uniqStmt = ip.uniqStmt;
			SimpleStmtValue stmtValue = ip.simpleStmtValue;
			
			
			IInstrumentation instr;
			switch (stmtValue.getType())
			{
			case FIELD_CALL:
				instr = new FieldCallInstrumentation(stmtKey, stmtValue, uniqStmt);
				break;
			case METHOD_CALL:
				instr = new MethodCallInstrumentation(stmtKey, stmtValue, uniqStmt);
				break;
			case CONSTRUCTOR_CALL:
				instr = new ConstructorCallInstrumentation(stmtKey, stmtValue, uniqStmt);
				break;
			case CLASS_NEW_INSTANCE:
				instr = new ClassNewInstanceCallInstrumentation(stmtKey, stmtValue, uniqStmt);
				break;
			default:
				instr = null;
			}
			
			if (null != instr)
			{
				System.out.println(instr);
				instr.instrument();
			}
		}
	}
	
	public static Set<InstrumentPoint> toUniqStmtSimpleStmtValues(Map<UniqStmt, StmtValue> uniqStmtKeyValues)
	{
		Set<InstrumentPoint> instrumentPoints = new HashSet<InstrumentPoint>();
		
		for (Map.Entry<UniqStmt, StmtValue> entry : uniqStmtKeyValues.entrySet())
		{
			UniqStmt uniqStmt = entry.getKey();
			StmtValue stmtValue = entry.getValue();
			
			for (ClassDescription clsDesc : stmtValue.getClsSet())
			{
				SimpleStmtValue ssValue = new SimpleStmtValue(stmtValue.getType(), clsDesc);
				
				InstrumentPoint ip = new InstrumentPoint();
				ip.uniqStmt = uniqStmt;
				ip.simpleStmtValue = ssValue;
				instrumentPoints.add(ip);
			}
		}
		
		return instrumentPoints;
	}
	
	public Set<InstrumentPoint> noAndroidSystemInstrumentation(Set<InstrumentPoint> instrumentPoints)
	{
		Set<InstrumentPoint> ips = new HashSet<InstrumentPoint>();
		for (InstrumentPoint ip : instrumentPoints)
		{
			String clsName = ip.simpleStmtValue.getClsDesc().cls;
			String fieldName = ip.simpleStmtValue.getClsDesc().name;
			
			if (null == clsName || clsName.isEmpty() || ("(.*)".equals(clsName)))
			{
				continue;
			}
			
			
			
			if (ip.simpleStmtValue.getType() == StmtType.CLASS_NEW_INSTANCE)
			{
				clsName = fieldName;
				
				/*
				if (null == fieldName || fieldName.isEmpty() || ("(.*)".equals(fieldName)))
				{
					continue;
				}*/
			}
			
			if (AndroidPackages.belongTo(clsName))
			{
				continue;
			}
			
			ips.add(ip);
		}
		
		return ips;
	}
	
	public Set<InstrumentPoint> probeStmtKey(Set<InstrumentPoint> instrumentPoints)
	{
		Set<InstrumentPoint> ips = new HashSet<InstrumentPoint>();
		for (InstrumentPoint ip : instrumentPoints)
		{
			ip.updateStmtKey();
			ips.add(ip);
		}
		
		return ips;
	}
}
